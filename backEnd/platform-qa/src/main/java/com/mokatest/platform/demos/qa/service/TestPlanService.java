package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.domain.TestPlan;

import java.util.List;
import java.util.Map;

public interface TestPlanService extends IService<TestPlan> {

    SaResult listByProject(Integer projectId, String keyword, String status, Integer moduleId, Integer page, Integer pageSize);

    SaResult saveOrUpdatePlan(TestPlan plan, Integer loginUserId);

    SaResult deletePlan(Integer id);

    SaResult getPlanDetail(Integer id);

    SaResult addCases(Integer planId, List<Integer> caseIds);

    SaResult removeCase(Integer planCaseId);
    
    SaResult updateCaseTestPoint(Integer planId, Integer planCaseId, Integer testPointId);

    SaResult executeCase(Integer planCaseId, String result, String remark, String stepResults, Integer executeUserId);

    SaResult batchExecute(Integer planId, List<Integer> planCaseIds, String result, Integer executeUserId);

    SaResult generateBugFromFailCase(Integer planCaseId, Bug bug, Integer createUserId);

    SaResult getExecutionHistory(Integer testCaseId);

    SaResult getPlanReport(Integer planId);

    /** 生成计划报告（快照固化到 test_plan_report.content），triggerType: MANUAL/AUTO */
    SaResult generatePlanReport(Integer planId, String triggerType, Integer loginUserId);

    /** 计划统计（全项目口径，供顶部统计卡片） */
    SaResult stats(Integer projectId);

    /** 获取计划的测试点树（含关联用例） */
    SaResult getTestPoints(Integer planId);

    /** 保存测试点树（增删改 + 用例关联） */
    SaResult saveTestPoints(Integer planId, List<Map<String, Object>> testPoints);
}
