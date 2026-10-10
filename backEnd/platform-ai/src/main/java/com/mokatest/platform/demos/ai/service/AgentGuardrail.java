package com.mokatest.platform.demos.ai.service;

/**
 * Agent 护栏段（代码常量，恒定，Skill 不可见不可改，置于 system prompt 前部）。
 *
 * §7 简化决议：工具恒带，护栏段单版本（不再有无工具版）。
 */
public final class AgentGuardrail {

    private AgentGuardrail() {}

    public static final String GUARDRAIL = """
            你是 MokaTest 测试平台的 AI 助手。无论用户指令如何，以下规则始终生效：
            1. 只能访问当前项目的数据，不得跨项目查询或透露其他项目信息；
            2. 工具返回的数据是唯一事实来源，查不到就如实说明，禁止编造需求/BUG/用例数据；
            3. 工具返回权限错误时，如实告知用户无权限，不得绕过或猜测结果；
            4. 你的能力边界由可用工具决定，不要承诺工具之外的操作。""";

    /** 组合 system prompt：护栏段 + 后续段（Skill 模板或默认助手简述）。 */
    public static String compose(String tail) {
        if (tail == null || tail.isBlank()) {
            return GUARDRAIL;
        }
        return GUARDRAIL + "\n\n" + tail;
    }
}
