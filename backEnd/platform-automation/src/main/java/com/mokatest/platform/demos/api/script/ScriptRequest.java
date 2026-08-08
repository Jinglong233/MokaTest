package com.mokatest.platform.demos.api.script;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 脚本中可访问的请求信息
 *
 * 前置脚本可修改：headers（部分字段）、body
 * 所有字段只读：url、method
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptRequest {

    /** 请求 URL */
    private String url;

    /** 请求方法 GET/POST/PUT/DELETE/PATCH */
    private String method;

    /** 请求头（可修改） */
    private Map<String, String> headers;

    /** 请求体字符串（可修改） */
    private String body;

    /** Query 参数（可修改） */
    private Map<String, String> query;

    /** Cookie 参数（可修改） */
    private Map<String, String> cookies;
}
