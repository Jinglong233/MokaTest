package com.mokatest.platform.demos.api.http.executor.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.BodyMode;
import com.mokatest.platform.demos.api.domain.apiEnum.RequestMethod;
import com.mokatest.platform.demos.api.domain.apiEnum.AuthType;
import com.mokatest.platform.demos.api.domain.requestModel.AuthConfig;
import com.mokatest.platform.demos.api.domain.requestModel.Body;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import com.mokatest.platform.demos.api.domain.requestModel.MockResponse;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.api.domain.requestModel.ResponseSchema;
import com.mokatest.platform.demos.api.domain.apiEnum.ParameterType;
import com.mokatest.platform.demos.api.service.FileUploadService;
import com.mokatest.platform.demos.util.MockConfigGenerator;
import com.mokatest.platform.demos.util.RequestParameterJsonUtils;
import com.mokatest.platform.demos.api.http.executor.RequestExecutor;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.api.http.model.VariableTrack;
import com.mokatest.platform.demos.api.script.ScriptContext;
import com.mokatest.platform.demos.api.script.ScriptExecutor;
import com.mokatest.platform.demos.api.script.ScriptRequest;
import com.mokatest.platform.demos.api.script.ScriptResponse;
import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.api.http.validation.SchemaValidator;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.util.FunctionParser;
import com.mokatest.platform.demos.util.MockRuleGenerator;
import com.mokatest.platform.demos.util.VariableReplacer;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.Buffer;
import okio.BufferedSink;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
/**
 * HTTP 请求执行器抽象基类
 *
 * 功能说明：定义 HTTP 请求执行的通用流程模板，所有具体方法（GET、POST 等）的执行器都继承此类
 *
 * 核心职责：
 *   - 变量替换：在请求执行前，将 URL、Header、Cookie、Query、Body 中的 ${var} / {{var}} 占位符替换为环境变量实际值
 *   - URL 构建：拼接环境基础 URL 与请求路径，附加 Query 参数
 *   - 请求头处理：添加自定义请求头，合并 OkHttp 自动生成的请求头
 *   - Cookie 处理：将配置中的 Cookie 拼接为 Cookie 请求头
 *   - 请求体构建：根据 BodyMode（FORM_DATA、JSON、XML 等）构建对应的 RequestBody
 *   - 响应转换：将 OkHttp 的 Response 转换为平台内部的 TestHttpResponse
 *
 * 执行流程（模板方法模式）：
 *   - resolveRequestVariables(ApiRequest) - 变量替换（深拷贝避免污染原始对象）
 *   - buildUrl(ApiRequest) - URL 拼接与 Query 参数编码
 *   - addHeaders(Request.Builder, ApiRequest) - 添加自定义请求头
 *   - addCookies(Request.Builder, ApiRequest) - 添加 Cookie
 *   - buildRequestBody(ApiRequest) - 构建请求体
 *   - configureRequestMethod(Request.Builder, RequestBody) - 子类实现请求方法（GET/POST/PUT 等）
 *   - 执行 HTTP 请求 → 转换响应
 *
 * 变量替换设计：
 *   - 使用 FastJSON 序列化/反序列化实现深拷贝，保证原始 ApiRequest 对象不被修改
 *   - 变量来源：RequestExecuteInfo（环境变量）
 *   - 替换范围：URL 路径、Header 值、Cookie 值、Query 值、Body 内容（JSON/XML/FormData）
 *
 * 线程安全：
 *   - 使用 ThreadLocal 存储网络拦截器捕获的完整请求头，避免多线程冲突
 *
 * @author JingLong
 * @see RequestExecutor
 * @since 2026-05-26
 */
public abstract class AbstractRequestExecutor implements RequestExecutor {

    /**
     * 临时文件上传服务，用于 FILE 类型参数读取文件
     */
    protected final FileUploadService fileUploadService;

    /**
     * OkHttp 客户端实例，由子类通过构造方法传入
     *
     * 该客户端已配置超时、重试、拦截器等，所有请求共用同一个实例
     */
    protected final OkHttpClient okHttpClient;

    /**
     * ThreadLocal 存储网络拦截器捕获的完整请求头
     *
     * 用途：OkHttp 的网络拦截器会在请求发出前自动添加一些请求头（如 Content-Length、Host 等），
     * 这些请求头在应用拦截器层不可见。通过 ThreadLocal 将网络层的完整请求头传递到响应构建阶段，
     * 用于在调试结果中展示完整的请求头信息
     *
     * 线程安全：每个线程独立存储，请求完成后会调用 ThreadLocal 清理
     */
    public static final ThreadLocal<Map<String, String>> FULL_HEADERS = new ThreadLocal<>();

    protected AbstractRequestExecutor(OkHttpClient okHttpClient, FileUploadService fileUploadService) {
        this.okHttpClient = okHttpClient;
        this.fileUploadService = fileUploadService;
    }

    @Override
    public TestHttpResponse execute(ApiRequest request) {
        // 数据边界：为数据模板等执行期消费提供项目上下文（IDOR 防护）。
        // HTTP 请求线程已由 TeamContextInterceptor 设置，此处仅在上下文缺失时
        // （计划执行/调试会话等异步线程）按 api_request.projectId 补齐，结束后清理。
        Integer prevProjectId = ProjectContextHolder.getProjectId();
        boolean setHere = prevProjectId == null && request.getProjectId() != null;
        if (setHere) {
            ProjectContextHolder.setProjectId(request.getProjectId());
        }
        try {
            return doExecute(request);
        } finally {
            if (setHere) {
                ProjectContextHolder.clear();
            }
        }
    }

    private TestHttpResponse doExecute(ApiRequest request) {
        long startTime = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        List<String> consoleLogs = new ArrayList<>();
        List<ScriptContext.ScriptAssertion> scriptAssertions = new ArrayList<>();

        log.info("[MockConfig] execute 入口 query={}, headers={}",
                request.getQuery(), request.getRequestHeader());

        // ===== 1. 前置脚本（在变量替换之前执行，脚本设置的变量可被替换到请求中） =====
        Map<String, Object> variables = buildVariableContext(request);
        if (request.getPreScript() != null && !request.getPreScript().isEmpty()) {
            try {
                ScriptRequest scriptRequest = buildScriptRequest(request);
                ScriptExecutor.ScriptResult preResult = ScriptExecutor.executePreScripts(
                        request.getPreScript(), variables, scriptRequest, request.getProjectId());
                consoleLogs.addAll(preResult.getConsoleLogs());
                scriptAssertions.addAll(preResult.getScriptAssertions());
                if (preResult.isSuccess()) {
                    // 同步脚本对请求的直接修改（headers/query/cookies/body/url/method）
                    syncScriptRequestToApiRequest(request, scriptRequest);
                    // 同步脚本设置的变量，供后续变量替换使用
                    variables.putAll(preResult.getVariables());
                    syncVariablesToRequest(request, variables);
                }
            } catch (Exception e) {
                // 前置脚本异常不应阻断请求执行，记录日志后继续
                log.warn("[脚本执行] 前置脚本执行异常，继续发送请求: {}", e.getMessage());
                consoleLogs.add("[ERROR] 前置脚本执行异常: " + e.getMessage());
            }
        }

        // ===== 2. 变量替换 =====
        // 鉴权配置在变量替换前注入为等效 Header/Query 参数，token 等字段里的 ${var} 可随统一管线解析
        applyAuthConfig(request);
        VariableResolveResult resolveResult = resolveRequestVariablesWithTrack(request);
        ApiRequest resolvedRequest = resolveResult.resolvedRequest;
        VariableTrack variableTrack = resolveResult.variableTrack;

        // ===== [NEW] Mock 响应 =====
        // 如果当前接口启用了 Mock，直接返回模拟响应，不发送真实 HTTP 请求
        MockResponse mockResponse = resolvedRequest.getMockResponse();
        log.info("[Mock] 接口ID={}, mockResponse={}, enabled={}", request.getId(), mockResponse,
                mockResponse != null && mockResponse.isEnabled());
        if (mockResponse != null && mockResponse.isEnabled()) {
            return buildMockResponse(resolvedRequest, startTime, uuid, variableTrack, consoleLogs, scriptAssertions);
        }

        String url = buildUrl(resolvedRequest);

        Request.Builder requestBuilder = null;
        try {
            requestBuilder = new Request.Builder().url(url);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("URL参数错误");
        }

        Map<String, String> customHeaders = addHeaders(requestBuilder, resolvedRequest);
        addCookies(requestBuilder, resolvedRequest);

        RequestBody requestBody = buildRequestBody(resolvedRequest);
        configureRequestMethod(requestBuilder, requestBody);

        Request okRequest = requestBuilder.build();

        Map<String, String> fullRequestHeaders = buildFullRequestHeaders(okRequest, customHeaders);

        // ===== 3. 发送请求并执行后置脚本 =====
        Response response = null;
        try {
            response = okHttpClient.newCall(okRequest).execute();
            long endTime = System.currentTimeMillis();

            // 获取网络拦截器中保存的完整请求头
            Map<String, String> networkHeaders = FULL_HEADERS.get();
            if (networkHeaders != null) {
                fullRequestHeaders.putAll(networkHeaders);
            }

            // 读取响应信息（只能读一次，保存 bytes 供后续使用）
            byte[] rawBodyBytes = null;
            String responseBodyStr = "";
            Map<String, String> responseHeaders = new HashMap<>();
            Headers headers = response.headers();
            for (String name : headers.names()) {
                responseHeaders.put(name, headers.get(name));
            }
            ResponseBody respBody = response.body();
            if (respBody != null) {
                try {
                    rawBodyBytes = respBody.bytes();
                    responseBodyStr = new String(rawBodyBytes, java.nio.charset.StandardCharsets.UTF_8);
                } catch (IOException ignored) {}
            }

            // 执行后置脚本
            if (request.getPostScript() != null && !request.getPostScript().isEmpty()) {
                try {
                    ScriptResponse scriptResponse = ScriptResponse.builder()
                            .statusCode(response.code())
                            .statusMessage(response.message())
                            .headers(responseHeaders)
                            .body(responseBodyStr)
                            .responseTimeMs(endTime - startTime)
                            .build();
                    ScriptExecutor.ScriptResult postResult = ScriptExecutor.executePostScripts(
                            request.getPostScript(), variables, scriptResponse, request.getProjectId());
                    consoleLogs.addAll(postResult.getConsoleLogs());
                    scriptAssertions.addAll(postResult.getScriptAssertions());
                } catch (Exception e) {
                    // 后置脚本异常不应阻断响应返回，记录日志后继续
                    log.warn("[脚本执行] 后置脚本执行异常，不影响响应结果: {}", e.getMessage());
                    consoleLogs.add("[ERROR] 后置脚本执行异常: " + e.getMessage());
                }
            }

            return ResponseConverter.convert(request, okRequest, response, rawBodyBytes, startTime, endTime, uuid,
                    fullRequestHeaders, variableTrack, consoleLogs, scriptAssertions);
        } catch (IOException e) {
            long endTime = System.currentTimeMillis();
            return buildErrorResponse(request, url, startTime, endTime, uuid, e,
                    fullRequestHeaders, requestBody, variableTrack, consoleLogs, scriptAssertions);
        } finally {
            FULL_HEADERS.remove();
            if (response != null) {
                response.close();
            }
        }
    }

    /**
     * 变量解析结果包装类
     */
    private static class VariableResolveResult {
        final ApiRequest resolvedRequest;
        final VariableTrack variableTrack;

        VariableResolveResult(ApiRequest resolvedRequest, VariableTrack variableTrack) {
            this.resolvedRequest = resolvedRequest;
            this.variableTrack = variableTrack;
        }
    }

    /**
     * 解析请求中的变量，返回变量替换后的请求副本和追踪信息
     *
     * 核心设计：使用 FastJSON 深拷贝避免修改原始 ApiRequest 对象
     * 同时记录每次替换前后的变化，供变量追踪展示
     *
     * @param request 原始 API 请求配置对象
     * @return 包含替换后的请求对象和追踪信息的结果
     * @see VariableReplacer#replaceWithTrack(String, Map)
     */
    private VariableResolveResult resolveRequestVariablesWithTrack(ApiRequest request) {
        // 1. 构建变量上下文
        Map<String, Object> variables = buildVariableContext(request);

        VariableTrack.VariableTrackBuilder trackBuilder = VariableTrack.builder();

        // 记录变量来源
        if (request.getEnvInfo() != null && request.getEnvInfo().getEnvVariables() != null) {
            Map<String, String> sources = new HashMap<>();
            request.getEnvInfo().getEnvVariables().forEach((k, v) ->
                sources.put(k, v != null ? v.toString() : null)
            );
            trackBuilder.variableSources(sources);
        }

        // 如果没有变量且请求中不包含公共函数、Body 也未绑定结构定义，直接返回（无需深拷贝）
        if (variables.isEmpty() && !containsAnyFunction(request) && !hasBoundJsonBody(request)) {
            return new VariableResolveResult(request, trackBuilder.build());
        }

        // 2. 深拷贝
        String jsonString = JSON.toJSONString(request);
        ApiRequest resolved = JSON.parseObject(jsonString, ApiRequest.class);

        // 2.1 JSON Body 绑定结构定义：先生成请求体，再走统一变量替换——
        // 保证生成内容里的 ${var}/函数表达式也被解析，且调试展示的请求体与实际发送一致
        if (resolved.getBody() != null) {
            String boundJson = resolveBoundJsonBody(resolved.getBody());
            if (boundJson != null) {
                resolved.getBody().setJson(boundJson);
            }
        }

        List<String> allUnmatched = new ArrayList<>();

        // 3. 替换 URL 路径（仅当包含变量占位符或函数表达式时才记录）
        if (resolved.getRequestPath() != null
                && (VariableReplacer.containsVariable(resolved.getRequestPath())
                    || FunctionParser.containsFunction(resolved.getRequestPath()))) {
            VariableReplacer.ReplaceResult rr = VariableReplacer.replaceWithTrack(resolved.getRequestPath(), variables);
            resolved.setRequestPath(rr.getResult());
            trackBuilder.urlReplace(VariableTrack.ReplaceRecord.builder()
                .name("请求路径")
                .before(request.getRequestPath())
                .after(rr.getResult())
                .variables(rr.getFoundVariables())
                .unmatchedVariables(rr.getUnmatchedVariables())
                .build());
            allUnmatched.addAll(rr.getUnmatchedVariables());
        }

        // 4. 替换请求头
        List<VariableTrack.ReplaceRecord> headerRecords = replaceInParametersWithTrack(
            resolved.getRequestHeader(), variables, request.getRequestHeader());
        trackBuilder.headerReplaces(headerRecords);
        headerRecords.forEach(r -> allUnmatched.addAll(r.getUnmatchedVariables()));

        // 5. 替换 Cookie
        List<VariableTrack.ReplaceRecord> cookieRecords = replaceInParametersWithTrack(
            resolved.getCookies(), variables, request.getCookies());
        trackBuilder.cookieReplaces(cookieRecords);
        cookieRecords.forEach(r -> allUnmatched.addAll(r.getUnmatchedVariables()));

        // 6. 替换 Query
        List<VariableTrack.ReplaceRecord> queryRecords = replaceInParametersWithTrack(
            resolved.getQuery(), variables, request.getQuery());
        trackBuilder.queryReplaces(queryRecords);
        queryRecords.forEach(r -> allUnmatched.addAll(r.getUnmatchedVariables()));

        // 7. 替换 Body
        if (resolved.getBody() != null) {
            Body body = resolved.getBody();
            Body originalBody = request.getBody();
            String bodyBefore = null;
            String bodyAfter = null;
            List<String> bodyVars = new ArrayList<>();

            if (body.getJson() != null && originalBody != null && originalBody.getJson() != null
                    && (VariableReplacer.containsVariable(body.getJson())
                        || FunctionParser.containsFunction(body.getJson()))) {
                VariableReplacer.ReplaceResult rr = VariableReplacer.replaceWithTrack(body.getJson(), variables);
                body.setJson(rr.getResult());
                bodyBefore = originalBody.getJson();
                bodyAfter = rr.getResult();
                bodyVars.addAll(rr.getFoundVariables());
                allUnmatched.addAll(rr.getUnmatchedVariables());
            }
            if (body.getXml() != null && originalBody != null && originalBody.getXml() != null
                    && (VariableReplacer.containsVariable(body.getXml())
                        || FunctionParser.containsFunction(body.getXml()))) {
                VariableReplacer.ReplaceResult rr = VariableReplacer.replaceWithTrack(body.getXml(), variables);
                body.setXml(rr.getResult());
                if (bodyBefore == null) {
                    bodyBefore = originalBody.getXml();
                    bodyAfter = rr.getResult();
                }
                bodyVars.addAll(rr.getFoundVariables());
                allUnmatched.addAll(rr.getUnmatchedVariables());
            }
            List<VariableTrack.ReplaceRecord> formDataRecords = replaceInParametersWithTrack(
                body.getFormData(), variables, originalBody != null ? originalBody.getFormData() : null);
            bodyVars.addAll(formDataRecords.stream().flatMap(r -> r.getVariables().stream()).toList());
            allUnmatched.addAll(formDataRecords.stream().flatMap(r -> r.getUnmatchedVariables().stream()).toList());

            List<VariableTrack.ReplaceRecord> urlEncodedRecords = replaceInParametersWithTrack(
                body.getXWwwFormUrlencoded(), variables, originalBody != null ? originalBody.getXWwwFormUrlencoded() : null);
            bodyVars.addAll(urlEncodedRecords.stream().flatMap(r -> r.getVariables().stream()).toList());
            allUnmatched.addAll(urlEncodedRecords.stream().flatMap(r -> r.getUnmatchedVariables().stream()).toList());

            if (bodyBefore != null) {
                List<String> bodyUnmatched = new ArrayList<>();
                bodyUnmatched.addAll(formDataRecords.stream().flatMap(r -> r.getUnmatchedVariables().stream()).toList());
                bodyUnmatched.addAll(urlEncodedRecords.stream().flatMap(r -> r.getUnmatchedVariables().stream()).toList());
                trackBuilder.bodyReplace(VariableTrack.ReplaceRecord.builder()
                    .name("请求体")
                    .before(bodyBefore)
                    .after(bodyAfter)
                    .variables(bodyVars)
                    .unmatchedVariables(bodyUnmatched.stream().distinct().toList())
                    .build());
            }
        }

        // 去重未匹配变量
        trackBuilder.unmatchedVariables(allUnmatched.stream().distinct().toList());

        return new VariableResolveResult(resolved, trackBuilder.build());
    }

    /**
     * 判断请求中是否包含任何需要解析的占位符
     * 用于在变量上下文为空时，判断是否需要执行深拷贝和函数解析
     *
     * 除了旧版 {@code {{__函数名(参数)__}}} 表达式外，还需要识别参数级结构化 Mock
     * 占位符 {@code {{__MOCK__}}}，否则无环境变量时会直接返回原始请求，导致 Mock
     * 数据无法生成。</p>
     */
    private boolean containsAnyFunction(ApiRequest request) {
        if (FunctionParser.containsFunction(request.getRequestPath())) return true;
        if (containsFunctionOrMockPlaceholderInParameters(request.getRequestHeader())) return true;
        if (containsFunctionOrMockPlaceholderInParameters(request.getCookies())) return true;
        if (containsFunctionOrMockPlaceholderInParameters(request.getQuery())) return true;
        if (request.getBody() != null) {
            if (FunctionParser.containsFunction(request.getBody().getJson())) return true;
            if (FunctionParser.containsFunction(request.getBody().getXml())) return true;
            if (containsFunctionOrMockPlaceholderInParameters(request.getBody().getFormData())) return true;
            if (containsFunctionOrMockPlaceholderInParameters(request.getBody().getXWwwFormUrlencoded())) return true;
        }
        return false;
    }

    private boolean containsFunctionOrMockPlaceholderInParameters(List<RequestParameter> parameters) {
        if (parameters == null) return false;
        for (RequestParameter p : parameters) {
            if (p.getValue() != null && FunctionParser.containsFunction(p.getValue())) {
                return true;
            }
            if (p.getValue() != null && MockConfigGenerator.isMockPlaceholder(p.getValue())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建变量上下文（变量名 → 变量值的映射）
     *
     * 变量来源优先级（目前仅实现环境变量，预留扩展）：
     *   - 环境变量：RequestExecuteInfo
     *   - 全局变量（预留）
     *   - 前置步骤提取的变量（预留，供后续 UI 自动化步骤间变量传递使用）
     *
     * @param request API 请求配置对象
     * @return 变量上下文映射表，key 为变量名，value 为变量值；如果环境信息为空则返回空 Map
     */
    protected Map<String, Object> buildVariableContext(ApiRequest request) {
        Map<String, Object> variables = new HashMap<>();
        if (request.getEnvInfo() != null && request.getEnvInfo().getEnvVariables() != null) {
            variables.putAll(request.getEnvInfo().getEnvVariables());
        }
        return variables;
    }

    /**
     * 构建脚本可用的请求信息对象
     */
    private ScriptRequest buildScriptRequest(ApiRequest request) {
        Map<String, String> headerMap = new HashMap<>();
        if (request.getRequestHeader() != null) {
            request.getRequestHeader().forEach(p -> {
                if (p.getName() != null && !p.isDisabled()) {
                    headerMap.put(p.getName(), p.getValue());
                }
            });
        }

        Map<String, String> queryMap = new HashMap<>();
        if (request.getQuery() != null) {
            request.getQuery().forEach(p -> {
                if (p.getName() != null && !p.isDisabled()) {
                    queryMap.put(p.getName(), p.getValue());
                }
            });
        }

        Map<String, String> cookieMap = new HashMap<>();
        if (request.getCookies() != null) {
            request.getCookies().forEach(p -> {
                if (p.getName() != null && !p.isDisabled()) {
                    cookieMap.put(p.getName(), p.getValue());
                }
            });
        }

        String bodyStr = "";
        if (request.getBody() != null) {
            Body body = request.getBody();
            if (body.getJson() != null) bodyStr = body.getJson();
            else if (body.getXml() != null) bodyStr = body.getXml();
        }

        return ScriptRequest.builder()
                .url(request.getRequestPath())
                .method(request.getRequestMethod() != null ? request.getRequestMethod().name() : "GET")
                .headers(headerMap)
                .query(queryMap)
                .cookies(cookieMap)
                .body(bodyStr)
                .build();
    }

    /**
     * 将脚本执行后的请求修改同步回 ApiRequest。
     * 前置脚本可通过 context.setHeader / setQuery / setCookie / setBody / setUrl 修改请求，
     * 执行完后需要把 ScriptRequest 上的变更写回 ApiRequest，才能真正影响后续 HTTP 请求。
     */
    private void syncScriptRequestToApiRequest(ApiRequest request, ScriptRequest scriptRequest) {
        if (scriptRequest == null || request == null) {
            return;
        }

        // URL
        if (scriptRequest.getUrl() != null) {
            request.setRequestPath(scriptRequest.getUrl());
        }

        // Method
        if (scriptRequest.getMethod() != null) {
            try {
                request.setRequestMethod(RequestMethod.valueOf(scriptRequest.getMethod().toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("[脚本执行] 未知请求方法: {}", scriptRequest.getMethod());
            }
        }

        // Headers
        if (scriptRequest.getHeaders() != null) {
            request.setRequestHeader(syncParameterMap(request.getRequestHeader(), scriptRequest.getHeaders()));
        }

        // Query
        if (scriptRequest.getQuery() != null) {
            request.setQuery(syncParameterMap(request.getQuery(), scriptRequest.getQuery()));
        }

        // Cookies
        if (scriptRequest.getCookies() != null) {
            request.setCookies(syncParameterMap(request.getCookies(), scriptRequest.getCookies()));
        }

        // Body：仅 JSON / XML 模式支持脚本直接修改
        if (scriptRequest.getBody() != null && request.getBody() != null) {
            Body body = request.getBody();
            if (body.getMode() == BodyMode.JSON) {
                body.setJson(scriptRequest.getBody());
            } else if (body.getMode() == BodyMode.XML) {
                body.setXml(scriptRequest.getBody());
            }
        }
    }

    /**
     * 将 Map 形式的参数同步回 RequestParameter 列表。
     * 保留原有参数的 mockConfig/description/type/disabled 等元数据，仅更新 value；
     * 脚本新增的参数以默认 RequestParameter 加入。
     */
    private List<RequestParameter> syncParameterMap(List<RequestParameter> original, Map<String, String> map) {
        List<RequestParameter> result = new ArrayList<>();
        Map<String, RequestParameter> originalMap = new HashMap<>();
        if (original != null) {
            original.forEach(p -> {
                if (p.getName() != null) {
                    originalMap.put(p.getName(), p);
                }
            });
        }
        map.forEach((name, value) -> {
            if (name == null) {
                return;
            }
            RequestParameter param = originalMap.get(name);
            if (param != null) {
                param.setValue(value);
                result.add(param);
            } else {
                result.add(new RequestParameter(name, value));
            }
        });
        return result;
    }

    /**
     * 将脚本执行后的变量同步回 Request 的 envInfo 中。
     * 字符串/数字/布尔值保持原样；对象/数组/Map/List 转为 JSON 字符串，便于变量替换使用。
     */
    private void syncVariablesToRequest(ApiRequest request, Map<String, Object> variables) {
        if (request.getEnvInfo() == null) {
            request.setEnvInfo(new com.mokatest.platform.demos.api.domain.requestModel.RequestExecuteInfo());
        }
        Map<String, String> envVars = new HashMap<>();
        variables.forEach((k, v) -> envVars.put(k, convertVariableToString(v)));
        request.getEnvInfo().setEnvVariables(envVars);
    }

    /**
     * 将变量值转换为字符串用于环境变量存储和变量替换。
     * 基础类型直接 toString；集合/对象类型转为 JSON 字符串。
     */
    private String convertVariableToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map || value instanceof List || value.getClass().isArray()) {
            return JSON.toJSONString(value);
        }
        return value.toString();
    }

    /**
     * 批量替换请求参数列表中的变量占位符，并记录追踪信息
     *
     * 额外处理 {@code {{__MOCK__}}} 占位符：当参数值等于该占位符且携带 {@code mockConfig} 时，
     * 按结构化配置生成数据并替换。</p>
     *
     * @param parameters       替换后的参数列表（会被修改）
     * @param variables        变量上下文
     * @param originalParameters 原始参数列表（用于获取替换前的值）
     * @return 替换记录列表
     */
    private List<VariableTrack.ReplaceRecord> replaceInParametersWithTrack(
            List<RequestParameter> parameters, Map<String, Object> variables,
            List<RequestParameter> originalParameters) {
        List<VariableTrack.ReplaceRecord> records = new ArrayList<>();
        if (parameters == null || parameters.isEmpty()) {
            return records;
        }
        for (int i = 0; i < parameters.size(); i++) {
            RequestParameter param = parameters.get(i);
            String originalValue = originalParameters != null && i < originalParameters.size()
                    ? originalParameters.get(i).getValue() : param.getValue();

            // ARRAY 类型逐元素解析变量 / @ 语法 / 旧版 {{__MOCK()__}}
            if (param.getType() == ParameterType.ARRAY && param.getValue() != null && !param.getValue().isEmpty()) {
                try {
                    Object parsed = JSON.parse(param.getValue());
                    if (parsed instanceof JSONArray) {
                        JSONArray array = (JSONArray) parsed;
                        boolean changed = false;
                        List<String> itemVars = new ArrayList<>();
                        List<String> itemUnmatched = new ArrayList<>();
                        for (int j = 0; j < array.size(); j++) {
                            Object item = array.get(j);
                            if (item == null) {
                                continue;
                            }
                            String itemStr = item.toString();
                            if (VariableReplacer.containsVariable(itemStr) || FunctionParser.containsFunction(itemStr)) {
                                VariableReplacer.ReplaceResult rr = VariableReplacer.replaceWithTrack(itemStr, variables);
                                array.set(j, rr.getResult());
                                itemVars.addAll(rr.getFoundVariables());
                                itemUnmatched.addAll(rr.getUnmatchedVariables());
                                changed = true;
                            }
                        }
                        if (changed) {
                            String after = JSON.toJSONString(array);
                            param.setValue(after);
                            log.info("[参数替换] ARRAY 元素替换，name={}, before={}, after={}", param.getName(), originalValue, after);
                            records.add(VariableTrack.ReplaceRecord.builder()
                                    .name(param.getName())
                                    .before(originalValue)
                                    .after(after)
                                    .variables(itemVars.stream().distinct().toList())
                                    .unmatchedVariables(itemUnmatched.stream().distinct().toList())
                                    .build());
                        }
                        continue;
                    }
                } catch (Exception e) {
                    log.warn("[参数替换] ARRAY 类型参数解析失败，name={}, value={}", param.getName(), param.getValue(), e);
                }
            }

            // 1. 处理 {{__MOCK__}} 结构化占位符
            if (param.getValue() != null && MockConfigGenerator.isMockPlaceholder(param.getValue())) {
                log.info("[MockConfig] 识别到占位符，参数名={}, mockConfig={}", param.getName(), param.getMockConfig());
                String generated = MockConfigGenerator.generate(param.getMockConfig());
                log.info("[MockConfig] 生成结果={}", generated);
                param.setValue(generated);
                records.add(VariableTrack.ReplaceRecord.builder()
                        .name(param.getName())
                        .before(originalValue)
                        .after(generated)
                        .variables(new ArrayList<>())
                        .unmatchedVariables(new ArrayList<>())
                        .build());
                continue;
            }

            // 2. 处理变量 / 旧版 {{__MOCK()__}} / @phone() 等函数表达式
            if (param.getValue() != null
                    && (VariableReplacer.containsVariable(param.getValue())
                        || FunctionParser.containsFunction(param.getValue()))) {
                VariableReplacer.ReplaceResult rr = VariableReplacer.replaceWithTrack(param.getValue(), variables);
                param.setValue(rr.getResult());
                records.add(VariableTrack.ReplaceRecord.builder()
                        .name(param.getName())
                        .before(originalValue)
                        .after(rr.getResult())
                        .variables(rr.getFoundVariables())
                        .unmatchedVariables(rr.getUnmatchedVariables())
                        .build());
            }
        }
        return records;
    }


    /**
     * 构建完整的请求 URL
     *
     * URL 组成：环境基础 URL + 请求路径 + Query 参数字符串
     *
     * 示例：
     *   基础 URL = "https://api.example.com"
     *   请求路径 = "/api/user/{{userId}}"
     *   Query = [{name: "status", value: "active", disabled: false}]
     *   结果 = "https://api.example.com/api/user/123?status=active"
     *
     * 注意：Query 参数中的 disabled=true 的参数会被跳过
     *
     * @param request 变量替换后的 API 请求配置对象
     * @return 完整的请求 URL 字符串
     */
    protected String buildUrl(ApiRequest request) {
        String requestPath = request.getRequestPath();
        String baseUrl = request.getEnvInfo() != null ? request.getEnvInfo().getBaseUrl() : null;

        StringBuilder url = new StringBuilder();

        if (requestPath != null && isAbsoluteUrl(requestPath)) {
            // requestPath 本身包含完整协议和域名，直接使用
            url.append(requestPath);
        } else if (baseUrl != null && !baseUrl.isEmpty()) {
            // requestPath 是相对路径，用 baseUrl 拼接
            url.append(baseUrl);
            if (requestPath != null) {
                // 处理 baseUrl 末尾和 requestPath 开头的斜杠，避免双斜杠
                boolean baseEndsWithSlash = baseUrl.endsWith("/");
                boolean pathStartsWithSlash = requestPath.startsWith("/");
                if (baseEndsWithSlash && pathStartsWithSlash) {
                    url.append(requestPath.substring(1));
                } else if (!baseEndsWithSlash && !pathStartsWithSlash) {
                    url.append("/").append(requestPath);
                } else {
                    url.append(requestPath);
                }
            }
        } else if (requestPath != null && !requestPath.isEmpty()) {
            // 没有 baseUrl，但 requestPath 存在（可能是相对路径，交给 OkHttp 处理）
            url.append(requestPath);
        } else {
            throw new BusinessException("请求地址不能为空，请在接口中填写请求路径或在环境配置中设置服务地址");
        }

        if (request.getQuery() != null && !request.getQuery().isEmpty()) {
            url.append("?");
            boolean first = true;
            for (var param : request.getQuery()) {
                // 跳过列表中的 null 元素（前端 JSON 序列化可能产生）
                if (param == null) continue;
                if (param.isDisabled()) continue;

                String name = param.getName() != null ? param.getName() : "";
                if (param.getType() == ParameterType.ARRAY) {
                    try {
                        List<RequestParameterJsonUtils.NameValue> pairs =
                                RequestParameterJsonUtils.safeParseArray(param.getValue(), name);
                        for (RequestParameterJsonUtils.NameValue pair : pairs) {
                            if (!first) url.append("&");
                            url.append(URLEncoder.encode(pair.getName(), StandardCharsets.UTF_8))
                               .append("=")
                               .append(URLEncoder.encode(pair.getValue(), StandardCharsets.UTF_8));
                            first = false;
                        }
                    } catch (BusinessException e) {
                        throw new BusinessException("Query 参数 [" + name + "] 解析失败: " + e.getMessage());
                    }
                } else {
                    if (!first) url.append("&");
                    String value = param.getValue() != null ? param.getValue() : "";
                    url.append(URLEncoder.encode(name, StandardCharsets.UTF_8))
                       .append("=")
                       .append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                    first = false;
                }
            }
        }

        String result = url.toString();
        // 最终检查：URL 必须包含协议头才能被 OkHttp 识别
        if (!isAbsoluteUrl(result)) {
            throw new BusinessException(
                "请求地址不完整，请检查：1）环境配置中是否设置了服务地址；2）或直接在请求路径中填写完整 URL（如 https://api.example.com/api/user）"
            );
        }
        return result;
    }

    /**
     * 判断 URL 是否为绝对地址（包含协议头）
     */
    private boolean isAbsoluteUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    /**
     * 添加请求头到 OkHttp 请求构建器
     *
     * 合并逻辑：先添加环境级 Header，再添加接口自身的 Header。
     * 接口自身的 Header 优先级更高（后添加，可覆盖环境级的同名 Header）。
     *
     * 处理逻辑：
     *   - 环境级 Header（envInfo.envHeaders）：自动附加到当前环境的所有请求
     *   - 接口级 Header（requestHeader）：接口单独配置的请求头
     *   - 跳过 disabled=true 的请求头
     *   - 跳过 name 或 value 为 null 的请求头
     *   - 同名请求头会被追加（OkHttp 支持多值请求头）
     *
     * @param builder OkHttp 请求构建器
     * @param request API 请求配置对象
     * @return 自定义请求头映射表（key 为请求头名称，value 为请求头值）
     */
    protected Map<String, String> addHeaders(Request.Builder builder, ApiRequest request) {
        Map<String, String> headers = new HashMap<>();

        // 1. 先添加环境级 Header（优先级低）
        if (request.getEnvInfo() != null && request.getEnvInfo().getEnvHeaders() != null) {
            for (var header : request.getEnvInfo().getEnvHeaders()) {
                if (!header.isDisabled() && header.getName() != null && header.getValue() != null) {
                    builder.addHeader(header.getName(), header.getValue());
                    headers.put(header.getName(), header.getValue());
                }
            }
        }

        // 2. 再添加接口自身的 Header（优先级高，使用 header() 覆盖环境级的同名 Header）
        if (request.getRequestHeader() != null) {
            for (var header : request.getRequestHeader()) {
                if (!header.isDisabled() && header.getName() != null && header.getValue() != null) {
                    // header() 会覆盖同名的已有 Header，确保接口级 Header 优先级最高
                    builder.header(header.getName(), header.getValue());
                    headers.put(header.getName(), header.getValue());
                }
            }
        }
        return headers;
    }

    /**
     * 应用接口鉴权配置：把 AuthConfig 转换为等效的 Header/Query 参数追加到请求中。
     *
     * 调用时机：变量替换之前（token/username/password/keyValue 中的 ${var} 随统一替换管线解析）。
     *
     * 优先级约定：接口 Header/Query 表中已存在同名启用参数时，以用户手写为准，跳过注入。
     * 幂等：重复调用不会重复追加。
     *
     * @param request API 请求配置对象（就地修改）
     */
    protected void applyAuthConfig(ApiRequest request) {
        AuthConfig auth = request.getAuthConfig();
        if (auth == null || auth.getAuthType() == null || auth.getAuthType() == AuthType.NONE) {
            return;
        }
        switch (auth.getAuthType()) {
            case BEARER -> {
                if (isNotBlank(auth.getToken()) && !hasEnabledParam(request.getRequestHeader(), "Authorization")) {
                    appendParam(request, true, "Authorization", "Bearer " + auth.getToken().trim());
                }
            }
            case BASIC -> {
                if (isNotBlank(auth.getUsername()) && !hasEnabledParam(request.getRequestHeader(), "Authorization")) {
                    String raw = auth.getUsername() + ":" + (auth.getPassword() == null ? "" : auth.getPassword());
                    String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
                    appendParam(request, true, "Authorization", "Basic " + encoded);
                }
            }
            case API_KEY -> {
                if (isNotBlank(auth.getKeyName()) && auth.getKeyValue() != null) {
                    boolean toQuery = "query".equalsIgnoreCase(auth.getKeyIn());
                    if (toQuery) {
                        if (!hasEnabledParam(request.getQuery(), auth.getKeyName())) {
                            appendParam(request, false, auth.getKeyName(), auth.getKeyValue());
                        }
                    } else if (!hasEnabledParam(request.getRequestHeader(), auth.getKeyName())) {
                        appendParam(request, true, auth.getKeyName(), auth.getKeyValue());
                    }
                }
            }
            default -> {
                // NONE：不处理
            }
        }
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * 判断参数列表中是否已存在同名且启用的参数（名称忽略大小写）
     */
    private boolean hasEnabledParam(List<RequestParameter> params, String name) {
        if (params == null) return false;
        for (RequestParameter p : params) {
            if (!p.isDisabled() && p.getName() != null && p.getName().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private void appendParam(ApiRequest request, boolean header, String name, String value) {
        if (header) {
            if (request.getRequestHeader() == null) {
                request.setRequestHeader(new ArrayList<>());
            }
            request.getRequestHeader().add(new RequestParameter(name, value));
        } else {
            if (request.getQuery() == null) {
                request.setQuery(new ArrayList<>());
            }
            request.getQuery().add(new RequestParameter(name, value));
        }
    }

    /**
     * 添加 Cookie 到 OkHttp 请求构建器
     *
     * 合并逻辑：先添加环境级 Cookie，再添加接口自身的 Cookie。
     * 接口自身的 Cookie 优先级更高（后出现在 Cookie 头中，可覆盖环境级的同名 Cookie）。
     *
     * 将所有启用的 Cookie 拼接为标准 Cookie 请求头格式（name=value; name2=value2），
     * 通过 Request.Builder#addHeader("Cookie", cookieHeader) 添加
     *
     * 处理逻辑：
     *   - 环境级 Cookie（envInfo.envCookies）：自动附加到当前环境的所有请求
     *   - 接口级 Cookie（cookies）：接口单独配置的 Cookie
     *   - 跳过 disabled=true 的 Cookie
     *   - 跳过 name 或 value 为 null 的 Cookie
     *   - 多个 Cookie 用 "; " 分隔
     *
     * @param builder OkHttp 请求构建器
     * @param request API 请求配置对象
     */
    protected void addCookies(Request.Builder builder, ApiRequest request) {
        StringBuilder cookieHeader = new StringBuilder();
        boolean first = true;

        // 1. 先添加环境级 Cookie（优先级低）
        if (request.getEnvInfo() != null && request.getEnvInfo().getEnvCookies() != null) {
            for (var cookie : request.getEnvInfo().getEnvCookies()) {
                if (!cookie.isDisabled() && cookie.getName() != null && cookie.getValue() != null) {
                    if (!first) cookieHeader.append("; ");
                    cookieHeader.append(cookie.getName()).append("=").append(cookie.getValue());
                    first = false;
                }
            }
        }

        // 2. 再添加接口自身的 Cookie（优先级高，后添加）
        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                if (!cookie.isDisabled() && cookie.getName() != null && cookie.getValue() != null) {
                    if (!first) cookieHeader.append("; ");
                    cookieHeader.append(cookie.getName()).append("=").append(cookie.getValue());
                    first = false;
                }
            }
        }

        if (cookieHeader.length() > 0) {
            builder.addHeader("Cookie", cookieHeader.toString());
        }
    }

    protected abstract void configureRequestMethod(Request.Builder builder, RequestBody requestBody);

    /**
     * 根据 BodyMode 构建对应的 OkHttp RequestBody
     *
     * 支持的 Body 类型映射：
     *   - BodyMode#FORM_DATA → MultipartBody（multipart/form-data）
     *   - BodyMode#X_WWW_FORM_URLENCODED → FormBody（application/x-www-form-urlencoded）
     *   - BodyMode#JSON → RequestBody（application/json）
     *   - BodyMode#XML → RequestBody（application/xml）
     *   - BodyMode#NONE / null → 空字节数组
     *
     * @param request API 请求配置对象
     * @return 对应类型的 OkHttp RequestBody；如果 Body 为空则返回空字节数组
     */
    /**
     * 解析请求体应使用的 Content-Type：优先取用户在请求头里显式配置的 Content-Type
     * （如 text/xml、application/json），没有配置或解析失败时回退到默认值。
     * 这样可避免「用户设了 text/xml 却被强制改成 application/xml」，也能精确控制是否带 charset。
     *
     * @param request    已解析变量的请求
     * @param defaultType 默认 Content-Type（如 application/json）
     * @return 解析得到的 MediaType
     */
    protected MediaType resolveContentType(ApiRequest request, String defaultType) {
        if (request.getRequestHeader() != null) {
            for (RequestParameter header : request.getRequestHeader()) {
                if (header != null && !header.isDisabled()
                        && header.getName() != null
                        && "content-type".equalsIgnoreCase(header.getName().trim())
                        && header.getValue() != null && !header.getValue().trim().isEmpty()) {
                    MediaType parsed = MediaType.parse(header.getValue().trim());
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        return MediaType.parse(defaultType);
    }

    protected RequestBody buildRequestBody(ApiRequest request) {
        if (request.getBody() == null || request.getBody().getMode() == null ||
                request.getBody().getMode() == BodyMode.NONE) {
            return RequestBody.create(null, new byte[0]);
        }

        Body body = request.getBody();

        switch (body.getMode()) {
            case FORM_DATA:
                return buildFormDataBody(body.getFormData());
            case X_WWW_FORM_URLENCODED:
                return buildFormUrlEncodedBody(body.getXWwwFormUrlencoded());
            case JSON: {
                // 注意：必须用 byte[] 重载（create(MediaType, byte[])），它会原样使用 Content-Type；
                // 若用 create(String, MediaType)，OkHttp 会自动追加 "; charset=utf-8"，
                // 部分服务端只认裸 "application/json"，会导致 body 解析不到。
                // 绑定结构定义的 Body 已在变量替换阶段生成并写回 body.json，这里直接用 json 字段
                String json = body.getJson() != null ? body.getJson() : "";
                MediaType jsonType = resolveContentType(request, "application/json");
                return RequestBody.create(jsonType, json.getBytes(StandardCharsets.UTF_8));
            }
            case XML: {
                String xml = body.getXml() != null ? body.getXml() : "";
                MediaType xmlType = resolveContentType(request, "application/xml");
                return RequestBody.create(xmlType, xml.getBytes(StandardCharsets.UTF_8));
            }
            case BINARY:
            default:
                return RequestBody.create(null, new byte[0]);
        }
    }

    /** 判断请求是否绑定了 JSON Body 结构定义 */
    private boolean hasBoundJsonBody(ApiRequest request) {
        return request.getBody() != null
                && request.getBody().getSchemaBinding() != null
                && request.getBody().getSchemaBinding().getMode() != null
                && request.getBody().getSchemaBinding().getMode() != ResponseSchema.Mode.NONE;
    }

    /**
     * 请求 Body 绑定了结构定义（schemaBinding）时按定义生成 JSON，未绑定/解析失败返回 null 走手写 json。
     */
    private String resolveBoundJsonBody(Body body) {
        try {
            ResponseSchema binding = body.getSchemaBinding();
            if (binding == null || binding.getMode() == null || binding.getMode() == ResponseSchema.Mode.NONE) {
                return null;
            }
            MockFieldRule root = SchemaValidator.resolveSchema(binding);
            return root != null ? MockRuleGenerator.generate(root) : null;
        } catch (Exception e) {
            log.warn("[Body] 按绑定结构生成请求体失败，回退手写 JSON: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建 multipart/form-data 请求体
     *
     * 将 formData 参数列表转换为 OkHttp 的 MultipartBody。
     * 支持参数类型：
     *   - STRING / INTEGER / DOUBLE：作为普通文本字段
     *   - FILE：从临时文件服务读取文件，作为文件字段上传
     *
     * @param formData 表单数据参数列表
     * @return MultipartBody 实例
     * @throws BusinessException 如果 formData 为空或构建失败
     */
    protected RequestBody buildFormDataBody(List<RequestParameter> formData) {
        if (formData == null || formData.isEmpty()) {
            throw new BusinessException("form-data数据为空");

        }

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);
        for (var param : formData) {
            if (param.isDisabled()) continue;
            if (param.getType() == ParameterType.FILE) {
                addFilePart(builder, param);
            } else if (param.getType() == ParameterType.ARRAY) {
                try {
                    List<RequestParameterJsonUtils.NameValue> pairs =
                            RequestParameterJsonUtils.safeParseArray(param.getValue(), param.getName());
                    for (RequestParameterJsonUtils.NameValue pair : pairs) {
                        builder.addFormDataPart(pair.getName(), pair.getValue());
                    }
                } catch (BusinessException e) {
                    throw new BusinessException("form-data 参数 [" + param.getName() + "] 解析失败: " + e.getMessage());
                }
            } else {
                builder.addFormDataPart(param.getName(), param.getValue());
            }
        }

        MultipartBody build = null;
        try {
            build = builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
        return build;
    }

    /**
     * 向 MultipartBody 添加文件字段
     *
     * @param builder MultipartBody 构建器
     * @param param   FILE 类型参数
     */
    private void addFilePart(MultipartBody.Builder builder, RequestParameter param) {
        if (param.getValue() == null || param.getValue().isEmpty()) {
            log.warn("[FormData] FILE 类型参数 {} 的 fileId 为空，跳过该字段", param.getName());
            return;
        }
        File file = fileUploadService.getFile(param.getValue());
        if (file == null || !file.exists()) {
            log.warn("[FormData] 未找到 FILE 类型参数 {} 对应的文件：{}", param.getName(), param.getValue());
            return;
        }
        String originalFilename = fileUploadService.getOriginalFilename(param.getValue());
        MediaType mediaType = guessMediaType(originalFilename);
        RequestBody fileBody = RequestBody.create(file, mediaType);
        builder.addFormDataPart(param.getName(), originalFilename, fileBody);
    }

    /**
     * 根据文件名猜测 MediaType，无法识别时返回 application/octet-stream
     *
     * @param filename 文件名
     * @return MediaType
     */
    private MediaType guessMediaType(String filename) {
        if (filename == null) {
            return MediaType.parse("application/octet-stream");
        }
        String lower = filename.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return MediaType.parse("image/jpeg");
        }
        if (lower.endsWith(".png")) {
            return MediaType.parse("image/png");
        }
        if (lower.endsWith(".gif")) {
            return MediaType.parse("image/gif");
        }
        if (lower.endsWith(".pdf")) {
            return MediaType.parse("application/pdf");
        }
        if (lower.endsWith(".txt")) {
            return MediaType.parse("text/plain");
        }
        if (lower.endsWith(".json")) {
            return MediaType.parse("application/json");
        }
        if (lower.endsWith(".xml")) {
            return MediaType.parse("application/xml");
        }
        return MediaType.parse("application/octet-stream");
    }

    /**
     * 构建 application/x-www-form-urlencoded 请求体
     *
     * 将表单参数列表转换为 OkHttp 的 FormBody，
     * 自动进行 URL 编码
     *
     * @param formParams URL 编码表单参数列表
     * @return FormBody 实例
     */
    protected RequestBody buildFormUrlEncodedBody(java.util.List<RequestParameter> formParams) {
        FormBody.Builder builder = new FormBody.Builder();

        if (formParams != null) {
            for (var param : formParams) {
                if (param.isDisabled()) continue;
                if (param.getType() == ParameterType.ARRAY) {
                    try {
                        List<RequestParameterJsonUtils.NameValue> pairs =
                                RequestParameterJsonUtils.safeParseArray(param.getValue(), param.getName());
                        for (RequestParameterJsonUtils.NameValue pair : pairs) {
                            builder.add(pair.getName(), pair.getValue());
                        }
                    } catch (BusinessException e) {
                        throw new BusinessException("x-www-form-urlencoded 参数 [" + param.getName() + "] 解析失败: " + e.getMessage());
                    }
                } else {
                    builder.add(param.getName(), param.getValue());
                }
            }
        }
        return builder.build();
    }

    protected abstract String getMethodName();

    /**
     * 根据字段规则根节点生成 Mock 响应体 JSON
     */
    private String buildMockBodyFromRules(MockFieldRule rules) {
        return MockRuleGenerator.generate(rules);
    }

    /**
     * 构造 Mock 响应
     *
     * 当接口启用 Mock 时，跳过真实 HTTP 请求，直接返回用户配置的模拟响应。
     *
     * 处理逻辑：
     *   按配置模拟延迟（Thread.sleep）
     *   按 bodyMode 生成 Mock Body：RULES 模式按字段规则生成，RAW 模式使用手写 JSON
     *   Mock Body 与响应头均经过变量替换和函数解析，支持 ${var} / {{var}} / {{__MOCK()__}} / {{__TEMPLATE()__}}
     *   构造 TestHttpResponse，status 固定为 "mock"
     */
    /**
     * 按响应定义生成 Mock Body。响应定义未配置（NONE/无 schema）时返回 null，走旧逻辑兜底。
     */
    private String buildMockBodyFromResponseSchema(ApiRequest request) {
        try {
            ResponseSchema rs = request.getResponseSchema();
            if (rs == null || rs.getMode() == null || rs.getMode() == ResponseSchema.Mode.NONE) {
                return null;
            }
            MockFieldRule root = SchemaValidator.resolveSchema(rs);
            if (root == null) {
                return null;
            }
            return MockRuleGenerator.generate(root);
        } catch (Exception e) {
            log.warn("[Mock] 按响应定义生成 Body 失败，回退旧 Mock 配置: {}", e.getMessage());
            return null;
        }
    }

    private TestHttpResponse buildMockResponse(ApiRequest request, long startTime, String uuid,
                                               VariableTrack variableTrack, List<String> consoleLogs,
                                               List<ScriptContext.ScriptAssertion> scriptAssertions) {
        MockResponse mock = request.getMockResponse();

        // 模拟延迟
        if (mock.getDelayMs() > 0) {
            try {
                Thread.sleep(mock.getDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // 构建变量上下文，用于解析 Mock Body 中的变量占位符
        Map<String, Object> variables = buildVariableContext(request);

        // 生成 Mock Body：优先跟随响应定义（responseSchema），一处定义 Mock/校验共用；
        // 未配置响应定义时回退旧逻辑（RULES 字段规则 / RAW 手写 JSON），兼容存量数据
        String mockBody = buildMockBodyFromResponseSchema(request);
        if (mockBody == null) {
            if ("RULES".equalsIgnoreCase(mock.getBodyMode())) {
                mockBody = buildMockBodyFromRules(mock.getRules());
            } else {
                mockBody = mock.getBody() != null ? mock.getBody() : "";
            }
        }

        // 解析 ${var} / {{var}} 变量占位符
        if (mockBody != null && VariableReplacer.containsVariable(mockBody)) {
            mockBody = VariableReplacer.replace(mockBody, variables);
        }
        // 解析 {{__MOCK()__}} / {{__TEMPLATE()__}} / {{__TEMPLATE_BATCH()__}} 等公共函数
        if (mockBody != null && FunctionParser.containsFunction(mockBody)) {
            mockBody = FunctionParser.parse(mockBody);
        }
        byte[] bodyBytes = mockBody != null ? mockBody.getBytes(StandardCharsets.UTF_8) : new byte[0];

        // 构造响应头（支持 ${var} / {{var}} / {{__MOCK()__}} / {{__TEMPLATE()__}} 解析）
        Map<String, String> responseHeaders = new HashMap<>();
        if (mock.getHeaders() != null) {
            for (RequestParameter header : mock.getHeaders()) {
                if (header != null && !header.isDisabled()
                        && header.getName() != null && header.getValue() != null) {
                    String headerValue = header.getValue();
                    if (VariableReplacer.containsVariable(headerValue)) {
                        headerValue = VariableReplacer.replace(headerValue, variables);
                    }
                    if (FunctionParser.containsFunction(headerValue)) {
                        headerValue = FunctionParser.parse(headerValue);
                    }
                    responseHeaders.put(header.getName(), headerValue);
                }
            }
        }
        // 默认补充 Content-Type，避免前端解析异常
        if (!responseHeaders.containsKey("Content-Type")
                && !responseHeaders.containsKey("content-type")) {
            responseHeaders.put("Content-Type", "application/json");
        }

        // 构造请求头（用于调试展示）
        Map<String, String> requestHeaders = new HashMap<>();
        if (request.getEnvInfo() != null && request.getEnvInfo().getEnvHeaders() != null) {
            for (RequestParameter header : request.getEnvInfo().getEnvHeaders()) {
                if (header != null && !header.isDisabled()
                        && header.getName() != null && header.getValue() != null) {
                    requestHeaders.put(header.getName(), header.getValue());
                }
            }
        }
        if (request.getRequestHeader() != null) {
            for (RequestParameter header : request.getRequestHeader()) {
                if (header != null && !header.isDisabled()
                        && header.getName() != null && header.getValue() != null) {
                    requestHeaders.put(header.getName(), header.getValue());
                }
            }
        }

        // 构造请求体（用于调试展示）
        RequestBody requestBody = buildRequestBody(request);
        byte[] requestBodyBytes = requestBodyToBytes(requestBody);

        long endTime = System.currentTimeMillis();

        return TestHttpResponse.builder()
                .uuid(uuid)
                .status("mock")
                .apiId(request.getId())
                .apiName(request.getApiName())
                .requestUrl(request.getRequestPath())
                .requestMethod(getMethodName())
                .requestHeaders(requestHeaders)
                .requestBody(requestBodyBytes)
                .statusCode(mock.getStatusCode())
                .responseHeaders(responseHeaders)
                .rawBody(bodyBytes)
                .responseTimeMs(endTime - startTime)
                .requestStartTime(startTime)
                .requestEndTime(endTime)
                .variableTrack(variableTrack)
                .scriptConsoleLog(consoleLogs)
                .scriptAssertions(scriptAssertions)
                .build();
    }

    /**
     * 构建完整的请求头信息
     *
     * 合并自定义请求头与 OkHttp 自动生成的请求头（如 Content-Length、Host、Connection 等），
     * 优先使用自定义请求头的值
     *
     * 用途：在调试结果中展示本次请求发送时携带的所有请求头，便于排查问题
     *
     * @param request        OkHttp Request 实例
     * @param customHeaders  用户自定义的请求头
     * @return 完整请求头映射表
     */
    private Map<String, String> buildFullRequestHeaders(Request request, Map<String, String> customHeaders) {
        Map<String, String> fullHeaders = new HashMap<>(customHeaders);

        request.headers().names().forEach(name -> {
            if (!fullHeaders.containsKey(name)) {
                fullHeaders.put(name, request.header(name));
            }
        });

        return fullHeaders;
    }

    /**
     * 构建请求失败时的错误响应对象
     *
     * 当 HTTP 请求发生 IOException（网络超时、DNS 解析失败、连接被拒等）时，
     * 不抛出异常，而是构建一个包含错误信息的 TestHttpResponse 返回给调用方
     *
     * 响应状态固定为 "error"，包含异常对象和错误消息，
     * 前端可根据此状态展示友好的错误提示
     *
     * @param request        原始 API 请求配置
     * @param url            请求 URL
     * @param startTime      请求开始时间戳
     * @param endTime        请求结束时间戳
     * @param uuid           请求唯一标识
     * @param e              捕获的异常对象
     * @param requestHeaders 请求头信息
     * @param requestBody    请求体
     * @return 包含错误信息的 TestHttpResponse 实例
     */
    private TestHttpResponse buildErrorResponse(ApiRequest request, String url, long startTime, long endTime,
                                                String uuid, Exception e, Map<String, String> requestHeaders,
                                                RequestBody requestBody, VariableTrack variableTrack,
                                                List<String> scriptConsoleLog,
                                                List<ScriptContext.ScriptAssertion> scriptAssertions) {
        return TestHttpResponse.builder()
                .uuid(uuid)
                .status("error")
                .apiId(request.getId())
                .apiName(request.getApiName())
                .requestUrl(url)
                .requestMethod(getMethodName())
                .requestHeaders(requestHeaders)
                .requestBody(requestBodyToBytes(requestBody))
                .requestStartTime(startTime)
                .requestEndTime(endTime)
                .responseTimeMs(endTime - startTime)
                .variableTrack(variableTrack)
                .scriptConsoleLog(scriptConsoleLog)
                .scriptAssertions(scriptAssertions)
                .exception(e)
                .errorMessage(e.getMessage())
                .build();
    }

    /**
     * 将 OkHttp RequestBody 转换为字节数组
     *
     * 用途：在调试结果中展示请求体的原始内容
     *
     * 实现原理：将 RequestBody 写入 Buffer，再读取为字节数组。
     * 注意：此操作会消耗 RequestBody，但在请求已发送后调用不影响请求本身
     *
     * @param body OkHttp RequestBody 实例
     * @return 请求体的字节数组；如果 body 为 null 或转换失败则返回 null
     */
    protected byte[] requestBodyToBytes(RequestBody body) {
        if (body == null) return null;
        try {
            BufferedSink sink = new Buffer();
            body.writeTo(sink);
            return sink.buffer().readByteArray();
        } catch (IOException e) {
            return null;
        }
    }
}
