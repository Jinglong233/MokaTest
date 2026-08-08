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
 * SQL步骤创建策略
 *
 * 注册到 Spring 容器后，StepBuilderFactory 会自动扫描并注册本策略。
 * 当 step_type = 'SQL' 时，使用本策略创建 {@link ApiRequestStep} 实例。
 *
 * 说明：SQL 步骤与 API_REQUEST 步骤的 stepDetail 结构一致（apiRequestId + apiConfig 副本），
 * 区别仅在 apiConfig.apiType = SQL；执行时由 {@link ApiStepExecutor} 按 apiType 分发到
 * SqlRequestExecutor，因此直接复用 DTO 与执行步骤类。
 */
@Component
public class SqlRequestStepCreationStrategy extends StepAbstractBuilder {

    @Autowired
    private ApiStepExecutor apiStepExecutor;

    public SqlRequestStepCreationStrategy() {
        super(StepType.SQL);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.SQL.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        ApiRequestStepDTO sqlRequestStepDTO = gson.fromJson(stepDetail.toString(), ApiRequestStepDTO.class);

        ApiRequestStep sqlRequestStep = new ApiRequestStep(
                sqlRequestStepDTO != null ? sqlRequestStepDTO.getApiRequestId() : null,
                sqlRequestStepDTO != null ? sqlRequestStepDTO.getApiConfig() : null,
                apiStepExecutor
        );

        // 设置通用属性（setting、assertList、extractList等）
        setCommonProperties(sqlRequestStep, stepEntity);

        return sqlRequestStep;
    }
}
