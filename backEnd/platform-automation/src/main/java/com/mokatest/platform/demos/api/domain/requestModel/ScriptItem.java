package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

/**
 * 脚本项（用于前置/后置脚本列表）
 *
 * 功能说明：
 *   - 每个接口可配置多个前置脚本和多个后置脚本
 *   - 每个脚本项包含名称、内容、启用状态、排序等属性
 *   - 只有 enabled=true 的脚本才会被执行
 *   - 脚本按 sort 升序排序后依次执行
 *
 * @author JingLong
 * @since 2026-05-27
 */
@Data
public class ScriptItem {

    /**
     * 脚本唯一标识（前端生成 UUID）
     */
    private String id;

    /**
     * 脚本名称（用于展示）
     */
    private String name;

    /**
     * 脚本内容（JavaScript 代码）
     */
    private String content;

    /**
     * 是否启用
     */
    private boolean enabled = true;

    /**
     * 排序（数值越小越靠前）
     */
    private Integer sort = 0;
}
