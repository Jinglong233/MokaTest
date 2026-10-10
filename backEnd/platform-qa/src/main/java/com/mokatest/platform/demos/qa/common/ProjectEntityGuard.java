package com.mokatest.platform.demos.qa.common;

import com.mokatest.platform.demos.config.saTokenConfig.ProjectContextHolder;
import com.mokatest.platform.demos.exception.BusinessException;

/**
 * 项目数据边界（IDOR）统一守卫。
 *
 * <p>所有 QA 模块按 id 读/改/删的 Service 方法，入口处先查出实体，再过本守卫：
 * <ul>
 *   <li>实体为 null → {@link BusinessException}("数据不存在")</li>
 *   <li>实体 projectId 与 {@link ProjectContextHolder#getProjectId()} 不一致 →
 *       {@link BusinessException}("无权操作该项目数据")</li>
 * </ul>
 *
 * <p>硬性约束（仓库级要求）：LLM 输出/工具参数中的 projectId/userId 一律不信任，
 * 由服务端基于请求头 X-Project-Id 解析的上下文收口；本守卫是该约束在 QA 数据层的落地。</p>
 *
 * @see ProjectScoped
 */
public final class ProjectEntityGuard {

    private ProjectEntityGuard() {
    }

    /**
     * 校验实体存在且归属当前项目，校验通过原样返回以支持链式使用。
     *
     * @param entity 已按 id 查出的实体（须实现 {@link ProjectScoped}）
     * @param <T>    实体类型
     * @return 传入的实体本身（便于 {@code ProjectEntityGuard.requireInProject(getById(id))} 链式调用）
     * @throws BusinessException 实体不存在或归属项目不匹配
     */
    public static <T extends ProjectScoped> T requireInProject(T entity) {
        if (entity == null) {
            throw new BusinessException("数据不存在");
        }
        Integer ctxProjectId = ProjectContextHolder.getProjectId();
        if (ctxProjectId == null || !ctxProjectId.equals(entity.getProjectId())) {
            throw new BusinessException("无权操作该项目数据");
        }
        return entity;
    }
}
