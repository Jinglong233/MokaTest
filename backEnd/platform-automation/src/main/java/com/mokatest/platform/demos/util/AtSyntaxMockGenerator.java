package com.mokatest.platform.demos.util;

/**
 * @ 语法 Mock 数据生成器
 *
 * 为 {@link FunctionParser} 提供 MeterSphere 风格的 {@code @function(args)} Mock
 * 数据生成能力，例如 {@code @phone()}、{@code @character('lower', 8)}、
 * {@code @integer(1, 100)}。</p>
 *
 * @author JingLong
 * @since 2026-06-19
 */
public class AtSyntaxMockGenerator {

    /**
     * 根据 @ 函数名和参数生成 Mock 数据
     *
     * @param functionName 函数名，如 phone、character、integer
     * @param rawArgs      原始参数字符串，可能包含引号，如 {@code 'lower', 8}
     * @return 生成的数据
     */
    public static String generate(String functionName, String rawArgs) {
        if (functionName == null || functionName.isEmpty()) {
            return "";
        }
        String name = functionName.trim();
        String args = normalizeArgs(rawArgs);

        switch (name.toLowerCase()) {
            case "integer":
                name = "int";
                break;
            case "fixed":
                return stripQuotes(rawArgs);
            case "template":
                return generateFromTemplate(stripQuotes(rawArgs));
            case "custom":
                // @custom(id, args...) 调用用户自定义 JS 函数
                return CustomFunctionExecutor.generate(rawArgs);
            case "md5":
            case "sha256":
            case "sha512":
                // @md5(text) 等加密函数代理到内置函数实现
                return FunctionParser.executeBuiltin(name.toUpperCase(), stripQuotes(rawArgs));
            case "phone":
            case "email":
            case "uuid":
            case "idcard":
            case "id_card":
            case "bankcard":
            case "cname":
            case "ename":
            case "boolean":
                // 无参函数，忽略 args
                return MockDataGenerator.generate(name);
            default:
                // 带参函数：name/company/address/character/text/date/datetime/time/
                // choice/int/long/float/double/timestamp
                break;
        }
        return MockDataGenerator.generate(buildParams(name, args));
    }

    private static String buildParams(String type, String args) {
        if (args == null || args.isEmpty()) {
            return type;
        }
        return type + ", " + args;
    }

    /**
     * 去掉参数首尾的单/双引号，并清理多余空格
     */
    private static String normalizeArgs(String rawArgs) {
        if (rawArgs == null) {
            return "";
        }
        String args = rawArgs.trim();
        // 去掉整体首尾引号（如 'lower', 8 整体被引号包裹时）
        if (args.length() >= 2
                && ((args.startsWith("'") && args.endsWith("'"))
                || (args.startsWith("\"") && args.endsWith("\"")))) {
            args = args.substring(1, args.length() - 1);
        }
        // 逐个参数去引号
        String[] parts = args.split(",\\s*");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(stripQuotes(parts[i]));
        }
        return sb.toString();
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String str = value.trim();
        if (str.length() >= 2
                && ((str.startsWith("'") && str.endsWith("'"))
                || (str.startsWith("\"") && str.endsWith("\"")))) {
            return str.substring(1, str.length() - 1);
        }
        return str;
    }

    private static String generateFromTemplate(String rawId) {
        if (rawId == null || rawId.isEmpty()) {
            return "";
        }
        try {
            return DataTemplateFunctionExecutor.generate(rawId);
        } catch (Exception e) {
            return "";
        }
    }
}
