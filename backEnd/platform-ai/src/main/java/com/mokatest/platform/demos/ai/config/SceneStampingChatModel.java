package com.mokatest.platform.demos.ai.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 场景标记 ChatModel 装饰器：在调用线程上打 scene 标记（AiUsageContext）后委托真实模型。
 *
 * 解决 agentic 并行/异步工作流下 ThreadLocal 不跨线程的问题——无论子 Agent 跑在哪个
 * 线程池线程上，模型调用本身经过装饰器，scene 都能正确落入用量日志。
 *
 * 附带响应透视：模型返回空/异常内容时 WARN 打印原始响应关键信息（finishReason、
 * 正文截断），避免结构化解析失败时只能看到 "Failed to parse null" 而看不到模型实际返回。
 */
public class SceneStampingChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(SceneStampingChatModel.class);

    private final ChatModel delegate;
    private final String scene;

    public SceneStampingChatModel(ChatModel delegate, String scene) {
        this.delegate = delegate;
        this.scene = scene;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        AiUsageContext.set(scene, AiUsageContext.projectId(), AiUsageContext.userId());
        try {
            ChatResponse resp = delegate.chat(request);
            inspect(resp);
            return resp;
        } finally {
            AiUsageContext.clear();
        }
    }

    /** 空正文/异常响应时打印诊断信息（正常响应零开销） */
    private void inspect(ChatResponse resp) {
        try {
            AiMessage msg = resp != null ? resp.aiMessage() : null;
            String text = msg != null ? msg.text() : null;
            if (text == null || text.isBlank()) {
                log.warn("[{}] 模型返回空正文 | finishReason={} hasThinking={} toolCalls={}",
                        scene,
                        resp != null ? resp.finishReason() : null,
                        msg != null && msg.thinking() != null,
                        msg != null && msg.toolExecutionRequests() != null && !msg.toolExecutionRequests().isEmpty());
            } else if (log.isDebugEnabled()) {
                log.debug("[{}] 模型返回 {} 字符: {}", scene, text.length(),
                        text.substring(0, Math.min(200, text.length())));
            }
        } catch (Exception ignored) {
            // 透视日志绝不能影响主流程
        }
    }
}

