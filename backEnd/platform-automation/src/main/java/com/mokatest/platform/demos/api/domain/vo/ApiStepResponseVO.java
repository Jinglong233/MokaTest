package com.mokatest.platform.demos.api.domain.vo;

import com.mokatest.platform.demos.api.http.extraction.ExtractionDetail;
import com.mokatest.platform.demos.result.AssertResult;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * API步骤执行响应VO
 * 用于序列化返回给前端，避免byte[]等类型序列化问题
 */
@Data
public class ApiStepResponseVO {

    private Integer apiId;
    private String apiName;

    // 执行标识
    private String status;

    // 请求信息
    private String requestUrl;
    private String requestMethod;
    private Map<String, String> requestHeaders;
    private String requestBody;

    // 响应信息
    private int statusCode;
    private String responseStatusMsg;
    private Map<String, String> responseHeaders;
    private Map<String, String> cookies;
    private String bodyAsString;
    private long responseBytes;

    // 性能信息
    private long responseTimeMs;
    private long requestBytes;

    // 测试结果
    private List<AssertResult> assertionResults;
    private Map<String, Object> extractedVariables;
    /**
     * 提取详情列表，包含每条提取规则的变量名、提取值、来源等信息
     */
    private List<ExtractionDetail> extractionDetails;
    private Object variableTrack;

    // 脚本执行
    private List<String> scriptConsoleLog;
    private List<?> scriptAssertions;

    // 异常信息
    private String errorMessage;
}
