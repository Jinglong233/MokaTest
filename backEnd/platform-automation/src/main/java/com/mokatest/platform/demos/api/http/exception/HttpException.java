package com.mokatest.platform.demos.api.http.exception;

import java.io.IOException;

/**
 * HTTP 请求异常
 * 包含 HTTP 状态码和详细信息
 */
public class HttpException extends IOException {

    private final int statusCode;
    private final String responseBody;
    private final String requestUrl;
    private final String requestMethod;

    public HttpException(int statusCode) {
        this(statusCode, "HTTP " + statusCode + " error");
    }

    public HttpException(int statusCode, String message) {
        this(statusCode, message, null, null, null);
    }

    public HttpException(int statusCode, String message, String responseBody) {
        this(statusCode, message, responseBody, null, null);
    }

    public HttpException(int statusCode, String message, String responseBody, String requestUrl, String requestMethod) {
        super(buildMessage(statusCode, message, requestUrl, requestMethod, responseBody));
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.requestUrl = requestUrl;
        this.requestMethod = requestMethod;
    }

    public HttpException(int statusCode, Throwable cause) {
        this(statusCode, cause.getMessage(), null, null, null, cause);
    }

    public HttpException(int statusCode, String message, String responseBody, String requestUrl, String requestMethod, Throwable cause) {
        super(buildMessage(statusCode, message, requestUrl, requestMethod, responseBody), cause);
        this.statusCode = statusCode;
        this.responseBody = responseBody;
        this.requestUrl = requestUrl;
        this.requestMethod = requestMethod;
    }

    private static String buildMessage(int statusCode, String message, String requestUrl, String requestMethod, String responseBody) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP request failed: ");
        sb.append(statusCode);
        if (requestMethod != null && requestUrl != null) {
            sb.append(" [").append(requestMethod).append(" ").append(requestUrl).append("]");
        }
        if (message != null && !message.equals("HTTP " + statusCode + " error")) {
            sb.append(" - ").append(message);
        }
        if (responseBody != null && !responseBody.isEmpty()) {
            // 限制响应体长度，避免日志过大
            String trimmed = responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody;
            sb.append("\nResponse body: ").append(trimmed);
        }
        return sb.toString();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public boolean isClientError() {
        return statusCode >= 400 && statusCode < 500;
    }

    public boolean isServerError() {
        return statusCode >= 500 && statusCode < 600;
    }

    public boolean isNotFound() {
        return statusCode == 404;
    }

    public boolean isUnauthorized() {
        return statusCode == 401;
    }

    public boolean isForbidden() {
        return statusCode == 403;
    }

    @Override
    public String toString() {
        return String.format("HttpException{statusCode=%d, message='%s', requestUrl='%s', requestMethod='%s'}",
                statusCode, getMessage(), requestUrl, requestMethod);
    }
}
