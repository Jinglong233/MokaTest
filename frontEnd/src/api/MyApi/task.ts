import axios from "axios";


/**
 * 执行任务
 * 返回报告ID
 */
export function executionTask(planId: number) {
    return axios.post<number>('/api/task/execute/' + planId);
}

/**
 * 激活计划
 * @param planId
 */
export function activeTask(planId: number) {
    return axios.post<boolean>('/api/task/active/' + planId);
}

/**
 * 停止计划
 * @param planId
 */
export function cancelTask(planId: number) {
    return axios.post<boolean>('/api/task/stop/' + planId);
}





