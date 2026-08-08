package com.mokatest.platform.demos.util;

import com.mokatest.platform.demos.api.domain.requestModel.MockConfig;

/**
 * 参数级 Mock 配置生成器
 *
 * 将 {@link MockConfig} 转换为 {@link MockDataGenerator} 可识别的参数字符串，
 * 生成对应的 Mock 数据。</p>
 *
 * @author JingLong
 * @since 2026-06-19
 */
public class MockConfigGenerator {

    private static final String PLACEHOLDER = "{{__MOCK__}}";

    /**
     * 判断给定值是否为 Mock 占位符
     */
    public static boolean isMockPlaceholder(String value) {
        return PLACEHOLDER.equals(value);
    }

    /**
     * 根据 MockConfig 生成单个值
     *
     * @param config Mock 配置
     * @return 生成的字符串值；配置为空时返回原占位符
     */
    public static String generate(MockConfig config) {
        if (config == null || config.getType() == null) {
            return PLACEHOLDER;
        }

        String type = config.getType().trim().toLowerCase();
        StringBuilder params = new StringBuilder(type);

        switch (type) {
            case "name":
            case "company":
            case "address":
                appendLocale(params, config.getLocale());
                break;
            case "cname":
            case "ename":
                params.setLength(0);
                params.append("name, ").append("cname".equals(type) ? "zh" : "en");
                break;
            case "character":
                params.setLength(0);
                params.append("character");
                appendCaseType(params, config.getCaseType());
                appendLength(params, config.getLength());
                break;
            case "int":
                appendRange(params, config.getMin(), config.getMax(), 0, 100);
                break;
            case "long":
                appendRange(params, config.getMin(), config.getMax(), 0L, Long.MAX_VALUE);
                break;
            case "float":
            case "double":
                appendRange(params, config.getMin(), config.getMax(), 0, 100);
                if (config.getScale() != null) {
                    params.append(", ").append(config.getScale());
                }
                break;
            case "text":
                if (config.getLength() != null) {
                    params.append(", ").append(config.getLength());
                }
                break;
            case "date":
            case "datetime":
            case "time":
                if (config.getFormat() != null && !config.getFormat().isEmpty()) {
                    params.append(", ").append(config.getFormat());
                }
                break;
            case "choice":
                if (config.getChoices() != null && !config.getChoices().isEmpty()) {
                    params.append(", \"").append(config.getChoices()).append("\"");
                }
                break;
            case "fixed":
                return config.getFixedValue() != null ? config.getFixedValue() : "";
            case "template":
                return generateFromTemplate(config.getTemplateId());
            case "boolean":
            case "timestamp":
            case "bankcard":
            case "phone":
            case "email":
            case "uuid":
            case "idcard":
            case "id_card":
                // 无额外参数
                break;
            default:
                break;
        }

        String result = MockDataGenerator.generate(params.toString());
        // 如果 MockDataGenerator 把原表达式原样返回，说明是不支持的类型，兜底为空字符串
        if (result != null && result.startsWith("{{__MOCK(") && result.endsWith(")__}}")) {
            return "";
        }
        return result;
    }

    private static void appendLocale(StringBuilder params, String locale) {
        if (locale != null && !locale.isEmpty()) {
            params.append(", ").append(locale);
        }
    }

    private static void appendCaseType(StringBuilder params, String caseType) {
        if (caseType != null && !caseType.isEmpty()) {
            params.append(", ").append(caseType);
        } else {
            params.append(", lower");
        }
    }

    private static void appendLength(StringBuilder params, Integer length) {
        if (length != null && length > 0) {
            params.append(", ").append(length);
        } else {
            params.append(", 1");
        }
    }

    private static void appendRange(StringBuilder params, Number min, Number max,
                                    Number defaultMin, Number defaultMax) {
        Number finalMin = min != null ? min : defaultMin;
        Number finalMax = max != null ? max : defaultMax;
        params.append(", ").append(finalMin).append(", ").append(finalMax);
    }

    private static String generateFromTemplate(Integer templateId) {
        if (templateId == null) {
            return "";
        }
        String json = DataTemplateFunctionExecutor.generate(String.valueOf(templateId));
        return json != null ? json : "";
    }
}
