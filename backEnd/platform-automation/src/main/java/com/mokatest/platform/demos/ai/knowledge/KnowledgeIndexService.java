package com.mokatest.platform.demos.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.gateway.AiGatewayService;
import com.mokatest.platform.demos.ai.gateway.ChatOptions;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 知识库异步索引：文档保存后 afterCommit 调度，30s 去抖，不阻塞保存接口
 *
 * 流程：INDEXING → 分块 → 逐块 embedding（未配模型/单块失败 → embedding=NULL 降级）
 * → 删旧插新 → READY；异常 → FAILED（保留旧 chunks，仍可关键词检索）。
 * 索引与检索缓存失效由本类统一触发。
 */
@Service
public class KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);

    /** 去抖窗口：30s 内重复保存只保留最后一次索引任务 */
    private static final long DEBOUNCE_MS = 30_000L;

    @Autowired
    private KnowledgeDocMapper docMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private AiGatewayService gatewayService;

    @Autowired
    private AiConfigService aiConfigService;

    @Autowired
    private EmbeddingStore embeddingStore;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "knowledge-index");
        t.setDaemon(true);
        return t;
    });

    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    /** 调度索引（默认 30s 去抖） */
    public void scheduleIndex(Long docId) {
        scheduleIndex(docId, DEBOUNCE_MS);
    }

    /** 调度索引（手动重建传 0 立即执行） */
    public void scheduleIndex(Long docId, long delayMs) {
        if (docId == null) {
            return;
        }
        ScheduledFuture<?> prev = pending.get(docId);
        if (prev != null) {
            prev.cancel(false);
        }
        pending.put(docId, scheduler.schedule(() -> {
            pending.remove(docId);
            doIndex(docId);
        }, delayMs, TimeUnit.MILLISECONDS));
    }

    /** 执行索引（调度线程内运行；失败不抛出，状态落 FAILED） */
    public void doIndex(Long docId) {
        KnowledgeDoc doc = docMapper.selectById(docId);
        if (doc == null) {
            return;
        }
        updateStatus(doc, KnowledgeDoc.STATUS_INDEXING);
        try {
            // 1. 分块（富文本 HTML 先转纯文本；失败时尚未动旧数据，旧 chunks 保留可检索）
            List<String> blocks = KnowledgeChunker.chunk(KnowledgeChunker.toPlainText(doc.getContent()));

            // 2. 逐块向量化（未配模型直接跳过；单块失败该块置 NULL 继续）
            boolean embeddingEnabled = hasEmbeddingModel();
            List<KnowledgeChunk> chunks = new ArrayList<>();
            for (int i = 0; i < blocks.size(); i++) {
                String text = blocks.get(i);
                KnowledgeChunk chunk = new KnowledgeChunk();
                chunk.setDocId(doc.getId());
                chunk.setProjectId(doc.getProjectId());
                chunk.setChunkText(text);
                chunk.setChunkIndex(i);
                chunk.setTokenCount(KnowledgeChunker.estimateTokens(text));
                if (embeddingEnabled) {
                    try {
                        ChatOptions options = new ChatOptions();
                        options.setScene("EMBEDDING");
                        options.setProjectId(doc.getProjectId());
                        options.setPromptSummary("知识库索引：" + abbreviate(doc.getTitle(), 50));
                        float[] vector = gatewayService.embed(text, options);
                        if (vector != null && vector.length > 0) {
                            List<Float> list = new ArrayList<>(vector.length);
                            for (float v : vector) {
                                list.add(v);
                            }
                            chunk.setEmbedding(list);
                        }
                    } catch (Exception e) {
                        log.warn("知识库分块向量化失败（doc={}, block={}），该块降级关键词检索: {}",
                                doc.getId(), i, e.getMessage());
                    }
                }
                chunks.add(chunk);
            }

            // 3. 删旧插新
            chunkMapper.delete(new QueryWrapper<KnowledgeChunk>().eq("doc_id", doc.getId()));
            for (KnowledgeChunk chunk : chunks) {
                chunkMapper.insert(chunk);
            }

            // 4. 就绪 + 缓存失效
            updateStatus(doc, KnowledgeDoc.STATUS_READY);
            embeddingStore.invalidate(doc.getProjectId());
            log.info("知识库索引完成: doc={}, chunks={}, embedding={}", doc.getId(), chunks.size(), embeddingEnabled);
        } catch (Exception e) {
            log.error("知识库索引失败: doc=" + docId, e);
            updateStatus(doc, KnowledgeDoc.STATUS_FAILED);
            embeddingStore.invalidate(doc.getProjectId());
        }
    }

    private void updateStatus(KnowledgeDoc doc, String status) {
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(doc.getId());
        update.setIndexStatus(status);
        docMapper.updateById(update);
        doc.setIndexStatus(status);
    }

    private boolean hasEmbeddingModel() {
        try {
            AiConfig config = aiConfigService.getActiveConfig();
            return config != null && config.getEmbeddingModel() != null && !config.getEmbeddingModel().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private String abbreviate(String text, int max) {
        return text == null || text.length() <= max ? text : text.substring(0, max);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
