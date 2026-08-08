package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mokatest.platform.demos.domain.ui.Plan;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.service.PlanService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 自动化任务（计划）管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看任务：auto:plan:view
 *   创建任务：auto:plan:create
 *   编辑任务：auto:plan:update
 *   删除任务：auto:plan:delete
 *   执行任务：auto:plan:execute
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/plan")
public class PlanController {

    @Resource
    private PlanService planService;

    /**
     * 获取所有任务
     * 权限：auto:plan:view
     */
    @SaCheckPermission("auto:plan:view")
    @GetMapping("allPlan")
    public ResponseVO allPlan(@RequestParam Integer projectId) {
        List<Plan> plans = planService.allPlan(projectId);
        return ResponseVO.success(plans);
    }

    /**
     * 根据id获取任务
     * 权限：auto:plan:view
     */
    @SaCheckPermission("auto:plan:view")
    @GetMapping("getPlanById")
    public ResponseVO getPlanById(@RequestParam Integer planId) {
        Plan plan = planService.getPlanById(planId);
        return ResponseVO.success(plan);
    }

    /**
     * 更新任务
     * 权限：auto:plan:update
     */
    @SaCheckPermission("auto:plan:update")
    @PostMapping("updatePlan")
    public ResponseVO updatePlan(@RequestBody Plan plan) {
        Boolean result = planService.updatePlan(plan);
        return ResponseVO.success(result);
    }

    /**
     * 新建任务
     * 权限：auto:plan:create
     */
    @SaCheckPermission("auto:plan:create")
    @PostMapping("addPlan")
    public ResponseVO addPlan(@RequestBody Plan plan) {
        Boolean result = planService.addPlan(plan);
        return ResponseVO.success(result);
    }

    /**
     * 删除任务
     * 权限：auto:plan:delete
     */
    @SaCheckPermission("auto:plan:delete")
    @GetMapping("deletePlan")
    public ResponseVO deletePlan(@RequestParam Integer planId) {
        Boolean result = planService.deletePlan(planId);
        return ResponseVO.success(result);
    }

    /**
     * 更新任务运行配置
     * 权限：auto:plan:update
     */
    @SaCheckPermission("auto:plan:update")
    @PostMapping("updatePlanRunningConfig")
    public ResponseVO updatePlanRunningConfig(@RequestBody Map<String, Object> sceneSetting) {
        Boolean result = planService.updatePlanRunningConfig(sceneSetting);
        return ResponseVO.success(result);
    }
}
