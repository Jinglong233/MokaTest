package com.mokatest.platform.demos.config;

import com.mokatest.platform.demos.manager.DebugSessionManager;
import com.mokatest.platform.demos.service.ScenePermissionChecker;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class DebugWebSocketHandler extends TextWebSocketHandler {
    private static final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Resource
    private DebugSessionManager sessionManager;

    @Resource
    private ScenePermissionChecker scenePermissionChecker;


    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);
        sessionManager.createSession(sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String command = message.getPayload();
        String sessionId = session.getId();

        if (command.startsWith("start")) {
            // start_<sceneId> 或 start_<sceneId>_until_<stepId>（执行到指定步骤前自动暂停）
            String[] parts = command.split("_");
            Integer sceneId = Integer.valueOf(parts[1]);
            Integer untilStepId = null;
            if (parts.length >= 4 && "until".equalsIgnoreCase(parts[2])) {
                try {
                    untilStepId = Integer.valueOf(parts[3]);
                } catch (NumberFormatException ignored) {
                    // 目标步骤 id 非法时按普通调试启动
                }
            }

            if (sessionManager.startDebugSession(sessionId, sceneId, untilStepId)) {
                DebugWebSocketHandler.sendToSession(sessionId, new Gson().toJson("启动成功，runID:" + sessionId));
            } else {
                DebugWebSocketHandler.sendToSession(sessionId, new Gson().toJson("启动失败"));
            }
        } else {
            sessionManager.sendCommand(sessionId, command);
        }
    }


    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        // 关闭浏览器
        String sessionId = session.getId();
        sessionManager.closeDebugSession(sessionId);
        sessions.remove(sessionId);
    }

    // 向指定会话发送消息
    public static void sendToSession(String sessionId, String message) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                // 连接已断开时忽略发送失败，避免中断执行线程
            }
        }
    }

    /**
     * 发送统一格式的 WebSocket 消息信封。
     * 格式：{"type": "TRACE|SYSTEM|STEP_RESULT", "payload": {...}}
     */
    public static void sendEnvelope(String sessionId, String type, Object payload) {
        JsonObject envelope = new JsonObject();
        envelope.addProperty("type", type);
        envelope.add("payload", new Gson().toJsonTree(payload));
        sendToSession(sessionId, new Gson().toJson(envelope));
    }


    // 结束会话
    public static void closeSession(String sessionId) {
        WebSocketSession session = sessions.remove(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                // 忽略关闭异常
            }
        }
    }
}
