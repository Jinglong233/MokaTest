package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.browser.SwitchTabMode;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.microsoft.playwright.PlaywrightException;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author JingLong
 * @Description 切换tab
 * @Date 2025/8/30 17:22
 **/
public class SwitchTabStep extends AbstractTestStep {

    private SwitchTabMode switchTabMode;
    private Integer customIndex;

    public SwitchTabStep(SwitchTabMode switchTabMode, Integer customIndex) {
        this.switchTabMode = switchTabMode;
        this.customIndex = customIndex;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        // 判断切换模式
        switch (switchTabMode) {
            case FIRST -> {
                context.getAllPages().get(0).bringToFront();
                context.setCurrentPageIndex(new AtomicInteger(0));
            }
            // 上一个页签
            case LAST -> {
                AtomicInteger currentPageIndex = context.getCurrentPageIndex();
                int i = currentPageIndex.get();
                if (i - 1 >= 0) {
                    context.getAllPages().get(i - 1).bringToFront();
                    context.setCurrentPageIndex(new AtomicInteger(i - 1));
                }
            }
            // 下一个页签
            case NEXT -> {
                AtomicInteger currentPageIndex = context.getCurrentPageIndex();
                int i = currentPageIndex.get();
                if (i + 1 < context.getAllPages().size()) {
                    context.getAllPages().get(i + 1).bringToFront();
                    context.setCurrentPageIndex(new AtomicInteger(i + 1));
                }
            }
            // 最后一个页签
            case END -> {
                context.getAllPages().get(context.getAllPages().size() - 1).bringToFront();
                context.setCurrentPageIndex(new AtomicInteger(context.getAllPages().size() - 1));
            }
            // 自定义页签索引
            case CUSTOM_INDEX -> {
                if (customIndex >= 0 && customIndex < context.getAllPages().size()) {
                    context.getAllPages().get(customIndex).bringToFront();
                    context.setCurrentPageIndex(new AtomicInteger(customIndex));
                }else {
                    throw new PlaywrightException("索引为" + customIndex + "的页面不存在");
                }
            }
        }
        if (context.getCurrentPage() != null) {
            // 切换页签之后，需要重置当前的iframe
            context.setCurrentFrame(context.getCurrentPage().mainFrame());
        }
        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);

        return result;
    }
}