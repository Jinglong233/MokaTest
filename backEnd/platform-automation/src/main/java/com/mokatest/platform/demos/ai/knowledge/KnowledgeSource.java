package com.mokatest.platform.demos.ai.knowledge;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.mokatest.platform.demos.ai.skill.ContextBlock;
import com.mokatest.platform.demos.ai.skill.ContextSource;
import com.mokatest.platform.demos.ai.skill.SkillRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 知识库上下文源（L0）：按场景 Service 组装的检索词检索 Top-5 分块注入 prompt
 *
 * 检索词来源：SkillRequest.retrievalQuery（需求标题+描述摘要 / 接口名+路径），
 * 由场景 Service 在 buildContext 前写入（automation 不反向依赖 qa/api 表，保持隔离边界）。
 *
 * 命中副作用：文档 cite_count+1（「AI 引用 X 次」统计）；引用明细写入
 * SkillRequest.citations（完成轮次时落库，前端「依据《XX 文档》」展示）。
 */
@Component
public class KnowledgeSource implements ContextSource {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSource.class);

    public static final String CODE = "knowledge";

    /** Top-K 命中分块数 */
    private static final int TOP_K = 5;
    /** 引用片段截断长度 */
    private static final int SNIPPET_LEN = 100;

    @Autowired
    private EmbeddingStore embeddingStore;

    @Autowired
    private KnowledgeDocMapper docMapper;

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String level() {
        return "L0";
    }

    @Override
    public boolean supports(String scene) {
        // 场景常量用字面值：automation 不反向依赖 qa 模块的 Skill 类
        return "GENERATE_CASE".equals(scene) || "GENERATE_API_CASE".equals(scene);
    }

    @Override
    public ContextBlock load(SkillRequest request) {
        String query = request.getRetrievalQuery();
        if (query == null || query.trim().isEmpty()) {
            query = request.getInstruction();
        }
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        List<EmbeddingStore.ScoredChunk> hits;
        try {
            hits = embeddingStore.search(request.getProjectId(), query.trim(), TOP_K);
        } catch (Exception e) {
            log.warn("知识库检索失败，本轮不注入知识库上下文: {}", e.getMessage());
            return null;
        }
        if (hits == null || hits.isEmpty()) {
            return null;
        }

        // 拼上下文块 + 引用明细 + 引用计数
        StringBuilder content = new StringBuilder();
        Set<Long> citedDocs = new HashSet<>();
        int idx = 1;
        for (EmbeddingStore.ScoredChunk hit : hits) {
            content.append("【").append(idx++).append("】《").append(hit.getDocTitle()).append("》\n")
                    .append(hit.getChunkText()).append("\n\n");
            request.getCitations().add(new SkillRequest.Citation(
                    hit.getDocId(), hit.getDocTitle(), abbreviate(hit.getChunkText(), SNIPPET_LEN)));
            if (citedDocs.add(hit.getDocId())) {
                docMapper.update(null, new UpdateWrapper<KnowledgeDoc>()
                        .eq("id", hit.getDocId())
                        .setSql("cite_count = cite_count + 1"));
            }
        }
        return new ContextBlock("L0", CODE, "知识库参考", content.toString().trim());
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
