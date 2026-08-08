import {RequestMethod} from "@/types/domain/api/apiEnum/RequestMethod";
import {RequestParameter} from "@/types/domain/api/requestModel/RequestParameter";
import {Environment} from "@/types/domain/api/Environment";
import {ApiType} from "@/types/domain/api/apiEnum/ApiType";
import {AssertParameter} from "@/types/domain/api/requestModel/AssertParameter";
import {ApiNodeType} from "@/types/domain/api/apiEnum/ApiNodeType";
import {Body} from '@/types/domain/api/requestModel/Body'
import {ScriptItem} from '@/types/domain/api/requestModel/ScriptItem'
import {MockResponse} from '@/types/domain/api/requestModel/MockResponse'
import {ApiResponseExample} from '@/types/domain/api/requestModel/ApiResponseExample'
import {SqlConfig} from '@/types/domain/api/requestModel/SqlConfig'
import {AuthConfig} from '@/types/domain/api/requestModel/AuthConfig'
import {ResponseSchema} from '@/types/domain/api/requestModel/ResponseSchema'
export class ApiRequest {
    /**
     * id
     */
    id?: number;

    /**
     * 父id
     */
    parentId?: number;

    /**
     * 所属项目id
     */
    projectId?: number;

    /**
     * 所属团队id
     */
    teamId?: number;

    /**
     * 接口名称
     */
    apiName?: string;

    /**
     * 节点类型
     */
    apiNode?: ApiNodeType;
    /**
     * 请求方式
     */
    requestMethod?: RequestMethod;

    /**
     * 请求路径
     */
    requestPath?: string;

    /**
     * 请求头
     */
    requestHeader?: RequestParameter[];

    /**
     * cookie
     */
    cookies?: RequestParameter[];

    /**
     * query参数
     */
    query?: RequestParameter[];

    /**
     * body请求体
     */
    body?: Body;

    /**
     * 关联的环境数据
     */
    envInfo?: Environment;

    /**
     * 接口类型（HTTP / SQL / TCP / WEBSOCKET）
     */
    apiType?: ApiType;

    /**
     * SQL 调试配置（apiType=SQL 时使用）
     */
    sqlConfig?: SqlConfig;

    /**
     * 排序
     */
    sort?: number;

    /**
     * 关联提取
     */
    associationExtraction?: Object;

    /**
     * 前置脚本列表（按 sort 排序后依次执行）
     */
    preScript?: ScriptItem[];

    /**
     * 后置脚本列表（按 sort 排序后依次执行）
     */
    postScript?: ScriptItem[];

    /**
     * Mock 响应配置
     */
    mockResponse?: MockResponse;

    /**
     * 响应定义（响应结构/数据模型，用于执行时校验）
     */
    responseSchema?: ResponseSchema;

    /**
     * 接口鉴权配置（HTTP 接口调试用）
     */
    authConfig?: AuthConfig;

    /**
     * Swagger / OpenAPI 响应示例列表
     */
    responseExamples?: ApiResponseExample[];

    /**
     * 断言
     */
    apiResultAssert?: AssertParameter[];

    /**
     * 来源id
     */
    sourceDratId?: number;

    /**
     * 创建时间
     */
    createTime?: Date;

    /**
     * 创建者id
     */
    createUserId?: number;

    /**
     * 更新时间
     */
    updateTime?: Date;

    /**
     * 更新者id
     */
    updateUserId?: number;
}