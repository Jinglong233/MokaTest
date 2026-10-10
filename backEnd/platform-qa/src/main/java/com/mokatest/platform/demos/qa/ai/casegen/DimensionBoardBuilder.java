package com.mokatest.platform.demos.qa.ai.casegen;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 维度清单面板数据派生：从 testPoints + 草稿池 + 审查结论计算每个维度点的展示状态。
 *
 * 状态机：PENDING（无草稿声明）→ COVERED（有声明且审查未标挂名）
 *        → ISSUE（审查 CLAIMED_ONLY / UNCOVERED）→ FAILED（自动循环耗尽仍未覆盖）
 *
 * 纯函数无依赖，供 SSE result 事件与记录恢复接口共用。
 */
public final class DimensionBoardBuilder {

    private DimensionBoardBuilder() {}

    /**
     * @param testPoints 维度点清单（[{id,title,category}]）
     * @param poolArr    草稿池（stage_payload.pool，元素含 caseName + coverPoints）
     * @param audit      审查结论（可为 null；含 verdicts: [{tpId,status,reason,claimedBy}]）
     * @param exhausted  自动补全循环是否已耗尽（仍有未覆盖时把 ISSUE 升级为 FAILED）
     */
    public static List<Map<String, Object>> build(List<Map<String, Object>> testPoints,
                                                  JSONArray poolArr,
                                                  Map<String, Object> audit,
                                                  boolean exhausted) {
        List<Map<String, Object>> dimensions = new ArrayList<>();
        if (testPoints == null || testPoints.isEmpty()) {
            return dimensions;
        }
        // 审查 verdict 索引：tpId → verdict
        Map<String, Map<String, Object>> verdictByTp = new HashMap<>();
        if (audit != null) {
            Object verdicts = audit.get("verdicts");
            if (verdicts instanceof List) {
                for (Object v : (List<?>) verdicts) {
                    if (v instanceof Map) {
                        Map<?, ?> vm = (Map<?, ?>) v;
                        Object tpId = vm.get("tpId");
                        if (tpId != null) verdictByTp.put(String.valueOf(tpId), new HashMap<>((Map<String, Object>) vm));
                    }
                }
            }
        }
        // 草稿名称索引：draftId → caseName（V2 caseDraftIds 用）
        Map<String, String> draftNameById = new HashMap<>();
        // 草稿声明索引：tpId → 声明覆盖它的草稿名称（最多3条）
        Map<String, List<String>> declaredBy = new LinkedHashMap<>();
        if (poolArr != null) {
            for (int i = 0; i < poolArr.size(); i++) {
                JSONObject draft = poolArr.getJSONObject(i);
                if (draft == null) continue;
                String draftId = draft.getString("draftId");
                String caseName = draft.getString("caseName");
                if (draftId != null && caseName != null) draftNameById.put(draftId, caseName);
                JSONArray coverPoints = draft.getJSONArray("coverPoints");
                if (coverPoints == null) continue;
                for (int j = 0; j < coverPoints.size(); j++) {
                    String tpId = String.valueOf(coverPoints.get(j));
                    declaredBy.computeIfAbsent(tpId, k -> new ArrayList<>());
                    if (declaredBy.get(tpId).size() < 3 && caseName != null) {
                        declaredBy.get(tpId).add(caseName);
                    }
                }
            }
        }

        for (Map<String, Object> tp : testPoints) {
            String tpId = String.valueOf(tp.get("id"));
            Map<String, Object> dim = new LinkedHashMap<>();
            dim.put("id", tpId);
            dim.put("title", tp.get("title"));
            dim.put("category", tp.get("category"));

            Map<String, Object> verdict = verdictByTp.get(tpId);
            String vStatus = verdict != null ? String.valueOf(verdict.get("status")) : null;
            List<String> caseNames = declaredBy.get(tpId);

            // V2：点状态优先（status/caseDraftIds），兼容 v1 回退 coverPoints+audit
            String tpStatus = tp.get("status") != null ? String.valueOf(tp.get("status")) : null;
            if (tpStatus != null) {
                // V2 caseDraftIds → 从 pool 查 caseNames
                List<String> v2Names = new ArrayList<>();
                Object cdIds = tp.get("caseDraftIds");
                if (cdIds instanceof List) {
                    for (Object id : (List<?>) cdIds) {
                        String name = draftNameById.get(String.valueOf(id));
                        if (name != null && v2Names.size() < 3) v2Names.add(name);
                    }
                }
                dim.put("caseNames", v2Names);
                switch (tpStatus) {
                    case "DONE":
                        // 审查可能标为 ISSUE（CLAIMED_ONLY）
                        if (verdict != null && "CLAIMED_ONLY".equals(vStatus)) {
                            dim.put("status", "ISSUE");
                            dim.put("issue", "用例声称覆盖但内容未体现"
                                    + (verdict.get("reason") != null ? ("：" + verdict.get("reason")) : ""));
                        } else {
                            dim.put("status", "COVERED");
                        }
                        break;
                    case "GENERATING": dim.put("status", "GENERATING"); break;
                    case "FAILED":
                        dim.put("status", "FAILED");
                        dim.put("issue", tp.get("failReason") != null
                                ? String.valueOf(tp.get("failReason"))
                                : "生成失败，可重试");
                        break;
                    default: dim.put("status", "PENDING"); break;
                }
                dimensions.add(dim);
                continue;
            }

            // V1 回退：无 status 字段，用 coverPoints + audit verdicts 推导
            dim.put("caseNames", caseNames != null ? caseNames : new ArrayList<>());

            if ("CLAIMED_ONLY".equals(vStatus)) {
                dim.put("status", "ISSUE");
                dim.put("issue", "用例声称覆盖但内容未体现"
                        + (verdict.get("reason") != null ? ("：" + verdict.get("reason")) : ""));
            } else if ("UNCOVERED".equals(vStatus)) {
                if (exhausted) {
                    dim.put("status", "FAILED");
                    dim.put("issue", "自动生成多轮后仍未覆盖，建议人工补充或重试");
                } else {
                    dim.put("status", "ISSUE");
                    dim.put("issue", "尚未有用例覆盖该维度");
                }
            } else if ("COVERED_ADOPTED".equals(vStatus) || "COVERED_DRAFT".equals(vStatus)) {
                dim.put("status", "COVERED");
            } else if (caseNames != null && !caseNames.isEmpty()) {
                // 无审查结论（或审查未覆盖到该点）：有声明即视为已覆盖
                dim.put("status", "COVERED");
            } else {
                dim.put("status", exhausted ? "FAILED" : "PENDING");
                if (exhausted) {
                    dim.put("issue", "自动生成多轮后仍未覆盖，建议人工补充或重试");
                }
            }
            dimensions.add(dim);
        }
        return dimensions;
    }
}
