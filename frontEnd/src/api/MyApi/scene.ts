import axios from "axios";
import {Scene} from "@/types/domain/Scene";
import {SceneVO} from "@/types/vo/SceneVO";


/**
 * 获取所有目录+场景 树
 */
export function getAllSceneList(projectId: string, sceneCategory?: string) {
    return axios.get<SceneVO[]>('/api/scene/allSceneList', {
        params: {
            projectId: projectId,
            sceneCategory: sceneCategory
        }
    });
}

/**
 * 获取目录列表
 * @param projectId
 * @param sceneCategory
 */
export function getFolderList(projectId: string, sceneCategory?: string) {
    return axios.get<SceneVO[]>('/api/scene/folderList', {
        params: {
            projectId: projectId,
            sceneCategory: sceneCategory
        }
    });
}


/**
 * 添加场景
 */
export function addScene(scene: Scene) {
    return axios.post<boolean>('/api/scene/addScene', scene);
}


/**
 * 更新场景信息
 * @param scene
 */
export function updateScene(scene: Scene) {
    return axios.post<boolean>('/api/scene/updateScene', scene);
}


/**
 * 删除场景（目录 or 场景）
 * @param scene
 */
export function deleteScene(sceneId: String) {
    return axios.get<boolean>('/api/scene/deleteFolderOrScene', {
        params: {
            sceneId: sceneId
        }
    });
}

/**
 * 导入场景
 */
export function importSceneInfo(sceneInfo: any) {
    return axios.post<boolean>('/api/scene/importScene', sceneInfo);
}


/**
 * 批量导出场景 JSON
 */
export function exportScenes(sceneIds: number[]) {
    return axios.post<any>('/api/scene/exportScenes', sceneIds);
}

/**
 * 从 MokaTest 场景 JSON 批量导入场景
 */
export function importScenesJson(data: any) {
    return axios.post<boolean>('/api/scene/importScenesJson', data);
}


/**
 * 根据场景id列表，获取场景列表
 */
export function getSceneListByIds(sceneIds: number[]) {
    return axios.post<Scene[]>('/api/scene/getSceneListByIds', sceneIds)
}


/**
 * 根据id获取场景
 */
export function getSceneById(sceneId: number) {
    return axios.get<Scene>('/api/scene/getSceneById', {
        params: {
            sceneId: sceneId
        }
    })
}

/**
 * 复制场景
 */
export function cpoyScene(sceneId: number) {
    return axios.get<Scene>('/api/scene/copyScene', {
        params: {
            sceneId: sceneId
        }
    })
}


/**
 * 更新场景排序
 */
export function updateSort(sceneVoList: SceneVO[]) {
    return axios.post<boolean>('/api/scene/updateSceneSort', sceneVoList)
}

/**
 * 更新场景配置
 * @param sceneId
 * @param sceneSetting
 */
export function updateSceneSetting(sceneId: any, sceneSetting: any) {
    return axios.post<boolean>('/api/scene/updateSceneSetting', {
        sceneId,
        sceneSetting
    })
}





