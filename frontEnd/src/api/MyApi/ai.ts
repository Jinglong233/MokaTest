import axios from "axios";
import { streamPost } from './aiStream';

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

// ===================== AI 需求评审 =====================

/** Agent 化评审（SSE，status/progress/done/error，ChatMemory 多轮记忆，结构化输出） */
export function streamReviewAgent(
    body: { requirementId: number; message: string },
    callbacks: { onStatus?: (s: string) => void; onProgress?: (d: any) => void; onDone?: (d: any) => void; onError?: (m: string) => void },
    signal?: AbortSignal,
) {
    return streamPost('/api/qa/ai/review/agent/chat', body, {
        onDone: callbacks.onDone,
        onError: callbacks.onError,
        onEvent: (name: string, data: any) => {
            if (name === 'status' && callbacks.onStatus) callbacks.onStatus(data);
            if (name === 'progress' && callbacks.onProgress) callbacks.onProgress(data);
        },
    } as any, signal);
}

/** 清除评审记忆（重新开始） */
export function clearReviewMemory(requirementId: number) {
    return axios.post('/api/qa/ai/review/agent/clear-memory', null, { params: { requirementId } });
}

/** 查询是否有进行中的评审（断线恢复检测） */
export function getReviewProgressStatus(requirementId: number) {
    return axios.get(`/api/qa/ai/review/agent/${requirementId}/progress-status`);
}

/** 恢复评审进度（SSE）：后端一次性回放全量进度快照，后续 progress/done 正常推送 */
export function resumeReviewProgress(
    requirementId: number,
    callbacks: {
        onRestore?: (items: any[]) => void;
        onProgress?: (data: any) => void;
        onDone?: (d: any) => void;
        onError?: (m: string) => void;
    },
    signal?: AbortSignal,
) {
    return streamPost(`/api/qa/ai/review/agent/${requirementId}/resume-progress`, {}, {
        onRestore: callbacks.onRestore,
        onDone: callbacks.onDone,
        onError: callbacks.onError,
        onEvent: (name: string, data: any) => {
            if (name === 'progress' && callbacks.onProgress) callbacks.onProgress(data);
        },
    } as any, signal);
}

/** 锁定评审（完成评审，锁定当前结果） */
export function finalizeReview(requirementId: number) {
    return axios.post('/api/qa/ai/review/agent/finalize', null, { params: { requirementId } });
}

/** SSE 评审（旧 4 专家并行，兼容保留） */
export function streamReview(
    body: { requirementId?: number; force?: boolean; instruction?: string },
    callbacks: { onDone?: (d: any) => void; onReused?: (d: any) => void; onEvent?: (name: string, d: any) => void; onError?: (m: string) => void },
    signal?: AbortSignal,
) {
    return streamPost('/api/qa/ai/review/stream', body, callbacks, signal);
}

/** 评审历史列表 */
export function listReviews(requirementId?: number, page?: number, pageSize?: number) {
    return axios.get('/api/qa/ai/review/list', { params: { requirementId, page, pageSize } });
}

/** 最新评审 */
export function getLatestReview(requirementId?: number) {
    return axios.get('/api/qa/ai/review/latest', { params: { requirementId } });
}

/** 评审详情 */
export function getReviewById(id: number) {
    return axios.get(`/api/qa/ai/review/${id}`);
}

/** 检查是否有正在进行的评审 */
export function getActiveReview(requirementId: number) {
    return axios.get('/api/qa/ai/review/active', { params: { requirementId } });
}

/** 删除评审 */
export function deleteReview(id: number) {
    if (!id) return Promise.reject('Invalid id');
    return axios.delete(`/api/qa/ai/review/${id}`);
}

/** 停止评审（已完成专家保留，在途专家取消） */
export function stopReview(reviewId: number) {
    return axios.post('/api/qa/ai/review/stop', null, { params: { reviewId } });
}

/** 续跑评审（仅重跑未完成专家） */
export function resumeReview(reviewId: number, callbacks: {
    onEvent?: (name: string, data: any) => void;
    onDone?: (d: any) => void;
    onError?: (m: string) => void;
}, signal?: AbortSignal) {
    return streamPost(`/api/qa/ai/review/resume?reviewId=${reviewId}`, {}, callbacks, signal);
}

/** AI 补写：按缺失检查项生成可追加到需求文档的补充段落 */
export function aiPatchRequirement(data: { requirementId: number; itemIds: string[] }) {
    return axios.post('/api/qa/ai/review/patch', data);
}

// ===================== AI Skill 配置/统计 =====================

/** Skill 列表 + 调用量/Token 统计 */
export function getSkillConfigList() {
    return axios.get('/api/ai/skill-config/list');
}

/** Token 用量明细 */
export function getSkillUsage() {
    return axios.get('/api/ai/skill-config/usage');
}

/** 可用 Skill 列表（内置 + 自定义） */
export function getSkillList() {
    return axios.get('/api/ai/skill/list');
}

// ===================== 自定义 Skill 定义 CRUD =====================

/** 自定义 Skill 列表 */
export function listSkillDefinitions() {
    return axios.get('/api/ai/skill-def/list');
}

/** 创建自定义 Skill */
export function createSkillDefinition(data: any) {
    return axios.post('/api/ai/skill-def/', data);
}

/** 更新自定义 Skill */
export function updateSkillDefinition(id: number, data: any) {
    return axios.put(`/api/ai/skill-def/${id}`, data);
}

/** 删除自定义 Skill */
export function deleteSkillDefinition(id: number) {
    return axios.delete(`/api/ai/skill-def/${id}`);
}

/** 启用/禁用自定义 Skill */
export function toggleSkillDefinition(id: number) {
    return axios.post(`/api/ai/skill-def/${id}/toggle`);
}
