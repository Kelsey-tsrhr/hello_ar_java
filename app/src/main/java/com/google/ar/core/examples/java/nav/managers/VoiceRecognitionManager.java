package com.google.ar.core.examples.java.nav.managers;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.google.ar.core.examples.java.nav.api.model.BaiduAsrResponse;

import java.io.ByteArrayOutputStream;

/**
 * 百度云短语音识别管理器
 *
 * 流程：
 * 1. 使用 AudioRecord 采集 16k / 单声道 / PCM16 音频
 * 2. 自动检测开始说话与静音结束
 * 3. 将 PCM 原始字节上传到百度 server_api
 */
public class VoiceRecognitionManager {
    private static final String TAG = "VoiceRecognitionMgr";

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private static final long MAX_RECORD_MS = 7000L;
    private static final long MAX_WAIT_FOR_SPEECH_MS = 2200L;
    private static final long SILENCE_HOLD_MS = 900L;
    private static final long MIN_SPEECH_MS = 500L;
    private static final int VOICE_THRESHOLD = 1500;

    private final Context appContext;
    private final NetworkRepository networkRepository;
    private final VoiceCallback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private volatile boolean isListening = false;
    private volatile boolean isDestroyed = false;
    private volatile boolean cancelRequested = false;
    private volatile AudioRecord audioRecord;

    public interface VoiceCallback {
        void onReady();
        void onListeningStarted();
        void onListeningFinished();
        void onCommandRecognized(String command);
        void onError(String message);
    }

    public VoiceRecognitionManager(Context context,
                                   NetworkRepository networkRepository,
                                   VoiceCallback callback) {
        this.appContext = context.getApplicationContext();
        this.networkRepository = networkRepository;
        this.callback = callback;
    }

    public boolean isListening() {
        return isListening;
    }

    public void startListening() {
        if (isDestroyed) {
            postError("语音识别器已销毁");
            return;
        }
        if (networkRepository == null) {
            postError("网络模块未初始化");
            return;
        }
        if (isListening) {
            postError("正在识别中，请稍候");
            return;
        }

        isListening = true;
        cancelRequested = false;

        new Thread(this::recordAndRecognize, "Baidu-ASR-Recorder").start();
    }

    public void cancelListening() {
        cancelRequested = true;
        isListening = false;
        stopAndReleaseRecorder();
    }

    public void stopListening() {
        cancelListening();
    }

    public void destroy() {
        isDestroyed = true;
        cancelListening();
    }

    private void recordAndRecognize() {
        ByteArrayOutputStream pcmOutput = new ByteArrayOutputStream();
        boolean speechDetected = false;
        boolean speechStartNotified = false;

        long startTs = SystemClock.elapsedRealtime();
        long speechStartTs = 0L;
        long lastVoiceTs = 0L;

        byte[] buffer = null;

        try {
            int minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_ENCODING);
            if (minBufferSize <= 0) {
                postErrorAndReset("无法初始化录音缓冲区");
                return;
            }

            int recordBufferSize = Math.max(minBufferSize * 2, 4096);
            buffer = new byte[recordBufferSize / 2];
            audioRecord = createAudioRecord(recordBufferSize);
            if (audioRecord == null || audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
                postErrorAndReset("无法初始化麦克风录音");
                return;
            }

            audioRecord.startRecording();
            postReady();

            while (isListening && !cancelRequested && !isDestroyed) {
                long now = SystemClock.elapsedRealtime();
                if (now - startTs > MAX_RECORD_MS) {
                    break;
                }

                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read <= 0) {
                    continue;
                }

                pcmOutput.write(buffer, 0, read);

                int amplitude = calculatePeakAmplitude(buffer, read);
                boolean voiced = amplitude >= VOICE_THRESHOLD;

                if (voiced) {
                    lastVoiceTs = now;
                    if (!speechDetected) {
                        speechDetected = true;
                        speechStartTs = now;
                    }
                    if (!speechStartNotified) {
                        speechStartNotified = true;
                        postListeningStarted();
                    }
                }

                if (!speechDetected && now - startTs > MAX_WAIT_FOR_SPEECH_MS) {
                    postErrorAndReset("未检测到清晰语音，请重试");
                    return;
                }

                if (speechDetected
                        && (now - lastVoiceTs) > SILENCE_HOLD_MS
                        && (now - speechStartTs) > MIN_SPEECH_MS) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "recordAndRecognize error", e);
            postErrorAndReset("录音失败: " + e.getMessage());
            return;
        } finally {
            stopAndReleaseRecorder();
        }

        if (cancelRequested || isDestroyed) {
            isListening = false;
            return;
        }

        byte[] pcmData = pcmOutput.toByteArray();
        if (pcmData.length == 0) {
            postErrorAndReset("没有采集到音频数据");
            return;
        }

        postListeningFinished();

        networkRepository.recognizeSpeechPcm(pcmData, new NetworkRepository.SpeechRecognitionCallback() {
            @Override
            public void onSuccess(BaiduAsrResponse response) {
                isListening = false;
                String text = response.getBestResult();
                if (text == null || text.trim().isEmpty()) {
                    postError("识别成功，但没有返回文本");
                    return;
                }
                postRecognized(text.trim());
            }

            @Override
            public void onFailure(String message, Throwable throwable) {
                isListening = false;
                postError(message);
            }
        });
    }

    private AudioRecord createAudioRecord(int bufferSize) {
        AudioRecord record = null;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_ENCODING,
                    bufferSize);
            if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                return record;
            }
            record.release();
        } catch (Exception ignored) {}

        try {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_ENCODING,
                    bufferSize);
            if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                return record;
            }
            record.release();
        } catch (Exception ignored) {}

        return null;
    }

    private void stopAndReleaseRecorder() {
        AudioRecord record = audioRecord;
        audioRecord = null;
        if (record == null) return;

        try {
            if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                record.stop();
            }
        } catch (Exception ignored) {}

        try {
            record.release();
        } catch (Exception ignored) {}
    }

    private int calculatePeakAmplitude(byte[] pcm, int length) {
        int max = 0;
        int safeLength = length - (length % 2);
        for (int i = 0; i < safeLength; i += 2) {
            int low = pcm[i] & 0xFF;
            int high = pcm[i + 1];
            short sample = (short) ((high << 8) | low);
            int abs = Math.abs(sample);
            if (abs > max) max = abs;
        }
        return max;
    }

    private void postReady() {
        if (callback == null) return;
        mainHandler.post(callback::onReady);
    }

    private void postListeningStarted() {
        if (callback == null) return;
        mainHandler.post(callback::onListeningStarted);
    }

    private void postListeningFinished() {
        if (callback == null) return;
        mainHandler.post(callback::onListeningFinished);
    }

    private void postRecognized(String text) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onCommandRecognized(text));
    }

    private void postError(String message) {
        if (callback == null) return;
        mainHandler.post(() -> callback.onError(message));
    }

    private void postErrorAndReset(String message) {
        isListening = false;
        postError(message);
    }
}
