package com.google.ar.core.examples.java.nav.managers;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 第一阶段：交互配置管理器
 *
 * 负责统一管理多模态交互相关配置，避免把这些状态散落在 Activity 中。
 * 当前仅覆盖第一阶段需要的配置：
 * 1. 导航播报详尽度
 * 2. 触觉反馈开关
 * 3. 触觉灵敏度
 * 4. 长按重复指令按钮开关
 * 5. 窄门识别开关
 * 6. 楼道低频提醒模式开关
 * 7. 无障碍模式开关
 */
public class InteractionConfigManager {

    private static final String PREF_NAME = "AppConfig";

    private static final String KEY_VERBOSITY = "InteractionVerbosity";
    private static final String KEY_HAPTIC_ENABLED = "InteractionHapticEnabled";
    private static final String KEY_HAPTIC_SENSITIVITY = "InteractionHapticSensitivity";
    private static final String KEY_REPEAT_BUTTON_ENABLED = "InteractionRepeatButtonEnabled";

    private static final String KEY_CLEAN_SCREEN_ENABLED = "InteractionCleanScreenEnabled";
    private static final String KEY_NARROW_DOOR_ENABLED = "NavigationNarrowDoorEnabled";
    private static final String KEY_CORRIDOR_MODE_ENABLED = "NavigationCorridorModeEnabled";
    private static final String KEY_ACCESSIBILITY_MODE_ENABLED = "AccessibilityModeEnabled";

    private static final String KEY_CURRENT_SCENE_ID = "CurrentSceneId";
    private static final String KEY_APP_LANGUAGE = "AppLanguage";

    public enum VerbosityLevel {
        VERBOSE,
        ESSENTIAL
    }

    public enum HapticSensitivity {
        HIGH,
        MEDIUM,
        LOW
    }

    public enum AppLanguage {
        ZH,
        EN
    }

    private final SharedPreferences prefs;

    public InteractionConfigManager(Context context) {
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public VerbosityLevel getVerbosityLevel() {
        String stored = prefs.getString(KEY_VERBOSITY, VerbosityLevel.VERBOSE.name());
        try {
            return VerbosityLevel.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return VerbosityLevel.VERBOSE;
        }
    }

    public void setVerbosityLevel(VerbosityLevel level) {
        if (level == null) return;
        prefs.edit().putString(KEY_VERBOSITY, level.name()).apply();
    }

    public boolean isHapticEnabled() {
        return prefs.getBoolean(KEY_HAPTIC_ENABLED, true);
    }

    public void setHapticEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply();
    }

    public HapticSensitivity getHapticSensitivity() {
        String stored = prefs.getString(KEY_HAPTIC_SENSITIVITY, HapticSensitivity.MEDIUM.name());
        try {
            return HapticSensitivity.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return HapticSensitivity.MEDIUM;
        }
    }

    public void setHapticSensitivity(HapticSensitivity sensitivity) {
        if (sensitivity == null) return;
        prefs.edit().putString(KEY_HAPTIC_SENSITIVITY, sensitivity.name()).apply();
    }

    public boolean isRepeatButtonEnabled() {
        return prefs.getBoolean(KEY_REPEAT_BUTTON_ENABLED, true);
    }

    public void setRepeatButtonEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REPEAT_BUTTON_ENABLED, enabled).apply();
    }

    public boolean isCleanScreenEnabled() {
        return prefs.getBoolean(KEY_CLEAN_SCREEN_ENABLED, false);
    }

    public void setCleanScreenEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CLEAN_SCREEN_ENABLED, enabled).apply();
    }

    public boolean isNarrowDoorDetectionEnabled() {
        return prefs.getBoolean(KEY_NARROW_DOOR_ENABLED, true);
    }

    public void setNarrowDoorDetectionEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_NARROW_DOOR_ENABLED, enabled).apply();
    }

    public boolean isCorridorModeEnabled() {
        return prefs.getBoolean(KEY_CORRIDOR_MODE_ENABLED, true);
    }

    public void setCorridorModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_CORRIDOR_MODE_ENABLED, enabled).apply();
    }

    public boolean isAccessibilityModeEnabled() {
        return prefs.getBoolean(KEY_ACCESSIBILITY_MODE_ENABLED, false);
    }

    public void setAccessibilityModeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_MODE_ENABLED, enabled).apply();
    }

    public float getObstacleTriggerDistanceMeters() {
        return obstacleTriggerDistanceFor(getHapticSensitivity());
    }

    public float getObstacleClearDistanceMeters() {
        return obstacleClearDistanceFor(getHapticSensitivity());
    }

    public static float obstacleTriggerDistanceFor(HapticSensitivity sensitivity) {
        HapticSensitivity safe = sensitivity == null ? HapticSensitivity.MEDIUM : sensitivity;
        switch (safe) {
            case HIGH:
                return 2.2f;
            case LOW:
                return 1.2f;
            case MEDIUM:
            default:
                return 1.7f;
        }
    }

    public static float obstacleClearDistanceFor(HapticSensitivity sensitivity) {
        HapticSensitivity safe = sensitivity == null ? HapticSensitivity.MEDIUM : sensitivity;
        switch (safe) {
            case HIGH:
                return 2.6f;
            case LOW:
                return 1.5f;
            case MEDIUM:
            default:
                return 2.0f;
        }
    }

    /**
     * 可选：用于首次启动或调试时快速恢复第一阶段默认配置。
     */
    public void resetToDefaults() {
        prefs.edit()
                .putString(KEY_VERBOSITY, VerbosityLevel.VERBOSE.name())
                .putBoolean(KEY_HAPTIC_ENABLED, true)
                .putString(KEY_HAPTIC_SENSITIVITY, HapticSensitivity.MEDIUM.name())
                .putBoolean(KEY_REPEAT_BUTTON_ENABLED, true)
                .putBoolean(KEY_CLEAN_SCREEN_ENABLED, false)
                .putBoolean(KEY_ACCESSIBILITY_MODE_ENABLED, false)
                .apply();
    }

    public int getCurrentSceneId() {
        return Math.max(1, prefs.getInt(KEY_CURRENT_SCENE_ID, 1));
    }

    public void setCurrentSceneId(int sceneId) {
        prefs.edit().putInt(KEY_CURRENT_SCENE_ID, Math.max(1, sceneId)).apply();
    }

    public AppLanguage getAppLanguage() {
        String stored = prefs.getString(KEY_APP_LANGUAGE, AppLanguage.ZH.name());
        try {
            return AppLanguage.valueOf(stored);
        } catch (IllegalArgumentException e) {
            return AppLanguage.ZH;
        }
    }

    public void setAppLanguage(AppLanguage language) {
        if (language == null) return;
        prefs.edit().putString(KEY_APP_LANGUAGE, language.name()).apply();
    }
}
