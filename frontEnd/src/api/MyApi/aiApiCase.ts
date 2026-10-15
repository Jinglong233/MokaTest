import axios from "axios";
import {streamPost as streamPostGeneric, streamGet as streamGetGeneric, AiStreamCallbacks as GenericCallbacks} from './aiStream';

/**
 * AI 生成 API 接口用例（场景四）
 *
 * SSE 统一走 aiStream 公共封装：delta 直接透传，result/expired 经 onEvent 兜底分发。
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

const BASE = (import.meta.env.VITE_API_BASE_URL || '/') + 'api/ai/apiCase';

/** 后端发完 result 即 complete（无 done 事件），需告知公共封装 result 就是终态 */
const TERMINAL_EVENTS = ['result'];

/** 私有回调形态 → 公共 SSE 回调：result/expired 走 onEvent 兜底，stopped 沿用 __STOPPED__ 约定 */
function adaptCallbacks(callbacks: AiStreamCallbacks & { onExpired?: () => void }): GenericCallbacks {
    return {
        onDelta: (chunk) => callbacks.onDelta?.(chunk),
        onError: (msg) => callbacks.onError?.(msg),
        onStopped: () => callbacks.onError?.('__STOPPED__'),
        onEvent: (event, data) => {
            if (event === 'result') {
                if (data && typeof data === 'object' && data.recordNo) {
                    callbacks.onResult?.(data.recordNo, data.drafts || []);
                } else {
                    callbacks.onError?.('结果解析失败');
                }
            } else if (event === 'expired') {
                callbacks.onExpired?.();
            }
        },
    };
}

export function generateApiCaseStream(body: {
    apiId: number;
    instruction?: string;
}, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    return streamPostGeneric(`${BASE}/generate`, body, adaptCallbacks(callbacks), signal, TERMINAL_EVENTS);
}

export function appendApiCaseStream(body: {
    recordNo: string;
    instruction?: string;
}, callbacks: AiStreamCallbacks, signal?: AbortSignal) {
    return streamPostGeneric(`${BASE}/append`, body, adaptCallbacks(callbacks), signal, TERMINAL_EVENTS);
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
    return streamPostGeneric(`${BASE}/regenerate?recordNo=${encodeURIComponent(recordNo)}`, {}, adaptCallbacks(callbacks), signal, TERMINAL_EVENTS);
}

/** 重连生成流（SSE，GET）：关弹窗重开时回放并接续 */
export function attachAiApiCaseStream(recordNo: string, callbacks: AiStreamCallbacks & { onExpired?: () => void }, signal?: AbortSignal) {
    return streamGetGeneric(`${BASE}/stream?recordNo=${encodeURIComponent(recordNo)}`, adaptCallbacks(callbacks), signal, TERMINAL_EVENTS);
}

/** 删除会话记录 */
export function deleteAiApiCaseRecord(recordNo: string) {
    return axios.post(`${BASE}/deleteRecord`, null, {params: {recordNo}});
}
