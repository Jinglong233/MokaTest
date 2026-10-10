package com.mokatest.platform.demos.ai.config;

import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import com.mokatest.platform.demos.ai.util.AesCryptoUtil;
import com.mokatest.platform.demos.exception.BusinessException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LangChain4j 模型管理器：替代旧 AiGatewayService 的模型缓存与创建逻辑。
 *
 * 职责：
 * 1. 从 AiConfigService 读取系统级 AI 配置
 * 2. 创建并缓存 ChatModel / StreamingChatModel / EmbeddingModel 实例
 * 3. 配置变更时失效缓存（invalidate）
 * 4. 自动注入 ChatModelListener 可观测性
 *
 * 不再手动管理限流、重试、API Key 解密、用量日志 —
 * 重试由框架 maxRetries 处理，用量日志由 AiUsageListener 自动触发。
 */
@Component
public class ModelManager {

    private static final Logger log = LoggerFactory.getLogger(ModelManager.class);

    /** Thinking 控制策略：AUTO（按厂商适配）/ OFF（生成类场景强制关）/ ON（对话场景保留） */
    public enum ThinkingPolicy {
        AUTO, OFF, ON
    }

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private AiUsageListener aiUsageListener;

    /** HTTP 级请求/响应日志（联调期默认开，定位"模型返回空"类问题；稳定后可设 mokatest.ai.log-http=false 关闭） */
    @org.springframework.beans.factory.annotation.Value("${mokatest.ai.log-http:true}")
    private boolean logHttp;

    /**
     * 知识库摄取缓存（Qdrant 未启用时无此 Bean，required=false + ObjectProvider 懒解析，
     * 同时避免 RagConfig→ModelManager→KnowledgeIngestService 的循环依赖）
     */
    @Autowired
    private org.springframework.beans.factory.ObjectProvider<com.mokatest.platform.demos.ai.rag.KnowledgeIngestService> knowledgeIngestService;

    /** 模型实例缓存（Fix⑤：按 configId 维度缓存，多项目不互相顶） */
    private final java.util.concurrent.ConcurrentHashMap<Long, ChatModel> chatModelCacheMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, StreamingChatModel> streamingModelCacheMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, StreamingChatModel> deterministicStreamingCacheMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, ChatModel> reviewChatModelCacheMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<Long, EmbeddingModel> embeddingModelCacheMap = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile Long cachedConfigId;

    // ==================== ChatModel ====================

    public ChatModel getChatModel() {
        return getChatModel(null);
    }

    public ChatModel getChatModel(AiConfig override) {
        AiConfig config = override != null ? override : requireConfig();
        if (override != null) {
            return buildChatModel(config);
        }
        // Fix⑤：按 configId 缓存，多项目不互相顶
        return chatModelCacheMap.computeIfAbsent(config.getId(),
                id -> { log.info("ChatModel 已创建 | model={}", config.getChatModel()); return buildChatModel(config); });
    }

    // ==================== StreamingChatModel ====================

    public StreamingChatModel getStreamingChatModel() {
        AiConfig config = requireConfig();
        return streamingModelCacheMap.computeIfAbsent(config.getId(),
                id -> { log.info("StreamingChatModel 已创建 | model={}", config.getChatModel()); return buildStreamingModel(config); });
    }

    /**
     * 用例生成专用：确定性流式模型 + maxTokens 限制。
     * 非推理模型设 maxTokens 防止隐藏 reasoning 过度消耗 token（4796→3000，省 ~37% 时间）。
     * 推理模型不设 maxTokens（thinking+正文总预算，设小会掐断）。
     * 不走缓存：maxTokens 按调用方需求变化，每次新建。
     */
    public StreamingChatModel createCaseGenStreamingModel(int maxTokens) {
        AiConfig config = requireConfig();
        // thinking 关 → 下发 maxTokens（防隐藏 reasoning 消耗）；thinking 开 → 不设（防掐断）
        boolean thinkingOff = isThinkingOff(config, ThinkingPolicy.OFF);
        return buildStreamingModel(config, 0.0, thinkingOff ? maxTokens : null, ThinkingPolicy.OFF);
    }

    /** 确定性流式模型（temperature=0）：评审/用例生成等要求准确性的场景专用。
     *  推理模型（reasoner 类）不支持 temperature 参数，自动跳过不设置。
     *  走缓存：与 getStreamingChatModel() 一样用 double-check，避免每次请求 new OpenAiStreamingChatModel */
    public StreamingChatModel createDeterministicStreamingChatModel() {
        AiConfig config = requireConfig();
        return deterministicStreamingCacheMap.computeIfAbsent(config.getId(),
                id -> { log.info("DeterministicStreamingChatModel 已创建 | model={}", config.getChatModel()); return buildStreamingModel(config, 0.0, null, ThinkingPolicy.OFF); });
    }

    /** 确定性同步模型（temperature=0）：解析重试/覆盖修正等反馈调用专用。
     *  不缓存：调用方场景分散，每次场景标记（SceneStampingChatModel）不同 */
    public ChatModel createDeterministicChatModel() {
        return buildChatModel(requireConfig(), 0.0, 120, ThinkingPolicy.OFF);
    }

    /** 评审专用模型：确定性 + 300s 超时（结构化输出需生成完整检查表，120s 不够）。
     *  走缓存：用例生成的 extractCases/OutputGuardrails 每次都用同一实例 */
    public ChatModel createReviewChatModel() {
        AiConfig config = requireConfig();
        return reviewChatModelCacheMap.computeIfAbsent(config.getId(),
                id -> { log.info("ReviewChatModel 已创建 | model={}", config.getChatModel()); return buildChatModel(config, 0.0, 300, ThinkingPolicy.OFF); });
    }

    /** 意图分类专用：轻量模型（thinking 强制关 + maxTokens=10 + 30s 超时） */
    public ChatModel createClassifierModel() {
        AiConfig config = requireConfig();
        OpenAiChatModel.OpenAiChatModelBuilder b = OpenAiChatModel.builder()
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(AesCryptoUtil.decrypt(config.getApiKey()))
                .modelName(config.getChatModel())
                .timeout(Duration.ofSeconds(30))
                .maxRetries(2)
                .maxTokens(10)
                .logRequests(logHttp)
                .logResponses(logHttp)
                .listeners(List.of(aiUsageListener));
        if (!isReasoningModel(config)) {
            b.temperature(0.0);
        }
        applyThinkingParams(b, config, ThinkingPolicy.OFF);
        return b.build();
    }

    /** 带场景标记的确定性模型：agentic 工作流子 Agent 用（线程无关的用量日志 scene 归属） */
    public ChatModel createDeterministicChatModel(String scene) {
        return new SceneStampingChatModel(createDeterministicChatModel(), scene);
    }

    // ==================== EmbeddingModel ====================
    public EmbeddingModel getEmbeddingModel() {
        return getEmbeddingModel(resolveProjectId());
    }

    /** 带 projectId 重载：工具执行线程无 ThreadLocal 时显式传入（§7.2 search_knowledge 修复） */
    public EmbeddingModel getEmbeddingModel(Integer projectId) {
        AiConfig config = aiConfigService.getActiveConfig(projectId);
        if (config == null) {
            throw new BusinessException("AI 功能未启用或未配置模型，请联系管理员");
        }
        if (config.getEmbeddingModel() == null || config.getEmbeddingModel().isEmpty()) {
            throw new BusinessException("未配置向量模型，无法生成 embedding");
        }
        return embeddingModelCacheMap.computeIfAbsent(config.getId(),
                id -> { log.info("EmbeddingModel 已创建 | model={}", config.getEmbeddingModel()); return buildEmbeddingModel(config); });
    }

    /** 是否配置了向量模型 */
    public boolean hasEmbeddingModel() {
        try {
            AiConfig config = aiConfigService.getActiveConfig(resolveProjectId());
            return config != null && config.getEmbeddingModel() != null && !config.getEmbeddingModel().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 是否启用 JSON 模式（response_format=json_object）：按档案显式声明，默认 false */
    public boolean supportsJsonMode() {
        AiConfig config = requireConfig();
        return config.getSupportJsonMode() != null && config.getSupportJsonMode() == 1;
    }

    // ==================== 缓存失效 ====================

    /** 配置变更时调用（AiConfigServiceImpl 在 save/activate/deactivate 后触发） */
    public void invalidate() {
        chatModelCacheMap.clear();
        streamingModelCacheMap.clear();
        deterministicStreamingCacheMap.clear();
        reviewChatModelCacheMap.clear();
        embeddingModelCacheMap.clear();
        log.info("LangChain4j 模型缓存已清空（全作用域）");
        // 联动清知识库 ingestor 缓存（内含固化的旧 EmbeddingModel）
        com.mokatest.platform.demos.ai.rag.KnowledgeIngestService ingest = knowledgeIngestService.getIfAvailable();
        if (ingest != null) {
            ingest.clearCache();
        }
    }

    // ==================== 构建方法 ====================

    private ChatModel buildChatModel(AiConfig config) {
        return buildChatModel(config, null, 120, ThinkingPolicy.AUTO);
    }

    private ChatModel buildChatModel(AiConfig config, Double temperatureOverride, int timeoutSeconds) {
        return buildChatModel(config, temperatureOverride, timeoutSeconds, ThinkingPolicy.AUTO);
    }

    /**
     * 创建 ChatModel。
     * 配置精简原则：模型参数不下发用户可调开关——temperature 仅由调用方按场景强制（准确性场景 0.0），
     * maxTokens 永不设置（推理模型该预算是「思考+正文」总和，设小会掐断输出），
     * 不下发 responseFormat（json_object 对兼容端点是兼容性炸弹；结构化输出走 prompt 契约 + 解析校验）。
     * 超时不做用户配置：同步 120s（只用于短输出控制类调用），长生成一律走流式（无整包超时问题）。
     *
     * @param temperatureOverride 非 null 时强制温度（准确性场景传 0.0）；推理模型始终不设置温度
     * @param policy thinking 控制策略（生成类场景传 OFF，对话场景传 AUTO）
     */
    private ChatModel buildChatModel(AiConfig config, Double temperatureOverride, int timeoutSeconds, ThinkingPolicy policy) {
        OpenAiChatModel.OpenAiChatModelBuilder b = OpenAiChatModel.builder()
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(AesCryptoUtil.decrypt(config.getApiKey()))
                .modelName(config.getChatModel())
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .maxRetries(2)
                .logRequests(logHttp)
                .logResponses(logHttp)
                .listeners(List.of(aiUsageListener));
        // 对话/通用场景默认温度 0.7，准确性场景由调用方 override 为 0.0
        if (!isReasoningModel(config)) {
            b.temperature(temperatureOverride != null ? temperatureOverride : 0.7);
        }
        applyThinkingParams(b, config, policy);
        return b.build();
    }

    private StreamingChatModel buildStreamingModel(AiConfig config) {
        return buildStreamingModel(config, null);
    }

    private StreamingChatModel buildStreamingModel(AiConfig config, Double temperatureOverride) {
        return buildStreamingModel(config, temperatureOverride, null);
    }

    private StreamingChatModel buildStreamingModel(AiConfig config, Double temperatureOverride, Integer maxTokens) {
        return buildStreamingModel(config, temperatureOverride, maxTokens, ThinkingPolicy.AUTO);
    }

    private StreamingChatModel buildStreamingModel(AiConfig config, Double temperatureOverride, Integer maxTokens, ThinkingPolicy policy) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder b = OpenAiStreamingChatModel.builder()
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(AesCryptoUtil.decrypt(config.getApiKey()))
                .modelName(config.getChatModel())
                // 流式是长生成的标准形态（token 持续到达即活着）：超时只是兜底防端点彻底僵死，给足 600s
                .timeout(Duration.ofSeconds(600))
                .listeners(List.of(aiUsageListener));
        // maxTokens 仅在调用方显式指定时下发（用例生成限制隐藏 reasoning 消耗）
        if (maxTokens != null) {
            b.maxTokens(maxTokens);
        }
        // 对话场景默认温度 0.7，准确性场景由调用方 override 为 0.0
        if (!isReasoningModel(config)) {
            b.temperature(temperatureOverride != null ? temperatureOverride : 0.7);
        }
        applyThinkingParams(b, config, policy);
        return b.build();
    }

    private EmbeddingModel buildEmbeddingModel(AiConfig config) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(normalizeBaseUrl(config.getBaseUrl()))
                .apiKey(AesCryptoUtil.decrypt(config.getApiKey()))
                .modelName(config.getEmbeddingModel())
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * 推理模型检测（公共）：供 AssistantFactory 等调用方按场景决定是否关闭 thinking。
     */
    public boolean isReasoningModel() {
        return isReasoningModel(requireConfig());
    }

    // ==================== 工具 ====================

    private AiConfig requireConfig() {
        Integer projectId = resolveProjectId();
        AiConfig config = aiConfigService.getActiveConfig(projectId);
        if (config == null) {
            throw new BusinessException("AI 功能未启用或未配置模型，请联系管理员");
        }
        return config;
    }

    /** 从线程上下文解析项目 ID（纯项目级：无 projectId = 无档案可用，报错而非静默降级） */
    private Integer resolveProjectId() {
        try {
            Integer pid = com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder.getProjectId();
            if (pid == null) {
                log.error("[ModelManager] 线程上下文无 projectId，该项目 AI 功能不可用（纯项目级无平台档案回落）");
                throw new BusinessException("无法确定当前项目，AI 功能不可用。请确保在项目上下文中调用。");
            }
            return pid;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[ModelManager] 解析项目上下文失败: {}", e.getMessage());
            throw new BusinessException("无法确定当前项目，AI 功能不可用");
        }
    }

    /**
     * 推理模型检测（仅关不掉的型号）：不支持 temperature 参数，thinking 强制开启。
     * GLM/Qwen3 等支持 temperature 的混合推理模型不在此列——thinking 由 thinking_mode 配置控制。
     */
    private static boolean isReasoningModel(AiConfig config) {
        if (config.getChatModel() == null) return false;
        String name = config.getChatModel().toLowerCase();
        if (name.contains("reasoner")) return true;
        if (name.contains("deepseek") && name.contains("r1")) return true;
        if (name.contains("qwq")) return true;
        if (name.contains("o1-") || name.contains("o3-")) return true;
        return false;
    }

    // ==================== Thinking 控制 ====================

    /** 向 ChatModel builder 注入 thinking 控制参数 */
    private void applyThinkingParams(OpenAiChatModel.OpenAiChatModelBuilder b, AiConfig config, ThinkingPolicy policy) {
        Map<String, Object> params = buildThinkingCustomParams(config, policy);
        if (params != null) {
            b.defaultRequestParameters(OpenAiChatRequestParameters.builder().customParameters(params).build());
        }
    }

    /** 向 StreamingChatModel builder 注入 thinking 控制参数 */
    private void applyThinkingParams(OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder b, AiConfig config, ThinkingPolicy policy) {
        Map<String, Object> params = buildThinkingCustomParams(config, policy);
        if (params != null) {
            b.defaultRequestParameters(OpenAiChatRequestParameters.builder().customParameters(params).build());
        }
    }

    /**
     * 计算需要注入的 thinking 控制参数（按厂商适配）。
     * GLM: customParameters 注入 {"thinking": {"type": "disabled"}}
     * Qwen3: customParameters 注入 {"enable_thinking": false}
     * reasoner/o 系列: log.warn，不注入（关不掉）
     */
    private Map<String, Object> buildThinkingCustomParams(AiConfig config, ThinkingPolicy policy) {
        if (!isThinkingOff(config, policy)) return null;
        String name = config.getChatModel() != null ? config.getChatModel().toLowerCase() : "";
        String baseUrl = config.getBaseUrl() != null ? config.getBaseUrl().toLowerCase() : "";
        // GLM（混合推理模型）
        if (baseUrl.contains("zhipu") || baseUrl.contains("bigmodel") || name.contains("glm")) {
            Map<String, Object> params = new HashMap<>();
            params.put("thinking", Map.of("type", "disabled"));
            return params;
        }
        // Qwen3
        if (name.contains("qwen3")) {
            Map<String, Object> params = new HashMap<>();
            params.put("enable_thinking", false);
            return params;
        }
        // 关不掉的推理模型
        if (isUnstoppableReasoning(name)) {
            log.warn("模型 {} 的 thinking 不可关闭，生成类场景建议更换模型", config.getChatModel());
        }
        return null;
    }

    /** 判定本次请求 thinking 是否实际关闭 */
    private boolean isThinkingOff(AiConfig config, ThinkingPolicy policy) {
        String mode = config.getThinkingMode();
        if ("DISABLED".equalsIgnoreCase(mode)) return true;
        if ("ENABLED".equalsIgnoreCase(mode)) return false;
        // AUTO：按场景策略
        if (policy == ThinkingPolicy.OFF) {
            String name = config.getChatModel() != null ? config.getChatModel().toLowerCase() : "";
            return !isUnstoppableReasoning(name);
        }
        return false;
    }

    /** 关不掉的推理模型（不支持 temperature，thinking 强制开启） */
    private static boolean isUnstoppableReasoning(String name) {
        if (name.contains("reasoner")) return true;
        if (name.contains("deepseek") && name.contains("r1")) return true;
        if (name.contains("qwq")) return true;
        if (name.contains("o1-") || name.contains("o3-")) return true;
        // Qwen3 MoE 变体（命名含总参+活跃参规格如 -2.4t-a95b）：DashScope 强制 thinking 不可关闭
        if (name.contains("qwen3") && name.matches(".*-\\d+\\.\\d+t-a\\d+b.*")) return true;
        return false;
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            throw new BusinessException("AI 服务地址未配置");
        }
        String url = baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
