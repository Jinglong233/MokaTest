package com.mokatest.platform.demos.api.http.validation;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiAssertType;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import com.mokatest.platform.demos.api.domain.requestModel.ResponseSchema;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.util.DataTemplateFunctionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 响应结构校验器（对标 Apifox 数据模型的结构校验）
 *
 * 按 MockFieldRule 规则树递归校验实际响应 JSON：
 *   - 字段存在性（required=false 以外均视为必填）
 *   - fieldType 类型匹配（STRING/INT/LONG/FLOAT/DOUBLE/BOOLEAN/OBJECT/ARRAY）
 *   - nullable 允许 null
 *   - choices 枚举值（仅 ruleType=choice 时校验）
 *   - ARRAY 的 minItems/maxItems 元素个数
 *   - TEMPLATE 节点：加载模板 schema（含继承链合并），应用 excludedFields/children 覆盖后递归校验
 *
 * 数值 min/max、字符串 minLength/maxLength 属于"生成约束"（有默认值无法区分用户是否显式配置），
 * 不参与校验，避免误报。
 *
 * @author JingLong
 * @since 2026-08-06
 */
public class SchemaValidator {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

    /** 单条校验违例 */
    public record SchemaViolation(String path, String message) {
        @Override
        public String toString() {
            return path + ": " + message;
        }
    }

    /**
     * 按接口响应定义校验执行结果。不适用（未定义/关闭/非JSON响应）时返回 null。
     */
    public static AssertResult validate(ResponseSchema rs, TestHttpResponse response) {
        if (rs == null || rs.getMode() == null || rs.getMode() == ResponseSchema.Mode.NONE
                || Boolean.FALSE.equals(rs.getValidateEnabled()) || response == null) {
            return null;
        }
        MockFieldRule root = resolveSchema(rs);
        if (root == null) {
            return null;
        }
        String body = response.getBodyAsString();
        Object actual;
        try {
            actual = JSON.parse(body);
        } catch (Exception e) {
            AssertResult result = new AssertResult(false, "响应体不是合法 JSON，无法做结构校验");
            result.setAssertType(ApiAssertType.SCHEMA.name());
            return result;
        }
        List<SchemaViolation> violations = new ArrayList<>();
        validateNode(root, actual, "$", violations);

        AssertResult result = new AssertResult();
        result.setAssertType(ApiAssertType.SCHEMA.name());
        result.setSuccess(violations.isEmpty());
        if (violations.isEmpty()) {
            result.setAssertTip("结构校验通过");
        } else {
            StringBuilder tip = new StringBuilder("结构校验失败（共 ").append(violations.size()).append(" 处）：");
            violations.stream().limit(5).forEach(v -> tip.append(v).append("；"));
            if (violations.size() > 5) {
                tip.append("…");
            }
            result.setAssertTip(tip.toString());
        }
        return result;
    }

    /**
     * 解析响应定义的最终规则树（公开供 Mock 生成等复用）：
     * INLINE 直接用内联 schema；TEMPLATE 加载模板（含继承链）并应用引用处隐藏/覆盖。
     */
    public static MockFieldRule resolveSchema(ResponseSchema rs) {
        if (rs.getMode() == ResponseSchema.Mode.INLINE) {
            return rs.getSchema();
        }
        if (rs.getMode() == ResponseSchema.Mode.TEMPLATE && rs.getTemplateId() != null) {
            MockFieldRule schema = DataTemplateFunctionExecutor.resolveSchemaById(rs.getTemplateId());
            return applyOverrides(schema, rs.getHiddenFields(), rs.getOverrideFields());
        }
        return null;
    }

    /**
     * 应用引用处隐藏/覆盖（返回新树，不改原树）：
     * hiddenFields 按 fieldName 剔除，overrideFields 同名字段替换。
     */
    public static MockFieldRule applyOverrides(MockFieldRule schema, List<String> hiddenFields, List<MockFieldRule> overrideFields) {
        if (schema == null || schema.getChildren() == null) {
            return schema;
        }
        boolean hasHidden = hiddenFields != null && !hiddenFields.isEmpty();
        boolean hasOverride = overrideFields != null && !overrideFields.isEmpty();
        if (!hasHidden && !hasOverride) {
            return schema;
        }
        MockFieldRule copy = JSON.parseObject(JSON.toJSONString(schema), MockFieldRule.class);
        List<MockFieldRule> children = new ArrayList<>(copy.getChildren());
        if (hasHidden) {
            children.removeIf(f -> f.getFieldName() != null && hiddenFields.contains(f.getFieldName()));
        }
        if (hasOverride) {
            for (MockFieldRule ov : overrideFields) {
                if (ov == null || ov.getFieldName() == null) {
                    continue;
                }
                children.removeIf(f -> ov.getFieldName().equals(f.getFieldName()));
                children.add(ov);
            }
        }
        copy.setChildren(children);
        return copy;
    }

    /**
     * 递归校验单个节点。actual 为已解析的 JSON 值（JSONObject/JSONArray/标量/null），
     * MISSING 哨兵表示字段不存在。
     */
    private static final Object MISSING = new Object();

    /** 校验期模板引用防环：记录当前递归栈上的模板 id（A→B→A 直接截断） */
    private static final ThreadLocal<java.util.Set<Integer>> VALIDATING_TEMPLATES =
            ThreadLocal.withInitial(java.util.HashSet::new);

    private static void validateNode(MockFieldRule rule, Object actual, String path,
                                     List<SchemaViolation> violations) {
        if (rule == null) {
            return;
        }
        if (actual == MISSING) {
            if (!Boolean.FALSE.equals(rule.getRequired())) {
                violations.add(new SchemaViolation(path, "缺少必填字段"));
            }
            return;
        }
        if (actual == null) {
            if (!Boolean.TRUE.equals(rule.getNullable())) {
                violations.add(new SchemaViolation(path, "值不应为 null"));
            }
            return;
        }
        String fieldType = rule.getFieldType() == null ? "STRING" : rule.getFieldType().trim().toUpperCase();
        switch (fieldType) {
            case "OBJECT" -> {
                if (!(actual instanceof JSONObject obj)) {
                    violations.add(new SchemaViolation(path, "期望对象，实际为 " + typeName(actual)));
                    return;
                }
                validateChildren(rule.getChildren(), obj, path, violations);
            }
            case "ARRAY" -> {
                if (!(actual instanceof JSONArray arr)) {
                    violations.add(new SchemaViolation(path, "期望数组，实际为 " + typeName(actual)));
                    return;
                }
                if (rule.getMinItems() != null && arr.size() < rule.getMinItems()) {
                    violations.add(new SchemaViolation(path, "数组元素个数 " + arr.size() + " 小于最小值 " + rule.getMinItems()));
                }
                if (rule.getMaxItems() != null && arr.size() > rule.getMaxItems()) {
                    violations.add(new SchemaViolation(path, "数组元素个数 " + arr.size() + " 大于最大值 " + rule.getMaxItems()));
                }
                if (rule.getChildren() != null && !rule.getChildren().isEmpty()) {
                    // 元素为对象：children 即元素对象的属性定义
                    for (int i = 0; i < arr.size(); i++) {
                        Object element = arr.get(i);
                        if (element instanceof JSONObject obj) {
                            validateChildren(rule.getChildren(), obj, path + "[" + i + "]", violations);
                        }
                    }
                }
            }
            case "TEMPLATE" -> {
                // 引用模板：解析模板 schema（含继承），应用引用处隐藏/覆盖后按对象校验
                if (rule.getTemplateId() == null) {
                    return;
                }
                if (!(actual instanceof JSONObject obj)) {
                    violations.add(new SchemaViolation(path, "期望对象（数据模板），实际为 " + typeName(actual)));
                    return;
                }
                // 防环：循环引用时跳过该校验分支（生成侧有同样的深度保护）
                java.util.Set<Integer> stack = VALIDATING_TEMPLATES.get();
                if (!stack.add(rule.getTemplateId())) {
                    log.warn("结构校验检测到模板循环引用，跳过: templateId={}", rule.getTemplateId());
                    return;
                }
                try {
                    MockFieldRule schema = DataTemplateFunctionExecutor.resolveSchemaById(rule.getTemplateId());
                    schema = applyOverrides(schema, rule.getExcludedFields(), rule.getChildren());
                    if (schema != null) {
                        validateChildren(schema.getChildren(), obj, path, violations);
                    }
                } finally {
                    stack.remove(rule.getTemplateId());
                    if (stack.isEmpty()) {
                        VALIDATING_TEMPLATES.remove();
                    }
                }
            }
            default -> validateScalar(rule, actual, path, fieldType, violations);
        }
    }

    private static void validateChildren(List<MockFieldRule> children, JSONObject obj, String path,
                                         List<SchemaViolation> violations) {
        if (children == null) {
            return;
        }
        for (MockFieldRule child : children) {
            if (child == null || child.getFieldName() == null) {
                continue;
            }
            Object value = obj.containsKey(child.getFieldName()) ? obj.get(child.getFieldName()) : MISSING;
            validateNode(child, value, path + "." + child.getFieldName(), violations);
        }
    }

    private static void validateScalar(MockFieldRule rule, Object actual, String path, String fieldType,
                                       List<SchemaViolation> violations) {
        boolean typeOk = switch (fieldType) {
            case "STRING" -> actual instanceof String;
            case "INT", "LONG" -> actual instanceof Number && isIntegral((Number) actual);
            case "FLOAT", "DOUBLE" -> actual instanceof Number;
            case "BOOLEAN" -> actual instanceof Boolean;
            default -> true;
        };
        if (!typeOk) {
            violations.add(new SchemaViolation(path, "期望类型 " + fieldType + "，实际为 " + typeName(actual)));
            return;
        }
        // 枚举值校验（仅 choice 规则）
        if ("choice".equalsIgnoreCase(rule.getRuleType()) && rule.getChoices() != null && !rule.getChoices().isBlank()) {
            boolean matched = Arrays.stream(rule.getChoices().split(","))
                    .map(String::trim).anyMatch(c -> c.equals(String.valueOf(actual)));
            if (!matched) {
                violations.add(new SchemaViolation(path, "值「" + actual + "」不在枚举范围 [" + rule.getChoices() + "]"));
            }
        }
    }

    private static boolean isIntegral(Number n) {
        double d = n.doubleValue();
        return d == Math.floor(d) && !Double.isInfinite(d);
    }

    private static String typeName(Object value) {
        if (value instanceof JSONObject) return "对象";
        if (value instanceof JSONArray) return "数组";
        if (value instanceof String) return "字符串";
        if (value instanceof Boolean) return "布尔";
        if (value instanceof Number) return "数字";
        return value == null ? "null" : value.getClass().getSimpleName();
    }
}
