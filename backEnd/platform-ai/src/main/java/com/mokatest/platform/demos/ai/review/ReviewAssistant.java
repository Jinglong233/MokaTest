package com.mokatest.platform.demos.ai.review;

import com.mokatest.platform.demos.ai.domain.ReviewResult;
import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * 需求评审助手（langchain4j AI Service + 结构化输出 + 输入/输出护栏 + RAG）。
 *
 * 单次调用：用户通过弹窗填写指令 → 后端拼装需求内容 + 指令 → review() 返回 ReviewResult POJO。
 * 不使用 ChatMemory（无多轮对话）。维度选择由 LLM 根据用户指令自然理解。
 */
@InputGuardrails(PromptInjectionGuard.class)
public interface ReviewAssistant {

    String FULL_CHECKLIST = """
            完整度(CMP): CMP-1 正常主流程, CMP-2 异常失败流程, CMP-3 前置条件与依赖, CMP-4 后置状态, CMP-5 多角色场景
            清晰度(CLR): CLR-1 术语定义一致, CLR-2 数值阈值明确, CLR-3 界面交互可对应, CLR-4 无歧义表述, CLR-5 范围边界明确
            可测性(TST): TST-1 验收标准明确, TST-2 可量化判定, TST-3 输入输出可验证, TST-4 性能指标可度量, TST-5 结果可观察
            一致性(CON): CON-1 内部不矛盾, CON-2 跨需求不冲突, CON-3 数据口径一致, CON-4 流程规则一致, CON-5 术语与平台一致
            安全性(SEC): SEC-1 权限控制, SEC-2 敏感数据保护, SEC-3 越权场景, SEC-4 操作审计, SEC-5 输入合法性校验
            边界(BND): BND-1 输入边界, BND-2 并发重复, BND-3 超时重试, BND-4 数据量上限, BND-5 异常恢复""";

    String SYSTEM_PROMPT = """
            你是资深需求评审专家，从测试视角逐项检查需求文档。

            ## 检查项清单
            """ + FULL_CHECKLIST + """

            ## 维度选择规则（由用户指令决定，只输出用户要求的维度）
            - 用户指定具体维度（如"只看完整性""重点检查安全"）→ 只输出对应维度
            - 用户要求跳过维度（如"不看安全"）→ 输出除该维度外的所有维度
            - 用户要求随机/随便（如"随便一个""任意一个维度"）→ 随机选一个维度
            - 用户未指定维度 → 覆盖全部 30 项

            ## answer 取值规则
            YES=需求中明确描述，PARTIAL=部分描述，NO=未描述

            ## quote 规则
            quote 必须是需求原文的连续子串（≤60字），answer 为 NO 且原文无相关内容时置空字符串

            ## risks 规则
            risks 不得只复述"缺少 XX"，desc 要说清具体后果，suggestion 要给可执行的改法
            """;

    @SystemMessage(SYSTEM_PROMPT)
    @OutputGuardrails(ReviewResultGuard.class)
    ReviewResult review(@UserMessage String message);
}
