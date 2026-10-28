import axios from "axios";
import {streamPost, AiStreamCallbacks} from './aiStream';
import type {RecordStepDraft} from './record';

/**
 * AI 派生场景（批次二）
 *
 * 后端契约（已最终确认）：
 *   生成：POST /api/automation/ai/scene/derive（SSE）
 *     body: {sceneId, instruction?, folderIds[], coverageDirs[], bizRules?}
 *     事件：event:delta（文本增量）/ event:result（最终草稿 JSON {name,description,steps}）
 *           event:warning（幻觉 id 剔除等告警）/ event:error
 *   落库：POST /api/automation/ai/scene/derive/save
 *     body: {deriveFrom, name, description?, steps:[{stepType, stepName, stepDetail}]}
 *     后端自动处理项目根下「AI派生场景」分组目录；scene 表 derive_from 列标记派生来源（NULL=非派生）
 */

// ==================== 与后端对齐的常量（集中定义） ====================

/** 派生场景保存接口路径 */
export const DERIVED_SCENE_SAVE_URL = '/api/automation/ai/scene/derive/save';

/** 判断场景是否为 AI 派生场景（scene.deriveFrom = 源场景 id，NULL=非派生） */
export function isAiDerivedScene(scene: any): boolean {
    return scene?.deriveFrom != null;
}

/** 发给 AI 的元素总数上限（与后端硬上限一致，超限前端禁止提交） */
export const DERIVE_ELEMENT_LIMIT = 200;

/** 业务规则/提示文案长度上限（与后端一致） */
export const DERIVE_BIZ_RULES_MAX_LENGTH = 2000;

/** 覆盖方向取值（coverageDirs[] 元素） */
export const DERIVE_COVERAGE = {
    NEGATIVE: 'NEGATIVE',       // 反用例
    BOUNDARY: 'BOUNDARY',       // 边界值
    EXCEPTION: 'EXCEPTION',     // 异常路径
    PERMISSION: 'PERMISSION',   // 权限校验
} as const;

// ==================== 类型 ====================

export interface AiSceneDeriveRequest {
    sceneId: number | string;
    instruction?: string;
    folderIds: number[];
    coverageDirs: string[];
    bizRules?: string;
}

/** AI 返回的派生场景草稿（event:result 载荷） */
export interface AiDerivedSceneDraft {
    name: string;
    description?: string;
    steps: RecordStepDraft[];
}

export interface AiSceneDeriveCallbacks {
    /** 生成过程文本增量 */
    onDelta?: (chunk: string) => void;
    /** 最终草稿 */
    onResult?: (draft: AiDerivedSceneDraft) => void;
    /** 告警（幻觉元素 id 剔除等） */
    onWarning?: (message: string) => void;
    /** 失败 */
    onError?: (message: string) => void;
}

const BASE = (import.meta.env.VITE_API_BASE_URL || '/') + 'api/automation/ai/scene';

/** 后端发完 result 即结束（无 done 事件），需告知公共封装 result 就是终态 */
const TERMINAL_EVENTS = ['result'];

/** 私有回调形态 → 公共 SSE 回调：result/warning 走 onEvent 兜底分发 */
function adaptCallbacks(callbacks: AiSceneDeriveCallbacks): AiStreamCallbacks {
    return {
        onDelta: (chunk) => callbacks.onDelta?.(chunk),
        onError: (msg) => callbacks.onError?.(msg),
        onEvent: (event, data) => {
            if (event === 'result') {
                if (data && typeof data === 'object' && Array.isArray(data.steps)) {
                    callbacks.onResult?.(data as AiDerivedSceneDraft);
                } else {
                    callbacks.onError?.('结果解析失败');
                }
            } else if (event === 'warning') {
                const msg = typeof data === 'string' ? data : (data?.message || JSON.stringify(data));
                callbacks.onWarning?.(msg);
            }
        },
    };
}

/** 发起 AI 派生场景生成（SSE 流式） */
export function deriveSceneStream(body: AiSceneDeriveRequest, callbacks: AiSceneDeriveCallbacks, signal?: AbortSignal) {
    return streamPost(`${BASE}/derive`, body, adaptCallbacks(callbacks), signal, TERMINAL_EVENTS);
}

/** 落库结果（SaResult.data 载荷） */
export interface DeriveSaveResult {
    sceneId: number;
    folderId: number;
    savedSteps: number;
    skippedSteps: number;
    warnings: string[];
}

/**
 * 保存确认后的派生场景。
 * 后端自动处理项目根下「AI派生场景」分组目录；stepDetail 中元素用 elementId 引用库元素，
 * 由后端解析为完整 ElementDTO 快照。axios 拦截器已统一处理 code !== 200 的报错，
 * 此处 resolve 即 code 成功，再按 data.sceneId 兜底判断。
 */
export function saveDerivedScene(body: {
    deriveFrom: number;
    name: string;
    description?: string;
    steps: { stepType: string; stepName: string; stepDetail: any }[];
}) {
    return axios.post<DeriveSaveResult>(DERIVED_SCENE_SAVE_URL, body);
}
