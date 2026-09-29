package com.google.ar.core.examples.java.nav.managers;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

/**
 * 第一阶段：触觉反馈管理器
 *
 * 目标：
 * 1. 将手机震动能力从 Activity 中抽离
 * 2. 提供少量离散、可复用的触觉反馈模式
 * 3. 先不承担连续障碍震动逻辑，避免与第三阶段耦合
 *
 * 说明：
 * - turn hint: 转弯提示，单次短震
 * - arrival: 到达提示，双脉冲短震
 * - confirmation: 轻确认
 * - error: 较重错误提示
 */
public class HapticFeedbackManager {

    private static final String TAG = "HapticFeedbackManager";

    // 为了避免同一帧/极短时间内重复触发震动，做轻量节流
    private static final long MIN_VIBRATION_INTERVAL_MS = 120;

    private final Vibrator vibrator;
    private long lastVibrationTimestamp = 0L;
    private long lastObstaclePulseTimestamp = 0L;

    public HapticFeedbackManager(Context context) {
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            VibratorManager vibratorManager =
                    (VibratorManager) appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = vibratorManager != null ? vibratorManager.getDefaultVibrator() : null;
        } else {
            vibrator = (Vibrator) appContext.getSystemService(Context.VIBRATOR_SERVICE);
        }
    }

    /**
     * 单次短震：适用于转弯提示。
     */
    public void playTurnHint() {
        if (!canVibrateNow()) return;
        vibrateOneShot(80, 180);
    }

    /**
     * 双脉冲：适用于到达目的地。
     */
    public void playArrival() {
        if (!canVibrateNow()) return;
        vibrateWaveform(
                new long[]{0, 100, 80, 140},
                new int[]{0, 180, 0, 255},
                -1
        );
    }

    /**
     * 轻量确认：适用于模式切换或重复指令确认。
     */
    public void playConfirmation() {
        if (!canVibrateNow()) return;
        vibrateOneShot(50, 120);
    }

    /**
     * 稍重错误提示：适用于异常或无效操作反馈。
     */
    public void playError() {
        if (!canVibrateNow()) return;
        vibrateWaveform(
                new long[]{0, 160, 70, 160},
                new int[]{0, 255, 0, 200},
                -1
        );
    }


    /**
     * 第二阶段：基于空间距离的动态触觉避障。
     *
     * 说明：
     * - 不主动发声，只输出短脉冲触觉。
     * - 障碍越近，脉冲越强、间隔越短。
     * - 由 sensitivity 控制最远触发距离。
     */
    public void playObstacleProximityPulse(float distanceMeters,
                                           InteractionConfigManager.HapticSensitivity sensitivity) {
        if (!isAvailable()) return;

        float triggerDistance = InteractionConfigManager.obstacleTriggerDistanceFor(sensitivity);
        if (distanceMeters <= 0f || distanceMeters > triggerDistance) return;

        float normalizedRisk = 1f - (distanceMeters / triggerDistance);
        normalizedRisk = Math.max(0f, Math.min(1f, normalizedRisk));
        float curvedRisk = normalizedRisk * normalizedRisk;

        long now = System.currentTimeMillis();
        long obstacleCooldownMs = (long) (900f - 700f * curvedRisk);
        if (now - lastObstaclePulseTimestamp < obstacleCooldownMs) {
            return;
        }
        if (now - lastVibrationTimestamp < MIN_VIBRATION_INTERVAL_MS) {
            return;
        }
        lastObstaclePulseTimestamp = now;
        lastVibrationTimestamp = now;

        long durationMs = (long) (45f + 85f * curvedRisk);
        int amplitude = (int) (90f + 165f * Math.sqrt(normalizedRisk));
        vibrateOneShot(durationMs, amplitude);
    }

    public void stopObstacleAlert() {
        lastObstaclePulseTimestamp = 0L;
        stop();
    }

    /**
     * 主动停止当前震动。
     */
    public void stop() {
        if (vibrator != null) {
            try {
                vibrator.cancel();
            } catch (Exception e) {
                Log.w(TAG, "cancel vibration failed: " + e.getMessage());
            }
        }
    }

    /**
     * 是否具备震动能力。
     */
    public boolean isAvailable() {
        return vibrator != null && vibrator.hasVibrator();
    }

    private boolean canVibrateNow() {
        if (!isAvailable()) return false;
        long now = System.currentTimeMillis();
        if (now - lastVibrationTimestamp < MIN_VIBRATION_INTERVAL_MS) {
            return false;
        }
        lastVibrationTimestamp = now;
        return true;
    }

    private void vibrateOneShot(long milliseconds, int amplitude) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(milliseconds, clampAmplitude(amplitude)));
            } else {
                vibrator.vibrate(milliseconds);
            }
        } catch (Exception e) {
            Log.w(TAG, "one shot vibration failed: " + e.getMessage());
        }
    }

    private void vibrateWaveform(long[] timings, int[] amplitudes, int repeat) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(timings, normalizeAmplitudes(amplitudes), repeat));
            } else {
                vibrator.vibrate(timings, repeat);
            }
        } catch (Exception e) {
            Log.w(TAG, "waveform vibration failed: " + e.getMessage());
        }
    }

    private int clampAmplitude(int amplitude) {
        if (amplitude < 1) return 1;
        return Math.min(amplitude, 255);
    }

    private int[] normalizeAmplitudes(int[] amplitudes) {
        if (amplitudes == null) return null;
        int[] normalized = new int[amplitudes.length];
        for (int i = 0; i < amplitudes.length; i++) {
            int value = amplitudes[i];
            if (value <= 0) normalized[i] = 0;
            else normalized[i] = clampAmplitude(value);
        }
        return normalized;
    }
}
