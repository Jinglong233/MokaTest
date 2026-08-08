package com.mokatest.platform.demos.qa.ai.casegen;

import com.mokatest.platform.demos.qa.domain.TestCase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 用例草稿 → test_case 实体映射（防腐层）
 *
 * 表结构变更（如新增字段）只需调整本类，prompt 模板与解析器不动。
 */
@Component
public class CaseDraftMapper {

    /**
     * 草稿转实体
     *
     * @param draft         草稿（枚举已在解析层兜底）
     * @param projectId     项目ID（记录锚点）
     * @param requirementId 锚定需求ID（记录锚点，不信前端）
     * @param moduleId      需求所属模块（继承）
     */
    public TestCase toEntity(CaseDraftDTO draft, Integer projectId, Integer requirementId, Integer moduleId) {
        TestCase testCase = new TestCase();
        testCase.setProjectId(projectId);
        testCase.setRequirementId(requirementId);
        testCase.setModuleId(moduleId);
        testCase.setCaseName(draft.getCaseName().trim());
        testCase.setPreCondition(draft.getPreCondition());
        testCase.setCaseType(draft.getCaseType());
        testCase.setPriority(draft.getPriority());
        testCase.setStatus("DRAFT");
        testCase.setTags(draft.getTags());
        testCase.setExpectDuration(draft.getExpectDuration());
        if (draft.getTestSteps() != null) {
            List<TestCase.TestStepItem> steps = new ArrayList<>();
            for (CaseDraftDTO.StepItem item : draft.getTestSteps()) {
                TestCase.TestStepItem step = new TestCase.TestStepItem();
                step.setStep(item.getStep());
                step.setExpected(item.getExpected());
                steps.add(step);
            }
            testCase.setTestSteps(steps);
        }
        return testCase;
    }
}
