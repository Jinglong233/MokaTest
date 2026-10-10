package com.mokatest.platform.demos.ai.context;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 上下文块组装工具：替代旧 SkillContext + ContextBlock + TokenBudgetTrimmer 三件套。
 *
 * 预算规则（对齐原设计）：
 * - 总上下文预算 4000 tokens
 * - L0（知识库）单独预算 ≤ 2000
 * - 超预算时按 L5 → L4 → L3 顺序整块裁剪（L1/L2 核心不可裁）
 *
 * 调用方按层级顺序装入 Block，assemble 完成裁剪并渲染为 prompt 文本。
 */
public final class ContextBlocks {

    /** 总上下文预算 */
    public static final int TOTAL_BUDGET = 4000;
    /** 知识库（L0）单独预算 */
    public static final int L0_BUDGET = 2000;

    /** 裁剪优先级：数值越小越先被裁（L5 最先，L3 次之，L1/L2 不裁） */
    private static final List<String> TRIM_ORDER = List.of("L5", "L4", "L3");

    private ContextBlocks() {
    }

    public record Block(String level, String title, String content, boolean trimmable) {
        public int estimatedTokens() {
            return estimateTokens(content);
        }
    }

    /** 中文场景粗算：2 字符 ≈ 1 token */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    /**
     * 按预算裁剪并渲染为 prompt 上下文章节。
     *
     * @param blocks       按 L0..L5 顺序装入的块（裁剪按 TRIM_ORDER 整块移除）
     * @param trimmedNotes 被裁块的说明（调用方传入，追加；可为 null）
     * @return 渲染文本（## 标题\n内容\n\n）
     */
    public static String assemble(List<Block> blocks, List<String> trimmedNotes) {
        List<Block> working = new ArrayList<>(blocks);

        // 1. L0 单独预算：同层级多块时按加入顺序保留，超出整块丢弃
        trimLevelBudget(working, "L0", L0_BUDGET, trimmedNotes);

        // 2. 总预算：按 L5→L4→L3 顺序裁，同层级大块先裁
        int total = totalTokens(working);
        if (total > TOTAL_BUDGET) {
            for (String level : TRIM_ORDER) {
                List<Block> candidates = new ArrayList<>();
                for (Block b : working) {
                    if (level.equals(b.level()) && b.trimmable()) {
                        candidates.add(b);
                    }
                }
                candidates.sort(Comparator.comparingInt(Block::estimatedTokens).reversed());
                for (Block b : candidates) {
                    if (total <= TOTAL_BUDGET) {
                        break;
                    }
                    working.remove(b);
                    if (trimmedNotes != null) {
                        trimmedNotes.add("因 token 预算裁剪：" + b.level() + " " + b.title()
                                + "（约 " + b.estimatedTokens() + " tokens）");
                    }
                    total -= b.estimatedTokens();
                }
                if (total <= TOTAL_BUDGET) {
                    break;
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Block b : working) {
            sb.append("## ").append(b.title()).append('\n').append(b.content()).append("\n\n");
        }
        return sb.toString();
    }

    private static void trimLevelBudget(List<Block> blocks, String level, int budget, List<String> trimmedNotes) {
        int levelTotal = 0;
        List<Block> toRemove = new ArrayList<>();
        for (Block b : blocks) {
            if (!level.equals(b.level())) {
                continue;
            }
            levelTotal += b.estimatedTokens();
            if (levelTotal > budget && b.trimmable()) {
                toRemove.add(b);
                levelTotal -= b.estimatedTokens();
            }
        }
        for (Block b : toRemove) {
            blocks.remove(b);
            if (trimmedNotes != null) {
                trimmedNotes.add("因 " + level + " 预算裁剪：" + b.title()
                        + "（约 " + b.estimatedTokens() + " tokens）");
            }
        }
    }

    private static int totalTokens(List<Block> blocks) {
        int sum = 0;
        for (Block b : blocks) {
            sum += b.estimatedTokens();
        }
        return sum;
    }
}
