package com.mokatest.platform.demos.api.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiNodeType;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiType;
import com.mokatest.platform.demos.api.domain.apiEnum.RequestMethod;
import com.mokatest.platform.demos.api.domain.requestModel.ApiExtraction;
import com.mokatest.platform.demos.api.domain.requestModel.ApiResponseExample;
import com.mokatest.platform.demos.api.domain.requestModel.AssertParameter;
import com.mokatest.platform.demos.api.domain.requestModel.AuthConfig;
import com.mokatest.platform.demos.api.domain.requestModel.Body;
import com.mokatest.platform.demos.api.domain.requestModel.MockResponse;
import com.mokatest.platform.demos.api.domain.requestModel.RequestExecuteInfo;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.api.domain.requestModel.ResponseSchema;
import com.mokatest.platform.demos.api.domain.requestModel.ScriptItem;
import com.mokatest.platform.demos.api.domain.requestModel.SqlConfig;
import com.mokatest.platform.demos.api.handler.ScriptListTypeHandler;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * API 请求配置实体类（对应数据库表 api_request）
 *
 * 功能说明：存储 API 接口的完整配置信息，包括请求路径、请求头、请求体、环境配置、数据提取规则等
 *
 * 数据库存储设计：
 *   - 复杂对象（List、Body、RequestExecuteInfo 等）使用 MyBatis-Plus 的 JacksonTypeHandler 序列化为 JSON 字符串存储
 *   - 枚举类型（RequestMethod、ApiNodeType、ApiType）使用 MyBatis-Plus 的类型处理器自动映射
 *   - @TableName(autoResultMap = true) 启用自动结果映射，支持 JSON 字段的反序列化
 *
 * 字段分类：
 *   - 基础信息：id、apiName、projectId、teamId、sort、createTime、updateTime 等
 *   - 树形结构：parentId（父节点 ID）、apiNode（节点类型：文件夹/接口）
 *   - 请求配置：requestMethod、requestPath、requestHeader、cookies、query、body
 *   - 环境配置：envInfo（基础 URL、环境变量等）
 *   - 高级功能：associationExtraction（数据提取）、apiResultAssert（断言）、preScript/postScript（脚本）
 *
 * @author JingLong
 * @since 2026-04-03
 */
@TableName(value = "api_request", autoResultMap = true)
@Data
public class ApiRequest {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 父id
     */
    private Integer parentId;

    /**
     * 所属项目id
     */
    private Integer projectId;

    /**
     * 所属团队id
     */
    private Integer teamId;

    /**
     * 接口名称
     */
    private String apiName;

    /**
     * 节点类型
     */
    private ApiNodeType apiNode;

    /**
     * 请求方式
     */
    private RequestMethod requestMethod;

    /**
     * 请求路径
     */
    private String requestPath;

    /**
     * 请求头
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequestParameter> requestHeader;

    /**
     * cookie
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequestParameter> cookies;

    /**
     * query参数
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequestParameter> query;

    /**
     * body请求体
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Body body;

    /**
     * 关联的环境执行配置
     *
     * 功能说明：包含当前接口调试/执行时使用的环境信息，如基础 URL、环境变量、环境 Cookie、环境 Header 等
     *
     * 核心字段：
     *   - baseUrl：环境基础 URL，如 https://api.example.com，与 requestPath 拼接为完整 URL
     *   - envVariables：环境变量 Map，用于请求前的变量替换（${var} / {{var}}）
     *   - envCookies：环境级 Cookie，自动附加到每个请求（预留，待实现）
     *   - envHeaders：环境级 Header，自动附加到每个请求（预留，待实现）
     *
     * @see RequestExecuteInfo
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private RequestExecuteInfo envInfo;

    /**
     * 接口类型（HTTP / SQL / TCP / WEBSOCKET）
     */
    private ApiType apiType;

    /**
     * SQL 调试配置（apiType=SQL 时使用）
     *
     * @see SqlConfig
     * @since 2026-07-28
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private SqlConfig sqlConfig;

    /**
     * 排序
     */
    private Integer sort;

    /**
     * 关联提取规则列表
     *
     * 功能说明：定义从当前接口响应中提取数据的规则，提取结果存入变量池供后续接口使用
     *
     * 数据类型变更说明：由 Object 改为 List<ApiExtraction>，
     *
     * 原因：
     *   - 明确数据类型，利用 MyBatis-Plus 的 JacksonTypeHandler 自动序列化/反序列化
     *   - Service 层可直接获取结构化数据，无需手动转换
     *   - 支持编译时类型检查，减少运行时类型转换错误
     *
     * 提取规则配置示例（JSON 存储格式）：
     * [
     *   {
     *     "type": "JSON_PATH",
     *     "expression": "$.data.token",
     *     "variableName": "token",
     *     "defaultValue": ""
     *   },
     *   {
     *     "type": "HEADER",
     *     "expression": "X-Request-Id",
     *     "variableName": "requestId",
     *     "defaultValue": "unknown"
     *   }
     * ]
     *
     * @see ApiExtraction
     * @since 2026-05-26 类型由 Object 改为 List<ApiExtraction>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ApiExtraction> associationExtraction;

    /**
     * 前置脚本列表（按 sort 排序后依次执行）
     *
     * 功能说明：
     *   - 每个脚本项包含名称、内容、启用状态
     *   - 只有 enabled=true 的脚本才会被执行
     *   - 脚本按 sort 升序排序后依次执行
     *
     * @see ScriptItem
     * @since 2026-05-27 由 String 改为 List<ScriptItem>
     */
    @TableField(typeHandler = ScriptListTypeHandler.class)
    private List<ScriptItem> preScript;

    /**
     * 后置脚本列表（按 sort 排序后依次执行）
     *
     * @see ScriptItem
     * @since 2026-05-27 由 String 改为 List<ScriptItem>
     */
    @TableField(typeHandler = ScriptListTypeHandler.class)
    private List<ScriptItem> postScript;

    /**
     * Mock 响应配置
     *
     * @see MockResponse
     * @since 2026-06-17
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private MockResponse mockResponse;

    /**
     * 接口鉴权配置（HTTP 接口调试用，变量替换后拼接到 Authorization / 自定义 Header / Query）
     *
     * @see AuthConfig
     * @since 2026-08-03
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private AuthConfig authConfig;

    /**
     * Swagger / OpenAPI 响应示例列表
     *
     * @see ApiResponseExample
     * @since 2026-07-03
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ApiResponseExample> responseExamples;

    /**
     * 响应结构定义（对标 Apifox 数据模型绑定）：可绑定数据模板或内联 schema，
     * validateEnabled=true 时执行后自动对响应体做结构校验，结果混入 assertionResults。
     *
     * @see ResponseSchema
     * @since 2026-08-06
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private ResponseSchema responseSchema;

    /**
     * 断言
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AssertParameter> apiResultAssert;

    /**
     * 来源id
     */
    private Integer sourceDratId;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建者id
     */
    private Integer createUserId;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 更新者id
     */
    private Integer updateUserId;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 删除时间
     */
    private Date deletedAt;
}