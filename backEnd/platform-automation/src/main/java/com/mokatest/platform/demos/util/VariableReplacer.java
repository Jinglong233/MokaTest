package com.mokatest.platform.demos.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量替换工具类（公共工具，API测试与UI自动化共用）
 *
 * 功能说明：
 *   - 支持 ${变量名} 和 {{变量名}} 两种变量占位符语法
 *   - 在字符串中查找变量占位符，并从变量上下文中获取实际值进行替换
 *   - 如果变量不存在，保持原样不替换
 *   - 使用 Matcher.quoteReplacement 处理替换值中的特殊字符（$、\）
 *
 * 使用场景：
 *   - API测试：请求URL、Header、Cookie、Query、Body中的变量替换
 *   - UI自动化：步骤参数中的变量替换（预留，暂未接入）
 *
 * 示例：
 *   Map<String, Object> vars = Map.of("userId", "123", "token", "abc");
 *   String result = VariableReplacer.replace("/api/user/${userId}", vars);
 *   // 结果："/api/user/123"
 *
 * @author JingLong
 * @since 2026-05-26
 */
public class VariableReplacer {

    /**
     * 匹配变量占位符的正则表达式
     *
     * 匹配规则：
     *   \$\{([^}]+)}  : 匹配 ${变量名} 格式
     *   \{\{([^}]+)}} : 匹配 {{变量名}} 格式
     *
     * group(1) 捕获 ${var} 中的变量名
     * group(2) 捕获 {{var}} 中的变量名
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}|\\{\\{([^}]+)}}");

    /**
     * 在文本中替换变量占位符
     *
     * 执行流程：
     * <ol>
     *   检查入参合法性（null 或空直接返回原值）
     *   使用正则匹配所有变量占位符
     *   从 variables 中查找变量名对应的值
     *   如果值存在，替换占位符；如果不存在，保持原样
     *   返回替换后的完整文本
     * </ol>
     *
     * @param text      原始文本，可能包含 ${var} 或 {{var}} 占位符
     * @param variables 变量映射表，key 为变量名，value 为变量值
     * @return 替换后的文本；如果入参非法则返回原值
     */
    public static String replace(String text, Map<String, Object> variables) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // 变量为空时仍要解析公共函数 {{__函数名(参数)__}}
        if (variables == null || variables.isEmpty()) {
            return FunctionParser.parse(text);
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            // group(1) 匹配 ${var} 格式中的变量名
            // group(2) 匹配 {{var}} 格式中的变量名
            String varName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            Object value = variables.get(varName);

            if (value != null) {
                // 使用 quoteReplacement 避免替换值中包含 $ 或 \ 导致异常
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
            }
            // 如果变量在上下文中不存在，保持原样不替换
        }

        matcher.appendTail(sb);
        // 变量替换完成后，再解析公共函数 {{__函数名(参数)__}}
        return FunctionParser.parse(sb.toString());
    }

    /**
     * 判断文本中是否包含变量占位符
     *
     * 用于在执行替换前快速判断是否需要变量替换，避免不必要的深拷贝操作
     *
     * @param text 原始文本
     * @return true 表示文本中包含 ${var} 或 {{var}} 格式的占位符；false 表示不包含或入参非法
     */
    public static boolean containsVariable(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return VARIABLE_PATTERN.matcher(text).find();
    }

    /**
     * 在文本中替换变量占位符，并返回发现的变量名列表
     *
     * 与 replace 的区别：额外返回文本中所有发现的变量名（无论是否成功替换）
     *
     * @param text      原始文本
     * @param variables 变量映射表
     * @return 包含替换结果和发现的变量名的结果对象
     */
    /**
     * 判断是否为公共函数调用格式（如 __MD5(abc)__）
     */
    private static boolean isFunctionCall(String varName) {
        if (varName == null || varName.length() < 5) {
            return false;
        }
        return varName.startsWith("__") && varName.endsWith("__")
                && varName.indexOf('(') > 2 && varName.indexOf(')') > varName.indexOf('(');
    }

    public static ReplaceResult replaceWithTrack(String text, Map<String, Object> variables) {
        List<String> foundVars = new ArrayList<>();
        List<String> unmatchedVars = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return new ReplaceResult(text, foundVars, unmatchedVars);
        }
        // 变量为空时仍要解析公共函数，避免 {{__MOCK()__}} 等函数无法替换
        if (variables == null || variables.isEmpty()) {
            return new ReplaceResult(FunctionParser.parse(text), foundVars, unmatchedVars);
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

            // 跳过公共函数调用（如 __MD5(abc)__），避免误报为未匹配变量
            if (isFunctionCall(varName)) {
                continue;
            }

            foundVars.add(varName);

            Object value = variables.get(varName);
            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
            } else {
                unmatchedVars.add(varName);
                // 变量不存在，保持原样
            }
        }

        matcher.appendTail(sb);
        // 变量替换完成后，再解析公共函数 {{__函数名(参数)__}}
        String afterFunctions = FunctionParser.parse(sb.toString());
        return new ReplaceResult(afterFunctions, foundVars, unmatchedVars);
    }

    /**
     * 替换结果（含追踪信息）
     */
    public static class ReplaceResult {
        private final String result;
        private final List<String> foundVariables;
        private final List<String> unmatchedVariables;

        public ReplaceResult(String result, List<String> foundVariables, List<String> unmatchedVariables) {
            this.result = result;
            this.foundVariables = foundVariables != null ? foundVariables : new ArrayList<>();
            this.unmatchedVariables = unmatchedVariables != null ? unmatchedVariables : new ArrayList<>();
        }

        public String getResult() {
            return result;
        }

        public List<String> getFoundVariables() {
            return foundVariables;
        }

        public List<String> getUnmatchedVariables() {
            return unmatchedVariables;
        }
    }
}
