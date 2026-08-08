import axios from "axios";
import {DataTemplate} from "@/types/domain/api/DataTemplate";
import {DataTemplateTreeNode} from "@/types/vo/DataTemplateTreeVO";

/**
 * 保存/更新数据模板
 */
export function saveDataTemplate(data: DataTemplate) {
    return axios.post('/api/dataTemplate/save', data);
}

/**
 * 保存/更新文件夹
 */
export function saveDataTemplateFolder(data: DataTemplate) {
    return axios.post('/api/dataTemplate/folder/save', data);
}

/**
 * 根据项目id查询模板列表（仅 TEMPLATE 节点）
 */
export function getDataTemplateList(projectId: number) {
    return axios.get<DataTemplate[]>('/api/dataTemplate/list', {
        params: {projectId}
    });
}

/**
 * 根据项目id查询文件夹树（仅 FOLDER 节点）
 */
export function getDataTemplateFolderList(projectId: number) {
    return axios.get<DataTemplateTreeNode[]>('/api/dataTemplate/folder/list', {
        params: {projectId}
    });
}

/**
 * 根据项目id查询模板混合树（文件夹+模板）
 */
export function getDataTemplateTree(projectId: number) {
    return axios.get<DataTemplateTreeNode[]>('/api/dataTemplate/tree', {
        params: {projectId}
    });
}

/**
 * 根据id查询详情
 */
export function getDataTemplateDetail(id: number) {
    return axios.get<DataTemplate>(`/api/dataTemplate/${id}`);
}

/**
 * 删除节点（文件夹递归删除，模板仅删除自身）
 */
export function deleteDataTemplate(id: number) {
    return axios.post(`/api/dataTemplate/delete/${id}`);
}

/**
 * 删除文件夹（递归删除所有后代）
 */
export function deleteDataTemplateFolder(id: number) {
    return axios.post(`/api/dataTemplate/folder/delete/${id}`);
}

/**
 * 统一拖拽排序/移动
 */
export function updateDataTemplateSort(treeNodes: DataTemplateTreeNode[]) {
    return axios.post('/api/dataTemplate/sort', treeNodes);
}

/**
 * 复制数据模板（仅 TEMPLATE 可复制）
 */
export function copyDataTemplate(id: number) {
    return axios.post(`/api/dataTemplate/copy/${id}`);
}

/**
 * 根据模板生成单条数据
 */
export function generateData(id: number) {
    return axios.post('/api/dataTemplate/generate', null, {
        params: {id}
    });
}

/**
 * 根据模板批量生成数据
 */
export function batchGenerateData(id: number, count: number) {
    return axios.post('/api/dataTemplate/batchGenerate', null, {
        params: {id, count}
    });
}

/**
 * 批量生成并导出（JSON/CSV/EXCEL）
 */
export function exportBatchData(id: number, count: number, format: 'JSON' | 'CSV' | 'EXCEL') {
    return axios.post('/api/dataTemplate/batchGenerate/export', null, {
        params: {id, count, format},
        responseType: 'blob'
    });
}
