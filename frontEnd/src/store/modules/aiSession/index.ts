import {defineStore} from 'pinia';

/**
 * AI 工作台会话状态管理
 *
 * mode: 'agent' 普通对话 | 'casegen' 用例生成卡片流
 * casegen 模式下管理 recordNo/stage/stagePayload 生成状态
 */
export const useAiSessionStore = defineStore('aiSession', {
    state: () => ({
        mode: 'agent' as 'agent' | 'casegen',
        recordNo: '' as string,
        stage: '' as string,
        stagePayload: null as any,
        requirementId: null as number | null,
    }),

    actions: {
        startCasegen(reqId: number) {
            this.mode = 'casegen';
            this.requirementId = reqId;
            this.recordNo = '';
            this.stage = '';
            this.stagePayload = null;
        },

        setStage(stage: string, payload?: any) {
            this.stage = stage;
            if (payload !== undefined) this.stagePayload = payload;
        },

        setRecordNo(recordNo: string) {
            this.recordNo = recordNo;
        },

        clearState() {
            this.mode = 'agent';
            this.recordNo = '';
            this.stage = '';
            this.stagePayload = null;
            this.requirementId = null;
        },
    },
});
