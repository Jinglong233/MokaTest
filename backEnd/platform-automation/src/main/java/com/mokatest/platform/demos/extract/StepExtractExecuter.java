package com.mokatest.platform.demos.extract;

import com.mokatest.platform.demos.domain.ui.uiEnum.extract.ExtractType;
import com.mokatest.platform.demos.debug.TestExecutionContext;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @Author JingLong
 * @Description 步骤提取执行器
 * @Date 2025/7/22 17:06
 **/
@Repository
public class StepExtractExecuter {
    @Resource
    private List<AssociationExtraction> associationExtractions;


    public Object execute(TestExecutionContext context, ExtractStepDTO stepExtractor) {
        for (AssociationExtraction associationExtraction : associationExtractions) {
            Object extractType = stepExtractor.getExtractType();
            ExtractType type = ExtractType.valueOf(extractType.toString());
            if (associationExtraction.isSupport(type)) {
                return associationExtraction.extract(context, stepExtractor);
            }
        }
        return null;
    }

}
