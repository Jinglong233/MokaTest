package com.mokatest.platform.demos.qa.ai.casegen;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DimensionBoardBuilder 单测：维度状态派生全路径覆盖。
 *
 * 状态机：PENDING（无声明）→ COVERED（有声明且审查未标挂名）
 *        → ISSUE（CLAIMED_ONLY / UNCOVERED 未耗尽）→ FAILED（UNCOVERED 耗尽 / 无声明耗尽）
 */
class DimensionBoardBuilderTest {

    /** 构造单个 testPoint */
    private static Map<String, Object> tp(String id, String title) {
        return Map.of("id", id, "title", title, "category", "FUNCTION");
    }

    /** 构造草稿池 JSONArray */
    private static JSONArray pool(String caseName, String... coverPoints) {
        JSONArray arr = new JSONArray();
        JSONObject draft = new JSONObject();
        draft.put("caseName", caseName);
        JSONArray cps = new JSONArray();
        for (String cp : coverPoints) cps.add(cp);
        draft.put("coverPoints", cps);
        arr.add(draft);
        return arr;
    }

    /** 构造审查结论 */
    private static Map<String, Object> audit(String tpId, String status) {
        JSONObject v = new JSONObject();
        v.put("tpId", tpId);
        v.put("status", status);
        v.put("reason", "test reason");
        v.put("claimedBy", "caseA");
        return Map.of("verdicts", List.of(v));
    }

    @Test
    void noDrafts_noAudit_pending() {
        var dims = DimensionBoardBuilder.build(List.of(tp("TP-1", "登录")), null, null, false);
        assertEquals(1, dims.size());
        assertEquals("PENDING", dims.get(0).get("status"));
    }

    @Test
    void hasDraftClaim_noAudit_covered() {
        var dims = DimensionBoardBuilder.build(
                List.of(tp("TP-1", "登录")), pool("登录成功测试", "TP-1"), null, false);
        assertEquals("COVERED", dims.get(0).get("status"));
        assertFalse(((List<?>) dims.get(0).get("caseNames")).isEmpty());
    }

    @Test
    void auditCoveredAdopted_covered() {
        var dims = DimensionBoardBuilder.build(
                List.of(tp("TP-1", "登录")), pool("登录测试", "TP-1"), audit("TP-1", "COVERED_ADOPTED"), false);
        assertEquals("COVERED", dims.get(0).get("status"));
    }

    @Test
    void auditCoveredDraft_covered() {
        var dims = DimensionBoardBuilder.build(
                List.of(tp("TP-1", "登录")), pool("登录测试", "TP-1"), audit("TP-1", "COVERED_DRAFT"), false);
        assertEquals("COVERED", dims.get(0).get("status"));
    }

    @Test
    void auditClaimedOnly_issue() {
        var dims = DimensionBoardBuilder.build(
                List.of(tp("TP-1", "登录")), pool("登录测试", "TP-1"), audit("TP-1", "CLAIMED_ONLY"), false);
        assertEquals("ISSUE", dims.get(0).get("status"));
        assertNotNull(dims.get(0).get("issue"));
        assertTrue(((String) dims.get(0).get("issue")).contains("声称覆盖但内容未体现"));
    }

    @Test
    void auditUncovered_notExhausted_issue() {
        var dims = DimensionBoardBuilder.build(
                List.of(tp("TP-1", "登录")), null, audit("TP-1", "UNCOVERED"), false);
        assertEquals("ISSUE", dims.get(0).get("status"));
        assertEquals("尚未有用例覆盖该维度", dims.get(0).get("issue"));
    }

    @Test
    void auditUncovered_exhausted_failed() {
        var dims = DimensionBoardBuilder.build(
                List.of(tp("TP-1", "登录")), null, audit("TP-1", "UNCOVERED"), true);
        assertEquals("FAILED", dims.get(0).get("status"));
        assertNotNull(dims.get(0).get("issue"));
    }

    @Test
    void noClaim_exhausted_failed() {
        var dims = DimensionBoardBuilder.build(
                List.of(tp("TP-1", "登录")), null, null, true);
        assertEquals("FAILED", dims.get(0).get("status"));
    }

    @Test
    void noClaim_notExhausted_pending() {
        var dims = DimensionBoardBuilder.build(
                List.of(tp("TP-1", "登录")), null, null, false);
        assertEquals("PENDING", dims.get(0).get("status"));
    }

    @Test
    void multipleTps_mixedStates() {
        var tps = List.of(tp("TP-1", "登录"), tp("TP-2", "登出"), tp("TP-3", "注册"));
        var poolArr = pool("登录测试", "TP-1");
        var auditMap = audit("TP-2", "CLAIMED_ONLY");
        var dims = DimensionBoardBuilder.build(tps, poolArr, auditMap, false);
        assertEquals(3, dims.size());
        assertEquals("COVERED", dims.get(0).get("status"));  // TP-1: has claim, no audit verdict
        assertEquals("ISSUE", dims.get(1).get("status"));     // TP-2: CLAIMED_ONLY
        assertEquals("PENDING", dims.get(2).get("status"));   // TP-3: no claim, no verdict
    }

    @Test
    void emptyTestPoints_returnsEmpty() {
        var dims = DimensionBoardBuilder.build(List.of(), null, null, false);
        assertTrue(dims.isEmpty());
    }
}
