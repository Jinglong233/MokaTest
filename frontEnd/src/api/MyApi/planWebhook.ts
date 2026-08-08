import axios from 'axios';
import { PlanWebhook } from '@/types/domain/PlanWebhook';

/**
 * Webhook 通知配置 API
 */

/**
 * 查询项目下的 Webhook 配置列表
 */
export function listPlanWebhooks(projectId: number) {
    return axios.get<any>('/api/planWebhook/list', {
        params: { projectId }
    });
}

/**
 * 新增 Webhook 配置
 */
export function savePlanWebhook(planWebhook: PlanWebhook) {
    return axios.post<any>('/api/planWebhook/save', planWebhook);
}

/**
 * 更新 Webhook 配置
 */
export function updatePlanWebhook(planWebhook: PlanWebhook) {
    return axios.post<any>('/api/planWebhook/update', planWebhook);
}

/**
 * 删除 Webhook 配置
 */
export function deletePlanWebhook(id: number) {
    return axios.get<any>('/api/planWebhook/delete', {
        params: { id }
    });
}

/**
 * 测试发送
 */
export function testPlanWebhook(planWebhook: PlanWebhook) {
    return axios.post<any>('/api/planWebhook/test', planWebhook);
}
