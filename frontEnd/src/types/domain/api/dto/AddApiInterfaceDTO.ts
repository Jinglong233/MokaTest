import {RequestMethod} from "@/types/domain/api/apiEnum/RequestMethod";
import {RequestParameter} from "@/types/domain/api/requestModel/RequestParameter";
import {Environment} from "@/types/domain/api/Environment";
import {ApiType} from "@/types/domain/api/apiEnum/ApiType";
import {AssertParameter} from "@/types/domain/api/requestModel/AssertParameter";
import {ApiExtraction} from "@/types/domain/api/requestModel/ApiExtraction";
import {useProjectStore} from "@/store";
import useTeamStore from "@/store/modules/team";
import {Body} from "@/types/domain/api/requestModel/Body";
import {ApiNodeType} from "@/types/domain/api/apiEnum/ApiNodeType";
import { RequestExecuteInfo } from '@/types/domain/api/requestModel/RequestExecuteInfo';
import {ScriptItem} from "@/types/domain/api/requestModel/ScriptItem";
import {MockResponse} from "@/types/domain/api/requestModel/MockResponse";
import {ApiResponseExample} from "@/types/domain/api/requestModel/ApiResponseExample";
import {SqlConfig} from "@/types/domain/api/requestModel/SqlConfig";
import {AuthConfig} from "@/types/domain/api/requestModel/AuthConfig";
import {ResponseSchema} from "@/types/domain/api/requestModel/ResponseSchema";

export class AddApiInterfaceDTO {
  id?: number;
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
  envInfo?: RequestExecuteInfo;

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
  associationExtraction?: ApiExtraction[];

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

  constructor(apiNode: ApiNodeType | undefined) {
    if (apiNode === ApiNodeType.FOLDER) {
      this.parentId = 0;
      (this.projectId = useProjectStore().getProjectId ?? undefined),
        (this.teamId = useTeamStore().getTeamId ?? undefined),
        (this.apiName = ''),
        (this.apiNode = ApiNodeType.FOLDER),
        (this.requestMethod = undefined),
        (this.requestPath = undefined),
        (this.requestHeader = undefined),
        (this.cookies = undefined),
        (this.query = undefined),
        (this.body = undefined),
        (this.envInfo = undefined),
        (this.apiType = undefined),
        (this.sort = 0),
        (this.associationExtraction = undefined),
        (this.preScript = undefined),
        (this.postScript = undefined),
        (this.apiResultAssert = undefined),
        (this.sourceDratId = undefined);
    } else {
      (this.parentId = 0),
        (this.projectId = useProjectStore().getProjectId ?? undefined),
        (this.teamId = useTeamStore().getTeamId ?? undefined),
        (this.apiName = '未命名接口'),
        (this.apiNode = ApiNodeType.INTERFACE),
        (this.requestMethod = RequestMethod.GET),
        (this.requestPath = '/api/example'),
        (this.requestHeader = undefined),
        (this.cookies = undefined),
        (this.query = undefined),
        (this.body = new Body()),
        (this.envInfo = new RequestExecuteInfo()),
        (this.apiType = ApiType.HTTP),
        (this.sort = 0),
        (this.associationExtraction = undefined),
        (this.preScript = undefined),
        (this.postScript = undefined),
        (this.mockResponse = new MockResponse()),
        (this.authConfig = new AuthConfig()),
        (this.apiResultAssert = undefined),
        (this.sourceDratId = 0);
    }
  }
}
