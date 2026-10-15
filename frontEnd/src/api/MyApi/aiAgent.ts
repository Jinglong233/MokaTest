import axios from 'axios';
import { streamPost } from './aiStream';

const BASE = (import.meta.env.VITE_API_BASE_URL || '/') + 'api/qa/ai/agent';

/** 创建会话 */
export function createAgentSession(title?: string) {
    return axios.post(`${BASE}/session`, { title });
}

/** 会话列表 */
export function listAgentSessions() {
    return axios.get(`${BASE}/sessions`);
}

/** 删除会话 */
export function deleteAgentSession(id: number) {
    return axios.delete(`${BASE}/session/${id}`);
}

/** 会话历史消息 */
export function getAgentMessages(sessionId: number) {
    return axios.get(`${BASE}/session/${sessionId}/messages`);
}

/** 停止生成（通知后端协作式取消；前端 abort 只断连接，后端 LLM 仍在跑） */
export function stopAgentChat(sessionId: number) {
    return axios.post(`${BASE}/chat/stop`, { sessionId });
}

/** 持久化卡片消息（带 messageId 时原地更新原卡片，用于卡片状态回写，如草稿入库后置为已保存） */
export function saveCardMessage(sessionId: number, cardType: string, data: any, messageId?: number) {
    return axios.post(`${BASE}/session/${sessionId}/card`, { cardType, data, messageId });
}

/** Agent 对话（SSE） */
export function streamAgentChat(
    body: { sessionId: number; message: string; partialContent?: string; skillScene?: string; retry?: boolean },
    callbacks: {
        onDelta?: (t: string) => void;
        onToolStart?: (data: any) => void;
        onToolEnd?: (data: any) => void;
        onCitations?: (titles: string[]) => void;
        onDone?: (data: any) => void;
        onStopped?: () => void;
        onError?: (m: string) => void;
        onSkillInvalid?: (data: { skill: string; msg: string }) => void;
    },
    signal?: AbortSignal,
) {
    // 扩展 streamPost 的回调，处理 tool_start/tool_end 事件
    return streamPost(`${BASE}/chat`, body, {
        onDelta: callbacks.onDelta,
        onTool: (data: string) => {
            try {
                const parsed = JSON.parse(data);
                callbacks.onToolStart?.(parsed);
            } catch {
                callbacks.onToolStart?.({ tool: data });
            }
        },
        onToolResult: (data: any) => {
            callbacks.onToolEnd?.(data);
        },
        onCitations: callbacks.onCitations,
        onDone: callbacks.onDone,
        onStopped: callbacks.onStopped,
        onError: callbacks.onError,
        onEvent: (name: string, data: any) => {
            if (name === 'skill_invalid') {
                callbacks.onSkillInvalid?.(data);
            }
        },
    }, signal);
}

/** 查询会话是否有进行中的流式回复（断线恢复检测） */
export function getStreamStatus(sessionId: number) {
    return axios.get(`${BASE}/session/${sessionId}/stream-status`);
}

/** 恢复流式对话（SSE）：后端一次性回放全量 fullText，后续 delta 正常推送 */
export function resumeStreamChat(
    body: { sessionId: number },
    callbacks: {
        onRestore?: (content: string) => void;
        onDelta?: (t: string) => void;
        onDone?: (data: any) => void;
        onError?: (m: string) => void;
    },
    signal?: AbortSignal,
) {
    return streamPost(`${BASE}/chat/resume`, body, {
        onRestore: callbacks.onRestore,
        onDelta: callbacks.onDelta,
        onDone: callbacks.onDone,
        onError: callbacks.onError,
    }, signal);
}
