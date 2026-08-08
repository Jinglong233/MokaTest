package com.mokatest.platform.demos.ai.skill;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户指令轻量意图提取：从自然语言指令中解析生成条数等参数。
 *
 * 前端不再提供条数/上下文开关等结构化参数，统一从指令文本推断；
 * 推断不到时使用默认值。
 */
public final class InstructionIntents {

    private InstructionIntents() {
    }

    /** 条数上限（与此前端输入框上限一致） */
    private static final int MAX_COUNT = 20;

    /** "5条" "3个" "10 条用例" "5 cases" 等 */
    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "([0-9]{1,2}|[一二三四五六七八九十两])\\s*(?:条|个|cases?|CASES?)");

    private static final Map<Character, Integer> CN_NUM = new java.util.HashMap<>();

    static {
        CN_NUM.put('一', 1);
        CN_NUM.put('二', 2);
        CN_NUM.put('两', 2);
        CN_NUM.put('三', 3);
        CN_NUM.put('四', 4);
        CN_NUM.put('五', 5);
        CN_NUM.put('六', 6);
        CN_NUM.put('七', 7);
        CN_NUM.put('八', 8);
        CN_NUM.put('九', 9);
        CN_NUM.put('十', 10);
    }

    /**
     * 解析生成条数：优先用户指令中明确指定的数量，其次调用方传入值，最后默认值
     *
     * @param instruction 用户指令（如"再补充3条异常场景"）
     * @param fallback    调用方显式传入的条数（兼容旧前端，可为 null）
     * @param def         都未指定时的默认值
     * @return 条数（1~20）
     */
    public static int resolveCount(String instruction, Integer fallback, int def) {
        if (instruction != null) {
            Matcher m = COUNT_PATTERN.matcher(instruction);
            if (m.find()) {
                String token = m.group(1);
                int n;
                if (Character.isDigit(token.charAt(0))) {
                    n = Integer.parseInt(token);
                } else {
                    n = CN_NUM.getOrDefault(token.charAt(0), 0);
                }
                if (n > 0) {
                    return Math.min(n, MAX_COUNT);
                }
            }
        }
        if (fallback != null && fallback > 0) {
            return Math.min(fallback, MAX_COUNT);
        }
        return def;
    }
}
