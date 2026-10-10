package com.mokatest.platform.demos.ai.stream;

import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流缓冲注册表：recordNo → AiStreamBuffer
 *
 * 供重连接口按 recordNo 回放/接续生成流。
 * 条目超过 TTL（1 小时）未访问即清理，避免内存泄漏。
 */
@Component
public class AiStreamBufferRegistry {

    private static final long TTL_MS = 3600_000L;

    private final Map<String, AiStreamBuffer> buffers = new ConcurrentHashMap<>();

    public AiStreamBuffer create(String recordNo) {
        // 同 recordNo 已存在活跃流：先取消旧 okhttp Call 再覆盖，避免后台流继续写已失效的 buffer
        AiStreamBuffer old = buffers.get(recordNo);
        if (old != null && old.getState() == AiStreamBuffer.State.STREAMING) {
            old.requestCancel();
        }
        AiStreamBuffer buffer = new AiStreamBuffer();
        buffers.put(recordNo, buffer);
        cleanup();
        return buffer;
    }

    public AiStreamBuffer get(String recordNo) {
        AiStreamBuffer buffer = buffers.get(recordNo);
        if (buffer != null) {
            buffer.touch();
        }
        return buffer;
    }

    public void remove(String recordNo) {
        buffers.remove(recordNo);
    }

    /** 惰性清理过期条目（create 时触发） */
    private void cleanup() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, AiStreamBuffer>> it = buffers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, AiStreamBuffer> entry = it.next();
            if (now - entry.getValue().getLastAccess() > TTL_MS) {
                it.remove();
            }
        }
    }
}
