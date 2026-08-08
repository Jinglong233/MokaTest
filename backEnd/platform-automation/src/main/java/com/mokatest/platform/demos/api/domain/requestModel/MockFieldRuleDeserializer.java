package com.mokatest.platform.demos.api.domain.requestModel;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * MockFieldRule 统一反序列化器
 *
 * 兼容以下旧格式：
 *   null：返回 null（子节点）
 *   数组：包装成 fieldType=OBJECT 的根节点（Mock 旧 rules / 数据模板旧 templateSchema）
 *   对象：手动字段映射，避免递归调用
 *
 * @author JingLong
 * @since 2026-06-17
 */
public class MockFieldRuleDeserializer extends JsonDeserializer<MockFieldRule> {

    @Override
    public MockFieldRule deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);
        return deserializeNode(node);
    }

    private MockFieldRule deserializeNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            return wrapArrayToRoot(node);
        }
        if (node.isObject()) {
            return deserializeObject(node);
        }
        return null;
    }

    private MockFieldRule createRoot() {
        MockFieldRule root = new MockFieldRule();
        root.setFieldType("OBJECT");
        root.setChildren(new ArrayList<>());
        return root;
    }

    private MockFieldRule wrapArrayToRoot(JsonNode arrayNode) {
        MockFieldRule root = createRoot();
        List<MockFieldRule> children = new ArrayList<>();
        for (JsonNode childNode : arrayNode) {
            if (childNode == null || childNode.isNull()) {
                continue;
            }
            children.add(deserializeObject(childNode));
        }
        root.setChildren(children);
        return root;
    }

    private MockFieldRule deserializeObject(JsonNode node) {
        MockFieldRule rule = new MockFieldRule();

        rule.setFieldName(getString(node, "fieldName"));
        rule.setDescription(getString(node, "description"));
        rule.setFieldType(getString(node, "fieldType"));
        rule.setNullable(getBoolean(node, "nullable"));
        rule.setRequired(getBoolean(node, "required"));
        rule.setIsEnum(getBoolean(node, "isEnum"));
        rule.setIsConstant(getBoolean(node, "isConstant"));
        rule.setRuleType(getString(node, "ruleType"));
        rule.setFixedValue(getString(node, "fixedValue"));
        rule.setArrayLength(getInteger(node, "arrayLength"));
        rule.setArrayElementType(getString(node, "arrayElementType"));
        rule.setMinItems(getInteger(node, "minItems"));
        rule.setMaxItems(getInteger(node, "maxItems"));
        rule.setUniqueItems(getBoolean(node, "uniqueItems"));
        rule.setTemplateId(getInteger(node, "templateId"));
        rule.setExcludedFields(getStringList(node, "excludedFields"));
        rule.setFormat(getString(node, "format"));
        rule.setMinLength(getInteger(node, "minLength"));
        rule.setMaxLength(getInteger(node, "maxLength"));
        rule.setMin(getNumber(node, "min"));
        rule.setMax(getNumber(node, "max"));
        rule.setScale(getInteger(node, "scale"));
        rule.setPattern(getString(node, "pattern"));
        rule.setDefaultValue(getString(node, "defaultValue"));
        rule.setChoices(getString(node, "choices"));
        rule.setCharset(getString(node, "charset"));
        rule.setCaseType(getString(node, "caseType"));
        rule.setLength(getInteger(node, "length"));
        rule.setLocale(getString(node, "locale"));

        // 兼容旧 TemplateFieldRule 的 type 字段
        if (node.has("type") && !node.get("type").isNull()) {
            String oldType = node.get("type").asText();
            rule.setRuleType(oldType);
            rule.setFieldType(mapOldTypeToFieldType(oldType));
        }

        // 兼容旧 MockFieldRuleParams 对象
        if (node.has("params") && !node.get("params").isNull()) {
            MockFieldRuleParams params = deserializeParams(node.get("params"));
            rule.setParams(params);
            // 顶层为空时，用旧 params 回填
            fallbackFromParams(rule, params);
        }

        // 递归处理子节点
        if (node.has("children") && !node.get("children").isNull()) {
            JsonNode childrenNode = node.get("children");
            if (childrenNode.isArray()) {
                List<MockFieldRule> children = new ArrayList<>();
                for (JsonNode childNode : childrenNode) {
                    if (childNode == null || childNode.isNull()) {
                        continue;
                    }
                    children.add(deserializeObject(childNode));
                }
                rule.setChildren(children);
            }
        }

        return rule;
    }

    private MockFieldRuleParams deserializeParams(JsonNode node) {
        MockFieldRuleParams params = new MockFieldRuleParams();
        params.setLocale(getString(node, "locale"));
        params.setMin(getInteger(node, "min"));
        params.setMax(getInteger(node, "max"));
        params.setScale(getInteger(node, "scale"));
        params.setLength(getInteger(node, "length"));
        params.setFormat(getString(node, "format"));
        params.setChoices(getString(node, "choices"));
        return params;
    }

    private void fallbackFromParams(MockFieldRule rule, MockFieldRuleParams params) {
        if (params == null) {
            return;
        }
        if (rule.getLocale() == null && params.getLocale() != null) {
            rule.setLocale(params.getLocale());
        }
        if (rule.getMin() == null && params.getMin() != null) {
            rule.setMin(params.getMin());
        }
        if (rule.getMax() == null && params.getMax() != null) {
            rule.setMax(params.getMax());
        }
        if (rule.getScale() == null && params.getScale() != null) {
            rule.setScale(params.getScale());
        }
        if (rule.getLength() == null && params.getLength() != null) {
            rule.setLength(params.getLength());
        }
        if (rule.getFormat() == null && params.getFormat() != null) {
            rule.setFormat(params.getFormat());
        }
        if (rule.getChoices() == null && params.getChoices() != null) {
            rule.setChoices(params.getChoices());
        }
    }

    private List<String> getStringList(JsonNode node, String field) {
        if (!node.has(field) || !node.get(field).isArray()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        for (JsonNode item : node.get(field)) {
            if (item != null && !item.isNull()) {
                list.add(item.asText());
            }
        }
        return list;
    }

    private String getString(JsonNode node, String field) {        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return null;
    }

    private Boolean getBoolean(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asBoolean();
        }
        return null;
    }

    private Integer getInteger(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asInt();
        }
        return null;
    }

    private Number getNumber(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).numberValue();
        }
        return null;
    }

    private String mapOldTypeToFieldType(String type) {
        if (type == null) {
            return "STRING";
        }
        switch (type.trim().toLowerCase()) {
            case "int":
                return "INT";
            case "float":
                return "FLOAT";
            default:
                return "STRING";
        }
    }
}
