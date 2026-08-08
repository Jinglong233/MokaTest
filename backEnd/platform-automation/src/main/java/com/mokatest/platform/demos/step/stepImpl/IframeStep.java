package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.element.ElementLocatorType;
import com.mokatest.platform.demos.domain.ui.uiEnum.iframe.SwitchIframeType;
import com.mokatest.platform.demos.config.ApplicationContextHolder;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.Element;
import com.mokatest.platform.demos.domain.ui.dto.ElementDTO;
import com.mokatest.platform.demos.element.ElementProcessor;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;

import java.util.List;

/**
 * @Author JingLong
 * @Description iframe框架切换步骤
 * @Date 2025/8/22 16:19
 **/
public class IframeStep extends AbstractTestStep {

    // 切换方式
    private SwitchIframeType switchIframeType;
    // 目标元素
    private ElementDTO element;
    private String url;
    private String iframeName;
    private Integer iframeIndex;
    private String iframeId;

    public IframeStep(SwitchIframeType switchIframeType, ElementDTO element, String url, String iframeName,
                      Integer iframeIndex, String iframeId) {
        this.switchIframeType = switchIframeType;
        this.element = element;
        this.url = url;
        this.iframeName = iframeName;
        this.iframeIndex = iframeIndex;
        this.iframeId = iframeId;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();
        switch (switchIframeType) {
            case ID -> {
                Frame frame = context.getCurrentPage().frame(iframeId);
                context.setCurrentFrame(frame);
            }
            case NAME -> {
                Frame frame = context.getCurrentPage().frame(iframeName);
                context.setCurrentFrame(frame);
            }
            case INDEX -> {
                List<Frame> frames = context.getCurrentPage().frames();
                if (!frames.isEmpty()) {
                    for (int i = 1; i <= frames.size(); i++) {
                        if (iframeIndex == i) {
                            context.setCurrentFrame(frames.get(iframeIndex));
                        }
                    }
                }
            }
            case EXIT -> {
                Frame frame = context.getCurrentFrame().parentFrame();
                if (frame != null) {
                    context.setCurrentFrame(frame);
                }
            }
            case URL -> {
                Frame frame = context.getCurrentPage().frameByUrl(url);
                context.setCurrentFrame(frame);
            }
            case ELEMENT -> {
                ElementProcessor bean = ApplicationContextHolder.getBean(ElementProcessor.class);
                Element elementLocator = bean.getElementLocator(element);
                Locator locator = getLocator(context.getCurrentFrame(),
                        ElementLocatorType.valueOf(elementLocator.getLocatorType().toString().toUpperCase()),
                        elementLocator.getLocatorValue());
                Frame frame = locator.elementHandle().contentFrame();
                context.setCurrentFrame(frame);
            }
        }
         context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return result;
    }
}
