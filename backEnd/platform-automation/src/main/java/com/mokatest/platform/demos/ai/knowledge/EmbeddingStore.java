package com.mokatest.platform.demos.ai.knowledge;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 向量存储/检索抽象：默认实现为 MySQL JSON + 内存余弦计算，
 * 预留扩展点（规模到十万级 chunk 时可替换 Qdrant/Milvus 实现，调用方无感）。
 */
public interface EmbeddingStore {

    /**
     * 检索项目内最相关的分块
     *
     * @param projectId 项目（隔离边界）
     * @param query     检索词（需求标题+描述摘要 / 接口名+路径）
     * @param topK      返回条数
     * @return 按相关度降序（无命中返回空列表）
     */
    List<ScoredChunk> search(Integer projectId, String query, int topK);

    /** 失效项目缓存（文档增删/索引重建后调用） */
    void invalidate(Integer projectId);

    /**
     * 命中的分块（含所属文档信息，供引用溯源）
     */
    @Data
    @AllArgsConstructor
    class ScoredChunk {
        private Long chunkId;
        private Long docId;
        private String docTitle;
        private String chunkText;
        /** 相关度得分（语义=余弦相似度；关键词=命中词数） */
        private double score;
    }
}
