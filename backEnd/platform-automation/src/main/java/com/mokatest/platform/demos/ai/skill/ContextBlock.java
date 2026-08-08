package com.mokatest.platform.demos.ai.skill;

import lombok.Data;

/**
 * 单个上下文块
 */
@Data
public class ContextBlock {

    /** 层级：L0 知识库 / L1 核心需求 / L2 血缘 / L3 同模块 / L4 已有用例 / L5 历史BUG */
    private String level;

    /** 来源标识（ContextSource.code） */
    private String source;

    /** 块标题（渲染进 prompt） */
    private String title;

    /** 块内容 */
    private String content;

    /** 估算 token 数（粗算：字符数 / 2） */
    private int estimatedTokens;

    /** 是否可被预算裁剪（L1 核心需求不可裁剪） */
    private boolean trimmable = true;

    public ContextBlock() {
    }

    public ContextBlock(String level, String source, String title, String content) {
        this.level = level;
        this.source = source;
        this.title = title;
        this.content = content;
        this.estimatedTokens = estimateTokens(content);
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        // 中文场景粗算：2 字符 ≈ 1 token
        return Math.max(1, text.length() / 2);
    }
}
