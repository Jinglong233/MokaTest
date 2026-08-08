import axios from "axios";
import {getToken} from '@/utils/auth';
import {useTeamStore} from '@/store/modules/team';
import {useProjectStore} from '@/store/modules/project';

/**
 * AI 生成测试用例（场景一）
 *
 * 流式接口用 fetch 实现（axios 不支持 SSE 逐块读取）；
 * 非流式接口走 axios，自动带 Authorization / X-Team-Id / X-Project-Id。
 */

export interface AiCaseDraft {
    caseName: string;
    preCondition?: string;
    testSteps?: { step: string; expected: string }[];
    caseType?: string;
    priority?: string;
    tags?: string;
    expectDuration?: number;
    /** 枚举兜底标记，前端标黄 */
    enumFallback?: boolean;
}

export interface AiStreamCallbacks {
    /** 流式增量文本 */
    onDelta?: (chunk: string) => void;
    /** 完成：recordNo + 解析后的草稿 */
    onResult?: (recordNo: string, drafts: AiCaseDraft[]) => void;
    /** 失败 */
    onError?: (message: string) => void;
}

function buildHeaders(): Record<string, string> {
    const headers: Record<string, string> = {'Content-Type': 'application/json'};
    const token = getToken();
    if (token) headers.Authorization = token as string;
    const teamId = useTeamStore().teamId;
    if (teamId) headers['X-Team-Id'] = String(teamId);
    const projectId = useProjectStore().projectId;
    if (projectId) headers['X-Project-Id'] = String(projectId);
    return headers;
}

const BASE = (import.meta.env.VITE_API_BASE_URL || '/') + 'api/qa/ai/case';

/**
 * SSE POST 流式请求：解析 event:/data: 协议帧
 */
async function streamPost(url: string, body: any, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    try {
        const resp = await fetch(url, {
            method: 'POST',
            headers: buildHeaders(),
            body: JSON.stringify(body),
            signal,
        });
        if (!resp.ok || !resp.body) {
            callbacks.onError?.(`请求失败（HTTP ${resp.status}）`);
            return;
        }
        const reader = resp.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let currentEvent = '';
        for (; ;) {
            const {done, value} = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, {stream: true});
            // SSE 帧以空行分隔
            let idx;
            while ((idx = buffer.indexOf('\n\n')) >= 0) {
                const frame = buffer.slice(0, idx);
                buffer = buffer.slice(idx + 2);
                for (const line of frame.split('\n')) {
                    if (line.startsWith('event:')) {
                        currentEvent = line.slice(6).trim();
                    } else if (line.startsWith('data:')) {
                        const data = line.slice(5);
                        if (currentEvent === 'delta') {
                            callbacks.onDelta?.(data);
                        } else if (currentEvent === 'result') {
                            try {
                                const parsed = JSON.parse(data);
                                callbacks.onResult?.(parsed.recordNo, parsed.drafts || []);
                            } catch {
                                callbacks.onError?.('结果解析失败');
                            }
                        } else if (currentEvent === 'error') {
                            callbacks.onError?.(data || '生成失败');
                        }
                    }
                }
            }
        }
    } catch (e: any) {
        if (e?.name !== 'AbortError') {
            callbacks.onError?.(e?.message || '网络异常');
        }
    }
}

/** 新会话：流式生成测试用例 */
export function generateCaseStream(body: {
    requirementId: number;
    instruction?: string;
}, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    return streamPost(`${BASE}/generate`, body, callbacks, signal);
}

/** 追加生成（同会话继续） */
export function appendCaseStream(body: {
    recordNo: string;
    instruction?: string;
}, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    return streamPost(`${BASE}/append`, body, callbacks, signal);
}

/** 采纳入库 */
export function adoptAiCases(body: { recordNo: string; items: AiCaseDraft[] }) {
    return axios.post(`${BASE}/adopt`, body);
}

/** 生成记录回溯 */
export function listAiCaseRecords(requirementId: number) {
    return axios.get(`${BASE}/records`, {params: {requirementId}});
}

/** 停止生成（保留部分内容） */
export function stopAiCaseStream(recordNo: string) {
    return axios.post(`${BASE}/stop`, null, {params: {recordNo}});
}

/** 重新生成（删最后一轮，同指令重跑，SSE） */
export function regenerateAiCaseStream(recordNo: string, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    return streamPost(`${BASE}/regenerate?recordNo=${encodeURIComponent(recordNo)}`, {}, callbacks, signal);
}

/** 重连生成流（SSE，GET）：关弹窗重开时回放并接续 */
export function attachAiCaseStream(recordNo: string, callbacks: AiStreamCallbacks & { onExpired?: () => void }, signal?: AbortSignal) {
    return streamGet(`${BASE}/stream?recordNo=${encodeURIComponent(recordNo)}`, callbacks, signal);
}

/** 删除会话记录 */
export function deleteAiCaseRecord(recordNo: string) {
    return axios.post(`${BASE}/deleteRecord`, null, {params: {recordNo}});
}

/** SSE GET 流式请求（重连用） */
async function streamGet(url: string, callbacks: AiStreamCallbacks & { onExpired?: () => void }, signal?: AbortSignal) {
    try {
        const resp = await fetch(url, {headers: buildHeaders(), signal});
        if (!resp.ok || !resp.body) {
            callbacks.onError?.(`请求失败（HTTP ${resp.status}）`);
            return;
        }
        const reader = resp.body.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let currentEvent = '';
        for (; ;) {
            const {done, value} = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, {stream: true});
            let idx;
            while ((idx = buffer.indexOf('\n\n')) >= 0) {
                const frame = buffer.slice(0, idx);
                buffer = buffer.slice(idx + 2);
                for (const line of frame.split('\n')) {
                    if (line.startsWith('event:')) {
                        currentEvent = line.slice(6).trim();
                    } else if (line.startsWith('data:')) {
                        const data = line.slice(5);
                        if (currentEvent === 'delta') callbacks.onDelta?.(data);
                        else if (currentEvent === 'result') {
                            try {
                                const parsed = JSON.parse(data);
                                callbacks.onResult?.(parsed.recordNo, parsed.drafts || []);
                            } catch { callbacks.onError?.('结果解析失败'); }
                        } else if (currentEvent === 'error') callbacks.onError?.(data || '生成失败');
                        else if (currentEvent === 'stopped') callbacks.onError?.('__STOPPED__');
                        else if (currentEvent === 'expired') callbacks.onExpired?.();
                    }
                }
            }
        }
    } catch (e: any) {
        if (e?.name !== 'AbortError') callbacks.onError?.(e?.message || '网络异常');
    }
}
