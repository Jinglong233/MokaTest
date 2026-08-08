package com.mokatest.platform.demos.config.saTokenConfig;

import cn.dev33.satoken.stp.StpInterface;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * SaToken 权限数据源实现
 *
 * 为 SaToken 的 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解提供权限列表。
 * 由于本项目 RBAC 按团队隔离，同一用户在不同团队权限不同，
 * 因此权限列表的查询依赖 {@link TeamContextHolder} 中保存的当前请求 teamId。
 *
 * 获取 teamId 的流程：
 * {@link TeamContextInterceptor} 在请求进入 Controller 前解析 Header/参数，
 * 将 teamId 写入 {@link TeamContextHolder}；
 * 本类在 @SaCheckPermission 触发时从 ThreadLocal 读取 teamId，
 * 再调用 {@link ProjectPermissionChecker#getPermissionList(Integer, String)} 查询权限编码。
 *
 * 注意：admin 角色默认拥有所有权限，无需在 role_permission 表中配置。
 */
@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    private ProjectPermissionChecker projectPermissionChecker;

    /**
     * 返回指定账号ID所拥有的权限码集合
     *
     * @param loginId   账号ID（当前登录用户ID）
     * @param loginType 账号类型
     * @return 权限码集合；若当前请求未设置 teamId 且非超级管理员，返回空列表
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        if (loginId == null) {
            return new ArrayList<>();
        }
        String loginIdStr = String.valueOf(loginId);
        // 超级管理员拥有所有权限，不依赖 teamId 上下文
        if (projectPermissionChecker.isSuperAdmin(loginIdStr)) {
            return projectPermissionChecker.getAllPermissionCodes();
        }
        Integer teamId = TeamContextHolder.getTeamId();
        if (teamId == null) {
            return new ArrayList<>();
        }
        // 当前请求若带有项目上下文（X-Project-Id），按「团队 + 该项目」精确解析权限，
        // 实现不同项目之间的权限清晰区分；否则按团队级解析。
        Integer projectId = ProjectContextHolder.getProjectId();
        return projectPermissionChecker.getPermissionList(teamId, projectId, loginIdStr);
    }

    /**
     * 返回指定账号ID所拥有的角色标识集合
     *
     * @param loginId   账号ID
     * @param loginType 账号类型
     * @return 角色编码集合；若当前请求未设置 teamId，返回空列表
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        Integer teamId = TeamContextHolder.getTeamId();
        if (teamId == null || loginId == null) {
            return new ArrayList<>();
        }
        String roleCode = projectPermissionChecker.getUserRoleCode(teamId, String.valueOf(loginId));
        List<String> roles = new ArrayList<>();
        if (roleCode != null) {
            roles.add(roleCode);
        }
        return roles;
    }
}
