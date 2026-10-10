<template>
  <div class="case-gen-page" v-if="projectStore.hasProjectSelected">
    <div class="cg-header">
      <a-space>
        <a-button size="small" @click="goBack"><template #icon><IconLeft /></template> {{ $t('qa.ai.common.back') }}</a-button>
        <span class="cg-req-code">{{ reqSummary?.reqCode }}</span>
        <span class="cg-req-title">{{ reqSummary?.title }}</span>
      </a-space>
      <div class="cg-coverage-bar">
        <template v-if="workbench.dimensions.length">
          <span class="cb-ok">{{ $t('qa.ai.caseGen.covered', { covered: coveredCount, total: workbench.dimensions.length }) }}</span>
          <span v-if="issueCount" class="cb-warn">{{ $t('qa.ai.caseGen.issues', { count: issueCount }) }}</span>
          <span v-if="pendingCount" class="cb-pending">{{ $t('qa.ai.caseGen.pendingGen', { count: pendingCount }) }}</span>
        </template>
        <span v-else class="cb-empty">{{ $t('qa.ai.caseGen.autoList') }}</span>
      </div>
      <a-button v-if="generating" status="danger" size="small" @click="stopGeneration">{{ $t('qa.ai.common.stop') }}</a-button>
    </div>

    <div class="cg-body">
      <TestPointSidebar
        :dimensions="workbench.dimensions"
        :active-tp-id="activeTpId"
        @select="selectTestPoint"
      />
      <div class="cg-right">
        <div class="cg-right-top">
          <PointDrafts
            :drafts="filteredDrafts"
            :adopted-drafts="filteredAdopted"
            :streaming-drafts="workbench.streamingDrafts"
            :tp-title="activeDim?.title"
            :adopting="adopting"
            @adopt-single="adoptSingle"
            @delete="deleteDraft"
            @edited="onDraftEdited"
          />
        </div>
        <div class="cg-right-bottom">
          <PointChat
            ref="pointChatRef"
            :messages="pointMessages"
            :generating="generating"
            :placeholder="activeDim ? $t('qa.ai.caseGen.chatPlaceholder', { title: activeDim.title }) : $t('qa.ai.caseGen.selectPointFirst')"
            @send="onPointSend"
            @stop="stopGeneration"
          />
        </div>
      </div>
    </div>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
export default { name: 'AiCaseGen' };
</script>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { IconLeft } from '@arco-design/web-vue/es/icon';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import { useProjectStore } from '@/store';
import TestPointSidebar from './caseGen/TestPointSidebar.vue';
import PointDrafts from './caseGen/PointDrafts.vue';
import PointChat from './caseGen/PointChat.vue';
import {
  streamCases, getRecordState, getReqSummary,
  adoptAiCases, stopAiCaseStream, saveDrafts, archiveRecord,
  listAiCaseRecords, attachAiCaseStream,
} from '@/api/MyApi/aiCase';

const route = useRoute();
const router = useRouter();
const projectStore = useProjectStore();
const { t } = useI18n();

const reqId = computed(() => { const n = Number(route.query.reqId); return Number.isNaN(n) ? 0 : n; });

const recordNo = ref('');
const reqSummary = ref<any>(null);
const generating = ref(false);
const adopting = ref(false);
let abortCtrl: AbortController | null = null;

const workbench = reactive({
  dimensions: [] as any[],
  drafts: [] as any[],
  adoptedDrafts: [] as any[],
  streamingDrafts: [] as any[],
  audit: null as any,
  coverage: null as any,
  converged: false,
  citations: [] as any[],
});
const archivedAudit = ref<any>(null);
const isConverged = computed(() => archivedAudit.value && !archivedAudit.value.stale && archivedAudit.value.overallVerdict === 'SUFFICIENT');

const activeTpId = ref<string>('');
const activeDim = computed(() => workbench.dimensions.find((d: any) => d.id === activeTpId.value));
// BUG#6 修复：按测试点存储对话消息，切换不丢历史
const pointMessagesMap = ref<Record<string, any[]>>({});
const pointMessages = computed(() => pointMessagesMap.value[activeTpId.value] || []);
let msgUid = 0;
function pushMsg(msg: any) { msg._uid = ++msgUid; const arr = pointMessagesMap.value[activeTpId.value] || (pointMessagesMap.value[activeTpId.value] = []); arr.push(msg); }

const pointChatRef = ref<InstanceType<typeof PointChat>>();

// 添加测试点

// 覆盖统计
const coveredCount = computed(() => workbench.dimensions.filter((d: any) => d.status === 'COVERED').length);
const issueCount = computed(() => workbench.dimensions.filter((d: any) => d.status === 'ISSUE' || d.status === 'FAILED').length);
const pendingCount = computed(() => workbench.dimensions.filter((d: any) => d.status === 'PENDING' || d.status === 'GENERATING').length);

// 按选中测试点过滤草稿
const filteredDrafts = computed(() => {
  if (!activeTpId.value) return workbench.drafts;
  return workbench.drafts.filter((d: any) => (d.coverPoints || []).includes(activeTpId.value));
});
const filteredAdopted = computed(() => {
  if (!activeTpId.value) return workbench.adoptedDrafts;
  return workbench.adoptedDrafts.filter((d: any) => (d.coverPoints || []).includes(activeTpId.value));
});

function goBack() { router.push({ name: 'Requirement' }); }

function selectTestPoint(dim: any) {
  // BUG#4 修复：生成中切换测试点先停止生成
  if (generating.value) stopGeneration();
  activeTpId.value = dim.id;
  if (!pointMessagesMap.value[dim.id]) pointMessagesMap.value[dim.id] = [];
}

// ==================== 发送（逐点补充） ====================
function onPointSend(text: string) {
  if (!text.trim() || generating.value) return;
  if (!recordNo.value) { Message.warning(t('qa.ai.caseGen.generatePointsFirst')); return; }
  if (!activeTpId.value) { Message.warning(t('qa.ai.caseGen.selectPointFirst')); return; }
  pushMsg({ role: 'user', content: text });
  startStream({ recordNo: recordNo.value, targetTpId: activeTpId.value, instruction: text });
}

function startStream(body: any) {
  generating.value = true;
  abortCtrl = new AbortController();
  const assistantMsg = ref<any>({
    role: 'assistant', content: t('qa.ai.caseGen.generating'), _done: false, _placeholder: true,
    _isQa: false,
  });
  pushMsg(assistantMsg.value);
  workbench.streamingDrafts = [];

  streamCases(body, {
    onRecord: (no: string) => { recordNo.value = no || ''; },
    onStage: (stage: string) => {
      if (stage === 'gen') assistantMsg.value.content = t('qa.ai.caseGen.generatingCases');
      if (stage === 'context') assistantMsg.value.content = t('qa.ai.caseGen.analyzingDims');
      if (stage === 'audit') assistantMsg.value.content = t('qa.ai.caseGen.auditing');
      pointChatRef.value?.scrollToBottom();
    },
    onDimensions: (dims: any[]) => {
      workbench.dimensions = dims || [];
      if (!activeTpId.value && dims.length) activeTpId.value = dims[0].id;
    },
    onPointStart: (d: any) => {
      const dim = workbench.dimensions.find((x: any) => x.id === d.tpId);
      if (dim) dim.status = 'GENERATING';
      if (d.tpId === activeTpId.value) assistantMsg.value.content = t('qa.ai.caseGen.generatingDim', { title: d.title });
      pointChatRef.value?.scrollToBottom();
    },
    onPointDone: (d: any) => {
      const dim = workbench.dimensions.find((x: any) => x.id === d.tpId);
      if (dim) dim.status = d.failed ? 'FAILED' : 'COVERED';
    },
    onCase: (draft: any) => { workbench.streamingDrafts.push(draft); },
    onDelta: (text: string) => {
      // Agent 流式文字：替换占位符或追加
      if (assistantMsg.value._placeholder) {
        assistantMsg.value.content = text;
        assistantMsg.value._placeholder = false;
      } else {
        assistantMsg.value.content += text;
      }
      pointChatRef.value?.scrollToBottom();
    },
    onTool: (d: any) => {
      // Agent 工具调用开始：插入状态行（不干扰后续 delta 文字）
      assistantMsg.value._placeholder = false;
      assistantMsg.value.content += `\n\n> ${t('qa.ai.tool.genericPending', { name: d.tool })}\n\n`;
      pointChatRef.value?.scrollToBottom();
    },
    onAudit: (audit: any) => { workbench.audit = { ...audit, time: new Date().toLocaleTimeString() }; },
    onResult: (data: any) => {
      generating.value = false;
      assistantMsg.value._done = true;
      if (data.type === 'qa') {
        assistantMsg.value._isQa = true;
        pointChatRef.value?.forceScrollToBottom();
        return;
      }
      if (data.poolDrafts && data.poolDrafts.length > 0) {
        workbench.drafts = data.poolDrafts.map((x: any) => ({ ...x, _selected: true, _adoptStatus: null, _round: x._round || 1 }));
      } else if (workbench.streamingDrafts.length > 0) {
        // Fallback: poolDrafts 为空时，用 streamingDrafts 补充
        workbench.drafts = workbench.streamingDrafts.map((x: any) => ({ ...x, _selected: true, _adoptStatus: null, _round: x._round || 1 }));
      }
      if (data.dimensions) workbench.dimensions = data.dimensions;
      if (data.audit) workbench.audit = { ...data.audit, time: new Date().toLocaleTimeString() };
      workbench.streamingDrafts = [];
      workbench.citations = data.citations || [];
      workbench.converged = false;
      // 只有当 delta 未写入文字时才覆盖（V2 批量路径），逐点路径的 delta 文字保留
      if (assistantMsg.value._placeholder) {
        if (data.type === 'point') {
          // 逐点生成：显示当前测试点的草稿数
          const count = data.pointCaseCount || 0;
          assistantMsg.value.content = t('qa.ai.caseGen.pointDraftsDone', { count });
        } else {
          // V2 批量路径：全局维度统计
          const dimTotal = workbench.dimensions.length;
          const dimCovered = coveredCount.value;
          const issueC = issueCount.value;
          assistantMsg.value.content = dimTotal
            ? (issueC
              ? t('qa.ai.caseGen.dimSummaryWithIssue', { total: dimTotal, covered: dimCovered, issues: issueC })
              : t('qa.ai.caseGen.dimSummary', { total: dimTotal, covered: dimCovered }))
            : t('qa.ai.caseGen.draftsGenerated', { count: workbench.drafts.length });
        }
      }
      pointChatRef.value?.forceScrollToBottom();
    },
    onError: (msg: string) => {
      // 断线重连：检测“连接已结束”自动重连
      if (recordNo.value && msg.includes('连接已结束')) {
        reattachStream(assistantMsg);
        return;
      }
      generating.value = false;
      assistantMsg.value._done = true;
      assistantMsg.value.content = t('qa.ai.caseGen.genFailed', { msg });
      pointChatRef.value?.forceScrollToBottom();
    },
    onStopped: () => {
      generating.value = false;
      assistantMsg.value._done = true;
      assistantMsg.value.content = t('qa.ai.caseGen.genStopped');
      if (workbench.streamingDrafts.length) {
        workbench.drafts = [...workbench.drafts, ...workbench.streamingDrafts.map((d: any) => ({ ...d, _selected: true, _adoptStatus: null }))];
        workbench.streamingDrafts = [];
      }
      pointChatRef.value?.forceScrollToBottom();
    },
  }, abortCtrl.signal);
}

function stopGeneration() {
  if (recordNo.value) stopAiCaseStream(recordNo.value).catch(() => {});
  if (abortCtrl) { abortCtrl.abort(); abortCtrl = null; }
  generating.value = false;
}

/** 断线重连：通过 attachAiCaseStream 恢复 SSE 连接，后端回放 buffer 事件 */
function reattachStream(assistantMsg: any) {
  if (!recordNo.value) return;
  abortCtrl = new AbortController();
  attachAiCaseStream(recordNo.value, {
    onRestore: (fullText: string) => {
      // 后端回放全量已累积文本，恢复 assistant 消息内容
      if (fullText) {
        assistantMsg.value.content = fullText;
        assistantMsg.value._placeholder = false;
      }
      pointChatRef.value?.scrollToBottom();
    },
    onStage: (stage: string) => {
      if (stage === 'gen') assistantMsg.value.content = t('qa.ai.caseGen.generatingCases');
      if (stage === 'context') assistantMsg.value.content = t('qa.ai.caseGen.analyzingDims');
      if (stage === 'audit') assistantMsg.value.content = t('qa.ai.caseGen.auditing');
      pointChatRef.value?.scrollToBottom();
    },
    onDimensions: (dims: any[]) => {
      workbench.dimensions = dims || workbench.dimensions;
    },
    onPointStart: (d: any) => {
      const dim = workbench.dimensions.find((x: any) => x.id === d.tpId);
      if (dim) dim.status = 'GENERATING';
      pointChatRef.value?.scrollToBottom();
    },
    onPointDone: (d: any) => {
      const dim = workbench.dimensions.find((x: any) => x.id === d.tpId);
      if (dim) dim.status = d.failed ? 'FAILED' : 'COVERED';
    },
    onAudit: (audit: any) => { workbench.audit = { ...audit, time: new Date().toLocaleTimeString() }; },
    onResult: (data: any) => {
      generating.value = false;
      assistantMsg.value._done = true;
      if (data.poolDrafts) {
        workbench.drafts = data.poolDrafts.map((x: any) => ({ ...x, _selected: true, _adoptStatus: null, _round: x._round || 1 }));
      }
      if (data.dimensions) workbench.dimensions = data.dimensions;
      if (data.audit) workbench.audit = { ...data.audit, time: new Date().toLocaleTimeString() };
      workbench.streamingDrafts = [];
      const dimTotal = workbench.dimensions.length;
      const dimCovered = coveredCount.value;
      const issueC = issueCount.value;
      assistantMsg.value.content = dimTotal
        ? (issueC
          ? t('qa.ai.caseGen.dimSummaryWithIssue', { total: dimTotal, covered: dimCovered, issues: issueC })
          : t('qa.ai.caseGen.dimSummary', { total: dimTotal, covered: dimCovered }))
        : t('qa.ai.caseGen.draftsGenerated', { count: workbench.drafts.length });
      pointChatRef.value?.forceScrollToBottom();
    },
    onError: (msg: string) => {
      if (msg === '__STOPPED__') return;
      generating.value = false;
      assistantMsg.value._done = true;
      assistantMsg.value.content = t('qa.ai.caseGen.resumeFailed', { msg });
      pointChatRef.value?.forceScrollToBottom();
    },
    onExpired: () => {
      generating.value = false;
      // 记录已过期，从 DB 恢复最终状态
      getRecordState(recordNo.value).then((res: any) => {
        if (res.data) restoreFromRecord(res.data);
      }).catch(() => {});
    },
  }, abortCtrl.signal);
}

// ==================== 采纳 ====================
async function adoptSingle(draft: any) {
  draft._adoptStatus = 'submitting';
  try {
    const res: any = await adoptAiCases({
      recordNo: recordNo.value,
      items: [{ draftId: draft.draftId, caseName: draft.caseName, preCondition: draft.preCondition, testSteps: draft.testSteps, caseType: draft.caseType, priority: draft.priority, tags: draft.tags, expectDuration: draft.expectDuration }] as any,
    });
    const saved = (res.data?.savedList || []).find((s: any) => s.draftId === draft.draftId);
    if (saved) {
      draft._adoptStatus = 'success'; draft._caseCode = saved.caseCode || '';
      const idx = workbench.drafts.indexOf(draft);
      if (idx >= 0) { workbench.drafts.splice(idx, 1); workbench.adoptedDrafts.push(draft); if (recordNo.value) saveDrafts(recordNo.value, workbench.drafts).catch(() => {}); }
      pushMsg({ role: 'assistant', content: t('qa.ai.caseGen.adoptedMsg', { name: draft.caseName, code: saved.caseCode || '' }), _done: true });
    } else {
      draft._adoptStatus = 'skipped'; draft._skipReason = t('qa.ai.caseGen.alreadyAdopted');
    }
    pointChatRef.value?.forceScrollToBottom();
  } catch (e: any) {
    draft._adoptStatus = 'failed'; Message.error(e?.message || t('qa.ai.common.adoptFailed'));
  }
}

function deleteDraft(draft: any) {
  const idx = workbench.drafts.indexOf(draft);
  if (idx >= 0) workbench.drafts.splice(idx, 1);
  if (recordNo.value) saveDrafts(recordNo.value, workbench.drafts).catch(() => {});
}

function onDraftEdited(draft: any) {
  draft.manuallyEdited = true;
  if (recordNo.value) {
    saveDrafts(recordNo.value, workbench.drafts).catch((e: any) => {
      Message.error(t('qa.ai.caseGen.draftSaveFailed', { msg: e?.message || t('qa.ai.common.networkError') }));
    });
  }
}

// ==================== 恢复 ====================
async function restoreFromRecord(state: any) {
  if (!state) return;
  recordNo.value = state.recordNo || '';
  // BUG#2 修复：优先用后端派生的 dimensions（含 COVERED/FAILED 状态），不用原始 testPoints
  if (state.dimensions) {
    workbench.dimensions = state.dimensions;
  } else if (state.stagePayload) {
    try {
      const payload = JSON.parse(state.stagePayload);
      if (payload.testPoints) {
        workbench.dimensions = payload.testPoints.map((x: any) => typeof x === 'string' ? JSON.parse(x) : x);
      }
    } catch {}
  }
  if (workbench.dimensions.length && !activeTpId.value) activeTpId.value = workbench.dimensions[0].id;
  // 对话历史留存：从 rounds 重建按测试点存储的消息
  if (state.rounds && Array.isArray(state.rounds)) {
    for (const round of state.rounds) {
      // #17: 从 instruction 前缀 [TP:xxx] 解析 tpId，没标记的归到 _init
      const tpMatch = String(round.instruction || '').match(/^\[TP:(.+?)\]/);
      const tpId = tpMatch ? tpMatch[1] : (round.tpId || '_init');
      const cleanInstruction = tpMatch ? String(round.instruction).replace(/^\[TP:.+?\]/, '') : round.instruction;
      if (round.instruction) {
        if (!pointMessagesMap.value[tpId]) pointMessagesMap.value[tpId] = [];
        pointMessagesMap.value[tpId].push({ role: 'user', content: String(cleanInstruction), _done: true, _uid: ++msgUid });
      }
      if (round.status === 'DONE' && round.type === 'gen') {
        if (!pointMessagesMap.value[tpId]) pointMessagesMap.value[tpId] = [];
        const d = round.drafts instanceof Array ? round.drafts : [];
        pointMessagesMap.value[tpId].push({ role: 'assistant', content: t('qa.ai.caseGen.draftsGenerated', { count: d.length }), _done: true, _uid: ++msgUid });
      } else if (round.status === 'FAILED') {
        if (!pointMessagesMap.value[tpId]) pointMessagesMap.value[tpId] = [];
        pointMessagesMap.value[tpId].push({ role: 'assistant', content: t('qa.ai.caseGen.genFailedPlain'), _done: true, _uid: ++msgUid });
      } else if (round.status === 'STOPPED') {
        const tpId = round.tpId || '_init';
        if (!pointMessagesMap.value[tpId]) pointMessagesMap.value[tpId] = [];
        pointMessagesMap.value[tpId].push({ role: 'assistant', content: t('qa.ai.caseGen.genStopped'), _done: true, _uid: ++msgUid });
      } else if (round.type === 'qa' && round.rawText) {
        const tpId = round.tpId || '_init';
        if (!pointMessagesMap.value[tpId]) pointMessagesMap.value[tpId] = [];
        pointMessagesMap.value[tpId].push({ role: 'assistant', content: String(round.rawText), _done: true, _isQa: true, _uid: ++msgUid });
      }
    }
  }
  if (state.stagePayload) {
    try {
      const payload = JSON.parse(state.stagePayload);
      if (payload.pool) {
        // pool 中的草稿都是未采纳的（后端 removeAdoptedFromPool 已清理）
        workbench.drafts = payload.pool.map((x: any) => {
          const d = typeof x === 'string' ? JSON.parse(x) : x;
          return { ...d, _selected: true, _adoptStatus: null };
        });
      }
    } catch {}
  }
}

onMounted(async () => {
  if (reqId.value) {
    try {
      const res: any = await getReqSummary(reqId.value);
      reqSummary.value = res.data;
      archivedAudit.value = res.data?.archivedAudit || null;
    } catch {}
    // 检查是否有活跃记录（断线恢复）
    try {
      const recordsRes: any = await listAiCaseRecords(reqId.value);
      const records = recordsRes.data || [];
      const active = records.find((r: any) => r.status === 'ACTIVE');
      if (active) {
        const stateRes: any = await getRecordState(active.recordNo);
        restoreFromRecord(stateRes.data);
        // 有可恢复的测试点/草稿时跳过自动触发（正常断线恢复）；陈旧空记录继续触发
        if (workbench.dimensions.length > 0 || workbench.drafts.length > 0) return;
      }
    } catch {}
    // 新需求：无活跃记录，自动触发生成测试点 + 逐点生成
    // BUG#1 修复：推送用户消息，与 onPointSend 行为一致
    // 「生成用例」同时作为发给后端的指令与展示的用户消息，保持原文不做翻译
    activeTpId.value = ''; // 首次生成无选中测试点，消息存入临时 key
    pointMessagesMap.value['_init'] = [];
    activeTpId.value = '_init';
    pushMsg({ role: 'user', content: '生成用例' });
    startStream({ requirementId: reqId.value, instruction: '生成用例' });
  }
});

onBeforeUnmount(() => { if (abortCtrl) abortCtrl.abort(); });
</script>

<style scoped>
.case-gen-page { height: var(--page-container-height, calc(100vh - 60px)); display: flex; flex-direction: column; }
.cg-header { display: flex; justify-content: space-between; align-items: center; padding: 0 16px; height: 48px; flex-shrink: 0; border-bottom: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.cg-req-code { font-size: 12px; color: var(--color-text-3); }
.cg-req-title { font-size: 16px; font-weight: 600; }
.cg-coverage-bar { display: flex; gap: 12px; font-size: 12px; }
.cb-ok { color: rgb(var(--success-6)); font-weight: 600; }
.cb-warn { color: rgb(var(--warning-6)); }
.cb-pending { color: var(--color-text-3); }
.cb-empty { color: var(--color-text-4); }
.cg-body { flex: 1; min-height: 0; display: flex; overflow: hidden; }
.cg-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.cg-right-top { flex: 1; min-height: 0; border-bottom: 1px solid var(--color-border-2); display: flex; flex-direction: column; overflow: hidden; }
.cg-right-bottom { height: 240px; flex-shrink: 0; display: flex; flex-direction: column; }
</style>
