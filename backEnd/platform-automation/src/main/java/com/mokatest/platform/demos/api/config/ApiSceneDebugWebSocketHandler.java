package com.mokatest.platform.demos.api.config;

import com.alibaba.fastjson.JSON;
import com.mokatest.platform.demos.api.service.ApiSceneDebugService;
import com.mokatest.platform.demos.service.ScenePermissionChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API 场景调试 WebSocket Handler
 *
 * 功能：为纯 API 场景提供实时调试结果推送，与 UI 自动化的 /ws/debug 完全独立。
 *
 * 通信协议：
 *   前端 → 后端：
 *     - start_<sceneId> ：启动场景调试
 *     - q              ：终止调试
 *   后端 → 前端：
 *     - "启动成功"        ：调试线程已启动
 *     - "执行结束"        ：所有步骤执行完毕
 *     - {ApiStepResult JSON} ：每执行完一个步骤，推送该步骤结果
 *
 * 线程模型：
 *   - 每个 WebSocket 会话独立 spawn 一个后台线程执行场景
 *   - 执行线程通过 sendToSession() 推送结果
 *   - 连接关闭或收到 q 命令时，中断执行线程并清理资源
 */
@Slf4j
@Component
public class ApiSceneDebugWebSocketHandler extends TextWebSocketHandler {

    private static final Map<String, WebSocketSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<String, Thread> EXECUTION_THREADS = new ConcurrentHashMap<>();

    @Autowired
    private ApiSceneDebugService apiSceneDebugService;

    @Autowired
    private ScenePermissionChecker scenePermissionChecker;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = session.getId();
        SESSIONS.put(sessionId, session);
        log.info("[API场景调试WS] 连接建立, sessionId={}", sessionId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String command = message.getPayload();
        String sessionId = session.getId();

        if (command.startsWith("start_")) {
            handleStartCommand(session, Integer.valueOf(command.split("_")[1]), null, null);
        } else if (command.startsWith("from_")) {
            // from_<sceneId>_<stepId>：从指定步骤开始执行
            String[] parts = command.split("_");
            handleStartCommand(session, Integer.valueOf(parts[1]), Integer.valueOf(parts[2]), null);
        } else if (command.startsWith("only_")) {
            // only_<sceneId>_<stepId>：仅执行指定步骤
            String[] parts = command.split("_");
            handleStartCommand(session, Integer.valueOf(parts[1]), null, Integer.valueOf(parts[2]));
        } else if ("q".equals(command)) {
            handleQuitCommand(sessionId);
        } else {
            log.warn("[API场景调试WS] 未知命令, sessionId={}, command={}", sessionId, command);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = session.getId();
        interruptExecution(sessionId);
        SESSIONS.remove(sessionId);
        EXECUTION_THREADS.remove(sessionId);
        log.info("[API场景调试WS] 连接关闭, sessionId={}, status={}", sessionId, status);
    }

    /**
     * 启动场景调试：在后台线程中串行执行场景，每完成一步通过 WebSocket 推送
     *
     * @param fromStepId 从该步骤开始执行（含），null 表示从头
     * @param onlyStepId 仅执行该步骤，null 表示不限制
     */
    private void handleStartCommand(WebSocketSession session, Integer sceneId, Integer fromStepId, Integer onlyStepId) {
        String sessionId = session.getId();
        try {
            // 避免重复启动
            if (EXECUTION_THREADS.containsKey(sessionId)) {
                log.warn("[API场景调试WS] 重复启动, sessionId={}", sessionId);
                sendToSession(sessionId, JSON.toJSONString("启动失败：已有执行中的调试"));
                return;
            }

            Thread execThread = new Thread(() -> {
                try {
                    log.info("[API场景调试WS] 调试线程启动, sessionId={}, sceneId={}, fromStepId={}, onlyStepId={}",
                            sessionId, sceneId, fromStepId, onlyStepId);
                    sendToSession(sessionId, JSON.toJSONString("启动成功"));

                    apiSceneDebugService.debugSceneRealtime(sceneId, fromStepId, onlyStepId, stepResult -> {
                        // 推送单步结果（检查连接是否仍存活）
                        if (SESSIONS.containsKey(sessionId)) {
                            sendToSession(sessionId, JSON.toJSONString(stepResult));
                        }
                    });

                    sendToSession(sessionId, JSON.toJSONString("执行结束"));
                } catch (Exception e) {
                    log.error("[API场景调试WS] 调试线程异常, sessionId={}", sessionId, e);
                    sendToSession(sessionId, JSON.toJSONString("执行异常: " + e.getMessage()));
                } finally {
                    EXECUTION_THREADS.remove(sessionId);
                }
            });

            execThread.setName("api-scene-debug-" + sessionId);
            EXECUTION_THREADS.put(sessionId, execThread);
            execThread.start();

        } catch (Exception e) {
            log.error("[API场景调试WS] 启动命令解析失败, sessionId={}", sessionId, e);
            sendToSession(sessionId, JSON.toJSONString("启动失败: " + e.getMessage()));
        }
    }

    /**
     * 终止调试：中断执行线程
     */
    private void handleQuitCommand(String sessionId) {
        log.info("[API场景调试WS] 收到终止命令, sessionId={}", sessionId);
        interruptExecution(sessionId);
    }

    private void interruptExecution(String sessionId) {
        Thread thread = EXECUTION_THREADS.remove(sessionId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
            log.info("[API场景调试WS] 已中断执行线程, sessionId={}", sessionId);
        }
    }

    /**
     * 向指定会话推送消息（线程安全）
     */
    public static void sendToSession(String sessionId, String message) {
        WebSocketSession session = SESSIONS.get(sessionId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
            } catch (IOException e) {
                log.error("[API场景调试WS] 消息发送失败, sessionId={}", sessionId, e);
            }
        }
    }
}
