package com.mokatest.platform.demos.api.domain.requestModel;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.List;

/**
 * Swagger / OpenAPI 响应示例
 *
 * 用于保存导入的 responses 示例数据，支持按状态码展示多个响应。
 *
 * @author JingLong
 * @since 2026-07-03
 */
@Data
public class ApiResponseExample {

    /**
     * HTTP 状态码，如 200、400、500
     */
    private int statusCode;

    /**
     * 响应描述，如 "成功"、"参数错误"
     */
    private String description;

    /**
     * 响应 content-type，如 application/json
     */
    private String contentType;

    /**
     * 响应头列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequestParameter> headers;

    /**
     * 响应体（RAW 文本）
     */
    private String body;

    /**
     * 响应体模式：RAW / RULES
     * 目前只支持 RAW
     */
    private String bodyMode = "RAW";
}
