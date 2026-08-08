package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.exception.UnsupportedStepTypeException;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StepBuilderFactory {
    private final Map<String, StepCreationStrategy> strategyMap = new ConcurrentHashMap<>();

    // 自动注入所有策略实现
    @Autowired
    public StepBuilderFactory(List<StepCreationStrategy> strategies) {
        for (StepCreationStrategy strategy : strategies) {
            // 注册策略类型（以大写形式存储）
            strategyMap.put(strategy.getStepType().toString(), strategy);
        }
    }

    /**
     * 获取步骤创建策略
     *
     * @param stepType 步骤类型
     * @return 对应的策略
     */
    private StepCreationStrategy getStrategy(String stepType) {
        if (stepType == null) {
            throw new IllegalArgumentException("Step type cannot be null");
        }

        // 尝试直接匹配
        StepCreationStrategy strategy = strategyMap.get(stepType.toUpperCase());
        if (strategy != null) {
            return strategy;
        }

        // 遍历所有策略进行匹配
        for (StepCreationStrategy s : strategyMap.values()) {
            if (s.supports(stepType)) {
                return s;
            }
        }
        throw new UnsupportedStepTypeException("不支持的步骤类型: " + stepType);
    }


    public AbstractTestStep build(String stepType, TestStep testStep) {
        StepCreationStrategy strategy = getStrategy(stepType);
        return strategy.createExecutableStep(testStep);
    }
}