package com.mokatest.platform.demos.ai.skill.apicase;

import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiAssertType;
import com.mokatest.platform.demos.api.domain.apiEnum.ExtractType;
import com.mokatest.platform.demos.api.domain.apiEnum.ParameterType;
import com.mokatest.platform.demos.api.domain.requestModel.ApiExtraction;
import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import com.mokatest.platform.demos.api.domain.requestModel.Body;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.enums.AssertRelationship;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * API 用例草稿 → api_request 实体映射（防腐层）
 *
 * 表结构/断言模型变更只需调整本类。
 */
@Component
public class ApiCaseDraftMapper {

    /**
     * 草稿转实体（基于锚定接口克隆，再用草稿变体覆盖）
     *
     * @param draft     草稿
     * @param base      锚定接口（作为克隆基底，保留 method/path/body 等）
     * @param projectId 项目ID（记录锚点）
     * @param teamId    团队ID（请求头上下文）
     */
    public ApiRequest toEntity(ApiCaseDraftDTO draft, ApiRequest base, Integer projectId, Integer teamId) {
        ApiRequest target = new ApiRequest();
        target.setProjectId(projectId);
        target.setTeamId(teamId);
        target.setApiNode(base.getApiNode());
        target.setRequestMethod(base.getRequestMethod());
        target.setRequestPath(base.getRequestPath());
        target.setApiType(base.getApiType());
        target.setApiName(draft.getCaseName().trim());

        // 参数变体：草稿提供则覆盖，否则沿用接口默认
        target.setRequestHeader(draft.getRequestHeader() != null
                ? toRequestParams(draft.getRequestHeader()) : base.getRequestHeader());
        target.setQuery(draft.getQuery() != null
                ? toRequestParams(draft.getQuery()) : base.getQuery());
        target.setCookies(base.getCookies());

        // body：草稿提供 bodyJson 时按 raw json 覆盖，否则沿用接口 body
        if (draft.getBodyJson() != null && !draft.getBodyJson().trim().isEmpty()) {
            Body body = new Body();
            body.setMode(base.getBody() != null ? base.getBody().getMode() : null);
            body.setJson(draft.getBodyJson());
            target.setBody(body);
        } else {
            target.setBody(base.getBody());
        }

        if (draft.getAssertions() != null) {
            List<AssertParameter> asserts = new ArrayList<>();
            for (ApiCaseDraftDTO.AssertItem item : draft.getAssertions()) {
                AssertParameter p = new AssertParameter();
                p.setApiAssertType(safeEnum(ApiAssertType.class, item.getApiAssertType(), ApiAssertType.STATUS_CODE));
                p.setField(item.getField());
                p.setAssertRelationship(safeEnum(AssertRelationship.class, item.getAssertRelationship(), AssertRelationship.EQUALS));
                p.setAssertValue(item.getAssertValue());
                asserts.add(p);
            }
            target.setApiResultAssert(asserts);
        } else {
            target.setApiResultAssert(base.getApiResultAssert());
        }

        if (draft.getExtractions() != null) {
            List<ApiExtraction> extractions = new ArrayList<>();
            for (ApiCaseDraftDTO.ExtractItem item : draft.getExtractions()) {
                ApiExtraction e = new ApiExtraction();
                e.setType(safeEnum(ExtractType.class, item.getType(), ExtractType.JSON_PATH));
                e.setExpression(item.getExpression());
                e.setVariableName(item.getVariableName());
                e.setDefaultValue(item.getDefaultValue());
                extractions.add(e);
            }
            target.setAssociationExtraction(extractions);
        }
        return target;
    }

    private List<RequestParameter> toRequestParams(List<ApiCaseDraftDTO.ParamItem> items) {
        List<RequestParameter> list = new ArrayList<>();
        for (ApiCaseDraftDTO.ParamItem item : items) {
            RequestParameter p = new RequestParameter();
            p.setName(item.getName());
            p.setValue(item.getValue());
            if (item.getType() != null) {
                try {
                    p.setType(ParameterType.valueOf(item.getType().toUpperCase()));
                } catch (IllegalArgumentException ignored) {
                    // 非法类型留空，执行器按默认处理
                }
            }
            p.setDisabled(Boolean.TRUE.equals(item.getDisabled()));
            list.add(p);
        }
        return list;
    }

    private <E extends Enum<E>> E safeEnum(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
