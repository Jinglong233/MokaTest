package com.mokatest.platform.demos.ai.skill;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 执行请求（单场景一次生成）
 */
@Data
public class SkillRequest {

    /** 调用用户ID */
    private String userId;

    /** 团队ID（请求头上下文解析） */
    private Integer teamId;

    /** 项目ID（请求头上下文解析，隔离边界） */
    private Integer projectId;

    /** 锚定实体ID（需求ID/接口ID/报告ID；项目级场景为 null） */
    private Long entityId;

    /** 生成记录编号（追加生成时必填，新会话为 null） */
    private String recordNo;

    /** 用户追加指令（如"多补充边界场景"） */
    private String instruction;

    /** 生成条数（生成类场景） */
    private Integer count;

    /** 上下文选项：各 ContextSource 的启用开关（key = source code） */
    private java.util.Map<String, Boolean> contextOptions;

    /** 对话历史摘要（服务端组装注入，用于感知修正/补充；不接受前端传参） */
    private String history;

    /** 知识库（L0）检索词：由各场景 Service 在 buildContext 前组装（需求标题+描述摘要 / 接口名+路径） */
    private String retrievalQuery;

    /** 知识库引用明细（KnowledgeSource 检索命中时写入，完成轮次时落库供引用溯源） */
    private List<Citation> citations = new ArrayList<>();

    public boolean isContextEnabled(String code, boolean defaultValue) {
        if (contextOptions == null || !contextOptions.containsKey(code)) {
            return defaultValue;
        }
        return Boolean.TRUE.equals(contextOptions.get(code));
    }

    /**
     * 知识库引用（生成轮次落库，前端展示「依据《XX 文档》」）
     */
    @Data
    @lombok.AllArgsConstructor
    public static class Citation {
        private Long docId;
        private String title;
        /** 命中片段（截断保存） */
        private String snippet;
    }
}
