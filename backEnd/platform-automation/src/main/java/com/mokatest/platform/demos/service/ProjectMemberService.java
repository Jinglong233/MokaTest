package com.mokatest.platform.demos.service;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.*;
import com.mokatest.platform.demos.mapper.*;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 项目成员管理服务
 */
@Service
public class ProjectMemberService {

    @Resource
    private UserRoleMapper userRoleMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ProjectMapper projectMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private ProjectPermissionChecker projectPermissionChecker;

    /**
     * 获取项目成员列表（含项目角色）
     */
    public SaResult getProjectMembers(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return SaResult.error("项目不存在");
        }
        // 可见性校验：仅本项目成员（项目管理员 owner / 模板授权成员）可见；
        // 超管、项目所属团队的团队管理员天然放行（项目创建者不持有特殊身份）
        String loginId = StpUtil.getLoginIdAsString();
        boolean exempt = projectPermissionChecker.isSuperAdmin(loginId)
                || projectPermissionChecker.isTeamAdmin(project.getTeamId(), loginId)
                || (project.getOwnerId() != null && loginId.equals(project.getOwnerId().toString()));
        if (!exempt) {
            QueryWrapper<UserRole> memberQw = new QueryWrapper<>();
            memberQw.eq("scope_id", projectId)
                    .eq("user_id", loginId)
                    .eq("status", 1);
            if (userRoleMapper.selectCount(memberQw) <= 0) {
                return SaResult.error("您不是该项目成员，无权查看项目成员列表");
            }
        }
        Team team = teamMapper.selectById(project.getTeamId());
        Integer teamId = team != null ? team.getId() : null;

        QueryWrapper<UserRole> wrapper = new QueryWrapper<>();
        wrapper.eq("scope_id", projectId)
                .eq("status", 1);
        List<UserRole> userRoles = userRoleMapper.selectList(wrapper);

        List<ProjectMemberVO> result = new ArrayList<>();
        // 按 userId 去重：同一用户在该项目下可能有多条授权，仅展示一条（项目管理员优先）
        java.util.LinkedHashMap<Long, ProjectMemberVO> memberMap = new java.util.LinkedHashMap<>();
        for (UserRole ur : userRoles) {
            Role role = roleMapper.selectById(ur.getRoleId());
            if (role == null) {
                continue;
            }
            // 只纳入项目级自定义模板（TEMPLATE）授权；
            // 团队角色(team_admin/team_member)的 scope_id 是 teamId，可能与 projectId 数值相同而被误命中，需排除；
            // SYSTEM project_admin 旧授权路径已废弃（项目管理员唯一来源是 project.owner_id），不再视为项目成员
            boolean projectLevel = "TEMPLATE".equals(role.getScopeType());
            if (!projectLevel) {
                continue;
            }
            User user = userMapper.selectById(ur.getUserId());
            if (user == null) {
                continue;
            }
            // 超管、当前项目所属团队的团队管理员不纳入项目成员列表：
            // 他们的项目权限是隐式拥有的（不依赖项目级授权），列出会误导成员管理；
            // 注意团队管理员按本项目所属团队(teamId)判断，其他团队的管理员身份不影响；
            // 查询时动态判断，用户后续被提升为团队管理员后会自动从列表消失
            Long uid = ur.getUserId();
            if ("super_admin".equals(user.getRole())
                    || projectPermissionChecker.isTeamAdmin(teamId, String.valueOf(uid))) {
                continue;
            }
            // 同一用户可能有多条模板授权，仅展示一条
            if (memberMap.containsKey(uid)) {
                continue;
            }
            ProjectMemberVO vo = new ProjectMemberVO();
            vo.setUserId(uid);
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setRoleId(role.getId());
            vo.setRoleName(role.getName());
            vo.setRoleCode(role.getCode());
            vo.setExpireTime(ur.getExpireTime());
            vo.setTeamAdmin(projectPermissionChecker.isTeamAdmin(teamId, String.valueOf(uid)));
            vo.setProjectCreator(String.valueOf(uid).equals(project.getCreateUserId()));
            vo.setTeamCreator(team != null && String.valueOf(uid).equals(team.getCreateUserId()));
            memberMap.put(uid, vo);
        }
        result.addAll(memberMap.values());

        // 项目管理员（project.owner_id）在成员列表中显示为项目管理员
        // （owner 若在列表中但只有模板角色，升级为项目管理员展示；不在列表则兜底加入）
        // 注意：owner 即使是团队管理员也照常展示（他是被显式指定的项目管理员）；
        // 仅团队管理员而非 owner 的人仍不展示（上面的成员循环已豁免）；超管一律不展示
        Long ownerId = project.getOwnerId();
        if (ownerId != null) {
            boolean ownerExempt = projectPermissionChecker.isSuperAdmin(String.valueOf(ownerId));
            if (!ownerExempt) {
                ProjectMemberVO ownerVo = result.stream()
                        .filter(vo -> ownerId.equals(vo.getUserId()))
                        .findFirst().orElse(null);
                if (ownerVo != null && !"project_admin".equals(ownerVo.getRoleCode())) {
                    Role projectAdminRole = roleMapper.selectOne(
                            new QueryWrapper<Role>()
                                    .eq("code", "project_admin")
                                    .eq("scope_type", "SYSTEM")
                                    .last("LIMIT 1")
                    );
                    if (projectAdminRole != null) {
                        ownerVo.setRoleId(projectAdminRole.getId());
                        ownerVo.setRoleName(projectAdminRole.getName());
                        ownerVo.setRoleCode(projectAdminRole.getCode());
                    }
                    ownerVo.setProjectOwner(true);
                } else if (ownerVo != null) {
                    ownerVo.setProjectOwner(true);
                } else {
                    User owner = userMapper.selectById(ownerId);
                    if (owner != null) {
                        Role projectAdminRole = roleMapper.selectOne(
                                new QueryWrapper<Role>()
                                        .eq("code", "project_admin")
                                        .eq("scope_type", "SYSTEM")
                                        .last("LIMIT 1")
                        );
                        ProjectMemberVO vo = new ProjectMemberVO();
                        vo.setUserId(ownerId);
                        vo.setUsername(owner.getUsername());
                        vo.setNickname(owner.getNickname());
                        vo.setRoleId(projectAdminRole != null ? projectAdminRole.getId() : null);
                        vo.setRoleName(projectAdminRole != null ? projectAdminRole.getName() : "项目管理员");
                        vo.setRoleCode(projectAdminRole != null ? projectAdminRole.getCode() : "project_admin");
                        vo.setTeamAdmin(projectPermissionChecker.isTeamAdmin(teamId, String.valueOf(ownerId)));
                        vo.setProjectCreator(String.valueOf(ownerId).equals(project.getCreateUserId()));
                        vo.setTeamCreator(team != null && String.valueOf(ownerId).equals(team.getCreateUserId()));
                        vo.setProjectOwner(true);
                        result.add(vo);
                    }
                }
            }
        }

        // 项目管理员排在最前，其余按用户ID排序
        result.sort((a, b) -> {
            boolean aAdmin = "project_admin".equals(a.getRoleCode());
            boolean bAdmin = "project_admin".equals(b.getRoleCode());
            if (aAdmin != bAdmin) {
                return aAdmin ? -1 : 1;
            }
            Long aId = a.getUserId() != null ? a.getUserId() : 0L;
            Long bId = b.getUserId() != null ? b.getUserId() : 0L;
            return aId.compareTo(bId);
        });

        return SaResult.ok().setData(result);
    }

    /**
     * 变更项目管理员（项目管理员的唯一来源是 project.owner_id）。
     * 旧管理员若在本项目没有任何有效授权，自动降级为内置只读模板「项目成员」（project_member），
     * 保留只读身份而非被剔除。
     */
    @Transactional
    public SaResult changeProjectOwner(Integer projectId, Long newOwnerId, String operatorId) {
        if (projectId == null || newOwnerId == null) {
            return SaResult.error("缺少参数");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return SaResult.error("项目不存在");
        }
        Long oldOwnerId = project.getOwnerId();
        if (newOwnerId.equals(oldOwnerId)) {
            return SaResult.ok("该用户已是项目管理员");
        }
        project.setOwnerId(newOwnerId);
        project.setUpdateUserId(operatorId);
        project.setUpdatedAt(new Date());
        projectMapper.updateById(project);
        demotePreviousOwnerIfNoRole(projectId, oldOwnerId, newOwnerId, operatorId);
        return SaResult.ok("变更成功");
    }

    /**
     * 项目管理员变更（A→B）后，旧管理员 A 若在本项目没有任何有效授权，
     * 自动降级为内置只读模板「项目成员」（project_member），保留只读身份而非被剔除。
     */
    public void demotePreviousOwnerIfNoRole(Integer projectId, Long oldOwnerId, Long newOwnerId, String operatorId) {
        if (oldOwnerId == null || oldOwnerId.equals(newOwnerId)) {
            return;
        }
        QueryWrapper<UserRole> urQw = new QueryWrapper<>();
        urQw.eq("scope_id", projectId)
                .eq("user_id", oldOwnerId)
                .eq("status", 1);
        if (userRoleMapper.selectCount(urQw) > 0) {
            return;
        }
        Role memberRole = roleMapper.selectOne(new QueryWrapper<Role>()
                .eq("code", "project_member")
                .eq("scope_type", "TEMPLATE")
                .isNull("team_id")
                .last("LIMIT 1"));
        if (memberRole == null) {
            return;
        }
        UserRole ur = new UserRole();
        ur.setUserId(oldOwnerId);
        ur.setRoleId(memberRole.getId());
        ur.setScopeId(Long.valueOf(projectId));
        ur.setGrantedBy(operatorId != null ? Long.valueOf(operatorId) : null);
        ur.setStatus(1);
        userRoleMapper.insert(ur);
    }

    /**
     * 分配项目角色
     */
    @Transactional
    public SaResult assignProjectRole(Integer projectId, Long userId, Long roleId, Date expireTime) {
        if (projectId == null || userId == null || roleId == null) {
            return SaResult.error("缺少参数");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return SaResult.error("项目不存在");
        }
        // 非超管不能给自己分配项目角色（防止项目管理员/团队管理员自我提权）
        String loginId = StpUtil.getLoginIdAsString();
        if (!projectPermissionChecker.isSuperAdmin(loginId)
                && loginId.equals(String.valueOf(userId))) {
            return SaResult.error("不能给自己分配项目角色");
        }
        // 用户必须是团队成员
        QueryWrapper<TeamMember> tmWrapper = new QueryWrapper<>();
        tmWrapper.eq("team_id", project.getTeamId())
                .eq("user_id", userId)
                .eq("status", 1);
        if (teamMemberMapper.selectCount(tmWrapper) <= 0) {
            return SaResult.error("该用户未加入项目所属团队，请先邀请加入团队");
        }
        // 项目负责人 ≡ 项目管理员，其权限来自 project.owner_id，分配项目角色无意义
        if (project.getOwnerId() != null && project.getOwnerId().equals(userId)) {
            return SaResult.error("该用户已是项目管理员，无需分配角色");
        }

        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            return SaResult.error("角色不存在");
        }
        boolean isTemplate = "TEMPLATE".equals(role.getScopeType());
        // 项目 scope 下只允许分配自定义模板(TEMPLATE)；
        // 项目管理员唯一来源是 project.owner_id（项目设置中变更），user_role 的 project_admin 旧路径已废弃
        if (!isTemplate) {
            return SaResult.error("只能分配自定义模板角色；项目管理员请在项目设置中变更");
        }
        // 自定义模板需校验范围：全局模板（scopeId=null）或本项目模板（scopeId=projectId）
        if (role.getScopeId() != null && !role.getScopeId().equals(projectId.longValue())) {
            return SaResult.error("不能分配其他项目的模板");
        }

        // 删除该用户在该项目下的同角色旧记录（如果有）
        QueryWrapper<UserRole> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("role_id", roleId)
                .eq("scope_id", projectId);
        userRoleMapper.delete(wrapper);

        UserRole userRole = new UserRole();
        userRole.setUserId(userId);
        userRole.setRoleId(roleId);
        userRole.setScopeId(Long.valueOf(projectId));
        userRole.setGrantedBy(Long.valueOf(StpUtil.getLoginIdAsString()));
        userRole.setExpireTime(expireTime);
        userRole.setStatus(1);
        userRoleMapper.insert(userRole);
        return SaResult.ok("分配成功");
    }

    /**
     * 移除项目成员（移除该用户在该项目下的所有项目角色）
     */
    @Transactional
    public SaResult removeProjectMember(Integer projectId, Long userId) {
        if (projectId == null || userId == null) {
            return SaResult.error("缺少参数");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return SaResult.error("项目不存在");
        }
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.equals(String.valueOf(userId))) {
            return SaResult.error("不能移除自己");
        }
        if (projectPermissionChecker.isSuperAdmin(String.valueOf(userId))) {
            return SaResult.error("不能移除超级管理员");
        }
        if (projectPermissionChecker.isTeamAdmin(project.getTeamId(), String.valueOf(userId))) {
            return SaResult.error("团队管理员不能从项目中移除");
        }
        // 团队创建者不能从所属项目中被移除
        Team team = teamMapper.selectById(project.getTeamId());
        if (team != null && String.valueOf(userId).equals(team.getCreateUserId())) {
            return SaResult.error("团队创建者不能从项目中移除");
        }
        // 项目管理员（owner）是项目维度唯一的管理者身份，需先变更负责人才能移除；
        // 项目创建者不持有特殊身份，可正常移除
        if (project.getOwnerId() != null && project.getOwnerId().equals(userId)) {
            return SaResult.error("项目管理员不能从项目中移除，请先在项目设置中变更项目管理员");
        }
        QueryWrapper<UserRole> wrapper = new QueryWrapper<>();
        wrapper.eq("user_id", userId)
                .eq("scope_id", projectId);
        userRoleMapper.delete(wrapper);
        return SaResult.ok("移除成功");
    }

    /**
     * 项目可分配的角色选项（自定义模板）。
     * 供邀请/分配角色弹窗使用；按项目鉴权：项目管理员(owner)、团队管理员、超管可用，
     * 或拥有项目成员管理类权限的用户可用。
     */
    public SaResult getProjectRoleOptions(Integer projectId) {
        if (projectId == null) {
            return SaResult.error("缺少项目ID");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return SaResult.error("项目不存在");
        }
        String loginId = StpUtil.getLoginIdAsString();
        boolean allowed = projectPermissionChecker.isSuperAdmin(loginId)
                || projectPermissionChecker.isTeamAdmin(project.getTeamId(), loginId)
                || projectPermissionChecker.hasProjectPermission(projectId, loginId, "project:member:manage")
                || projectPermissionChecker.hasProjectPermission(projectId, loginId, "project:member:create")
                || projectPermissionChecker.hasProjectPermission(projectId, loginId, "project:member:update");
        if (!allowed) {
            return SaResult.error("无权限查看项目角色选项");
        }
        // 仅自定义模板角色（项目管理员唯一来源是 project.owner_id，不在此分配）；
        // 模板按团队隔离：本团队的模板 + 平台级模板（team_id 为空）
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        wrapper.eq("scope_type", "TEMPLATE")
                .and(w -> w.isNull("team_id").or().eq("team_id", project.getTeamId()))
                .orderByAsc("create_time");
        return SaResult.ok().setData(roleMapper.selectList(wrapper));
    }

    /**
     * 项目成员 VO
     */
    public static class ProjectMemberVO {
        private Long userId;
        private String username;
        private String nickname;
        private Long roleId;
        private String roleName;
        private String roleCode;
        private Date expireTime;
        private Boolean teamAdmin;
        private Boolean teamCreator;
        private Boolean projectCreator;
        private Boolean projectOwner;

        // getters / setters
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Long getRoleId() { return roleId; }
        public void setRoleId(Long roleId) { this.roleId = roleId; }
        public String getRoleName() { return roleName; }
        public void setRoleName(String roleName) { this.roleName = roleName; }
        public String getRoleCode() { return roleCode; }
        public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
        public Date getExpireTime() { return expireTime; }
        public void setExpireTime(Date expireTime) { this.expireTime = expireTime; }
        public Boolean getTeamAdmin() { return teamAdmin; }
        public void setTeamAdmin(Boolean teamAdmin) { this.teamAdmin = teamAdmin; }
        public Boolean getTeamCreator() { return teamCreator; }
        public void setTeamCreator(Boolean teamCreator) { this.teamCreator = teamCreator; }
        public Boolean getProjectCreator() { return projectCreator; }
        public void setProjectCreator(Boolean projectCreator) { this.projectCreator = projectCreator; }
        public Boolean getProjectOwner() { return projectOwner; }
        public void setProjectOwner(Boolean projectOwner) { this.projectOwner = projectOwner; }
    }
}
