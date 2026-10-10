package com.mokatest.platform.demos.ai.guardrail;

import dev.langchain4j.guardrail.InputGuardrail;
import dev.langchain4j.guardrail.InputGuardrailRequest;
import dev.langchain4j.guardrail.InputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 输入护栏：检测提示注入攻击。
 *
 * LLM 调用前执行，检测到注入模式时直接 fatal 阻止调用。
 */
public class PromptInjectionGuard implements InputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuard.class);

    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        String text = request.userMessage().singleText();
        String lower = text != null ? text.toLowerCase() : "";

        if (lower.contains("忽略以上指令") || lower.contains("ignore previous instructions")
                || lower.contains("ignore all previous") || lower.contains("disregard the above")) {
            log.warn("检测到提示注入攻击: {}", text.length() > 100 ? text.substring(0, 100) + "..." : text);
            return fatal("检测到提示注入攻击，请求已被拦截");
        }
        return success();
    }
}
