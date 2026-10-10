package com.mokatest.platform.demos.ai.config;

import com.mokatest.platform.demos.ai.domain.AiUsageLog;
import com.mokatest.platform.demos.ai.mapper.AiUsageLogMapper;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

/**
 * AI 可观测性监听器：替代旧 AiGatewayService.writeLog() 手动调用。
 *
 * 注册到 ChatModel 后每次 LLM 调用自动触发，不遗漏。
 */
@Component
public class AiUsageListener implements ChatModelListener {

    private static final Logger log = LoggerFactory.getLogger(AiUsageListener.class);

    @Autowired
    private AiUsageLogMapper usageLogMapper;

    @Override
    public void onRequest(ChatModelRequestContext ctx) {
        ctx.attributes().put("_startTime", System.currentTimeMillis());
    }

    @Override
    public void onResponse(ChatModelResponseContext ctx) {
        try {
            AiUsageLog record = baseLog(ctx.attributes(), 1, null);
            ChatResponse resp = ctx.chatResponse();
            if (resp != null) {
                TokenUsage u = resp.tokenUsage();
                if (u != null) {
                    record.setTokens(u.totalTokenCount());
                    record.setInputTokens(u.inputTokenCount());
                    record.setOutputTokens(u.outputTokenCount());
                }
                if (resp.finishReason() != null) {
                    record.setFinishReason(resp.finishReason().toString());
                }
                if (resp.metadata() != null) {
                    record.setModelName(resp.metadata().modelName());
                }
            }
            usageLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("写 AI 用量日志失败: {}", e.getMessage());
        }
    }

    @Override
    public void onError(ChatModelErrorContext ctx) {
        try {
            AiUsageLog record = baseLog(ctx.attributes(), 0,
                    abbreviate(ctx.error() != null ? ctx.error().getMessage() : "unknown", 1000));
            usageLogMapper.insert(record);
        } catch (Exception e) {
            log.warn("写 AI 错误日志失败: {}", e.getMessage());
        }
    }

    /** 从 attributes 提取业务上下文 + 耗时，构建日志基础记录。
     *  attributes 里通常只有 _startTime；scene/userId/projectId 改由 AiUsageContext（ThreadLocal）兜底，
     *  全无来源时 scene 落 "UNKNOWN"（ai_usage_log.scene 是 NOT NULL，不能插 null）。 */
    private AiUsageLog baseLog(Map<Object, Object> attrs, int success, String errorMsg) {
        AiUsageLog r = new AiUsageLog();
        String userId = (String) attrs.get("userId");
        Integer teamId = attrs.get("teamId") instanceof Integer ti ? ti : null;
        Integer projectId = attrs.get("projectId") instanceof Integer pi ? pi : null;
        String scene = (String) attrs.get("scene");
        if (scene == null) scene = AiUsageContext.scene();
        if (userId == null) userId = AiUsageContext.userId();
        if (projectId == null) projectId = AiUsageContext.projectId();
        r.setUserId(userId);
        r.setTeamId(teamId);
        r.setProjectId(projectId);
        r.setScene(scene != null ? scene : "UNKNOWN");
        r.setPromptSummary(abbreviate((String) attrs.get("promptSummary"), 500));
        r.setSuccess(success);
        r.setErrorMsg(errorMsg);
        Object start = attrs.get("_startTime");
        if (start instanceof Long s) {
            r.setDurationMs((int) (System.currentTimeMillis() - s));
        }
        r.setCreateTime(new Date());
        return r;
    }

    private String abbreviate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }
}
