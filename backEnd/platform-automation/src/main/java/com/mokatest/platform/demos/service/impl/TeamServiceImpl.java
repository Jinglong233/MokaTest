package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.Project;
import com.mokatest.platform.demos.domain.ui.Role;
import com.mokatest.platform.demos.domain.ui.Team;
import com.mokatest.platform.demos.domain.ui.TeamMember;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.domain.ui.UserRole;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.AddTeamMemberDTO;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.TeamMemberOperateDTO;
import com.mokatest.platform.demos.domain.ui.dto.addDTO.UpdateTeamMemberRoleDTO;
import com.mokatest.platform.demos.domain.ui.vo.TeamMemberVO;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.listener.teamLIstener.Enum.TeamChangeType;
import com.mokatest.platform.demos.listener.teamLIstener.TeamUpdateEvent;
import com.mokatest.platform.demos.mapper.*;
import com.mokatest.platform.demos.service.ProjectMemberService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import com.mokatest.platform.demos.service.TeamService;
import jakarta.annotation.Resource;
import org.apache.ibatis.executor.BatchResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: JingLong
 * @description 针对表【team】的数据库操作Service实现
 * @createDate 2026-03-21 14:10:23
 */
@Service
public class TeamServiceImpl extends ServiceImpl<TeamMapper, Team>
        implements TeamService {

    @Resource
    private TeamMapper teamMapper;
    @Resource
    private TeamMemberMapper teamMemberMapper;
    @Resource
    private UserMapper userMapper;
    @Resource
    private RoleMapper roleMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private UserRoleMapper userRoleMapper;

    @Resource
    private ApplicationEventPublisher applicationEventPublisher;

    @Resource
    private ProjectPermissionChecker permissionChecker;

    @Resource
    private ProjectMemberService projectMemberService;

    @Override
    public SaResult getTeamList() {
        String userId = StpUtil.getLoginIdAsString();
        // 超级管理员返回全部团队
        if (isSuperAdmin(userId)) {
            List<Team> teams = teamMapper.selectList(null);
            // 个人团队是用户工作空间，不在超管团队列表/总览中展示
            teams.removeIf(t -> Integer.valueOf(1).equals(t.getIsPersonal()));
            teams.sort(Comparator.comparing(Team::getIsPersonal, Comparator.nullsLast(Comparator.reverseOrder())));
            // 超管不使用团队切换下拉，仅保持语义一致
            teams.forEach(t -> t.setManageable(true));
            fillCreateUserName(teams);
            fillMemberCount(teams);
            return SaResult.ok().setData(teams);
        }

        QueryWrapper<TeamMember> teamMemberMapperQueryWrapper = new QueryWrapper<>();
        teamMemberMapperQueryWrapper.eq("user_id", userId);
        List<TeamMember> teamMembers =
                teamMemberMapper.selectList(teamMemberMapperQueryWrapper);
        if (teamMembers.size() <= 0) {
            return SaResult.ok().setData(new ArrayList<>());
        }
        // 获取teamMembers中的teamId
        List<Long> teamIds = teamMembers.stream().map(TeamMember::getTeamId).toList();
        if (teamIds.isEmpty()) {
            return SaResult.ok().setData(new ArrayList<>());
        }
        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>();
        teamQueryWrapper.in("id", teamIds).eq("status", 1);
        List<Team> teams = teamMapper.selectList(teamQueryWrapper);
        // 个人团队排在最前面，方便用户优先进入自己的工作空间
        teams.sort(Comparator.comparing(Team::getIsPersonal, Comparator.nullsLast(Comparator.reverseOrder())));
        // 填充当前用户对各团队的可管理标记（团队管理员唯一来源是 team.owner_id）
        teams.forEach(t -> t.setManageable(
                t.getOwnerId() != null && userId.equals(String.valueOf(t.getOwnerId()))));
        fillCreateUserName(teams);
        fillMemberCount(teams);
        return SaResult.ok().setData(teams);
    }

    /**
     * 批量填充团队成员数（仅统计正常状态、且排除超级管理员，与成员抽屉口径一致）
     */
    private void fillMemberCount(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            return;
        }
        List<Integer> teamIds = teams.stream()
                .map(Team::getId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (teamIds.isEmpty()) {
            return;
        }
        // 超级管理员用户ID集合（统计成员数时排除）
        java.util.Set<Long> superAdminIds = userMapper
                .selectList(new QueryWrapper<User>().eq("role", "super_admin"))
                .stream().map(User::getId).filter(id -> id != null).collect(java.util.stream.Collectors.toSet());

        List<TeamMember> all = teamMemberMapper.selectList(
                new QueryWrapper<TeamMember>().in("team_id", teamIds).eq("status", 1));
        Map<Long, Integer> countMap = new HashMap<>();
        for (TeamMember m : all) {
            if (m.getTeamId() == null || superAdminIds.contains(m.getUserId())) {
                continue;
            }
            countMap.merge(m.getTeamId(), 1, Integer::sum);
        }
        teams.forEach(t -> t.setTeamNumber(
                t.getId() == null ? 0 : countMap.getOrDefault(t.getId().longValue(), 0)));
    }

    /**
     * 批量填充团队创建人显示名（nickname 优先，其次 username），避免前端再查用户
     */
    private void fillCreateUserName(List<Team> teams) {
        if (teams == null || teams.isEmpty()) {
            return;
        }
        List<String> creatorIds = teams.stream()
                .map(Team::getCreateUserId)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .toList();
        if (creatorIds.isEmpty()) {
            return;
        }
        Map<String, String> nameMap = new HashMap<>();
        List<User> users = userMapper.selectBatchIds(creatorIds);
        for (User u : users) {
            if (u != null && u.getId() != null) {
                String name = u.getNickname() != null && !u.getNickname().isEmpty()
                        ? u.getNickname() : u.getUsername();
                nameMap.put(String.valueOf(u.getId()), name);
            }
        }
        teams.forEach(t -> t.setCreateUserName(nameMap.get(t.getCreateUserId())));
    }

    @Override
    @Transactional
    public SaResult createTeam(Team team) {
        if (team == null) {
            return SaResult.error("缺少团队信息");
        }
        // 仅超级管理员可创建团队（个人团队由注册流程单独创建，不走此方法）
        if (!isSuperAdmin(StpUtil.getLoginIdAsString())) {
            return SaResult.error("无权限创建团队，仅超级管理员可创建");
        }
        // 校验团队名称的长度、必填、唯一性、名称合法
        //团队名称只能包含汉字、字母、数字和下划线
        if (!team.getTeamName().matches("^[\\u4e00-\\u9fa5a-zA-Z0-9_]+$")) {
            return SaResult.error("团队名称只能包含汉字、字母、数字和下划线");
        }
        if (team.getTeamName().length() > 11 || team.getTeamName().length() < 5) {
            return SaResult.error("团队名称长度必须在5-10个字符之间");
        }
        if (teamMapper.selectOne(new QueryWrapper<Team>().eq("team_name", team.getTeamName())) != null) {
            return SaResult.error("团队名称已存在");
        }

        team.setCreateUserId(StpUtil.getLoginIdAsString());
        // 默认创建正常状态的团队
        if (team.getStatus() == null) {
            team.setStatus(1);
        }
        Long creatorId0 = Long.valueOf(StpUtil.getLoginIdAsString());
        // 团队管理员唯一来源是 team.owner_id；未指定时默认为创建者
        if (team.getOwnerId() == null) {
            team.setOwnerId(creatorId0);
        } else if (!team.getOwnerId().equals(creatorId0)) {
            // 指定了其他管理员，先校验
            User ownerUser = userMapper.selectById(team.getOwnerId());
            if (ownerUser == null) {
                return SaResult.error("指定的团队管理员不存在");
            }
            if ("super_admin".equals(ownerUser.getRole())) {
                return SaResult.error("不能指定超级管理员为团队管理员");
            }
        }
        if (teamMapper.insert(team) <= 0) {
            return SaResult.error("创建失败");
        }

        // 查询 member 系统角色ID（兼容旧 member / 新 team_member）
        Long memberRoleId = getSystemRoleIdByCodes("member", "team_member");

        Long teamIdLong = Long.valueOf(team.getId());
        Long creatorId = creatorId0;
        Long ownerId = team.getOwnerId();

        // 将创建者加入团队（管理员身份由 team.owner_id 决定，成员角色一律为普通成员）
        TeamMember creatorMember = new TeamMember();
        creatorMember.setTeamId(teamIdLong);
        creatorMember.setUserId(creatorId);
        creatorMember.setStatus(1);
        creatorMember.setRoleId(memberRoleId);
        creatorMember.setRole(memberRoleId != null ? "member" : "team_member");
        if (teamMemberMapper.insert(creatorMember) <= 0) {
            return SaResult.error("创建失败");
        }

        // 如果指定了其他管理员，将管理员也加入团队（普通成员角色，管理员身份来自 owner_id）
        if (ownerId != null && !ownerId.equals(creatorId)) {
            TeamMember ownerMember = new TeamMember();
            ownerMember.setTeamId(teamIdLong);
            ownerMember.setUserId(ownerId);
            ownerMember.setRoleId(memberRoleId);
            ownerMember.setRole(memberRoleId != null ? "member" : "team_member");
            ownerMember.setStatus(1);
            if (teamMemberMapper.insert(ownerMember) <= 0) {
                return SaResult.error("创建失败");
            }
        }

        return SaResult.ok("创建成功");
    }

    @Override
    public SaResult updateTeam(Team team) {
        if (team == null) {
            return SaResult.error("缺少团队信息");
        }
        // 校验团队名称的长度、必填、唯一性、名称合法
        //团队名称只能包含汉字、字母、数字和下划线
        if (!team.getTeamName().matches("^[\\u4e00-\\u9fa5a-zA-Z0-9_]+$")) {
            return SaResult.error("团队名称只能包含汉字、字母、数字和下划线");
        }
        if (team.getTeamName().length() > 11 || team.getTeamName().length() < 5) {
            return SaResult.error("团队名称长度必须在5-10个字符之间");
        }
        QueryWrapper<Team> teamQueryWrapper = new QueryWrapper<>();
        teamQueryWrapper.eq("team_name", team.getTeamName());
        teamQueryWrapper.ne("id", team.getId());
        if (teamMapper.selectOne(teamQueryWrapper) != null) {
            return SaResult.error("团队名称已存在");
        }

        team.setUpdateUserId(StpUtil.getLoginIdAsString());
        team.setUpdatedAt(new Date());
        // 状态只允许 0（禁用）或 1（正常）
        if (team.getStatus() != null && team.getStatus() != 0 && team.getStatus() != 1) {
            return SaResult.error("团队状态不合法");
        }

        // 变更团队管理员（owner_id）：新管理员必须是本团队正常成员，且不能是超管
        if (team.getOwnerId() != null) {
            User newOwner = userMapper.selectById(team.getOwnerId());
            if (newOwner == null) {
                return SaResult.error("指定的团队管理员不存在");
            }
            if ("super_admin".equals(newOwner.getRole())) {
                return SaResult.error("不能指定超级管理员为团队管理员");
            }
            QueryWrapper<TeamMember> ownerMemberQw = new QueryWrapper<>();
            ownerMemberQw.eq("team_id", team.getId())
                    .eq("user_id", team.getOwnerId())
                    .eq("status", 1);
            if (teamMemberMapper.selectCount(ownerMemberQw) <= 0) {
                return SaResult.error("新团队管理员必须是本团队成员");
            }
        }

        return teamMapper.updateById(team) > 0 ? SaResult.ok("更新成功") : SaResult.error("更新失败");
    }

    @Override
    @Transactional
    public SaResult addTeamMember(AddTeamMemberDTO addTeamMemberDTO) {
        if (addTeamMemberDTO == null) {
            return SaResult.error("缺少参数");
        }
        Team team = teamMapper.selectById(addTeamMemberDTO.getTeamId());
        if (team == null) {
            return SaResult.error("团队不存在");
        }
        // 个人团队是单人工作空间，不支持邀请其他成员
        if (team.getIsPersonal() != null && team.getIsPersonal() == 1) {
            return SaResult.error("个人团队不支持邀请成员，请创建正式团队");
        }
        // 判断用户是否存在
        if (addTeamMemberDTO.getUserList().isEmpty()) {
            return SaResult.error("请选择用户");
        }

        // 可选：邀请入团队的同时加入指定项目并分配项目角色。
        // 校验前置，任何一步失败整体回滚（不入团队）
        Project targetProject = null;
        if (Boolean.TRUE.equals(addTeamMemberDTO.getAssignProjectRole())) {
            if (addTeamMemberDTO.getProjectId() == null) {
                return SaResult.error("请先选择要加入的项目");
            }
            targetProject = projectMapper.selectById(addTeamMemberDTO.getProjectId());
            if (targetProject == null) {
                return SaResult.error("项目不存在");
            }
            // 数据边界：只能加入当前团队下的项目，禁止跨团队写入
            if (!targetProject.getTeamId().equals(team.getId())) {
                return SaResult.error("只能选择当前团队的项目");
            }
            // 项目管理员唯一来源是 project.owner_id（单人），批量指定无意义
            if (Boolean.TRUE.equals(addTeamMemberDTO.getAssignAsProjectAdmin())
                    && addTeamMemberDTO.getUserList().size() > 1) {
                return SaResult.error("指定项目管理员时一次只能邀请一个用户");
            }
        }

        // 查询 tester/member 系统角色ID（兼容旧 tester / 新 team_member）
        Long memberRoleId = getSystemRoleIdByCodes("tester", "member", "team_member");

        // 批量添加
        List<TeamMember> teamMembers = new ArrayList<>();
        for (String userId : addTeamMemberDTO.getUserList()) {
            // 判断该用户是否已存在于该团队
            QueryWrapper<TeamMember> teamMemberQueryWrapper = new QueryWrapper<>();
            teamMemberQueryWrapper.eq("team_id", team.getId());
            teamMemberQueryWrapper.eq("user_id", userId);
            if (teamMemberMapper.selectOne(teamMemberQueryWrapper) != null) {
                continue;
            }
            TeamMember teamMember = new TeamMember();
            teamMember.setTeamId(Long.valueOf(team.getId()));
            teamMember.setUserId(Long.valueOf(userId));
            teamMember.setRoleId(memberRoleId);
            teamMember.setRole("tester");
            teamMember.setStatus(1);
            teamMembers.add(teamMember);
        }
        if (teamMembers.size() <= 0){
            return SaResult.ok("添加成功");
        }

        List<BatchResult> insert = teamMemberMapper.insert(teamMembers);
        if (insert.size() <= 0) {
            return SaResult.error("添加失败");
        }

        // 可选：为本次实际新加入的成员分配项目角色（已存在的成员不重复分配）
        if (targetProject != null) {
            String loginId = StpUtil.getLoginIdAsString();
            Date expireTime = parseExpireTime(addTeamMemberDTO.getExpireTime());
            for (TeamMember member : teamMembers) {
                if (Boolean.TRUE.equals(addTeamMemberDTO.getAssignAsProjectAdmin())) {
                    // 指定为项目管理员：变更 project.owner_id，旧管理员自动降级为「项目成员」
                    SaResult result = projectMemberService.changeProjectOwner(
                            targetProject.getId(), member.getUserId(), loginId);
                    if (result.getCode() != 200) {
                        throw new BusinessException(result.getMsg());
                    }
                } else {
                    // 未指定角色时默认分配内置只读模板「项目成员」（project_member）
                    Long roleId = addTeamMemberDTO.getProjectRoleId();
                    if (roleId == null) {
                        Role memberRole = roleMapper.selectOne(new QueryWrapper<Role>()
                                .eq("code", "project_member")
                                .eq("scope_type", "TEMPLATE")
                                .isNull("team_id")
                                .last("LIMIT 1"));
                        if (memberRole == null) {
                            throw new BusinessException("内置角色「项目成员」不存在，请先执行角色初始化脚本");
                        }
                        roleId = memberRole.getId();
                    }
                    SaResult result = projectMemberService.assignProjectRole(
                            targetProject.getId(), member.getUserId(), roleId, expireTime);
                    if (result.getCode() != 200) {
                        throw new BusinessException(result.getMsg());
                    }
                }
            }
        }

        applicationEventPublisher.publishEvent(new TeamUpdateEvent(this, team.getId(), insert.size(), TeamChangeType.INSERT));
        return SaResult.ok("添加成功");
    }

    /**
     * 解析角色到期时间（yyyy-MM-dd HH:mm:ss），为空表示永久生效
     */
    private Date parseExpireTime(String expireTime) {
        if (expireTime == null || expireTime.isBlank()) {
            return null;
        }
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(expireTime);
        } catch (ParseException e) {
            throw new BusinessException("到期时间格式不正确，应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    @Override
    public SaResult getTeamMembers(Integer teamId) {
        if (teamId == null) {
            return SaResult.error("缺少团队id");
        }
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            return SaResult.error("团队不存在");
        }

        QueryWrapper<TeamMember> wrapper = new QueryWrapper<>();
        wrapper.eq("team_id", teamId);
        wrapper.orderByDesc("join_time");
        List<TeamMember> members = teamMemberMapper.selectList(wrapper);
        if (members.isEmpty()) {
            return SaResult.ok().setData(new ArrayList<>());
        }

        List<TeamMemberVO> result = new ArrayList<>();
        for (TeamMember member : members) {
            User user = userMapper.selectById(member.getUserId());
            if (user == null) {
                continue;
            }
            // 超级管理员不在团队成员列表中展示
            if ("super_admin".equals(user.getRole())) {
                continue;
            }
            Role role = member.getRoleId() != null ? roleMapper.selectById(member.getRoleId()) : null;

            TeamMemberVO vo = new TeamMemberVO();
            vo.setId(member.getId());
            vo.setTeamId(member.getTeamId());
            vo.setUserId(member.getUserId());
            vo.setUsername(user.getUsername());
            vo.setNickname(user.getNickname());
            vo.setAvatar(user.getAvatar());
            vo.setPhone(desensitizePhone(user.getPhone()));
            vo.setEmail(desensitizeEmail(user.getEmail()));
            vo.setRoleId(member.getRoleId());
            // 管理员身份以 team.owner_id 为准：owner 一律显示为团队管理员；
            // 历史 member 记录里的 admin/team_admin 角色已废弃，显示为普通成员
            boolean isOwner = team.getOwnerId() != null && team.getOwnerId().equals(member.getUserId());
            vo.setTeamOwner(isOwner);
            String rawCode = role != null ? role.getCode() : member.getRole();
            boolean legacyAdmin = "admin".equals(rawCode) || "team_admin".equals(rawCode);
            if (isOwner) {
                vo.setRoleName("团队管理员");
                vo.setRoleCode("team_admin");
            } else if (legacyAdmin) {
                vo.setRoleName("普通成员");
                vo.setRoleCode("team_member");
            } else {
                vo.setRoleName(role != null ? role.getName() : member.getRole());
                vo.setRoleCode(rawCode);
            }
            vo.setStatus(member.getStatus());
            vo.setJoinTime(member.getJoinTime());
            vo.setSuperAdmin("super_admin".equals(user.getRole()));
            result.add(vo);
        }
        return SaResult.ok().setData(result);
    }

    @Override
    @Transactional
    public SaResult updateMemberRole(UpdateTeamMemberRoleDTO dto) {
        if (dto == null || dto.getTeamId() == null || dto.getUserId() == null || dto.getRoleId() == null) {
            return SaResult.error("缺少参数");
        }
        Role newRole = roleMapper.selectById(dto.getRoleId());
        if (newRole == null) {
            return SaResult.error("角色不存在");
        }
        // 只能分配系统角色或当前团队下的角色
        if (newRole.getTeamId() != null && !newRole.getTeamId().equals(dto.getTeamId().longValue())) {
            return SaResult.error("不能分配其他团队的角色");
        }

        // 团队管理员唯一来源是 team.owner_id，不能通过成员角色分配/解除管理员
        if ("admin".equals(newRole.getCode()) || "team_admin".equals(newRole.getCode())) {
            return SaResult.error("团队管理员请在团队设置中变更，不能通过分配角色设置");
        }

        QueryWrapper<TeamMember> wrapper = new QueryWrapper<>();
        wrapper.eq("team_id", dto.getTeamId()).eq("user_id", dto.getUserId());
        TeamMember member = teamMemberMapper.selectOne(wrapper);
        if (member == null) {
            return SaResult.error("成员不存在");
        }

        // 不能修改超级管理员的角色
        if (isSuperAdmin(String.valueOf(dto.getUserId()))) {
            return SaResult.error("不能修改超级管理员的角色");
        }

        // 团队管理员（owner）的成员角色无权限含义，无需调整
        Team team = teamMapper.selectById(dto.getTeamId());
        if (team != null && team.getOwnerId() != null && team.getOwnerId().equals(dto.getUserId())) {
            return SaResult.error("该成员是团队管理员，无需调整角色");
        }

        member.setRoleId(dto.getRoleId());
        member.setRole(newRole.getCode());
        return teamMemberMapper.updateById(member) > 0 ? SaResult.ok("修改成功") : SaResult.error("修改失败");
    }

    @Override
    @Transactional
    public SaResult removeMember(TeamMemberOperateDTO dto) {
        if (dto == null || dto.getTeamId() == null || dto.getUserId() == null) {
            return SaResult.error("缺少参数");
        }
        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.equals(String.valueOf(dto.getUserId()))) {
            return SaResult.error("不能移除自己");
        }

        // 不能移除超级管理员
        if (isSuperAdmin(String.valueOf(dto.getUserId()))) {
            return SaResult.error("不能移除超级管理员");
        }

        QueryWrapper<TeamMember> wrapper = new QueryWrapper<>();
        wrapper.eq("team_id", dto.getTeamId()).eq("user_id", dto.getUserId());
        TeamMember member = teamMemberMapper.selectOne(wrapper);
        if (member == null) {
            return SaResult.error("成员不存在");
        }

        // 团队管理员（owner）不能从团队中移除，需先在团队设置中变更管理员
        Team team0 = teamMapper.selectById(dto.getTeamId());
        if (team0 != null && team0.getOwnerId() != null && team0.getOwnerId().equals(dto.getUserId())) {
            return SaResult.error("团队管理员不能从团队中移除，请先在团队设置中变更团队管理员");
        }

        // 移除团队成员时，同步清理该用户在该团队下所有项目的角色授权
        QueryWrapper<Project> projectQw = new QueryWrapper<>();
        projectQw.eq("team_id", dto.getTeamId());
        List<Project> projects = projectMapper.selectList(projectQw);
        if (!projects.isEmpty()) {
            List<Long> projectIds = projects.stream()
                    .map(p -> p.getId() != null ? p.getId().longValue() : null)
                    .filter(id -> id != null)
                    .toList();
            if (!projectIds.isEmpty()) {
                QueryWrapper<UserRole> urWrapper = new QueryWrapper<>();
                urWrapper.eq("user_id", dto.getUserId())
                        .in("scope_id", projectIds);
                userRoleMapper.delete(urWrapper);
            }
        }

        return teamMemberMapper.delete(wrapper) > 0 ? SaResult.ok("移除成功") : SaResult.error("移除失败");
    }

    @Override
    @Transactional
    public SaResult updateMemberStatus(TeamMemberOperateDTO dto) {
        if (dto == null || dto.getTeamId() == null || dto.getUserId() == null || dto.getStatus() == null) {
            return SaResult.error("缺少参数");
        }
        if (dto.getStatus() != 0 && dto.getStatus() != 1) {
            return SaResult.error("状态不合法");
        }

        String loginId = StpUtil.getLoginIdAsString();
        if (loginId.equals(String.valueOf(dto.getUserId()))) {
            return SaResult.error("不能操作自己");
        }

        // 不能禁用超级管理员
        if (dto.getStatus() == 0 && isSuperAdmin(String.valueOf(dto.getUserId()))) {
            return SaResult.error("不能禁用超级管理员");
        }

        QueryWrapper<TeamMember> wrapper = new QueryWrapper<>();
        wrapper.eq("team_id", dto.getTeamId()).eq("user_id", dto.getUserId());
        TeamMember member = teamMemberMapper.selectOne(wrapper);
        if (member == null) {
            return SaResult.error("成员不存在");
        }

        // 团队管理员（owner）不能禁用，需先在团队设置中变更管理员
        if (dto.getStatus() == 0) {
            Team team1 = teamMapper.selectById(dto.getTeamId());
            if (team1 != null && team1.getOwnerId() != null && team1.getOwnerId().equals(dto.getUserId())) {
                return SaResult.error("团队管理员不能禁用，请先在团队设置中变更团队管理员");
            }
        }

        member.setStatus(dto.getStatus());
        return teamMemberMapper.updateById(member) > 0 ? SaResult.ok("操作成功") : SaResult.error("操作失败");
    }

    @Override
    @Transactional
    public SaResult deleteTeam(Integer teamId) {
        if (teamId == null) {
            return SaResult.error("缺少团队id");
        }
        Team team = teamMapper.selectById(teamId);
        if (team == null) {
            return SaResult.error("团队不存在");
        }
        // 个人团队是用户默认工作空间，禁止解散
        if (team.getIsPersonal() != null && team.getIsPersonal() == 1) {
            return SaResult.error("个人团队不支持解散");
        }
        team.setDeletedAt(new Date());
        teamMapper.deleteById(team);
        return SaResult.ok("团队已解散");
    }

    @Override
    public boolean canDeleteTeam(Integer teamId, String loginId) {
        if (teamId == null || loginId == null) {
            return false;
        }
        // 超管
        if (permissionChecker.isSuperAdmin(loginId)) {
            return true;
        }
        // 有 team:delete 权限
        if (permissionChecker.hasPermission(teamId, loginId, "team:delete")) {
            return true;
        }
        // 团队创建者兜底
        Team team = teamMapper.selectById(teamId);
        return team != null && loginId.equals(team.getCreateUserId());
    }

    /**
     * 获取系统角色ID（兼容新旧角色编码）
     */
    private Long getSystemRoleId(String code) {
        return getSystemRoleIdByCodes(code);
    }

    private Long getSystemRoleIdByCodes(String... codes) {
        if (codes == null || codes.length == 0) {
            return null;
        }
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        wrapper.in("code", Arrays.asList(codes)).isNull("team_id");
        Role role = roleMapper.selectOne(wrapper);
        return role != null ? role.getId() : null;
    }

    /**
     * 判断指定用户是否为超级管理员
     */
    private boolean isSuperAdmin(String userId) {
        User user = userMapper.selectById(userId);
        return user != null && "super_admin".equals(user.getRole());
    }

    /**
     * 手机号脱敏
     */
    private String desensitizePhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 邮箱脱敏
     */
    private String desensitizeEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        String[] parts = email.split("@");
        String name = parts[0];
        if (name.length() <= 2) {
            return name + "@" + parts[1];
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + "@" + parts[1];
    }
}
