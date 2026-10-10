package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.guardrail.CaseGenJsonValidator;
import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.guardrail.InputGuardrails;
import dev.langchain4j.service.guardrail.OutputGuardrails;

/**
 * 用例生成助手（流式 + RAG + 输入/输出护栏）。
 *
 * generate()：QA 问答 + 维度点生成（自由文本或简单 JSON，无输出护栏）
 * generateCases()：用例生成（结构化 JSON 输出，@OutputGuardrails 自动验证 + reprompt，教程 09）
 *
 * 不挂 ChatMemory：生成场景每轮独立，对话历史摘要由调用方手工注入。
 */
@InputGuardrails(PromptInjectionGuard.class)
public interface CaseGenerationAssistant {

    /** 通用流式生成（QA + 维度点，无输出护栏） */
    @UserMessage("{{instruction}}")
    TokenStream generate(@V("instruction") String instruction);

    /** 用例生成（结构化 JSON 输出，@OutputGuardrails 自动提取 JSON + 解析 + 失败 reprompt） */
    @UserMessage("{{instruction}}")
    @OutputGuardrails(CaseGenJsonValidator.class)
    TokenStream generateCases(@V("instruction") String instruction);

    /** 从 Markdown 用例文档提取结构化 JSON（同步调用，@OutputGuardrails 自动验证格式） */
    @UserMessage("{{markdown}}")
    @OutputGuardrails(CaseGenJsonValidator.class)
    String extractCases(@V("markdown") String markdown);

    /** V2 逐点生成同步调用（返回完整 JSON，@OutputGuardrails 自动验证 + reprompt） */
    @UserMessage("{{instruction}}")
    @OutputGuardrails(CaseGenJsonValidator.class)
    String generateCasesSync(@V("instruction") String instruction);
}
