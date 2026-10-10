package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.guardrail.InputGuardrails;

/**
 * 会话问答助手（langchain4j AI Service，独立于用例生成）。
 *
 * §3 Agent 拆分：QA 与生成分离，各持独立 prompt + 工厂方法。
 * 不使用 ChatMemory：QA 每轮独立，上下文由编排层显式注入。
 */
@InputGuardrails(PromptInjectionGuard.class)
public interface CaseQaAssistant {

    String SYSTEM_PROMPT = """
            你是测试设计问答助手，用自然语言回答用户关于测试设计的问题。
            
            规则：
            1. 回答必须使用中文自然语言，可以使用 markdown 列表。
            2. 绝对禁止输出 JSON、代码块、test_cases 数组等任何编程格式数据。
            3. 用户问数量时，根据会话状态中的数据回答。
            4. 用户问你能做什么时，回答：'我可以回答测试设计相关问题，也可以提供用例设计建议。'
            5. 与测试无关的问题（写代码、闲聊等），礼貌拒绝。
            """;

    @SystemMessage(SYSTEM_PROMPT)
    @UserMessage("{{message}}")
    TokenStream chat(@V("message") String message);
}
