package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;

/**
 * 逐点 QA 助手（langchain4j AI Service，带 ChatMemory 可追问）。
 *
 * 与 CaseQaAssistant 的区别：
 * - 带 @MemoryId：按 recordNo+tpId 隔离，支持追问
 * - 无 RAG：防止弱模型输出 JSON
 * - system prompt 通过 systemMessageProvider 动态注入（需求摘要+测试点信息）
 */
@InputGuardrails(PromptInjectionGuard.class)
public interface CasePointQaAssistant {

    String DEFAULT_SYSTEM_PROMPT = """
            你是测试设计问答助手，围绕当前测试点与用户对话。
            回答必须使用中文自然语言，可以使用 markdown 列表。
            禁止输出 JSON、代码块等任何编程格式数据。
            只回答与测试用例设计相关的问题。
            与测试无关的问题，礼貌拒绝。
            只讨论当前测试点，不涉及其他测试点或维度。
            """;

    @SystemMessage(DEFAULT_SYSTEM_PROMPT)
    TokenStream chat(@MemoryId @V("memoryId") String memoryId,
                     @UserMessage @V("message") String message);
}
