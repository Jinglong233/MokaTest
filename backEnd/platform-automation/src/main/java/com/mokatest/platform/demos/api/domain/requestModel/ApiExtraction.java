package com.mokatest.platform.demos.api.domain.requestModel;

import com.mokatest.platform.demos.api.domain.apiEnum.ExtractType;
import lombok.Data;

/**
 * API 数据提取配置模型
 *
 * 功能说明：定义从 HTTP 响应中提取数据的规则，用于将响应中的关键数据提取到变量中，
 * 供后续接口请求使用（如登录后提取 token，下一个接口请求头中使用 {{token}}）
 *
 * 使用流程：
 *   - 在接口配置中设置提取规则（type + expression + variableName）
 *   - 接口执行完成后，提取引擎根据规则从响应中提取数据
 *   - 提取结果存入变量池，key 为 variableName，value 为提取到的值
 *   - 后续接口通过 {{variableName}} 或 ${variableName} 引用该变量
 *
 * 配置示例（JSON 格式存储）：
 * [
 *   {
 *     "type": "JSON_PATH",
 *     "expression": "$.data.token",
 *     "variableName": "token",
 *     "defaultValue": "",
 *     "description": "提取登录token"
 *   },
 *   {
 *     "type": "HEADER",
 *     "expression": "X-Request-Id",
 *     "variableName": "requestId",
 *     "defaultValue": "unknown"
 *   }
 * ]
 *
 * @author JingLong
 * @see ExtractType
 * @see ExtractionExecutor
 * @since 2026-05-26
 */
@Data
public class ApiExtraction {

    /**
     * 提取类型，决定从响应的哪个部分提取数据
     *
     * 可选值：
     *   - ExtractType#JSON_PATH  - 从 JSON 响应体中提取
     *   - ExtractType#REGEX      - 从响应体文本中正则提取
     *   - ExtractType#HEADER     - 从响应头中提取
     *   - ExtractType#COOKIE     - 从 Cookie 中提取
     *   - ExtractType#STATUS_CODE - 提取 HTTP 状态码
     */
    private ExtractType type;

    /**
     * 提取表达式，根据 type 的不同含义不同
     *
     * 各类型对应的 expression 格式：
     *   - JSON_PATH  : JSONPath 表达式，如 "$.data.token"
     *   - REGEX      : 正则表达式，如 "token":"([^"]+)"
     *   - HEADER     : 响应头名称，如 "Authorization"
     *   - COOKIE     : Cookie 名称，如 "sessionId"
     *   - STATUS_CODE: 无需填写（可为 null）
     */
    private String expression;

    /**
     * 变量名，提取成功后存入变量池的 key
     *
     * 后续接口通过 ${variableName} 或 {{variableName}} 引用该变量
     *
     * 示例：variableName = "token"，后续接口请求头中使用 {{token}}
     */
    private String variableName;

    /**
     * 默认值，当提取失败时使用的备用值
     *
     * 提取失败场景：
     *   - JSONPath 表达式在响应中未找到匹配
     *   - 正则表达式未匹配到内容
     *   - 响应头/Cookie 中不存在指定名称
     *   - 响应体为空
     *
     * 如果 defaultValue 也为 null，则该变量不会存入变量池
     */
    private String defaultValue;

    /**
     * 提取规则描述，用于前端展示和文档说明
     *
     * 示例："提取登录接口返回的 token"
     */
    private String description;

    /**
     * 是否禁用该提取规则
     *
     * true 表示该规则在当前接口中不生效，不会执行提取逻辑
     * false 表示规则生效（默认）
     */
    private boolean disabled = false;
}
