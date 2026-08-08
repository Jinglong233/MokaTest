package com.mokatest.platform.demos.api.domain.dto;

import com.baomidou.mybatisplus.annotation.TableField;
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

import java.util.List;

/**
 * @Description: 接口存储传输对象
 * @Author: JingLong
 * @DateTime: 2026/4/3 15:13
 */
@Data
public class AddApiInterfaceDTO {

    private Integer id;

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
     * 关联的环境数据
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private RequestExecuteInfo envInfo;

    /**
     * 接口类型（HTTP / SQL / TCP / WEBSOCKET）
     */
    private ApiType apiType;

    /**
     * SQL 调试配置（apiType=SQL 时使用）
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
     * 定义从当前接口响应中提取数据的规则，提取结果存入变量池供后续接口使用
     *
     * @see ApiExtraction
     * @since 2026-05-26 类型由 Object 改为 List<ApiExtraction>
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ApiExtraction> associationExtraction;

    /**
     * 前置脚本列表（按 sort 排序后依次执行）
     *
     * @see ScriptItem
     * @since 2026-05-27 由 Object 改为 List<ScriptItem>
     */
    @TableField(typeHandler = ScriptListTypeHandler.class)
    private List<ScriptItem> preScript;

    /**
     * 后置脚本列表（按 sort 排序后依次执行）
     *
     * @see ScriptItem
     * @since 2026-05-27 由 Object 改为 List<ScriptItem>
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
     * 接口鉴权配置（HTTP 接口调试用）
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
     * 响应结构定义（绑定数据模板/内联 schema/覆盖/校验开关）
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


}