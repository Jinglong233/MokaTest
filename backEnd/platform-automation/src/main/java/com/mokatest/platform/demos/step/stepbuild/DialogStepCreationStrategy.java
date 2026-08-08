package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.DialogStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.DialogStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description 对话框操作步骤构建策略
 * @Date 2025/12/30 11:40
 **/
@Component
public class DialogStepCreationStrategy extends StepAbstractBuilder {


    public DialogStepCreationStrategy() {
        super(StepType.DIALOG);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.DIALOG.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        // 获取步骤详情
        Object stepDetail = stepEntity.getStepDetail();

        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        DialogStepDTO dialogStepDTO = gson.fromJson(stepDetail.toString(), DialogStepDTO.class);


        DialogStep dialogStep = null;
        // 判断类型是什么
        switch (dialogStepDTO.getDialogOperationType()) {
            case ACCEPT, DISMISS -> dialogStep = new DialogStep(dialogStepDTO.getDialogOperationType());
            case MESSAGE -> dialogStep = new DialogStep(dialogStepDTO.getDialogOperationType(),
                    dialogStepDTO.getDialogMessage());
            default ->
                    throw new RuntimeException("不支持的对话框操作类型:" + dialogStepDTO.getDialogOperationType().name());
        }
        setCommonProperties(dialogStep, stepEntity);

        return dialogStep;
    }
}