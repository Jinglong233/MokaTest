package com.mokatest.platform.demos.api.http.assertion.impl;

import com.mokatest.platform.demos.api.domain.apiEnum.ApiAssertType;
import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import com.mokatest.platform.demos.api.http.assertion.ApiAssertExecutor;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.enums.AssertRelationship;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.jayway.jsonpath.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 默认 API 断言执行器
 *
 * 功能说明：ApiAssertExecutor 的默认实现，支持 5 种断言目标类型：
 * 响应体、响应头、状态码、响应时间、自定义正则
 *
 * 设计说明：
 *   - 每条断言规则独立执行，单条失败不会阻断其他规则
 *   - 所有异常被捕获并记录 warn 日志，断言标记为失败但不中断流程
 *   - 响应体断言复用 Jayway JSONPath 库提取实际值
 *
 * 断言关系支持：
 *   - EQUALS / NOT_EQUALS：字符串精确比较
 *   - CONTAINS / NOT_CONTAINS：字符串包含关系
 *   - GT / LT / GE / LE：数值大小比较（自动尝试转数字）
 *   - REGULAR：正则表达式匹配
 *
 * @author JingLong
 * @see ApiAssertExecutor
 * @since 2026-05-26
 */
@Slf4j
@Component
public class DefaultApiAssertExecutor implements ApiAssertExecutor {

    /**
     * 执行断言验证的主入口
     *
     * 遍历所有断言规则，逐个执行验证，将结果汇总为列表
     *
     * @param assertions 断言配置列表，可为 null 或空
     * @param response   HTTP 响应结果
     * @return 断言结果列表，每条规则对应一个 AssertResult
     */
    @Override
    public List<AssertResult> execute(List<AssertParameter> assertions, TestHttpResponse response) {
        List<AssertResult> results = new ArrayList<>();
        if (assertions == null || assertions.isEmpty()) {
            return results;
        }

        for (AssertParameter assertion : assertions) {
            // 跳过禁用的断言规则
            if (assertion.isDisabled()) {
                continue;
            }
            try {
                AssertResult result = executeSingle(assertion, response);
                results.add(result);
            } catch (Exception e) {
                // 单条断言异常不影响其他断言，记录日志并标记为失败
                log.warn("断言执行异常: type={}, field={}, error={}",
                        assertion.getApiAssertType(), assertion.getField(), e.getMessage());
                results.add(new AssertResult(false,
                        "断言执行异常: " + e.getMessage(),
                        assertion.getApiAssertType() != null ? assertion.getApiAssertType().name() : "UNKNOWN",
                        assertion.getAssertRelationship() != null ? assertion.getAssertRelationship().name() : null,
                        null,
                        assertion.getSource()));
            }
        }
        return results;
    }

    /**
     * 单条断言规则的执行
     *
     * @param assertion 单条断言配置
     * @param response  HTTP 响应结果
     * @return 断言执行结果
     */
    private AssertResult executeSingle(AssertParameter assertion, TestHttpResponse response) {
        // 获取变量上下文（当前接口提取出的变量）
        Map<String, Object> extractedVariables = response.getExtractedVariables();

        // 获取实际值（字段中可能包含变量，需先替换）
        Object actualValueObj = extractActualValue(assertion, response, extractedVariables);
        String actualValue;
        if (actualValueObj == null) {
            actualValue = "";
        } else if (actualValueObj instanceof String) {
            actualValue = (String) actualValueObj;
        } else {
            // JSONPath 提取的复杂类型（Map/List）需要序列化为 JSON 字符串，避免 toString() 格式不符合预期
            actualValue = com.alibaba.fastjson.JSON.toJSONString(actualValueObj);
        }
        String expectedValue = assertion.getAssertValue() != null ? assertion.getAssertValue() : "";

        // 对预期值进行变量替换，支持 {{变量名}} 和 ${变量名} 语法
        if (extractedVariables != null && !extractedVariables.isEmpty()) {
            expectedValue = VariableReplacer.replace(expectedValue, extractedVariables);
        }

        // 执行比较
        boolean success = compare(actualValue, expectedValue, assertion.getAssertRelationship());

        // 构建提示信息（字段也做变量替换后显示）
        String displayField = assertion.getField();
        if (displayField != null && extractedVariables != null && !extractedVariables.isEmpty()) {
            displayField = VariableReplacer.replace(displayField, extractedVariables);
        }
        String tip = buildTip(assertion, displayField, actualValue, expectedValue, success);

        return new AssertResult(success, tip,
                assertion.getApiAssertType() != null ? assertion.getApiAssertType().name() : "UNKNOWN",
                assertion.getAssertRelationship() != null ? assertion.getAssertRelationship().name() : null,
                truncate(actualValue, 500),
                assertion.getSource());
    }

    /**
     * 根据断言类型提取实际值
     *
     * @param assertion           断言配置
     * @param response            HTTP 响应
     * @param extractedVariables  提取的变量上下文（用于字段中的变量替换）
     * @return 实际值对象
     */
    private Object extractActualValue(AssertParameter assertion, TestHttpResponse response, Map<String, Object> extractedVariables) {
        ApiAssertType type = assertion.getApiAssertType();
        String field = assertion.getField();

        // 对字段进行变量替换（支持 JSONPath 或 Header 名称中使用变量）
        if (field != null && extractedVariables != null && !extractedVariables.isEmpty()) {
            field = VariableReplacer.replace(field, extractedVariables);
        }

        if (type == null) {
            return null;
        }

        switch (type) {
            case BODY:
                // 从响应体中通过 JSONPath 提取字段值
                if (field == null || field.isEmpty()) {
                    return null;
                }
                String body = response.getBodyAsString();
                if (body == null || body.isEmpty()) {
                    return null;
                }
                try {
                    return JsonPath.read(body, field);
                } catch (Exception e) {
                    log.warn("JSONPath 提取失败: path={}, error={}", field, e.getMessage());
                    return null;
                }
            case HEADER:
                // 按名称获取响应头
                if (field == null || field.isEmpty()) {
                    return null;
                }
                return response.getResponseHeader(field);
            case STATUS_CODE:
                // HTTP 状态码
                return response.getStatusCode();
            case RESPONSE_TIME:
                // 响应耗时（毫秒）
                return response.getResponseTimeMs();
            case CUSTOM:
                // 自定义：如果字段不为空，用字段值（已做变量替换）作为实际值
                // 如果字段为空，返回完整响应体字符串用于正则匹配
                if (field != null && !field.isEmpty()) {
                    return field;
                }
                return response.getBodyAsString();
            default:
                log.warn("不支持的断言类型: {}", type);
                return null;
        }
    }

    /**
     * 根据断言关系执行比较
     *
     * @param actual       实际值
     * @param expected     预期值
     * @param relationship 断言关系
     * @return 比较是否通过
     */
    private boolean compare(String actual, String expected, AssertRelationship relationship) {
        if (relationship == null) {
            return false;
        }

        switch (relationship) {
            case EQUALS:
                return actual.equals(expected);
            case NOT_EQUALS:
                return !actual.equals(expected);
            case CONTAINS:
                return actual.contains(expected);
            case NOT_CONTAINS:
                return !actual.contains(expected);
            case REGULAR:
                return Pattern.matches(expected, actual);
            case GT:
                return compareNumber(actual, expected) > 0;
            case LT:
                return compareNumber(actual, expected) < 0;
            case GE:
                return compareNumber(actual, expected) >= 0;
            case LE:
                return compareNumber(actual, expected) <= 0;
            default:
                return false;
        }
    }

    /**
     * 数值比较辅助方法
     *
     * 尝试将两个字符串转为 Double 进行比较，转换失败时按字符串比较
     *
     * @param actual   实际值
     * @param expected 预期值
     * @return >0 表示 actual > expected，=0 表示相等，<0 表示 actual < expected
     */
    private int compareNumber(String actual, String expected) {
        try {
            double actualNum = Double.parseDouble(actual);
            double expectedNum = Double.parseDouble(expected);
            return Double.compare(actualNum, expectedNum);
        } catch (NumberFormatException e) {
            // 非数字时回退到字符串比较
            return actual.compareTo(expected);
        }
    }

    /**
     * 构建断言提示信息
     *
     * @param assertion    断言配置
     * @param displayField 显示用的字段（已做变量替换）
     * @param actual       实际值
     * @param expected     预期值
     * @param success      是否通过
     * @return 人类可读的提示文本
     */
    private String buildTip(AssertParameter assertion, String displayField, String actual, String expected, boolean success) {
        StringBuilder sb = new StringBuilder();

        if (displayField != null && !displayField.isEmpty()) {
            sb.append(displayField).append(" ");
        }

        sb.append(relationshipLabel(assertion.getAssertRelationship())).append(" ");
        sb.append(expected);

        if (success) {
            sb.append(" ✓ 通过（实际值: ").append(truncate(actual, 200)).append("）");
        } else {
            sb.append(" ✗ 失败（实际值: ").append(truncate(actual, 200)).append("）");
        }

        return sb.toString();
    }

    /**
     * 截断过长字符串，避免提示信息过于冗长
     */
    private String truncate(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, maxLength) + "...(已截断)";
    }

    /**
     * 断言关系中文标签
     */
    private String relationshipLabel(AssertRelationship relationship) {
        if (relationship == null) return "";
        switch (relationship) {
            case EQUALS: return "等于";
            case NOT_EQUALS: return "不等于";
            case CONTAINS: return "包含";
            case NOT_CONTAINS: return "不包含";
            case GT: return "大于";
            case LT: return "小于";
            case GE: return "大于等于";
            case LE: return "小于等于";
            case REGULAR: return "正则匹配";
            default: return relationship.name();
        }
    }
}
