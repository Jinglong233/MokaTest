import axios from "axios";
import {getToken} from '@/utils/auth';
import {useTeamStore} from '@/store/modules/team';
import {useProjectStore} from '@/store/modules/project';

/**
 * AI 生成 API 接口用例（场景四）
 */

export interface AiApiCaseDraft {
    caseName: string;
    description?: string;
    requestHeader?: { name: string; value: string; type?: string; disabled?: boolean }[];
    query?: { name: string; value: string; type?: string; disabled?: boolean }[];
    bodyJson?: string;
    assertions?: { apiAssertType: string; field: string; assertRelationship: string; assertValue: string }[];
    extractions?: { type: string; expression: string; variableName: string; defaultValue?: string }[];
}

export interface AiStreamCallbacks {
    onDelta?: (chunk: string) => void;
    onResult?: (recordNo: string, drafts: AiApiCaseDraft[]) => void;
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

const BASE = (import.meta.env.VITE_API_BASE_URL || '/') + 'api/ai/apiCase';

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

export function generateApiCaseStream(body: {
    apiId: number;
    instruction?: string;
}, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    return streamPost(`${BASE}/generate`, body, callbacks, signal);
}

export function appendApiCaseStream(body: {
    recordNo: string;
    instruction?: string;
}, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    return streamPost(`${BASE}/append`, body, callbacks, signal);
}

export function adoptAiApiCases(body: { recordNo: string; items: AiApiCaseDraft[] }) {
    return axios.post(`${BASE}/adopt`, body);
}

export function listAiApiCaseRecords(apiId: number) {
    return axios.get(`${BASE}/records`, {params: {apiId}});
}

/** 停止生成（保留部分内容） */
export function stopAiApiCaseStream(recordNo: string) {
    return axios.post(`${BASE}/stop`, null, {params: {recordNo}});
}

/** 重新生成（删最后一轮，同指令重跑，SSE） */
export function regenerateAiApiCaseStream(recordNo: string, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    return streamPost(`${BASE}/regenerate?recordNo=${encodeURIComponent(recordNo)}`, {}, callbacks, signal);
}

/** 重连生成流（SSE，GET）：关弹窗重开时回放并接续 */
export function attachAiApiCaseStream(recordNo: string, callbacks: AiStreamCallbacks & { onExpired?: () => void }, signal?: AbortSignal) {
    return streamGet(`${BASE}/stream?recordNo=${encodeURIComponent(recordNo)}`, callbacks, signal);
}

/** 删除会话记录 */
export function deleteAiApiCaseRecord(recordNo: string) {
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
