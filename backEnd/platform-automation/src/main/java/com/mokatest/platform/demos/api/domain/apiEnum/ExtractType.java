package com.mokatest.platform.demos.api.domain.apiEnum;

/**
 * API 测试数据提取类型枚举
 *
 * 功能说明：定义从 HTTP 响应中提取数据的方式，支持多种提取策略
 *
 * 使用场景：在接口配置中指定提取规则，调试执行后从响应中提取数据存入变量，供后续接口使用
 *
 * 各提取类型说明：
 *   - JSON_PATH  : 使用 JSONPath 表达式从 JSON 响应体中提取数据
 *   - REGEX      : 使用正则表达式从响应体文本中提取数据
 *   - HEADER     : 从 HTTP 响应头中提取指定名称的值
 *   - COOKIE     : 从响应的 Cookie 中提取指定名称的值
 *   - STATUS_CODE: 提取 HTTP 状态码（如 200、404）
 *
 * 示例：
 *   JSON_PATH  + "$.data.token"       → 从 JSON 中提取 token 字段
 *   REGEX      + "token=([a-z0-9]+)"  → 从文本中正则提取 token
 *   HEADER     + "Authorization"      → 提取响应头 Authorization 的值
 *   COOKIE     + "sessionId"          → 提取 Cookie 中 sessionId 的值
 *   STATUS_CODE + null                → 提取状态码（无需表达式）
 *
 * @author JingLong
 * @see ApiExtraction
 * @see ExtractionExecutor
 * @since 2026-05-26
 */
public enum ExtractType {

    /**
     * JSONPath 提取
     *
     * 使用 Jayway JSONPath 库从 JSON 响应体中提取数据
     *
     * expression 格式示例：
     *   - $.data.token      : 提取根对象 data 下的 token 字段
     *   - $.list[0].id      : 提取列表第一个元素的 id
     *   - $..name           : 递归提取所有 name 字段
     */
    JSON_PATH,

    /**
     * 正则提取
     *
     * 使用 Java 正则表达式从响应体文本中提取数据
     *
     * expression 格式说明：
     *   - 如果正则包含捕获组，返回第一个捕获组 (group(1)) 的内容
     *   - 如果没有捕获组，返回整个匹配的内容 (group(0))
     *
     * expression 示例："token":"([^"]+)"
     */
    REGEX,

    /**
     * 响应头提取
     *
     * 从 HTTP 响应头中提取指定名称的值
     *
     * expression 为响应头的名称（不区分大小写）
     *
     * 示例：expression = "Content-Type" 提取 Content-Type 头
     */
    HEADER,

    /**
     * Cookie 提取
     *
     * 从响应的 Cookie 中提取指定名称的值
     *
     * expression 为 Cookie 的名称
     *
     * 示例：expression = "sessionId" 提取 sessionId 的 Cookie 值
     */
    COOKIE,

    /**
     * 状态码提取
     *
     * 提取 HTTP 响应状态码（如 200、404、500）
     *
     * 不需要 expression，固定返回 Integer 类型的状态码
     */
    STATUS_CODE
}
