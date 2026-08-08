package com.mokatest.platform.demos.api.domain.requestModel;

import com.mokatest.platform.demos.api.domain.apiEnum.AuthType;
import lombok.Data;

/**
 * 接口鉴权配置（HTTP 接口调试时自动附加认证信息）
 * <p>
 * 执行时机：变量替换之后、发送请求之前，由执行器解析并拼接到
 * Authorization 请求头（BEARER/BASIC）或自定义 Header/Query（API_KEY）。
 * 优先级低于接口 Header 表：用户在 Header 中手写了同名头时以手写为准。
 * <p>
 * token / username / password / keyValue 均支持 ${var} 变量引用。
 *
 * @author JingLong
 * @since 2026-08-03
 */
@Data
public class AuthConfig {
    /**
     * 鉴权类型（默认 NONE）
     */
    private AuthType authType = AuthType.NONE;

    /**
     * BEARER：token 值（支持 ${var}）
     */
    private String token;

    /**
     * BASIC：用户名（支持 ${var}）
     */
    private String username;

    /**
     * BASIC：密码（支持 ${var}）
     */
    private String password;

    /**
     * API_KEY：参数名（Header 名或 Query 参数名）
     */
    private String keyName;

    /**
     * API_KEY：参数值（支持 ${var}）
     */
    private String keyValue;

    /**
     * API_KEY：附加位置，header / query（默认 header）
     */
    private String keyIn = "header";
}
