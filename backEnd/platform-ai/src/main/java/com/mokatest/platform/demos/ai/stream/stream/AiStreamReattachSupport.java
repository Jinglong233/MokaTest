package com.mokatest.platform.demos.ai.stream;

import com.mokatest.platform.demos.ai.service.AiGenerationRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 流重连支持：会话生成中关闭弹窗后重新打开时，按 recordNo 回放并接续实时流
 *
 * 行为：
 * - 缓冲存在：先推送全量文本作为 restore 事件（前端一次性替换 content），
 *   再 300ms 轮询缓冲推送增量 delta，结束按状态推送 result / error / stopped 事件
 * - 缓冲不存在（服务重启或已过期）：推送 expired 事件，前端降级为轮询记录
 */
@Component
public class AiStreamReattachSupport {

    private static final Logger log = LoggerFactory.getLogger(AiStreamReattachSupport.class);

    @Autowired
    private AiStreamBufferRegistry registry;

    @Autowired
    private AiGenerationRecordService recordService;

    /** 死流判定：STREAMING 状态最后写入超过 180s 视为死流 */
    private static final long DEAD_STREAM_TIMEOUT_MS = 180_000L;

    /** 同 recordNo 活跃轮询线程登记，避免重复创建 */
    private final java.util.Map<String, Thread> pollingThreads = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 重连生成流
     *
     * @param recordNo  生成记录编号（锚点校验，跨项目拒绝）
     * @param projectId 当前项目
     * @param emitter   SSE 通道
     */
    public void reattach(String recordNo, Integer projectId, SseEmitter emitter) {
        recordService.requireValid(recordNo, projectId);
        AiStreamBuffer buffer = registry.get(recordNo);
        if (buffer == null) {
            send(emitter, "expired", "生成已结束或缓冲已过期");
            emitter.complete();
            return;
        }
        // 同 recordNo 已有活跃轮询线程：旧 emitter 已关闭，终止旧轮询避免多线程竞写同一 buffer
        Thread existing = pollingThreads.get(recordNo);
        if (existing != null && existing.isAlive()) {
            existing.interrupt();
        }
        Thread thread = new Thread(() -> {
            // 先推送全量文本作为 restore 事件（前端一次性替换，而非逐段追加）
            String fullText = buffer.text();
            if (fullText != null && !fullText.isEmpty()) {
                send(emitter, "restore", fullText);
            }
            // offset 从全量文本末尾开始，后续只推增量 delta
            int offset = fullText.length();
            try {
                while (true) {
                    String text = buffer.text();
                    if (text.length() > offset) {
                        send(emitter, "delta", text.substring(offset));
                        offset = text.length();
                    }
                    AiStreamBuffer.State state = buffer.getState();
                    if (state == AiStreamBuffer.State.DONE) {
                        send(emitter, "result", buffer.getResultJson() == null ? "{}" : buffer.getResultJson());
                        break;
                    }
                    if (state == AiStreamBuffer.State.FAILED) {
                        send(emitter, "error", buffer.getErrorMsg() == null ? "生成失败" : buffer.getErrorMsg());
                        break;
                    }
                    if (state == AiStreamBuffer.State.STOPPED) {
                        send(emitter, "stopped", "已停止");
                        break;
                    }
                    // STREAMING 死流判定：最后写入超过 180s 无响应视为死流，推 error 后退出
                    if (System.currentTimeMillis() - buffer.getLastAccess() > DEAD_STREAM_TIMEOUT_MS) {
                        send(emitter, "error", "生成流超时无响应");
                        break;
                    }
                    Thread.sleep(300);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                pollingThreads.remove(recordNo);
                emitter.complete();
            }
        }, "ai-reattach-" + recordNo);
        thread.setDaemon(true);
        pollingThreads.put(recordNo, thread);
        thread.start();
    }

    private void send(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (IOException | IllegalStateException e) {
            // 客户端断开
            log.debug("SSE 重连推送失败（客户端断开）: {}", e.getMessage());
        }
    }
}
