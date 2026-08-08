import axios from "axios";
import {AddApiInterfaceDTO} from "@/types/domain/api/dto/AddApiInterfaceDTO";
import {ApiFolderTreeVO} from "@/types/domain/api/vo/ApiFolderTreeVO";

export function saveApi(addApiInterfaceDTO: AddApiInterfaceDTO) {
    return axios.post('/api/interface/save', addApiInterfaceDTO)
}

/**
 * 保存并调试
 */
export function debug(id: number) {
    return axios.get('/api/interface/debug', {
        params: {
            id: id
        }
    })
}

/**
 *获取接口目录列表
 */
export function getFolderList(projectId: number) {
    return axios.get<AddApiInterfaceDTO[]>('/api/interface/folderList', {
        params: {
            projectId: projectId
        }
    })
}

/**
 * 获取接口树状列表
 */
export function getApiTreeList(projectId: number) {
    return axios.get<AddApiInterfaceDTO[]>('/api/interface/apiListTree', {
        params: {
            projectId: projectId
        }
    })
}

/**
 * 根据id删除api节点
 */
export function deleteApi(id: number) {
    return axios.get<boolean>('/api/interface/deleteApi', {
        params: {
            id: id
        }
    })
}

/**
 * 更新api树列表
 */
export function updateApiTreeList(apiList: ApiFolderTreeVO[]) {
    return axios.post<boolean>('/api/interface/updateApiSort', apiList)
}

/**
 * 根据id获取api节点
 */
export function getApiById(id: number) {
  return axios.get<AddApiInterfaceDTO>('/api/interface/getApiById', {
    params: {
      id: id,
    },
  });
}

/**
 * 复制接口
 */
export function copyApi(id: number) {
  return axios.get<AddApiInterfaceDTO>('/api/interface/copyApi', {
    params: {
      id: id,
    },
  });
}

/**
 * 导入 Swagger / OpenAPI 文档
 */
export function importSwagger(formData: FormData) {
  return axios.post('/api/interface/importSwagger', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  });
}

/**
 * 保存接口用例
 */
export function saveCase(addApiInterfaceDTO: AddApiInterfaceDTO) {
  return axios.post('/api/interface/saveCase', addApiInterfaceDTO);
}

/**
 * 查询接口下的用例列表
 */
export function getCases(sourceId: number) {
  return axios.get<AddApiInterfaceDTO[]>('/api/interface/cases', {
    params: {
      sourceId: sourceId,
    },
  });
}

/**
 * 获取接口-用例关系树
 * 结构：接口（父节点）-> 用例（子节点）
 */
export function getInterfaceCaseTree(projectId: number) {
  return axios.get<ApiFolderTreeVO[]>('/api/interface/interfaceCaseTree', {
    params: {
      projectId: projectId,
    },
  });
}

/**
 * 根据API配置直接调试（不查询数据库）
 */
export function debugByConfig(apiRequest: any) {
  return axios.post('/api/interface/debugByConfig', apiRequest);
}