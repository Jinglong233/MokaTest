package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import com.mokatest.platform.demos.service.ProjectMemberService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.Date;

/**
 * 项目成员管理接口
 */
@RestController
@RequestMapping("/project/member/")
public class ProjectMemberController {

    @Resource
    private ProjectMemberService projectMemberService;

    @Resource
    private ProjectPermissionChecker projectPermissionChecker;

    /**
     * 获取项目成员列表
     * 可见范围：本项目成员（项目管理员 owner + 模板授权成员）；超管/团队管理员天然放行（service 内校验）
     */
    @GetMapping("list/{projectId}")
    public SaResult list(@PathVariable Integer projectId) {
        return projectMemberService.getProjectMembers(projectId);
    }

    /**
     * 分配项目角色（新增或修改）
     */
    @SaCheckPermission(value = {"project:member:create", "project:member:update", "project:member:manage", "team:member:manage"}, mode = SaMode.OR)
    @OperationLog(module = "project", type = OperateType.BIND, targetType = "projectMember",
            targetId = "#dto.userId",
            targetName = "@logText.userName(#dto.userId)",
            description = "'加入项目「' + @logText.projectName(#dto.projectId) + '」，角色：' + @logText.roleName(#dto.roleId)")
    @PostMapping("assign")
    public SaResult assign(@RequestBody AssignProjectRoleDTO dto) {
        return projectMemberService.assignProjectRole(
                dto.getProjectId(), dto.getUserId(), dto.getRoleId(), dto.getExpireTime());
    }

    /**
     * 移除项目成员
     */
    @SaCheckPermission(value = {"project:member:delete", "project:member:manage", "team:member:manage"}, mode = SaMode.OR)
    @OperationLog(module = "project", type = OperateType.UNBIND, targetType = "projectMember",
            targetId = "#dto.userId",
            targetName = "@logText.userName(#dto.userId)",
            description = "'从项目「' + @logText.projectName(#dto.projectId) + '」移除成员'")
    @PostMapping("remove")
    public SaResult remove(@RequestBody RemoveProjectMemberDTO dto) {
        return projectMemberService.removeProjectMember(dto.getProjectId(), dto.getUserId());
    }

    /**
     * 判断当前用户是否为项目管理员（供前端使用）
     */
    @GetMapping("isAdmin/{projectId}")
    public SaResult isAdmin(@PathVariable Integer projectId) {
        String loginId = StpUtil.getLoginIdAsString();
        boolean isAdmin = projectPermissionChecker.hasProjectPermission(projectId, loginId, "project:member:manage")
                || projectPermissionChecker.hasProjectPermission(projectId, loginId, "project:member:create")
                || projectPermissionChecker.hasProjectPermission(projectId, loginId, "project:member:update")
                || projectPermissionChecker.hasProjectPermission(projectId, loginId, "project:member:delete");
        return SaResult.data(isAdmin);
    }

    /**
     * 项目可分配的角色选项（自定义模板）
     * 供邀请/分配角色弹窗使用；项目管理员等非团队管理角色也可用（service 内按项目鉴权）
     */
    @GetMapping("roleOptions/{projectId}")
    public SaResult roleOptions(@PathVariable Integer projectId) {
        return projectMemberService.getProjectRoleOptions(projectId);
    }

    public static class AssignProjectRoleDTO {
        private Integer projectId;
        private Long userId;
        private Long roleId;
        private Date expireTime;

        public Integer getProjectId() { return projectId; }
        public void setProjectId(Integer projectId) { this.projectId = projectId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
        public Date getExpireTime() { return expireTime; }
        public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
    }

    public static class RemoveProjectMemberDTO {
        private Integer projectId;
        private Long userId;

        public Integer getProjectId() { return projectId; }
        public void setProjectId(Integer projectId) { this.projectId = projectId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }
}
