import {AddApiInterfaceDTO} from '@/types/domain/api/dto/AddApiInterfaceDTO';
import {ApiNodeType} from '@/types/domain/api/apiEnum/ApiNodeType';
import {ApiType} from '@/types/domain/api/apiEnum/ApiType';

/**
 * HTTP/SQL 接口步骤的「来源数据」构造工具。
 *
 * 场景中 API_REQUEST / SQL 步骤为副本模式：
 * - apiRequestId 仅用于追溯来源（可为空）
 * - apiConfig 为完整配置副本，源接口后续修改/删除不影响步骤执行
 */
export interface ApiStepSourceData {
  apiRequestId: number | null;
  apiName: string;
  apiConfig: any;
}

/**
 * 新建 HTTP 接口步骤的空白配置（与原 ApiRequestStepEditor「新建接口」一致）
 */
export const buildNewHttpStepData = (): ApiStepSourceData => {
  const newApi = new AddApiInterfaceDTO(ApiNodeType.INTERFACE);
  return {
    apiRequestId: null,
    apiName: newApi.apiName || '未命名接口',
    apiConfig: JSON.parse(JSON.stringify(newApi)),
  };
};

/**
 * 新建 SQL 接口步骤的空白配置（与 API 测试页「新建 SQL 接口」的默认模板保持一致）
 */
export const buildNewSqlStepData = (): ApiStepSourceData => {
  const newApi = new AddApiInterfaceDTO(ApiNodeType.INTERFACE);
  newApi.apiType = ApiType.SQL;
  newApi.apiName = 'SQL查询';
  newApi.requestMethod = undefined as any;
  newApi.requestPath = undefined;
  newApi.requestHeader = undefined;
  newApi.cookies = undefined;
  newApi.query = undefined;
  newApi.body = undefined;
  newApi.envInfo = undefined;
  newApi.mockResponse = undefined;
  newApi.sqlConfig = {
    sql: '',
    dbConnectionName: undefined,
    dbConfig: undefined,
    timeout: 30,
    maxRows: 1000,
    params: [],
    sqlExtractions: [],
    sqlAssertions: []
  };
  return {
    apiRequestId: null,
    apiName: newApi.apiName || 'SQL查询',
    apiConfig: JSON.parse(JSON.stringify(newApi)),
  };
};

/**
 * 引入已有接口/用例：深拷贝为步骤副本（id 置空表示副本而非引用，apiRequestId 仅作来源标记）
 */
export const buildImportedStepData = (apiDetail: any): ApiStepSourceData => {
  const apiConfigCopy = JSON.parse(JSON.stringify(apiDetail));
  apiConfigCopy.id = undefined;
  return {
    apiRequestId: apiDetail.id,
    apiName: apiDetail.apiName,
    apiConfig: apiConfigCopy,
  };
};
