package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.domain.CoverageAuditReport;
import com.mokatest.platform.demos.ai.guardrail.CoverageAuditGuard;
import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * 覆盖审查助手（langchain4j AI Service + 结构化输出 + 输入/输出护栏）。
 *
 * §5.2 实现规范：声明式 @SystemMessage + @UserMessage，返回 CoverageAuditReport POJO，
 * schema 指令下发与 JSON 反序列化全部由框架完成，业务代码零解析。
 *
 * 不使用 ChatMemory：审查是无状态的一次性调用。
 */
@InputGuardrails(PromptInjectionGuard.class)
public interface CoverageAuditAssistant {

    String SYSTEM_PROMPT = """
            你是测试覆盖度审计员。基于需求原文和维度点清单，找出清单遗漏的维度点。

            ## 任务
            基于需求原文，找出维度点清单遗漏的维度点（标题+分类+理由一句话）

            ## 判定口径
            - 判定依据是用例的步骤内容（步骤→预期），不是用例的名称或身份标记。
            - 维度点覆盖状态（verdicts）由平台代码另行裁定，你只需关注遗漏的维度点。

            ## 输出 JSON 结构
            {"missingPoints":[{"title":"维度点标题","category":"FUNCTION","reason":"理由"}]}
            """;

    @SystemMessage(SYSTEM_PROMPT)
    @UserMessage("""
            ## 需求原文
            {{requirement}}

            ## 维度点清单
            {{testPoints}}

            ## 已入库用例
            {{adoptedCases}}

            ## 待采纳草稿
            {{drafts}}
            """)
    @OutputGuardrails(CoverageAuditGuard.class)
    CoverageAuditReport audit(@V("requirement") String requirement,
                               @V("testPoints") String testPoints,
                               @V("adoptedCases") String adoptedCases,
                               @V("drafts") String drafts);
}
