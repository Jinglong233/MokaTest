package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.vo.ApiStepResponseVO;
import com.mokatest.platform.demos.api.service.ApiStepExecutor;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.result.BaseStepResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * API请求步骤执行类
 *
 * 说明：
 * - 纯API场景执行时，由 {@link com.mokatest.platform.demos.api.service.ApiSceneDebugService} 直接调用API执行器，不走本类的doExecute
 * - 混合场景（UI 场景里插入 API 步骤）时，通过 {@link #doExecute} 调用 {@link ApiStepExecutor} 真正发请求，
 *   并使用 {@link TestExecutionContext} 的共享变量池实现步骤间变量传递（提取回写、后续步骤 ${var} 引用）
 * - 本类已注册到 {@link com.mokatest.platform.demos.step.stepbuild.StepBuilderFactory} 策略工厂中
 */
public class ApiRequestStep extends AbstractTestStep {

    private Integer apiRequestId;

    /**
     * 步骤内联的完整 API 配置（场景副本模式，优先于 apiRequestId）。
     * transient：步骤结果会被 GSON 序列化推送到 /ws/debug，这些执行期依赖不应进入 JSON。
     */
    private final transient ApiRequest apiConfig;

    /** 由策略注入的执行器（Spring bean，含 OkHttpClient 等，绝不能被 GSON 序列化） */
    private final transient ApiStepExecutor apiStepExecutor;

    public ApiRequestStep(Integer apiRequestId, ApiRequest apiConfig, ApiStepExecutor apiStepExecutor) {
        this.apiRequestId = apiRequestId;
        this.apiConfig = apiConfig;
        this.apiStepExecutor = apiStepExecutor;
    }

    @Override
    protected StepResult doExecute(TestExecutionContext context) {
        BaseStepResult common = context.getCurrentCommonStepResult();

        if (apiStepExecutor == null) {
            // 兜底：未注入执行器（理论上不会发生），按成功跳过，避免整场景中断
            common.setStatus(StepExecutionType.SUCCESS);
            return context.getCurrentStepResult();
        }

        // 使用场景级共享变量池：提取结果回写其中，后续步骤可引用
        // sceneEnvironment：场景级环境（UI 场景选择的环境），执行器据此合并 baseUrl/环境变量/Header/Cookie，
        // SQL 步骤据此解析环境级数据库连接；未选择环境时为 null，按无环境执行
        ApiStepResponseVO vo = apiStepExecutor.execute(apiConfig, apiRequestId, context.getVariables(),
                context.getSceneEnvironment());

        common.setApiResponse(vo);
        boolean success = apiStepExecutor.isStepSuccess(vo);
        common.setStatus(success ? StepExecutionType.SUCCESS : StepExecutionType.FAILURE);

        // 将 API 断言/提取结果映射到 BaseStepResult，便于报告展示
        if (vo != null) {
            if (vo.getAssertionResults() != null && !vo.getAssertionResults().isEmpty()) {
                Map<Integer, AssertResult> assertMap = new LinkedHashMap<>();
                int idx = 1;
                for (AssertResult ar : vo.getAssertionResults()) {
                    assertMap.put(idx++, ar);
                }
                common.setAssertResults(assertMap);
            }
            if (vo.getExtractedVariables() != null && !vo.getExtractedVariables().isEmpty()) {
                Map<String, String> extractMap = new LinkedHashMap<>();
                vo.getExtractedVariables().forEach((k, v) -> extractMap.put(k, v == null ? "" : v.toString()));
                common.setExtractResultsList(extractMap);
            }
        }

        if (!success && vo != null) {
            if (vo.getErrorMessage() != null && !vo.getErrorMessage().isEmpty()) {
                common.setErrorMessage(vo.getErrorMessage());
            } else {
                String msg = "HTTP 状态码: " + vo.getStatusCode();
                if (hasFailedAssertion(vo)) {
                    msg += "，存在断言失败";
                }
                common.setErrorMessage(msg);
            }
        }
        return context.getCurrentStepResult();
    }

    /**
     * API 步骤无需 Playwright 截图（且场景首步是 API 时还没有 page），覆写为 no-op 避免截图报错。
     */
    @Override
    protected void stepScreenshot(TestExecutionContext context) {
        // no-op
    }

    private boolean hasFailedAssertion(ApiStepResponseVO vo) {
        return vo.getAssertionResults() != null
                && vo.getAssertionResults().stream().anyMatch(r -> !Boolean.TRUE.equals(r.getSuccess()));
    }

    public Integer getApiRequestId() {
        return apiRequestId;
    }

    public void setApiRequestId(Integer apiRequestId) {
        this.apiRequestId = apiRequestId;
    }
}
