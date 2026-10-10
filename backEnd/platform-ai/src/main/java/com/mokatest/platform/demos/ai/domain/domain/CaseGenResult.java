package com.mokatest.platform.demos.ai.domain;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/**
 * 用例生成结果（LangChain4j 结构化输出 POJO）。
 *
 * 输出契约：{"test_cases": [...]}
 * 框架自动生成 JSON Schema 注入 prompt，自动解析输出，解析失败由 OutputGuardrail 自动 reprompt 重试。
 */
@Description("用例生成结果")
public record CaseGenResult(
        @Description("生成的测试用例列表")
        List<TestCaseDraft> test_cases) {

    @Description("测试用例草稿")
    public record TestCaseDraft(
            @Description("用例名称")
            String caseName,

            @Description("前置条件")
            String preCondition,

            @Description("测试步骤列表，每项含 step 和 expected")
            List<StepItem> testSteps,

            @Description("用例类型：FUNCTION/API/PERFORMANCE/COMPATIBILITY/SMOKE")
            String caseType,

            @Description("优先级：P0/P1/P2")
            String priority,

            @Description("标签，逗号分隔")
            String tags,

            @Description("预期执行时长（分钟）")
            Integer expectDuration,

            @Description("测试设计方法：EQUIVALENCE/BOUNDARY/SCENARIO/ERROR_GUESSING")
            String designMethod,

            @Description("覆盖的设计维度点ID列表")
            List<String> coverPoints) {
    }

    @Description("测试步骤")
    public record StepItem(
            @Description("操作步骤")
            String step,

            @Description("预期结果")
            String expected) {
    }
}
