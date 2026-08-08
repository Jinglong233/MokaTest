package com.mokatest.platform.demos.api.domain.apiEnum;

/**
 * 接口鉴权类型
 * <p>
 * NONE    : 无鉴权，不附加任何认证信息
 * BEARER  : Bearer Token，生成 Authorization: Bearer {token}
 * BASIC   : Basic Auth，生成 Authorization: Basic base64(username:password)
 * API_KEY : 自定义 Key，可放到 Header 或 Query
 */
public enum AuthType {
    NONE,
    BEARER,
    BASIC,
    API_KEY
}
