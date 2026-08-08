import axios from "axios";

/**
 * AI 配置管理（系统级，仅超管）
 */

/** 获取当前配置（apiKey 打码返回） */
export function getAiConfig() {
    return axios.get('/api/ai/config');
}

/** 配置档案列表（apiKey 打码，生效行置顶） */
export function listAiConfigs() {
    return axios.get('/api/ai/config/list');
}

/** 保存配置（无 id 新建，有 id 更新） */
export function saveAiConfig(data: any) {
    return axios.post('/api/ai/config/save', data);
}

/** 激活指定配置为唯一生效（旧的自动停用） */
export function activateAiConfig(id: number) {
    return axios.post(`/api/ai/config/activate/${id}`);
}

/** 停用生效配置（全平台 AI 进入未启用状态） */
export function deactivateAiConfig(id: number) {
    return axios.post(`/api/ai/config/deactivate/${id}`);
}

/** 删除配置（生效中的禁止删除） */
export function deleteAiConfig(id: number) {
    return axios.post(`/api/ai/config/delete/${id}`);
}

/** 连通性测试 */
export function testAiConfig(data: any) {
    return axios.post('/api/ai/config/test', data);
}
