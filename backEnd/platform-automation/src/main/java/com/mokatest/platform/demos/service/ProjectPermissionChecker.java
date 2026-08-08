package com.mokatest.platform.demos.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.Environment;
import com.mokatest.platform.demos.api.domain.GlobalVar;
import com.mokatest.platform.demos.api.mapper.ApiRequestMapper;
import com.mokatest.platform.demos.api.mapper.EnvironmentMapper;
import com.mokatest.platform.demos.api.mapper.GlobalVarMapper;
import com.mokatest.platform.demos.domain.ui.*;
import com.mokatest.platform.demos.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mokatest.platform.demos.domain.ui.TeamMember;
import com.mokatest.platform.demos.mapper.TeamMemberMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 项目权限校验器
 *
 * 支持两种权限模型：
 * 1. 基于团队角色的 RBAC 权限校验（推荐）：通过 role + permission + role_permission 表判断用户是否拥有某权限。
 * 2. 项目创建者/团队成员校验（兼容旧逻辑）：项目创建者或团队成员默认有权限。
 *
 * 权限校验入口：
 * - hasPermission(teamId, loginId, permissionCode)：通过团队ID校验权限
 * - hasPermissionByProjectId(projectId, loginId, permissionCode)：通过项目ID校验权限
 * - isTeamAdmin(teamId, loginId)：判断是否为团队管理员
 * - hasPermissionByXxxId(xxxId, loginId)：通过具体资源ID追溯到项目/团队后校验成员身份
 *
 * 注意：admin 角色默认拥有所有权限，无需在 role_permission 中配置。
 */
@Component
public class ProjectPermissionChecker {

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private SceneMapper sceneMapper;

    @Autowired
    private PlanMapper planMapper;

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private TestStepMapper testStepMapper;

    @Autowired
    private ElementMapper elementMapper;

    @Autowired
    private ApiRequestMapper apiRequestMapper;

    @Autowired
    private EnvironmentMapper environmentMapper;

    @Autowired
    private GlobalVarMapper globalVarMapper;

    @Autowired
    private PlanWebhookMapper planWebhookMapper;

    @Autowired
    private TeamMemberMapper teamMemberMapper;

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private TeamMapper teamMapper;

    @Autowired
    private UserRoleMapper userRoleMapper;

    // ==================== 超管判断 ====================

    /**
     * 判断用户是否为超级管理员
     * 超级管理员通过 user.role = 'super_admin' 标识，不受团队/项目权限限制，拥有系统内所有权限
     *
     * @param loginId 登录用户ID
     * @return true 表示是超级管理员
     */
    public boolean isSuperAdmin(String loginId) {
        if (loginId == null) {
            return false;
        }
        try {
            User user = userMapper.selectById(Long.valueOf(loginId));
            return user != null && "super_admin".equals(user.getRole());
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 判断团队是否处于正常状态（status = 1）
     * 团队被禁用时，除超级管理员外所有成员失去该团队下的访问权限
     */
    private boolean isTeamActive(Integer teamId) {
        if (teamId == null) {
            return false;
        }
        Team team = teamMapper.selectById(teamId);
        return team != null && team.getStatus() != null && team.getStatus() == 1;
    }

    // ==================== 基础校验：通过 projectId ====================

    /**
     * 直接通过项目ID校验权限
     * 规则：项目创建者 或 项目所属团队成员 均有权限；团队被禁用时仅超管可访问
     */
    public boolean hasPermissionByProjectId(Integer projectId, String loginId) {
        if (projectId == null || loginId == null) {
            return false;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return false;
        }
        // 团队被禁用时，仅超级管理员可访问
        if (!isTeamActive(project.getTeamId()) && !isSuperAdmin(loginId)) {
            return false;
        }
        // 1. 项目创建者
        if (loginId.equals(project.getCreateUserId())) {
            return true;
        }
        // 2. 项目负责人
        if (project.getOwnerId() != null && loginId.equals(project.getOwnerId().toString())) {
            return true;
        }
        // 3. 项目所属团队成员
        return isTeamMember(project.getTeamId(), loginId);
    }

    /**
     * 通过项目ID校验指定 RBAC 权限
     * 规则：先定位到项目所属团队，再校验用户在该团队的角色是否拥有指定权限
     *
     * @param projectId      项目ID
     * @param loginId        登录用户ID
     * @param permissionCode 权限编码，如 qa:bug:delete
     * @return 是否有权限
     */
    public boolean hasPermissionByProjectId(Integer projectId, String loginId, String permissionCode) {
        if (projectId == null || loginId == null || permissionCode == null) {
            return false;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return false;
        }
        // 按「团队 + 项目」精确解析：超管/团队管理员/项目管理员(owner_id)/模板成员各自命中
        return getPermissionList(project.getTeamId(), projectId, loginId).contains(permissionCode);
    }

    /**
     * 直接通过项目ID校验权限（String 类型的 projectId）
     */
    public boolean hasPermissionByProjectId(String projectId, String loginId) {
        if (projectId == null || loginId == null) {
            return false;
        }
        try {
            return hasPermissionByProjectId(Integer.valueOf(projectId), loginId);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 通过项目ID校验指定 RBAC 权限（别名，兼容 Controller 中的调用）
     *
     * @param projectId      项目ID
     * @param loginId        登录用户ID
     * @param permissionCode 权限编码
     * @return 是否有权限
     */
    public boolean hasProjectPermission(Integer projectId, String loginId, String permissionCode) {
        return hasPermissionByProjectId(projectId, loginId, permissionCode);
    }

    /**
     * 通过项目ID反查团队ID
     * 供团队上下文拦截器使用，当请求只带了 projectId 时自动定位团队
     *
     * @param projectId 项目ID
     * @return 团队ID，项目不存在或 projectId 为空时返回 null
     */
    public Integer getTeamIdByProjectId(Integer projectId) {
        if (projectId == null) {
            return null;
        }
        Project project = projectMapper.selectById(projectId);
        return project != null ? project.getTeamId() : null;
    }

    // ==================== 通过场景ID ====================

    /**
     * 通过场景ID校验权限
     */
    public boolean hasPermissionBySceneId(Integer sceneId, String loginId) {
        if (sceneId == null || loginId == null) {
            return false;
        }
        Scene scene = sceneMapper.selectById(sceneId);
        if (scene == null || scene.getProjectId() == null) {
            return false;
        }
        return hasPermissionByProjectId(scene.getProjectId(), loginId);
    }

    // ==================== 通过计划ID ====================

    /**
     * 通过计划ID校验权限
     */
    public boolean hasPermissionByPlanId(Integer planId, String loginId) {
        if (planId == null || loginId == null) {
            return false;
        }
        Plan plan = planMapper.selectById(planId);
        if (plan == null || plan.getProjectId() == null) {
            return false;
        }
        return hasPermissionByProjectId(plan.getProjectId(), loginId);
    }

    // ==================== 通过报告ID ====================

    /**
     * 通过报告ID校验权限
     */
    public boolean hasPermissionByReportId(Integer reportId, String loginId) {
        if (reportId == null || loginId == null) {
            return false;
        }
        Report report = reportMapper.selectById(reportId);
        if (report == null || report.getProjectId() == null) {
            return false;
        }
        return hasPermissionByProjectId(report.getProjectId(), loginId);
    }

    // ==================== 通过步骤ID ====================

    /**
     * 通过步骤ID校验权限
     */
    public boolean hasPermissionByStepId(Integer stepId, String loginId) {
        if (stepId == null || loginId == null) {
            return false;
        }
        TestStep step = testStepMapper.selectById(stepId);
        if (step == null) {
            return false;
        }
        // 优先使用步骤上的 projectId
        if (step.getProjectId() != null) {
            return hasPermissionByProjectId(step.getProjectId(), loginId);
        }
        // 回退到通过 scenarioId 查找场景再获取 projectId
        String scenarioId = step.getScenarioId();
        if (scenarioId != null && !scenarioId.isEmpty()) {
            try {
                return hasPermissionBySceneId(Integer.valueOf(scenarioId), loginId);
            } catch (NumberFormatException ignored) {
            }
        }
        return false;
    }

    // ==================== 通过元素ID ====================

    /**
     * 通过元素ID校验权限
     */
    public boolean hasPermissionByElementId(Integer elementId, String loginId) {
        if (elementId == null || loginId == null) {
            return false;
        }
        Element element = elementMapper.selectById(elementId);
        if (element == null || element.getProjectId() == null) {
            return false;
        }
        return hasPermissionByProjectId(element.getProjectId(), loginId);
    }

    // ==================== 通过API请求ID ====================

    /**
     * 通过API请求ID校验权限
     */
    public boolean hasPermissionByApiRequestId(Integer apiRequestId, String loginId) {
        if (apiRequestId == null || loginId == null) {
            return false;
        }
        ApiRequest apiRequest = apiRequestMapper.selectById(apiRequestId);
        if (apiRequest == null) {
            return false;
        }
        // 优先使用 projectId
        if (apiRequest.getProjectId() != null) {
            return hasPermissionByProjectId(apiRequest.getProjectId(), loginId);
        }
        // 回退到 teamId
        if (apiRequest.getTeamId() != null) {
            return hasTeamPermission(apiRequest.getTeamId(), loginId);
        }
        return false;
    }

    // ==================== 通过环境ID ====================

    /**
     * 通过环境ID校验权限
     */
    public boolean hasPermissionByEnvironmentId(Integer environmentId, String loginId) {
        if (environmentId == null || loginId == null) {
            return false;
        }
        Environment env = environmentMapper.selectById(environmentId);
        if (env == null || env.getTeamId() == null) {
            return false;
        }
        return hasTeamPermission(env.getTeamId(), loginId);
    }

    // ==================== 通过全局变量ID ====================

    /**
     * 通过全局变量ID校验权限
     */
    public boolean hasPermissionByGlobalVarId(Integer globalVarId, String loginId) {
        if (globalVarId == null || loginId == null) {
            return false;
        }
        GlobalVar globalVar = globalVarMapper.selectById(globalVarId);
        if (globalVar == null || globalVar.getTeamId() == null) {
            return false;
        }
        return hasTeamPermission(globalVar.getTeamId(), loginId);
    }

    // ==================== 通过WebhookID ====================

    /**
     * 通过Webhook配置ID校验权限
     */
    public boolean hasPermissionByPlanWebhookId(Integer planWebhookId, String loginId) {
        if (planWebhookId == null || loginId == null) {
            return false;
        }
        PlanWebhook webhook = planWebhookMapper.selectById(planWebhookId);
        if (webhook == null || webhook.getProjectId() == null) {
            return false;
        }
        return hasPermissionByProjectId(webhook.getProjectId(), loginId);
    }

    /**
     * 检查用户是否属于指定团队（查 team_member 表）
     * 团队被禁用时返回 false，确保禁用后成员无法访问团队资源
     */
    public boolean isTeamMember(Integer teamId, String loginId) {
        if (teamId == null || loginId == null) {
            return false;
        }
        if (!isTeamActive(teamId)) {
            return false;
        }
        QueryWrapper<TeamMember> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("team_id", teamId)
                .eq("user_id", loginId)
                .eq("status", 1);
        return teamMemberMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 获取用户在指定团队的角色编码
     * 团队被禁用时返回 null，确保禁用后所有基于角色的权限校验失败
     * 团队管理员唯一来源是 team.owner_id（team_member 的 team_admin/admin 角色路径已废弃）：
     * owner 直接返回 team_admin；历史 member 记录里的 admin/team_admin 降级为 team_member
     */
    public String getUserRoleCode(Integer teamId, String loginId) {
        if (teamId == null || loginId == null) {
            return null;
        }
        // 被禁用的用户无任何角色/权限（兜底，禁用时会同时踢出会话）
        User loginUser = userMapper.selectById(Long.valueOf(loginId));
        if (loginUser == null || (loginUser.getStatus() != null && loginUser.getStatus() != 1)) {
            return null;
        }
        if (!isTeamActive(teamId)) {
            return null;
        }
        Team team = teamMapper.selectById(teamId);
        if (team != null && team.getOwnerId() != null
                && loginId.equals(team.getOwnerId().toString())) {
            return "team_admin";
        }
        QueryWrapper<TeamMember> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("team_id", teamId)
                .eq("user_id", loginId)
                .eq("status", 1);
        TeamMember member = teamMemberMapper.selectOne(queryWrapper);
        if (member == null) {
            return null;
        }
        String code = null;
        // 优先从 role_id 关联 role 表获取
        if (member.getRoleId() != null) {
            Role role = roleMapper.selectById(member.getRoleId());
            if (role != null) {
                code = role.getCode();
            }
        }
        // 兼容旧字段
        if (code == null) {
            code = member.getRole();
        }
        // 历史 admin/team_admin 成员角色不再代表管理员（管理员唯一来源是 team.owner_id）
        if ("admin".equals(code) || "team_admin".equals(code)) {
            return "team_member";
        }
        return code;
    }

    /**
     * 检查用户是否为指定团队的管理员
     */
    public boolean isTeamAdmin(Integer teamId, String loginId) {
        String code = getUserRoleCode(teamId, loginId);
        return "admin".equals(code) || "team_admin".equals(code);
    }

    /**
     * 获取用户在指定团队下的所有权限编码列表
     * 供 SaToken 的 StpInterface 实现类使用，支持 @SaCheckPermission 注解鉴权
     *
     * @param teamId  团队ID
     * @param loginId 登录用户ID
     * @return 权限编码列表；用户不在团队或角色无权限时返回空列表
     */
    public List<String> getPermissionList(Integer teamId, String loginId) {
        return getPermissionList(teamId, null, loginId);
    }

    /**
     * 获取用户在「团队 + 指定项目」上下文下的权限编码列表。
     *
     * 结果 = 团队级权限 ∪ 当前项目级权限：
     *   超级管理员：系统全部权限；
     *   团队管理员：所有非 platform 权限（含团队管理 + 所有项目级权限）；
     *   项目管理员（仅对 projectId 指定的项目）：该项目的全部项目级权限（不含 platform/team）；
     *   普通成员：团队角色自身权限 ∪ 当前项目模板的执行类权限（仅该项目，不跨项目）；
     *   未指定 projectId（团队级页面上下文）：回退为该用户在本团队下所有项目模板权限的并集。
     */
    public List<String> getPermissionList(Integer teamId, Integer projectId, String loginId) {
        if (loginId == null) {
            return new ArrayList<>();
        }
        // 超级管理员拥有所有权限，不受 teamId 限制
        if (isSuperAdmin(loginId)) {
            QueryWrapper<Permission> permQw = new QueryWrapper<>();
            permQw.select("code");
            return permissionMapper.selectList(permQw).stream()
                    .map(Permission::getCode)
                    .filter(code -> code != null)
                    .collect(Collectors.toList());
        }
        if (teamId == null) {
            return new ArrayList<>();
        }
        String roleCode = getUserRoleCode(teamId, loginId);
        // 团队管理员默认拥有所有团队级+项目级权限（不含 platform: 前缀的平台级权限）
        if ("admin".equals(roleCode) || "team_admin".equals(roleCode)) {
            QueryWrapper<Permission> permQw = new QueryWrapper<>();
            permQw.select("code").notLike("code", "platform:%");
            return permissionMapper.selectList(permQw).stream()
                    .map(Permission::getCode)
                    .filter(code -> code != null)
                    .collect(Collectors.toList());
        }

        Set<String> codes = new HashSet<>();
        // 1) 团队级角色自身的权限（team_member 门票角色无业务权限；兼容旧的自定义团队角色）
        if (roleCode != null) {
            QueryWrapper<Role> roleQw = new QueryWrapper<>();
            roleQw.eq("code", roleCode);
            roleQw.and(w -> w.isNull("team_id").or().eq("team_id", teamId));
            Role role = roleMapper.selectOne(roleQw);
            if (role != null) {
                QueryWrapper<RolePermission> rpQw = new QueryWrapper<>();
                rpQw.eq("role_id", role.getId());
                List<RolePermission> rpList = rolePermissionMapper.selectList(rpQw);
                if (rpList != null && !rpList.isEmpty()) {
                    List<Long> permissionIds = rpList.stream()
                            .map(RolePermission::getPermissionId)
                            .collect(Collectors.toList());
                    QueryWrapper<Permission> permQw = new QueryWrapper<>();
                    permQw.in("id", permissionIds);
                    permissionMapper.selectList(permQw).stream()
                            .map(Permission::getCode)
                            .filter(code -> code != null)
                            .forEach(codes::add);
                }
            }
        }

        Long loginIdLong = Long.valueOf(loginId);
        if (projectId != null) {
            // 2a) 指定了项目：只解析该项目的权限，做到项目间清晰区分
            // 项目管理员的唯一来源是 project.owner_id（user_role 的 project_admin 旧路径已废弃）
            Project proj = projectMapper.selectById(projectId);
            boolean isOwner = proj != null && proj.getOwnerId() != null
                    && loginId.equals(proj.getOwnerId().toString());
            if (isOwner) {
                // 项目管理员：该项目的全部项目级权限（排除 platform / team 级）
                QueryWrapper<Permission> permQw = new QueryWrapper<>();
                permQw.select("code")
                        .notLike("code", "platform:%").ne("code", "platform")
                        .notLike("code", "team:%").ne("code", "team");
                permissionMapper.selectList(permQw).stream()
                        .map(Permission::getCode)
                        .filter(code -> code != null)
                        .forEach(codes::add);
            } else {
                Set<String> tpl = userRoleMapper.getProjectTemplatePermissionCodes(loginIdLong, projectId.longValue());
                if (tpl != null) {
                    codes.addAll(tpl);
                }
            }
        } else {
            // 2b) 未指定项目（团队级页面上下文）：回退为本团队下所有项目模板权限并集
            Set<String> tpl = userRoleMapper.getTeamTemplatePermissionCodes(loginIdLong, (long) teamId);
            if (tpl != null) {
                codes.addAll(tpl);
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * 获取系统中所有权限编码列表
     * 供超级管理员 bypass 鉴权时使用
     */
    public List<String> getAllPermissionCodes() {
        QueryWrapper<Permission> permQw = new QueryWrapper<>();
        permQw.select("code");
        return permissionMapper.selectList(permQw).stream()
                .map(Permission::getCode)
                .filter(code -> code != null)
                .collect(Collectors.toList());
    }

    /**
     * 检查用户是否有指定权限
     */
    public boolean hasPermission(Integer teamId, String loginId, String permissionCode) {
        if (loginId == null || permissionCode == null) {
            return false;
        }
        // 超级管理员 bypass 所有权限校验
        if (isSuperAdmin(loginId)) {
            return true;
        }
        if (teamId == null) {
            return false;
        }
        String roleCode = getUserRoleCode(teamId, loginId);
        if (roleCode == null) {
            return false;
        }
        // 团队管理员拥有所有团队级权限（不含 platform: 前缀的平台级权限）
        if ("admin".equals(roleCode) || "team_admin".equals(roleCode)) {
            return !permissionCode.startsWith("platform:");
        }
        // 查询角色ID
        QueryWrapper<Role> roleQw = new QueryWrapper<>();
        roleQw.eq("code", roleCode);
        roleQw.and(w -> w.isNull("team_id").or().eq("team_id", teamId));
        Role role = roleMapper.selectOne(roleQw);
        if (role == null) {
            return false;
        }
        // 查询权限ID
        QueryWrapper<Permission> permQw = new QueryWrapper<>();
        permQw.eq("code", permissionCode);
        Permission permission = permissionMapper.selectOne(permQw);
        if (permission == null) {
            return false;
        }
        // 查询角色权限关联
        QueryWrapper<RolePermission> rpQw = new QueryWrapper<>();
        rpQw.eq("role_id", role.getId()).eq("permission_id", permission.getId());
        return rolePermissionMapper.selectCount(rpQw) > 0;
    }

    // ==================== Team 级别权限校验 ====================

    /**
     * 检查用户是否属于指定团队（直接查 team_member 表）
     */
    public boolean hasTeamPermission(Integer teamId, String loginId) {
        return isTeamMember(teamId, loginId);
    }
}
