package com.mokatest.platform.demos.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.gateway.AiGatewayService;
import com.mokatest.platform.demos.ai.gateway.ChatOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EmbeddingStore 默认实现：MySQL JSON 存储 + 项目级内存缓存 + Java 余弦计算
 *
 * 检索策略（可配置降级）：
 * - 已配置 embedding_model 且存在向量分块：query 向量化后按余弦相似度取 Top-K
 * - 其余情况（未配模型 / 索引失败无向量 / embed 调用失败）：降级关键词命中数排序
 *
 * 缓存：项目级全量分块内存缓存（key=projectId），文档增删/索引重建后 invalidate。
 */
@Component
public class MysqlEmbeddingStore implements EmbeddingStore {

    private static final Logger log = LoggerFactory.getLogger(MysqlEmbeddingStore.class);

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private KnowledgeDocMapper docMapper;

    @Autowired
    private AiGatewayService gatewayService;

    @Autowired
    private com.mokatest.platform.demos.ai.service.AiConfigService aiConfigService;

    private final Map<Integer, List<ChunkEntry>> cache = new ConcurrentHashMap<>();

    @Override
    public List<ScoredChunk> search(Integer projectId, String query, int topK) {
        if (projectId == null || query == null || query.trim().isEmpty() || topK <= 0) {
            return List.of();
        }
        List<ChunkEntry> chunks = loadChunks(projectId);
        if (chunks.isEmpty()) {
            return List.of();
        }
        // 语义检索：需配置向量模型且库内有向量
        if (hasEmbeddingModel() && chunks.stream().anyMatch(c -> c.embedding != null)) {
            try {
                return semanticSearch(chunks, query.trim(), topK, projectId);
            } catch (Exception e) {
                log.warn("知识库语义检索失败，降级关键词检索: {}", e.getMessage());
            }
        }
        return keywordSearch(chunks, query.trim(), topK);
    }

    @Override
    public void invalidate(Integer projectId) {
        if (projectId != null) {
            cache.remove(projectId);
        }
    }

    // ==================== 语义检索 ====================

    private List<ScoredChunk> semanticSearch(List<ChunkEntry> chunks, String query, int topK, Integer projectId) {
        ChatOptions options = new ChatOptions();
        options.setScene("EMBEDDING");
        options.setProjectId(projectId);
        options.setPromptSummary("知识库检索向量化");
        float[] queryVector = gatewayService.embed(query, options);
        if (queryVector == null || queryVector.length == 0) {
            return keywordSearch(chunks, query, topK);
        }
        List<ScoredChunk> scored = new ArrayList<>();
        for (ChunkEntry chunk : chunks) {
            if (chunk.embedding == null || chunk.embedding.isEmpty()) {
                continue;
            }
            double score = cosine(queryVector, chunk.embedding);
            scored.add(new ScoredChunk(chunk.chunkId, chunk.docId, chunk.docTitle, chunk.chunkText, score));
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }

    private double cosine(float[] a, List<Float> b) {
        int len = Math.min(a.length, b.size());
        if (len == 0) {
            return 0;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < len; i++) {
            dot += a[i] * b.get(i);
            normA += a[i] * a[i];
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // ==================== 关键词降级 ====================

    private List<ScoredChunk> keywordSearch(List<ChunkEntry> chunks, String query, int topK) {
        List<String> terms = extractTerms(query);
        if (terms.isEmpty()) {
            return List.of();
        }
        List<ScoredChunk> scored = new ArrayList<>();
        for (ChunkEntry chunk : chunks) {
            String text = chunk.chunkText.toLowerCase();
            int hits = 0;
            for (String term : terms) {
                if (text.contains(term)) {
                    hits++;
                }
            }
            if (hits > 0) {
                scored.add(new ScoredChunk(chunk.chunkId, chunk.docId, chunk.docTitle, chunk.chunkText, hits));
            }
        }
        scored.sort(Comparator.comparingDouble(ScoredChunk::getScore).reversed());
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }

    /**
     * 检索词切分：按非中英文数字切词；中文长词展开为二元组（bigram），
     * 避免"支付超时时间"整词必须完全命中导致的过度严格
     */
    private List<String> extractTerms(String query) {
        List<String> terms = new ArrayList<>();
        for (String term : query.split("[^\\p{IsHan}\\w]+")) {
            if (term.length() < 2) {
                continue;
            }
            boolean hasHan = term.chars().anyMatch(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN);
            if (hasHan && term.length() > 2) {
                for (int i = 0; i + 2 <= term.length(); i++) {
                    String bigram = term.substring(i, i + 2).toLowerCase();
                    if (!terms.contains(bigram)) {
                        terms.add(bigram);
                    }
                }
            } else {
                String lower = term.toLowerCase();
                if (!terms.contains(lower)) {
                    terms.add(lower);
                }
            }
        }
        return terms;
    }

    // ==================== 缓存加载 ====================

    private List<ChunkEntry> loadChunks(Integer projectId) {
        return cache.computeIfAbsent(projectId, pid -> {
            List<KnowledgeChunk> chunks = chunkMapper.selectList(new QueryWrapper<KnowledgeChunk>()
                    .eq("project_id", pid));
            Map<Long, String> docTitles = new HashMap<>();
            docMapper.selectList(new QueryWrapper<KnowledgeDoc>()
                            .eq("project_id", pid)
                            .select("id", "title", "index_status"))
                    .forEach(d -> docTitles.put(d.getId(), d.getTitle()));
            List<ChunkEntry> entries = new ArrayList<>();
            for (KnowledgeChunk chunk : chunks) {
                String title = docTitles.get(chunk.getDocId());
                if (title == null) {
                    continue; // 文档已删除的孤儿块
                }
                entries.add(new ChunkEntry(chunk.getId(), chunk.getDocId(), title,
                        chunk.getChunkText(), chunk.getEmbedding()));
            }
            return entries;
        });
    }

    private boolean hasEmbeddingModel() {
        try {
            AiConfig config = aiConfigService.getActiveConfig();
            return config != null && config.getEmbeddingModel() != null && !config.getEmbeddingModel().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /** 缓存条目（含所属文档标题，供引用溯源） */
    private static class ChunkEntry {
        final Long chunkId;
        final Long docId;
        final String docTitle;
        final String chunkText;
        final List<Float> embedding;

        ChunkEntry(Long chunkId, Long docId, String docTitle, String chunkText, List<Float> embedding) {
            this.chunkId = chunkId;
            this.docId = docId;
            this.docTitle = docTitle;
            this.chunkText = chunkText;
            this.embedding = embedding;
        }
    }
}
