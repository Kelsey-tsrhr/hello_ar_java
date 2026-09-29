package com.google.ar.core.examples.java.nav.interaction;

import java.util.Locale;

/**
 * 负责把语音识别返回的文本转换成稳定的应用命令。
 *
 * <p>这里不执行任何副作用：不更新 UI、不播放语音，也不触发定位或导航。
 * Activity 只需要根据 {@link CommandType} 调用对应的业务入口。</p>
 */
public final class VoiceCommandParser {

    public enum CommandType {
        STATUS_QUERY,
        REPEAT,
        LOCALIZE,
        NAVIGATE,
        RESET,
        SETTINGS,
        UNKNOWN
    }

    public static final class ParsedCommand {
        public final CommandType type;
        public final String rawText;
        public final String destination;

        private ParsedCommand(CommandType type, String rawText, String destination) {
            this.type = type;
            this.rawText = rawText;
            this.destination = destination;
        }

        public static ParsedCommand of(CommandType type, String rawText) {
            return new ParsedCommand(type, rawText, null);
        }

        public static ParsedCommand navigate(String rawText, String destination) {
            return new ParsedCommand(CommandType.NAVIGATE, rawText, destination);
        }
    }

    public ParsedCommand parse(String rawText) {
        String spoken = rawText == null ? "" : rawText.trim();
        if (spoken.isEmpty()) {
            return ParsedCommand.of(CommandType.UNKNOWN, spoken);
        }

        if (containsAnyPhrase(spoken,
                "我在哪",
                "现在在哪",
                "当前状态",
                "导航状态",
                "where am i",
                "where am i now",
                "current status",
                "navigation status")) {
            return ParsedCommand.of(CommandType.STATUS_QUERY, spoken);
        }

        if (containsAnyPhrase(spoken,
                "重复",
                "再说一遍",
                "再播报",
                "重播",
                "repeat",
                "say again",
                "repeat that")) {
            return ParsedCommand.of(CommandType.REPEAT, spoken);
        }

        if (containsAnyPhrase(spoken, "定位", "localize", "locate")) {
            return ParsedCommand.of(CommandType.LOCALIZE, spoken);
        }

        if (isNavigationCommand(spoken)) {
            return ParsedCommand.navigate(spoken, extractNavigationDestination(spoken));
        }

        if (containsAnyPhrase(spoken, "重置", "reset", "relocalize")) {
            return ParsedCommand.of(CommandType.RESET, spoken);
        }

        if (containsAnyPhrase(spoken, "设置", "settings", "open settings")) {
            return ParsedCommand.of(CommandType.SETTINGS, spoken);
        }

        return ParsedCommand.of(CommandType.UNKNOWN, spoken);
    }

    private boolean isNavigationCommand(String spoken) {
        return containsAnyPhrase(spoken,
                "去", "导航到", "前往", "go to", "navigate to", "take me to");
    }

    private String extractNavigationDestination(String spoken) {
        String trimmed = spoken == null ? "" : spoken.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        String[] englishPrefixes = {"navigate to", "take me to", "go to"};
        for (String prefix : englishPrefixes) {
            int index = lower.lastIndexOf(prefix);
            if (index >= 0) {
                return trimmed.substring(index + prefix.length()).trim();
            }
        }

        return trimmed.replaceFirst("^.*(去|导航到|前往)", "").trim();
    }

    private boolean containsAnyPhrase(String spoken, String... phrases) {
        if (spoken == null || phrases == null) {
            return false;
        }

        String normalized = spoken.toLowerCase(Locale.ROOT);
        for (String phrase : phrases) {
            if (phrase != null
                    && !phrase.isEmpty()
                    && normalized.contains(phrase.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
