package com.mokatest.platform.demos.api.domain.requestModel;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.List;

/**
 * Mock 响应配置
 *
 * 用于在接口调试/场景执行时模拟后端返回，避免依赖真实服务。
 *
 * @author JingLong
 * @since 2026-06-17
 */
@Data
public class MockResponse {

    /**
     * 是否启用 Mock
     */
    private boolean enabled;

    /**
     * 响应状态码
     */
    private int statusCode = 200;

    /**
     * 响应头列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequestParameter> headers;

    /**
     * 响应体（JSON / XML / 文本均可，RAW 模式下使用）
     */
    private String body;

    /**
     * 响应体生成模式：RAW（原始 JSON）/ RULES（字段规则）
     */
    private String bodyMode = "RAW";

    /**
     * 字段规则根节点（bodyMode = RULES 时使用）。
     * 旧数据可能为数组，通过 {@link MockFieldRule} 类级反序列化器兼容。
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private MockFieldRule rules;

    /**
     * 模拟延迟，单位毫秒
     */
    private int delayMs;
}
