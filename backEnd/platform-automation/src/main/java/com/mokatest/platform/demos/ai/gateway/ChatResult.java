package com.mokatest.platform.demos.ai.gateway;

import lombok.Data;

/**
 * AI 调用结果
 */
@Data
public class ChatResult {

    /** 模型输出文本 */
    private String content;

    /** 消耗 tokens（usage.total_tokens，可能为 null） */
    private Integer tokens;

    /** 耗时（毫秒） */
    private Integer durationMs;

    public ChatResult(String content, Integer tokens, Integer durationMs) {
        this.content = content;
        this.tokens = tokens;
        this.durationMs = durationMs;
    }
}
