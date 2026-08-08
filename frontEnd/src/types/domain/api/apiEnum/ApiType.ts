/**
 * 接口类型枚举
 * 使用字符串值以匹配后端 Jackson 序列化（enum name → "SQL"）
 * 注意：新建/编辑时 JSON.stringify 输出 "SQL"，与后端返回格式一致
 */
export enum ApiType {
    HTTP = "HTTP",
    TCP = "TCP",
    SQL = "SQL",
    WEBSOCKET = "WEBSOCKET"
}
