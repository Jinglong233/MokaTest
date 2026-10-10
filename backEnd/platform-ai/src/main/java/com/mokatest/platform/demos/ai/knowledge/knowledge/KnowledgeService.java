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
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 知识库文档服务（项目级，projectId 一律来自请求头上下文，不信前端传参）
 *
 * 索引调度：保存/上传事务提交后（afterCommit）异步向量化入 Qdrant（KnowledgeIngestService）。
 * 已移除旧 EmbeddingStore / KnowledgeChunk / KnowledgeChunkMapper / KnowledgeChunker 依赖。
 */
@Service
@Slf4j
public class KnowledgeService {

    private static final List<String> ALLOWED_EXT = List.of("md", "markdown", "txt");

    @Autowired
    private KnowledgeDocMapper docMapper;

    @Autowired(required = false)
    private com.mokatest.platform.demos.ai.rag.KnowledgeIngestService ingestService;

    public void settleCitations(List<com.mokatest.platform.demos.ai.domain.Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return;
        }
        java.util.Set<Long> docIds = new java.util.HashSet<>();
        for (com.mokatest.platform.demos.ai.domain.Citation c : citations) {
            if (c.getDocId() != null) {
                docIds.add(c.getDocId());
            }
        }
        for (Long docId : docIds) {
            docMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<KnowledgeDoc>()
                    .eq("id", docId)
                    .setSql("cite_count = cite_count + 1"));
        }
    }

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

        List<Map<String, Object>> result = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", doc.getId());
            item.put("title", doc.getTitle());
            item.put("docType", doc.getDocType());
            item.put("indexStatus", doc.getIndexStatus());
            item.put("citeCount", doc.getCiteCount() == null ? 0 : doc.getCiteCount());
            // ponytail: 分块数统计已废弃（向量存 Qdrant，无 knowledge_chunk 表），返回 0 保持契约
            item.put("chunkCount", 0);
            item.put("createUserId", doc.getCreateUserId());
            item.put("updateUserId", doc.getUpdateUserId());
            item.put("createTime", doc.getCreateTime());
            item.put("updateTime", doc.getUpdateTime());
            result.add(item);
        }
        return result;
    }

    public KnowledgeDoc getDetail(Long id) {
        return requireOwnedDoc(id);
    }

    public KnowledgeDoc save(KnowledgeDoc input) {
        Integer projectId = requireProjectId();
        requireQdrant();
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

        Long docId = doc.getId();
        Integer pid = projectId;
        String docTitle = doc.getTitle();
        String docContent = doc.getContent();
        runAfterCommit(() -> CompletableFuture.runAsync(() -> {
            KnowledgeDoc statusUpdate = new KnowledgeDoc();
            statusUpdate.setId(docId);
            try {
                String plainText = toPlainText(docContent);
                ingestService.ingest(pid, docId, docTitle, plainText);
                statusUpdate.setIndexStatus(KnowledgeDoc.STATUS_READY);
            } catch (Exception e) {
                log.error("知识库索引失败: docId={}", docId, e);
                statusUpdate.setIndexStatus(KnowledgeDoc.STATUS_FAILED);
            }
            docMapper.updateById(statusUpdate);
        }));
        return doc;
    }

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

    public void delete(Long id) {
        KnowledgeDoc doc = requireOwnedDoc(id);
        if (ingestService != null) {
            ingestService.deleteByDoc(doc.getProjectId(), doc.getId());
        }
        docMapper.deleteById(doc.getId());
    }

    /** 分块预览（向量存 Qdrant 后无本地分块表，返回空保持契约） */
    public List<Map<String, Object>> listChunks(Long docId) {
        requireOwnedDoc(docId);
        return List.of();
    }

    public void rebuildIndex(Long id) {
        requireQdrant();
        KnowledgeDoc doc = requireOwnedDoc(id);
        KnowledgeDoc update = new KnowledgeDoc();
        update.setId(doc.getId());
        update.setIndexStatus(KnowledgeDoc.STATUS_PENDING);
        docMapper.updateById(update);
        // ponytail: ForkJoinPool common pool, fine for I/O-bound indexing. Dedicated executor if backpressure matters.
        CompletableFuture.runAsync(() -> {
            try {
                String plainText = toPlainText(doc.getContent());
                ingestService.ingest(doc.getProjectId(), doc.getId(), doc.getTitle(), plainText);
                update.setIndexStatus(KnowledgeDoc.STATUS_READY);
            } catch (Exception e) {
                log.error("知识库索引失败: docId={}", doc.getId(), e);
                update.setIndexStatus(KnowledgeDoc.STATUS_FAILED);
            }
            docMapper.updateById(update);
        });
    }

    public int rebuildAll() {
        requireQdrant();
        List<KnowledgeDoc> docs = docMapper.selectList(new QueryWrapper<KnowledgeDoc>().select("id", "project_id"));
        for (KnowledgeDoc doc : docs) {
            if (doc.getProjectId() == null) continue;
            KnowledgeDoc update = new KnowledgeDoc();
            update.setId(doc.getId());
            update.setIndexStatus(KnowledgeDoc.STATUS_PENDING);
            docMapper.updateById(update);
            // ponytail: ForkJoinPool common pool, fine for I/O-bound indexing. Dedicated executor if backpressure matters.
            CompletableFuture.runAsync(() -> {
                try {
                    KnowledgeDoc full = docMapper.selectById(doc.getId());
                    if (full != null && full.getContent() != null) {
                        String plainText = toPlainText(full.getContent());
                        ingestService.ingest(full.getProjectId(), full.getId(), full.getTitle(), plainText);
                        update.setIndexStatus(KnowledgeDoc.STATUS_READY);
                    }
                } catch (Exception e) {
                    log.error("知识库索引失败: docId={}", doc.getId(), e);
                    update.setIndexStatus(KnowledgeDoc.STATUS_FAILED);
                }
                docMapper.updateById(update);
            });
        }
        return docs.size();
    }

    // ==================== 内部 ====================

    private KnowledgeDoc requireOwnedDoc(Long id) {
        if (id == null) {
            throw new BusinessException("缺少文档ID");
        }
        KnowledgeDoc doc = docMapper.selectById(id);
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

    private void requireQdrant() {
        if (ingestService == null) {
            throw new BusinessException("向量检索服务(Qdrant)未启用，无法构建知识库索引。请联系管理员开启 mokatest.qdrant.enabled=true");
        }
    }

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

    /** 富文本 HTML → 纯文本（从旧 KnowledgeChunker 搬入，块级标签转行、去标签、解实体） */
    private static String toPlainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|tr|table|ul|ol|blockquote|pre)>", "\n")
                .replaceAll("(?i)<li[^>]*>", "- ")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");
        return text.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
