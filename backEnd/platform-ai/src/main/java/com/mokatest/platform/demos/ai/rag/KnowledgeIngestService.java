package com.mokatest.platform.demos.ai.rag;

import com.mokatest.platform.demos.ai.config.GenericTokenCountEstimator;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库摄取服务：用 LangChain4j EmbeddingStoreIngestor 替代旧 KnowledgeIndexService（205行）。
 *
 * 一行 ingest(document) 完成：分块 → 向量化 → 存入 Qdrant。
 * collection 按项目隔离，store 实例按 collection 缓存。
 */
@Service
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "mokatest.qdrant.enabled", havingValue = "true")
public class KnowledgeIngestService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIngestService.class);

    @Autowired
    private RagConfig ragConfig;

    /** collection → store 实例缓存（QdrantEmbeddingStore 线程安全，可复用） */
    private final ConcurrentHashMap<String, EmbeddingStore<TextSegment>> storeCache = new ConcurrentHashMap<>();

    /** collection → ingestor 实例缓存（含 splitter，复用避免每次重建） */
    private final ConcurrentHashMap<String, EmbeddingStoreIngestor> ingestorCache = new ConcurrentHashMap<>();

    /** 获取项目级 EmbeddingStore（collection = kb_{projectId}） */
    private EmbeddingStore<TextSegment> getStore(Integer projectId) {
        return storeCache.computeIfAbsent("kb_" + projectId,
                name -> ragConfig.createEmbeddingStore(projectId, name));
    }

    /** 获取项目级 EmbeddingStoreIngestor（缓存复用，显式配置 splitter） */
    private EmbeddingStoreIngestor getIngestor(Integer projectId) {
        return ingestorCache.computeIfAbsent("kb_" + projectId, name ->
                EmbeddingStoreIngestor.builder()
                        .embeddingModel(ragConfig.getEmbeddingModel(projectId))
                        .embeddingStore(getStore(projectId))
                        .documentSplitter(DocumentSplitters.recursive(1000, 200,
                                new GenericTokenCountEstimator()))
                        .build());
    }

    /**
     * 摄取文档到 Qdrant（分块 + 向量化 + 存储）
     *
     * 幂等：先按 docId 删除旧向量再写入，重建索引/重复保存不会产生重复 chunk。
     *
     * @param projectId 项目ID
     * @param docId 文档ID（写入 metadata 供过滤）
     * @param title 文档标题
     * @param text 纯文本内容
     */
    public void ingest(Integer projectId, Long docId, String title, String text) {
        deleteByDoc(projectId, docId);
        Metadata metadata = Metadata.from("docId", docId.toString())
                .put("docTitle", title)
                .put("projectId", projectId.toString());
        Document document = Document.from(text, metadata);
        getIngestor(projectId).ingest(document);
        log.info("知识库摄取完成: projectId={}, docId={}, title={}", projectId, docId, title);
    }

    /**
     * 删除项目内某文档的所有向量
     */
   public void deleteByDoc(Integer projectId, Long docId) {
        try {
            EmbeddingStore<TextSegment> store = getStore(projectId);
            store.removeAll(dev.langchain4j.store.embedding.filter.MetadataFilterBuilder
                    .metadataKey("docId").isEqualTo(docId.toString()));
            log.info("知识库删除完成: projectId={} docId={}", projectId, docId);
        } catch (Exception e) {
            log.warn("知识库删除失败 | projectId={} docId={} err={}", projectId, docId, e.getMessage());
        }
    }

    /** 获取项目级 EmbeddingStore（供 ContentRetriever 使用） */
    public EmbeddingStore<TextSegment> getEmbeddingStore(Integer projectId) {
        return getStore(projectId);
    }

    /**
     * 清空 store / ingestor 缓存。
     * AI 配置变更（向量模型切换）时由 ModelManager.invalidate() 联动调用，
     * 否则 ingestor 内固化的旧 EmbeddingModel 会一直用到重启。
     */
    public void clearCache() {
        ingestorCache.clear();
        storeCache.clear();
        log.info("知识库 ingest/store 缓存已清空");
    }
}
