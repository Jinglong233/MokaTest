package com.mokatest.platform.demos.ai.skill;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Token 预算裁剪器
 *
 * 预算规则（对齐设计文档）：
 * - 总上下文预算 4000 tokens
 * - L0（知识库）单独预算 ≤ 2000
 * - 超预算时按 L5 → L3 顺序裁剪（L1 核心需求、L2 血缘不裁）
 * - 整块裁剪（不做块内截断），被裁的块记录到 trimmedNotes 供回溯
 */
@Component
public class TokenBudgetTrimmer {

    /** 总上下文预算 */
    public static final int TOTAL_BUDGET = 4000;
    /** 知识库（L0）单独预算 */
    public static final int L0_BUDGET = 2000;

    /** 裁剪优先级：数值越小越先被裁（L5 最先，L3 次之，L1/L2 不裁） */
    private static final List<String> TRIM_ORDER = List.of("L5", "L4", "L3");

    /**
     * 按预算裁剪上下文块（原地修改 context）
     */
    public void trim(SkillContext context) {
        // 1. L0 单独预算：同层级多块时按加入顺序保留，超出整块丢弃
        trimLevelBudget(context, "L0", L0_BUDGET);

        // 2. 总预算
        int total = totalTokens(context.getBlocks());
        if (total <= TOTAL_BUDGET) {
            return;
        }
        for (String level : TRIM_ORDER) {
            List<ContextBlock> candidates = new ArrayList<>();
            for (ContextBlock block : context.getBlocks()) {
                if (level.equals(block.getLevel()) && block.isTrimmable()) {
                    candidates.add(block);
                }
            }
            // 同层级按块大小从大到小裁，先裁大块收益高
            candidates.sort(Comparator.comparingInt(ContextBlock::getEstimatedTokens).reversed());
            for (ContextBlock block : candidates) {
                if (total <= TOTAL_BUDGET) {
                    break;
                }
                context.getBlocks().remove(block);
                context.getTrimmedNotes().add(
                        "因 token 预算裁剪：" + block.getLevel() + " " + block.getTitle()
                                + "（约 " + block.getEstimatedTokens() + " tokens）");
                total -= block.getEstimatedTokens();
            }
            if (total <= TOTAL_BUDGET) {
                break;
            }
        }
    }

    private void trimLevelBudget(SkillContext context, String level, int budget) {
        int levelTotal = 0;
        List<ContextBlock> toRemove = new ArrayList<>();
        for (ContextBlock block : context.getBlocks()) {
            if (!level.equals(block.getLevel())) {
                continue;
            }
            levelTotal += block.getEstimatedTokens();
            if (levelTotal > budget && block.isTrimmable()) {
                toRemove.add(block);
                levelTotal -= block.getEstimatedTokens();
            }
        }
        for (ContextBlock block : toRemove) {
            context.getBlocks().remove(block);
            context.getTrimmedNotes().add(
                    "因 " + level + " 预算裁剪：" + block.getTitle()
                            + "（约 " + block.getEstimatedTokens() + " tokens）");
        }
    }

    private int totalTokens(List<ContextBlock> blocks) {
        int sum = 0;
        for (ContextBlock block : blocks) {
            sum += block.getEstimatedTokens();
        }
        return sum;
    }
}
