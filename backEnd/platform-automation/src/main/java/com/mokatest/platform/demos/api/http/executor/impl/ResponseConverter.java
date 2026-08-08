package com.mokatest.platform.demos.api.http.executor.impl;

import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.api.http.model.VariableTrack;
import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ResponseConverter {

    /**
     * 执行机 IP 缓存。InetAddress.getLocalHost() 在 Windows 上可能很慢（DNS 解析超时），
     * 因此只在类加载时获取一次。
     */
    private static final String CACHED_EXECUTOR_IP;

    static {
        String ip;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ip = "127.0.0.1";
        }
        CACHED_EXECUTOR_IP = ip;
    }

    public static TestHttpResponse convert(ApiRequest apiRequest, Request request, Response response,
                                           byte[] rawBody,
                                           long startTime, long endTime, String uuid,
                                           Map<String, String> requestHeaders, VariableTrack variableTrack,
                                           List<String> scriptConsoleLog,
                                           List<com.mokatest.platform.demos.api.script.ScriptContext.ScriptAssertion> scriptAssertions) {
        Map<String, String> responseHeaders = new HashMap<>();
        Headers headers = response.headers();
        for (String name : headers.names()) {
            responseHeaders.put(name, headers.get(name));
        }

        Map<String, String> cookies = new HashMap<>();
        // HTTP 响应可能有多个 Set-Cookie 头，需要遍历所有值
        List<String> cookieHeaders = headers.values("Set-Cookie");
        for (String cookieHeader : cookieHeaders) {
            if (cookieHeader != null) {
                // Set-Cookie 格式：name=value; Path=/; HttpOnly
                // 只取第一个分号前的 name=value 部分
                String[] cookiePairs = cookieHeader.split(";");
                for (String pair : cookiePairs) {
                    String trimmed = pair.trim();
                    // 跳过 Path、Domain、Expires 等属性段（不含 = 或在标准属性列表中）
                    if (trimmed.isEmpty() || isCookieAttribute(trimmed)) {
                        continue;
                    }
                    String[] parts = trimmed.split("=", 2);
                    if (parts.length == 2) {
                        cookies.put(parts[0], parts[1]);
                    }
                }
            }
        }

        String finalUrl = response.request().url().toString();

        List<String> redirectHistory = null;
        if (response.priorResponse() != null) {
            redirectHistory = List.of(finalUrl);
        }

        // remoteIp 仅作展示用途，DNS 解析可能阻塞，因此捕获全部异常
        String remoteIp;
        try {
            remoteIp = InetAddress.getByName(response.request().url().host()).getHostAddress();
        } catch (Exception e) {
            remoteIp = response.request().url().host();
        }

        String executorIp = CACHED_EXECUTOR_IP;

        String status = response.isSuccessful() ? "success" : "failed";

        return TestHttpResponse.builder()
                .uuid(uuid)
                .status(status)
                .apiId(apiRequest.getId())
                .apiName(apiRequest.getApiName())
                .requestUrl(request.url().toString())
                .requestMethod(request.method())
                .requestHeaders(requestHeaders)
                .requestBody(getRequestBody(request))
                .rawRequestHeader(request.headers().toString())
                .statusCode(response.code())
                .responseStatusMsg(response.message())
                .responseHeaders(responseHeaders)
                .cookies(cookies)
                .rawBody(rawBody)
                .rawResponseHeader(headers.toString())
                .responseTimeMs(endTime - startTime)
                .responseBytes(rawBody != null ? rawBody.length : 0)
                .requestBytes(request.body() != null ? estimateRequestBodySize(request.body()) : 0)
                .finalUrl(finalUrl)
                .redirectHistory(redirectHistory)
                .remoteIp(remoteIp)
                .executorIp(executorIp)
                .requestStartTime(startTime)
                .variableTrack(variableTrack)
                .scriptConsoleLog(scriptConsoleLog)
                .scriptAssertions(scriptAssertions)
                .requestEndTime(endTime)
                .build();
    }

    /**
     * 判断是否为 Cookie 属性段（而非 name=value 的键值对）
     * Set-Cookie 中的属性如 Path=/、Domain=.example.com、HttpOnly、Secure 等不应被当作 Cookie 值解析
     */
    private static boolean isCookieAttribute(String segment) {
        String lower = segment.toLowerCase();
        return lower.startsWith("path=")
                || lower.startsWith("domain=")
                || lower.startsWith("expires=")
                || lower.startsWith("max-age=")
                || lower.startsWith("samesite=")
                || lower.equals("httponly")
                || lower.equals("secure")
                || lower.equals("partitioned");
    }

    private static byte[] getRequestBody(Request request) {
        if (request.body() == null) return null;
        try {
            okio.BufferedSink sink = new okio.Buffer();
            request.body().writeTo(sink);
            return sink.buffer().readByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private static long estimateRequestBodySize(okhttp3.RequestBody body) {
        try {
            okio.BufferedSink sink = new okio.Buffer();
            body.writeTo(sink);
            return sink.buffer().size();
        } catch (IOException e) {
            return 0;
        }
    }
}
