import axios from "axios";
import {getToken} from '@/utils/auth';
import {useTeamStore} from '@/store/modules/team';
import {useProjectStore} from '@/store/modules/project';
import { streamPost as streamPostGeneric, AiStreamCallbacks as GenericCallbacks } from './aiStream';

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
                // 按 SSE 规范：一帧内多行 data 用 \n 拼接后统一派发
                let eventName = currentEvent;
                const dataLines: string[] = [];
                for (const line of frame.split('\n')) {
                    if (line.startsWith('event:')) {
                        eventName = line.slice(6).trim();
                        currentEvent = eventName;
                    } else if (line.startsWith('data:')) {
                        dataLines.push(line.slice(5));
                    }
                }
                if (dataLines.length === 0) continue;
                const data = dataLines.join('\n');
                if (eventName === 'delta') {
                    callbacks.onDelta?.(data);
                } else if (eventName === 'result') {
                    try {
                        const parsed = JSON.parse(data);
                        callbacks.onResult?.(parsed.recordNo, parsed.drafts || []);
                    } catch {
                        callbacks.onError?.('结果解析失败');
                    }
                } else if (eventName === 'error') {
                    callbacks.onError?.(data || '生成失败');
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

/** 重连生成流（SSE，GET）：restore 全量回放 + 后续增量 delta + 接续实时流 */
export function attachAiCaseStream(recordNo: string, callbacks: {
    onRestore?: (t: string) => void;
    onDelta?: (t: string) => void;
    onResult?: (d: any) => void;
    onStage?: (stage: string) => void;
    onDimensions?: (dims: any[]) => void;
    onRoundStart?: (d: any) => void;
    onRoundDone?: (d: any) => void;
    onPointStart?: (d: any) => void;
    onPointDone?: (d: any) => void;
    onAudit?: (d: any) => void;
    onError?: (m: string) => void;
    onExpired?: () => void;
}, signal?: AbortSignal) {
    return streamGet(`${BASE}/stream?recordNo=${encodeURIComponent(recordNo)}`, callbacks, signal);
}

/** 删除会话记录 */
export function deleteAiCaseRecord(recordNo: string) {
    return axios.post(`${BASE}/deleteRecord`, null, {params: {recordNo}});
}

// ==================== Phase 4: 四阶段接口 ====================

/** 阶段1：设计维度点生成 SSE */
export function streamPoints(
    body: { requirementId: number; instruction?: string; count?: number },
    callbacks: { onDelta?: (t: string) => void; onDone?: (d: any) => void; onError?: (m: string) => void; onStopped?: () => void; onRecord?: (recordNo: string) => void },
    signal?: AbortSignal,
) {
    return streamPostGeneric(`${BASE}/points/stream`, body, callbacks as GenericCallbacks, signal);
}

/** 保存用户确认的设计维度点 */
export function saveTestPoints(recordNo: string, testPoints: any[]) {
    return axios.put(`${BASE}/record/${recordNo}/testPoints`, JSON.stringify(testPoints), {
        headers: { 'Content-Type': 'application/json' },
    });
}

/** 用例生成 SSE（无设计点环节：recordNo 空时按 requirementId 直接发起，refine 事件已随自打磨下线） */
export function streamCases(
    body: { recordNo?: string; requirementId?: number; instruction?: string; count?: number; targetTpId?: string },
    callbacks: {
        onDelta?: (t: string) => void;
        onThinking?: (t: string) => void;
        onCase?: (draft: any) => void;
        onResult?: (d: any) => void;
        onReview?: (d: any) => void;
        onRefine?: (d: any) => void;
        onRecord?: (recordNo: string) => void;
        onStage?: (stage: string) => void;
        /** 维度清单（首轮解析完成 / 审查补充新维度时推送） */
        onDimensions?: (dims: any[]) => void;
        /** 自动补轮开始 {round, targetDims} */
        onRoundStart?: (d: any) => void;
        /** 单轮完成 {round, newCases, drafts} */
        onRoundDone?: (d: any) => void;
        /** 自动覆盖审查结论 */
        onAudit?: (d: any) => void;
        /** V2 逐点生成开始 {tpId, title} */
        onPointStart?: (d: any) => void;
        /** V2 单点完成 {tpId, caseCount} */
        onPointDone?: (d: any) => void;
        /** Agent 工具调用开始 {tool, args} */
        onTool?: (d: any) => void;
        onError?: (m: string) => void;
        onStopped?: () => void;
    },
    signal?: AbortSignal,
) {
    streamPostOverride(`${BASE}/cases/stream`, body, callbacks, signal);
}

/** 获取记录全量状态（断线恢复） */
export function getRecordState(recordNo: string) {
    return axios.get(`${BASE}/record/${recordNo}`);
}

/** Phase 4 专用流式 POST：处理 result 事件的完整 JSON */
async function streamPostOverride(
    url: string,
    body: any,
    callbacks: { onDelta?: (t: string) => void; onThinking?: (t: string) => void; onCase?: (draft: any) => void; onResult?: (d: any) => void; onReview?: (d: any) => void; onRefine?: (d: any) => void; onRecord?: (recordNo: string) => void; onStage?: (stage: string) => void; onDimensions?: (dims: any[]) => void; onRoundStart?: (d: any) => void; onRoundDone?: (d: any) => void; onAudit?: (d: any) => void; onPointStart?: (d: any) => void; onPointDone?: (d: any) => void; onTool?: (d: any) => void; onError?: (m: string) => void; onStopped?: () => void },
    signal?: AbortSignal,
) {
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
        // 终态兜底：连接正常结束但没收到 result/error/stopped 时必须报错，
        // 否则 emitter 超时静默断流会让页面永远停在"生成中"
        let terminalReceived = false;
        for (;;) {
            const { done, value } = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, { stream: true });
            let idx;
            while ((idx = buffer.indexOf('\n\n')) >= 0) {
                const frame = buffer.slice(0, idx);
                buffer = buffer.slice(idx + 2);
                // 按 SSE 规范：一帧内多行 data 用 \n 拼接后统一派发
                let eventName = currentEvent;
                const dataLines: string[] = [];
                for (const line of frame.split('\n')) {
                    if (line.startsWith('event:')) {
                        eventName = line.slice(6).trim();
                        currentEvent = eventName;
                    } else if (line.startsWith('data:')) {
                        dataLines.push(line.slice(5));
                    }
                }
                if (dataLines.length === 0) continue;
                const data = dataLines.join('\n');
                if (eventName === 'delta') {
                    try { callbacks.onDelta?.(JSON.parse(data)); } catch { callbacks.onDelta?.(data); }
                } else if (eventName === 'case') {
                    try { callbacks.onCase?.(JSON.parse(data)); } catch { callbacks.onCase?.(data); }
                } else if (eventName === 'thinking') {
                    callbacks.onThinking?.(data);
                } else if (eventName === 'result') {
                    terminalReceived = true;
                    try {
                        callbacks.onResult?.(JSON.parse(data));
                    } catch {
                        callbacks.onError?.('结果解析失败');
                    }
                } else if (eventName === 'review') {
                    try {
                        callbacks.onReview?.(JSON.parse(data));
                    } catch {
                        callbacks.onReview?.(data);
                    }
                } else if (eventName === 'record') {
                    try {
                        callbacks.onRecord?.(JSON.parse(data).recordNo);
                    } catch { callbacks.onRecord?.(data); }
                } else if (eventName === 'stage') {
                    try {
                        callbacks.onStage?.(JSON.parse(data).stage);
                    } catch { /* ignore */ }
                } else if (eventName === 'dimensions') {
                    try { callbacks.onDimensions?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'round_start') {
                    try { callbacks.onRoundStart?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'round_done') {
                    try { callbacks.onRoundDone?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'point_start') {
                    try { callbacks.onPointStart?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'point_done') {
                    try { callbacks.onPointDone?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'audit') {
                    try { callbacks.onAudit?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'tool') {
                    try { callbacks.onTool?.(JSON.parse(data)); } catch { callbacks.onTool?.(data); }
                } else if (eventName === 'error') {
                    terminalReceived = true;
                    callbacks.onError?.(data || '生成失败');
                } else if (eventName === 'stopped') {
                    terminalReceived = true;
                    callbacks.onStopped?.();
                }
            }
        }
        if (!terminalReceived) {
            callbacks.onError?.('连接已结束但未收到完成事件，可能已超时，请重试');
        }
    } catch (e: any) {
        if (e?.name !== 'AbortError') {
            callbacks.onError?.(e?.message || '网络异常');
        }
    }
}

/** SSE GET 流式请求（重连用，支持 restore 全量回放） */
async function streamGet(url: string, callbacks: {
    onDelta?: (t: string) => void;
    onRestore?: (t: string) => void;
    /** result 事件完整载荷（含 poolDrafts/dimensions/audit/coverage） */
    onResult?: (d: any) => void;
    onStage?: (stage: string) => void;
    onDimensions?: (dims: any[]) => void;
    onRoundStart?: (d: any) => void;
    onRoundDone?: (d: any) => void;
    onPointStart?: (d: any) => void;
    onPointDone?: (d: any) => void;
    onAudit?: (d: any) => void;
    onError?: (m: string) => void;
    onExpired?: () => void;
}, signal?: AbortSignal) {
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
                let eventName = currentEvent;
                const dataLines: string[] = [];
                for (const line of frame.split('\n')) {
                    if (line.startsWith('event:')) {
                        eventName = line.slice(6).trim();
                        currentEvent = eventName;
                    } else if (line.startsWith('data:')) {
                        dataLines.push(line.slice(5));
                    }
                }
                if (dataLines.length === 0) continue;
                const data = dataLines.join('\n');
                if (eventName === 'restore') {
                    callbacks.onRestore?.(data);
                } else if (eventName === 'delta') {
                    try { callbacks.onDelta?.(JSON.parse(data)); } catch { callbacks.onDelta?.(data); }
                } else if (eventName === 'result') {
                    try {
                        callbacks.onResult?.(JSON.parse(data));
                    } catch { callbacks.onError?.('结果解析失败'); }
                } else if (eventName === 'error') callbacks.onError?.(data || '生成失败');
                else if (eventName === 'stopped') callbacks.onError?.('__STOPPED__');
                else if (eventName === 'expired') callbacks.onExpired?.();
                else if (eventName === 'stage') {
                    try { callbacks.onStage?.(JSON.parse(data).stage); } catch { /* ignore */ }
                } else if (eventName === 'dimensions') {
                    try { callbacks.onDimensions?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'round_start') {
                    try { callbacks.onRoundStart?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'round_done') {
                    try { callbacks.onRoundDone?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'point_start') {
                    try { callbacks.onPointStart?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'point_done') {
                    try { callbacks.onPointDone?.(JSON.parse(data)); } catch { /* ignore */ }
                } else if (eventName === 'audit') {
                    try { callbacks.onAudit?.(JSON.parse(data)); } catch { /* ignore */ }
                }
            }
        }
    } catch (e: any) {
        if (e?.name !== 'AbortError') callbacks.onError?.(e?.message || '网络异常');
    }
}

/** 需求摘要（Card 1 用） */
export function getReqSummary(reqId: number) {
    return axios.get(`${BASE}/req-summary/${reqId}`);
}

/** 增量保存草稿（前端就地编辑后调用） */
export function saveDrafts(recordNo: string, drafts: any[]) {
    return axios.put(`${BASE}/record/${recordNo}/drafts`, JSON.stringify(drafts), {
        headers: { 'Content-Type': 'application/json' },
    });
}

/** 覆盖度审查（同步返回审查结论） */
export function auditCoverage(recordNo: string) {
    return axios.post(`${BASE}/auditCoverage`, { recordNo });
}

/** 归档会话记录（标记为 ARCHIVED，不影响已入库用例） */
export function archiveRecord(recordNo: string) {
    return axios.post(`${BASE}/archive`, null, { params: { recordNo } });
}
