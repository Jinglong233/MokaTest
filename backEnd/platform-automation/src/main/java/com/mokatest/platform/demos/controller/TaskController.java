package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mokatest.platform.demos.domain.ui.Plan;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.service.PlanService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 自动化任务（计划）执行接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看任务状态：auto:plan:view
 *   创建任务：auto:plan:create
 *   执行任务/激活/停止/重跑：auto:plan:execute
 * 前端需在 Header 中传 X-Team-Id，供 SaToken 解析当前团队上下文。
 */
@RestController
@RequestMapping("/task")
public class TaskController {


    @Resource
    private PlanService planService;


    /**
     * 激活任务
     * 权限：auto:plan:execute
     */
    @SaCheckPermission("auto:plan:execute")
    @PostMapping("active/{planId}")
    public ResponseVO activeTask(@PathVariable Integer planId) {
        Boolean result = planService.activeTask(planId);
        return ResponseVO.success(result);
    }

    /**
     * 添加任务
     * 权限：auto:plan:create
     */
    @SaCheckPermission("auto:plan:create")
    @PostMapping("add")
    public ResponseVO addPlan(@RequestBody Plan plan) {
        Boolean result = planService.addPlan(plan);
        return ResponseVO.success(result);
    }


    /**
     * 停止任务
     * 权限：auto:plan:execute
     */
    @SaCheckPermission("auto:plan:execute")
    @PostMapping("/stop/{planId}")
    public ResponseVO stopTask(@PathVariable Integer planId) {
        Boolean result = planService.stopPlan(planId);
        return ResponseVO.success(result);
    }

    /**
     * 立即执行任务
     * 同步创建报告并返回报告ID，场景执行异步进行
     * 权限：auto:plan:execute
     */
    @SaCheckPermission("auto:plan:execute")
    @PostMapping("/execute/{taskId}")
    public ResponseVO executeTask(@PathVariable Integer taskId) {
        Integer reportId = planService.executeTask(taskId);
        return ResponseVO.success(reportId);
    }


    /**
     * 获取所有任务状态
     * 权限：auto:plan:view
     */
    @SaCheckPermission("auto:plan:view")
    @GetMapping("/status")
    public ResponseVO getStatus() {
        Map<Integer, Object> result = planService.getTaskStatus();
        return ResponseVO.success(result);
    }

    /**
     * 重新执行失败用例
     * 权限：auto:plan:execute
     */
    @SaCheckPermission("auto:plan:execute")
    @PostMapping("/reRun")
    public ResponseVO reRun(@RequestBody Map<String, Object> reTryScenes) {
        Boolean result = planService.reRun(reTryScenes);
        return ResponseVO.success(result);
    }
}
