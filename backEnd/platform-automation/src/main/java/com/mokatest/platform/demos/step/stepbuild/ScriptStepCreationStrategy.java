package com.mokatest.platform.demos.step.stepbuild;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.ScriptStepDTO;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.ScriptStep;
import org.springframework.stereotype.Component;

/**
 * 脚本步骤创建策略
 *
 * 注册到 Spring 容器后，StepBuilderFactory 会自动扫描并注册本策略。
 * 当 step_type = 'SCRIPT' 时，使用本策略创建 {@link ScriptStep} 实例。
 */
@Component
public class ScriptStepCreationStrategy extends StepAbstractBuilder {

    public ScriptStepCreationStrategy() {
        super(StepType.SCRIPT);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.SCRIPT.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        ScriptStepDTO scriptStepDTO = gson.fromJson(stepDetail.toString(), ScriptStepDTO.class);

        ScriptStep scriptStep = new ScriptStep(
                scriptStepDTO != null ? scriptStepDTO.getScriptContent() : null,
                parseProjectId(stepEntity.getProjectId())
        );

        // 设置通用属性（setting、assertList、extractList等）
        setCommonProperties(scriptStep, stepEntity);

        return scriptStep;
    }

    private Integer parseProjectId(String projectId) {
        try {
            return projectId != null ? Integer.valueOf(projectId) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
