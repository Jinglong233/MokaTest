package com.mokatest.platform.demos.ai.controller;

import dev.langchain4j.service.TokenStream;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * TokenStream → SseEmitter 桥接工具。
 *
 * 替代旧 AiGatewayService.chatStream() 的 CountDownLatch 阻塞模式 —
 * TokenStream 是异步非阻塞的，直接链式回调推送到 SSE，不占线程。
 */
public final class AiStreamBridge {

    private AiStreamBridge() {
    }

    public static void bridge(TokenStream tokenStream, SseEmitter emitter) {
        tokenStream
                .onPartialResponse(token -> send(emitter, "delta", token))
                .onCompleteResponse(response -> {
                    send(emitter, "done", "{}");
                    safeComplete(emitter);
                })
                .onError(error -> {
                    send(emitter, "error", error.getMessage() != null ? error.getMessage() : "AI 调用失败");
                    safeComplete(emitter);
                })
                .start();
    }

    private static void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // 客户端断开，忽略
        }
    }

    private static void safeComplete(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }
}
