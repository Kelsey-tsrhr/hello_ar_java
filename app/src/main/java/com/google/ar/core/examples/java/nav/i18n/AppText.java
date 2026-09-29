package com.google.ar.core.examples.java.nav.i18n;

import com.google.ar.core.examples.java.nav.managers.InteractionConfigManager;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AppText {

    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    static {
        put("btn_localize", "定位", "Locate");
        put("btn_destination", "目的地", "Destination");
        put("btn_voice", "语音", "Voice");
        put("btn_narrow_door_test", "窄门测试", "Narrow Door Test");
        put("btn_narrow_door_test_stop", "停止窄门测试", "Stop Door Test");
        put("btn_show_path_on", "隐藏路径", "Hide Path");
        put("btn_show_path_off", "显示路径", "Show Path");
        put("btn_language_switch", "EN", "中文");
        put("a11y_btn_localize", "开始定位", "Start localization");
        put("a11y_btn_destination", "选择目的地", "Choose destination");
        put("a11y_btn_voice", "开始语音输入", "Start voice input");
        put("a11y_btn_narrow_door_test", "窄门实拍测试", "Live narrow door test");
        put("a11y_btn_narrow_door_test_stop", "停止窄门实拍测试", "Stop live narrow door test");
        put("a11y_btn_show_path", "隐藏路径节点", "Hide path nodes");
        put("a11y_btn_hide_path", "显示路径节点", "Show path nodes");
        put("a11y_btn_language", "切换语言", "Switch language");
        put("a11y_btn_settings", "打开设置", "Open settings");
        put("a11y_state_on", "当前开启", "currently on");
        put("a11y_state_off", "当前关闭", "currently off");
        put("a11y_double_tap_hint", "双击执行", "double tap to activate");

        put("status_waiting_localization", "等待定位...", "Waiting for localization...");
        put("status_start_voice", "正在启动语音识别...", "Starting voice recognition...");
        put("status_ready", "已准备，请说话", "Ready, please speak.");
        put("status_listening", "检测到说话，正在录音...", "Voice detected, recording...");
        put("status_recognizing", "录音结束，正在识别...", "Recording finished, recognizing...");
        put("status_recognized", "识别成功：%s", "Recognized: %s");
        put("status_error", "识别错误：%s", "Recognition error: %s");
        put("status_feature_unmatched", "识别成功，但未匹配功能：%s", "Recognized, but no function matched: %s");
        put("status_obstacle_test_stopped", "已停止障碍提醒测试", "Obstacle alert test stopped.");
        put("status_obstacle_test_near", "已触发近距离障碍测试", "Near obstacle test triggered.");
        put("status_obstacle_test_mid", "已触发中距离障碍测试", "Mid-range obstacle test triggered.");
        put("status_obstacle_test_far", "已触发远距离障碍测试", "Far obstacle test triggered.");
        put("status_destination_recognized", "已识别目的地：%1$s（归一化：%2$s）", "Destination recognized: %1$s (normalized: %2$s)");
        put("status_destination_not_found", "未找到目的地：%s", "Destination not found: %s");
        put("status_collecting_image", "正在采集图像...", "Capturing image...");
        put("status_uploading_scene_map", "正在上传并请求场景 %d 的地图数据...", "Uploading image and requesting map data for scene %d...");
        put("status_scene_localization_failed", "场景 %d 定位失败或返回空地图", "Scene %d localization failed or returned no map.");
        put("status_network_error_scene", "网络错误（场景 %d）", "Network error (scene %d).");
        put("status_localization_empty", "定位结果为空", "Localization result is empty.");
        put("status_localization_success", "定位成功！", "Localization successful.");
        put("status_capture_failed", "图像采集失败", "Image capture failed.");
        put("status_reset", "已重置", "Reset completed.");
        put("status_arcore_ready", "ARCore 已就绪", "ARCore is ready.");
        put("status_arcore_ready_no_depth", "ARCore 已就绪（Depth 不支持，本地避障测距不可用）", "ARCore is ready. Depth is not supported, so local obstacle ranging is unavailable.");
        put("status_no_audio_permission", "没有录音权限", "Microphone permission is missing.");
        put("status_camera_permission_granted", "相机权限已授予，正在初始化 ARCore...", "Camera permission granted. Initializing ARCore...");
        put("status_checking_arcore", "正在检查 ARCore 可用性，请稍候...", "Checking ARCore availability...");
        put("status_requesting_arcore_install", "正在请求安装或更新 Google Play Services for AR，请按提示完成后返回", "Requesting installation or update for Google Play Services for AR. Follow the prompt and return.");
        put("status_scene_switched", "已切换到场景 %d，正在重新初始化...", "Switched to scene %d. Reinitializing...");

        put("toast_clean_on", "已进入截图模式，长按画面恢复界面", "Screenshot mode enabled. Long press the view to restore the UI.");
        put("toast_clean_off", "已恢复界面显示", "UI restored.");
        put("toast_debug_on", "调试开启", "Debug enabled");
        put("toast_debug_off", "调试关闭", "Debug disabled");
        put("toast_auto_reloc_on", "已开启自动重定位", "Auto relocalization enabled");
        put("toast_auto_reloc_off", "已关闭自动重定位", "Auto relocalization disabled");
        put("toast_scene_input_empty", "请输入场景编号", "Please enter a scene ID.");
        put("toast_scene_format_invalid", "场景编号格式不正确", "Invalid scene ID format.");
        put("toast_scene_min", "场景编号必须大于等于 1", "Scene ID must be at least 1.");
        put("toast_scene_same", "当前已经是场景 %d", "Already on scene %d.");
        put("toast_scene_switched", "已切换到场景 %d", "Switched to scene %d.");

        put("dialog_settings_title", "设置", "Settings");
        put("dialog_about_title", "关于", "About");
        put("dialog_about_message", "室内 AR 导航 v1.0", "Indoor AR Navigation v1.0");
        put("dialog_ok", "确定", "OK");
        put("dialog_close", "关闭", "Close");
        put("dialog_cancel", "取消", "Cancel");
        put("dialog_visual_mode_title", "选择视觉模式", "Choose Visual Mode");
        put("dialog_interaction_title", "交互反馈设置", "Interaction Feedback");
        put("dialog_navigation_features_title", "导航辅助设置", "Navigation Assistance");
        put("dialog_accessibility_mode_title", "无障碍模式", "Accessibility Mode");
        put("dialog_sensitivity_title", "选择触觉灵敏度", "Choose Haptic Sensitivity");
        put("dialog_destination_title", "选择目的地", "Choose Destination");
        put("dialog_scene_title", "切换测试场景", "Switch Test Scene");
        put("dialog_scene_message", "请输入场景编号。将请求 /api/v1/localization/query_relative/{sceneId}，并完整重新初始化。", "Enter a scene ID. The app will request /api/v1/localization/query_relative/{sceneId} and fully reinitialize.");
        put("dialog_scene_switch", "切换", "Switch");
        put("input_scene_hint", "请输入场景编号，例如 1、2、3", "Enter a scene ID, for example 1, 2, or 3.");

        put("settings_interaction_feedback", "交互反馈设置", "Interaction Feedback");
        put("settings_navigation_features", "导航辅助设置（窄门 / 楼道）", "Navigation Assistance (Door / Corridor)");
        put("settings_accessibility_mode_on", "无障碍模式（开启）", "Accessibility Mode (On)");
        put("settings_accessibility_mode_off", "无障碍模式（关闭）", "Accessibility Mode (Off)");
        put("settings_visual_mode", "视觉辅助模式", "Visual Assistance");
        put("settings_clean_on", "进入截图模式（隐藏 UI）", "Enter Screenshot Mode (Hide UI)");
        put("settings_clean_off", "退出截图模式", "Exit Screenshot Mode");
        put("settings_announce_status", "播报当前状态", "Speak Current Status");
        put("settings_debug_on", "开启调试信息", "Show Debug Info");
        put("settings_debug_off", "关闭调试信息", "Hide Debug Info");
        put("settings_auto_reloc_on", "开启自动重定位", "Enable Auto Relocalization");
        put("settings_auto_reloc_off", "关闭自动重定位", "Disable Auto Relocalization");
        put("settings_scene_switch", "切换测试场景（当前：%d）", "Switch Test Scene (Current: %d)");
        put("settings_relocalize", "重新定位（重置）", "Relocalize (Reset)");
        put("settings_about", "关于", "About");
        put("settings_exit", "退出", "Exit");

        put("navigation_feature_narrow_door", "窄门识别", "Narrow Door Detection");
        put("navigation_feature_corridor_mode", "楼道低频提醒模式", "Corridor Low-Frequency Guidance");
        put("navigation_feature_narrow_door_hint", "需要同时看到左右门框，不要求上下门框。", "Requires both side door frames; top and bottom frames are not required.");
        put("navigation_feature_corridor_hint", "沿路线方向正确时减少重复方向提醒。", "Reduces repeated direction reminders when following the route.");
        put("dialog_save", "保存", "Save");
        put("accessibility_mode_switch", "无障碍模式", "Accessibility Mode");
        put("accessibility_mode_hint", "单击朗读按钮，双击执行操作；支持屏幕朗读。", "Single tap to hear a button, double tap to activate it; supports screen readers.");
        put("toast_accessibility_mode_on", "已开启无障碍模式", "Accessibility mode enabled");
        put("toast_accessibility_mode_off", "已关闭无障碍模式", "Accessibility mode disabled");
        put("tts_accessibility_mode_on", "已开启无障碍模式，单击朗读，双击执行", "Accessibility mode enabled. Single tap to hear, double tap to activate.");
        put("tts_accessibility_mode_off", "已关闭无障碍模式", "Accessibility mode disabled.");
        put("toast_narrow_door_on", "已开启窄门识别", "Narrow door detection enabled");
        put("toast_narrow_door_off", "已关闭窄门识别", "Narrow door detection disabled");
        put("toast_corridor_mode_on", "已开启楼道低频提醒模式", "Corridor low-frequency guidance enabled");
        put("toast_corridor_mode_off", "已关闭楼道低频提醒模式", "Corridor low-frequency guidance disabled");

        put("visual_mode_default", "默认模式", "Default Mode");
        put("visual_mode_high_contrast", "高对比度", "High Contrast");
        put("visual_mode_color_assist", "色彩辅助", "Color Assist");

        put("interaction_verbosity_to_essential", "切换到简略模式", "Switch to Essential Mode");
        put("interaction_verbosity_to_verbose", "切换到详尽模式", "Switch to Verbose Mode");
        put("interaction_haptic_disable", "关闭触觉反馈", "Turn Off Haptic Feedback");
        put("interaction_haptic_enable", "开启触觉反馈", "Turn On Haptic Feedback");
        put("interaction_repeat_disable", "关闭长按重复指令", "Turn Off Long-Press Repeat");
        put("interaction_repeat_enable", "开启长按重复指令", "Turn On Long-Press Repeat");
        put("interaction_sensitivity", "触觉灵敏度：%s", "Haptic Sensitivity: %s");
        put("interaction_restore_defaults", "恢复默认交互设置", "Restore Default Interaction Settings");

        put("sensitivity_high", "高", "High");
        put("sensitivity_medium", "中", "Medium");
        put("sensitivity_low", "低", "Low");

        put("tts_fetch_map_data", "开始获取地图数据", "Fetching map data.");
        put("tts_unknown_voice", "已识别到指令，但当前未匹配到功能", "Command recognized, but no function is matched.");
        put("tts_localization_requested", "开始定位", "Starting localization.");
        put("tts_localization_success", "定位成功", "Localization successful.");
        put("tts_relocalized", "已重置", "Reset completed.");
        put("tts_repeat_on", "已开启长按重复指令", "Long-press repeat enabled.");
        put("tts_repeat_off", "已关闭长按重复指令", "Long-press repeat disabled.");
        put("tts_mode_changed_default", "模式已切换", "Mode switched.");
        put("tts_mode_switched", "已切换到%s", "Switched to %s.");
        put("tts_default_target", "目的地", "destination");
        put("tts_nav_start", "开始导航，前往%s", "Navigation started to %s.");
        put("tts_nav_start_simple", "开始导航", "Navigation started.");
        put("tts_veer_left_strong", "你偏左了，请向右回到路径", "You are drifting left. Move right to return to the path.");
        put("tts_veer_left", "你偏左了，请向右微调", "You are drifting left. Adjust right.");
        put("tts_veer_left_simple", "偏左", "Veer left.");
        put("tts_veer_right_strong", "你偏右了，请向左回到路径", "You are drifting right. Move left to return to the path.");
        put("tts_veer_right", "你偏右了，请向左微调", "You are drifting right. Adjust left.");
        put("tts_veer_right_simple", "偏右", "Veer right.");
        put("tts_veer_recovered", "已回到路线中央", "Back on the route.");
        put("tts_route_replanned", "正在重新规划路线", "Route replanned from your current position.");
        put("tts_arrived", "已到达目的地", "You have arrived at the destination.");
        put("tts_arrived_short", "已到达", "Arrived.");
        put("tts_verbosity_verbose", "已切换到详尽模式", "Switched to verbose mode.");
        put("tts_verbosity_essential", "已切换到简略模式", "Switched to essential mode.");
        put("tts_haptic_on", "已开启触觉反馈", "Haptic feedback enabled.");
        put("tts_haptic_off", "已关闭触觉反馈", "Haptic feedback disabled.");
        put("tts_sensitivity_high", "已切换到高灵敏度，避障预警会更早触发", "Switched to high sensitivity. Obstacle warnings will trigger earlier.");
        put("tts_sensitivity_medium", "已切换到中灵敏度", "Switched to medium sensitivity.");
        put("tts_sensitivity_low", "已切换到低灵敏度，避障预警会在更近时触发", "Switched to low sensitivity. Obstacle warnings will trigger later.");
        put("tts_status_not_localized", "尚未定位", "Not localized yet.");
        put("tts_status_localized_ready", "已定位，可选择目的地", "Localized. You can choose a destination.");
        put("tts_status_navigating", "正在导航", "Navigating.");
        put("tts_status_navigating_to", "正在前往%s", "Navigating to %s.");
        put("tts_repeat_empty", "当前没有可重复的指令", "No instruction to repeat.");
        put("tts_turn_left", "左转", "Turn left.");
        put("tts_turn_right", "右转", "Turn right.");
        put("tts_pre_turn_left", "前方两米左转", "Turn left in two meters.");
        put("tts_pre_turn_right", "前方两米右转", "Turn right in two meters.");
        put("tts_heading_left", "向左调整方向", "Adjust slightly left.");
        put("tts_heading_right", "向右调整方向", "Adjust slightly right.");
        put("tts_heading_straight", "请继续直行", "Please continue straight.");
        put("tts_narrow_door", "前方有窄门，请小心行走", "Narrow doorway ahead. Please walk carefully.");
        put("tts_language_switched_zh", "已切换到中文", "已切换到中文");
        put("tts_language_switched_en", "Switched to English", "Switched to English");
        put("tts_need_localization_first", "请先完成定位", "Please localize first.");
        put("tts_repeat_destination", "请再说一遍目的地", "Please say the destination again.");
        put("tts_no_route_now", "当前无法找到可导航路径", "No navigable path is available right now.");
        put("tts_path_not_found", "未能生成有效路径", "Unable to generate a valid path.");
        put("tts_restore_interaction_defaults", "已恢复默认交互设置", "Default interaction settings restored.");

        put("feature_repeat_button_disabled", "长按重复指令未开启", "Long-press repeat is disabled.");

        put("arcore_no_camera_permission", "缺少相机权限，请允许相机权限后重试", "Camera permission is missing. Please allow it and try again.");
        put("arcore_unsupported_device", "当前设备或系统 ROM 不支持 ARCore", "This device or system ROM does not support ARCore.");
        put("arcore_unknown_support", "无法确定 ARCore 支持状态（未知错误）", "Unable to determine ARCore support status (unknown error).");
        put("arcore_support_timeout", "ARCore 支持检查超时，请重试", "ARCore support check timed out. Please try again.");
        put("arcore_not_installed", "未安装 Google Play Services for AR", "Google Play Services for AR is not installed.");
        put("arcore_apk_too_old", "Google Play Services for AR 版本过旧", "Google Play Services for AR is out of date.");
        put("arcore_sdk_too_old", "当前 App 使用的 ARCore SDK 版本过旧", "The ARCore SDK used by this app is out of date.");
        put("arcore_device_not_compatible", "当前设备、系统版本或 ROM 与 ARCore 不兼容", "This device, system version, or ROM is not compatible with ARCore.");
        put("arcore_user_declined", "你取消了 ARCore 的安装或更新", "You cancelled the ARCore installation or update.");
        put("arcore_security", "系统拒绝访问相机，请检查相机权限", "The system denied camera access. Please check camera permission.");
        put("arcore_camera_not_available", "相机暂时不可用，可能被其他应用占用", "The camera is temporarily unavailable and may be in use by another app.");
        put("arcore_start_failed", "启动失败：%s", "Startup failed: %s");
        put("arcore_error_prefix", "ARCore 错误：%s", "ARCore error: %s");

        put("report_not_localized", "尚未定位，无法播报当前位置。", "Not localized yet. I cannot report your current position.");
        put("report_nearby_intro", "你周围最近的门牌有：", "Nearby doorplates: ");
        put("report_separator", "；", "; ");
        put("report_nearby_item", "%1$s 在%2$s，约 %3$s", "%1$s is %2$s, about %3$s away");
        put("report_end", "。", ".");
        put("report_no_nearby", "已定位，但附近暂未找到可播报的门牌节点。", "Localized, but there are no nearby doorplates to report.");
        put("report_current_target", "当前目的地是 %s。", "Current destination is %s.");
        put("report_remaining_distance", "距离目的地约 %s。", "Remaining distance is about %s.");
        put("report_next_turn", "先直行约 %s，然后%s。", "Go straight for %s, then turn %s.");
        put("report_keep_straight", "走法是基本保持当前方向直行即可到达。", "Keep your current direction and continue straight to arrive.");
        put("report_path_unavailable", "但当前暂时无法计算到目的地的路径。", "A path to the destination cannot be calculated right now.");
        put("report_turn_left", "左转", "left");
        put("report_turn_right", "右转", "right");
        put("direction_nearby", "附近", "nearby");
        put("direction_back", "后方", "behind you");
        put("direction_left_back", "左后方", "back-left");
        put("direction_right_back", "右后方", "back-right");
        put("direction_front", "前方", "in front of you");
        put("direction_left_front", "左前方", "front-left");
        put("direction_right_front", "右前方", "front-right");
        put("meters_unknown", "未知", "unknown");
    }

    private AppText() {}

    public static String text(InteractionConfigManager.AppLanguage lang, String key, Object... args) {
        Map<String, String> table = lang == InteractionConfigManager.AppLanguage.EN ? EN : ZH;
        String raw = table.get(key);
        if (raw == null) {
            return key;
        }
        if (args == null || args.length == 0) {
            return raw;
        }
        Locale locale = lang == InteractionConfigManager.AppLanguage.EN ? Locale.US : Locale.CHINA;
        return String.format(locale, raw, args);
    }

    private static void put(String key, String zh, String en) {
        ZH.put(key, zh);
        EN.put(key, en);
    }
}
