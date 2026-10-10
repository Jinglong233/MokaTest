package com.mokatest.platform.demos.ai.guardrail;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.guardrail.JsonExtractorOutputGuardrail;

import java.util.List;
import java.util.Map;

/**
 * 用例草稿输出护栏（spec 4.2）。
 *
 * ponytail: CaseGenerationAssistant.generate 返回 TokenStream（流式）。流式场景挂输出护栏
 * 会导致 onPartialResponse 被缓冲、校验通过后才重放（丢失渐进渲染 UX），因此草稿解析 + 重试
 * 由 AiCaseService.handleComplete 手工完成（保留流式 UX 的取舍）。此类供未来同步结构化输出场景使用。
 */
public class CaseDraftGuard extends JsonExtractorOutputGuardrail<List<Map<String, Object>>> {
    public CaseDraftGuard() {
        super(new TypeReference<>() {});
    }
}
