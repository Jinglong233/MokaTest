package com.mokatest.platform.demos.service.impl;

import cn.dev33.satoken.secure.SaSecureUtil;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.UserQueryDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.AdminCreateUserDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.AdminResetPwdDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.UpdateUserInfoDTO;
import com.mokatest.platform.demos.domain.ui.dto.user.UserInfoDTO;
import com.mokatest.platform.demos.domain.ui.vo.UserTeamVO;
import com.mokatest.platform.demos.domain.ui.vo.UserVO;
import com.mokatest.platform.demos.domain.ui.Role;
import com.mokatest.platform.demos.domain.ui.Team;
import com.mokatest.platform.demos.domain.ui.TeamMember;
import com.mokatest.platform.demos.exception.ParamIsEmptyException;
import com.mokatest.platform.demos.listener.userListener.UserAddEvent;
import com.mokatest.platform.demos.mapper.RoleMapper;
import com.mokatest.platform.demos.mapper.TeamMapper;
import com.mokatest.platform.demos.mapper.TeamMemberMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import com.mokatest.platform.demos.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author: JingLong
 * @description 针对表【user(用户表)】的数据库操作Service实现
 * @createDate 2026-03-19 17:00:25
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private com.mokatest.platform.demos.mapper.ProjectMapper projectMapper;

    @Resource
    private com.mokatest.platform.demos.mapper.UserRoleMapper userRoleMapper;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    private com.mokatest.platform.demos.operationlog.service.SysLoginLogService sysLoginLogService;

    @Override
    @Transactional
    public SaResult register(UserInfoDTO userInfoDTO) {
        if (userInfoDTO == null) {
            return SaResult.error("用户信息不能为空");
        }
        String username = userInfoDTO.getUsername();
        String password = userInfoDTO.getPassword();
        if (username == null || password == null) {
            return SaResult.error("用户名或密码不能为空");
        }

        // 判断账号是否合法
        if (username.length() < 5 || username.length() > 20) {
            return SaResult.error("账号长度需要大于5小于20");
        }

        if (password.length() < 6 || password.length() > 20) {
            return SaResult.error("密码长度不能小于6，且密码长度不能大于20");
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            return SaResult.error("账号只能包含字母、数字和下划线");
        }
        if (!password.matches("^[a-zA-Z0-9_]+$")) {
            return SaResult.error("密码只能包含字母、数字和下划线");
        }

        // 判断用户名是否重复
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq("username", username);
        if (userMapper.selectOne(userQueryWrapper) != null) {
            return SaResult.error("用户名已存在");
        }
        User user = new User();
        user.setUsername(username);
        UUID salt = UUID.randomUUID();
        user.setSalt(salt.toString());
        // 密码加密
        user.setPassword(SaSecureUtil.aesEncrypt(salt.toString(), password));
        int insert = userMapper.insert(user);
        if (insert <= 0) {
            return SaResult.error("注册失败");
        }
        eventPublisher.publishEvent(new UserAddEvent(this, user.getId().toString()));
        return SaResult.ok("注册成功");
    }

    @Override
    public SaResult login(UserInfoDTO userInfoDTO) {
        if (userInfoDTO == null) {
            return SaResult.error("用户信息不能为空");
        }
        String username = userInfoDTO.getUsername();
        String password = userInfoDTO.getPassword();
        if (username == null || password == null) {
            return SaResult.error("用户名或密码不能为空");
        }
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq("username", username);
        User user = userMapper.selectOne(userQueryWrapper);
        if (user == null) {
            sysLoginLogService.record("LOGIN", null, username, null, false, "用户不存在");
            return SaResult.error("用户不存在");
        }
        if (!SaSecureUtil.aesEncrypt(user.getSalt(), password).equals(user.getPassword())) {
            sysLoginLogService.record("LOGIN", user.getId(), username, user.getNickname(), false, "密码错误");
            return SaResult.error("密码错误");
        }
        if (user.getStatus() != null && user.getStatus() != 1) {
            sysLoginLogService.record("LOGIN", user.getId(), username, user.getNickname(), false, "账号已被禁用");
            return SaResult.error("账号已被禁用，请联系管理员");
        }
        StpUtil.login(user.getId());
        sysLoginLogService.record("LOGIN", user.getId(), username, user.getNickname(), true, null);

//        UserVO userVO = new UserVO();
//        BeanUtil.copyProperties(user, userVO);
        return SaResult.ok("登录成功").setData(StpUtil.getTokenValue());
    }

    @Override
    public SaResult getLoginInfo() {
        if (!StpUtil.isLogin()) {
            return SaResult.error("未登录");
        }
        User user = userMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            return SaResult.error("用户不存在");
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return SaResult.ok().setData(userVO);
    }

    @Override
    public SaResult updateUserInfo(UpdateUserInfoDTO userInfoDTO) {
        if (userInfoDTO == null) {
            return SaResult.error("用户信息不能为空");
        }

        // 强制使用当前登录用户的ID，防止通过DTO中的id篡改他人信息
        String loginId = StpUtil.getLoginIdAsString();

        // 如果email 不为空，则判断email是否合法
        if (userInfoDTO.getEmail() != null && !userInfoDTO.getEmail().matches("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$")) {
            return SaResult.error("邮箱格式不正确");
        }
        User user = new User();
        BeanUtil.copyProperties(userInfoDTO, user);
        user.setId(Long.valueOf(loginId));  // 强制设置为当前用户，防止篡改他人
        user.setUpdateTime(new Date());
        return userMapper.updateById(user) > 0 ? SaResult.ok("更新成功") : SaResult.error("更新失败");

    }

    @Override
    public SaResult updatePwd(String oldPwd, String newPwd) {
        // 验证两次密码是否一样
        if (oldPwd.equals(newPwd)) {
            return SaResult.error("新旧密码不能一样");
        }
        // 判断用户是否存在
        if (!StpUtil.isLogin()) {
            return SaResult.error("用户不存在");
        }
        String loginIdAsString = StpUtil.getLoginIdAsString();
        User user = userMapper.selectById(loginIdAsString);
        String salt = user.getSalt();
        if (!SaSecureUtil.aesEncrypt(salt, oldPwd).equals(user.getPassword())) {
            return SaResult.error("旧密码错误");
        }

        // 校验新密码
        if (newPwd.length() < 6 || newPwd.length() > 20) {
            return SaResult.error("密码长度不能小于6，且密码长度不能大于20");
        }
        if (!newPwd.matches("^[a-zA-Z0-9_]+$")) {
            return SaResult.error("密码只能包含字母、数字和下划线");
        }
        user.setPassword(SaSecureUtil.aesEncrypt(salt, newPwd));
        if (userMapper.updateById(user) <= 0) {
            return SaResult.error("更新失败");
        }
        return SaResult.ok("修改成功");
    }

    @Override
    @Transactional
    public SaResult adminCreateUser(AdminCreateUserDTO dto) {
        if (!isSuperAdmin(StpUtil.getLoginIdAsString())) {
            return SaResult.error("仅超级管理员可执行此操作");
        }
        if (dto == null || StrUtil.isBlank(dto.getUsername())) {
            return SaResult.error("用户名不能为空");
        }
        String username = dto.getUsername().trim();
        if (username.length() < 5 || username.length() > 20) {
            return SaResult.error("账号长度需要大于5小于20");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            return SaResult.error("账号只能包含字母、数字和下划线");
        }
        QueryWrapper<User> checkQw = new QueryWrapper<>();
        checkQw.eq("username", username);
        if (userMapper.selectOne(checkQw) != null) {
            return SaResult.error("用户名已存在");
        }
        // 校验邮箱唯一性
        if (StrUtil.isNotBlank(dto.getEmail())) {
            QueryWrapper<User> emailQw = new QueryWrapper<>();
            emailQw.eq("email", dto.getEmail().trim());
            if (userMapper.selectOne(emailQw) != null) {
                return SaResult.error("邮箱已被使用");
            }
        }

        // 随机生成密码
        String password = generateRandomPassword();
        User user = new User();
        user.setUsername(username);
        user.setNickname(StrUtil.isNotBlank(dto.getNickname()) ? dto.getNickname().trim() : null);
        user.setEmail(StrUtil.isNotBlank(dto.getEmail()) ? dto.getEmail().trim() : null);
        user.setPhone(StrUtil.isNotBlank(dto.getPhone()) ? dto.getPhone().trim() : null);
        UUID salt = UUID.randomUUID();
        user.setSalt(salt.toString());
        user.setPassword(SaSecureUtil.aesEncrypt(salt.toString(), password));
        int insert = userMapper.insert(user);
        if (insert <= 0) {
            return SaResult.error("新建用户失败");
        }
        eventPublisher.publishEvent(new UserAddEvent(this, user.getId().toString()));
        Map<String, Object> data = new HashMap<>();
        data.put("username", username);
        data.put("password", password);
        return SaResult.ok("新建用户成功").setData(data);
    }

    @Override
    @Transactional
    public SaResult adminResetPwd(AdminResetPwdDTO dto) {
        if (!isSuperAdmin(StpUtil.getLoginIdAsString())) {
            return SaResult.error("仅超级管理员可执行此操作");
        }
        if (dto == null || dto.getUserId() == null) {
            return SaResult.error("用户ID不能为空");
        }

        User user = userMapper.selectById(dto.getUserId());
        if (user == null) {
            return SaResult.error("用户不存在");
        }
        // 随机生成新密码
        String newPwd = generateRandomPassword();
        user.setPassword(SaSecureUtil.aesEncrypt(user.getSalt(), newPwd));
        if (userMapper.updateById(user) <= 0) {
            return SaResult.error("重置密码失败");
        }
        Map<String, Object> data = new HashMap<>();
        data.put("username", user.getUsername());
        data.put("password", newPwd);
        return SaResult.ok("密码重置成功").setData(data);
    }

    @Override
    public SaResult updateUserStatus(Long userId, Integer status) {
        String loginId = StpUtil.getLoginIdAsString();
        if (!isSuperAdmin(loginId)) {
            return SaResult.error("仅超级管理员可执行此操作");
        }
        if (userId == null || status == null || (status != 0 && status != 1)) {
            return SaResult.error("参数不合法");
        }
        if (loginId.equals(String.valueOf(userId))) {
            return SaResult.error("不能禁用/启用自己");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return SaResult.error("用户不存在");
        }
        if ("super_admin".equals(user.getRole())) {
            return SaResult.error("不能禁用超级管理员");
        }
        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        update.setUpdateTime(new java.util.Date());
        if (userMapper.updateById(update) <= 0) {
            return SaResult.error("操作失败");
        }
        // 禁用时踢出会话，使其现有 token 立即失效
        if (status == 0) {
            StpUtil.logout(userId);
        }
        return SaResult.ok(status == 1 ? "已启用" : "已禁用");
    }

    /**
     * 生成随机密码：10位，包含大小写字母、数字和下划线，符合密码规则（6-20位，^[a-zA-Z0-9_]+$）
     */
    private String generateRandomPassword() {
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String digits = "0123456789";
        String underscore = "_";
        String all = lower + upper + digits + underscore;
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        // 保证至少包含1个小写、1个大写、1个数字
        sb.append(lower.charAt(random.nextInt(lower.length())));
        sb.append(upper.charAt(random.nextInt(upper.length())));
        sb.append(digits.charAt(random.nextInt(digits.length())));
        // 补齐到 10 位
        for (int i = 3; i < 10; i++) {
            sb.append(all.charAt(random.nextInt(all.length())));
        }
        // 打乱顺序
        List<Character> chars = sb.chars().mapToObj(c -> (char) c).collect(Collectors.toList());
        Collections.shuffle(chars, random);
        return chars.stream().map(String::valueOf).collect(Collectors.joining());
    }

    @Override
    public SaResult getUserList() {
        String loginIdAsString = StpUtil.getLoginIdAsString();
        // 超级管理员返回全部用户，并附带团队角色信息
        if (isSuperAdmin(loginIdAsString)) {
            List<User> userList = userMapper.selectList(null);
            List<Long> userIds = userList.stream().map(User::getId).toList();
            Map<Long, List<UserTeamVO>> userTeamMap = buildUserTeamMap(userIds);
            List<UserVO> list = userList.stream().map(user -> {
                UserVO userVO = new UserVO();
                BeanUtil.copyProperties(user, userVO);
                userVO.setTeams(userTeamMap.getOrDefault(user.getId(), List.of()));
                return userVO;
            }).toList();
            return SaResult.ok().setData(list);
        }
        // 只返回同团队成员，并剔除超级管理员账号
        Set<Long> teammateIds = getTeammateUserIds(loginIdAsString);
        if (teammateIds.isEmpty()) {
            return SaResult.ok().setData(List.of());
        }
        List<User> userList = userMapper.selectBatchIds(teammateIds);
        List<UserVO> list = userList.stream()
                .filter(user -> !"super_admin".equals(user.getRole()))
                .map(user -> {
                    UserVO userVO = new UserVO();
                    BeanUtil.copyProperties(user, userVO);
                    return userVO;
                }).toList();
        return SaResult.ok().setData(list);
    }

    /**
     * 获取指定用户的同团队成员 userId 集合（包含自己）
     */
    private Set<Long> getTeammateUserIds(String userId) {
        // 1. 查询该用户所属的所有团队
        QueryWrapper<TeamMember> teamQw = new QueryWrapper<>();
        teamQw.eq("user_id", userId).eq("status", 1);
        List<TeamMember> myTeams = teamMemberMapper.selectList(teamQw);
        if (myTeams == null || myTeams.isEmpty()) {
            return Set.of();
        }
        List<Long> teamIds = myTeams.stream().map(TeamMember::getTeamId).toList();

        // 2. 查询这些团队下的所有成员
        QueryWrapper<TeamMember> memberQw = new QueryWrapper<>();
        memberQw.in("team_id", teamIds).eq("status", 1);
        List<TeamMember> teammates = teamMemberMapper.selectList(memberQw);

        return teammates.stream()
                .map(TeamMember::getUserId)
                .collect(Collectors.toSet());
    }

    /**
     * 项目可选人集合：项目级授权成员（user_role scope_id=projectId）∪ 项目所属团队的团队管理员。
     * 普通团队成员即使属于该团队也不在可选范围（必须被邀请进项目）。
     */
    private Set<Long> getProjectMemberUserIds(Integer projectId) {
        Set<Long> memberIds = new HashSet<>();
        if (projectId == null) {
            return memberIds;
        }
        QueryWrapper<com.mokatest.platform.demos.domain.ui.UserRole> roleQw = new QueryWrapper<>();
        roleQw.eq("scope_id", projectId).eq("status", 1);
        for (com.mokatest.platform.demos.domain.ui.UserRole ur : userRoleMapper.selectList(roleQw)) {
            if (ur.getUserId() != null) {
                memberIds.add(ur.getUserId());
            }
        }
        com.mokatest.platform.demos.domain.ui.Project project = projectMapper.selectById(projectId);
        if (project != null && project.getTeamId() != null) {
            // 团队管理员唯一来源是 team.owner_id
            com.mokatest.platform.demos.domain.ui.Team team =
                    teamMapper.selectById(project.getTeamId());
            if (team != null && team.getOwnerId() != null) {
                memberIds.add(team.getOwnerId());
            }
        }
        return memberIds;
    }

    /**
     * 团队成员是否为团队管理员：唯一来源是 team.owner_id
     */
    private boolean isTeamAdminMember(TeamMember member) {
        if (member == null || member.getTeamId() == null || member.getUserId() == null) {
            return false;
        }
        com.mokatest.platform.demos.domain.ui.Team team = teamMapper.selectById(member.getTeamId());
        return team != null && team.getOwnerId() != null && team.getOwnerId().equals(member.getUserId());
    }

    @Override
    public SaResult getUserListByPage(UserQueryDTO userQueryDTO) {
        if (userQueryDTO == null) {
            throw new ParamIsEmptyException("缺少参数");
        }
        String loginIdAsString = StpUtil.getLoginIdAsString();

        // 创建分页对象
        Page<User> page = new Page<>(userQueryDTO.getPageNum(), userQueryDTO.getPageSize());

        // 创建查询条件
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StrUtil.isNotBlank(userQueryDTO.getUsername()), User::getUsername, userQueryDTO.getUsername())
                .eq(StrUtil.isNotBlank(userQueryDTO.getEmail()), User::getEmail, userQueryDTO.getEmail())
                .eq(userQueryDTO.getStatus() != null, User::getStatus, userQueryDTO.getStatus())
                .eq(StrUtil.isNotBlank(userQueryDTO.getNickname()), User::getNickname, userQueryDTO.getNickname())
                .eq(StrUtil.isNotBlank(userQueryDTO.getPhone()), User::getPhone, userQueryDTO.getPhone());

        // 项目维度收窄：项目级授权成员 ∪ 项目所属团队的启用成员（含团队管理员），剔除超管
        if (userQueryDTO.getProjectId() != null) {
            Set<Long> memberIds = getProjectMemberUserIds(userQueryDTO.getProjectId());
            if (memberIds.isEmpty()) {
                Page<User> emptyPage = new Page<>(userQueryDTO.getPageNum(), userQueryDTO.getPageSize());
                emptyPage.setRecords(List.of());
                return SaResult.ok().setData(emptyPage.convert(user -> {
                    UserVO vo = new UserVO();
                    BeanUtils.copyProperties(user, vo);
                    return vo;
                }));
            }
            queryWrapper.in(User::getId, memberIds)
                    .ne(User::getRole, "super_admin");
        } else if (!isSuperAdmin(loginIdAsString)) {
            // 非超管只能查同团队成员，并剔除超管账号
            Set<Long> teammateIds = getTeammateUserIds(loginIdAsString);
            if (teammateIds.isEmpty()) {
                Page<User> emptyPage = new Page<>(userQueryDTO.getPageNum(), userQueryDTO.getPageSize());
                emptyPage.setRecords(List.of());
                return SaResult.ok().setData(emptyPage.convert(user -> {
                    UserVO vo = new UserVO();
                    BeanUtils.copyProperties(user, vo);
                    return vo;
                }));
            }
            queryWrapper.in(User::getId, teammateIds)
                    .ne(User::getRole, "super_admin");
        }

        Page<User> userPage = userMapper.selectPage(page, queryWrapper);

        IPage<UserVO> userVOIPage = userPage.convert(user -> {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);
            return vo;
        });
        return SaResult.ok().setData(userVOIPage);
    }

    @Override
    public SaResult getInviteUserList(Long teamId) {
        if (teamId == null) {
            return SaResult.error("缺少团队ID");
        }
        String loginId = StpUtil.getLoginIdAsString();

        // 获取当前用户所属的所有团队
        QueryWrapper<TeamMember> myTeamsQw = new QueryWrapper<>();
        myTeamsQw.eq("user_id", loginId).eq("status", 1);
        List<TeamMember> myTeams = teamMemberMapper.selectList(myTeamsQw);
        Set<Long> myTeamIds = myTeams.stream()
                .map(TeamMember::getTeamId)
                .collect(Collectors.toSet());

        // 必须属于目标团队（超管除外）
        if (!isSuperAdmin(loginId) && !myTeamIds.contains(teamId.longValue())) {
            return SaResult.error("无权限邀请该团队成员");
        }

        // 获取目标团队已有成员
        QueryWrapper<TeamMember> targetTeamMembersQw = new QueryWrapper<>();
        targetTeamMembersQw.eq("team_id", teamId);
        List<TeamMember> targetMembers = teamMemberMapper.selectList(targetTeamMembersQw);
        Set<Long> existUserIds = targetMembers.stream()
                .map(TeamMember::getUserId)
                .collect(Collectors.toSet());

        // 可邀请的候选人：全量非超管用户，排除目标团队已有成员和自己
        // （调用方已校验必须是该团队成员，超管/团队管理员均可邀请）
        existUserIds.add(Long.valueOf(loginId));

        QueryWrapper<User> userQw = new QueryWrapper<>();
        // 排除目标团队已有成员和当前登录用户自己
        if (!existUserIds.isEmpty()) {
            userQw.notIn("id", existUserIds);
        }
        // 排除超管账号（超管不需要被邀请）
        userQw.ne("role", "super_admin");

        List<User> users = userMapper.selectList(userQw);
        List<UserVO> list = users.stream().map(user -> {
            UserVO vo = new UserVO();
            BeanUtil.copyProperties(user, vo);
            return vo;
        }).toList();
        return SaResult.ok().setData(list);
    }

    /**
     * 构建用户ID到团队角色列表的映射
     */
    private Map<Long, List<UserTeamVO>> buildUserTeamMap(List<Long> userIds) {
        Map<Long, List<UserTeamVO>> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        QueryWrapper<TeamMember> tmQw = new QueryWrapper<>();
        tmQw.in("user_id", userIds);
        List<TeamMember> members = teamMemberMapper.selectList(tmQw);
        if (members.isEmpty()) {
            return result;
        }

        Set<Long> teamIds = members.stream().map(TeamMember::getTeamId).collect(Collectors.toSet());
        Set<Long> roleIds = members.stream().map(TeamMember::getRoleId).filter(Objects::nonNull).collect(Collectors.toSet());

        // Team.id 为 Integer，TeamMember.teamId 为 Long，需要转换后查询与映射
        Set<Integer> teamIdInts = teamIds.stream().map(Long::intValue).collect(Collectors.toSet());
        Map<Integer, Team> teamMap = teamIdInts.isEmpty() ? Map.of() :
                teamMapper.selectBatchIds(teamIdInts).stream().collect(Collectors.toMap(Team::getId, t -> t));
        Map<Long, Role> roleMap = roleIds.isEmpty() ? Map.of() :
                roleMapper.selectBatchIds(roleIds).stream().collect(Collectors.toMap(Role::getId, r -> r));

        for (TeamMember member : members) {
            UserTeamVO vo = new UserTeamVO();
            vo.setTeamId(member.getTeamId());
            Team team = teamMap.get(member.getTeamId() != null ? member.getTeamId().intValue() : null);
            // 个人团队不计入用户的团队列表/团队数
            if (team != null && Integer.valueOf(1).equals(team.getIsPersonal())) {
                continue;
            }
            vo.setTeamName(team != null ? team.getTeamName() : null);
            vo.setRoleId(member.getRoleId());
            Role role = roleMap.get(member.getRoleId());
            vo.setRoleName(role != null ? role.getName() : member.getRole());
            vo.setRoleCode(role != null ? role.getCode() : member.getRole());
            vo.setStatus(member.getStatus());
            result.computeIfAbsent(member.getUserId(), k -> new ArrayList<>()).add(vo);
        }
        return result;
    }

    /**
     * 判断指定用户是否为超级管理员
     */
    private boolean isSuperAdmin(String userId) {
        User user = userMapper.selectById(userId);
        return user != null && "super_admin".equals(user.getRole());
    }
}




