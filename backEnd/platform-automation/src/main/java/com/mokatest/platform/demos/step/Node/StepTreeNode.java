package com.mokatest.platform.demos.step.Node;

import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class StepTreeNode {
    private TestStep stepEntity;
    private AbstractTestStep executableStep;
    private List<StepTreeNode> children = new ArrayList<>();
    private int orderIndex;

    public StepTreeNode(TestStep stepEntity) {
        this.stepEntity = stepEntity;
        this.orderIndex = stepEntity.getOrderIndex();
    }
}