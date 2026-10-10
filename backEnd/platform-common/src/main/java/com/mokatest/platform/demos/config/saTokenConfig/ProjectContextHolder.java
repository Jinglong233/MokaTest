package com.mokatest.platform.demos.config.saTokenConfig;

/**
 * 项目上下文持有者
 *
 * 使用 ThreadLocal 在当前请求线程中保存当前操作的项目ID（projectId），
 * 供 {@link StpInterfaceImpl} 在 @SaCheckPermission 注解鉴权时读取。</p>
 *
 * projectId 由 {@link TeamContextInterceptor} 在请求进入 Controller 前解析并设置，
 * 请求结束后由拦截器清理，避免线程池复用导致的数据污染。</p>
 *
 * 解析优先级：<ol>
 *   请求 Header：X-Project-Id（推荐，前端统一传递）
 *   请求参数：projectId
 * </ol></p>
 */
public class ProjectContextHolder {

    private static final ThreadLocal<Integer> PROJECT_ID_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前线程的项目ID
     */
    public static void setProjectId(Integer projectId) {
        PROJECT_ID_HOLDER.set(projectId);
    }

    /**
     * 获取当前线程的项目ID
     */
    public static Integer getProjectId() {
        return PROJECT_ID_HOLDER.get();
    }

    /**
     * 清除当前线程的项目ID
     */
    public static void clear() {
        PROJECT_ID_HOLDER.remove();
    }
}
