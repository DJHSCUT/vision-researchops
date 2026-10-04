package com.djh.researchops.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** 用户输入到规范业务编号；无效输入返回 null，绝不将编号数字解释为数据库 ID。 */
public final class BusinessCodeParser {

    private static final Pattern TASK_CODE = Pattern.compile("T-?([1-9][0-9]{0,18})");
    private static final Pattern RUN_CODE = Pattern.compile("R-?([1-9][0-9]{0,18})");

    private BusinessCodeParser() { }

    public static String normalizeTaskCode(String input) {
        return normalize(input, TASK_CODE, "T");
    }

    public static String normalizeRunCode(String input) {
        return normalize(input, RUN_CODE, "R");
    }

    private static String normalize(String input, Pattern pattern, String prefix) {
        if (input == null) return null;
        var matcher = pattern.matcher(input.trim().toUpperCase(Locale.ROOT));
        if (!matcher.matches()) return null;
        try {
            long sequence = Long.parseLong(matcher.group(1));
            return prefix + "-" + sequence;
        } catch (NumberFormatException overflow) {
            return null;
        }
    }
}
