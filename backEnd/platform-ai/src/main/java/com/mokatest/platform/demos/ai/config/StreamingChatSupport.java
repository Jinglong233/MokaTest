package com.mokatest.platform.demos.ai.config;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 流式调用同步收集工具：需要完整文本但又想用流式传输（无整包超时）时使用。
 * 典型：agentic 工作流的非 AI 节点内部执行长生成（如用例再生成器）。
 */
public final class StreamingChatSupport {

    private StreamingChatSupport() {
    }

    /** 流式调用并收集完整输出（阻塞至完成；出错抛 RuntimeException） */
    public static String collect(StreamingChatModel model, String systemPrompt, String userMessage) {
        StringBuilder buf = new StringBuilder();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        var req = ChatRequest.builder()
                .messages(systemPrompt != null && !systemPrompt.isBlank()
                        ? java.util.List.of(SystemMessage.from(systemPrompt), UserMessage.from(userMessage))
                        : java.util.List.of(UserMessage.from(userMessage)))
                .build();
        model.chat(req, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse != null) {
                    buf.append(partialResponse);
                }
            }

            @Override
            public void onCompleteResponse(dev.langchain4j.model.chat.response.ChatResponse completeResponse) {
                latch.countDown();
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("流式调用被中断", e);
        }
        if (error.get() != null) {
            throw new RuntimeException("流式调用失败：" + error.get().getMessage(), error.get());
        }
        return buf.toString();
    }
}
