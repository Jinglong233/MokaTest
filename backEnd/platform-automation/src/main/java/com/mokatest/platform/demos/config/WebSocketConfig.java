package com.mokatest.platform.demos.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Autowired // 注入Spring管理的Handler
    private DebugWebSocketHandler debugWebSocketHandler;

    @Autowired
    private com.mokatest.platform.demos.api.config.ApiSceneDebugWebSocketHandler apiSceneDebugWebSocketHandler;

    @Autowired
    private WebSocketAuthHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(debugWebSocketHandler, "/ws/debug")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");

        // API 场景调试独立通道（与 UI 自动化的 /ws/debug 完全隔离）
        registry.addHandler(apiSceneDebugWebSocketHandler, "/ws/apiSceneDebug")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*");
    }

//    @Bean
//    public WebSocketHandler debugWebSocketHandler() {
//        return new DebugWebSocketHandler();
//    }
}
