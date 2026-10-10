package com.mokatest.platform.demos.ai.review;

import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;

/**
 * 需求评审专家（流式 AiService，单接口多实例）。
 *
 * 长生成按教程 07 章走 TokenStream（token 持续到达即活着，无整包超时问题）；
 * 4 个专家由调用方 CompletableFuture 并行编排（各管一段 checklist 子集）。
 */
@InputGuardrails(PromptInjectionGuard.class)
public interface ReviewExpert {

    @SystemMessage(ReviewExpertPrompts.SYSTEM)
    @UserMessage("""
            请逐项检查你负责的检查项并输出结果：

            {{requirement}}

            ## 你负责的检查项（必须且只能覆盖这些 id）
            {{checklistSpec}}

            {{feedback}}
            """)
    TokenStream review(@V("requirement") String requirement,
                       @V("checklistSpec") String checklistSpec,
                       @V("feedback") String feedback);
}
