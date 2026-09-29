package com.google.ar.core.examples.java.nav.managers;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Queue;

/**
 * 第一阶段增强版 TTS 管理器
 *
 * 兼容旧接口：
 * - speak(String)
 * - speakUrgent(String)
 * - speakWithId(String, String, int)
 * - isSpeaking()
 * - destroy()
 *
 * 在原始实现基础上增加：
 * 1. 初始化完成前的消息缓存，避免早期 speak 丢失
 * 2. 更稳健的初始化状态管理
 * 3. stop / setSpeechRate / setPitch 能力
 * 4. 使用 Application Context，降低 Activity 泄漏风险
 */
public class TTSManager {

    private static final String TAG = "TTSManager";

    public static final String ID_LOC_SUCCESS = "ID_LOC_SUCCESS";
    public static final String ID_NAV_INTRO = "ID_NAV_INTRO";

    private final Context appContext;
    private final TTSCallback callback;
    private final Queue<PendingSpeakRequest> pendingQueue = new ArrayDeque<>();

    private TextToSpeech textToSpeech;
    private boolean isInitialized = false;
    private boolean isDestroyed = false;

    private float speechRate = 1.0f;
    private float pitch = 1.0f;
    private Locale currentLocale = Locale.CHINA;

    public interface TTSCallback {
        void onUtteranceDone(String utteranceId);
    }

    private static class PendingSpeakRequest {
        final String message;
        final String utteranceId;
        final int queueMode;

        PendingSpeakRequest(String message, String utteranceId, int queueMode) {
            this.message = message;
            this.utteranceId = utteranceId;
            this.queueMode = queueMode;
        }
    }

    public TTSManager(Context context, TTSCallback callback) {
        this.appContext = context.getApplicationContext();
        this.callback = callback;
        initTts();
    }

    private void initTts() {
        textToSpeech = new TextToSpeech(appContext, status -> {
            if (isDestroyed) return;

            if (status != TextToSpeech.SUCCESS) {
                Log.e(TAG, "TTS Initialization failed");
                isInitialized = false;
                return;
            }

            try {
                boolean languageApplied = applyLanguageLocale(currentLocale);
                textToSpeech.setSpeechRate(speechRate);
                textToSpeech.setPitch(pitch);
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        Log.d(TAG, "TTS onStart: " + utteranceId);
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        if (callback != null) {
                            callback.onUtteranceDone(utteranceId);
                        }
                    }

                    @Override
                    public void onError(String utteranceId) {
                        Log.w(TAG, "TTS onError: " + utteranceId);
                    }

                    @Override
                    public void onError(String utteranceId, int errorCode) {
                        Log.w(TAG, "TTS onError: " + utteranceId + ", code=" + errorCode);
                    }

                    @Override
                    public void onStop(String utteranceId, boolean interrupted) {
                        Log.d(TAG, "TTS onStop: " + utteranceId + ", interrupted=" + interrupted);
                    }
                });

                if (!languageApplied) {
                    Log.e(TAG, "Language is not supported or missing data");
                    isInitialized = false;
                    return;
                }

                isInitialized = true;

                flushPendingQueue();
            } catch (Exception e) {
                Log.e(TAG, "TTS initialization setup failed", e);
                isInitialized = false;
            }
        });
    }

    /**
     * 排队说（不打断当前播报）
     */
    public void speak(String message) {
        speakWithId(message, null, TextToSpeech.QUEUE_ADD);
    }

    /**
     * 插队说（打断当前播报）
     */
    public void speakUrgent(String message) {
        speakWithId(message, null, TextToSpeech.QUEUE_FLUSH);
    }

    /**
     * 带 utteranceId 的播报。
     */
    public void speakWithId(String message, String utteranceId, int queueMode) {
        if (isDestroyed) {
            Log.w(TAG, "ignore speak after destroy");
            return;
        }
        if (message == null || message.trim().isEmpty()) {
            return;
        }

        if (!isInitialized || textToSpeech == null) {
            pendingQueue.offer(new PendingSpeakRequest(message, utteranceId, queueMode));
            Log.d(TAG, "TTS not ready, queued message: " + message);
            return;
        }

        internalSpeak(message, utteranceId, queueMode);
    }

    public boolean isSpeaking() {
        return textToSpeech != null && textToSpeech.isSpeaking();
    }

    /**
     * 主动停止当前播报。
     */
    public void stop() {
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
            } catch (Exception e) {
                Log.w(TAG, "stop failed", e);
            }
        }
    }

    /**
     * 调整语速，建议范围 0.5f ~ 1.5f。
     */
    public void setSpeechRate(float rate) {
        speechRate = clamp(rate, 0.3f, 2.0f);
        if (textToSpeech != null) {
            try {
                textToSpeech.setSpeechRate(speechRate);
            } catch (Exception e) {
                Log.w(TAG, "setSpeechRate failed", e);
            }
        }
    }

    /**
     * 调整音调，建议范围 0.8f ~ 1.2f。
     */
    public void setPitch(float newPitch) {
        pitch = clamp(newPitch, 0.3f, 2.0f);
        if (textToSpeech != null) {
            try {
                textToSpeech.setPitch(pitch);
            } catch (Exception e) {
                Log.w(TAG, "setPitch failed", e);
            }
        }
    }

    public float getSpeechRate() {
        return speechRate;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    public synchronized void setLanguageLocale(Locale locale) {
        Locale safeLocale = locale == null ? Locale.CHINA : locale;
        Locale previousLocale = currentLocale;
        currentLocale = safeLocale;

        if (textToSpeech == null || !isInitialized) {
            return;
        }

        try {
            if (!applyLanguageLocale(safeLocale)) {
                currentLocale = previousLocale;
                applyLanguageLocale(previousLocale);
            }
        } catch (Exception e) {
            currentLocale = previousLocale;
            Log.w(TAG, "setLanguageLocale failed", e);
        }
    }

    public Locale getLanguageLocale() {
        return currentLocale;
    }

    public void destroy() {
        isDestroyed = true;
        pendingQueue.clear();
        if (textToSpeech != null) {
            try {
                textToSpeech.stop();
                textToSpeech.shutdown();
            } catch (Exception e) {
                Log.w(TAG, "destroy failed", e);
            } finally {
                textToSpeech = null;
                isInitialized = false;
            }
        }
    }

    private void flushPendingQueue() {
        while (isInitialized && textToSpeech != null && !pendingQueue.isEmpty()) {
            PendingSpeakRequest request = pendingQueue.poll();
            if (request != null) {
                internalSpeak(request.message, request.utteranceId, request.queueMode);
            }
        }
    }

    private void internalSpeak(String message, String utteranceId, int queueMode) {
        try {
            Bundle params = new Bundle();
            if (utteranceId != null) {
                params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
            }
            textToSpeech.speak(message, queueMode, params, utteranceId);
        } catch (Exception e) {
            Log.e(TAG, "speak failed: " + message, e);
        }
    }

    private boolean applyLanguageLocale(Locale locale) {
        if (textToSpeech == null) {
            return false;
        }
        int result = textToSpeech.setLanguage(locale);
        return result != TextToSpeech.LANG_MISSING_DATA
                && result != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
