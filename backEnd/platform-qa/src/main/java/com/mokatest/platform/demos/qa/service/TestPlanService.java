package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.domain.TestPlan;

import java.util.List;

public interface TestPlanService extends IService<TestPlan> {

    SaResult listByProject(Integer projectId, String keyword, String status, Integer page, Integer pageSize);

    SaResult saveOrUpdatePlan(TestPlan plan, Integer loginUserId);

    SaResult deletePlan(Integer id);

    SaResult getPlanDetail(Integer id);

    SaResult addCases(Integer planId, List<Integer> caseIds);

    SaResult removeCase(Integer planCaseId);

    SaResult executeCase(Integer planCaseId, String result, String remark, Integer executeUserId);

    SaResult batchExecute(Integer planId, List<Integer> planCaseIds, String result, Integer executeUserId);

    SaResult generateBugFromFailCase(Integer planCaseId, Bug bug, Integer createUserId);

    SaResult getExecutionHistory(Integer testCaseId);

    SaResult getPlanReport(Integer planId);

    /** 计划统计（全项目口径，供顶部统计卡片） */
    SaResult stats(Integer projectId);
}
