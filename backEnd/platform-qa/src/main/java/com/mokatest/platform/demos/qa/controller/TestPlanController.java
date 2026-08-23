package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.domain.TestPlan;
import com.mokatest.platform.demos.qa.service.TestPlanService;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 测试计划管理接口（QA维度）
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看列表/详情/报告：qa:testplan:view
 *   创建测试计划：qa:testplan:create
 *   编辑测试计划：qa:testplan:update
 *   删除测试计划：qa:testplan:delete
 *   执行/批量执行测试计划：qa:testplan:execute
 *   从失败用例生成BUG：qa:bug:create
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/testPlan")
@RequiredArgsConstructor
public class TestPlanController {

    private final TestPlanService testPlanService;

    /**
     * 测试计划列表
     * 权限：qa:testplan:view
     */
    @SaCheckPermission("qa:testplan:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Integer moduleId,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return testPlanService.listByProject(projectId, keyword, status, moduleId, page, pageSize);
    }

    /**
     * 测试计划统计（全项目口径，供顶部统计卡片）
     * 权限：qa:testplan:view
     */
    @SaCheckPermission("qa:testplan:view")
    @GetMapping("/stats")
    public SaResult stats(@RequestParam Integer projectId) {
        return testPlanService.stats(projectId);
    }

    /**
     * 创建测试计划
     * 权限：qa:testplan:create
     */
    @SaCheckPermission("qa:testplan:create")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "testPlan", targetId = "#plan.id", targetName = "#plan.planName")
    @PostMapping("/save")
    public SaResult save(@RequestBody TestPlan plan) {
        return testPlanService.saveOrUpdatePlan(plan, Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 编辑测试计划
     * 权限：qa:testplan:update
     */
    @SaCheckPermission("qa:testplan:update")
    @OperationLog(module = "qa", type = OperateType.UPDATE, targetType = "testPlan", targetId = "#plan.id", targetName = "#plan.planName", compareClass = TestPlan.class)
    @PostMapping("/update")
    public SaResult update(@RequestBody TestPlan plan) {
        return testPlanService.saveOrUpdatePlan(plan, Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 删除测试计划
     * 权限：qa:testplan:delete
     */
    @SaCheckPermission("qa:testplan:delete")
    @OperationLog(module = "qa", type = OperateType.DELETE, targetType = "testPlan", targetId = "#id", compareClass = TestPlan.class)
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return testPlanService.deletePlan(id);
    }

    /**
     * 测试计划详情
     * 权限：qa:testplan:view
     */
    @SaCheckPermission("qa:testplan:view")
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Integer id) {
        return testPlanService.getPlanDetail(id);
    }

    /**
     * 向测试计划添加用例
     * 权限：qa:testplan:update
     */
    @SaCheckPermission("qa:testplan:update")
    @OperationLog(module = "qa", type = OperateType.BIND, targetType = "testPlan", targetId = "#planId", compareClass = TestPlan.class,
            description = "'添加 ' + #caseIds.size() + ' 条用例到计划'")
    @PostMapping("/addCases")
    public SaResult addCases(@RequestParam Integer planId, @RequestBody List<Integer> caseIds) {
        return testPlanService.addCases(planId, caseIds);
    }

    /**
     * 从测试计划移除用例
     * 权限：qa:testplan:update
     */
    @SaCheckPermission("qa:testplan:update")
    @OperationLog(module = "qa", type = OperateType.UNBIND, targetType = "testPlan",
            description = "'从计划移除用例（计划用例#' + #planCaseId + '）'")
    @PostMapping("/removeCase")
    public SaResult removeCase(@RequestParam Integer planCaseId) {
        return testPlanService.removeCase(planCaseId);
    }

    /**
     * 更新计划用例的测试点关联（关联/取消关联）
     * 权限：qa:testplan:update
     */
    @SaCheckPermission("qa:testplan:update")
    @PostMapping("/updateCaseTestPoint")
    public SaResult updateCaseTestPoint(@RequestParam Integer planId,
                                       @RequestParam Integer planCaseId,
                                       @RequestParam(required = false) Integer testPointId) {
        return testPlanService.updateCaseTestPoint(planId, planCaseId, testPointId);
    }

    /**
     * 执行计划用例
     * 权限：qa:testplan:execute
     */
    @SaCheckPermission("qa:testplan:execute")
    @OperationLog(module = "qa", type = OperateType.EXECUTE, targetType = "testPlan",
            description = "'执行计划用例（计划用例#' + #planCaseId + '），结果：' + #result")
    @PostMapping("/execute")
    public SaResult executeCase(@RequestParam Integer planCaseId,
                                @RequestParam String result,
                                @RequestParam(required = false) String remark,
                                @RequestBody(required = false) List<Map<String, Object>> stepResults) {
        String stepResultsJson = null;
        if (stepResults != null) {
            try {
                stepResultsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(stepResults);
            } catch (Exception e) {
                return SaResult.error("步骤结果序列化失败");
            }
        }
        return testPlanService.executeCase(planCaseId, result, remark, stepResultsJson, Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 批量执行计划用例
     * 权限：qa:testplan:execute
     */
    @SaCheckPermission("qa:testplan:execute")
    @OperationLog(module = "qa", type = OperateType.EXECUTE, targetType = "testPlan", targetId = "#planId", compareClass = TestPlan.class,
            description = "'批量执行 ' + #planCaseIds.size() + ' 条计划用例，结果：' + #result")
    @PostMapping("/batchExecute")
    public SaResult batchExecute(@RequestParam Integer planId,
                                 @RequestBody List<Integer> planCaseIds,
                                 @RequestParam String result) {
        return testPlanService.batchExecute(planId, planCaseIds, result, Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 从执行失败的计划用例生成BUG
     * 权限：qa:bug:create
     */
    @SaCheckPermission("qa:bug:create")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "bug", targetId = "#bug.id", targetName = "#bug.title")
    @PostMapping("/generateBug")
    public SaResult generateBug(@RequestParam Integer planCaseId, @RequestBody Bug bug) {
        return testPlanService.generateBugFromFailCase(planCaseId, bug, Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 查询用例执行历史
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/executionHistory")
    public SaResult executionHistory(@RequestParam Integer testCaseId) {
        return testPlanService.getExecutionHistory(testCaseId);
    }

    /**
     * 测试计划报告
     * 权限：qa:testplan:view
     */
    @SaCheckPermission("qa:testplan:view")
    @GetMapping("/{planId}/report")
    public SaResult report(@PathVariable Integer planId) {
        return testPlanService.getPlanReport(planId);
    }

    /**
     * 获取测试点树（含关联用例）
     * 权限：qa:testplan:view
     */
    @SaCheckPermission("qa:testplan:view")
    @GetMapping("/{planId}/testPoints")
    public SaResult testPoints(@PathVariable Integer planId) {
        return testPlanService.getTestPoints(planId);
    }

    /**
     * 保存测试点树（增删改 + 用例关联）
     * 权限：qa:testplan:update
     */
    @SaCheckPermission("qa:testplan:update")
    @PostMapping("/{planId}/testPoints/save")
    public SaResult saveTestPoints(@PathVariable Integer planId, @RequestBody List<Map<String, Object>> testPoints) {
        return testPlanService.saveTestPoints(planId, testPoints);
    }
}
