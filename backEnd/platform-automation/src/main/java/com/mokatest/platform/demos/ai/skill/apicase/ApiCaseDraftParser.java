package com.mokatest.platform.demos.ai.skill.apicase;

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
 * API 用例草稿解析器：模型原始输出 → DraftGenResult&lt;ApiCaseDraftDTO&gt;
 *
 * 输出契约：{"test_cases": [...], "uncertainties": [...]}；
 * 兼容模型仍返回裸 JSON 数组的情况（视为无 uncertainties 的旧格式）。
 *
 * 断言枚举非法值兜底：type→STATUS_CODE、relationship→EQUALS，并打 fallback 标黄标记。
 */
public class ApiCaseDraftParser {

    private static final Set<String> VALID_ASSERT_TYPES = new HashSet<>(
            Arrays.asList("HEADER", "BODY", "STATUS_CODE", "RESPONSE_TIME", "CUSTOM"));
    private static final Set<String> VALID_RELATIONSHIPS = new HashSet<>(
            Arrays.asList("EQUALS", "NOT_EQUALS", "CONTAINS", "NOT_CONTAINS", "GT", "LT", "GE", "LE", "REGULAR"));
    private static final Set<String> VALID_EXTRACT_TYPES = new HashSet<>(
            Arrays.asList("JSON_PATH", "HEADER"));

    public DraftGenResult<ApiCaseDraftDTO> parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BusinessException("模型输出为空");
        }
        DraftGenResult<ApiCaseDraftDTO> result = new DraftGenResult<>();
        JSONArray array = extractTestCases(raw, result.getUncertainties());
        List<ApiCaseDraftDTO> drafts = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj == null) {
                continue;
            }
            ApiCaseDraftDTO draft = toDraft(obj);
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

    private ApiCaseDraftDTO toDraft(JSONObject obj) {
        ApiCaseDraftDTO draft = new ApiCaseDraftDTO();
        draft.setCaseName(obj.getString("caseName"));
        draft.setDescription(obj.getString("description"));
        draft.setBodyJson(obj.getString("bodyJson"));
        draft.setRequestHeader(toParams(obj.getJSONArray("requestHeader")));
        draft.setQuery(toParams(obj.getJSONArray("query")));
        draft.setAssertions(toAssertions(obj.getJSONArray("assertions")));
        draft.setExtractions(toExtractions(obj.getJSONArray("extractions")));
        return draft;
    }

    private List<ApiCaseDraftDTO.ParamItem> toParams(JSONArray arr) {
        if (arr == null) {
            return null;
        }
        List<ApiCaseDraftDTO.ParamItem> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null || o.getString("name") == null) {
                continue;
            }
            ApiCaseDraftDTO.ParamItem item = new ApiCaseDraftDTO.ParamItem();
            item.setName(o.getString("name"));
            item.setValue(o.getString("value"));
            item.setType(o.getString("type"));
            item.setDisabled(o.getBoolean("disabled"));
            list.add(item);
        }
        return list;
    }

    private List<ApiCaseDraftDTO.AssertItem> toAssertions(JSONArray arr) {
        if (arr == null) {
            return null;
        }
        List<ApiCaseDraftDTO.AssertItem> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            ApiCaseDraftDTO.AssertItem item = new ApiCaseDraftDTO.AssertItem();
            String type = normalize(o.getString("apiAssertType"));
            item.setApiAssertType(type != null && VALID_ASSERT_TYPES.contains(type) ? type : "STATUS_CODE");
            item.setField(o.getString("field"));
            String rel = normalize(o.getString("assertRelationship"));
            item.setAssertRelationship(rel != null && VALID_RELATIONSHIPS.contains(rel) ? rel : "EQUALS");
            item.setAssertValue(o.getString("assertValue"));
            list.add(item);
        }
        return list;
    }

    private List<ApiCaseDraftDTO.ExtractItem> toExtractions(JSONArray arr) {
        if (arr == null) {
            return null;
        }
        List<ApiCaseDraftDTO.ExtractItem> list = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            ApiCaseDraftDTO.ExtractItem item = new ApiCaseDraftDTO.ExtractItem();
            String type = normalize(o.getString("type"));
            item.setType(type != null && VALID_EXTRACT_TYPES.contains(type) ? type : "JSON_PATH");
            item.setExpression(o.getString("expression"));
            item.setVariableName(o.getString("variableName"));
            item.setDefaultValue(o.getString("defaultValue"));
            list.add(item);
        }
        return list;
    }

    private String normalize(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim().toUpperCase();
    }
}
