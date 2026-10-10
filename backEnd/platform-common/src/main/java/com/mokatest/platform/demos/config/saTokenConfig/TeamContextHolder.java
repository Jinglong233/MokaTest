package com.mokatest.platform.demos.config.saTokenConfig;

/**
 * 团队上下文持有者
 *
 * 使用 ThreadLocal 在当前请求线程中保存当前操作的团队ID（teamId），
 * 供 {@link StpInterfaceImpl} 在 @SaCheckPermission 注解鉴权时读取。
 *
 * teamId 由 {@link TeamContextInterceptor} 在请求进入 Controller 前解析并设置，
 * 请求结束后由拦截器清理，避免线程池复用导致的数据污染。
 *
 * 解析优先级：
 * <ol>
 *   请求 Header：X-Team-Id（推荐，前端统一传递）
 *   请求参数：teamId
 *   请求参数：projectId（反查 teamId）
 * </ol>
 */
public class TeamContextHolder {

    private static final ThreadLocal<Integer> TEAM_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程的团队ID
     */
    public static void setTeamId(Integer teamId) {
        TEAM_ID_HOLDER.set(teamId);
    }

    /**
     * 获取当前线程的团队ID
     */
    public static Integer getTeamId() {
        return TEAM_ID_HOLDER.get();
    }

    /**
     * 清除当前线程的团队ID
     */
    public static void clear() {
        TEAM_ID_HOLDER.remove();
    }
}
