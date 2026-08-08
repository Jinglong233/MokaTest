package com.mokatest.platform.demos.ai.gateway;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.domain.AiUsageLog;
import com.mokatest.platform.demos.ai.mapper.AiUsageLogMapper;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import com.mokatest.platform.demos.ai.util.AesCryptoUtil;
import com.mokatest.platform.demos.exception.BusinessException;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.BufferedSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * AI 统一网关：平台内所有大模型调用的唯一出口
 *
 * 职责：
 * 1. 读取系统级 AI 配置（未启用时抛业务异常）
 * 2. 封装 OpenAI 兼容协议（chat / chatStream / embed）
 * 3. 每用户限流（10 次/分钟）
 * 4. 写 ai_usage_log（不记录 prompt 全文）
 *
 * 注意：调用方必须保证 options.projectId 来自请求头上下文解析（ProjectContextHolder），
 * 不接受前端 body 传参，避免跨项目写日志/串数据。
 */
@Component
public class AiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final int MAX_RETRY = 1;

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private AiUsageLogMapper usageLogMapper;

    @Autowired
    private AiRateLimiter rateLimiter;

    // ==================== 对外能力 ====================

    /**
     * 同步对话调用
     *
     * @param messages 消息列表
     * @param options  调用选项（scene 必填）
     * @return 模型输出
     */
    public ChatResult chat(List<ChatMessage> messages, ChatOptions options) {
        checkRateLimit(options);
        AiConfig config = requireConfig(null);
        long start = System.currentTimeMillis();
        try {
            ChatResult result = doChatWithRetry(config, messages, options);
            writeLog(options, result.getTokens(), (int) (System.currentTimeMillis() - start), true, null);
            return result;
        } catch (Exception e) {
            writeLog(options, null, (int) (System.currentTimeMillis() - start), false, e.getMessage());
            throw wrap(e);
        }
    }

    /**
     * 流式对话调用（SSE）
     *
     * @param messages  消息列表
     * @param options   调用选项
     * @param onChunk   每个增量文本回调
     * @param onComplete 完成回调（入参为完整文本）
     * @param onError   异常回调
     */
    public void chatStream(List<ChatMessage> messages, ChatOptions options,
                           Consumer<String> onChunk, Consumer<String> onComplete, Consumer<Throwable> onError) {
        chatStream(messages, options, onChunk, onComplete, onError, null);
    }

    /**
     * 流式对话调用（SSE，可暴露底层 Call 供外部取消）
     *
     * @param callHook 底层 okhttp Call 创建后回调（用于停止接口取消请求），可为 null
     */
    public void chatStream(List<ChatMessage> messages, ChatOptions options,
                           Consumer<String> onChunk, Consumer<String> onComplete, Consumer<Throwable> onError,
                           Consumer<okhttp3.Call> callHook) {
        try {
            checkRateLimit(options);
            AiConfig config = requireConfig(null);
            long start = System.currentTimeMillis();
            StringBuilder full = new StringBuilder();

            JSONObject body = buildChatBody(config, messages, options);
            body.put("stream", true);
            Request request = new Request.Builder()
                    .url(normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions")
                    .addHeader("Authorization", "Bearer " + AesCryptoUtil.decrypt(config.getApiKey()))
                    .post(RequestBody.create(JSON.toJSONString(body), JSON_MEDIA))
                    .build();

            OkHttpClient client = buildClient(config, true, options);
            okhttp3.Call call = client.newCall(request);
            if (callHook != null) {
                callHook.accept(call);
            }
            try (Response response = call.execute()) {
                if (!response.isSuccessful()) {
                    throw new BusinessException("AI 服务返回异常（HTTP " + response.code() + "）");
                }
                BufferedSource source = response.body().source();
                while (!source.exhausted()) {
                    String line = source.readUtf8Line();
                    if (line == null || !line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) {
                        break;
                    }
                    String delta = parseStreamDelta(data);
                    if (delta != null && !delta.isEmpty()) {
                        full.append(delta);
                        onChunk.accept(delta);
                    }
                }
            }
            writeLog(options, null, (int) (System.currentTimeMillis() - start), true, null);
            onComplete.accept(full.toString());
        } catch (Exception e) {
            writeLogQuietly(options, e.getMessage());
            onError.accept(wrap(e));
        }
    }

    /**
     * 文本向量化
     *
     * @param text    文本
     * @param options 调用选项
     * @return 向量；未配置向量模型时抛业务异常（调用方自行降级）
     */
    public float[] embed(String text, ChatOptions options) {
        checkRateLimit(options);
        AiConfig config = requireConfig(null);
        if (config.getEmbeddingModel() == null || config.getEmbeddingModel().isEmpty()) {
            throw new BusinessException("未配置向量模型，无法生成 embedding");
        }
        long start = System.currentTimeMillis();
        try {
            JSONObject body = new JSONObject();
            body.put("model", config.getEmbeddingModel());
            body.put("input", text);
            Request request = new Request.Builder()
                    .url(normalizeBaseUrl(config.getBaseUrl()) + "/embeddings")
                    .addHeader("Authorization", "Bearer " + AesCryptoUtil.decrypt(config.getApiKey()))
                    .post(RequestBody.create(JSON.toJSONString(body), JSON_MEDIA))
                    .build();
            OkHttpClient client = buildClient(config, false);
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new BusinessException("向量服务返回异常（HTTP " + response.code() + "）");
                }
                JSONObject resp = JSON.parseObject(response.body().string());
                JSONArray arr = resp.getJSONArray("data").getJSONObject(0).getJSONArray("embedding");
                float[] vector = new float[arr.size()];
                for (int i = 0; i < arr.size(); i++) {
                    vector[i] = arr.getFloatValue(i);
                }
                writeLog(options, resp.getJSONObject("usage") == null ? null : resp.getJSONObject("usage").getInteger("total_tokens"),
                        (int) (System.currentTimeMillis() - start), true, null);
                return vector;
            }
        } catch (Exception e) {
            writeLog(options, null, (int) (System.currentTimeMillis() - start), false, e.getMessage());
            throw wrap(e);
        }
    }

    /**
     * 使用指定配置做连通性测试（不读取库中配置，供配置页「测试」按钮）
     */
    public ChatResult testConnection(AiConfig rawConfig) {
        AiConfig config = new AiConfig();
        config.setBaseUrl(rawConfig.getBaseUrl());
        config.setApiKey(rawConfig.getApiKey());
        config.setChatModel(rawConfig.getChatModel());
        config.setMaxTokens(rawConfig.getMaxTokens() == null ? 32 : Math.min(rawConfig.getMaxTokens(), 32));
        config.setTemperature(rawConfig.getTemperature());
        config.setTimeoutMs(rawConfig.getTimeoutMs() == null ? 30000 : rawConfig.getTimeoutMs());
        // 测试入参的 apiKey 可能是打码值（包含 ****）或密文，统一走解密（解不开按原文）
        ChatOptions options = new ChatOptions();
        options.setScene("CONFIG_TEST");
        options.setPromptSummary("连通性测试");
        long start = System.currentTimeMillis();
        try {
            ChatResult result = doChatWithRetry(config, java.util.Collections.singletonList(ChatMessage.user("ping")), options);
            result.setDurationMs((int) (System.currentTimeMillis() - start));
            return result;
        } catch (Exception e) {
            throw wrap(e);
        }
    }

    // ==================== 内部实现 ====================

    private void checkRateLimit(ChatOptions options) {
        if (!rateLimiter.acquire(options == null ? null : options.getUserId())) {
            throw new BusinessException("AI 调用过于频繁，请稍后再试（每分钟最多 10 次）");
        }
    }

    private AiConfig requireConfig(AiConfig override) {
        if (override != null) {
            return override;
        }
        AiConfig config = aiConfigService.getActiveConfig();
        if (config == null) {
            throw new BusinessException("AI 功能未启用或未配置模型，请联系平台管理员");
        }
        return config;
    }

    private ChatResult doChatWithRetry(AiConfig config, List<ChatMessage> messages, ChatOptions options) {
        Exception last = null;
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                return doChat(config, messages, options);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                last = e;
                log.warn("AI chat 调用失败，第 {} 次重试", attempt + 1);
            }
        }
        throw wrap(last);
    }

    private ChatResult doChat(AiConfig config, List<ChatMessage> messages, ChatOptions options) throws Exception {
        long start = System.currentTimeMillis();
        JSONObject body = buildChatBody(config, messages, options);
        Request request = new Request.Builder()
                .url(normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions")
                .addHeader("Authorization", "Bearer " + AesCryptoUtil.decrypt(config.getApiKey()))
                .post(RequestBody.create(JSON.toJSONString(body), JSON_MEDIA))
                .build();
        OkHttpClient client = buildClient(config, false, options);
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errBody = response.body() == null ? "" : response.body().string();
                throw new BusinessException("AI 服务返回异常（HTTP " + response.code() + "）：" + abbreviate(errBody, 200));
            }
            JSONObject resp = JSON.parseObject(response.body().string());
            JSONObject message = resp.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message");
            String content = message.getString("content");
            // 推理模型（如 deepseek-reasoner）：max_tokens 较小时 content 可能为空，
            // 答案实际落在 reasoning_content 里，做兜底提取
            if ((content == null || content.isEmpty()) && message.getString("reasoning_content") != null) {
                content = message.getString("reasoning_content");
            }
            Integer tokens = resp.getJSONObject("usage") == null ? null : resp.getJSONObject("usage").getInteger("total_tokens");
            return new ChatResult(content, tokens, (int) (System.currentTimeMillis() - start));
        }
    }

    private JSONObject buildChatBody(AiConfig config, List<ChatMessage> messages, ChatOptions options) {
        JSONObject body = new JSONObject();
        body.put("model", config.getChatModel());
        JSONArray arr = new JSONArray();
        for (ChatMessage msg : messages) {
            JSONObject m = new JSONObject();
            m.put("role", msg.getRole());
            if (msg.getParts() != null) {
                JSONArray parts = new JSONArray();
                for (ChatMessage.ContentPart part : msg.getParts()) {
                    JSONObject p = new JSONObject();
                    p.put("type", part.getType());
                    if ("text".equals(part.getType())) {
                        p.put("text", part.getText());
                    } else if ("image_url".equals(part.getType()) && part.getImageUrl() != null) {
                        JSONObject img = new JSONObject();
                        img.put("url", part.getImageUrl().getUrl());
                        p.put("image_url", img);
                    }
                    parts.add(p);
                }
                m.put("content", parts);
            } else {
                m.put("content", msg.getContent());
            }
            arr.add(m);
        }
        body.put("messages", arr);
        body.put("max_tokens", options != null && options.getMaxTokens() != null ? options.getMaxTokens() : config.getMaxTokens());
        body.put("temperature", options != null && options.getTemperature() != null
                ? options.getTemperature()
                : (config.getTemperature() == null ? 0.3 : config.getTemperature().doubleValue()));
        return body;
    }

    private String parseStreamDelta(String data) {
        try {
            JSONObject chunk = JSON.parseObject(data);
            JSONArray choices = chunk.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                return null;
            }
            JSONObject delta = choices.getJSONObject(0).getJSONObject("delta");
            return delta == null ? null : delta.getString("content");
        } catch (Exception e) {
            return null;
        }
    }

    private OkHttpClient buildClient(AiConfig config, boolean streaming) {
        return buildClient(config, streaming, null);
    }

    private OkHttpClient buildClient(AiConfig config, boolean streaming, ChatOptions options) {
        long timeout = config.getTimeoutMs() == null ? 60000 : config.getTimeoutMs();
        if (options != null && options.getTimeoutMs() != null) {
            timeout = options.getTimeoutMs();
        }
        return new OkHttpClient.Builder()
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(streaming ? Math.max(timeout, 120000) : timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            throw new BusinessException("AI 服务地址未配置");
        }
        String url = baseUrl.trim();
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private void writeLog(ChatOptions options, Integer tokens, Integer durationMs, boolean success, String errorMsg) {
        try {
            AiUsageLog log = new AiUsageLog();
            if (options != null) {
                log.setUserId(options.getUserId());
                log.setTeamId(options.getTeamId());
                log.setProjectId(options.getProjectId());
                log.setScene(options.getScene());
                log.setPromptSummary(abbreviate(options.getPromptSummary(), 500));
            }
            log.setTokens(tokens);
            log.setDurationMs(durationMs);
            log.setSuccess(success ? 1 : 0);
            log.setErrorMsg(abbreviate(errorMsg, 1000));
            log.setCreateTime(new Date());
            usageLogMapper.insert(log);
        } catch (Exception e) {
            AiGatewayService.log.warn("写 AI 用量日志失败: {}", e.getMessage());
        }
    }

    private void writeLogQuietly(ChatOptions options, String errorMsg) {
        writeLog(options, null, null, false, errorMsg);
    }

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max);
    }

    private BusinessException wrap(Exception e) {
        if (e instanceof BusinessException) {
            return (BusinessException) e;
        }
        return new BusinessException("AI 服务调用失败：" + e.getMessage());
    }
}
