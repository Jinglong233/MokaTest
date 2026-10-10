package com.mokatest.platform.demos.ai.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API 用例生成 prompt 模板 + 指令条数解析（从旧 GenerateApiCaseSkill 提炼）。
 */
public final class ApiCaseGenPrompts {

    private ApiCaseGenPrompts() {
    }

    public static final String SCENE = "GENERATE_API_CASE";

    private static final int MAX_COUNT = 20;
    private static final Pattern COUNT_PATTERN = Pattern.compile(
            "([0-9]{1,2}|[一二三四五六七八九十两])\\s*(?:条|个|cases?|CASES?)");
    private static final java.util.Map<Character, Integer> CN_NUM = new java.util.HashMap<>();

    static {
        CN_NUM.put('一', 1); CN_NUM.put('二', 2); CN_NUM.put('两', 2);
        CN_NUM.put('三', 3); CN_NUM.put('四', 4); CN_NUM.put('五', 5);
        CN_NUM.put('六', 6); CN_NUM.put('七', 7); CN_NUM.put('八', 8);
        CN_NUM.put('九', 9); CN_NUM.put('十', 10);
    }

    public static int resolveCount(String instruction, Integer fallback, int def) {
        if (instruction != null) {
            Matcher m = COUNT_PATTERN.matcher(instruction);
            if (m.find()) {
                String token = m.group(1);
                int n = Character.isDigit(token.charAt(0))
                        ? Integer.parseInt(token)
                        : CN_NUM.getOrDefault(token.charAt(0), 0);
                if (n > 0) return Math.min(n, MAX_COUNT);
            }
        }
        if (fallback != null && fallback > 0) return Math.min(fallback, MAX_COUNT);
        return def;
    }

    public static String systemPrompt() {
        return "你是资深接口测试工程师，负责根据接口定义设计高质量的接口测试用例。\n"
                + "要求：\n"
                + "1. 覆盖正常请求、必填缺失、边界值、异常参数、权限/状态等场景；\n"
                + "1.1 用户本轮指令是最高优先级要求：指令指定的主题/数量/侧重点必须严格满足，"
                + "上下文仅作为背景理解，不要偏离指令主题；"
                + "若指令明确要求不要使用某类上下文（如\"不用参考已有用例\"），忽略对应上下文；\n"
                + "2. 未提供的字段默认沿用接口定义，不要随意改变 method/path；\n"
                + "## 输出契约（严格遵守，不要输出解释文字、不要用 markdown 代码块）\n"
                + "1. 只输出一个 JSON 对象，结构：\n"
                + "{\"test_cases\":[{\"caseName\":\"用例名称\",\"description\":\"场景说明\","
                + "\"requestHeader\":[{\"name\":\"...\",\"value\":\"...\",\"type\":\"...\",\"disabled\":false}],"
                + "\"query\":[],"
                + "\"bodyJson\":\"raw JSON 请求体文本，可空表示沿用接口默认；可使用 @phone() @integer(1,100) @template(id) 等 Mock 表达式\","
                + "\"assertions\":[{\"apiAssertType\":\"STATUS_CODE/BODY/HEADER/RESPONSE_TIME/CUSTOM\","
                + "\"field\":\"status 或 $.code\",\"assertRelationship\":\"EQUALS/NOT_EQUALS/CONTAINS/NOT_CONTAINS/GT/LT/GE/LE/REGULAR\","
                + "\"assertValue\":\"断言值\"}],"
                + "\"extractions\":[{\"type\":\"JSON_PATH/HEADER\",\"expression\":\"表达式\",\"variableName\":\"变量名\"}]}],"
                + "\"uncertainties\":[\"...\"]}\n"
                + "2. requestHeader/query/extractions 可空（空表示沿用接口默认）；\n"
                + "3. 生成用例时必须检查接口定义是否存在关键缺失（必填参数不明确、取值范围/枚举未定义、"
                + "错误码未说明等）：如有，逐条填入 uncertainties，每条说明「缺什么」和「为什么影响测试设计」；"
                + "如无，uncertainties 返回空数组。";
    }

    public static String buildUserMessage(String context, String history, String instruction,
                                         String previousOutput, int count) {
        StringBuilder user = new StringBuilder();
        user.append("请基于以下上下文，为该接口设计 ").append(count).append(" 条测试用例")
                .append("（若用户本轮指令中明确指定了数量，以指令中的数量为准）：\n\n");
        user.append(context);
        if (history != null && !history.isEmpty()) {
            user.append("\n## 本会话对话历史\n").append(history)
                    .append("（注意：历史中可能包含用户对之前生成结果的修正意见或补充信息，本轮生成必须吸收）\n");
        }
        if (instruction != null && !instruction.isEmpty()) {
            user.append("\n用户本轮指令：").append(instruction).append('\n');
        }
        if (previousOutput != null && !previousOutput.isEmpty()) {
            user.append("\n上一轮已生成如下用例，本轮请按额外要求调整/补充，输出本轮完整用例数组：\n")
                    .append(previousOutput).append('\n');
        }
        return user.toString();
    }

    public static String qaSystemPrompt() {
        return "你是资深接口测试专家助手。请基于给定的接口上下文和对话历史回答用户问题。"
                + "回答使用中文，简洁专业，可以使用 markdown 列表。"
                + "不要输出 JSON 用例数组；如果用户的问题其实是在要求生成用例，"
                + "也只需用文字给出设计建议即可。";
    }

    public static String buildQaUserMessage(String context, String history, String instruction) {
        StringBuilder user = new StringBuilder();
        user.append(context);
        if (history != null && !history.isEmpty()) {
            user.append("\n## 本会话对话历史\n").append(history).append('\n');
        }
        user.append("\n用户问题：").append(instruction);
        return user.toString();
    }
}
