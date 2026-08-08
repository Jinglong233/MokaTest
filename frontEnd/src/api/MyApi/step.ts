import axios from "axios";
import {StepVO} from "@/types/vo/StepVO";
import {TestStep} from "@/types/domain/TestStep";
import {AddAdjacentStepDTO} from "@/types/dto/other/AddAdjacentStepDTO";
import {ImportExistSceneStepDTO} from "@/types/dto/other/ImportExistSceneStepDTO";

/**
 * 获取步骤列表
 * @param sceneId
 */
export function getStepList(sceneId: number) {
    return axios.get<StepVO[]>('/api/step/stepList', {
        params: {
            sceneId: sceneId
        }
    })
}


/**
 * 获取步骤详情
 * @param stepId
 */
export function getStepDetail(stepId: number) {
    return axios.get<TestStep[]>('/api/step/stepDetail', {
        params: {
            stepId: stepId
        }
    })
}

/**
 * 添加步骤
 * @param step
 */
export function addStep(step: TestStep) {
    return axios.post<StepVO[]>('/api/step/addStep', step)
}


/**
 * 添加相邻步骤
 * @param step
 */
export function addAdjacentTestStep(adjacentStepDTO: AddAdjacentStepDTO) {
    return axios.post<StepVO[]>('/api/step/addAdjacentStep', adjacentStepDTO)
}

/**
 * 批量删除步骤
 */
export function batchDelete(stepIds: number[]) {
    return axios.post<boolean>('/api/step/batchDeleteStep', stepIds)
}

/**
 * 批量启用步骤
 */
export function batchEnable(stepIds: number[]) {
    return axios.post<boolean>('/api/step/batchEnableStep', stepIds)
}

/**
 * 批量禁用步骤
 */
export function batchDisable(stepIds: number[]) {
    return axios.post<boolean>('/api/step/batchDisableStep', stepIds)
}


/**
 * 根据场景id获取步骤列表
 * @param sceneId
 */
export function getStepBySceneId(sceneId: string) {
    return axios.get<TestStep[]>('/api/step/getStepBySceneId', {
        params: {
            sceneId: sceneId
        }
    })
}

/**
 * 更新步骤
 * @param step
 */
export function updateStep(step: TestStep) {
    return axios.post<TestStep[]>('/api/step/updateStep', step)

}


/**
 * 删除步骤
 * @param stepId
 */
export function deleteStepById(stepId: number) {
    return axios.get<Boolean>('/api/step/deleteStep', {
        params: {
            stepId
        }
    })
}


/**
 * 复制步骤
 * @param stepId
 */
export function copyStepById(copyId: number) {
    return axios.get<Boolean>('/api/step/copyStep', {
        params: {
            copyId
        }
    })
}


/**
 * 更新步骤排序
 * @param updateInfo
 */
export function updateStepSort(updateInfo: Object) {
    return axios.post<Boolean>('/api/step/updateStepSort', updateInfo)

}

/**
 * 更新禁用状态
 */
export function updateDisableStatus(testStepId: number) {
    return axios.get<Boolean>('/api/step/disableStep', {
        params: {
            testStepId
        }
    })
}

/**
 * 导入已存在的场景步骤
 */
export function importExistSceneStep(existSceneStepDTO: ImportExistSceneStepDTO){
    return axios.post<Boolean>('/api/step/importExistSceneStep', existSceneStepDTO)
}
