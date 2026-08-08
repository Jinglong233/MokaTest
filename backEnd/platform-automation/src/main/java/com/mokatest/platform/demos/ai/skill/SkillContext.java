package com.mokatest.platform.demos.ai.skill;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Skill 上下文：由若干 ContextSource 按层级拼装，经 token 预算裁剪后的最终文本块
 */
@Data
public class SkillContext {

    /** 已装入的上下文块（裁剪后） */
    private List<ContextBlock> blocks = new ArrayList<>();

    /** 被裁剪掉的块说明（写生成记录 inputSummary 供回溯） */
    private List<String> trimmedNotes = new ArrayList<>();

    public void addBlock(ContextBlock block) {
        blocks.add(block);
    }

    /** 拼装为 prompt 中的上下文章节 */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (ContextBlock block : blocks) {
            sb.append("## ").append(block.getTitle()).append('\n');
            sb.append(block.getContent()).append("\n\n");
        }
        return sb.toString();
    }
}
