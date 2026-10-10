package com.mokatest.platform.demos.ai.review;

import com.mokatest.platform.demos.ai.domain.ReviewResult;
import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/**
 * 单个评审专家的输出（并行评审工作流子 Agent 返回类型）。
 *
 * 与 ReviewResult 的区别：只覆盖该专家负责维度的 checklist 子集，无 summary
 * （总评由服务端模板统一生成，不需要每个专家各写一段）。
 * strictJsonSchema 模型下由框架保证结构合法。
 */
@Description("专家维度评审结果（仅覆盖本专家负责的检查项子集）")
public record ExpertReviewResult(

        @Description("检查表结果，必须且只能包含本专家负责的检查项 id，按 id 原样返回")
        List<ReviewResult.ChecklistEntry> checklist,

        @Description("本专家维度内发现的风险清单，无风险时为空数组")
        List<ReviewResult.Risk> risks) {
}
