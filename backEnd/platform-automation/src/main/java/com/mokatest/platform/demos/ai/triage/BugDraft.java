package com.mokatest.platform.demos.ai.triage;

import dev.langchain4j.model.output.structured.Description;

/**
 * 真 BUG 专家的产出：BUG 草稿（字段对齐 platform-qa Bug 实体，由前端确认后调 QA 接口创建）。
 */
@Description("BUG 草稿")
public record BugDraft(

        @Description("BUG 标题，简明说明什么操作下出现了什么异常")
        String title,

        @Description("BUG 描述：实际表现 vs 预期表现，含关键错误信息")
        String description,

        @Description("复现步骤（每行一步）")
        String reproduceSteps,

        @Description("严重程度：FATAL=阻断主流程 / SERIOUS=核心功能异常 / NORMAL=一般问题 / TIPS=轻微")
        String severity) {
}
