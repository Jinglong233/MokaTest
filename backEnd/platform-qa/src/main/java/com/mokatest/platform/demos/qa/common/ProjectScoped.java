package com.mokatest.platform.demos.qa.common;

/**
 * 归属项目的实体标记接口。
 *
 * <p>实现该接口的实体（如 Requirement/TestCase/Bug/TestPlan）自带 {@code projectId} 字段，
 * 可交由 {@link ProjectEntityGuard#requireInProject(ProjectScoped)} 统一做项目数据边界（IDOR）校验：
 * 实体不存在或归属项目与当前请求上下文不一致时直接抛业务异常。</p>
 *
 * <p>设计动机：Phase 0 安全基座要求所有按 id 读/改/删的 QA Service 入口处先查实体再过 Guard，
 * 杜绝跨项目读写（头部传 A 项目 id 却操作 B 项目数据）。</p>
 */
public interface ProjectScoped {

    /**
     * 获取实体所属项目ID。
     */
    Integer getProjectId();
}
