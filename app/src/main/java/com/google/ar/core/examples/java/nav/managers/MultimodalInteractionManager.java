package com.google.ar.core.examples.java.nav.managers;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;

import com.google.ar.core.examples.java.nav.i18n.AppText;

/**
 * 第一阶段：多模态交互编排器
 *
 * 目标：
 * 1. 将导航语音、触觉反馈、远程日志统一收口
 * 2. 将导航中的听觉/触觉输出从 Activity 中抽离并统一管理
 * 3. 为后续第三阶段（障碍触觉）预留扩展点
 *
 * 当前支持：
 * - 语音唤醒提示
 * - 语音命令日志
 * - 导航开始提示
 * - 转弯提示（听觉 + 触觉）
 * - 偏航纠正提示（仅听觉）
 * - 偏航恢复提示（仅听觉）
 * - 到达提示
 * - 配置变更提示
 * - 当前状态播报
 * - 重复上一条指令
 */
public class MultimodalInteractionManager {

    private static final String TAG = "MultimodalInteraction";

    // 远程日志标签
    private static final String LOG_TAG_VOICE_EVENT = "VOICE_EVENT";
    private static final String LOG_TAG_SETTINGS_CHANGE = "SETTINGS_CHANGE";
    private static final String LOG_TAG_NAV_EVENT = "NAV_EVENT";
    private static final String LOG_TAG_AUDIO_OUTPUT = "AUDIO_OUTPUT";
    private static final String LOG_TAG_HAPTIC_OUTPUT = "HAPTIC_OUTPUT";
    private static final String LOG_TAG_INTERACTION_STATE = "INTERACTION_STATE";

    private final Context context;
    private final TTSManager ttsManager;
    private final HapticFeedbackManager hapticManager;
    private final InteractionConfigManager configManager;
    private final NetworkRepository networkRepository;

    private String lastInstructionText = null;
    private long lastInstructionTimestamp = 0L;

    public MultimodalInteractionManager(
            Context context,
            TTSManager ttsManager,
            HapticFeedbackManager hapticManager,
            InteractionConfigManager configManager,
            NetworkRepository networkRepository
    ) {
        this.context = context.getApplicationContext();
        this.ttsManager = ttsManager;
        this.hapticManager = hapticManager;
        this.configManager = configManager;
        this.networkRepository = networkRepository;
    }

    private String tr(String key, Object... args) {
        InteractionConfigManager.AppLanguage language = configManager == null
                ? InteractionConfigManager.AppLanguage.ZH
                : configManager.getAppLanguage();
        return AppText.text(language, key, args);
    }

    /**
     * 点击语音按钮后的唤醒反馈。
     */
    public void onVoiceWakeup() {
        // 关键修复：语音按钮按下后不再通过 TTS 说“请下达指令”。
        // 在部分 ROM / 语音服务上，识别器打开麦克风的同时本地 TTS 播报，
        // 容易触发底层音频/识别管线竞争，表现为点击语音按钮直接闪退。
        maybePlayConfirmation("voice_wakeup");
        sendLog(LOG_TAG_VOICE_EVENT, "voice_wakeup", "mode=haptic_only,verbosity=" + configManager.getVerbosityLevel().name());
    }

    /**
     * 记录识别到的原始语音命令，便于调试与实验分析。
     */
    public void onVoiceCommandRecognized(String rawCommand) {
        String safe = rawCommand == null ? "" : rawCommand.trim();
        sendLog(LOG_TAG_VOICE_EVENT, "command_recognized", "raw=" + safe);
    }


    /**
     * 语音识别未匹配到功能时的统一反馈。
     */
    public void onUnknownVoiceCommand(String rawCommand) {
        speak(tr("tts_unknown_voice"), false, false, "voice_unknown_command");
        maybePlayError("voice_unknown_command");
        sendLog(LOG_TAG_VOICE_EVENT, "command_unhandled", "raw=" + safeText(rawCommand));
    }

    /**
     * 手动定位触发时的统一反馈。
     */
    public void onLocalizationRequested() {
        speak(tr("tts_localization_requested"), false, false, "localization_requested");
        maybePlayConfirmation("localization_requested");
        sendLog(LOG_TAG_INTERACTION_STATE, "localization_requested", "source=manual_or_voice");
    }

    /**
     * 定位成功后的统一反馈。
     */
    public void onLocalizationSucceeded() {
        if (ttsManager == null) return;
        String text = tr("tts_localization_success");
        ttsManager.speakWithId(text, TTSManager.ID_LOC_SUCCESS, TextToSpeech.QUEUE_ADD);
        lastInstructionText = text;
        lastInstructionTimestamp = System.currentTimeMillis();
        maybePlayConfirmation("localization_success");
        sendLog(LOG_TAG_AUDIO_OUTPUT, "tts", "text=" + safeText(text) + ",source=localization_success,remembered=true,utteranceId=" + TTSManager.ID_LOC_SUCCESS);
        sendLog(LOG_TAG_INTERACTION_STATE, "localization_success", "verbosity=" + configManager.getVerbosityLevel().name());
    }

    /**
     * 重置 / 重新定位时的统一反馈。
     */
    public void onRelocalizationReset() {
        speak(tr("tts_relocalized"), false, true, "relocalization_reset");
        clearObstacleAlert();
        maybePlayConfirmation("relocalization_reset");
        sendLog(LOG_TAG_INTERACTION_STATE, "relocalization_reset", "navigating_cleared=true");
    }

    /**
     * 用于播报导航相关错误或提示。
     */
    public void onNavigationMessage(String text, boolean isError) {
        if (TextUtils.isEmpty(text)) return;
        speak(text, isError, false, isError ? "navigation_error" : "navigation_message");
        if (isError) {
            maybePlayError("navigation_error");
        } else {
            maybePlayConfirmation("navigation_message");
        }
        sendLog(LOG_TAG_NAV_EVENT, isError ? "navigation_error" : "navigation_message", "text=" + safeText(text));
    }

    /**
     * 功能被关闭时的统一提示。
     */
    public void onFeatureUnavailable(String text) {
        if (TextUtils.isEmpty(text)) return;
        speak(text, false, false, "feature_unavailable");
        maybePlayError("feature_unavailable");
        sendLog(LOG_TAG_INTERACTION_STATE, "feature_unavailable", "text=" + safeText(text));
    }

    public void onRepeatButtonToggled(boolean enabled) {
        String text = tr(enabled ? "tts_repeat_on" : "tts_repeat_off");
        speak(text, false, false, "repeat_button_toggled");
        maybePlayConfirmation("repeat_button_toggled");
        sendLog(LOG_TAG_SETTINGS_CHANGE, "repeat_button_toggled", "enabled=" + enabled);
    }

    public void onVisualModeChanged(String modeName) {
        String text = TextUtils.isEmpty(modeName)
                ? tr("tts_mode_changed_default")
                : tr("tts_mode_switched", modeName);
        speak(text, false, false, "visual_mode_changed");
        maybePlayConfirmation("visual_mode_changed");
        sendLog(LOG_TAG_SETTINGS_CHANGE, "visual_mode_changed", "mode=" + safeText(modeName));
    }

    /**
     * 导航开始提示。
     */
    public void onNavigationStarted(String targetName) {
        String target = TextUtils.isEmpty(targetName) ? tr("tts_default_target") : targetName;
        String text = configManager.getVerbosityLevel() == InteractionConfigManager.VerbosityLevel.VERBOSE
                ? tr("tts_nav_start", target)
                : tr("tts_nav_start_simple");

        if (ttsManager != null) {
            ttsManager.speakWithId(text, TTSManager.ID_NAV_INTRO, TextToSpeech.QUEUE_ADD);
            lastInstructionText = text;
            lastInstructionTimestamp = System.currentTimeMillis();
        }
        maybePlayConfirmation("nav_start");
        Log.d(TAG, "navigation_started: target=" + target + ", verbosity=" + configManager.getVerbosityLevel().name());
        sendLog(LOG_TAG_NAV_EVENT, "navigation_started", "target=" + target + ",verbosity=" + configManager.getVerbosityLevel().name());
    }

    /**
     * 转弯提示：左/右转使用语音 + 触觉，直行仅使用语音。
     */
    public void onTurnInstruction(int direction) {
        onTurnNow(direction);
    }

    /**
     * 偏航纠正：仅使用听觉通道，避免与转弯触觉冲突。
     * veerDirection 表示“偏到路径哪一侧”。
     */
    public void onVeerDetected(int veerDirection, float lateralOffsetMeters) {
        InteractionConfigManager.VerbosityLevel level = configManager.getVerbosityLevel();
        String instruction;
        switch (veerDirection) {
            case NavigationEngine.DIRECTION_LEFT:
                instruction = level == InteractionConfigManager.VerbosityLevel.VERBOSE
                        ? tr(lateralOffsetMeters >= 1.5f ? "tts_veer_left_strong" : "tts_veer_left")
                        : tr("tts_veer_left_simple");
                break;
            case NavigationEngine.DIRECTION_RIGHT:
                instruction = level == InteractionConfigManager.VerbosityLevel.VERBOSE
                        ? tr(lateralOffsetMeters >= 1.5f ? "tts_veer_right_strong" : "tts_veer_right")
                        : tr("tts_veer_right_simple");
                break;
            default:
                return;
        }

        speak(instruction, true, true, "nav_veer_" + directionToName(veerDirection).toLowerCase());
        Log.d(TAG, "veer_detected: direction=" + directionToName(veerDirection)
                + ", offset=" + lateralOffsetMeters + "m, text=" + instruction);
        sendLog(LOG_TAG_NAV_EVENT, "veer_detected",
                "direction=" + directionToName(veerDirection)
                        + ",offsetMeters=" + lateralOffsetMeters
                        + ",verbosity=" + level.name());
    }

    /**
     * 偏航恢复提示，仅使用听觉。
     */
    public void onVeerRecovered() {
        speak(tr("tts_veer_recovered"), false, false, "nav_veer_recovered");
        Log.d(TAG, "veer_recovered");
        sendLog(LOG_TAG_NAV_EVENT, "veer_recovered",
                "verbosity=" + configManager.getVerbosityLevel().name());
    }

    /**
     * 偏航后已经基于当前位置重新生成路线。
     */
    public void onNavigationReplanned() {
        speak(tr("tts_route_replanned"), false, true, "nav_route_replanned");
        Log.d(TAG, "navigation_replanned");
        sendLog(LOG_TAG_NAV_EVENT, "navigation_replanned",
                "verbosity=" + configManager.getVerbosityLevel().name());
    }

    /**
     * 兼容旧入口，便于旧调用逐步迁移。
     */
    public void onDirectionChanged(int direction, boolean urgent) {
        if (urgent) {
            onVeerRecovered();
        } else {
            onTurnInstruction(direction);
        }
    }

    /**
     * 到达提示：采用更明确的终点语音与到达触觉。
     */
    public void onArrived() {
        String text = configManager.getVerbosityLevel() == InteractionConfigManager.VerbosityLevel.VERBOSE
                ? tr("tts_arrived")
                : tr("tts_arrived_short");
        speak(text, true, true, "nav_arrived");
        clearObstacleAlert();
        maybePlayArrival("nav_arrived");
        Log.d(TAG, "arrived: text=" + text);
        sendLog(LOG_TAG_NAV_EVENT, "arrived", "verbosity=" + configManager.getVerbosityLevel().name());
    }

    public void onVerbosityChanged(InteractionConfigManager.VerbosityLevel level) {
        if (level == null) return;
        String text = level == InteractionConfigManager.VerbosityLevel.VERBOSE
                ? tr("tts_verbosity_verbose")
                : tr("tts_verbosity_essential");
        speak(text, false, false, "verbosity_changed");
        maybePlayConfirmation("verbosity_changed");
        sendLog(LOG_TAG_SETTINGS_CHANGE, "verbosity_changed", "value=" + level.name());
    }

    public void onHapticToggled(boolean enabled) {
        String text = tr(enabled ? "tts_haptic_on" : "tts_haptic_off");
        speak(text, false, false, "haptic_toggled");
        if (enabled) {
            maybePlayConfirmation("haptic_enabled");
        }
        sendLog(LOG_TAG_SETTINGS_CHANGE, "haptic_toggled", "enabled=" + enabled);
    }

    /**
     * 第二阶段：障碍物距离采样入口。
     * 只走触觉，不打断主导航语音。
     */
    public void onObstacleDistanceSample(float distanceMeters) {
        if (!isHapticAllowed() || configManager == null) return;
        if (distanceMeters <= 0f) return;

        InteractionConfigManager.HapticSensitivity sensitivity = configManager.getHapticSensitivity();
        float triggerDistance = configManager.getObstacleTriggerDistanceMeters();
        float clearDistance = configManager.getObstacleClearDistanceMeters();
        if (distanceMeters > clearDistance) {
            clearObstacleAlert();
            return;
        }
        if (distanceMeters > triggerDistance) {
            return;
        }

        try {
            hapticManager.playObstacleProximityPulse(distanceMeters, sensitivity);
            sendLog(LOG_TAG_HAPTIC_OUTPUT, "obstacle_proximity",
                    "distanceMeters=" + distanceMeters
                            + ",sensitivity=" + sensitivity.name()
                            + ",triggerDistance=" + triggerDistance
                            + ",clearDistance=" + clearDistance);
        } catch (Exception e) {
            Log.w(TAG, "playObstacleProximityPulse failed: " + e.getMessage());
        }
    }

    public void clearObstacleAlert() {
        if (hapticManager == null) return;
        try {
            hapticManager.stopObstacleAlert();
        } catch (Exception e) {
            Log.w(TAG, "stopObstacleAlert failed: " + e.getMessage());
        }
    }

    public void onSensitivityChanged(InteractionConfigManager.HapticSensitivity sensitivity) {
        if (sensitivity == null) return;
        String text;
        switch (sensitivity) {
            case HIGH:
                text = tr("tts_sensitivity_high");
                break;
            case LOW:
                text = tr("tts_sensitivity_low");
                break;
            case MEDIUM:
            default:
                text = tr("tts_sensitivity_medium");
                break;
        }
        speak(text, false, false, "sensitivity_changed");
        maybePlayConfirmation("sensitivity_changed");
        sendLog(LOG_TAG_SETTINGS_CHANGE, "haptic_sensitivity_changed", "value=" + sensitivity.name());
    }

    /**
     * 第一阶段的轻量状态播报。
     * 暂不计算剩余距离，也不依赖更复杂的语义查询模块。
     */
    public void announceCurrentStatus(boolean isLocalized, boolean isNavigating, String targetName) {
        final String text;
        if (!isLocalized) {
            text = tr("tts_status_not_localized");
        } else if (!isNavigating) {
            text = tr("tts_status_localized_ready");
        } else if (!TextUtils.isEmpty(targetName)) {
            if (configManager.getVerbosityLevel() == InteractionConfigManager.VerbosityLevel.VERBOSE) {
                text = tr("tts_status_navigating_to", targetName);
            } else {
                text = tr("tts_status_navigating");
            }
        } else {
            text = tr("tts_status_navigating");
        }

        speak(text, false, true, "status_query");
        sendLog(LOG_TAG_INTERACTION_STATE, "status_query",
                "localized=" + isLocalized + ",navigating=" + isNavigating + ",target=" + safeText(targetName));
    }

    /**
     * 重复上一条指令。
     */
    public void repeatLastInstruction() {
        if (TextUtils.isEmpty(lastInstructionText)) {
            speak(tr("tts_repeat_empty"), false, false, "repeat_empty");
            maybePlayError("repeat_empty");
            sendLog(LOG_TAG_INTERACTION_STATE, "repeat_last_instruction", "hasInstruction=false");
            return;
        }

        speak(lastInstructionText, true, false, "repeat_last_instruction");
        maybePlayConfirmation("repeat_last_instruction");
        sendLog(LOG_TAG_INTERACTION_STATE, "repeat_last_instruction", "hasInstruction=true,text=" + safeText(lastInstructionText));
    }

    /**
     * 可选辅助：用于 Activity 销毁时停止可能尚未结束的震动。
     */
    public void stopAllHaptics() {
        if (hapticManager != null) {
            hapticManager.stop();
        }
    }

    /**
     * 暴露最近一次指令文本，便于调试或 UI 展示。
     */
    public String getLastInstructionText() {
        return lastInstructionText;
    }

    public long getLastInstructionTimestamp() {
        return lastInstructionTimestamp;
    }

    // =========================
    // 内部实现
    // =========================

    private void speak(String text, boolean urgent, boolean rememberAsLastInstruction, String source) {
        if (ttsManager == null || TextUtils.isEmpty(text)) return;

        if (urgent) ttsManager.speakUrgent(text);
        else ttsManager.speak(text);

        if (rememberAsLastInstruction) {
            lastInstructionText = text;
            lastInstructionTimestamp = System.currentTimeMillis();
        }

        sendLog(LOG_TAG_AUDIO_OUTPUT, urgent ? "tts_urgent" : "tts",
                "text=" + safeText(text) + ",source=" + source + ",remembered=" + rememberAsLastInstruction);
    }

    private void maybePlayTurnHint(String direction) {
        if (!isHapticAllowed()) return;
        try {
            hapticManager.playTurnHint();
            sendLog(LOG_TAG_HAPTIC_OUTPUT, "turn_hint",
                    "direction=" + direction + ",sensitivity=" + configManager.getHapticSensitivity().name());
        } catch (Exception e) {
            Log.w(TAG, "playTurnHint failed: " + e.getMessage());
        }
    }

    private void maybePlayArrival(String source) {
        if (!isHapticAllowed()) return;
        try {
            hapticManager.playArrival();
            sendLog(LOG_TAG_HAPTIC_OUTPUT, "arrival", "source=" + source);
        } catch (Exception e) {
            Log.w(TAG, "playArrival failed: " + e.getMessage());
        }
    }

    private void maybePlayConfirmation(String source) {
        if (!isHapticAllowed()) return;
        try {
            hapticManager.playConfirmation();
            sendLog(LOG_TAG_HAPTIC_OUTPUT, "confirmation", "source=" + source);
        } catch (Exception e) {
            Log.w(TAG, "playConfirmation failed: " + e.getMessage());
        }
    }

    private void maybePlayError(String source) {
        if (!isHapticAllowed()) return;
        try {
            hapticManager.playError();
            sendLog(LOG_TAG_HAPTIC_OUTPUT, "error", "source=" + source);
        } catch (Exception e) {
            Log.w(TAG, "playError failed: " + e.getMessage());
        }
    }

    private boolean isHapticAllowed() {
        return hapticManager != null
                && configManager != null
                && configManager.isHapticEnabled()
                && hapticManager.isAvailable();
    }

    private void sendLog(String tag, String message, String details) {
        try {
            if (networkRepository != null) {
                networkRepository.sendLogToServer(tag, message, details);
            }
        } catch (Exception e) {
            Log.w(TAG, "sendLog failed: " + e.getMessage());
        }
    }

    private String directionToName(int direction) {
        switch (direction) {
            case 1:
                return "LEFT";
            case 2:
                return "RIGHT";
            case 0:
            default:
                return "STRAIGHT";
        }
    }

    private String safeText(String text) {
        if (text == null) return "null";
        return text.replace("\n", " ").replace("\r", " ");
    }

    /**
     * 预播报：不打断当前语音，不作为最高优先级。
     * 文案固定为“前方两米左转 / 右转”。
     */
    public void onPreTurnInstruction(int direction, float distanceMeters) {
        String instruction;
        switch (direction) {
            case NavigationEngine.DIRECTION_LEFT:
                instruction = tr("tts_pre_turn_left");
                break;
            case NavigationEngine.DIRECTION_RIGHT:
                instruction = tr("tts_pre_turn_right");
                break;
            default:
                return;
        }

        speak(instruction, false, false, "nav_pre_turn_" + directionToName(direction).toLowerCase());
        Log.d(TAG, "pre_turn_instruction: direction=" + directionToName(direction)
                + ", distanceMeters=" + distanceMeters + ", text=" + instruction);
        sendLog(LOG_TAG_NAV_EVENT, "pre_turn_instruction",
                "direction=" + directionToName(direction)
                        + ",distanceMeters=" + distanceMeters
                        + ",verbosity=" + configManager.getVerbosityLevel().name());
    }

    /**
     * 正式播报：优先级最高，必须抢占。
     * 文案固定为“左转 / 右转”。
     */
    public void onTurnNow(int direction) {
        String instruction;
        switch (direction) {
            case NavigationEngine.DIRECTION_LEFT:
                instruction = tr("tts_turn_left");
                break;
            case NavigationEngine.DIRECTION_RIGHT:
                instruction = tr("tts_turn_right");
                break;
            default:
                return;
        }

        speak(instruction, true, true, "nav_turn_now_" + directionToName(direction).toLowerCase());
        maybePlayTurnHint(directionToName(direction).toLowerCase());

        Log.d(TAG, "turn_now_instruction: direction=" + directionToName(direction) + ", text=" + instruction);
        sendLog(LOG_TAG_NAV_EVENT, "turn_now_instruction",
                "direction=" + directionToName(direction)
                        + ",verbosity=" + configManager.getVerbosityLevel().name()
                        + ",priority=highest");
    }

    /**
     * 朝向修正：低优先级，不和正式转弯混同。
     */
    public void onHeadingGuidance(int direction) {
        String instruction;
        switch (direction) {
            case NavigationEngine.DIRECTION_LEFT:
                instruction = tr("tts_heading_left");
                break;
            case NavigationEngine.DIRECTION_RIGHT:
                instruction = tr("tts_heading_right");
                break;
            case NavigationEngine.DIRECTION_STRAIGHT:
            default:
                instruction = tr("tts_heading_straight");
                break;
        }

        speak(instruction, false, false, "nav_heading_" + directionToName(direction).toLowerCase());
        Log.d(TAG, "heading_guidance: direction=" + directionToName(direction) + ", text=" + instruction);
        sendLog(LOG_TAG_NAV_EVENT, "heading_guidance",
                "direction=" + directionToName(direction)
                        + ",verbosity=" + configManager.getVerbosityLevel().name());
    }

    /**
     * 窄门候选已经通过连续帧确认后的安全提示。
     */
    public void onNarrowDoorDetected(float widthMeters, float distanceMeters) {
        if (widthMeters <= 0f || distanceMeters <= 0f) return;

        String instruction = tr("tts_narrow_door");
        speak(instruction, true, true, "narrow_door_detected");
        Log.d(TAG, "narrow_door_detected: width=" + widthMeters
                + "m, distance=" + distanceMeters + "m, text=" + instruction);
        sendLog(LOG_TAG_NAV_EVENT, "narrow_door_detected",
                "widthMeters=" + widthMeters
                        + ",distanceMeters=" + distanceMeters
                        + ",verbosity=" + configManager.getVerbosityLevel().name());
    }
}
