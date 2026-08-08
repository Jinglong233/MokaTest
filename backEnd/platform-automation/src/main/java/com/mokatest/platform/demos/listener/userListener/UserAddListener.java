package com.mokatest.platform.demos.listener.userListener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mokatest.platform.demos.domain.ui.Role;
import com.mokatest.platform.demos.domain.ui.Team;
import com.mokatest.platform.demos.domain.ui.TeamMember;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.RoleMapper;
import com.mokatest.platform.demos.mapper.TeamMapper;
import com.mokatest.platform.demos.mapper.TeamMemberMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户新增监听器：为新注册用户自动创建个人团队
 */
@Slf4j
@Component
public class UserAddListener {

    private static final String PERSONAL_TEAM_PREFIX = "个人_";
    private static final int TEAM_NAME_MAX_LEN = 10;

    @Resource
    private UserMapper userMapper;

    @Resource
    private TeamMapper teamMapper;

    @Resource
    private TeamMemberMapper teamMemberMapper;

    @Resource
    private RoleMapper roleMapper;

    @EventListener
    @Transactional
    public void handleUserAdd(UserAddEvent event) throws Exception {
        String userId = event.getUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new Exception("用户不存在");
        }

        // 如果用户已经加入任何团队（例如管理员预分配），则不再创建个人团队
        QueryWrapper<TeamMember> existMemberWrapper = new QueryWrapper<>();
        existMemberWrapper.eq("user_id", userId);
        Long existCount = teamMemberMapper.selectCount(existMemberWrapper);
        if (existCount != null && existCount > 0) {
            log.info("用户已存在团队绑定，跳过个人团队创建，userId={}", userId);
            return;
        }

        // 生成个人团队名称
        String teamName = generatePersonalTeamName(user.getUsername(), userId);

        // 创建个人团队
        Team team = new Team();
        team.setTeamName(teamName);
        team.setTeamNumber(1);
        team.setStatus(1);
        team.setIsPersonal(1);
        team.setDescription("个人工作空间");
        team.setCreateUserId(userId);
        team.setUpdateUserId(userId);
        // 团队管理员唯一来源是 team.owner_id
        team.setOwnerId(Long.valueOf(userId));
        if (teamMapper.insert(team) <= 0) {
            throw new Exception("创建个人团队失败");
        }

        // 将用户绑定为团队管理员（扁平模型角色编码 team_admin，兼容旧 admin）
        Long adminRoleId = getTeamAdminRoleId();
        TeamMember teamMember = new TeamMember();
        teamMember.setTeamId(Long.valueOf(team.getId()));
        teamMember.setUserId(Long.valueOf(userId));
        teamMember.setRoleId(adminRoleId);
        teamMember.setRole("team_admin");
        teamMember.setStatus(1);
        if (teamMemberMapper.insert(teamMember) <= 0) {
            throw new Exception("绑定个人团队失败");
        }

        log.info("为用户创建个人团队成功，userId={}，teamId={}，teamName={}", userId, team.getId(), teamName);
    }

    /**
     * 生成个人团队名称
     * 规则：个人_ + 用户名前缀，总长度控制在 5-10 字符
     */
    private String generatePersonalTeamName(String username, String userId) {
        if (username == null) {
            username = "user";
        }
        int maxNamePartLen = TEAM_NAME_MAX_LEN - PERSONAL_TEAM_PREFIX.length();
        String namePart = username.substring(0, Math.min(username.length(), maxNamePartLen));
        String teamName = PERSONAL_TEAM_PREFIX + namePart;

        // 处理极小概率的冲突：追加用户ID后3位并截断到10字符
        QueryWrapper<Team> nameWrapper = new QueryWrapper<>();
        nameWrapper.eq("team_name", teamName);
        if (teamMapper.selectOne(nameWrapper) != null) {
            String suffix = "_" + userId.substring(Math.max(0, userId.length() - 3));
            int maxBaseLen = TEAM_NAME_MAX_LEN - suffix.length();
            teamName = teamName.substring(0, Math.min(teamName.length(), maxBaseLen)) + suffix;
        }
        return teamName;
    }

    /**
     * 获取团队管理员系统角色ID（扁平模型 team_admin，兼容旧 admin）
     */
    private Long getTeamAdminRoleId() {
        QueryWrapper<Role> wrapper = new QueryWrapper<>();
        wrapper.in("code", java.util.Arrays.asList("team_admin", "admin")).isNull("team_id");
        wrapper.last("LIMIT 1");
        Role role = roleMapper.selectOne(wrapper);
        return role != null ? role.getId() : null;
    }
}
