import axios from "axios";
import {Scene} from "@/types/domain/Scene";
import {ElementVO} from "@/types/vo/ElementVO";
import {ElementQueryDTO} from "@/types/dto/queryDTO/ElementQueryDTO";
import {SceneVO} from "@/types/vo/SceneVO";

/**
 * 获取目录列表
 */
export function getFolderList(projectId: string) {
    return axios.get<ElementVO[]>('/api/element/folderList', {
        params: {
            projectId: projectId
        }
    });
}


/**
 * 添加（元素 or 目录）
 */
export function add(element: Element) {
    return axios.post<ElementVO[]>('/api/element/add', element);
}


/**
 * 更新（元素 or 目录）
 */
export function update(element: Element) {
    return axios.post<boolean>('/api/element/update', element);
}


/**
 * 根据目录id获取文件下的元素列表
 * @param folderId
 */
export function getElementListByFolderId(folderInfo: Object) {
    return axios.post<ElementVO[]>('/api/element/getElementListByFolderId', folderInfo);
}

/**
 * 根据项目id获取所有元素
 * @param projectId
 */
export function getAllElementByProjectId(projectId: string) {
    return axios.get<ElementVO[]>('/api/element/allElementList', {
        params: {
            projectId: projectId
        }
    });
}


/**
 * 删除元素 or 目录
 * @param elementId
 */
export function deleteElementOrFolder(elementId: string) {
    return axios.get<Boolean>('/api/element/deleteElementOrFolder', {
        params: {
            elementId: elementId
        }
    });
}


/**
 * 搜索元素
 */
export function pageElementList(element: ElementQueryDTO) {
    return axios.post<Element[]>('/api/element/pageElementList', element);
}


/**
 * 搜索元素（非分页）
 */
export function getElementList(element: any) {
    return axios.post<Element[]>('/api/element/getElementList', element);
}

/**
 * 根据ids批量删除元素
 * @param elementIds
 */
export function deleteElementBatch(elementIds: string[]) {
    return axios.post<Boolean>('/api/element/deleteElementBatch', elementIds);
}

/**
 * 根据id获取元素详情
 * @param elementId
 */
export function getElementById(elementId: number) {
    return axios.get<Element>('/api/element/getElementById', {
        params: {
            elementId: elementId
        }
    });
}

/**
 * 更新元素/目录排序
 */
export function updateElementSort(elementVoList: ElementVO[]) {
    return axios.post<boolean>('/api/element/updateElementSort', elementVoList)
}




