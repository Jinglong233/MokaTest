package com.mokatest.platform.demos.qa.ai.casegen;

import lombok.Data;

import java.util.List;

/**
 * AI 生成的用例草稿（防腐层 DTO）
 *
 * 与 test_case 表结构解耦：模型输出 → CaseDraftDTO → CaseDraftMapper → TestCase，
 * 表结构变更只需调整 Mapper，prompt/解析器不动。
 */
@Data
public class CaseDraftDTO {

    /** 草稿ID（生成时后端分配，防重复入库的身份标识） */
    private String draftId;

    /** 用例名称（必填） */
    private String caseName;

    /** 前置条件 */
    private String preCondition;

    /** 测试步骤 */
    private List<StepItem> testSteps;

    /** 用例类型：FUNCTION/API/PERFORMANCE/COMPATIBILITY/SMOKE（非法值兜底 FUNCTION + 标黄） */
    private String caseType;

    /** 优先级：P0/P1/P2（非法值兜底 P2 + 标黄） */
    private String priority;

    /** 标签（逗号分隔） */
    private String tags;

    /** 预期执行时长（分钟） */
    private Integer expectDuration;

    /** 枚举兜底标记：true=有字段被兜底，前端标黄提示人工确认 */
    private Boolean enumFallback;

    @Data
    public static class StepItem {
        private String step;
        private String expected;
    }
}
