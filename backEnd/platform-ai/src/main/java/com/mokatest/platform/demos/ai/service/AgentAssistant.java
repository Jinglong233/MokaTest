package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;

/**
 * AI 对话工作台助手（工具调用 + 流式 + 多轮记忆 + 输入护栏）
 */
@InputGuardrails(PromptInjectionGuard.class)
public interface AgentAssistant extends ChatMemoryAccess {

    String SYSTEM_PROMPT = AgentGuardrail.GUARDRAIL + "\n\n"
            + "你是测试管理助手，可以调用工具查询需求、用例、BUG、知识库等信息。"
            + "根据用户请求选择合适的工具完成操作。\n"
            + "重要规则：\n"
            + "1. 涉及具体实体的操作（生成用例、查血缘等）必须目标明确。用户未指明具体对象时，"
            + "先用 search 类工具列出候选，回复候选列表请用户选择，不要自行批量执行。\n"
            + "2. start_case_generation 一次只能针对一个需求调用，且每次对话中调用一次即可，禁止循环批量调用。\n"
            + "3. 工具返回 error 时向用户说明原因，不要重试同一工具超过一次。";

    @SystemMessage(SYSTEM_PROMPT)
    String chat(@MemoryId String memoryId, @UserMessage String message);

    @SystemMessage(SYSTEM_PROMPT)
    TokenStream chatStream(@MemoryId String memoryId, @UserMessage String message);
}
