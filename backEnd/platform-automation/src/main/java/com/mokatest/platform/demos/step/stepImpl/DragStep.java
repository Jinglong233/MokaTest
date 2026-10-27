package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.config.ApplicationContextHolder;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.element.ElementProcessor;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.microsoft.playwright.Locator;

/**
 * @Author JingLong
 * @Description 拖拽步骤
 * @Date 2025/12/24 17:55
 **/
public class DragStep extends AbstractTestStep {
    // 被拖拽的元素
    private ElementDTO dragElement;
    // 目标元素
    private ElementDTO targetElement;

    public DragStep(ElementDTO dragElement, ElementDTO targetElement) {
        this.dragElement = dragElement;
        this.targetElement = targetElement;
    }

    @Override
    protected StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        Locator targetLocator = getLocator(context, targetElement);
        Locator dragLocator = getLocator(context, dragElement);
        dragLocator.dragTo(targetLocator);
        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return result;
    }


    private Locator getLocator(TestExecutionContext context,ElementDTO element) {
        ElementProcessor bean = ApplicationContextHolder.getBean(ElementProcessor.class);
        // 定位链 fallback：按链逐个短超时只读探测，全部失败返回主定位
        return bean.resolveLocator(context.getCurrentFrame(), element);
    }
}
