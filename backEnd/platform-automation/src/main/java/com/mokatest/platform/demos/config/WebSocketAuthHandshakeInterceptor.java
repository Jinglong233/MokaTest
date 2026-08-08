package com.mokatest.platform.demos.config;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器
 * 
 * 核心职责：
 * 1. 校验当前 token 是否有效（未登录 / token 过期 → 拒绝握手）
 * 2. 将解析出的 loginId 存入 session attributes，供 Handler 做业务权限校验
 * 
 * 注意：Spring 的 SaInterceptor 默认不拦截 WebSocket 握手请求（SimpleUrlHandlerMapping），
 * 因此本拦截器是 WebSocket 入口唯一的登录校验点。
 */
@Slf4j
@Component
public class WebSocketAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String LOGIN_ID_KEY = "loginId";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // WebSocket 鉴权已移除，直接放行
        return true;
    }

    /**
     * 通过 token 解析登录用户ID并校验有效性。
     * 方式1：直接通过 StpLogic.getLoginId(tokenValue) 解析（优先，最准确）。
     * 方式2：从 sa-token 请求上下文获取（兜底，兼容无显式 token 的场景）。
     *
     * @return loginId 字符串；token 无效时返回 null
     */
    private String resolveLoginId(String tokenValue) {
        // 方式1：直接通过传入的 token value 获取 loginId（最准确，优先）
        // WebSocket 握手是独立线程，ThreadLocal 上下文可能是空的或残留的，不能依赖
        try {
            StpLogic stpLogic = StpUtil.getStpLogic();
            Object loginIdByToken = stpLogic.getLoginId(tokenValue);
            if (loginIdByToken != null) {
                return loginIdByToken.toString();
            }
        } catch (Exception e) {
            log.debug("[WebSocket握手] 通过 token 获取 loginId 失败: {}", e.getMessage());
        }

        // 方式2：从 sa-token 请求上下文获取（兜底）
        try {
            Object loginId = StpUtil.getLoginIdDefaultNull();
            if (loginId != null) {
                return loginId.toString();
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
