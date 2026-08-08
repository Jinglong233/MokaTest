package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.uiEnum.iframe.SwitchIframeType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.IframeStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.IframeStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/22 16:52
 **/
@Component
public class IframeStepCreationStrategy extends StepAbstractBuilder {

    public IframeStepCreationStrategy() {
        super(StepType.IFRAME);
    }

    @Override
    public boolean supports(String stepType) {
        return StepType.IFRAME.toString().equals(stepType);
    }

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        IframeStepDTO iframeStepDTO = gson.fromJson(stepDetail.toString(), IframeStepDTO.class);
        SwitchIframeType switchIframeType = iframeStepDTO.getSwitchIframeType();
        ElementDTO element = iframeStepDTO.getElement();
        String url = iframeStepDTO.getUrl();
        String iframeName = iframeStepDTO.getIframeName();
        Integer iframeIndex = iframeStepDTO.getIframeIndex();
        String iframeId = iframeStepDTO.getIframeId();

        IframeStep iframeStep = new IframeStep(switchIframeType, element, url, iframeName, iframeIndex, iframeId);
        setCommonProperties(iframeStep, stepEntity);

        return iframeStep;
    }
}
