package com.mokatest.platform.demos.ai.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Token 预算裁剪器单元测试
 */
class TokenBudgetTrimmerTest {

    private final TokenBudgetTrimmer trimmer = new TokenBudgetTrimmer();

    private ContextBlock block(String level, String title, int tokens, boolean trimmable) {
        ContextBlock b = new ContextBlock();
        b.setLevel(level);
        b.setSource("test");
        b.setTitle(title);
        b.setContent("x");
        b.setEstimatedTokens(tokens);
        b.setTrimmable(trimmable);
        return b;
    }

    @Test
    void underBudget_keepsEverything() {
        SkillContext ctx = new SkillContext();
        ctx.addBlock(block("L1", "核心需求", 1000, false));
        ctx.addBlock(block("L5", "历史BUG", 500, true));
        trimmer.trim(ctx);
        assertEquals(2, ctx.getBlocks().size());
        assertTrue(ctx.getTrimmedNotes().isEmpty());
    }

    @Test
    void overBudget_trimsL5First() {
        SkillContext ctx = new SkillContext();
        ctx.addBlock(block("L1", "核心需求", 1500, false));
        ctx.addBlock(block("L2", "血缘", 800, false));
        ctx.addBlock(block("L3", "同模块", 1000, true));
        ctx.addBlock(block("L5", "历史BUG", 1200, true)); // L5 先裁：1500+800+1000=3300 ≤ 4000
        trimmer.trim(ctx);
        assertEquals(3, ctx.getBlocks().size());
        assertTrue(ctx.getBlocks().stream().noneMatch(b -> "L5".equals(b.getLevel())));
        assertEquals(1, ctx.getTrimmedNotes().size());
    }

    @Test
    void overBudget_keepsUntrimmableL1L2() {
        SkillContext ctx = new SkillContext();
        ctx.addBlock(block("L1", "核心需求", 2500, false));
        ctx.addBlock(block("L2", "血缘", 2000, false));
        ctx.addBlock(block("L5", "历史BUG", 500, true));
        ctx.addBlock(block("L3", "同模块", 500, true));
        // L5+L3 全裁完仍超预算，但 L1/L2 不裁
        trimmer.trim(ctx);
        assertEquals(2, ctx.getBlocks().size());
        assertEquals(2, ctx.getTrimmedNotes().size());
    }

    @Test
    void l0HasOwnBudget() {
        SkillContext ctx = new SkillContext();
        ctx.addBlock(block("L0", "知识库块1", 1500, true));
        ctx.addBlock(block("L0", "知识库块2", 1000, true)); // L0 超 2000 → 裁掉第二块
        ctx.addBlock(block("L1", "核心需求", 500, false));
        trimmer.trim(ctx);
        assertEquals(2, ctx.getBlocks().size());
        assertTrue(ctx.getBlocks().stream().anyMatch(b -> "知识库块1".equals(b.getTitle())));
        assertTrue(ctx.getBlocks().stream().noneMatch(b -> "知识库块2".equals(b.getTitle())));
    }

    @Test
    void estimateTokens() {
        assertEquals(0, ContextBlock.estimateTokens(null));
        assertEquals(0, ContextBlock.estimateTokens(""));
        assertEquals(5, ContextBlock.estimateTokens("0123456789"));
    }
}
