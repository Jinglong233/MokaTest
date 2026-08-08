package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.KeyboardStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepImpl.KeyboardStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class KeyboardCreationStrategy extends StepAbstractBuilder {

    public KeyboardCreationStrategy() {
        super(StepType.KEYBOARD);
    }

    @Override
    public boolean supports(String stepType) {
        return "INPUT".equalsIgnoreCase(stepType);
    }

    private Map<String, String> params = new HashMap<>();

    @Override
    public AbstractTestStep createExecutableStep(TestStep stepEntity) {
        Object stepDetail = stepEntity.getStepDetail();
        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        KeyboardStepDTO keyboardStepDTO = gson.fromJson(stepDetail.toString(), KeyboardStepDTO.class);
        KeyboardStep keyboardStep = new KeyboardStep(keyboardStepDTO.getElement(), keyboardStepDTO.getInputType(),
                keyboardStepDTO.getKeyboardKey(), keyboardStepDTO.getInputValue(), keyboardStepDTO.getIsAdditional());
        setCommonProperties(keyboardStep, stepEntity);
        return keyboardStep;

    }
}