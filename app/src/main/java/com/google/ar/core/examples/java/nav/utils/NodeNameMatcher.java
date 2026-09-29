package com.google.ar.core.examples.java.nav.utils;

import com.google.ar.core.examples.java.nav.api.model.Node;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 目的地名称匹配工具。
 * 目标：
 * 1. 对节点 ID 与语音识别文本做统一归一化；
 * 2. 解决大小写、中文数字、幺/一、字母读法、空格与标点差异；
 * 3. 优先匹配 doorplate 节点，必要时回退到其他节点。
 */
public class NodeNameMatcher {

    public static class MatchResult {
        public final Node node;
        public final int score;
        public final String normalizedInput;
        public final String normalizedNodeId;

        public MatchResult(Node node, int score, String normalizedInput, String normalizedNodeId) {
            this.node = node;
            this.score = score;
            this.normalizedInput = normalizedInput;
            this.normalizedNodeId = normalizedNodeId;
        }
    }

    private static final Map<Character, Character> DIGIT_CHAR_MAP = new HashMap<>();
    private static final Set<Character> CHINESE_NUM_CHARS = new HashSet<>();
    private static final Map<String, String> SPOKEN_LETTER_MAP = new HashMap<>();
    private static final Map<String, String> ENGLISH_DIGIT_WORD_MAP = new HashMap<>();
    private static final Map<String, Integer> ENGLISH_SMALL_NUMBER_MAP = new HashMap<>();
    private static final Map<String, Integer> ENGLISH_TENS_NUMBER_MAP = new HashMap<>();
    private static final Set<String> ENGLISH_NUMBER_TOKENS = new HashSet<>();
    private static final String NOISE_WORDS_REGEX =
            "(开始导航前往|开始导航到|导航前往|导航到|前往|我要去|我去|带我去|去往|去|到|房间|教室|室|门牌|号码|号房|房号|房|号)";
    private static final String ENGLISH_NOISE_WORDS_REGEX =
            "\\b(?:ROOM|DOORPLATE|DOOR|NUMBER|NO|DESTINATION)\\b";

    static {
        putDigit('零', '0'); putDigit('〇', '0'); putDigit('○', '0');
        putDigit('一', '1'); putDigit('壹', '1'); putDigit('幺', '1');
        putDigit('二', '2'); putDigit('两', '2'); putDigit('俩', '2'); putDigit('贰', '2');
        putDigit('三', '3'); putDigit('叁', '3');
        putDigit('四', '4'); putDigit('肆', '4');
        putDigit('五', '5'); putDigit('伍', '5');
        putDigit('六', '6'); putDigit('陆', '6');
        putDigit('七', '7'); putDigit('柒', '7');
        putDigit('八', '8'); putDigit('捌', '8');
        putDigit('九', '9'); putDigit('玖', '9');
        CHINESE_NUM_CHARS.add('十'); CHINESE_NUM_CHARS.add('拾');
        CHINESE_NUM_CHARS.add('百'); CHINESE_NUM_CHARS.add('佰');
        CHINESE_NUM_CHARS.add('千'); CHINESE_NUM_CHARS.add('仟');

        // 常见字母读法，仅在目的地词槽中使用。
        putLetter("诶", "A"); putLetter("哎", "A"); putLetter("唉", "A"); putLetter("欸", "A");
        putLetter("比", "B"); putLetter("毕", "B"); putLetter("币", "B"); putLetter("逼", "B");
        putLetter("西", "C"); putLetter("希", "C"); putLetter("溪", "C");
        putLetter("迪", "D"); putLetter("弟", "D"); putLetter("滴", "D");
        putLetter("伊", "E"); putLetter("衣", "E");
        putLetter("艾弗", "F"); putLetter("诶弗", "F");
        putLetter("记", "G"); putLetter("吉", "G");
        putLetter("艾尺", "H");
        putLetter("艾", "I");
        putLetter("杰", "J");
        putLetter("开", "K");
        putLetter("艾勒", "L");
        putLetter("艾姆", "M");
        putLetter("恩", "N");
        putLetter("欧", "O");
        putLetter("屁", "P");
        putLetter("Q", "Q");
        putLetter("啊Q", "Q");
        putLetter("艾儿", "R");
        putLetter("艾斯", "S");
        putLetter("提", "T");
        putLetter("优", "U");
        putLetter("微", "V");
        putLetter("达不溜", "W");
        putLetter("双U", "W");
        putLetter("叉", "X");
        putLetter("歪", "Y");
        putLetter("贼", "Z");

        putEnglishDigit("ZERO", "0");
        putEnglishDigit("OH", "0");
        putEnglishDigit("O", "0");
        putEnglishDigit("ONE", "1");
        putEnglishDigit("TWO", "2");
        putEnglishDigit("THREE", "3");
        putEnglishDigit("FOUR", "4");
        putEnglishDigit("FIVE", "5");
        putEnglishDigit("SIX", "6");
        putEnglishDigit("SEVEN", "7");
        putEnglishDigit("EIGHT", "8");
        putEnglishDigit("NINE", "9");

        putEnglishSmall("ZERO", 0);
        putEnglishSmall("ONE", 1);
        putEnglishSmall("TWO", 2);
        putEnglishSmall("THREE", 3);
        putEnglishSmall("FOUR", 4);
        putEnglishSmall("FIVE", 5);
        putEnglishSmall("SIX", 6);
        putEnglishSmall("SEVEN", 7);
        putEnglishSmall("EIGHT", 8);
        putEnglishSmall("NINE", 9);
        putEnglishSmall("TEN", 10);
        putEnglishSmall("ELEVEN", 11);
        putEnglishSmall("TWELVE", 12);
        putEnglishSmall("THIRTEEN", 13);
        putEnglishSmall("FOURTEEN", 14);
        putEnglishSmall("FIFTEEN", 15);
        putEnglishSmall("SIXTEEN", 16);
        putEnglishSmall("SEVENTEEN", 17);
        putEnglishSmall("EIGHTEEN", 18);
        putEnglishSmall("NINETEEN", 19);

        putEnglishTens("TWENTY", 20);
        putEnglishTens("THIRTY", 30);
        putEnglishTens("FORTY", 40);
        putEnglishTens("FIFTY", 50);
        putEnglishTens("SIXTY", 60);
        putEnglishTens("SEVENTY", 70);
        putEnglishTens("EIGHTY", 80);
        putEnglishTens("NINETY", 90);

        ENGLISH_NUMBER_TOKENS.addAll(Arrays.asList("AND", "HUNDRED", "THOUSAND"));
    }

    private static void putDigit(char src, char dest) {
        DIGIT_CHAR_MAP.put(src, dest);
        CHINESE_NUM_CHARS.add(src);
    }

    private static void putLetter(String spoken, String letter) {
        SPOKEN_LETTER_MAP.put(spoken, letter);
    }

    private static void putEnglishDigit(String word, String digit) {
        ENGLISH_DIGIT_WORD_MAP.put(word, digit);
        ENGLISH_NUMBER_TOKENS.add(word);
    }

    private static void putEnglishSmall(String word, int value) {
        ENGLISH_SMALL_NUMBER_MAP.put(word, value);
        ENGLISH_NUMBER_TOKENS.add(word);
    }

    private static void putEnglishTens(String word, int value) {
        ENGLISH_TENS_NUMBER_MAP.put(word, value);
        ENGLISH_NUMBER_TOKENS.add(word);
    }

    public static MatchResult findBestMatch(String spokenDest, List<Node> preferredCandidates, List<Node> fallbackCandidates) {
        MatchResult preferred = findBestMatch(spokenDest, preferredCandidates);
        if (preferred != null) return preferred;
        return findBestMatch(spokenDest, fallbackCandidates);
    }

    public static MatchResult findBestMatch(String spokenDest, List<Node> candidates) {
        if (spokenDest == null || candidates == null || candidates.isEmpty()) return null;

        String normalizedInput = normalize(spokenDest);
        if (normalizedInput.isEmpty()) return null;

        MatchResult best = null;
        for (Node node : candidates) {
            if (node == null || node.id == null || node.id.trim().isEmpty()) continue;
            String normalizedNodeId = normalize(node.id);
            if (normalizedNodeId.isEmpty()) continue;

            int score = scoreMatch(normalizedInput, normalizedNodeId);
            if (score <= 0) continue;

            if (best == null || score > best.score ||
                    (score == best.score && normalizedNodeId.length() > best.normalizedNodeId.length())) {
                best = new MatchResult(node, score, normalizedInput, normalizedNodeId);
            }
        }

        if (best == null) return null;
        // 分数太低时视为不可靠，避免误导航。
        return best.score >= 70 ? best : null;
    }

    public static String normalize(String raw) {
        if (raw == null) return "";

        String s = toHalfWidth(raw).toUpperCase(Locale.ROOT);
        s = s.replaceAll(NOISE_WORDS_REGEX, " ");
        s = s.replace("负一", "-1");
        s = s.replaceAll("[\\-_/.,，。；;：:（）()【】\\[\\]{}]+", " ");

        // 先替换多字符字母读法，避免被逐字拆坏。
        List<String> spokenKeys = new ArrayList<>(SPOKEN_LETTER_MAP.keySet());
        Collections.sort(spokenKeys, (a, b) -> Integer.compare(b.length(), a.length()));
        for (String key : spokenKeys) {
            s = s.replace(key.toUpperCase(Locale.ROOT), SPOKEN_LETTER_MAP.get(key));
        }

        // 把中文数字片段统一转成阿拉伯数字。
        s = normalizeChineseNumberFragments(s);
        s = normalizeEnglishNumberFragments(s);
        s = s.replaceAll(ENGLISH_NOISE_WORDS_REGEX, " ");

        // 单字符数字映射（例如“幺”）。
        StringBuilder mapped = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (DIGIT_CHAR_MAP.containsKey(c)) {
                mapped.append(DIGIT_CHAR_MAP.get(c));
            } else {
                mapped.append(c);
            }
        }
        s = mapped.toString();

        // 清理分隔符与噪声。
        s = s.replaceAll("[\\s\\-_/.,，。；;：:（）()【】\\[\\]{}]+", "");
        s = s.replaceAll("[^A-Z0-9]", "");
        return s;
    }

    private static String normalizeEnglishNumberFragments(String input) {
        if (input == null) return "";

        String trimmed = input.trim();
        if (trimmed.isEmpty()) return trimmed;

        String[] tokens = trimmed.split("\\s+");
        StringBuilder out = new StringBuilder();
        int index = 0;
        while (index < tokens.length) {
            String token = tokens[index];
            if (!canStartEnglishNumberPhrase(token)) {
                appendToken(out, token);
                index++;
                continue;
            }

            int phraseEnd = index;
            while (phraseEnd < tokens.length && isEnglishNumberToken(tokens[phraseEnd])) {
                phraseEnd++;
            }

            ParseCandidate candidate = decodeEnglishNumberTokens(tokens, index, phraseEnd);
            if (candidate != null && !candidate.digits.isEmpty()) {
                appendToken(out, candidate.digits);
                index = phraseEnd;
                continue;
            }

            appendToken(out, token);
            index++;
        }
        return out.toString();
    }

    private static void appendToken(StringBuilder out, String token) {
        if (token == null || token.isEmpty()) return;
        if (out.length() > 0) {
            out.append(' ');
        }
        out.append(token);
    }

    private static boolean canStartEnglishNumberPhrase(String token) {
        return ENGLISH_DIGIT_WORD_MAP.containsKey(token)
                || ENGLISH_SMALL_NUMBER_MAP.containsKey(token)
                || ENGLISH_TENS_NUMBER_MAP.containsKey(token);
    }

    private static boolean isEnglishNumberToken(String token) {
        return ENGLISH_NUMBER_TOKENS.contains(token);
    }

    private static ParseCandidate decodeEnglishNumberTokens(String[] tokens, int start, int end) {
        ParseCandidate[] memo = new ParseCandidate[end + 1];
        boolean[] visited = new boolean[end + 1];
        return decodeEnglishNumberTokens(tokens, start, end, memo, visited);
    }

    private static ParseCandidate decodeEnglishNumberTokens(String[] tokens,
                                                            int index,
                                                            int end,
                                                            ParseCandidate[] memo,
                                                            boolean[] visited) {
        if (visited[index]) {
            return memo[index];
        }
        visited[index] = true;

        if (index == end) {
            memo[index] = new ParseCandidate("", 0);
            return memo[index];
        }

        ParseCandidate best = null;
        for (int next = index + 1; next <= end; next++) {
            String digitSequence = parseEnglishDigitSequence(tokens, index, next);
            best = chooseBetterCandidate(
                    best,
                    combineCandidates(digitSequence, decodeEnglishNumberTokens(tokens, next, end, memo, visited))
            );

            String cardinal = parseEnglishCardinal(tokens, index, next);
            best = chooseBetterCandidate(
                    best,
                    combineCandidates(cardinal, decodeEnglishNumberTokens(tokens, next, end, memo, visited))
            );
        }

        memo[index] = best;
        return best;
    }

    private static ParseCandidate combineCandidates(String prefixDigits, ParseCandidate suffix) {
        if (prefixDigits == null || suffix == null) return null;
        return new ParseCandidate(prefixDigits + suffix.digits, suffix.chunkCount + 1);
    }

    private static ParseCandidate chooseBetterCandidate(ParseCandidate current, ParseCandidate incoming) {
        if (incoming == null) return current;
        if (current == null) return incoming;
        if (incoming.chunkCount != current.chunkCount) {
            return incoming.chunkCount < current.chunkCount ? incoming : current;
        }
        if (incoming.digits.length() != current.digits.length()) {
            return incoming.digits.length() < current.digits.length() ? incoming : current;
        }
        return incoming.digits.compareTo(current.digits) < 0 ? incoming : current;
    }

    private static String parseEnglishDigitSequence(String[] tokens, int start, int end) {
        if (start >= end) return null;

        StringBuilder digits = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            String digit = ENGLISH_DIGIT_WORD_MAP.get(tokens[i]);
            if (digit == null) {
                return null;
            }
            digits.append(digit);
        }
        return digits.toString();
    }

    private static String parseEnglishCardinal(String[] tokens, int start, int end) {
        if (start >= end) return null;
        if ("AND".equals(tokens[start]) || "AND".equals(tokens[end - 1])) return null;
        Integer value = parseEnglishCardinalValue(tokens, start, end);
        return value == null ? null : String.valueOf(value);
    }

    private static Integer parseEnglishCardinalValue(String[] tokens, int start, int end) {
        int thousandIndex = findUniqueToken(tokens, start, end, "THOUSAND");
        if (thousandIndex == -2) return null;
        if (thousandIndex >= 0) {
            Integer thousands = parseEnglishSubHundred(tokens, start, thousandIndex);
            if (thousands == null || thousands <= 0) return null;

            int next = thousandIndex + 1;
            if (next < end && "AND".equals(tokens[next])) {
                next++;
            }

            Integer remainder = next < end ? parseEnglishSubThousand(tokens, next, end) : 0;
            if (remainder == null) return null;
            return thousands * 1000 + remainder;
        }

        return parseEnglishSubThousand(tokens, start, end);
    }

    private static Integer parseEnglishSubThousand(String[] tokens, int start, int end) {
        if (start >= end) return 0;

        int hundredIndex = findUniqueToken(tokens, start, end, "HUNDRED");
        if (hundredIndex == -2) return null;
        if (hundredIndex >= 0) {
            Integer hundreds = parseEnglishSubHundred(tokens, start, hundredIndex);
            if (hundreds == null || hundreds <= 0) return null;

            int next = hundredIndex + 1;
            if (next < end && "AND".equals(tokens[next])) {
                next++;
            }

            Integer remainder = next < end ? parseEnglishSubHundred(tokens, next, end) : 0;
            if (remainder == null) return null;
            return hundreds * 100 + remainder;
        }

        return parseEnglishSubHundred(tokens, start, end);
    }

    private static Integer parseEnglishSubHundred(String[] tokens, int start, int end) {
        if (start >= end) return null;

        int length = end - start;
        if (length == 1) {
            Integer small = ENGLISH_SMALL_NUMBER_MAP.get(tokens[start]);
            if (small != null) return small;
            return ENGLISH_TENS_NUMBER_MAP.get(tokens[start]);
        }

        if (length == 2) {
            Integer tens = ENGLISH_TENS_NUMBER_MAP.get(tokens[start]);
            Integer ones = ENGLISH_SMALL_NUMBER_MAP.get(tokens[start + 1]);
            if (tens != null && ones != null && ones >= 0 && ones < 10) {
                return tens + ones;
            }
        }

        return null;
    }

    private static int findUniqueToken(String[] tokens, int start, int end, String target) {
        int found = -1;
        for (int i = start; i < end; i++) {
            if (!target.equals(tokens[i])) continue;
            if (found >= 0) return -2;
            found = i;
        }
        return found;
    }

    private static final class ParseCandidate {
        final String digits;
        final int chunkCount;

        ParseCandidate(String digits, int chunkCount) {
            this.digits = digits;
            this.chunkCount = chunkCount;
        }
    }

    private static String normalizeChineseNumberFragments(String input) {
        StringBuilder out = new StringBuilder();
        StringBuilder token = new StringBuilder();

        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (CHINESE_NUM_CHARS.contains(c)) {
                token.append(c);
            } else {
                flushChineseNumberToken(token, out);
                out.append(c);
            }
        }
        flushChineseNumberToken(token, out);
        return out.toString();
    }

    private static void flushChineseNumberToken(StringBuilder token, StringBuilder out) {
        if (token.length() == 0) return;
        String t = token.toString();
        token.setLength(0);
        if (containsUnit(t)) {
            out.append(parseChineseNumberWithUnit(t));
        } else {
            for (int i = 0; i < t.length(); i++) {
                Character d = DIGIT_CHAR_MAP.get(t.charAt(i));
                if (d != null) out.append(d);
            }
        }
    }

    private static boolean containsUnit(String t) {
        return t.indexOf('十') >= 0 || t.indexOf('拾') >= 0 ||
                t.indexOf('百') >= 0 || t.indexOf('佰') >= 0 ||
                t.indexOf('千') >= 0 || t.indexOf('仟') >= 0;
    }

    private static String parseChineseNumberWithUnit(String token) {
        int total = 0;
        int current = 0;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            int unit = unitValue(c);
            if (unit > 0) {
                if (current == 0) current = 1;
                total += current * unit;
                current = 0;
            } else {
                Character d = DIGIT_CHAR_MAP.get(c);
                if (d != null) current = d - '0';
            }
        }
        total += current;
        return String.valueOf(total);
    }

    private static int unitValue(char c) {
        switch (c) {
            case '十':
            case '拾':
                return 10;
            case '百':
            case '佰':
                return 100;
            case '千':
            case '仟':
                return 1000;
            default:
                return 0;
        }
    }

    private static String toHalfWidth(String input) {
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == 12288) out.append(' ');
            else if (c >= 65281 && c <= 65374) out.append((char) (c - 65248));
            else out.append(c);
        }
        return out.toString();
    }

    private static int scoreMatch(String input, String nodeId) {
        if (input.equals(nodeId)) return 100;
        if (input.contains(nodeId) || nodeId.contains(input)) return 92;

        int distance = levenshtein(input, nodeId);
        int maxLen = Math.max(input.length(), nodeId.length());
        if (maxLen > 0 && distance <= 1) return 88;
        if (maxLen > 0 && distance == 2 && maxLen >= 4) return 76;

        if (commonPrefixLength(input, nodeId) >= Math.min(3, Math.min(input.length(), nodeId.length()))) {
            return 72;
        }
        return 0;
    }

    private static int commonPrefixLength(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int i = 0;
        while (i < len && a.charAt(i) == b.charAt(i)) i++;
        return i;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
