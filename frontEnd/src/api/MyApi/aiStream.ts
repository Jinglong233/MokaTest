import {getToken} from '@/utils/auth';
import {useTeamStore} from '@/store/modules/team';
import {useProjectStore} from '@/store/modules/project';

/**
 * 通用 SSE 流式请求工具（fetch + ReadableStream，非 EventSource）
 * 所有 AI Skill / Agent 流式接口复用此函数。
 *
 * SSE 事件格式：
 *   event:delta        data:token文本     → 流式增量
 *   event:tool         data:工具名称      → Agent 工具调用开始
 *   event:tool_result  data:{tool,result} → Agent 工具执行结果
 *   event:done         data:{sessionId}   → 完成
 *   event:error        data:错误信息      → 错误
 */

export interface AiStreamCallbacks {
    /** 流式增量文本 */
    onDelta?: (chunk: string) => void;
    /** Agent 工具调用开始 */
    onTool?: (toolName: string) => void;
    /** Agent 工具执行结果 */
    onToolResult?: (data: { tool: string; result: string }) => void;
    /** 完成 */
    onDone?: (data: any) => void;
    /** RAG 引用溯源（检索命中的知识库文档标题列表） */
    onCitations?: (titles: string[]) => void;
    /** 复用缓存（需求未变更） */
    onReused?: (data: any) => void;
    /** 重试清屏 */
    onRestart?: () => void;
    /** 用户停止（后端确认） */
    onStopped?: () => void;
    /** 记录创建（流式早期下发 recordNo，供停止/重挂使用） */
    onRecord?: (recordNo: string) => void;
    /** 失败 */
    onError?: (message: string) => void;
    /** 全量恢复：断线重连后后端一次性推送已累积的全量文本 */
    onRestore?: (content: string) => void;
    /** 兜底自定义事件（如评审并行化的 expert_start/expert_retry/expert_done/aggregating） */
    onEvent?: (eventName: string, data: any) => void;
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

/**
 * SSE POST 流式请求
 * @param url    完整 API 地址（如 /api/ai/skill/generate-case）
 * @param body   请求体
 * @param callbacks 回调
 * @param signal AbortSignal（可选，用于取消）
 * @param extraTerminalEvents 额外的终态事件名（如 result）：收到后不再触发"未收到完成事件"兜底报错
 */
export async function streamPost(
    url: string,
    body: any,
    callbacks: AiStreamCallbacks,
    signal?: AbortSignal,
    extraTerminalEvents?: string[],
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
        await readEventStream(resp, callbacks, extraTerminalEvents);
    } catch (e: any) {
        if (e?.name !== 'AbortError') {
            callbacks.onError?.(e?.message || '网络异常');
        }
    }
}

/** SSE GET 流式请求（断线重连/回放用），事件协议与 streamPost 一致 */
export async function streamGet(
    url: string,
    callbacks: AiStreamCallbacks,
    signal?: AbortSignal,
    extraTerminalEvents?: string[],
) {
    try {
        const resp = await fetch(url, {headers: buildHeaders(), signal});
        if (!resp.ok || !resp.body) {
            callbacks.onError?.(`请求失败（HTTP ${resp.status}）`);
            return;
        }
        await readEventStream(resp, callbacks, extraTerminalEvents);
    } catch (e: any) {
        if (e?.name !== 'AbortError') {
            callbacks.onError?.(e?.message || '网络异常');
        }
    }
}

/** 读取 SSE 响应流并逐帧分发到回调 */
async function readEventStream(resp: Response, callbacks: AiStreamCallbacks, extraTerminalEvents?: string[]) {
        const reader = resp.body!.getReader();
        const decoder = new TextDecoder('utf-8');
        let buffer = '';
        let currentEvent = '';
        // 是否收到过终止事件（done/error/reused）。服务端超时等场景会静默断流，
        // 没有终态事件时必须在结束时兜底报错，否则调用方会永远停在"进行中"
        let terminalReceived = false;
        for (;;) {
            const {done, value} = await reader.read();
            if (done) break;
            buffer += decoder.decode(value, {stream: true});
            // SSE 帧以空行分隔
            let idx;
            while ((idx = buffer.indexOf('\n\n')) >= 0) {
                const frame = buffer.slice(0, idx);
                buffer = buffer.slice(idx + 2);
                // 解析帧：收集 event 和所有 data 行，按 SSE 规范拼接多行 data
                let eventName = currentEvent;
                const dataLines: string[] = [];
                for (const line of frame.split('\n')) {
                    if (line.startsWith('event:')) {
                        eventName = line.slice(6).trim();
                        currentEvent = eventName;
                    } else if (line.startsWith('data:')) {
                        // SSE 规范：多个 data: 行用 \n 拼接
                        dataLines.push(line.slice(5));
                    } else if (line.startsWith(':')) {
                        // SSE 注释行（心跳），忽略
                    } else if (line.length > 0 && dataLines.length > 0) {
                        // 裸行回填：Spring SseEmitter 未按 SSE 规范将 token 中的 \n 分割为多行 data:，
                        // 导致换行后的内容成为不带 data: 前缀的裸行，追加回上一个 data 行以恢复换行符
                        dataLines[dataLines.length - 1] += '\n' + line;
                    }
                }
                if (dataLines.length === 0) continue;
                // 多行 data 用 \n 拼接（SSE 规范）
                const data = dataLines.join('\n');
                if (eventName === 'delta') {
                    // delta 事件 JSON 解码，还原 token 中的换行符（后端 JSON 编码防止 \n 破坏 SSE 帧）
                    let token = data;
                    try { token = JSON.parse(data); } catch { /* 非 JSON 原样透传（后端未编码的兼容） */ }
                    callbacks.onDelta?.(token);
                } else if (eventName === 'tool') {
                    callbacks.onTool?.(data);
                } else if (eventName === 'tool_result') {
                    try {
                        callbacks.onToolResult?.(JSON.parse(data));
                    } catch {
                        callbacks.onToolResult?.({tool: 'unknown', result: data});
                    }
                } else if (eventName === 'citations') {
                    try {
                        callbacks.onCitations?.(JSON.parse(data));
                    } catch { /* 引用列表解析失败忽略 */ }
                } else if (eventName === 'done') {
                    terminalReceived = true;
                    try {
                        callbacks.onDone?.(JSON.parse(data));
                    } catch {
                        callbacks.onDone?.(data);
                    }
                } else if (eventName === 'reused') {
                    terminalReceived = true;
                    try {
                        callbacks.onReused?.(JSON.parse(data));
                    } catch {
                        callbacks.onReused?.(data);
                    }
                } else if (eventName === 'restart') {
                    callbacks.onRestart?.();
                } else if (eventName === 'record') {
                    try {
                        callbacks.onRecord?.(JSON.parse(data).recordNo);
                    } catch { callbacks.onRecord?.(data); }
                } else if (eventName === 'stopped') {
                    terminalReceived = true;
                    callbacks.onStopped?.();
                } else if (eventName === 'restore') {
                    // 全量恢复：后端一次性推送已累积的 fullText，前端直接替换 content
                    let content = data;
                    try { content = JSON.parse(data); } catch { /* 非 JSON 原样透传 */ }
                    callbacks.onRestore?.(content);
                } else if (eventName === 'error') {
                    terminalReceived = true;
                    callbacks.onError?.(data || '生成失败');
                } else {
                    if (extraTerminalEvents?.includes(eventName)) terminalReceived = true;
                    // 未识别的自定义事件统一走 onEvent 兜底分发
                    if (callbacks.onEvent && eventName !== 'ping') {
                        let parsed: any = data;
                        try { parsed = JSON.parse(data); } catch { /* 非 JSON 原样透传 */ }
                        callbacks.onEvent(eventName, parsed);
                    }
                }
            }
        }
        // 流正常结束但没收到任何终态事件（典型：后端 emitter 超时静默断流）→ 兜底报错
        if (!terminalReceived) {
            callbacks.onError?.('连接已结束但未收到完成事件，可能已超时，请重试');
        }
}
