package com.mokatest.platform.demos.ai.guardrail;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 用例生成 JSON 验证护栏（langchain4j 教程 09：自定义 OutputGuardrail）。
 *
 * 验证 LLM 输出是否为合法 JSON 且包含 test_cases 数组，失败自动 reprompt。
 * 不使用 JsonExtractorOutputGuardrail（会转换返回类型，与 TokenStream 不兼容）。
 * 流式场景下护栏在 onCompleteResponse 时执行，onPartialResponse 被缓冲后重放。
 */
public class CaseGenJsonValidator implements OutputGuardrail {

    private static final Logger log = LoggerFactory.getLogger(CaseGenJsonValidator.class);
    private static final String REPROMPT_MSG = "请严格按 JSON 格式输出，不要用 markdown 代码块包裹：{\"test_cases\": [{\"caseName\":\"...\",\"testSteps\":[{\"step\":\"...\",\"expected\":\"...\"}],\"caseType\":\"FUNCTION\",\"priority\":\"P0\"}]}";

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        String text = responseFromLLM.text();
        if (text == null || text.isBlank()) {
            log.warn("[性能] OutputGuardrail 失败→将 reprompt | 原因=模型输出为空");
            return reprompt(REPROMPT_MSG, "模型输出为空");
        }
        // strip markdown 代码块围栏（模型常用 ```json ... ``` 包裹输出，导致 JSON 解析失败）
        String cleaned = stripCodeFences(text);
        try {
            JSONObject root = JSON.parseObject(cleaned);
            if (root == null) {
                log.warn("[性能] OutputGuardrail 失败→将 reprompt | 原因=JSON 解析为 null 原始长度={}", text.length());
                return reprompt(REPROMPT_MSG, "JSON 解析结果为 null");
            }
            if (!root.containsKey("test_cases")) {
                log.warn("[性能] OutputGuardrail 失败→将 reprompt | 原因=缺少 test_cases 字段 顶层keys={}", root.keySet());
                return reprompt(REPROMPT_MSG, "缺少 test_cases 字段");
            }
            log.info("[性能] OutputGuardrail 通过 | 原始字符={} 清洗后字符={} test_cases数={}",
                    text.length(), cleaned.length(), root.getJSONArray("test_cases") != null ? root.getJSONArray("test_cases").size() : 0);
            return success();
        } catch (Exception e) {
            log.warn("[性能] OutputGuardrail 失败→将 reprompt | 原因=JSON 解析异常: {} 原始前100字符={}",
                    e.getMessage(), text.length() > 100 ? text.substring(0, 100) : text);
            return reprompt(REPROMPT_MSG, "JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 去除 markdown 代码块围栏：```json ... ``` → ...
     * 模型经常用 ```json\n{...}\n``` 包裹 JSON 输出，导致 JSON.parseObject 失败。
     */
    private static String stripCodeFences(String text) {
        String t = text.trim();
        // 去除开头的 ```json 或 ```
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            } else {
                t = t.substring(3); // ``` without newline
            }
            // 去除可能的语言标识（json, JSON 等）
            t = t.replaceFirst("^(json|JSON|javascript|js)\\s*\\n?", "");
        }
        // 去除结尾的 ```
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
