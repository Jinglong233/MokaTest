package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.guardrail.PromptInjectionGuard;
import dev.langchain4j.service.memory.ChatMemoryAccess;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;

/**
 * AI 对话工作台助手（工具调用 + 流式 + 多轮记忆 + 输入护栏）
 *
 * 注意：system prompt 不得用 @SystemMessage 注解配置——langchain4j 中注解优先于
 * systemMessageProvider，会静默覆盖快捷指令注入。统一由 AssistantFactory 的
 * systemMessageProvider 提供（默认走 SYSTEM_PROMPT）。
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
            + "3. 工具返回 error 时向用户说明原因，不要重试同一工具超过一次。\n"
            + "4. 用户想生成测试用例但未说明类型（功能用例还是接口用例）时，先调用 ask_case_gen_type 让用户选择。\n"
            + "5. 用户想为接口生成用例（接口用例/调试用例）时，无论是否已指明接口，都先调用 search_apis "
            + "列出候选接口请用户选择。search_apis 返回后不要复述候选列表（系统会以卡片展示），简短提示用户选择即可。"
            + "用户通过文字选定某个候选（如\"第二个\"/\"ID 13 那个\"）后，调用 start_api_case_generation 并传入对应 apiId，"
            + "系统会自动打开生成入口。接口用例由候选卡片或 start_api_case_generation 触发，"
            + "你不要自己生成用例内容，也不要调用 create_test_case。一次只为一个接口生成用例。\n"
            + "6. create_test_case 仅用于步骤型功能用例（入用例池）。调用后不要复述草稿内容，"
            + "系统会展示可编辑卡片，简短提示用户在卡片中确认入库即可。";

    String chat(@MemoryId String memoryId, @UserMessage String message);

    TokenStream chatStream(@MemoryId String memoryId, @UserMessage String message);
}
