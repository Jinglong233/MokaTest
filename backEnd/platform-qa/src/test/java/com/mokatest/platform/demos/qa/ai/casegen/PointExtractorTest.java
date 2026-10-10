package com.mokatest.platform.demos.qa.ai.casegen;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PointExtractor 单测：bigram Jaccard 边界 + A 层三态分类。
 */
class PointExtractorTest {

    @Test
    void identicalStrings_scoreOne() {
        assertEquals(1.0, PointExtractor.bigramJaccard("登录测试", "登录测试"), 0.001);
    }

    @Test
    void disjointStrings_scoreZero() {
        assertEquals(0.0, PointExtractor.bigramJaccard("登录", "支付"), 0.001);
    }

    @Test
    void partialOverlap_betweenZeroAndOne() {
        double score = PointExtractor.bigramJaccard("登录功能测试", "登录异常测试");
        assertTrue(score > 0 && score < 1, "partial overlap should be between 0 and 1, got " + score);
    }

    @Test
    void whitespaceStripped_beforeBigram() {
        assertEquals(1.0, PointExtractor.bigramJaccard("登录 测试", "登录测试"), 0.001,
                "whitespace should not affect similarity");
    }

    @Test
    void emptyOrShortString_scoreZero() {
        assertEquals(0.0, PointExtractor.bigramJaccard("", "测试"));
        assertEquals(0.0, PointExtractor.bigramJaccard(null, "测试"));
        assertEquals(0.0, PointExtractor.bigramJaccard("a", "ab"));
    }

    @Test
    void classify_belowFloor_noClaim_uncovered() {
        assertEquals("UNCOVERED", PointExtractor.classifyByScore(0.05, false));
    }

    @Test
    void classify_belowFloor_hasClaim_claimedOnly() {
        assertEquals("CLAIMED_ONLY", PointExtractor.classifyByScore(0.05, true));
    }

    @Test
    void classify_aboveCeil_covered() {
        assertEquals("COVERED", PointExtractor.classifyByScore(0.50, false));
        assertEquals("COVERED", PointExtractor.classifyByScore(0.50, true));
    }

    @Test
    void classify_middleBand_ambiguous() {
        assertEquals("AMBIGUOUS", PointExtractor.classifyByScore(0.20, false));
        assertEquals("AMBIGUOUS", PointExtractor.classifyByScore(0.40, true));
    }

    @Test
    void evidenceScore_tpVsCaseText() {
        double score = PointExtractor.evidenceScore("登录失败重试", "登录失败后自动重试三次");
        assertTrue(score > 0, "related TP and case should have positive score");
    }

    // ==================== 测试点去重 ====================

    private static Map<String, Object> tp(String id, String title) {
        return Map.of("id", id, "title", title, "category", "FUNCTION");
    }

    @Test
    void dedup_emptyList_returnsEmpty() {
        assertTrue(PointExtractor.dedupTestPoints(List.of()).isEmpty());
    }

    @Test
    void dedup_singleElement_unchanged() {
        var tps = List.of(tp("TP-1", "登录测试"));
        var result = PointExtractor.dedupTestPoints(tps);
        assertEquals(1, result.size());
        assertEquals("TP-1", result.get(0).get("id"));
    }

    @Test
    void dedup_identicalTitles_merged() {
        var tps = List.of(tp("TP-1", "登录测试"), tp("TP-2", "登录测试"));
        var result = PointExtractor.dedupTestPoints(tps);
        assertEquals(1, result.size(), "identical titles should merge");
    }

    @Test
    void dedup_highSimilarity_merged() {
        var tps = List.of(tp("TP-1", "用户登录测试"), tp("TP-2", "用户登录测试验证"));
        var result = PointExtractor.dedupTestPoints(tps);
        assertEquals(1, result.size(), "similarity >= 0.7 should merge");
    }

    @Test
    void dedup_lowSimilarity_kept() {
        var tps = List.of(tp("TP-1", "登录测试"), tp("TP-2", "支付接口安全"));
        var result = PointExtractor.dedupTestPoints(tps);
        assertEquals(2, result.size(), "dissimilar titles should not merge");
    }

    @Test
    void dedup_keepsLongerTitle() {
        var tps = List.of(tp("TP-1", "用户登录测试"), tp("TP-2", "用户登录测试验证"));
        var result = PointExtractor.dedupTestPoints(tps);
        assertEquals(1, result.size());
        assertEquals("用户登录测试验证", result.get(0).get("title"), "should keep longer title");
    }
}
