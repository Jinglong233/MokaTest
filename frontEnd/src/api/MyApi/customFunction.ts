import axios from "axios";
import {CustomFunction} from "@/types/domain/api/CustomFunction";

/**
 * 保存/更新自定义函数
 */
export function saveCustomFunction(data: CustomFunction) {
    return axios.post('/api/customFunction/save', data);
}

/**
 * 根据项目id查询函数列表
 */
export function getCustomFunctionList(projectId: number) {
    return axios.get<CustomFunction[]>('/api/customFunction/list', {
        params: {projectId}
    });
}

/**
 * 根据id查询详情
 */
export function getCustomFunctionDetail(id: number) {
    return axios.get<CustomFunction>(`/api/customFunction/${id}`);
}

/**
 * 删除自定义函数（逻辑删除）
 */
export function deleteCustomFunction(id: number) {
    return axios.post(`/api/customFunction/delete/${id}`);
}

export interface CustomFunctionTestRunResult {
    success: boolean;
    value: string;
    errorMessage?: string;
    consoleLogs?: string[];
    executionTimeMs?: number;
}

/**
 * 试运行：用示例参数在沙箱中执行函数体
 */
export function testRunCustomFunction(id: number, args: any[]) {
    return axios.post<CustomFunctionTestRunResult>('/api/customFunction/testRun', {id, args});
}
