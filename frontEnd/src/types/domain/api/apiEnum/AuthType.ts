/**
 * 接口鉴权类型枚举
 * 使用字符串值以匹配后端 Jackson 序列化（enum name → "BEARER"）
 */
export enum AuthType {
    NONE = "NONE",
    BEARER = "BEARER",
    BASIC = "BASIC",
    API_KEY = "API_KEY"
}
