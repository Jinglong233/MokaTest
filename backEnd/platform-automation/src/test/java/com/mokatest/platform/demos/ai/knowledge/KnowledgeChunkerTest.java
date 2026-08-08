package com.mokatest.platform.demos.ai.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeChunkerTest {

    @Test
    void emptyContent() {
        assertTrue(KnowledgeChunker.chunk(null).isEmpty());
        assertTrue(KnowledgeChunker.chunk("   ").isEmpty());
    }

    @Test
    void shortContentSingleChunk() {
        List<String> chunks = KnowledgeChunker.chunk("这是一段很短的内容。");
        assertEquals(1, chunks.size());
        assertEquals("这是一段很短的内容。", chunks.get(0));
    }

    @Test
    void headingsStartNewChunk() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 4; i++) {
            sb.append("# 标题").append(i).append("\n");
            sb.append("内容".repeat(200)).append("\n");
        }
        // 每段约 400+ 字符，贪心合并后应为 2 块，且标题不被拆离正文
        List<String> chunks = KnowledgeChunker.chunk(sb.toString());
        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).startsWith("# 标题1") && chunks.get(0).contains("# 标题2"));
        assertTrue(chunks.get(1).startsWith("# 标题3") && chunks.get(1).contains("# 标题4"));
    }

    @Test
    void smallParagraphsMerged() {
        String content = "段落一内容。\n\n段落二内容。\n\n段落三内容。";
        List<String> chunks = KnowledgeChunker.chunk(content);
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).contains("段落一") && chunks.get(0).contains("段落三"));
    }

    @Test
    void oversizedBlockHardSplit() {
        String longText = "字".repeat(KnowledgeChunker.TARGET_CHARS * 2 + 100);
        List<String> chunks = KnowledgeChunker.chunk(longText);
        assertEquals(3, chunks.size());
        assertEquals(KnowledgeChunker.TARGET_CHARS, chunks.get(0).length());
        assertEquals(100, chunks.get(2).length());
    }

    @Test
    void tokenEstimation() {
        assertEquals(0, KnowledgeChunker.estimateTokens(null));
        assertEquals(5, KnowledgeChunker.estimateTokens("0123456789"));
    }

    @Test
    void htmlToPlainText() {
        String html = "<h2>支付规则</h2><p>超时 <b>900</b> 秒</p><ul><li>库存回滚</li><li>签名验证</li></ul>";
        String text = KnowledgeChunker.toPlainText(html);
        assertTrue(text.contains("支付规则"));
        assertTrue(text.contains("超时 900 秒"));
        assertTrue(text.contains("- 库存回滚"));
        assertFalse(text.contains("<"));
        assertEquals("", KnowledgeChunker.toPlainText(null));
    }
}
