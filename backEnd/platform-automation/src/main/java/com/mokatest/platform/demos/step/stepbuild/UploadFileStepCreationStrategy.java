package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.dto.step.UploadFileStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.UploadFileStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

@Component
public class UploadFileStepCreationStrategy extends StepAbstractBuilder {

    public UploadFileStepCreationStrategy() {
        super(StepType.FILE_UPLOAD);
    }

    @Override
    public boolean supports(String stepType) {
        return "FILE_UPLOAD".equalsIgnoreCase(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();

        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss").create();
        UploadFileStepDTO uploadFileStepDTO = gson.fromJson(stepDetail.toString(), UploadFileStepDTO.class);

        UploadFileStep uploadFileStep = new UploadFileStep(uploadFileStepDTO.getElement(), uploadFileStepDTO.getFileIds());
        setCommonProperties(uploadFileStep, stepEntity);

        return uploadFileStep;
    }
}
