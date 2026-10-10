package com.mokatest.platform.demos.qa.ai.casegen;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mokatest.platform.demos.ai.skill.DraftGenResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用例流式增量解析器：对 LLM 流式输出的 JSON 文本做增量解析，按条产出 CaseDraftDTO。
 *
 * 每个 onPartialResponse chunk 喂入 {@link #feed}，{@link #drainNew} 取出新解析完成的完整用例。
 * 流结束后 {@link #finish} 做全量解析（整包优先，增量兜底）。
 *
 * 单条解析失败只影响单条，不中断流。
 */
public class CaseStreamParser {

    private static final Set<String> VALID_TYPES = new HashSet<>(
            Arrays.asList("FUNCTION", "API", "PERFORMANCE", "COMPATIBILITY", "SMOKE"));
    private static final Set<String> VALID_PRIORITIES = new HashSet<>(
            Arrays.asList("P0", "P1", "P2"));

    private final StringBuilder buffer = new StringBuilder();
    private final List<CaseDraftDTO> parsed = new ArrayList<>();
    private final List<String> parseErrors = new ArrayList<>();
    private int drainOffset = 0;
    private int arrayStart = -1;
    // 增量解析状态机字段（跨 feed 持久化，避免重扫）
    private int scanPos = -1;
    private int depth = 0;
    private int objStart = -1;
    private boolean inString = false;
    private boolean escape = false;

    /** 喂入流式 chunk */
    public void feed(String chunk) {
        if (chunk == null || chunk.isEmpty()) return;
        buffer.append(chunk);
        tryParseNewObjects();
    }

    /** 取出新解析完成的用例（每次调用后偏移前进） */
    public List<CaseDraftDTO> drainNew() {
        if (drainOffset >= parsed.size()) return List.of();
        List<CaseDraftDTO> result = new ArrayList<>(parsed.subList(drainOffset, parsed.size()));
        drainOffset = parsed.size();
        return result;
    }

    private java.util.List<java.util.Map<String, Object>> testPoints = new java.util.ArrayList<>();

    /** 流结束后全量解析（整包优先，增量兜底） */
    public DraftGenResult<CaseDraftDTO> finish() {
        String full = buffer.toString();
        DraftGenResult<CaseDraftDTO> result = new DraftGenResult<>();
        // 1. 整包解析优先
        try {
            String cleaned = stripCodeFence(full);
            JSONObject root = JSON.parseObject(cleaned);
            if (root != null) {
                // 解析 test_points（首轮有，后续轮为空）
                JSONArray tpArr = root.getJSONArray("test_points");
                if (tpArr != null) {
                    for (int i = 0; i < tpArr.size(); i++) {
                        JSONObject tp = tpArr.getJSONObject(i);
                        if (tp != null) {
                            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
                            m.put("id", tp.getString("id"));
                            m.put("title", tp.getString("title"));
                            m.put("category", tp.getString("category"));
                            m.put("priority", tp.getString("priority"));
                            testPoints.add(m);
                        }
                    }
                }
                JSONArray testCases = root.getJSONArray("test_cases");
                if (testCases != null && !testCases.isEmpty()) {
                    for (int i = 0; i < testCases.size(); i++) {
                        JSONObject obj = testCases.getJSONObject(i);
                        if (obj == null) continue;
                        CaseDraftDTO draft = mapToDraft(obj);
                        if (draft.getCaseName() != null && !draft.getCaseName().trim().isEmpty()) {
                            result.getDrafts().add(draft);
                        }
                    }
                }
                JSONArray unc = root.getJSONArray("uncertainties");
                if (unc != null) {
                    for (int i = 0; i < unc.size(); i++) {
                        String item = unc.getString(i);
                        if (item != null && !item.trim().isEmpty()) {
                            result.getUncertainties().add(item.trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 整包解析失败，回退增量解析结果
        }
        // 2. 整包为空时回退增量解析
        if (result.getDrafts().isEmpty() && !parsed.isEmpty()) {
            result.getDrafts().addAll(parsed);
        }
        return result;
    }

    public List<String> getParseErrors() {
        return parseErrors;
    }

    /** 获取首轮解析出的维度点列表（后续轮为空） */
    public java.util.List<java.util.Map<String, Object>> getTestPoints() {
        return testPoints;
    }

    public String getFullText() {
        return buffer.toString();
    }

    // ==================== 增量解析 ====================

    /** 在 buffer 中增量定位 test_cases 数组内的完整 {...} 对象（状态机跨 feed 持久化，不重扫） */
    private void tryParseNewObjects() {
        // 定位数组开始位置（只找一次）
        if (arrayStart < 0) {
            int keyIdx = buffer.indexOf("test_cases");
            if (keyIdx < 0) return;
            arrayStart = buffer.indexOf("[", keyIdx);
            if (arrayStart < 0) return;
            scanPos = arrayStart + 1;
        }
        if (scanPos < 0) return;

        for (int i = scanPos; i < buffer.length(); i++) {
            char c = buffer.charAt(i);
            if (escape) { escape = false; scanPos = i + 1; continue; }
            if (c == '\\') { escape = true; scanPos = i + 1; continue; }
            if (c == '"') { inString = !inString; scanPos = i + 1; continue; }
            if (inString) { scanPos = i + 1; continue; }

            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String objJson = buffer.substring(objStart, i + 1);
                    try {
                        JSONObject obj = JSON.parseObject(objJson);
                        CaseDraftDTO draft = mapToDraft(obj);
                        if (draft.getCaseName() != null && !draft.getCaseName().trim().isEmpty()) {
                            parsed.add(draft);
                        }
                    } catch (Exception e) {
                        parseErrors.add("第 " + (parsed.size() + parseErrors.size() + 1) + " 条解析失败: " + e.getMessage());
                    }
                    objStart = -1;
                }
                if (depth < 0) { scanPos = i + 1; return; } // 数组已结束
            }
            scanPos = i + 1;
        }
    }

    // ==================== JSON -> DTO ====================

    private CaseDraftDTO mapToDraft(JSONObject obj) {
        CaseDraftDTO draft = new CaseDraftDTO();
        boolean fallback = false;
        draft.setCaseName(obj.getString("caseName"));
        draft.setPreCondition(obj.getString("preCondition"));
        draft.setTags(obj.getString("tags"));
        draft.setExpectDuration(obj.getInteger("expectDuration"));
        String caseType = normalizeEnum(obj.getString("caseType"));
        if (caseType == null || !VALID_TYPES.contains(caseType)) {
            if (caseType != null) fallback = true;
            caseType = "FUNCTION";
        }
        draft.setCaseType(caseType);
        String priority = normalizeEnum(obj.getString("priority"));
        if (priority == null || !VALID_PRIORITIES.contains(priority)) {
            if (priority != null) fallback = true;
            priority = "P2";
        }
        draft.setPriority(priority);
        JSONArray steps = obj.getJSONArray("testSteps");
        if (steps != null) {
            List<CaseDraftDTO.StepItem> stepItems = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                JSONObject s = steps.getJSONObject(i);
                if (s == null || s.getString("step") == null) continue;
                CaseDraftDTO.StepItem item = new CaseDraftDTO.StepItem();
                item.setStep(s.getString("step"));
                item.setExpected(s.getString("expected"));
                stepItems.add(item);
            }
            draft.setTestSteps(stepItems);
        }
        draft.setDesignMethod(normalizeEnum(obj.getString("designMethod")));
        JSONArray coverPoints = obj.getJSONArray("coverPoints");
        if (coverPoints != null) {
            List<String> cpList = new ArrayList<>();
            for (int i = 0; i < coverPoints.size(); i++) {
                String cp = coverPoints.getString(i);
                if (cp != null && !cp.trim().isEmpty()) cpList.add(cp.trim());
            }
            draft.setCoverPoints(cpList);
        }
        draft.setEnumFallback(fallback);
        return draft;
    }

    private static String normalizeEnum(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value.trim().toUpperCase();
    }

    /** 去除 markdown 代码块围栏 */
    private static String stripCodeFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNl = t.indexOf('\n');
            if (firstNl > 0) {
                t = t.substring(firstNl + 1);
            } else {
                t = t.substring(3);
            }
            t = t.replaceFirst("^(json|JSON|javascript|js)\\s*\\n?", "");
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }
}
