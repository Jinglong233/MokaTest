/**
 * 数据提取类型枚举
 *
 * 功能说明：定义从 HTTP 响应中提取数据的方式
 *
 * JSON_PATH   - 使用 JSONPath 表达式从 JSON 响应体中提取数据
 * REGEX       - 使用正则表达式从响应体文本中提取数据
 * HEADER      - 从 HTTP 响应头中提取指定名称的值
 * COOKIE      - 从响应的 Cookie 中提取指定名称的值
 * STATUS_CODE - 提取 HTTP 响应状态码（如 200、404）
 */
export enum ExtractType {
    JSON_PATH = "JSON_PATH",
    REGEX = "REGEX",
    HEADER = "HEADER",
    COOKIE = "COOKIE",
    STATUS_CODE = "STATUS_CODE"
}
