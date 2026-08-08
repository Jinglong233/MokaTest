package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.service.BugService;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * BUG池管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看列表/详情：qa:bug:view
 *   创建BUG：qa:bug:create
 *   编辑BUG：qa:bug:update
 *   删除BUG：qa:bug:delete
 *   状态流转：qa:bug:transition
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/bug")
@RequiredArgsConstructor
public class BugController {

    private final BugService bugService;

    /**
     * BUG列表
     * 权限：qa:bug:view
     */
    @SaCheckPermission("qa:bug:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String severity,
                         @RequestParam(required = false) String priority,
                         @RequestParam(required = false) Integer requirementId,
                         @RequestParam(required = false) Integer testCaseId,
                         @RequestParam(required = false) Integer moduleId,
                         @RequestParam(required = false) String environment,
                         @RequestParam(required = false) String reproduceRate,
                         @RequestParam(required = false) String closeReason,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return bugService.listByProject(projectId, keyword, status, severity, priority, requirementId, testCaseId, moduleId, environment, reproduceRate, closeReason, page, pageSize);
    }

    /**
     * BUG 统计（全项目口径，供顶部统计卡片）
     * 权限：qa:bug:view
     */
    @SaCheckPermission("qa:bug:view")
    @GetMapping("/stats")
    public SaResult stats(@RequestParam Integer projectId) {
        return bugService.stats(projectId);
    }

    /**
     * 创建BUG
     * 权限：qa:bug:create
     */
    @SaCheckPermission("qa:bug:create")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "bug", targetId = "#bug.id", targetName = "#bug.title")
    @PostMapping("/save")
    public SaResult save(@RequestBody Bug bug) {
        return bugService.saveOrUpdateBug(bug);
    }

    /**
     * 编辑BUG
     * 权限：qa:bug:update
     */
    @SaCheckPermission("qa:bug:update")
    @OperationLog(module = "qa", type = OperateType.UPDATE, targetType = "bug", targetId = "#bug.id", targetName = "#bug.title", compareClass = Bug.class)
    @PostMapping("/update")
    public SaResult update(@RequestBody Bug bug) {
        return bugService.saveOrUpdateBug(bug);
    }

    /**
     * 删除BUG
     * 权限：qa:bug:delete
     */
    @SaCheckPermission("qa:bug:delete")
    @OperationLog(module = "qa", type = OperateType.DELETE, targetType = "bug", targetId = "#id", compareClass = Bug.class)
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return bugService.deleteBug(id);
    }

    /**
     * 批量删除BUG
     * 权限：qa:bug:delete
     */
    @SaCheckPermission("qa:bug:delete")
    @OperationLog(module = "qa", type = OperateType.BATCH_DELETE, targetType = "bug", targetId = "#ids")
    @PostMapping("/batchDelete")
    public SaResult batchDelete(@RequestBody List<Integer> ids) {
        return bugService.batchDeleteBug(ids);
    }

    /**
     * BUG详情
     * 权限：qa:bug:view
     */
    @SaCheckPermission("qa:bug:view")
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Integer id) {
        return bugService.getDetail(id);
    }

    /**
     * BUG状态流转
     * 权限：qa:bug:transition
     */
    @SaCheckPermission("qa:bug:transition")
    @OperationLog(module = "qa", type = OperateType.TRANSITION, targetType = "bug", targetId = "#bugId", compareClass = Bug.class)
    @PostMapping("/transition")
    public SaResult transition(@RequestParam Integer bugId, @RequestParam String targetStatus) {
        return bugService.transitionStatus(bugId, targetStatus);
    }
}
