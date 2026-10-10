package com.mokatest.platform.demos.qa.ai.agent;

import com.alibaba.fastjson.JSONObject;

/**
 * Agent 工具接口（Phase 5）
 *
 * 工具实现里只允许用 ctx 的值（projectId/userId），忽略 args 中的 projectId/userId 字段。
 * 每个工具都是薄封装，内部调现有 Service，天然继承 Phase 0 的 ProjectEntityGuard。
 */
public interface AgentTool {

    /** 工具名称（唯一标识） */
    String name();

    /** 工具描述（给模型看的说明） */
    String description();

    /** 参数 JSON Schema（给模型看的参数格式） */
    String paramSchema();

    /** 所需权限码 */
    String requiredPermission();

    /** 执行工具 */
    String execute(AgentContext ctx, JSONObject args);
}
