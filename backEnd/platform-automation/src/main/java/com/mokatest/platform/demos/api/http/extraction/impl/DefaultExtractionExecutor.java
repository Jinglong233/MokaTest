package com.mokatest.platform.demos.api.http.extraction.impl;

import com.mokatest.platform.demos.api.domain.apiEnum.ExtractType;
import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import com.mokatest.platform.demos.api.domain.requestModel.ApiExtraction;
import com.mokatest.platform.demos.api.http.extraction.ExtractionDetail;
import com.mokatest.platform.demos.api.http.extraction.ExtractionExecutor;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认数据提取执行器
 *
 * 功能说明：ExtractionExecutor 的默认实现，支持 5 种提取策略：
 * JSONPath、正则表达式、响应头、Cookie、状态码
 *
 * 设计说明：
 *   - 每个提取规则独立执行，互不影响，单个规则失败不会阻断其他规则
 *   - 提取失败时优先使用 ApiExtraction 作为备用值
 *   - 所有异常被捕获并记录 warn 日志，不会抛出影响主流程
 *
 * 提取策略详解：
 *   - ExtractType#JSON_PATH : 使用 Jayway JSONPath 库解析 JSON 响应体，支持复杂路径如 $.data.list[0].id
 *   - ExtractType#REGEX     : 使用 Java Pattern/Matcher 进行正则匹配，优先返回捕获组 group(1)，无捕获组返回 group(0)
 *   - ExtractType#HEADER    : 调用 TestHttpResponse 获取响应头，不区分大小写
 *   - ExtractType#COOKIE    : 调用 TestHttpResponse 获取 Cookie 值
 *   - ExtractType#STATUS_CODE: 直接返回 TestHttpResponse 的 Integer 值
 *
 * Spring 管理：通过 Component 注解注册为 Spring Bean，由
 * ApiRequestServiceImpl 注入使用
 *
 * @author JingLong
 * @see ExtractionExecutor
 * @see ExtractType
 * @since 2026-05-26
 */
@Slf4j
@Component
public class DefaultExtractionExecutor implements ExtractionExecutor {

    /**
     * 执行数据提取的主入口
     *
     * 遍历所有提取规则，逐个执行提取，将结果汇总为变量映射表
     *
     * @param extractions 提取配置列表，可为 null 或空
     * @param response    HTTP 响应结果
     * @return 变量名 -> 提取值的映射表
     */
    @Override
    public Map<String, Object> execute(List<ApiExtraction> extractions, TestHttpResponse response) {
        Map<String, Object> variables = new HashMap<>();
        if (extractions == null || extractions.isEmpty()) {
            return variables;
        }

        for (ApiExtraction extraction : extractions) {
            // 跳过禁用的提取规则
            if (extraction.isDisabled()) {
                continue;
            }
            try {
                Object value = extractSingle(extraction, response);
                if (value != null) {
                    // 提取成功，存入变量池
                    variables.put(extraction.getVariableName(), value);
                } else if (extraction.getDefaultValue() != null) {
                    // 提取失败但配置了默认值，使用默认值（支持 ${var} / {{var}} 引用已提取的变量）
                    variables.put(extraction.getVariableName(),
                            VariableReplacer.replace(extraction.getDefaultValue(), variables));
                }
            } catch (Exception e) {
                // 单个规则异常不影响其他规则，记录日志后使用默认值（如有）
                log.warn("提取失败: type={}, expression={}, variableName={}, error={}",
                        extraction.getType(), extraction.getExpression(),
                        extraction.getVariableName(), e.getMessage());
                if (extraction.getDefaultValue() != null) {
                    variables.put(extraction.getVariableName(),
                            VariableReplacer.replace(extraction.getDefaultValue(), variables));
                }
            }
        }
        return variables;
    }

    /**
     * 执行数据提取（带来源信息的详细版本）
     *
     * 在 {@link #execute} 的基础上，额外记录每条规则的来源信息和执行状态，
     * 便于在调试结果中展示提取规则的来源和详情
     *
     * @param extractions 提取配置列表
     * @param response    HTTP 响应结果
     * @param source      提取规则的统一来源标识
     * @return 提取详情列表
     */
    @Override
    public List<ExtractionDetail> executeWithDetails(List<ApiExtraction> extractions, TestHttpResponse response, RuleSource source) {
        List<ExtractionDetail> details = new ArrayList<>();
        if (extractions == null || extractions.isEmpty()) {
            return details;
        }
        // 累积已提取的变量，供后续规则的默认值做 ${var} / {{var}} 替换
        Map<String, Object> resolvedVars = new HashMap<>();

        for (ApiExtraction extraction : extractions) {
            // 跳过禁用的提取规则
            if (extraction.isDisabled()) {
                continue;
            }

            ExtractionDetail.ExtractionDetailBuilder detailBuilder = ExtractionDetail.builder()
                    .variableName(extraction.getVariableName())
                    .type(extraction.getType())
                    .expression(extraction.getExpression())
                    .source(source);

            try {
                Object value = extractSingle(extraction, response);
                if (value != null) {
                    detailBuilder.value(value).success(true);
                    resolvedVars.put(extraction.getVariableName(), value);
                } else if (extraction.getDefaultValue() != null) {
                    Object defaultValue = VariableReplacer.replace(extraction.getDefaultValue(), resolvedVars);
                    detailBuilder.value(defaultValue).success(true)
                            .errorMessage("提取失败，使用默认值");
                    resolvedVars.put(extraction.getVariableName(), defaultValue);
                } else {
                    detailBuilder.value(null).success(false)
                            .errorMessage("提取失败，未匹配到内容且无默认值");
                }
            } catch (Exception e) {
                log.warn("提取失败: type={}, expression={}, variableName={}, error={}",
                        extraction.getType(), extraction.getExpression(),
                        extraction.getVariableName(), e.getMessage());
                if (extraction.getDefaultValue() != null) {
                    Object defaultValue = VariableReplacer.replace(extraction.getDefaultValue(), resolvedVars);
                    detailBuilder.value(defaultValue).success(true)
                            .errorMessage("提取异常: " + e.getMessage() + "，使用默认值");
                    resolvedVars.put(extraction.getVariableName(), defaultValue);
                } else {
                    detailBuilder.value(null).success(false)
                            .errorMessage("提取异常: " + e.getMessage());
                }
            }
            details.add(detailBuilder.build());
        }
        return details;
    }

    /**
     * 单条提取规则的执行分发
     *
     * 根据 ApiExtraction 路由到对应的提取方法
     *
     * @param extraction 单条提取规则配置
     * @param response   HTTP 响应结果
     * @return 提取到的值，提取失败返回 null
     */
    private Object extractSingle(ApiExtraction extraction, TestHttpResponse response) {
        switch (extraction.getType()) {
            case JSON_PATH:
                return extractByJsonPath(extraction.getExpression(), response.getBodyAsString());
            case REGEX:
                return extractByRegex(extraction.getExpression(), response.getBodyAsString());
            case HEADER:
                return response.getResponseHeader(extraction.getExpression());
            case COOKIE:
                return response.getCookie(extraction.getExpression());
            case STATUS_CODE:
                return response.getStatusCode();
            default:
                log.warn("不支持的提取类型: {}", extraction.getType());
                return null;
        }
    }

    /**
     * 使用 JSONPath 从 JSON 字符串中提取数据
     *
     * 依赖 Jayway JSONPath 库，支持标准 JSONPath 语法
     *
     * @param jsonPath JSONPath 表达式，如 "$.data.token"
     * @param json     JSON 响应体字符串
     * @return 提取结果，可能为基本类型、Map、List 等；未找到匹配返回 null
     */
    private Object extractByJsonPath(String jsonPath, String json) {
        if (json == null || json.isEmpty() || jsonPath == null || jsonPath.isEmpty()) {
            return null;
        }
        try {
            return JsonPath.read(json, jsonPath);
        } catch (PathNotFoundException e) {
            log.warn("JSONPath 未找到匹配: path={}, error={}", jsonPath, e.getMessage());
            return null;
        }
    }

    /**
     * 使用正则表达式从文本中提取数据
     *
     * 匹配逻辑：
     *   - 编译正则表达式并创建 Matcher
     *   - 调用 find() 查找第一个匹配
     *   - 如果有捕获组（groupCount > 0），返回 group(1) 的内容
     *   - 如果无捕获组，返回 group(0) 即整个匹配内容
     *
     * @param regex 正则表达式，可包含捕获组
     * @param text  待匹配的文本（响应体）
     * @return 提取到的字符串；未匹配返回 null
     */
    private String extractByRegex(String regex, String text) {
        if (text == null || text.isEmpty() || regex == null || regex.isEmpty()) {
            return null;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            // 如果有捕获组，返回第一个捕获组的内容
            if (matcher.groupCount() > 0) {
                return matcher.group(1);
            }
            // 无捕获组返回整个匹配
            return matcher.group(0);
        }
        return null;
    }
}
