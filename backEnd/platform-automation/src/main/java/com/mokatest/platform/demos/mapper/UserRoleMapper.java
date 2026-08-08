package com.mokatest.platform.demos.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mokatest.platform.demos.domain.ui.UserRole;
import com.mokatest.platform.demos.domain.ui.vo.UserProjectRoleVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户角色授权查询。
 *
 * 扁平模板模型下，user_role 只会指向两类角色：
 *   SYSTEM 内置角色（team_admin / team_member / project_admin）：管理权限硬编码放行，不挂 role_permission；
 *   TEMPLATE 自定义模板：普通成员的执行类权限来源，通过 role_permission 联表。
 */
public interface UserRoleMapper extends BaseMapper<UserRole> {

    // ==================== 普通成员模板权限（TEMPLATE，动态引用） ====================

    /**
     * 获取用户在指定项目上下文中、通过模板获得的所有执行类权限编码
     */
    @Select("SELECT p.code " +
            "FROM user_role ur " +
            "JOIN role r ON ur.role_id = r.id " +
            "JOIN role_permission rp ON r.id = rp.role_id " +
            "JOIN permission p ON rp.permission_id = p.id " +
            "WHERE ur.user_id = #{userId} " +
            "  AND ur.status = 1 " +
            "  AND (ur.expire_time IS NULL OR ur.expire_time > NOW()) " +
            "  AND r.scope_type = 'TEMPLATE' " +
            "  AND ur.scope_id = #{projectId}")
    Set<String> getProjectTemplatePermissionCodes(@Param("userId") Long userId,
                                                   @Param("projectId") Long projectId);

    /**
     * 获取用户在指定团队下所有项目中、通过模板获得的执行类权限编码并集。
     * 用于在纯团队上下文（无 projectId）访问团队级资源时解析普通成员的执行权限。
     * 注：软删除模板对已分配成员仍生效，故不过滤 is_deleted。
     */
    @Select("SELECT DISTINCT p.code " +
            "FROM user_role ur " +
            "JOIN role r ON ur.role_id = r.id " +
            "JOIN role_permission rp ON r.id = rp.role_id " +
            "JOIN permission p ON rp.permission_id = p.id " +
            "JOIN project proj ON ur.scope_id = proj.id " +
            "WHERE ur.user_id = #{userId} " +
            "  AND ur.status = 1 " +
            "  AND (ur.expire_time IS NULL OR ur.expire_time > NOW()) " +
            "  AND r.scope_type = 'TEMPLATE' " +
            "  AND proj.team_id = #{teamId} " +
            "  AND proj.is_deleted = 0")
    Set<String> getTeamTemplatePermissionCodes(@Param("userId") Long userId,
                                               @Param("teamId") Long teamId);

    /**
     * 获取用户通过模板获得的所有权限编码（跨所有项目）
     */
    @Select("SELECT DISTINCT p.code " +
            "FROM user_role ur " +
            "JOIN role r ON ur.role_id = r.id " +
            "JOIN role_permission rp ON r.id = rp.role_id " +
            "JOIN permission p ON rp.permission_id = p.id " +
            "WHERE ur.user_id = #{userId} " +
            "  AND ur.status = 1 " +
            "  AND (ur.expire_time IS NULL OR ur.expire_time > NOW()) " +
            "  AND r.scope_type = 'TEMPLATE'")
    Set<String> getAllPermissionCodesByUserId(@Param("userId") Long userId);

    // ==================== 项目成员/角色统计（仅统计 TEMPLATE 分配） ====================

    /**
     * 统计团队中每个成员被分配的项目模板数量
     */
    @Select("SELECT ur.user_id AS userId, COUNT(*) AS cnt " +
            "FROM user_role ur " +
            "JOIN role r ON ur.role_id = r.id " +
            "JOIN project p ON ur.scope_id = p.id " +
            "WHERE p.team_id = #{teamId} " +
            "  AND r.scope_type = 'TEMPLATE' " +
            "  AND ur.status = 1 " +
            "  AND (ur.expire_time IS NULL OR ur.expire_time > NOW()) " +
            "  AND p.is_deleted = 0 " +
            "GROUP BY ur.user_id")
    List<Map<String, Object>> countProjectRolesByTeam(@Param("teamId") Long teamId);

    /**
     * 获取指定用户在团队下所有项目的模板分配
     */
    @Select("SELECT p.id AS projectId, p.project_name AS projectName, " +
            "       r.id AS roleId, r.name AS roleName, r.code AS roleCode, " +
            "       ur.expire_time AS expireTime " +
            "FROM user_role ur " +
            "JOIN role r ON ur.role_id = r.id " +
            "JOIN project p ON ur.scope_id = p.id " +
            "WHERE p.team_id = #{teamId} " +
            "  AND ur.user_id = #{userId} " +
            "  AND r.scope_type = 'TEMPLATE' " +
            "  AND ur.status = 1 " +
            "  AND (ur.expire_time IS NULL OR ur.expire_time > NOW()) " +
            "  AND p.is_deleted = 0")
    List<UserProjectRoleVO> getUserProjectRoles(@Param("teamId") Long teamId, @Param("userId") Long userId);

    /**
     * 获取用户在指定团队下、通过模板拥有指定权限的项目ID集合
     */
    @Select("SELECT DISTINCT p.id " +
            "FROM user_role ur " +
            "JOIN role r ON ur.role_id = r.id " +
            "JOIN role_permission rp ON r.id = rp.role_id " +
            "JOIN permission pm ON rp.permission_id = pm.id " +
            "JOIN project p ON ur.scope_id = p.id " +
            "WHERE p.team_id = #{teamId} " +
            "  AND ur.user_id = #{userId} " +
            "  AND r.scope_type = 'TEMPLATE' " +
            "  AND ur.status = 1 " +
            "  AND (ur.expire_time IS NULL OR ur.expire_time > NOW()) " +
            "  AND pm.code = #{permissionCode} " +
            "  AND p.is_deleted = 0")
    Set<Integer> getAccessibleProjectIds(@Param("teamId") Integer teamId,
                                         @Param("userId") Long userId,
                                         @Param("permissionCode") String permissionCode);
}
