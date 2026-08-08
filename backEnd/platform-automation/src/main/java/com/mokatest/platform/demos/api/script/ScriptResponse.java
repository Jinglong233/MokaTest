package com.mokatest.platform.demos.api.script;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 脚本中可访问的响应信息（后置脚本只读）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScriptResponse {

    /** HTTP 状态码 */
    private int statusCode;

    /** 状态消息 */
    private String statusMessage;

    /** 响应头 */
    private Map<String, String> headers;

    /** 响应体字符串 */
    private String body;

    /** 响应时间（毫秒） */
    private long responseTimeMs;
}
