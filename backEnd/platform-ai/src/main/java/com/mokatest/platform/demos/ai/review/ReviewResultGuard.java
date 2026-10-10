package com.mokatest.platform.demos.ai.review;

import com.mokatest.platform.demos.ai.domain.ReviewResult;
import dev.langchain4j.guardrail.JsonExtractorOutputGuardrail;

/**
 * 评审结果输出护栏（langchain4j 内置 JsonExtractorOutputGuardrail）。
 *
 * 检查 LLM 输出是否能成功反序列化为 ReviewResult 对象。
 * 解析失败时框架自动 reprompt LLM 重新输出，不手动 parse JSON。
 */
public class ReviewResultGuard extends JsonExtractorOutputGuardrail<ReviewResult> {
    public ReviewResultGuard() {
        super(ReviewResult.class);
    }
}
