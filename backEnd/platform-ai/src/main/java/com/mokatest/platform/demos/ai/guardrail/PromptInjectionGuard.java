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

    /** 注入黑名单模式（小写匹配），用户输入与 Skill 模板内容共用 */
    private static final String[] INJECTION_PATTERNS = {
            "忽略以上指令", "忽略上述指令", "无视以上指令", "无视上述指令",
            "ignore previous instructions", "ignore all previous", "disregard the above",
            "disregard previous instructions"
    };

    /** 命中注入模式返回 true，供输入护栏与 Skill 内容校验共用 */
    public static boolean containsInjection(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String pattern : INJECTION_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public InputGuardrailResult validate(InputGuardrailRequest request) {
        String text = request.userMessage().singleText();
        if (containsInjection(text)) {
            log.warn("检测到提示注入攻击: {}", text.length() > 100 ? text.substring(0, 100) + "..." : text);
            return fatal("检测到提示注入攻击，请求已被拦截");
        }
        return success();
    }
}
