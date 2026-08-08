package com.mokatest.platform.demos.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.Plan;

import java.util.List;
import java.util.Map;

/**
* @author: JingLong
* @description 针对表【plan】的数据库操作Service
* @createDate 2025-09-23 16:35:14
*/
public interface PlanService extends IService<Plan> {


    Boolean addPlan(Plan plan);

    // 停止任务
    Boolean stopPlan(Integer planId);

    Integer executeTask(Integer taskId);

    Map<Integer, Object> getTaskStatus();

    List<Plan> allPlan(Integer projectId);

    Plan getPlanById(Integer planId);

    Boolean updatePlan(Plan plan);

    Boolean deletePlan(Integer planId);

    Boolean activeTask(Integer planId);

    Boolean reRun(Map<String, Object> reTryScenes);

    Boolean updatePlanRunningConfig(Map<String, Object> sceneSetting);
}
