package com.mokatest.platform.demos.api.domain.apiEnum;

/**
 * @Description:
 * @Author: JingLong
 * @DateTime: 2026/4/3 14:28
 */
public enum ApiAssertType {
    // 响应头
    HEADER,
    // 响应体
    BODY,
    // 响应码
    STATUS_CODE,
    // 响应时间
    RESPONSE_TIME,
    // 自定义
    CUSTOM,
    // 响应结构校验（由接口响应定义 responseSchema 自动产生，非手动配置）
    SCHEMA
}
