package com.mokatest.platform.demos.util;

import com.mokatest.platform.demos.debug.TestExecutionContext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @Author JingLong
 * @Description 文字输入模板解析工具类
 * @Date 2025/8/30 11:00
 **/
public class TextParseUtil {


    /**
     * 替换期望值（支持多次替换）
     */
    public static String replaceExceptValue(TestExecutionContext context, String text) {
        if (text == null || context == null || context.getVariables() == null) {
            return text;
        }

        String result = text;
        String variableName;

        // 循环替换所有变量
        while ((variableName = extractVariable(result)) != null) {
            Object variableValue = context.getVariables().get(variableName);
            if (variableValue != null) {
                // 替换第一个匹配的变量
                result = result.replaceFirst(
                        Pattern.quote("{{" + variableName + "}}"),
                        Matcher.quoteReplacement(variableValue.toString())
                );
            } else {
                // 如果找不到变量值，保留原样，避免死循环
                break;
            }
        }

        return result;
    }

    /**
     * 提取双大括号语法里的内容（更精确的匹配）
     */
    private static String extractVariable(String text) {
        // 使用非贪婪匹配，匹配最内层的 {{}}
        // \\{\\{ 匹配 {{
        // ([^\\{\\}]+?) 非贪婪匹配一个或多个非大括号字符
        // \\}\\} 匹配 }}
        Pattern pattern = Pattern.compile("\\{\\{([^\\{\\}]+?)\\}\\}");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            // 返回第一个匹配项，并去除内部内容的前后空格
            return matcher.group(1).trim();
        }

        return null;
    }

}
