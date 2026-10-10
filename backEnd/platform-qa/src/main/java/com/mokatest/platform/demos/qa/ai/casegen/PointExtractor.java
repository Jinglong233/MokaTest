package com.mokatest.platform.demos.qa.ai.casegen;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 覆盖证据矩阵工具（A 层）：纯代码计算 TP × 用例/草稿 的关键词重叠分。
 *
 * bigram Jaccard 相似度：字符级 2-gram 集合的交并比。
 * 阈值 COVER_FLOOR/COVER_CEIL 将 TP-用例对分为确定态（UNCOVERED/CLAIMED_ONLY/COVERED）和模糊态（进 B 层模型判断）。
 * 模型能力差异对此层无影响——弱模型最多导致模糊对进 B 层多调一次，方向保守。
 */
public final class PointExtractor {

    /** 重叠分下限：低于此分数判定为未覆盖/挂名 */
    static final double COVER_FLOOR = 0.15;
    /** 重叠分上限：达到此分数判定为已覆盖 */
    static final double COVER_CEIL = 0.45;

    private PointExtractor() {}

    /**
     * bigram Jaccard 相似度：字符级 2-gram 集合的 |A∩B| / |A∪B|。
     * 空白字符全部剔除后提取 bigram，避免空格差异影响相似度。
     */
    public static double bigramJaccard(String a, String b) {
        Set<String> setA = bigrams(a);
        Set<String> setB = bigrams(b);
        if (setA.isEmpty() || setB.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return (double) intersection.size() / union.size();
    }

    /**
     * 证据分：TP 标题 vs 用例文本（名称+步骤拼接）的 bigram Jaccard。
     */
    public static double evidenceScore(String tpTitle, String caseText) {
        return bigramJaccard(tpTitle, caseText);
    }

    /**
     * 按证据分分类 verdict（A 层）。
     * @param score 证据分
     * @param hasClaim 用例/草稿是否声明覆盖该 TP（coverPoints 包含）
     * @return UNCOVERED / CLAIMED_ONLY / COVERED / AMBIGUOUS
     */
    public static String classifyByScore(double score, boolean hasClaim) {
        if (score < COVER_FLOOR) {
            return hasClaim ? "CLAIMED_ONLY" : "UNCOVERED";
        }
        if (score >= COVER_CEIL) {
            return "COVERED";
        }
        return "AMBIGUOUS";
    }

    // ==================== 测试点去重 ====================

    /**
     * 测试点去重：两两 bigram Jaccard ≥ 0.7 合并（保留信息更全的），
     * 同 category 下标题完全相同直接合并。
     *
     * @param testPoints 维度点清单（元素含 id/title/category）
     * @return 去重后的清单（新 List，不修改入参）
     */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> dedupTestPoints(List<Map<String, Object>> testPoints) {
        if (testPoints == null || testPoints.size() <= 1) {
            return testPoints != null ? new ArrayList<>(testPoints) : new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        boolean[] merged = new boolean[testPoints.size()];
        for (int i = 0; i < testPoints.size(); i++) {
            if (merged[i]) continue;
            Map<String, Object> current = new LinkedHashMap<>(testPoints.get(i));
            String curTitle = str(current, "title");
            String curCat = str(current, "category");
            for (int j = i + 1; j < testPoints.size(); j++) {
                if (merged[j]) continue;
                Map<String, Object> other = testPoints.get(j);
                String otherTitle = str(other, "title");
                String otherCat = str(other, "category");
                boolean shouldMerge = false;
                // 同分类同标题直接合并
                if (curCat.equals(otherCat) && curTitle.equals(otherTitle)) {
                    shouldMerge = true;
                }
                // bigram 相似度 ≥ 0.7 合并
                if (!shouldMerge && bigramJaccard(curTitle, otherTitle) >= 0.7) {
                    shouldMerge = true;
                }
                if (shouldMerge) {
                    // 保留信息更全的（title 更长 = 更具体）
                    if (otherTitle.length() > curTitle.length()) {
                        current = new LinkedHashMap<>(other);
                        curTitle = otherTitle;
                        curCat = otherCat;
                    }
                    merged[j] = true;
                }
            }
            result.add(current);
        }
        return result;
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v);
    }

    // ==================== 内部方法 ====================

    /** 提取字符级 2-gram 集合（去空白） */
    private static Set<String> bigrams(String s) {
        Set<String> result = new HashSet<>();
        if (s == null) return result;
        String clean = s.replaceAll("\\s+", "");
        if (clean.length() < 2) {
            if (!clean.isEmpty()) result.add(clean);
            return result;
        }
        for (int i = 0; i < clean.length() - 1; i++) {
            result.add(clean.substring(i, i + 2));
        }
        return result;
    }
}
