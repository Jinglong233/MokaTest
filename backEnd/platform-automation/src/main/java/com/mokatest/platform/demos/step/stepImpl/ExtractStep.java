package com.mokatest.platform.demos.step.stepImpl;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepExecutionType;
import com.mokatest.platform.demos.config.ApplicationContextHolder;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import com.mokatest.platform.demos.extract.StepExtractExecuter;
import com.mokatest.platform.demos.result.BaseStepResult;
import com.mokatest.platform.demos.result.StepResult;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;

import java.util.HashMap;

/**
 * @Author JingLong
 * @Description 关联提取步骤
 * @Date 2025/7/24 19:23
 **/
public class ExtractStep extends AbstractTestStep {


    /**
     * 关联提取信息
     */
    private ExtractStepDTO stepExtractorInfo;

    public ExtractStep(
            ExtractStepDTO stepExtractorInfo) {
        this.stepExtractorInfo = stepExtractorInfo;

    }

    @Override
    public StepResult doExecute(TestExecutionContext context) {
        BaseStepResult result =  context.getCurrentCommonStepResult();
        StepExtractExecuter stepExtractExecuter = ApplicationContextHolder.getBean(StepExtractExecuter.class);
        HashMap<String, String> execute = (HashMap<String, String>) stepExtractExecuter.execute(context,
                stepExtractorInfo);
        result.setExtractResultsList(execute);
         context.getCurrentCommonStepResult().setStatus(StepExecutionType.SUCCESS);
        return context.getCurrentStepResult();
    }
}
