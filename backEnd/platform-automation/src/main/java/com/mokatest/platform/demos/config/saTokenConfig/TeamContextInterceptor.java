package com.mokatest.platform.demos.config.saTokenConfig;

import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 团队上下文拦截器
 *
 * 在请求进入 Controller 前解析并设置当前操作的 teamId，
 * 确保后续 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解触发时，
 * {@link StpInterfaceImpl} 能够拿到正确的团队上下文来查询用户权限。
 *
 * 解析优先级：
 * <ol>
 *   请求 Header：X-Team-Id（最推荐，前端统一传递当前选中团队）
 *   请求参数：teamId
 *   请求参数：projectId（通过 ProjectPermissionChecker 反查 teamId）
 * </ol>
 *
 * 解析失败不会阻断请求，而是让 teamId 保持为空；
 * 此时 StpInterfaceImpl 会返回空权限列表，@SaCheckPermission 校验将按无权限处理。
 */
@Component
public class TeamContextInterceptor implements HandlerInterceptor {

    /**
     * 前端传递当前团队ID的请求头名称
     */
    public static final String TEAM_ID_HEADER = "X-Team-Id";

    /**
     * 前端传递当前项目ID的请求头名称
     */
    public static final String PROJECT_ID_HEADER = "X-Project-Id";

    @Autowired
    private ProjectPermissionChecker projectPermissionChecker;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // SSE（SseEmitter）完成时容器会以 ASYNC 类型二次分发请求，直接放行
        if (request.getDispatcherType() == jakarta.servlet.DispatcherType.ASYNC) {
            return true;
        }
        try {
            Integer teamId = resolveTeamId(request);
            if (teamId != null) {
                TeamContextHolder.setTeamId(teamId);
            }
            Integer projectId = resolveProjectId(request);
            if (projectId != null) {
                ProjectContextHolder.setProjectId(projectId);
            }
        } catch (Exception e) {
            // 解析失败不阻断请求，交给 @SaCheckPermission 自行判断（无 teamId 时权限列表为空）
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束后必须清理 ThreadLocal，防止线程池复用导致上下文泄漏
        TeamContextHolder.clear();
        ProjectContextHolder.clear();
    }

    /**
     * 按优先级解析当前请求对应的项目ID：Header(X-Project-Id) > 参数 projectId
     */
    private Integer resolveProjectId(HttpServletRequest request) {
        String header = request.getHeader(PROJECT_ID_HEADER);
        if (header != null && !header.isEmpty()) {
            try {
                return Integer.valueOf(header);
            } catch (NumberFormatException ignored) {
            }
        }
        String param = request.getParameter("projectId");
        if (param != null && !param.isEmpty()) {
            try {
                return Integer.valueOf(param);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * 按优先级解析当前请求对应的团队ID
     */
    private Integer resolveTeamId(HttpServletRequest request) {
        // 1. Header 优先级最高
        String teamIdHeader = request.getHeader(TEAM_ID_HEADER);
        if (teamIdHeader != null && !teamIdHeader.isEmpty()) {
            try {
                return Integer.valueOf(teamIdHeader);
            } catch (NumberFormatException ignored) {
            }
        }

        // 2. 请求参数 teamId
        String teamIdParam = request.getParameter("teamId");
        if (teamIdParam != null && !teamIdParam.isEmpty()) {
            try {
                return Integer.valueOf(teamIdParam);
            } catch (NumberFormatException ignored) {
            }
        }

        // 3. 请求参数 projectId 反查 teamId
        String projectIdParam = request.getParameter("projectId");
        if (projectIdParam != null && !projectIdParam.isEmpty()) {
            try {
                Integer projectId = Integer.valueOf(projectIdParam);
                return projectPermissionChecker.getTeamIdByProjectId(projectId);
            } catch (NumberFormatException ignored) {
            }
        }

        return null;
    }
}
