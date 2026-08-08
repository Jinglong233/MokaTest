package com.mokatest.platform.demos.ai.skill.apicase;

import lombok.Data;

import java.util.List;

/**
 * AI 生成的 API 接口用例草稿（防腐层 DTO）
 *
 * 入库时映射为 api_request（api_node=INTERFACE，source_drat_id=锚定接口ID）。
 */
@Data
public class ApiCaseDraftDTO {

    /** 草稿ID（生成时后端分配，防重复入库的身份标识） */
    private String draftId;

    /** 用例名称（必填） */
    private String caseName;

    /** 用例说明 */
    private String description;

    /** 请求头变体（name/value/type/disabled），覆盖接口默认值 */
    private List<ParamItem> requestHeader;

    /** Query 参数变体 */
    private List<ParamItem> query;

    /** Body 变体：raw JSON 文本（保持接口原 body 结构做字段级调整） */
    private String bodyJson;

    /** 断言列表 */
    private List<AssertItem> assertions;

    /** 提取规则（可选） */
    private List<ExtractItem> extractions;

    @Data
    public static class ParamItem {
        private String name;
        private String value;
        private String type;
        private Boolean disabled;
    }

    @Data
    public static class AssertItem {
        /** HEADER / BODY / STATUS_CODE / RESPONSE_TIME / CUSTOM */
        private String apiAssertType;
        /** 字段（如 $.code / status） */
        private String field;
        /** EQUALS / NOT_EQUALS / CONTAINS / NOT_CONTAINS / GT / LT / GE / LE / REGULAR */
        private String assertRelationship;
        private String assertValue;
    }

    @Data
    public static class ExtractItem {
        /** JSON_PATH / HEADER */
        private String type;
        private String expression;
        private String variableName;
        private String defaultValue;
    }
}
