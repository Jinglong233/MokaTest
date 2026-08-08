package com.mokatest.platform.demos.api.domain.responseModel;

import com.mokatest.platform.demos.api.domain.apiEnum.RequestMethod;
import com.mokatest.platform.demos.api.domain.apiEnum.RequestStatus;
import com.mokatest.platform.demos.result.AssertResult;
import lombok.Data;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class HttpResponseResult {

    // ==================== 基本信息 ====================

    /**
     * 接口ID
     */
    private Integer apiId;

    /**
     * 接口名称
     */
    private String apiName;

    /**
     * HTTP 方法（GET、POST、PUT、DELETE、PATCH 等）
     */
    private RequestMethod method;

    /**
     * 请求地址（完整URL）
     */
    private String requestUrl;

    /**
     * 请求状态（SUCCESS、FAILED、TIMEOUT、ERROR）
     */
    private RequestStatus status;

    /**
     * 执行机器 IP/ID
     */
    private String machineIp;

    /**
     * 请求时间戳
     */
    private Date requestTime;


    // ==================== 请求相关 ====================

    /**
     * 请求头（JSON 格式）
     */
    private String requestHeaders;

    /**
     * 请求体
     */
    private String requestBody;

    /**
     * 请求 Cookie
     */
    private String requestCookies;


    // ==================== 响应相关 ====================

    /**
     * 响应状态码（200、404、500 等）
     */
    private Integer responseStatusCode;

    /**
     * 响应状态消息（OK、Not Found 等）
     */
    private String responseStatusMessage;

    /**
     * 响应头（JSON 格式）
     */
    private String responseHeaders;

    /**
     * 响应体
     */
    private String responseBody;

    /**
     * 响应 Cookie
     */
    private String responseCookies;

    /**
     * 响应内容大小（字节）
     */
    private Long responseSizeBytes;


    // ==================== 性能相关 ====================

    /**
     * 总耗时（毫秒）- 从发送到接收完成
     */
    private Long totalTimeMs;

    /**
     * DNS 解析耗时（毫秒）
     */
    private Long dnsTimeMs;

    /**
     * 连接耗时（毫秒）
     */
    private Long connectTimeMs;

    /**
     * TTFB（首字节时间，毫秒）
     */
    private Long ttfbMs;


    // ==================== 断言与脚本 ====================

    /**
     * 断言结果列表
     */
    private List<AssertResult> assertResults;

    /**
     * 变量追踪结果（键值对）
     */
    private Map<String, Object> variableTrack;

    /**
     * JS 脚本执行结果
     */
    private Object jsExecResult;


    // ==================== 错误信息 ====================

    /**
     * 错误信息（请求失败时）
     */
    private String errorMessage;

    /**
     * 异常堆栈（调试用）
     */
    private String errorStackTrace;

    /**
     * 提取结果列表（关联提取）
     */
    private Map<String,String> extractResults;

    /**
     * 控制台日志（JS 脚本输出）
     */
    private List<String> consoleLogs;


}