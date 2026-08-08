package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.*;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.TeamMapper;
import com.mokatest.platform.demos.mapper.TeamMemberMapper;
import com.mokatest.platform.demos.mapper.RoleMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.mapper.UserRoleMapper;
import com.mokatest.platform.demos.service.ProjectMemberService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import com.mokatest.platform.demos.service.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: JingLong
 * @description 针对表【project】的数据库操作Service实现
 * @createDate 2025-09-13 11:42:28
 */
@Service
public class ProjectServiceImpl extends ServiceImpl<ProjectMapper, Project> implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;


    @Resource
    private UserMapper userMapper;

    @Resource
    private ProjectPermissionChecker permissionChecker;

    @Resource
    private UserRoleMapper userRoleMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private ProjectMemberService projectMemberService;

    @Override
    public List<Project> allProject() {
        QueryWrapper<Project> queryWrapper = new QueryWrapper<>();
        // 根据创建时间降序
        queryWrapper.orderByDesc("created_at");
        // 获取登录用户id
        String loginId = StpUtil.getLoginIdAsString();

        // 超管：返回全部项目
        if (permissionChecker.isSuperAdmin(loginId)) {
            return projectMapper.selectList(queryWrapper);
        }

        // 普通用户/团队管理员：收集自己创建的、负责的、通过 user_role 授权参与的项目ID
        Long loginUserId = Long.valueOf(loginId);
        QueryWrapper<UserRole> urWrapper = new QueryWrapper<>();
        urWrapper.eq("user_id", loginId).eq("status", 1)
                .and(w -> w.isNull("expire_time").or().gt("expire_time", new Date()));
        Set<Integer> scopeProjectIds = userRoleMapper.selectList(urWrapper).stream()
                .map(UserRole::getScopeId)
                .filter(id -> id != null)
                .map(Long::intValue)
                .collect(Collectors.toSet());

        // 查询用户是团队管理员的团队ID（团队管理员唯一来源是 team.owner_id），团队管理员应能看到对应团队的全部项目
        Set<Integer> adminTeamIds = new HashSet<>();
        List<Team> ownedTeams = teamMapper.selectList(
                new QueryWrapper<Team>().eq("owner_id", loginUserId));
        for (Team t : ownedTeams) {
            if (t.getId() != null) {
                adminTeamIds.add(t.getId());
            }
        }

        if (!adminTeamIds.isEmpty()) {
            queryWrapper.and(w -> w.eq("create_user_id", loginId)
                    .or().eq("owner_id", loginUserId)
                    .or(!scopeProjectIds.isEmpty(), q -> q.in("id", scopeProjectIds))
                    .or(q -> q.in("team_id", adminTeamIds)));
        } else {
            queryWrapper.and(w -> w.eq("create_user_id", loginId)
                    .or().eq("owner_id", loginUserId)
                    .or(!scopeProjectIds.isEmpty(), q -> q.in("id", scopeProjectIds)));
        }
        List<Project> projects = projectMapper.selectList(queryWrapper);
        return projects;
    }

    @Override
    public Boolean addProject(Project project) {
        if (project == null) {
            throw new ParamIsEmptyException("缺少项目信息");
        }
        // 个人团队最多创建 1 个项目（个人空间仅用于单项目/试用，多项目请创建正式团队）
        Integer teamId = project.getTeamId();
        if (teamId != null) {
            Team team = teamMapper.selectById(teamId);
            if (team != null && Integer.valueOf(1).equals(team.getIsPersonal())) {
                Long count = projectMapper.selectCount(
                        new QueryWrapper<Project>().eq("team_id", teamId));
                if (count != null && count >= 1) {
                    throw new BusinessException("个人团队最多创建 1 个项目，如需更多请创建正式团队");
                }
            }
        }
        String addUserId = StpUtil.getLoginIdAsString();
        User user = userMapper.selectById(addUserId);
        project.setCreateUserId(addUserId);
        project.setCreateUserName(user.getUsername());
        // 负责人允许留空：未指定时 owner_id 保持 null，项目由团队管理员/超管管理
        // 初始化概览统计字段，避免数据库非空约束报错且保证前端默认展示 0
        if (project.getCoverage() == null) {
            project.setCoverage(0);
        }
        if (project.getApiTotal() == null) {
            project.setApiTotal(0);
        }
        if (project.getUiTotal() == null) {
            project.setUiTotal(0);
        }
        if (project.getPerformanceTotal() == null) {
            project.setPerformanceTotal(0);
        }
        if (project.getPlanTotal() == null) {
            project.setPlanTotal(0);
        }
        if (project.getUiPass() == null) {
            project.setUiPass(0);
        }
        return projectMapper.insert(project) > 0;
    }

    @Override
    public Boolean updateProject(Project project) {
        if (project == null || project.getId() == null) {
            throw new ParamIsEmptyException("缺少项目信息");
        }
        project.setUpdatedAt(new Date());
        String updateUserId = StpUtil.getLoginIdAsString();
        User user = userMapper.selectById(updateUserId);
        project.setUpdateUserId(updateUserId);
        project.setCreateUserName(user.getUsername());
        // 记录变更前的项目管理员，用于换管理员后给旧管理员兜底降级
        Project before = projectMapper.selectById(project.getId());
        Long oldOwnerId = before != null ? before.getOwnerId() : null;
        // 未传负责人时保留原值，避免被覆盖为空
        if (project.getOwnerId() == null) {
            if (before != null) {
                project.setOwnerId(before.getOwnerId());
            }
        }
        boolean updated = projectMapper.updateById(project) > 0;
        // 项目管理员变更（A→B）：A 若在本项目没有任何授权记录，
        // 自动降级为内置只读模板「项目成员」（project_member），保留只读身份而非被剔除
        if (updated && oldOwnerId != null && !oldOwnerId.equals(project.getOwnerId())) {
            projectMemberService.demotePreviousOwnerIfNoRole(
                    project.getId(), oldOwnerId, project.getOwnerId(), updateUserId);
        }
        return updated;
    }

    @Override
    public SaResult getProjectListByTeamId(Integer teamId) {
        if (teamId == null) {
            return SaResult.error("缺少团队id");
        }
        QueryWrapper<Project> projectQueryWrapper = new QueryWrapper<>();
        projectQueryWrapper.eq("team_id", teamId);
        List<Project> projects = projectMapper.selectList(projectQueryWrapper);

        String loginId = StpUtil.getLoginIdAsString();
        // 超管 / 团队管理员：看本团队全部项目
        if (permissionChecker.isSuperAdmin(loginId) || permissionChecker.isTeamAdmin(teamId, loginId)) {
            fillMyRole(projects, teamId, loginId);
            return SaResult.ok().setData(projects);
        }
        // 其他成员：只看自己创建的、被授权（user_role）参与的，或身为项目负责人的项目
        QueryWrapper<UserRole> urWrapper = new QueryWrapper<>();
        urWrapper.eq("user_id", loginId).eq("status", 1)
                .and(w -> w.isNull("expire_time").or().gt("expire_time", new Date()));
        Set<Long> myScopeIds = userRoleMapper.selectList(urWrapper).stream()
                .map(UserRole::getScopeId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        List<Project> mine = projects.stream()
                .filter(p -> loginId.equals(p.getCreateUserId())
                        || (p.getOwnerId() != null && loginId.equals(p.getOwnerId().toString()))
                        || (p.getId() != null && myScopeIds.contains(p.getId().longValue())))
                .collect(Collectors.toList());
        fillMyRole(mine, teamId, loginId);
        return SaResult.ok().setData(mine);
    }

    /**
     * 填充当前登录用户在每个项目的角色（列表展示）：
     * 项目管理员(owner) > 团队管理员 > 超管 > 模板授权角色 > 创建者
     */
    private void fillMyRole(List<Project> projects, Integer teamId, String loginId) {
        if (projects == null || projects.isEmpty()) {
            return;
        }
        boolean superAdmin = permissionChecker.isSuperAdmin(loginId);
        boolean teamAdmin = permissionChecker.isTeamAdmin(teamId, loginId);
        // 当前用户在这些项目下的有效模板授权（含未过期），按项目取第一个角色名
        Set<Long> projectIds = projects.stream()
                .map(Project::getId).filter(id -> id != null)
                .map(Integer::longValue).collect(Collectors.toSet());
        Map<Long, Role> projectRoleMap = new HashMap<>();
        if (!projectIds.isEmpty()) {
            QueryWrapper<UserRole> urQw = new QueryWrapper<>();
            urQw.eq("user_id", loginId).eq("status", 1).in("scope_id", projectIds)
                    .and(w -> w.isNull("expire_time").or().gt("expire_time", new Date()));
            for (UserRole ur : userRoleMapper.selectList(urQw)) {
                if (ur.getScopeId() == null || projectRoleMap.containsKey(ur.getScopeId())) {
                    continue;
                }
                Role role = ur.getRoleId() != null ? roleMapper.selectById(ur.getRoleId()) : null;
                if (role != null) {
                    projectRoleMap.put(ur.getScopeId(), role);
                }
            }
        }
        for (Project p : projects) {
            if (p.getOwnerId() != null && loginId.equals(p.getOwnerId().toString())) {
                p.setMyRoleName("项目管理员");
                p.setMyRoleCode("project_admin");
            } else if (teamAdmin) {
                p.setMyRoleName("团队管理员");
                p.setMyRoleCode("team_admin");
            } else if (superAdmin) {
                p.setMyRoleName("超级管理员");
                p.setMyRoleCode("super_admin");
            } else if (p.getId() != null && projectRoleMap.containsKey(p.getId().longValue())) {
                Role role = projectRoleMap.get(p.getId().longValue());
                p.setMyRoleName(role.getName());
                p.setMyRoleCode(role.getCode());
            } else if (loginId.equals(p.getCreateUserId())) {
                p.setMyRoleName("创建者");
                p.setMyRoleCode("creator");
            }
        }
    }

    @Override
    public SaResult getProjectById(Integer id) {
        if (id == null) {
            return SaResult.error("缺少项目id");
        }
        return SaResult.ok().setData(projectMapper.selectById(id));
    }

    @Override
    @Transactional
    public Boolean deleteProject(Integer projectId) {
        if (projectId == null) {
            throw new ParamIsEmptyException("缺少项目id");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new ParamIsEmptyException("项目不存在");
        }
        project.setDeletedAt(new Date());
        return projectMapper.deleteById(project) > 0;
    }

    @Override
    public boolean canDeleteProject(Integer projectId, String loginId) {
        if (projectId == null || loginId == null) {
            return false;
        }
        // 超管
        if (permissionChecker.isSuperAdmin(loginId)) {
            return true;
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            return false;
        }
        // 有 project:delete 权限
        if (permissionChecker.hasPermission(project.getTeamId(), loginId, "project:delete")) {
            return true;
        }
        // 项目创建者兜底
        return loginId.equals(project.getCreateUserId());
    }
}




