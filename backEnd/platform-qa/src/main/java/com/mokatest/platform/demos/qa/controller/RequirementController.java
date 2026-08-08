package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.service.RequirementService;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 需求池管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看列表/详情/血缘：qa:requirement:view
 *   创建需求：qa:requirement:create
 *   编辑需求：qa:requirement:update
 *   删除需求：qa:requirement:delete
 *   状态流转：qa:requirement:transition
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/requirement")
@RequiredArgsConstructor
public class RequirementController {

    private final RequirementService requirementService;

    /**
     * 需求列表
     * 权限：qa:requirement:view
     */
    @SaCheckPermission("qa:requirement:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) Integer moduleId,
                         @RequestParam(required = false) String reqType,
                         @RequestParam(required = false) String source,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return requirementService.listByProject(projectId, keyword, status, moduleId, reqType, source, page, pageSize);
    }

    /**
     * 需求统计（全项目口径，供顶部统计卡片）
     * 权限：qa:requirement:view
     */
    @SaCheckPermission("qa:requirement:view")
    @GetMapping("/stats")
    public SaResult stats(@RequestParam Integer projectId) {
        return requirementService.stats(projectId);
    }

    /**
     * 创建需求
     * 权限：qa:requirement:create
     */
    @SaCheckPermission("qa:requirement:create")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "requirement", targetId = "#requirement.id", targetName = "#requirement.title")
    @PostMapping("/save")
    public SaResult save(@RequestBody Requirement requirement) {
        return requirementService.saveOrUpdateRequirement(requirement);
    }

    /**
     * 编辑需求
     * 权限：qa:requirement:update
     */
    @SaCheckPermission("qa:requirement:update")
    @OperationLog(module = "qa", type = OperateType.UPDATE, targetType = "requirement", targetId = "#requirement.id", targetName = "#requirement.title", compareClass = Requirement.class)
    @PostMapping("/update")
    public SaResult update(@RequestBody Requirement requirement) {
        return requirementService.saveOrUpdateRequirement(requirement);
    }

    /**
     * 删除需求
     * 权限：qa:requirement:delete
     */
    @SaCheckPermission("qa:requirement:delete")
    @OperationLog(module = "qa", type = OperateType.DELETE, targetType = "requirement", targetId = "#id", compareClass = Requirement.class)
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return requirementService.deleteRequirement(id);
    }

    /**
     * 批量删除需求
     * 权限：qa:requirement:delete
     */
    @SaCheckPermission("qa:requirement:delete")
    @OperationLog(module = "qa", type = OperateType.BATCH_DELETE, targetType = "requirement", targetId = "#ids")
    @PostMapping("/batchDelete")
    public SaResult batchDelete(@RequestBody List<Integer> ids) {
        return requirementService.batchDeleteRequirement(ids);
    }

    /**
     * 需求详情
     * 权限：qa:requirement:view
     */
    @SaCheckPermission("qa:requirement:view")
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Integer id) {
        return requirementService.getDetail(id);
    }

    /**
     * 需求状态流转
     * 权限：qa:requirement:transition
     */
    @SaCheckPermission("qa:requirement:transition")
    @OperationLog(module = "qa", type = OperateType.TRANSITION, targetType = "requirement", targetId = "#requirementId", compareClass = Requirement.class)
    @PostMapping("/transition")
    public SaResult transition(@RequestParam Integer requirementId, @RequestParam String targetStatus) {
        return requirementService.transitionStatus(requirementId, targetStatus);
    }

    /**
     * 需求血缘追踪：查询需求 → 用例 → BUG 的完整关联链
     * 权限：qa:requirement:view
     */
    @SaCheckPermission("qa:requirement:view")
    @GetMapping("/{id}/trace")
    public SaResult trace(@PathVariable Integer id) {
        return requirementService.getTraceability(id);
    }
}
