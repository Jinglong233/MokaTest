package com.mokatest.platform.demos.qa.ai.casegen;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mokatest.platform.demos.ai.skill.DraftGenResult;
import com.mokatest.platform.demos.exception.BusinessException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用例草稿解析器：模型原始输出 → DraftGenResult&lt;CaseDraftDTO&gt;
 *
 * 输出契约：{"test_cases": [...], "uncertainties": [...]}；
 * 兼容模型仍返回裸 JSON 数组的情况（视为无 uncertainties 的旧格式）。
 *
 * 容错：
 * - 容忍 ```json 代码块包裹、前后多余文字（截取首个 {/[ 到末个 }/]）
 * - 枚举非法值兜底（caseType→FUNCTION，priority→P2）并置 enumFallback 标黄
 * - 结构非法（非 JSON / 无有效条目）抛 BusinessException，由上层重试一次
 */
public class CaseDraftParser {

    private static final Set<String> VALID_TYPES = new HashSet<>(
            Arrays.asList("FUNCTION", "API", "PERFORMANCE", "COMPATIBILITY", "SMOKE"));
    private static final Set<String> VALID_PRIORITIES = new HashSet<>(
            Arrays.asList("P0", "P1", "P2"));

    public DraftGenResult<CaseDraftDTO> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BusinessException("模型输出为空");
        }
        DraftGenResult<CaseDraftDTO> result = new DraftGenResult<>();
        JSONArray array = extractTestCases(raw, result.getUncertainties());
        List<CaseDraftDTO> drafts = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj == null) {
                continue;
            }
            CaseDraftDTO draft = toDraft(obj);
            if (draft.getCaseName() != null && !draft.getCaseName().trim().isEmpty()) {
                drafts.add(draft);
            }
        }
        if (drafts.isEmpty()) {
            throw new BusinessException("模型输出中没有有效用例条目");
        }
        result.setDrafts(drafts);
        return result;
    }

    /** 截取模型输出中的用例数组：优先按新契约对象解析，兼容旧裸数组格式 */
    private JSONArray extractTestCases(String raw, List<String> uncertainties) {
        String text = raw.trim();
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        // 新契约：{ 出现在 [ 之前 → 按对象解析
        if (objStart >= 0 && (arrStart < 0 || objStart < arrStart)) {
            int objEnd = text.lastIndexOf('}');
            if (objEnd <= objStart) {
                throw new BusinessException("模型输出不是合法 JSON 对象");
            }
            JSONObject root;
            try {
                root = JSON.parseObject(text.substring(objStart, objEnd + 1));
            } catch (Exception e) {
                throw new BusinessException("模型输出不是合法 JSON 对象");
            }
            JSONArray array = root.getJSONArray("test_cases");
            if (array == null) {
                throw new BusinessException("模型输出缺少 test_cases 数组");
            }
            JSONArray unc = root.getJSONArray("uncertainties");
            if (unc != null) {
                for (int i = 0; i < unc.size(); i++) {
                    String item = unc.getString(i);
                    if (item != null && !item.trim().isEmpty()) {
                        uncertainties.add(item.trim());
                    }
                }
            }
            return array;
        }
        // 旧格式：裸数组
        int end = text.lastIndexOf(']');
        if (arrStart < 0 || end <= arrStart) {
            throw new BusinessException("模型输出中未找到 JSON 数组");
        }
        try {
            return JSON.parseArray(text.substring(arrStart, end + 1));
        } catch (Exception e) {
            throw new BusinessException("模型输出不是合法 JSON 数组");
        }
    }

    private CaseDraftDTO toDraft(JSONObject obj) {
        CaseDraftDTO draft = new CaseDraftDTO();
        boolean fallback = false;

        draft.setCaseName(obj.getString("caseName"));
        draft.setPreCondition(obj.getString("preCondition"));
        draft.setTags(obj.getString("tags"));
        draft.setExpectDuration(obj.getInteger("expectDuration"));

        String caseType = normalize(obj.getString("caseType"));
        if (caseType == null || !VALID_TYPES.contains(caseType)) {
            if (caseType != null) {
                fallback = true;
            }
            caseType = "FUNCTION";
        }
        draft.setCaseType(caseType);

        String priority = normalize(obj.getString("priority"));
        if (priority == null || !VALID_PRIORITIES.contains(priority)) {
            if (priority != null) {
                fallback = true;
            }
            priority = "P2";
        }
        draft.setPriority(priority);

        JSONArray steps = obj.getJSONArray("testSteps");
        List<CaseDraftDTO.StepItem> stepItems = new ArrayList<>();
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                JSONObject s = steps.getJSONObject(i);
                if (s == null || s.getString("step") == null) {
                    continue;
                }
                CaseDraftDTO.StepItem item = new CaseDraftDTO.StepItem();
                item.setStep(s.getString("step"));
                item.setExpected(s.getString("expected"));
                stepItems.add(item);
            }
        }
        draft.setTestSteps(stepItems);
        draft.setEnumFallback(fallback);
        return draft;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
