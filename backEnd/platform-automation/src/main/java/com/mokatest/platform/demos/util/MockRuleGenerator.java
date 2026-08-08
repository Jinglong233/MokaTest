package com.mokatest.platform.demos.util;

import com.alibaba.fastjson.JSON;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRuleParams;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Mock 字段规则生成器
 *
 * 根据可视化字段规则生成 Mock 响应体 JSON。支持根节点、可空/必填、嵌套对象/数组。
 *
 * @author JingLong
 * @since 2026-06-17
 */
public class MockRuleGenerator {

    private static final Random RANDOM = new Random();

    /** 模板嵌套引用最大深度，防止 A↔B 循环引用导致 StackOverflow */
    private static final int MAX_TEMPLATE_DEPTH = 5;
    private static final ThreadLocal<Integer> TEMPLATE_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
     * 根据字段规则根节点生成 JSON 字符串
     */
    public static String generate(MockFieldRule root) {
        if (root == null) {
            return "{}";
        }
        String fieldType = root.getFieldType();
        if (fieldType == null) {
            fieldType = "OBJECT";
        }
        if ("OBJECT".equalsIgnoreCase(fieldType)) {
            return JSON.toJSONString(generateObject(root));
        }
        if ("ARRAY".equalsIgnoreCase(fieldType)) {
            return JSON.toJSONString(generateArray(root));
        }
        return JSON.toJSONString(generateScalarValue(root));
    }

    /**
     * 生成对象字段对应的 Map
     */
    private static Map<String, Object> generateObject(MockFieldRule rule) {
        Map<String, Object> obj = new LinkedHashMap<>();
        if (rule.getChildren() == null) {
            return obj;
        }
        for (MockFieldRule child : rule.getChildren()) {
            if (child == null || child.getFieldName() == null) {
                continue;
            }
            if (Boolean.FALSE.equals(child.getRequired()) && RANDOM.nextDouble() < 0.3) {
                continue;
            }
            Object value = generateValue(child);
            obj.put(child.getFieldName(), value);
        }
        return obj;
    }

    /**
     * 递归生成单条字段规则对应的值
     */
    public static Object generateValue(MockFieldRule rule) {
        if (rule == null) {
            return "";
        }
        if (Boolean.TRUE.equals(rule.getNullable()) && RANDOM.nextDouble() < 0.2) {
            return null;
        }
        String fieldType = rule.getFieldType();
        if (fieldType == null) {
            return "";
        }
        fieldType = fieldType.trim().toUpperCase();

        if ("OBJECT".equals(fieldType)) {
            return generateObject(rule);
        }
        if ("ARRAY".equals(fieldType)) {
            return generateArray(rule);
        }
        return generateScalarValue(rule);
    }

    private static List<Object> generateArray(MockFieldRule rule) {
        List<Object> list = new ArrayList<>();
        int minItems = rule.getMinItems() != null ? rule.getMinItems() : 0;
        int maxItems = rule.getMaxItems() != null ? rule.getMaxItems() : 10;

        // 兼容旧数据：只有 arrayLength 时按固定长度兜底
        if (rule.getArrayLength() != null && rule.getMinItems() == null && rule.getMaxItems() == null) {
            minItems = rule.getArrayLength();
            maxItems = rule.getArrayLength();
        }

        if (minItems < 0) {
            minItems = 0;
        }
        if (maxItems < minItems) {
            maxItems = minItems;
        }
        if (maxItems > 1000) {
            maxItems = 1000;
        }

        int length = minItems;
        if (maxItems > minItems) {
            length += RANDOM.nextInt(maxItems - minItems + 1);
        }

        boolean uniqueItems = Boolean.TRUE.equals(rule.getUniqueItems());
        int maxRetries = 20;

        for (int i = 0; i < length; i++) {
            Object element = generateArrayElement(rule);
            if (uniqueItems) {
                int retries = 0;
                while (listContainsJson(list, element) && retries < maxRetries) {
                    element = generateArrayElement(rule);
                    retries++;
                }
            }
            list.add(element);
        }
        return list;
    }

    private static Object generateArrayElement(MockFieldRule arrayRule) {
        // Array 现在只作为容器，元素由 children 生成对象
        if (arrayRule.getChildren() != null && !arrayRule.getChildren().isEmpty()) {
            return generateObject(arrayRule);
        }
        return new LinkedHashMap<>();
    }

    private static boolean listContainsJson(List<Object> list, Object element) {
        String elementJson = JSON.toJSONString(element);
        for (Object item : list) {
            if (JSON.toJSONString(item).equals(elementJson)) {
                return true;
            }
        }
        return false;
    }

    private static Object generateScalarValue(MockFieldRule rule) {
        if (Boolean.TRUE.equals(rule.getIsConstant()) && rule.getFixedValue() != null) {
            return convertValueByFieldType(rule.getFixedValue(), rule.getFieldType());
        }
        // 正则表达式优先：配置 pattern 时按正则生成，忽略其他规则
        if (rule.getPattern() != null && !rule.getPattern().isBlank()
                && "STRING".equalsIgnoreCase(rule.getFieldType())) {
            String generated = generateByPattern(rule.getPattern());
            if (generated != null) {
                return generated;
            }
            // 正则非法/不支持时回退默认值，无默认值则空串
            return rule.getDefaultValue() != null ? rule.getDefaultValue() : "";
        }
        String ruleType = rule.getRuleType();
        String fieldType = rule.getFieldType();
        if (ruleType == null && !"TEMPLATE".equalsIgnoreCase(fieldType)) {
            return "";
        }
        if (ruleType != null) {
            ruleType = ruleType.trim().toLowerCase();
        }

        if ("fixed".equals(ruleType)) {
            return convertValueByFieldType(rule.getFixedValue(), fieldType);
        }

        if ("template".equals(ruleType) || "TEMPLATE".equalsIgnoreCase(fieldType)) {
            if (rule.getTemplateId() != null) {
                return generateFromTemplate(rule);
            }
            return new LinkedHashMap<>();
        }

        if ("null".equals(ruleType)) {
            return null;
        }

        if (ruleType == null) {
            return "";
        }

        StringBuilder mockParams = new StringBuilder(ruleType);
        switch (ruleType) {
            case "name":
            case "company":
            case "address":
                String locale = getLocale(rule);
                if (locale != null && !locale.isEmpty()) {
                    mockParams.append(", ").append(locale);
                }
                break;
            case "int":
                mockParams.append(", ").append(getMin(rule, 0));
                mockParams.append(", ").append(getMax(rule, 100));
                break;
            case "long":
                mockParams.append(", ").append(getMin(rule, 0L));
                mockParams.append(", ").append(getMax(rule, Long.MAX_VALUE));
                break;
            case "float":
            case "double":
                mockParams.append(", ").append(getMin(rule, 0));
                mockParams.append(", ").append(getMax(rule, 100));
                Integer scale = getScale(rule);
                if (scale != null) {
                    mockParams.append(", ").append(scale);
                }
                break;
            case "text":
                mockParams.append(", ").append(resolveTextLength(rule, 10));
                String charset = getCharset(rule);
                if (charset != null && !charset.isEmpty()) {
                    mockParams.append(", ").append(charset);
                }
                break;
            case "character":
                String caseType = rule.getCaseType();
                mockParams.append(", ").append(caseType != null && !caseType.isEmpty() ? caseType : "lower");
                mockParams.append(", ").append(resolveTextLength(rule, 1));
                break;
            case "timestamp":
                // 单位复用 format 字段：s / ms / ns，默认 ms
                String unit = getFormat(rule);
                if (unit != null && !unit.isEmpty()) {
                    mockParams.append(", ").append(unit);
                }
                break;
            case "datetime":
            case "time": {
                String dtFormat = getFormat(rule);
                if (dtFormat != null && !dtFormat.isEmpty()) {
                    mockParams.append(", ").append(dtFormat);
                }
                break;
            }
            case "date":
                String format = getFormat(rule);
                if ("timestamp".equalsIgnoreCase(format)) {
                    return System.currentTimeMillis();
                }
                if ("timestamp_s".equalsIgnoreCase(format)) {
                    return System.currentTimeMillis() / 1000;
                }
                if (format != null && !format.isEmpty()) {
                    mockParams.append(", ").append(format);
                }
                break;
            case "choice":
                String choices = getChoices(rule);
                if (choices != null && !choices.isEmpty()) {
                    mockParams.append(", \"").append(choices).append("\"");
                }
                break;
            default:
                break;
        }

        String value = MockDataGenerator.generate(mockParams.toString());
        if (value != null && value.startsWith("{{__MOCK(") && rule.getDefaultValue() != null) {
            value = rule.getDefaultValue();
        }
        return convertValueByFieldType(value, rule.getFieldType());
    }

    /**
     * 按正则表达式生成匹配字符串（rgxgen）。正则不合法或不支持时返回 null。
     */
    private static String generateByPattern(String pattern) {
        try {
            return com.github.curiousoddman.rgxgen.RgxGen.parse(pattern).generate(RANDOM);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 text/character 生成长度：minLength/maxLength 有效时在区间内随机，否则用 length 字段兜底
     */
    private static int resolveTextLength(MockFieldRule rule, int defaultLength) {
        Integer min = rule.getMinLength();
        Integer max = rule.getMaxLength();
        if (min != null && max != null && max > 0 && max >= min && min >= 0) {
            // 未配置（0/1000 之类的模板默认值）时视为未设置，回退 length
            if (min == 0 && max == 1000) {
                Integer length = getLength(rule);
                return length != null && length > 0 ? length : defaultLength;
            }
            return max > min ? min + RANDOM.nextInt(max - min + 1) : min;
        }
        Integer length = getLength(rule);
        return length != null && length > 0 ? length : defaultLength;
    }

    private static Object generateFromTemplate(MockFieldRule rule) {
        Integer templateId = rule.getTemplateId();
        if (templateId == null) {
            return new LinkedHashMap<>();
        }
        int depth = TEMPLATE_DEPTH.get();
        if (depth >= MAX_TEMPLATE_DEPTH) {
            // 超过嵌套深度上限（疑似循环引用），返回空对象兜底
            return new LinkedHashMap<>();
        }
        TEMPLATE_DEPTH.set(depth + 1);
        try {
            String json = DataTemplateFunctionExecutor.generate(String.valueOf(templateId));
            Object parsed = JSON.parse(json);
            // 引用处隐藏/覆盖（仅对象为 Map 时适用）
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                if (rule.getExcludedFields() != null) {
                    rule.getExcludedFields().forEach(map::remove);
                }
                // TEMPLATE 节点的 children 作为覆盖字段：同名以本地规则生成值为准
                if (rule.getChildren() != null) {
                    for (MockFieldRule child : rule.getChildren()) {
                        if (child != null && child.getFieldName() != null) {
                            map.put(child.getFieldName(), generateValue(child));
                        }
                    }
                }
            }
            return parsed;
        } catch (Exception e) {
            return new LinkedHashMap<>();
        } finally {
            if (depth == 0) {
                TEMPLATE_DEPTH.remove();
            } else {
                TEMPLATE_DEPTH.set(depth);
            }
        }
    }

    private static Object convertValueByFieldType(String value, String fieldType) {
        if (value == null || fieldType == null) {
            return value;
        }
        try {
            switch (fieldType.trim().toUpperCase()) {
                case "INT":
                case "INTEGER":
                    return Integer.parseInt(value);
                case "LONG":
                    return Long.parseLong(value);
                case "FLOAT":
                    return Float.parseFloat(value);
                case "DOUBLE":
                    return Double.parseDouble(value);
                case "BOOLEAN":
                    return Boolean.parseBoolean(value);
                default:
                    return value;
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }

    // ---------- 参数读取：优先顶层字段，回退旧 params ----------

    private static String getLocale(MockFieldRule rule) {
        if (rule.getLocale() != null) {
            return rule.getLocale();
        }
        MockFieldRuleParams params = rule.getParams();
        return params != null ? params.getLocale() : null;
    }

    private static Number getMin(MockFieldRule rule, Number defaultValue) {
        if (rule.getMin() != null) {
            return rule.getMin();
        }
        MockFieldRuleParams params = rule.getParams();
        return params != null && params.getMin() != null ? params.getMin() : defaultValue;
    }

    private static Number getMax(MockFieldRule rule, Number defaultValue) {
        if (rule.getMax() != null) {
            return rule.getMax();
        }
        MockFieldRuleParams params = rule.getParams();
        return params != null && params.getMax() != null ? params.getMax() : defaultValue;
    }

    private static Integer getScale(MockFieldRule rule) {
        if (rule.getScale() != null) {
            return rule.getScale();
        }
        MockFieldRuleParams params = rule.getParams();
        return params != null ? params.getScale() : null;
    }

    private static Integer getLength(MockFieldRule rule) {
        if (rule.getLength() != null) {
            return rule.getLength();
        }
        MockFieldRuleParams params = rule.getParams();
        return params != null ? params.getLength() : null;
    }

    private static String getCharset(MockFieldRule rule) {
        return rule.getCharset();
    }

    private static String getFormat(MockFieldRule rule) {
        if (rule.getFormat() != null) {
            return rule.getFormat();
        }
        MockFieldRuleParams params = rule.getParams();
        return params != null ? params.getFormat() : null;
    }

    private static String getChoices(MockFieldRule rule) {
        if (rule.getChoices() != null) {
            return rule.getChoices();
        }
        MockFieldRuleParams params = rule.getParams();
        return params != null ? params.getChoices() : null;
    }
}
