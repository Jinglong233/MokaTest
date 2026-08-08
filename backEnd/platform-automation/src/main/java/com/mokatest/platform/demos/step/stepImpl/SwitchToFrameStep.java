package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;


public class SwitchToFrameStep extends AbstractTestStep {
    String frameSelector;
    private final Object context;

    public SwitchToFrameStep(Object context, String frameSelector) {
        this.frameSelector = frameSelector;
        this.context = context;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        // 正确获取框架元素句柄
        ElementHandle frameElement = context.getCurrentFrame().querySelector(frameSelector);

        if (frameElement == null) {
            result.getResult().setErrorMessage("Frame not found with selector: " + frameSelector);
            return result;
        }

        // 获取框架元素的内容框架
        Frame newFrame = frameElement.contentFrame();


        if (newFrame == null) {
            result.getResult().setErrorMessage("Element is not a frame: " + frameSelector);
            return result;
        }
          /*  List<Frame> frames = this.context.getCurrentPage().frames();
            frames.add(newFrame);
            this.context.setCurrentFrame(newFrame);*/
         context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        result.getResult().setAdditionalInfo("Switched to frame: " + newFrame.url());

        return result;
    }
}