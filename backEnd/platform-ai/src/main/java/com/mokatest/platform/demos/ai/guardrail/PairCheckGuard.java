package com.mokatest.platform.demos.ai.guardrail;

import com.mokatest.platform.demos.ai.domain.PairCheckResult;
import dev.langchain4j.guardrail.JsonExtractorOutputGuardrail;

/**
 * B 层窄判断输出护栏（langchain4j 内置 JsonExtractorOutputGuardrail）。
 *
 * 检查 LLM 输出是否能成功反序列化为 PairCheckResult 对象。
 * PairCheckAssistant 返回 POJO（非 TokenStream），JsonExtractor 安全使用。
 */
public class PairCheckGuard extends JsonExtractorOutputGuardrail<PairCheckResult> {
    public PairCheckGuard() {
        super(PairCheckResult.class);
    }
}
