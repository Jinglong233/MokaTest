export enum ApiAssertType {
    // 响应头
    HEADER = "HEADER",
    // 响应体
    BODY = "BODY",
    // 响应码
    STATUS_CODE = "STATUS_CODE",
    // 响应时间
    RESPONSE_TIME = "RESPONSE_TIME",
    // 自定义
    CUSTOM = "CUSTOM",
    // 响应结构校验（由响应定义自动产生，不允许手动配置）
    SCHEMA = "SCHEMA"
}

