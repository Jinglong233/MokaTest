package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.domain.PairCheckResult;
import com.mokatest.platform.demos.ai.guardrail.PairCheckGuard;
import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * B 层窄判断 AI Service（langchain4j AI Service + 结构化输出 + 输入/输出护栏）。
 *
 * 仅对 A 层判为 AMBIGUOUS 的（维度点, 用例）对做 YES/NO 二选一判断。
 * 输出空间极小（二选一），弱模型答错代价被限制在模糊带内且方向保守。
 * 不使用 ChatMemory：一次性批量调用，无多轮。
 */
@InputGuardrails(PromptInjectionGuard.class)
public interface PairCheckAssistant {

    String SYSTEM_PROMPT = """
            你是测试覆盖核对员。对每个（维度点, 用例）对，判断该用例的步骤内容是否覆盖了该维度点。

            ## 规则
            - 只回答 YES 或 NO，不解释
            - YES = 用例步骤内容确实覆盖了该维度点描述的场景
            - NO = 用例步骤内容没有覆盖该维度点描述的场景

            ## 输出 JSON 结构
            {"answers":[{"tpId":"TP-1","caseName":"用例名称","answer":"YES"}]}
            """;

    @SystemMessage(SYSTEM_PROMPT)
    @UserMessage("""
            逐对判断以下用例是否覆盖对应维度点：

            {{pairs}}
            """)
    @OutputGuardrails(PairCheckGuard.class)
    PairCheckResult check(@V("pairs") String pairs);
}
