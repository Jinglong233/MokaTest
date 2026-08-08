package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.api.service.ApiStepExecutor;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.ApiRequestStepDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ApiRequestStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * API请求步骤创建策略
 *
 * 注册到 Spring 容器后，StepBuilderFactory 会自动扫描并注册本策略。
 * 当 step_type = 'API_REQUEST' 时，使用本策略创建 {@link ApiRequestStep} 实例。
 */
@Component
public class ApiRequestStepCreationStrategy extends StepAbstractBuilder {

    @Autowired
    private ApiStepExecutor apiStepExecutor;

    public ApiRequestStepCreationStrategy() {
        super(StepType.API_REQUEST);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.API_REQUEST.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        ApiRequestStepDTO apiRequestStepDTO = gson.fromJson(stepDetail.toString(), ApiRequestStepDTO.class);

        ApiRequestStep apiRequestStep = new ApiRequestStep(
                apiRequestStepDTO != null ? apiRequestStepDTO.getApiRequestId() : null,
                apiRequestStepDTO != null ? apiRequestStepDTO.getApiConfig() : null,
                apiStepExecutor
        );

        // 设置通用属性（setting、assertList、extractList等）
        setCommonProperties(apiRequestStep, stepEntity);

        return apiRequestStep;
    }
}
