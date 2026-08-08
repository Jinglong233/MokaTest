package com.mokatest.platform.demos.step.stepbuild;

import com.mokatest.platform.demos.domain.ui.uiEnum.StepType;
import com.mokatest.platform.demos.domain.ui.Setting;
import com.mokatest.platform.demos.domain.ui.TestStep;
import com.mokatest.platform.demos.domain.ui.dto.step.AssertStepDTO;
import com.mokatest.platform.demos.domain.ui.dto.step.ExtractStepDTO;
import com.mokatest.platform.demos.step.abstractStep.AbstractTestStep;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * @Author JingLong
 * @Description
 * @Date 2025/8/16 11:23
 **/
@Data
public abstract class StepAbstractBuilder implements StepCreationStrategy {
    private StepType stepType;

    public StepAbstractBuilder(StepType stepType) {
        this.stepType = stepType;
    }


    public StepType getStepType() {
        return this.stepType;
    }

    protected void setCommonProperties(AbstractTestStep testStep, TestStep stepEntity) {

        // 设置id
        testStep.setId(stepEntity.getId());
        // 设置是否禁用
        testStep.setIsDisable(stepEntity.getIsDisable());

        // 设置父id
        testStep.setParentId(stepEntity.getParentId());

        // 设置步骤名称
        testStep.setStepName(stepEntity.getStepName());

        // 设置步骤类型
        testStep.setStepType(StepType.valueOf(stepEntity.getStepType()));

        // 设置步骤顺序
        testStep.setOrderIndex(stepEntity.getOrderIndex());

        Object stepDetail = stepEntity.getStepDetail();

        Gson gson = new GsonBuilder().setDateFormat("yyyy-MM-dd HH:mm:ss")
                .create();
        Map map = gson.fromJson(stepDetail.toString(), Map.class);

        // 设置配置
        testStep.setSetting(gson.fromJson(gson.toJson(map.get("setting")), Setting.class));

        // 设置断言列表
        if (map.get("assertList") != null) {
            testStep.setAssertList(gson.fromJson(gson.toJson(map.get("assertList")),
                    new TypeToken<List<AssertStepDTO>>() {
                    }.getType()));
        }

        // 设置抽取列表
        if (map.get("extractList") != null) {
            testStep.setExtractList(gson.fromJson(gson.toJson(map.get("extractList")),
                    new TypeToken<List<ExtractStepDTO>>() {
                    }.getType()));
        }
    }

}
