package com.mokatest.platform.demos.operationlog.support;

import com.mokatest.platform.demos.domain.ui.Project;
import com.mokatest.platform.demos.domain.ui.Role;
import com.mokatest.platform.demos.domain.ui.Team;
import com.mokatest.platform.demos.domain.ui.User;
import com.mokatest.platform.demos.mapper.ProjectMapper;
import com.mokatest.platform.demos.mapper.RoleMapper;
import com.mokatest.platform.demos.mapper.TeamMapper;
import com.mokatest.platform.demos.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 操作日志 SpEL 表达式专用工具 bean
 * 在 @OperationLog 注解的 targetName/description 表达式中通过 @logText 调用，
 * 用于把 ID 解析为可读名称（带缓存）
 */
@Component("logText")
@RequiredArgsConstructor
public class LogText {

    private final UserMapper userMapper;
    private final ProjectMapper projectMapper;
    private final TeamMapper teamMapper;
    private final RoleMapper roleMapper;

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 用户ID → 昵称（无昵称用用户名）
     */
    public String userName(Object id) {
        Long lid = toLong(id);
        if (lid == null) return String.valueOf(id);
        return cache.computeIfAbsent("user:" + lid, k -> {
            User user = userMapper.selectById(lid);
            if (user == null) return "用户(" + lid + ")";
            String name = user.getNickname();
            return (name != null && !name.isEmpty()) ? name : user.getUsername();
        });
    }

    /**
     * 用户ID列表 → 逗号分隔昵称
     */
    public String userNames(List<?> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return ids.stream().map(this::userName).collect(Collectors.joining("、"));
    }

    /**
     * 项目ID → 项目名
     */
    public String projectName(Object id) {
        Long lid = toLong(id);
        if (lid == null) return String.valueOf(id);
        return cache.computeIfAbsent("project:" + lid, k -> {
            Project project = projectMapper.selectById(lid);
            return project != null && project.getProjectName() != null
                    ? project.getProjectName() : "项目(" + lid + ")";
        });
    }

    /**
     * 团队ID → 团队名
     */
    public String teamName(Object id) {
        Long lid = toLong(id);
        if (lid == null) return String.valueOf(id);
        return cache.computeIfAbsent("team:" + lid, k -> {
            Team team = teamMapper.selectById(lid);
            return team != null && team.getTeamName() != null
                    ? team.getTeamName() : "团队(" + lid + ")";
        });
    }

    /**
     * 角色ID → 角色名
     */
    public String roleName(Object id) {
        Long lid = toLong(id);
        if (lid == null) return String.valueOf(id);
        return cache.computeIfAbsent("role:" + lid, k -> {
            Role role = roleMapper.selectById(lid);
            return role != null && role.getName() != null
                    ? role.getName() : "角色(" + lid + ")";
        });
    }

    private Long toLong(Object id) {
        if (id == null) return null;
        if (id instanceof Number) return ((Number) id).longValue();
        try {
            return Long.valueOf(id.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
