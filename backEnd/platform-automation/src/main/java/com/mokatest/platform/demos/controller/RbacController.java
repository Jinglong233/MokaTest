package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.config.saTokenConfig.TeamContextHolder;
import com.mokatest.platform.demos.domain.ui.Role;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.AssignRolePermissionDTO;
import com.mokatest.platform.demos.service.PermissionService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import com.mokatest.platform.demos.service.RoleService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rbac/")
public class RbacController {

    @Resource
    private RoleService roleService;

    @Resource
    private PermissionService permissionService;

    @Resource
    private ProjectPermissionChecker projectPermissionChecker;

    /**
     * 获取角色列表
     * 用于成员管理页（分配角色）和角色管理页，因此允许 team:member:manage 或 team:role:manage 任一权限
     */
    @SaCheckPermission(value = {"team:member:manage", "team:role:manage"}, mode = SaMode.OR)
    @GetMapping("roles")
    public SaResult getRoleList(@RequestParam(required = false) Long teamId) {
        return roleService.getRoleList(teamId);
    }

    /**
     * 创建角色
     * 仅超级管理员可创建（系统角色或团队自定义角色）
     */
    @SaCheckPermission("team:role:manage")
    @PostMapping("role/create")
    public SaResult createRole(@RequestBody Role role) {
        String loginId = StpUtil.getLoginIdAsString();
        if (!projectPermissionChecker.isSuperAdmin(loginId)) {
            return SaResult.error("仅超级管理员可创建角色");
        }
        // 团队角色必须与当前团队一致
        if (role.getTeamId() != null) {
            Integer currentTeamId = TeamContextHolder.getTeamId();
            if (currentTeamId == null || !currentTeamId.equals(role.getTeamId().intValue())) {
                return SaResult.error("团队ID不合法");
            }
        }
        return roleService.createRole(role);
    }

    /**
     * 更新角色
     * 仅超级管理员可更新
     */
    @SaCheckPermission("team:role:manage")
    @PostMapping("role/update")
    public SaResult updateRole(@RequestBody Role role) {
        if (role.getId() == null) {
            return SaResult.error("缺少角色ID");
        }
        String loginId = StpUtil.getLoginIdAsString();
        if (!projectPermissionChecker.isSuperAdmin(loginId)) {
            return SaResult.error("仅超级管理员可更新角色");
        }
        return roleService.updateRole(role);
    }

    /**
     * 删除角色
     * 仅超级管理员可删除
     */
    @SaCheckPermission("team:role:manage")
    @PostMapping("role/delete/{id}")
    public SaResult deleteRole(@PathVariable Long id) {
        String loginId = StpUtil.getLoginIdAsString();
        if (!projectPermissionChecker.isSuperAdmin(loginId)) {
            return SaResult.error("仅超级管理员可删除角色");
        }
        return roleService.deleteRole(id);
    }

    /**
     * 获取角色已分配的权限
     */
    @SaCheckPermission("team:role:manage")
    @GetMapping("role/permissions/{roleId}")
    public SaResult getRolePermissions(@PathVariable Long roleId) {
        return roleService.getRolePermissions(roleId);
    }

    /**
     * 为角色分配权限
     * 仅超级管理员可操作
     */
    @SaCheckPermission("team:role:manage")
    @PostMapping("role/assignPermissions")
    public SaResult assignPermissions(@RequestBody AssignRolePermissionDTO dto) {
        String loginId = StpUtil.getLoginIdAsString();
        if (!projectPermissionChecker.isSuperAdmin(loginId)) {
            return SaResult.error("仅超级管理员可分配权限");
        }
        return roleService.assignPermissions(dto.getRoleId(), dto.getPermissionIds());
    }

    /**
     * 获取当前登录用户在指定团队下的权限编码列表
     *
     * 供前端做按钮级/页面级权限控制，返回用户拥有的所有 permission.code。
     * 超级管理员不受团队限制，返回系统全部权限编码。
     *
     * @param teamId 团队ID（超管可不传）
     * @return 权限编码列表
     */
    @GetMapping("user/permissions")
    public SaResult getUserPermissions(@RequestParam(required = false) Integer teamId,
                                       @RequestParam(required = false) Integer projectId) {
        String loginId = StpUtil.getLoginIdAsString();
        // 超级管理员返回所有权限
        if (projectPermissionChecker.isSuperAdmin(loginId)) {
            return SaResult.data(projectPermissionChecker.getAllPermissionCodes());
        }
        if (teamId == null) {
            return SaResult.error("团队ID不能为空");
        }
        // 传了 projectId 时按「团队 + 该项目」精确解析；否则按团队级解析
        List<String> permissions = projectPermissionChecker.getPermissionList(teamId, projectId, loginId);
        return SaResult.data(permissions);
    }

    /**
     * 获取权限树
     */
    @SaCheckPermission("team:role:manage")
    @GetMapping("permissions/tree")
    public SaResult getPermissionTree() {
        return permissionService.getPermissionTree();
    }

    /**
     * 获取所有权限
     */
    @SaCheckPermission("team:role:manage")
    @GetMapping("permissions")
    public SaResult getAllPermissions() {
        return permissionService.getAllPermissions();
    }
}
