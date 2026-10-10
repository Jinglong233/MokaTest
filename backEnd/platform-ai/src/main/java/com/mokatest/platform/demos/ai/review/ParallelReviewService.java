package com.mokatest.platform.demos.ai.review;

import com.mokatest.platform.demos.ai.config.ModelManager;
import com.mokatest.platform.demos.ai.skill.ReviewChecklist;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 并行需求评审服务（流式专家工厂）。
 *
 * 形态说明：评审是长生成场景，按教程 07 章走流式 TokenStream（token 持续到达即活着，
 * 无整包超时问题）；4 专家的并行编排在调用方用 CompletableFuture 做（agentic 同步
 * 并行工作流不适合长生成，已弃用）。聚合仍由调用方纯代码完成。
 */
@Component
public class ParallelReviewService {

    /** 专家定义：key → 负责维度（checklist 按 ReviewChecklist.DIM_ITEMS 前缀天然可分） */
    public record ExpertDef(String key, String label, List<String> dims) {
        public List<String> checklistIds() {
            return ReviewChecklist.idsOfDims(dims);
        }

        public String renderSpec() {
            return ReviewChecklist.renderChecklistSpec(dims);
        }
    }

    public static final List<ExpertDef> EXPERTS = List.of(
            new ExpertDef("completeness", "完整性与清晰度", List.of("completeness", "clarity")),
            new ExpertDef("testability", "可测性与一致性", List.of("testability", "consistency")),
            new ExpertDef("boundary", "边界与异常", List.of("boundary")),
            new ExpertDef("risk", "安全性与风险深挖", List.of("security")));

    public static ExpertDef expertDef(String key) {
        return EXPERTS.stream().filter(e -> e.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知评审专家: " + key));
    }

    @Autowired
    private ModelManager modelManager;

    /** 创建流式评审专家（确定性流式模型 temp=0；每次调用新建，模型由 ModelManager 缓存） */
    public ReviewExpert createExpert() {
        return AiServices.builder(ReviewExpert.class)
                .streamingChatModel(modelManager.createDeterministicStreamingChatModel())
                .build();
    }
}
