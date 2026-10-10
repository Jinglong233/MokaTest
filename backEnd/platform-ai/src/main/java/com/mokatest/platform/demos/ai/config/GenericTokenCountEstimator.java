package com.mokatest.platform.demos.ai.config;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.TokenCountEstimator;

/**
 * 通用 Token 计数器：固定使用 CL100K_BASE 编码，与模型名解耦。
 *
 * LangChain4j 自带的 OpenAiTokenCountEstimator 内置「模型名→编码」映射表，
 * 只认识 OpenAI 系模型，非 OpenAI 模型名（如 glm、deepseek）会直接抛
 * IllegalArgumentException("Model 'xx' is unknown to jtokkit")。
 * CL100K_BASE 是主流 BPE 编码，对其他模型是足够好的近似（仅用于记忆窗口淘汰）。
 */
public class GenericTokenCountEstimator implements TokenCountEstimator {

    /** 与 OpenAI 一致的消息开销近似值 */
    private static final int EXTRA_TOKENS_PER_MESSAGE = 4;
    private static final int EXTRA_TOKENS_PER_REQUEST = 3;

    private static final Encoding ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    @Override
    public int estimateTokenCountInText(String text) {
        return text == null ? 0 : ENCODING.countTokensOrdinary(text);
    }

    @Override
    public int estimateTokenCountInMessage(ChatMessage message) {
        if (message == null) {
            return 0;
        }
        return EXTRA_TOKENS_PER_MESSAGE + estimateTokenCountInText(extractText(message));
    }

    /** ChatMessage 无统一 text() 方法，按类型提取正文（未知类型用 toString 兜底近似） */
    private static String extractText(ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.UserMessage um) {
            return um.singleText();
        }
        if (message instanceof dev.langchain4j.data.message.SystemMessage sm) {
            return sm.text();
        }
        if (message instanceof dev.langchain4j.data.message.AiMessage am) {
            return am.text();
        }
        if (message instanceof dev.langchain4j.data.message.ToolExecutionResultMessage tr) {
            return tr.text();
        }
        return message.toString();
    }

    @Override
    public int estimateTokenCountInMessages(Iterable<ChatMessage> messages) {
        int sum = EXTRA_TOKENS_PER_REQUEST;
        for (ChatMessage message : messages) {
            sum += estimateTokenCountInMessage(message);
        }
        return sum;
    }
}
