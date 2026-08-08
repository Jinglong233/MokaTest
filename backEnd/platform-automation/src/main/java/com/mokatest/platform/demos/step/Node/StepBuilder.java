package com.mokatest.platform.demos.step.Node;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.mapper.TestStepMapper;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.mokatest.platform.demos.step.stepbuild.StepBuilderFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class StepBuilder {
    @Resource
    private TestStepMapper testStepMapper;

    @Resource
    private StepBuilderFactory stepBuilderFactory;

    // 构建场景的步骤树
    public List<StepTreeNode> buildScenarioSteps(Integer scenarioId) {
        QueryWrapper<TestStep> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("scenario_id", scenarioId);
        // 1. 获取场景下的所有步骤
        List<TestStep> steps = testStepMapper.selectList(queryWrapper);
        // 排序
        steps.sort(Comparator.comparingInt(node -> node.getOrderIndex()));

        // 2. 获取parentId=0的顶父节点
        List<TestStep> parentSteps = steps.stream().filter(step -> step.getParentId() == 0).toList();


        List<StepTreeNode> stepTree = new ArrayList<>();

        // 3. 递归构建树状节点
        for (TestStep parentStep : parentSteps) {
            // 剔除禁用的节点以及子节点（这里不直接剔除 禁用节点，是因为初始化报告结果的时候，还需要记录这个节点数据）
//            if (parentStep.getIsDisable() == 1) continue;
            StepTreeNode stepTreeNode = new StepTreeNode(parentStep);
            AbstractTestStep build = stepBuilderFactory.build(parentStep.getStepType(), parentStep);
            stepTreeNode.setExecutableStep(build);
            buildStepTree(stepTreeNode, steps);
            stepTree.add(stepTreeNode);
        }
        return stepTree;
    }

    // 构建步骤树结构
    private void buildStepTree(StepTreeNode parentNode, List<TestStep> testSteps) {
        Integer parentId = parentNode.getStepEntity().getId();
        // 根据父id获取子节点
        List<TestStep> childrenSteps = testSteps.stream()
                .filter(step -> step.getParentId().equals(parentId))
                .collect(Collectors.toList());        // 排序
        if (childrenSteps.isEmpty()) return;
        childrenSteps.sort(Comparator.comparingInt(TestStep::getOrderIndex));
        List<StepTreeNode> node = new ArrayList<>();
        // 继续遍历
        for (TestStep childStep : childrenSteps) {
            StepTreeNode childNode = new StepTreeNode(childStep);
            AbstractTestStep build = stepBuilderFactory.build(childStep.getStepType(), childStep);
            childNode.setExecutableStep(build);
            buildStepTree(childNode, testSteps);
            node.add(childNode);
        }
        parentNode.setChildren(node);
    }

}