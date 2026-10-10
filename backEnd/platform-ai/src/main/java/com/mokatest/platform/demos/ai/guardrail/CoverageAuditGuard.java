package com.mokatest.platform.demos.ai.guardrail;

import com.mokatest.platform.demos.ai.domain.CoverageAuditReport;
import dev.langchain4j.guardrail.JsonExtractorOutputGuardrail;

/**
 * 覆盖审查结论输出护栏（langchain4j 内置 JsonExtractorOutputGuardrail）。
 *
 * 检查 LLM 输出是否能成功反序列化为 CoverageAuditReport 对象。
 * 解析失败时框架自动 reprompt LLM 重新输出，不手动 parse JSON。
 */
public class CoverageAuditGuard extends JsonExtractorOutputGuardrail<CoverageAuditReport> {
    public CoverageAuditGuard() {
        super(CoverageAuditReport.class);
    }
}
