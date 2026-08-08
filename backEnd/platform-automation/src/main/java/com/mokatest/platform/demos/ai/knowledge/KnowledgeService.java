package com.mokatest.platform.demos.ai.knowledge;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档服务（项目级，projectId 一律来自请求头上下文，不信前端传参）
 *
 * 索引调度：保存/上传事务提交后（afterCommit）异步分块向量化，不阻塞接口。
 */
@Service
public class KnowledgeService {

    /** 上传允许的扩展名（PDF 预留，本期不开放） */
    private static final List<String> ALLOWED_EXT = List.of("md", "markdown", "txt");

    @Autowired
    private KnowledgeDocMapper docMapper;

    @Autowired
    private KnowledgeChunkMapper chunkMapper;

    @Autowired
    private KnowledgeIndexService indexService;

    @Autowired
    private EmbeddingStore embeddingStore;

    /**
     * 项目文档列表（含分块数统计）
     */
    public List<Map<String, Object>> listByProject(String keyword) {
        Integer projectId = requireProjectId();
        QueryWrapper<KnowledgeDoc> qw = new QueryWrapper<KnowledgeDoc>()
                .eq("project_id", projectId)
                .orderByDesc("id")
                .select("id", "project_id", "title", "doc_type", "index_status", "cite_count",
                        "create_user_id", "update_user_id", "create_time", "update_time");
        if (keyword != null && !keyword.trim().isEmpty()) {
            qw.like("title", keyword.trim());
        }
        List<KnowledgeDoc> docs = docMapper.selectList(qw);

        // 分块数统计
        Map<Long, Integer> chunkCounts = new HashMap<>();
        List<KnowledgeChunk> chunks = chunkMapper.selectList(new QueryWrapper<KnowledgeChunk>()
                .eq("project_id", projectId)
                .select("doc_id"));
        for (KnowledgeChunk chunk : chunks) {
            chunkCounts.merge(chunk.getDocId(), 1, Integer::sum);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", doc.getId());
            item.put("title", doc.getTitle());
            item.put("docType", doc.getDocType());
            item.put("indexStatus", doc.getIndexStatus());
            item.put("citeCount", doc.getCiteCount() == null ? 0 : doc.getCiteCount());
            item.put("chunkCount", chunkCounts.getOrDefault(doc.getId(), 0));
            item.put("createUserId", doc.getCreateUserId());
            item.put("updateUserId", doc.getUpdateUserId());
            item.put("createTime", doc.getCreateTime());
            item.put("updateTime", doc.getUpdateTime());
            result.add(item);
        }
        return result;
    }

    /**
     * 文档详情（编辑用，含全文）
     */
    public KnowledgeDoc getDetail(Long id) {
        return requireOwnedDoc(id);
    }

    /**
     * 保存（新建/更新）：内容变更时重置索引状态并调度重建
     */
    public KnowledgeDoc save(KnowledgeDoc input) {
        Integer projectId = requireProjectId();
        if (input.getTitle() == null || input.getTitle().trim().isEmpty()) {
            throw new BusinessException("文档标题不能为空");
        }
        String docType = input.getDocType() == null ? "MD" : input.getDocType().trim().toUpperCase();
        if (!"MD".equals(docType) && !"TXT".equals(docType)) {
            throw new BusinessException("文档类型仅支持 MD/TXT");
        }
        String loginId = StpUtil.getLoginIdAsString();
        Date now = new Date();

        KnowledgeDoc doc;
        if (input.getId() == null) {
            doc = new KnowledgeDoc();
            doc.setProjectId(projectId);
            doc.setCreateUserId(loginId);
            doc.setCreateTime(now);
        } else {
            doc = requireOwnedDoc(input.getId());
        }
        doc.setTitle(input.getTitle().trim());
        doc.setDocType(docType);
        doc.setContent(input.getContent());
        doc.setIndexStatus(KnowledgeDoc.STATUS_PENDING);
        doc.setUpdateUserId(loginId);
        doc.setUpdateTime(now);
        if (doc.getId() == null) {
            docMapper.insert(doc);
        } else {
            docMapper.updateById(doc);
        }

        // 事务提交后调度异步索引（30s 去抖）
        Long docId = doc.getId();
        runAfterCommit(() -> indexService.scheduleIndex(docId));
        return doc;
    }

    /**
     * 上传文档（.md/.txt）：读取文本后走 save 同链路
     */
    public KnowledgeDoc upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择要上传的文件");
        }
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String ext = filename.contains(".")
                ? filename.substring(filename.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException("仅支持 .md / .txt 文件");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BusinessException("文件读取失败：" + e.getMessage());
        }
        KnowledgeDoc input = new KnowledgeDoc();
        input.setTitle(filename.substring(0, filename.lastIndexOf('.') > 0 ? filename.lastIndexOf('.') : filename.length()));
        input.setDocType("txt".equals(ext) ? "TXT" : "MD");
        input.setContent(content);
        return save(input);
    }

    /**
     * 删除：逻辑删文档 + 物理清理分块 + 缓存失效 + 取消待执行索引
     */
    public void delete(Long id) {
        KnowledgeDoc doc = requireOwnedDoc(id);
        docMapper.deleteById(doc.getId());
        chunkMapper.delete(new QueryWrapper<KnowledgeChunk>().eq("doc_id", doc.getId()));
        embeddingStore.invalidate(doc.getProjectId());
    }

    /**
     * 分块预览
     */
    public List<Map<String, Object>> listChunks(Long docId) {
        KnowledgeDoc doc = requireOwnedDoc(docId);
        List<KnowledgeChunk> chunks = chunkMapper.selectList(new QueryWrapper<KnowledgeChunk>()
                .eq("doc_id", doc.getId())
                .orderByAsc("chunk_index")
                .select("id", "chunk_index", "chunk_text", "token_count", "embedding"));
        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", chunk.getId());
            item.put("chunkIndex", chunk.getChunkIndex());
            item.put("chunkText", chunk.getChunkText());
            item.put("tokenCount", chunk.getTokenCount());
            item.put("hasEmbedding", chunk.getEmbedding() != null && !chunk.getEmbedding().isEmpty());
            result.add(item);
        }
        return result;
    }

    /**
     * 手动重建索引（FAILED 恢复 / 模型配置变更后）
     */
    public void rebuildIndex(Long id) {
        KnowledgeDoc doc = requireOwnedDoc(id);
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(doc.getId());
        update.setIndexStatus(KnowledgeDoc.STATUS_PENDING);
        docMapper.updateById(update);
        indexService.scheduleIndex(doc.getId(), 0);
    }

    // ==================== 内部 ====================

    private KnowledgeDoc requireOwnedDoc(Long id) {
        if (id == null) {
            throw new BusinessException("缺少文档ID");
        }
        KnowledgeDoc doc = docMapper.selectById(id);
        // 跨项目访问统一报"不存在"
        if (doc == null || !doc.getProjectId().equals(requireProjectId())) {
            throw new BusinessException("文档不存在");
        }
        return doc;
    }

    private Integer requireProjectId() {
        Integer projectId = ProjectContextHolder.getProjectId();
        if (projectId == null) {
            throw new BusinessException("缺少项目上下文（X-Project-Id）");
        }
        return projectId;
    }

    /** 事务提交后执行（无事务时直接执行），与站内信 afterCommit 模式一致 */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }
}
