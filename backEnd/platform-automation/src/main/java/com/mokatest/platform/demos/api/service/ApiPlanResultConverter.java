package com.mokatest.platform.demos.api.service;

import com.mokatest.platform.demos.api.domain.vo.ApiStepResponseVO;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.result.BaseStepResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.service.TestStepService;
import com.mokatest.platform.demos.step.stepImpl.PlainTestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * API 计划执行结果转换器
 *
 * 将 {@link ApiSceneDebugService} 同步调试返回的 {@link ApiSceneDebugService.ApiStepResult}
 * 树转换为平台统一的 {@link StepResult} 树，以便复用现有报告存储和展示结构。</p>
 */
@Component
public class ApiPlanResultConverter {

    @Autowired
    private TestStepService testStepService;

    /**
     * 把单个 API 场景的执行结果转换为 StepResult 列表
     *
     * @param sceneId   场景 ID
     * @param apiResults API 场景执行结果（顶级步骤列表）
     * @return StepResult 列表
     */
    public List<StepResult> convert(Integer sceneId, List<ApiSceneDebugService.ApiStepResult> apiResults) {
        if (apiResults == null || apiResults.isEmpty()) {
            return new ArrayList<>();
        }
        List<TestStep> allSteps = testStepService.getStepBySceneId(sceneId);
        Map<Integer, TestStep> stepMap = allSteps.stream()
                .collect(Collectors.toMap(TestStep::getId, s -> s, (a, b) -> a));
        return apiResults.stream()
                .map(r -> buildStepResult(r, stepMap))
                .collect(Collectors.toList());
    }

    private StepResult buildStepResult(ApiSceneDebugService.ApiStepResult apiResult, Map<Integer, TestStep> stepMap) {
        StepResult stepResult = new StepResult(buildPlainStep(apiResult, stepMap));
        stepResult.setResult(buildBaseStepResult(apiResult));

        if (apiResult.getChildrenResults() != null && !apiResult.getChildrenResults().isEmpty()) {
            List<StepResult> children = apiResult.getChildrenResults().stream()
                    .map(c -> buildStepResult(c, stepMap))
                    .collect(Collectors.toList());
            stepResult.setChildren(children);
        }

        return stepResult;
    }

    private PlainTestStep buildPlainStep(ApiSceneDebugService.ApiStepResult apiResult, Map<Integer, TestStep> stepMap) {
        PlainTestStep step = new PlainTestStep();
        TestStep testStep = stepMap.get(apiResult.getStepId());
        if (testStep != null) {
            step.setId(testStep.getId());
            step.setParentId(testStep.getParentId());
            step.setIsDisable(testStep.getIsDisable());
            step.setStepName(testStep.getStepName());
            step.setOrderIndex(testStep.getOrderIndex());
            step.setStepType(StepType.valueOf(testStep.getStepType()));
        } else {
            step.setId(apiResult.getStepId());
            step.setStepName(apiResult.getStepName());
            step.setStepType(StepType.API_REQUEST);
        }
        return step;
    }

    private BaseStepResult buildBaseStepResult(ApiSceneDebugService.ApiStepResult apiResult) {
        BaseStepResult result = new BaseStepResult();
        result.setStatus(apiResult.getStatus());
        result.setTimeConsuming(apiResult.getTimeConsuming());
        result.setErrorMessage(apiResult.getErrorMessage());
        result.setApiResponse(apiResult.getResponse());

        // 断言结果：序号作为 key，兼容前端现有断言标签页
        if (apiResult.getAssertionResults() != null && !apiResult.getAssertionResults().isEmpty()) {
            Map<Integer, AssertResult> assertMap = new LinkedHashMap<>();
            int idx = 1;
            for (AssertResult ar : apiResult.getAssertionResults()) {
                assertMap.put(idx++, ar);
            }
            result.setAssertResults(assertMap);
        }

        // 提取结果
        if (apiResult.getExtractedVariables() != null && !apiResult.getExtractedVariables().isEmpty()) {
            Map<String, String> extractMap = new LinkedHashMap<>();
            apiResult.getExtractedVariables().forEach((k, v) ->
                    extractMap.put(k, v == null ? "" : v.toString())
            );
            result.setExtractResultsList(extractMap);
        }

        // 提取详情（如果有）
        ApiStepResponseVO responseVO = apiResult.getResponse();
        if (responseVO != null && responseVO.getExtractionDetails() != null && !responseVO.getExtractionDetails().isEmpty()) {
            // extractionDetails 已在 responseVO 中，前端可直接从 apiResponse 读取
        }

        return result;
    }
}
