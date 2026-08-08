package com.mokatest.platform.demos.api.script;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 脚本执行上下文 - 暴露给 JavaScript 脚本的 API
 *
 * 脚本中通过 `context` 对象访问所有功能。
 * 支持标准 JavaScript 语法（ES2022），同时提供测试平台专属 API。
 */
@Data
public class ScriptContext {

    /** 变量存储（脚本可读写） */
    private Map<String, Object> variables = new HashMap<>();

    /** 控制台日志输出 */
    private List<String> consoleLogs = new ArrayList<>();

    /** 自定义断言结果 */
    private List<ScriptAssertion> scriptAssertions = new ArrayList<>();

    /** 请求信息（前置脚本可修改部分字段） */
    private ScriptRequest request;

    /** 响应信息（后置脚本只读） */
    private ScriptResponse response;

    /** 当前项目 id（用于按名称解析自定义函数 fn.名称(...)） */
    private Integer projectId;

    // ==================== 变量操作 ====================

    public Object getVariable(String name) {
        return variables.get(name);
    }

    public void setVariable(String name, Object value) {
        variables.put(name, value);
    }

    public void setVariables(Map<String, Object> vars) {
        if (vars != null) {
            variables.putAll(vars);
        }
    }

    // ==================== 日志输出 ====================

    public void log(Object message) {
        consoleLogs.add("[INFO] " + (message != null ? String.valueOf(message) : "null"));
    }

    public void error(Object message) {
        consoleLogs.add("[ERROR] " + (message != null ? String.valueOf(message) : "null"));
    }

    // ==================== 自定义断言 ====================

    public void assertCondition(boolean condition, String message) {
        scriptAssertions.add(new ScriptAssertion(condition, message));
    }

    // ==================== 请求信息 API（前置脚本用） ====================

    /** 获取当前请求 URL */
    public String getUrl() {
        return request != null ? request.getUrl() : null;
    }

    /** 获取当前请求方法 */
    public String getMethod() {
        return request != null ? request.getMethod() : null;
    }

    /** 获取当前请求体 */
    public String getBody() {
        return request != null ? request.getBody() : null;
    }

    /** 获取所有请求头 */
    public Map<String, String> getHeaders() {
        return request != null && request.getHeaders() != null ? new HashMap<>(request.getHeaders()) : new HashMap<>();
    }

    /** 获取指定请求头 */
    public String getHeader(String name) {
        if (request == null || request.getHeaders() == null) return null;
        return request.getHeaders().get(name);
    }

    /** 获取所有 Query 参数 */
    public Map<String, String> getQueries() {
        return request != null && request.getQuery() != null ? new HashMap<>(request.getQuery()) : new HashMap<>();
    }

    /** 获取指定 Query 参数 */
    public String getQuery(String name) {
        if (request == null || request.getQuery() == null) return null;
        return request.getQuery().get(name);
    }

    /** 获取所有 Cookie */
    public Map<String, String> getCookies() {
        return request != null && request.getCookies() != null ? new HashMap<>(request.getCookies()) : new HashMap<>();
    }

    /** 获取指定 Cookie */
    public String getCookie(String name) {
        if (request == null || request.getCookies() == null) return null;
        return request.getCookies().get(name);
    }

    /** 设置/添加请求头 */
    public void setHeader(String name, String value) {
        if (request == null) return;
        if (request.getHeaders() == null) {
            request.setHeaders(new HashMap<>());
        }
        request.getHeaders().put(name, value);
    }

    /** 设置/添加 Query 参数 */
    public void setQuery(String name, String value) {
        if (request == null) return;
        if (request.getQuery() == null) {
            request.setQuery(new HashMap<>());
        }
        request.getQuery().put(name, value);
    }

    /** 设置/添加 Cookie */
    public void setCookie(String name, String value) {
        if (request == null) return;
        if (request.getCookies() == null) {
            request.setCookies(new HashMap<>());
        }
        request.getCookies().put(name, value);
    }

    /** 设置请求体 */
    public void setBody(String body) {
        if (request != null) {
            request.setBody(body);
        }
    }

    /** 设置请求 URL */
    public void setUrl(String url) {
        if (request != null) {
            request.setUrl(url);
        }
    }

    // ==================== 响应信息 API（后置脚本用） ====================

    /** 获取响应体 */
    public String getResponseBody() {
        return response != null ? response.getBody() : null;
    }

    /** 获取响应状态码 */
    public int getResponseStatus() {
        return response != null ? response.getStatusCode() : 0;
    }

    /** 获取所有响应头 */
    public Map<String, String> getResponseHeaders() {
        return response != null && response.getHeaders() != null ? new HashMap<>(response.getHeaders()) : new HashMap<>();
    }

    /** 获取指定响应头 */
    public String getResponseHeader(String name) {
        if (response == null || response.getHeaders() == null) return null;
        return response.getHeaders().get(name);
    }

    /** 获取响应时间（毫秒） */
    public long getResponseTime() {
        return response != null ? response.getResponseTimeMs() : 0;
    }

    /** 获取响应状态消息 */
    public String getResponseStatusMessage() {
        return response != null ? response.getStatusMessage() : null;
    }

    // ==================== 工具函数 ====================

    public ScriptUtils getUtils() {
        return ScriptUtils.getInstance();
    }

    /**
     * 供 JS 中的 fn 代理调用：按「当前项目 + 函数名」执行自定义函数。
     *
     * JS 侧用法：fn.请求签名("a", "b") —— Proxy 把属性名和参数序列化后调用本方法。
     * 函数不存在或执行失败时抛异常（脚本按失败处理，错误信息可见）。
     *
     * @param name     函数名称（项目内唯一）
     * @param argsJson 参数 JSON 数组字符串
     * @return 函数执行结果的字符串形式
     */
    public String callCustomByName(String name, String argsJson) {
        if (projectId == null) {
            throw new IllegalStateException("当前脚本上下文无项目信息，无法按名称调用自定义函数");
        }
        List<Object> args;
        try {
            args = com.alibaba.fastjson.JSON.parseArray(argsJson, Object.class);
        } catch (Exception e) {
            throw new IllegalStateException("自定义函数参数解析失败: " + e.getMessage());
        }
        com.mokatest.platform.demos.util.CustomFunctionExecutor.RunResult result =
                com.mokatest.platform.demos.util.CustomFunctionExecutor.executeByName(
                        projectId, name, args != null ? args : new ArrayList<>());
        if (!result.isSuccess()) {
            throw new IllegalStateException(result.getErrorMessage());
        }
        return result.getValue();
    }

    // ==================== 断言内部类 ====================

    @Data
    public static class ScriptAssertion {
        private final boolean success;
        private final String message;
    }
}
