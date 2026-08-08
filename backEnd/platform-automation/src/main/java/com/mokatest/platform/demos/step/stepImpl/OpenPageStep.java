package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;

// 导航步骤
public class OpenPageStep extends AbstractTestStep {
    private String url;

    private Integer recover;

    public OpenPageStep(String url, Integer recover) {
        this.url = url;
        this.recover = recover;
    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        StepResult result = context.getCurrentStepResult();
        BrowserContext browserContext = context.getContext();
        // URL 支持 ${var}/{{var}} 变量与 @mock/{{__函数__}} 函数表达式
        String url = VariableReplacer.replace(this.url, context.getVariables());
        if (recover == 1) {
            context.getCurrentFrame().navigate(url);
        } else {
            // 判断是不是新页面
            if (!context.getCurrentFrame().page().url().equals("about:blank")) {
                Page page = browserContext.newPage();
                context.initNewPage(page);
            }
            // 新开的页面就说明刚打开浏览器
            context.getCurrentFrame().navigate(url);
        }

        context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);

        return result;
    }
}