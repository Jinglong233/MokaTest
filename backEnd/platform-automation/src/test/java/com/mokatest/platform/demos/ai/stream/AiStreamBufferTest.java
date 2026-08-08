package com.mokatest.platform.demos.ai.stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 流缓冲单元测试
 */
class AiStreamBufferTest {

    @Test
    void appendAndFinish() {
        AiStreamBuffer buffer = new AiStreamBuffer();
        buffer.append("hello ");
        buffer.append("world");
        assertEquals("hello world", buffer.text());
        assertEquals(11, buffer.length());
        assertEquals(AiStreamBuffer.State.STREAMING, buffer.getState());

        buffer.finish("{\"recordNo\":\"x\"}");
        assertEquals(AiStreamBuffer.State.DONE, buffer.getState());
        assertEquals("{\"recordNo\":\"x\"}", buffer.getResultJson());
    }

    @Test
    void failKeepsContent() {
        AiStreamBuffer buffer = new AiStreamBuffer();
        buffer.append("partial");
        buffer.fail("timeout");
        assertEquals(AiStreamBuffer.State.FAILED, buffer.getState());
        assertEquals("timeout", buffer.getErrorMsg());
        assertEquals("partial", buffer.text(), "失败时已生成内容保留");
    }

    @Test
    void stopKeepsPartialContent() {
        AiStreamBuffer buffer = new AiStreamBuffer();
        buffer.append("abc");
        buffer.requestCancel();
        assertTrue(buffer.isCancelRequested());
        buffer.markStopped();
        assertEquals(AiStreamBuffer.State.STOPPED, buffer.getState());
        assertEquals("abc", buffer.text(), "停止后部分内容保留");
    }

    @Test
    void registryTtlCleanup() {
        AiStreamBufferRegistry registry = new AiStreamBufferRegistry();
        AiStreamBuffer buffer = registry.create("r1");
        assertSame(buffer, registry.get("r1"));
        registry.remove("r1");
        assertNull(registry.get("r1"));
    }
}
