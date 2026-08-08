import {AddGlobalVarDTO} from "@/types/domain/api/dto/AddGlobalVarDTO";
import axios from "axios";
import {GlobalVar} from "@/types/domain/api/GlobalVar";

/**
 * 保存或更新全局参数
 */
export function saveOrUpdateGlobalVar(addGlobalVarDTO: AddGlobalVarDTO) {
    return axios.post<any>('/api/globalVar/saveOrUpdate', addGlobalVarDTO);
}

/**
 * 获取全局参数列表
 */
export function getGlobalArgList(teamId: number) {
    return axios.get<any[]>('/api/globalVar/getGlobalArgList', {
        params: {
            teamId: teamId
        }
    });
}


/**
 * 删除指定id的全局参数
 */
export function deleteGlobalVarById(id: number) {
    return axios.get<any>('/api/globalVar/deleteById', {
        params: {
            id: id
        }
    })
}

