package com.mokatest.platform.demos.api.service;

import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiType;
import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import com.mokatest.platform.demos.api.domain.requestModel.RequestExecuteInfo;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.api.domain.vo.ApiStepResponseVO;
import com.mokatest.platform.demos.api.http.assertion.ApiAssertExecutor;
import com.mokatest.platform.demos.api.http.validation.SchemaValidator;
import com.mokatest.platform.demos.api.http.executor.RequestExecutor;
import com.mokatest.platform.demos.api.http.executor.RequestExecutorFactory;
import com.mokatest.platform.demos.api.http.executor.impl.SqlRequestExecutor;
import com.mokatest.platform.demos.api.http.extraction.ExtractionDetail;
import com.mokatest.platform.demos.api.http.extraction.ExtractionExecutor;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.result.AssertResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单个 API 请求步骤执行器（可被 UI 引擎与 API 场景复用）。
 *
 * 设计目的：让「API 请求」能作为一个步骤跑在 UI（Playwright）场景里——
 * 与纯 API 场景 {@link ApiSceneDebugService#executeApiRequestStep} 共享同一套底层执行器
 * （{@link RequestExecutorFactory} / {@link ExtractionExecutor} / {@link ApiAssertExecutor}），
 * 并通过传入的共享 {@code variables} 实现步骤间变量传递（提取结果回写、后续步骤 ${var} 引用）。
 *
 * 注意：本类只负责「单请求 + 提取 + 断言」，不做 scene/env 配置合并（混合场景 MVP 用完整 URL）。
 * 纯 API 场景仍走 {@code ApiSceneDebugService}，本类不影响其链路。
 */
@Slf4j
@Service
public class ApiStepExecutor {

    @Autowired
    private RequestExecutorFactory requestExecutorFactory;

    @Autowired
    private ExtractionExecutor extractionExecutor;

    @Autowired
    private ApiAssertExecutor apiAssertExecutor;

    @Autowired
    private ApiRequestMapper apiRequestMapper;

    @Autowired
    private SqlRequestExecutor sqlRequestExecutor;

    /**
     * 执行单个 API 请求。
     *
     * @param apiConfig    步骤内联的完整 API 配置（优先），可为 null
     * @param apiRequestId 兜底：按 id 加载接口（apiConfig 为空时使用），可为 null
     * @param variables    跨步骤共享的变量池；执行前注入到请求，提取结果会 putAll 回写
     * @return 响应 VO（含状态码/响应体/断言结果/提取变量等），永远非 null（异常时为 error VO）
     */
    public ApiStepResponseVO execute(ApiRequest apiConfig, Integer apiRequestId, Map<String, Object> variables) {
        return execute(apiConfig, apiRequestId, variables, null);
    }

    /**
     * 执行单个 API 请求（带场景环境）。
     *
     * @param sceneEnv 场景级环境（UI 场景选择的环境），可为 null。
     *                 非 null 时合并 baseUrl、环境变量、环境 Header/Cookie，
     *                 并将 envInfo.envId 设为该环境（SQL 步骤据此解析环境级数据库连接）
     */
    public ApiStepResponseVO execute(ApiRequest apiConfig, Integer apiRequestId, Map<String, Object> variables,
                                     com.mokatest.platform.demos.api.domain.Environment sceneEnv) {
        ApiRequest apiRequest = apiConfig;

        if (apiRequest == null && apiRequestId != null) {
            apiRequest = apiRequestMapper.selectById(apiRequestId);
        }
        if (apiRequest == null) {
            ApiStepResponseVO vo = new ApiStepResponseVO();
            vo.setStatus("error");
            vo.setErrorMessage("未配置 API 请求（apiConfig 与 apiRequestId 均为空或无效）");
            return vo;
        }

        try {
            // 1. 合并场景环境（baseUrl/环境变量/Header/Cookie/envId），优先级低于共享变量池
            if (sceneEnv != null) {
                mergeEnvironmentConfig(apiRequest, sceneEnv);
            } else if (apiRequest.getEnvInfo() != null) {
                // 场景未选环境：步骤副本里的 envId 只是编辑时从场景环境继承的快照（非用户手动覆盖），
                // 场景环境被清空后视为失效——置空，避免静默连接旧环境的数据库/服务；
                // SQL 步骤此时只能靠步骤级内联 dbConfig，否则报「未找到数据库连接配置」
                apiRequest.getEnvInfo().setEnvId(null);
            }

            // 2. 注入共享变量 → envInfo.envVariables（执行器内部据此替换 ${var}/{{var}}）
            injectVariables(apiRequest, variables);

            // 2. 发送请求：SQL 接口无 HTTP 请求方法，按 apiType 分发到 SQL 执行器。
            // SQL 的提取/断言在 SqlRequestExecutor 内部按 sqlConfig 执行完毕，结果挂在 response 上
            if (apiRequest.getApiType() == ApiType.SQL) {
                TestHttpResponse sqlResponse = sqlRequestExecutor.execute(apiRequest);
                // 提取结果回写共享变量池，供后续步骤 ${var} 引用
                if (sqlResponse.getExtractedVariables() != null && !sqlResponse.getExtractedVariables().isEmpty()
                        && variables != null) {
                    variables.putAll(sqlResponse.getExtractedVariables());
                }
                return convertToResponseVO(sqlResponse);
            }

            RequestExecutor executor = requestExecutorFactory.getExecutor(apiRequest.getRequestMethod());
            if (executor == null) {
                ApiStepResponseVO vo = buildErrorVO(apiRequest, "不支持的请求方法: " + apiRequest.getRequestMethod());
                return vo;
            }
            TestHttpResponse response = executor.execute(apiRequest);

            // 3. 关联提取 → 回写共享变量池
            Map<String, Object> extractedVariables = new HashMap<>();
            List<ExtractionDetail> extractionDetails = new ArrayList<>();
            if (apiRequest.getAssociationExtraction() != null && !apiRequest.getAssociationExtraction().isEmpty()) {
                extractedVariables = extractionExecutor.execute(apiRequest.getAssociationExtraction(), response);
                if (extractedVariables != null && !extractedVariables.isEmpty() && variables != null) {
                    variables.putAll(extractedVariables);
                }
                extractionDetails = extractionExecutor.executeWithDetails(
                        apiRequest.getAssociationExtraction(), response, RuleSource.API);
            }
            response.setExtractedVariables(extractedVariables);
            response.setExtractionDetails(extractionDetails);

            // 4. 断言（接口自身的 apiResultAssert）
            List<AssertResult> allAssertionResults = new ArrayList<>();
            if (apiRequest.getApiResultAssert() != null && !apiRequest.getApiResultAssert().isEmpty()) {
                for (AssertParameter ap : apiRequest.getApiResultAssert()) {
                    if (ap != null) ap.setSource(RuleSource.API);
                }
                allAssertionResults.addAll(apiAssertExecutor.execute(apiRequest.getApiResultAssert(), response));
            }
            // 4.1 响应结构校验（响应定义开启时自动执行）
            AssertResult schemaResult = SchemaValidator.validate(apiRequest.getResponseSchema(), response);
            if (schemaResult != null) {
                allAssertionResults.add(schemaResult);
            }
            response.setAssertionResults(allAssertionResults);

            // 5. 组装响应 VO
            return convertToResponseVO(response);

        } catch (Exception e) {
            log.error("[混合场景-API步骤] 执行异常, apiName={}", apiRequest.getApiName(), e);
            return buildErrorVO(apiRequest, e.getMessage());
        }
    }

    /**
     * 判断该响应对应的步骤是否成功：HTTP 2xx 且全部断言通过。
     */
    public boolean isStepSuccess(ApiStepResponseVO vo) {
        if (vo == null) return false;
        int code = vo.getStatusCode();
        boolean httpOk = code >= 200 && code < 300;
        if (!httpOk) return false;
        if (vo.getAssertionResults() == null) return true;
        return vo.getAssertionResults().stream().allMatch(r -> Boolean.TRUE.equals(r.getSuccess()));
    }

    // ==================== 内部工具（与 ApiSceneDebugService 一致）====================

    /**
     * 合并场景环境配置到请求（对齐 ApiSceneDebugService.mergeEnvironmentConfig）。
     *
     * 优先级：环境配置 < 共享变量池注入（injectVariables 后执行）< 接口自身配置。
     * 额外把 envInfo.envId 设为场景环境，SQL 步骤的 resolveDbConnection 据此解析环境级数据库连接。
     */
    private void mergeEnvironmentConfig(ApiRequest apiRequest, com.mokatest.platform.demos.api.domain.Environment env) {
        if (apiRequest.getEnvInfo() == null) {
            apiRequest.setEnvInfo(new RequestExecuteInfo());
        }
        RequestExecuteInfo envInfo = apiRequest.getEnvInfo();

        // 0. 场景环境 ID：SQL 步骤按 envId + dbConnectionName 解析环境级数据库连接
        envInfo.setEnvId(env.getId());

        // 1. 合并 baseUrl：接口未设置时使用环境的第一个 serve 地址
        if ((envInfo.getBaseUrl() == null || envInfo.getBaseUrl().isEmpty())
                && env.getServe() != null && !env.getServe().isEmpty()) {
            for (var serve : env.getServe()) {
                if (serve != null && serve.getAddress() != null && !serve.getAddress().isEmpty()) {
                    envInfo.setBaseUrl(serve.getAddress());
                    break;
                }
            }
        }

        // 2. 合并环境变量（优先级最低，已有变量覆盖同名环境变量）
        if (env.getEnvVar() != null && !env.getEnvVar().isEmpty()) {
            Map<String, String> mergedVars = new HashMap<>();
            for (RequestParameter varParam : env.getEnvVar()) {
                if (varParam != null && !varParam.isDisabled() && varParam.getName() != null) {
                    mergedVars.put(varParam.getName(), varParam.getValue());
                }
            }
            if (envInfo.getEnvVariables() != null) {
                mergedVars.putAll(envInfo.getEnvVariables());
            }
            envInfo.setEnvVariables(mergedVars);
        }

        // 3. 合并环境 Header（优先级最低，在列表前面）
        if (env.getHeaders() != null && !env.getHeaders().isEmpty()) {
            List<RequestParameter> mergedHeaders = new ArrayList<>();
            for (RequestParameter header : env.getHeaders()) {
                if (header != null && !header.isDisabled()) {
                    mergedHeaders.add(header);
                }
            }
            if (envInfo.getEnvHeaders() != null) {
                mergedHeaders.addAll(envInfo.getEnvHeaders());
            }
            envInfo.setEnvHeaders(mergedHeaders);
        }

        // 4. 合并环境 Cookie（优先级最低，在列表前面）
        if (env.getCookies() != null && !env.getCookies().isEmpty()) {
            List<RequestParameter> mergedCookies = new ArrayList<>();
            for (RequestParameter cookie : env.getCookies()) {
                if (cookie != null && !cookie.isDisabled()) {
                    mergedCookies.add(cookie);
                }
            }
            if (envInfo.getEnvCookies() != null) {
                mergedCookies.addAll(envInfo.getEnvCookies());
            }
            envInfo.setEnvCookies(mergedCookies);
        }
    }

    private void injectVariables(ApiRequest apiRequest, Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) return;
        if (apiRequest.getEnvInfo() == null) {
            apiRequest.setEnvInfo(new RequestExecuteInfo());
        }
        Map<String, String> envVars = apiRequest.getEnvInfo().getEnvVariables();
        if (envVars == null) {
            envVars = new HashMap<>();
            apiRequest.getEnvInfo().setEnvVariables(envVars);
        }
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            envVars.put(entry.getKey(), entry.getValue() != null ? entry.getValue().toString() : null);
        }
    }

    private ApiStepResponseVO buildErrorVO(ApiRequest apiRequest, String message) {
        ApiStepResponseVO vo = new ApiStepResponseVO();
        vo.setStatus("error");
        vo.setApiId(apiRequest.getId());
        vo.setApiName(apiRequest.getApiName());
        vo.setRequestMethod(apiRequest.getRequestMethod() != null ? apiRequest.getRequestMethod().name() : "UNKNOWN");
        vo.setRequestUrl(apiRequest.getRequestPath());
        vo.setErrorMessage(message);
        return vo;
    }

    private ApiStepResponseVO convertToResponseVO(TestHttpResponse response) {
        if (response == null) return null;
        ApiStepResponseVO vo = new ApiStepResponseVO();
        vo.setApiId(response.getApiId());
        vo.setApiName(response.getApiName());
        vo.setStatus(response.getStatus());
        vo.setRequestUrl(response.getRequestUrl());
        vo.setRequestMethod(response.getRequestMethod());
        vo.setRequestHeaders(response.getRequestHeaders());
        if (response.getRequestBody() != null) {
            vo.setRequestBody(new String(response.getRequestBody(), StandardCharsets.UTF_8));
        }
        vo.setStatusCode(response.getStatusCode());
        vo.setResponseStatusMsg(response.getResponseStatusMsg());
        vo.setResponseHeaders(response.getResponseHeaders());
        vo.setCookies(response.getCookies());
        vo.setBodyAsString(response.getBodyAsString());
        vo.setResponseBytes(response.getResponseBytes());
        vo.setResponseTimeMs(response.getResponseTimeMs());
        vo.setRequestBytes(response.getRequestBytes());
        if (response.getAssertionResults() != null) {
            List<AssertResult> copied = new ArrayList<>();
            for (AssertResult ar : response.getAssertionResults()) {
                AssertResult copy = new AssertResult();
                BeanUtils.copyProperties(ar, copy);
                copied.add(copy);
            }
            vo.setAssertionResults(copied);
        }
        vo.setExtractedVariables(response.getExtractedVariables());
        vo.setExtractionDetails(response.getExtractionDetails());
        vo.setVariableTrack(response.getVariableTrack());
        vo.setScriptConsoleLog(response.getScriptConsoleLog());
        vo.setScriptAssertions(response.getScriptAssertions());
        vo.setErrorMessage(response.getErrorMessage());
        return vo;
    }
}
