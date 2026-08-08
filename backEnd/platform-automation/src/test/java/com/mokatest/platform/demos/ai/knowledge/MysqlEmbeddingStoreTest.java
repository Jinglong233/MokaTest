package com.mokatest.platform.demos.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.gateway.AiGatewayService;
import com.mokatest.platform.demos.ai.gateway.ChatOptions;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 检索 Store：关键词降级排序、缓存失效、语义检索排序
 */
class MysqlEmbeddingStoreTest {

    private MysqlEmbeddingStore store;
    private KnowledgeChunkMapper chunkMapper;
    private KnowledgeDocMapper docMapper;
    private AiGatewayService gatewayService;
    private AiConfigService aiConfigService;

    @BeforeEach
    void setUp() {
        store = new MysqlEmbeddingStore();
        chunkMapper = Mockito.mock(KnowledgeChunkMapper.class);
        docMapper = Mockito.mock(KnowledgeDocMapper.class);
        gatewayService = Mockito.mock(AiGatewayService.class);
        aiConfigService = Mockito.mock(AiConfigService.class);
        ReflectionTestUtils.setField(store, "chunkMapper", chunkMapper);
        ReflectionTestUtils.setField(store, "docMapper", docMapper);
        ReflectionTestUtils.setField(store, "gatewayService", gatewayService);
        ReflectionTestUtils.setField(store, "aiConfigService", aiConfigService);
    }

    private void mockData(List<KnowledgeChunk> chunks, List<KnowledgeDoc> docs) {
        when(chunkMapper.selectList(any(QueryWrapper.class))).thenReturn(chunks);
        when(docMapper.selectList(any(QueryWrapper.class))).thenReturn(docs);
    }

    private KnowledgeChunk chunk(long id, long docId, String text, List<Float> embedding) {
        KnowledgeChunk c = new KnowledgeChunk();
        c.setId(id);
        c.setDocId(docId);
        c.setProjectId(1);
        c.setChunkText(text);
        c.setEmbedding(embedding);
        return c;
    }

    private KnowledgeDoc doc(long id, String title) {
        KnowledgeDoc d = new KnowledgeDoc();
        d.setId(id);
        d.setProjectId(1);
        d.setTitle(title);
        return d;
    }

    @Test
    void keywordSearchRanksByHits() {
        mockData(List.of(
                chunk(1, 1, "支付回调需要签名验证", null),
                chunk(2, 1, "支付超时时间与支付重试策略", null),
                chunk(3, 2, "完全无关的内容", null)),
                List.of(doc(1, "支付文档"), doc(2, "其他")));
        when(aiConfigService.getActiveConfig()).thenReturn(null);

        List<EmbeddingStore.ScoredChunk> hits = store.search(1, "支付超时时间", 5);
        assertEquals(2, hits.size());
        assertEquals(2L, hits.get(0).getChunkId(), "命中两个词的排最前");
        assertEquals("支付文档", hits.get(0).getDocTitle());
    }

    @Test
    void keywordSearchNoHitReturnsEmpty() {
        mockData(List.of(chunk(1, 1, "支付回调", null)), List.of(doc(1, "支付文档")));
        when(aiConfigService.getActiveConfig()).thenReturn(null);
        assertTrue(store.search(1, "登录注册", 5).isEmpty());
    }

    @Test
    void orphanChunksSkipped() {
        mockData(List.of(chunk(1, 99, "支付回调", null)), List.of(doc(1, "支付文档")));
        when(aiConfigService.getActiveConfig()).thenReturn(null);
        assertTrue(store.search(1, "支付", 5).isEmpty(), "文档已删除的孤儿块不返回");
    }

    @Test
    void invalidateClearsCache() {
        mockData(List.of(chunk(1, 1, "支付回调", null)), List.of(doc(1, "支付文档")));
        when(aiConfigService.getActiveConfig()).thenReturn(null);
        assertEquals(1, store.search(1, "支付", 5).size());

        // 缓存失效后重新加载（数据变为空）
        store.invalidate(1);
        mockData(List.of(), List.of());
        assertTrue(store.search(1, "支付", 5).isEmpty());
    }

    @Test
    void semanticSearchRanksByCosine() {
        mockData(List.of(
                chunk(1, 1, "文本A", List.of(1f, 0f, 0f)),
                chunk(2, 1, "文本B", List.of(0f, 1f, 0f))),
                List.of(doc(1, "文档")));
        AiConfig config = new AiConfig();
        config.setEmbeddingModel("text-embedding");
        when(aiConfigService.getActiveConfig()).thenReturn(config);
        when(gatewayService.embed(anyString(), any(ChatOptions.class)))
                .thenReturn(new float[]{0.9f, 0.1f, 0f});

        List<EmbeddingStore.ScoredChunk> hits = store.search(1, "查询", 5);
        assertEquals(2, hits.size());
        assertEquals(1L, hits.get(0).getChunkId(), "与 query 向量更接近的排最前");
    }

    @Test
    void semanticFailureFallsBackToKeyword() {
        mockData(List.of(
                chunk(1, 1, "支付回调需要签名", List.of(1f, 0f))),
                List.of(doc(1, "支付文档")));
        AiConfig config = new AiConfig();
        config.setEmbeddingModel("text-embedding");
        when(aiConfigService.getActiveConfig()).thenReturn(config);
        when(gatewayService.embed(anyString(), any(ChatOptions.class)))
                .thenThrow(new RuntimeException("embed down"));

        List<EmbeddingStore.ScoredChunk> hits = store.search(1, "支付回调", 5);
        assertEquals(1, hits.size());
    }
}
