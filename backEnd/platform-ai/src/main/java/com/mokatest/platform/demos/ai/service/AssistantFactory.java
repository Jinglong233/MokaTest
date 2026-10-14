package com.mokatest.platform.demos.ai.service;

import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.ai.memory.MysqlChatMemoryStore;
import dev.langchain4j.memory.chat.TokenWindowChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * AI Service 工厂：用 AiServices.builder() 从 LangChain4j 原生 API 创建各场景助手。
 *
 * 替代旧 SkillExecutionSupport + SkillRegistry 的手写编排逻辑。
 * 每个 AI Service 自带记忆管理，后续 Phase 逐步加 RAG / 工具 / 护栏。
 */
@Component
public class AssistantFactory {

    @Autowired
    private ModelManager modelManager;

    @Autowired
    private MysqlChatMemoryStore memoryStore;

    @Autowired(required = false)
    private com.mokatest.platform.demos.ai.rag.KnowledgeIngestService ingestService;

    @Autowired(required = false)
    private dev.langchain4j.service.tool.ToolProvider mcpToolProvider;

    /** 记忆窗口上限（token）。MessageWindowChatMemory 官方定位为原型用途，生产按 token 淘汰 */
    private static final int MEMORY_MAX_TOKENS = 8000;

    /** §7.2 全局容错：模型幻觉调用不存在工具时，降级为错误提示喂回模型，不炸流。
     *  文案归因于「本次」+ 允许重试，避免模型把临时失败当成永久禁令存进记忆。 */
    private static final java.util.function.Function<ToolExecutionRequest, ToolExecutionResultMessage>
            HALLUCINATED_TOOL_STRATEGY = request -> ToolExecutionResultMessage.from(
                    request, "工具 " + request.name() + " 本次调用失败（临时不可用）。"
                    + "本次请基于已有信息用文字回答；该工具后续可能恢复，用户再次明确要求时可以重试。");

    private ChatMemoryProvider memoryProvider() {
        // 必须 .id(memoryId)：缺省时所有记忆 id 都是 "default"，持久化存储下会串会话/串用户
        return memoryId -> TokenWindowChatMemory.builder()
                .id(memoryId)
                .maxTokens(MEMORY_MAX_TOKENS, new com.mokatest.platform.demos.ai.config.GenericTokenCountEstimator())
                .chatMemoryStore(memoryStore)
                .build();
    }

    /** 用例生成助手（流式 + RAG），使用系统级流式模型与注解默认 system prompt */
    public CaseGenerationAssistant createCaseGenerator(Integer projectId) {
        return createCaseGenerator(projectId, null, 0);
    }

    /**
     * 用例生成助手（流式 + RAG + 动态 system prompt）。
     * 不设置输出 token 上限——对推理模型该上限是「思考+正文」总预算，设小会掐断输出；
     * 留给服务端默认（不限），如需限制在 AI 配置里统一设置。
     *
     * @param projectId    项目ID（RAG 检索隔离；null 则不挂检索器）
     * @param systemPrompt 动态 system prompt（null 时用接口注解默认）
     */
    public CaseGenerationAssistant createCaseGenerator(Integer projectId, String systemPrompt) {
        return createCaseGenerator(projectId, systemPrompt, 0);
    }

    /**
     * 用例生成助手（流式 + RAG + 动态 system prompt + maxTokens 限制）。
     *
     * @param projectId    项目ID（RAG 检索隔离；null 则不挂检索器）
     * @param systemPrompt 动态 system prompt（null 时用接口注解默认）
     * @param maxTokens    输出 token 上限（>0 时生效；=0 不限制）。非推理模型设此值可防止隐藏 reasoning 过度消耗。
     */
    public CaseGenerationAssistant createCaseGenerator(Integer projectId, String systemPrompt, int maxTokens) {
        // 生成场景要求准确性：temperature 强制 0（推理模型不支持温度参数则自动跳过）
        StreamingChatModel streamingModel = maxTokens > 0
                ? modelManager.createCaseGenStreamingModel(maxTokens)
                : modelManager.createDeterministicStreamingChatModel();
        var builder = AiServices.builder(CaseGenerationAssistant.class)
                .streamingChatModel(streamingModel)
                .chatModel(modelManager.createReviewChatModel()) // 确定性(temp=0) + 300s 超时（extractCases 需处理大量 Markdown）
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            // thinking 控制已收口到 ModelManager（按厂商适配表注入关闭参数），此处只设 prompt
            builder.systemMessageProvider(memoryId -> systemPrompt);
        }
        ContentRetriever retriever = createContentRetriever(projectId);
        if (retriever != null) {
            builder.contentRetriever(retriever);
        }
        return builder.build();
    }

    /** AI 对话助手（流式 + 记忆 + RAG + 工具） */
    public AgentAssistant createAgent(Integer projectId, Object... tools) {
        ChatModel model = modelManager.getChatModel();
        StreamingChatModel streamingModel = modelManager.getStreamingChatModel();
        var builder = AiServices.builder(AgentAssistant.class)
                .chatModel(model)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(memoryProvider())
                .systemMessageProvider(memoryId -> AgentAssistant.SYSTEM_PROMPT)
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        ContentRetriever retriever = createContentRetriever(projectId);
        if (retriever != null) {
            builder.contentRetriever(retriever);
        }
        if (tools != null && tools.length > 0) {
            builder.tools(tools);
        }
        if (mcpToolProvider != null) {
            builder.toolProvider(mcpToolProvider);
        }
        return builder.build();
    }

    /** AI 对话助手（流式 + 记忆 + RAG + 工具），工具以 ToolSpecification/ToolExecutor 闭包注入 */
    public AgentAssistant createAgent(Integer projectId,
                                       java.util.Map<ToolSpecification, ToolExecutor> tools) {
        ChatModel model = modelManager.getChatModel();
        StreamingChatModel streamingModel = modelManager.getStreamingChatModel();
        var builder = AiServices.builder(AgentAssistant.class)
                .chatModel(model)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(memoryProvider())
                .systemMessageProvider(memoryId -> AgentAssistant.SYSTEM_PROMPT)
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        ContentRetriever retriever = createContentRetriever(projectId);
        if (retriever != null) {
            builder.contentRetriever(retriever);
        }
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools);
        }
        if (mcpToolProvider != null) {
            builder.toolProvider(mcpToolProvider);
        }
        return builder.build();
    }

    /** 评审助手（确定性模型 + RAG + 结构化输出，无 ChatMemory） */
    public com.mokatest.platform.demos.ai.review.ReviewAssistant createReviewAssistant(Integer projectId) {
        var builder = AiServices.builder(com.mokatest.platform.demos.ai.review.ReviewAssistant.class)
                .chatModel(modelManager.createReviewChatModel())
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        ContentRetriever retriever = createContentRetriever(projectId);
        if (retriever != null) {
            builder.contentRetriever(retriever);
        }
        return builder.build();
    }

    /** 意图分类器（无状态，无记忆） */
    public IntentClassifier createClassifier() {
        return AiServices.create(IntentClassifier.class, modelManager.createClassifierModel());
    }

    /**
     * §3 QA 助手（流式 + RAG，独立 prompt）。
     * 与 CaseGenerationAssistant 分离，各持独立 @SystemMessage。
     */
    public CaseQaAssistant createCaseQaAssistant(Integer projectId) {
        var builder = AiServices.builder(CaseQaAssistant.class)
                .streamingChatModel(modelManager.createDeterministicStreamingChatModel())
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        ContentRetriever retriever = createContentRetriever(projectId);
        if (retriever != null) {
            builder.contentRetriever(retriever);
        }
        return builder.build();
    }

    /**
     * 逐点 QA 助手（流式，无 RAG）。
     * RAG 会注入需求文档中的用例内容，触发 LLM 生成 JSON，逐点 QA 不需要 RAG。
     */
    public CaseQaAssistant createCaseQaAssistantNoRag() {
        return AiServices.builder(CaseQaAssistant.class)
                .streamingChatModel(modelManager.createDeterministicStreamingChatModel())
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY)
                .build();
    }

    /**
     * 逐点 QA 助手（流式 + ChatMemory + 动态 system prompt，无 RAG）。
     * 按 memoryId 隔离，支持追问。system prompt 动态注入需求摘要+测试点信息。
     */
    public CasePointQaAssistant createCasePointQaAssistant(String systemPrompt) {
        var builder = AiServices.builder(CasePointQaAssistant.class)
                .streamingChatModel(modelManager.createDeterministicStreamingChatModel())
                .chatMemoryProvider(memoryProvider())
                .systemMessageProvider(memoryId -> systemPrompt)
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        return builder.build();
    }

    /**
     * 覆盖审查助手（§5.2：确定性模型 + 结构化输出 + 输入/输出护栏，无 ChatMemory）。
     * 返回 CoverageAuditReport POJO，schema 指令下发与 JSON 反序列化由框架完成。
     */
    public CoverageAuditAssistant createCoverageAuditor(Integer projectId) {
        var builder = AiServices.builder(CoverageAuditAssistant.class)
                .chatModel(modelManager.createDeterministicChatModel())
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        ContentRetriever retriever = createContentRetriever(projectId);
        if (retriever != null) {
            builder.contentRetriever(retriever);
        }
        return builder.build();
    }

    /**
     * B 层窄判断助手（确定性模型 + 结构化输出 + 输入/输出护栏，无 ChatMemory）。
     * 对 A 层判为 AMBIGUOUS 的（维度点, 用例）对做 YES/NO 判断。
     */
    public PairCheckAssistant createPairCheckAssistant(Integer projectId) {
        var builder = AiServices.builder(PairCheckAssistant.class)
                .chatModel(modelManager.createDeterministicChatModel())
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        return builder.build();
    }

    /** 创建项目级 ContentRetriever（Qdrant 未启用时返回 null）。
     *  maxResults=3：3 篇足够覆盖核心知识，减少输入 token + 加速检索 */
    private ContentRetriever createContentRetriever(Integer projectId) {
        if (ingestService == null || projectId == null) {
            return null;
        }
        EmbeddingStore store = ingestService.getEmbeddingStore(projectId);
        if (store == null) {
            return null;
        }
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(modelManager.getEmbeddingModel())
                .maxResults(3)
                .minScore(0.70)
                .build();
    }

    /**
     * Agent 助手 + 自定义 system prompt（快捷指令注入）
     * 工具/RAG/记忆全部复用，仅 system prompt 不同（provider 方式，接口不得加 @SystemMessage 注解）
     */
    public AgentAssistant createAgentWithSystemPrompt(Integer projectId, String systemPrompt,
                                                         java.util.Map<ToolSpecification, ToolExecutor> tools) {
        ChatModel model = modelManager.getChatModel();
        StreamingChatModel streamingModel = modelManager.getStreamingChatModel();
        var builder = AiServices.builder(AgentAssistant.class)
                .chatModel(model)
                .streamingChatModel(streamingModel)
                .chatMemoryProvider(memoryProvider())
                .systemMessageProvider(memoryId -> systemPrompt)
                .hallucinatedToolNameStrategy(HALLUCINATED_TOOL_STRATEGY);
        ContentRetriever retriever = createContentRetriever(projectId);
        if (retriever != null) {
            builder.contentRetriever(retriever);
        }
        if (tools != null && !tools.isEmpty()) {
            builder.tools(tools);
        }
        if (mcpToolProvider != null) {
            builder.toolProvider(mcpToolProvider);
        }
        return builder.build();
    }
}
