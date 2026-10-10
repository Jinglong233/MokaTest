package com.mokatest.platform.demos.ai.domain;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

/**
 * 需求评审结果（LangChain4j 结构化输出 POJO，契约 v2）
 *
 * 与流式路径（reviewStream）的输出契约一致：{checklist, risks, summary}。
 * 同步路径 review() 在启用 strictJsonSchema 的 ChatModel 下由框架保证结构合法。
 */
@Description("需求评审结果")
public record ReviewResult(
        @Description("检查表结果列表，只包含本轮实际评审的检查项，按 id 原样返回")
        List<ChecklistEntry> checklist,

        @Description("风险清单，无风险时为空数组")
        List<Risk> risks,

        @Description("一段中文总评（200字以内）")
        String summary) {

    public enum Answer {
        /** 需求中明确描述 */ YES,
        /** 部分描述 */ PARTIAL,
        /** 未描述 */ NO
    }

    @Description("检查表单项结果")
    public record ChecklistEntry(
            @Description("检查项编号，如 CMP-1，必须原样返回检查表中的 id")
            String id,

            @Description("YES=需求中明确描述，PARTIAL=部分描述，NO=未描述")
            Answer answer,

            @Description("需求原文连续子串（≤60字）；answer 为 NO 且原文无相关内容时置空字符串")
            String quote) {
    }

    @Description("风险项")
    public record Risk(
            @Description("维度：completeness/clarity/testability/consistency/security/boundary")
            String dimension,

            @Description("级别：HIGH/MEDIUM/LOW")
            String level,

            @Description("风险描述，说明具体后果，不得只复述\"缺少XX\"")
            String desc,

            @Description("可执行的改进建议")
            String suggestion,

            @Description("需求原文引用")
            String quote,

            @Description("与某检查项直接相关时填该项 id（如 CMP-2）；检查表覆盖不到的深层问题置空字符串")
            String checklistId) {
    }
}
