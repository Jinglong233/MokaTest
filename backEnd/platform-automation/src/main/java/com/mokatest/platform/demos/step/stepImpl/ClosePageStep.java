package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.domain.ui.uiEnum.browser.ColsePageMode;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.microsoft.playwright.Page;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/17 11:29
 **/
public class ClosePageStep extends AbstractTestStep {
    private ColsePageMode closePageMode; // 关闭页面模式
    private Integer customIndex; // 自定义关闭页面索引

    public ClosePageStep(ColsePageMode closePageMode, Integer customIndex) {
        this.closePageMode = closePageMode;
        this.customIndex = customIndex;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();

        List<Page> allPages = context.getAllPages();
        switch (closePageMode) {
            case CURRENT -> context.getCurrentPage().close();
            case CUSTOM_INDEX -> {
                if (customIndex >= 0 && customIndex < allPages.size()){
                    allPages.get(customIndex).close();
                }else {
                    throw new IndexOutOfBoundsException(String.format("索引为%s的页面不存在", customIndex));
                }
            }
            case FIRST -> allPages.get(0).close();
            case LAST -> {
                // 获取当前页面索引
                AtomicInteger currentPageIndex = context.getCurrentPageIndex();
                int closeIndex = Math.max(currentPageIndex.get() - 1, 0);
                // 如果小于0，则默认为0
                Page page = allPages.get(closeIndex);
                if (page != null && !page.isClosed()) {
                    page.close();
                }
            }
            case NEXT -> {
                // 获取当前页面索引
                AtomicInteger currentPageIndex = context.getCurrentPageIndex();
                int closeIndex = Math.min(currentPageIndex.get() + 1, context.getAllPages().size() - 1);
                // 如果大于当前列表长度，则默认为最后一个
                Page page = allPages.get(closeIndex);
                if (page != null && !page.isClosed()) {
                    page.close();
                }
            }
            case ALL -> allPages.forEach(page -> {
                if (page != null && !page.isClosed()) {
                    page.close();
                }
            });
            case END -> {
                Page page = allPages.get(allPages.size() - 1);
                if (page != null && !page.isClosed()){
                    page.close();
                }

            }
        }
        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return result;
    }
}
