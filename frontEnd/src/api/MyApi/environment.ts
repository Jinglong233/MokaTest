import axios from "axios";
import {AddEnvDTO} from "@/types/domain/api/dto/AddEnvDTO";
import {Environment} from "@/types/domain/api/Environment";
import {DataBaseParameter} from "@/types/domain/api/requestModel/DataBaseParameter";

/**
 * 保存或更新环境数据
 */
export function saveOrUpdate(addEnvDTO: AddEnvDTO) {
    return axios.post<boolean>('/api/env/saveOrUpdate', addEnvDTO);
}

/**
 * 获取环境列表
 */
export function getEnvList(teamId: number) {
    return axios.get<Environment[]>('/api/env/getEnvList', {
        params: {
            teamId: teamId
        }
    });
}

/**
 * 删除环境
 */
export function deleteEnvById(id: number) {
    return axios.get<boolean>('/api/env/delete', {
        params: {
            id: id
        }
    })
}


/**
 * 复制环境
 */
export function copyEnv(id: number) {
    return axios.get<boolean>('/api/env/copy', {
        params: {
            id: id
        }
    })
}

/**
 * 测试数据库连接
 */
export function testDbConnectionApi(param: DataBaseParameter) {
    return axios.post('/api/env/db/testConnection', param);
}

