package com.mokatest.platform.demos.http.model;

import com.mokatest.platform.demos.result.AssertResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import org.apache.hc.core5.http.ProtocolVersion;

import java.util.List;
import java.util.Map;

/**
 * API测试平台HTTP响应结果封装
 */
@Getter
@Builder
public class TestHttpResponse {

    private String apiId;
    private String apiName;

    // === 执行标识 ===
    private final String uuid;                   // 执行唯一ID
    private final String status;                 // success/failed/error

    // === 请求信息 ===
    private final String requestUrl;
    private final String requestMethod;
    @Singular("requestHeader")
    private final Map<String, String> requestHeaders;
    private final byte[] requestBody;
    private final String rawRequestHeader;       // 原始请求头

    // === 响应信息 ===
    private final int statusCode;
    private final String responseStatusMsg;      // HTTP状态消息
    @Singular("header")
    private final Map<String, String> headers;
    @Singular("cookie")
    private final Map<String, String> cookies;
    private final byte[] rawBody;
    private final String rawResponseHeader;      // 原始响应头

    // === 性能信息 ===
    private final long responseTimeMs;
    private final long responseBytes;
    private final long requestBytes;

    // === 网络信息 ===
    private final String finalUrl;
    private final List<String> redirectHistory;
    private final String remoteIp;
    private final String executorIp;             // 执行机IP

    // === 测试结果 ===
    private final List<AssertResult> assertionResults;
    private final Map<String, Object> extractedVariables;
    private final Map<String, Object> globalVariableTrack;

    // === 脚本执行 ===
    private final List<String> scriptConsoleLog;

    // === 异常信息 ===
    private final Exception exception;
    private final String errorMessage;

    // === 时间戳 ===
    private final long requestStartTime;
    private final long requestEndTime;

    // === 协议信息 ===
    private final ProtocolVersion protocolVersion;

    /**
     * 判断请求是否成功（HTTP状态码2xx）
     */
    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * 判断响应是否为JSON格式
     */
    public boolean isJson() {
        String contentType = getHeader("Content-Type");
        return contentType != null && contentType.contains("application/json");
    }

    /**
     * 获取响应头（不区分大小写）
     */
    public String getHeader(String name) {
        if (headers == null || name == null) return null;
        String value = headers.get(name);
        if (value != null) return value;
        return headers.get(name.toLowerCase());
    }

    /**
     * 获取Cookie值
     */
    public String getCookie(String name) {
        return cookies != null ? cookies.get(name) : null;
    }

    /**
     * 获取响应体字符串（默认UTF-8）
     */
    public String getBodyAsString() {
        if (rawBody == null) return null;
        return new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * 获取响应体字符串（指定字符集）
     */
    public String getBodyAsString(String charset) {
        if (rawBody == null) return null;
        try {
            return new String(rawBody, charset);
        } catch (java.io.UnsupportedEncodingException e) {
            return new String(rawBody, java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    /**
     * 获取Content-Type
     */
    public String getContentType() {
        return getHeader("Content-Type");
    }

    /**
     * 获取Content-Length
     */
    public long getContentLength() {
        String len = getHeader("Content-Length");
        if (len == null) return -1;
        try {
            return Long.parseLong(len);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 整体断言是否全部通过
     */
//    public boolean isAllAssertionsPassed() {
//        if (assertionResults == null) return true;
//        return assertionResults.stream().allMatch(AssertResult::isSucceed);
//    }
}