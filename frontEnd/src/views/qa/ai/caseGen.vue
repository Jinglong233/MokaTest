<template>
  <div class="case-gen-page" v-if="projectStore.hasProjectSelected">
    <div class="cg-header">
      <a-space>
        <a-button size="small" @click="goBack"><template #icon><IconLeft /></template> {{ $t('qa.ai.common.back') }}</a-button>
        <span class="cg-req-code">{{ reqSummary?.reqCode }}</span>
        <span class="cg-req-title">{{ reqSummary?.title }}</span>
      </a-space>
      <div class="cg-coverage-bar">
        <GenProgressBar
          v-if="generating"
          :stage="genStage"
          :dimensions="workbench.dimensions"
          :current-title="currentGenTitle"
          :generating="generating"
          :start-time="genStartTime"
          @stop="stopGeneration"
        />
        <template v-else-if="workbench.dimensions.length">
          <span class="cb-ok">{{ $t('qa.ai.caseGen.covered', { covered: coveredCount, total: workbench.dimensions.length }) }}</span>
          <span v-if="issueCount" class="cb-warn">{{ $t('qa.ai.caseGen.issues', { count: issueCount }) }}</span>
          <span v-if="pendingCount" class="cb-pending">{{ $t('qa.ai.caseGen.pendingGen', { count: pendingCount }) }}</span>
        </template>
        <span v-else class="cb-empty">{{ $t('qa.ai.caseGen.autoList') }}</span>
      </div>
    </div>

    <div class="cg-body">
      <TestPointSidebar
        :dimensions="workbench.dimensions"
        :active-tp-id="activeTpId"
        :draft-counts="draftCounts"
        @select="selectTestPoint"
        @retry="retryDimension"
      />
      <div class="cg-right">
        <div class="cg-right-top">
          <PointDrafts
            :drafts="filteredDrafts"
            :adopted-drafts="filteredAdopted"
            :streaming-drafts="workbench.streamingDrafts"
            :tp-title="activeDim?.title"
            :adopting="adopting"
            :dimensions="workbench.dimensions"
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
            :collapsible="true"
            :collapsed="chatCollapsed"
            :ticker="latestEvent"
            :placeholder="activeDim ? $t('qa.ai.caseGen.chatPlaceholder', { title: activeDim.title }) : $t('qa.ai.caseGen.selectPointFirst')"
            @send="onPointSend"
            @stop="stopGeneration"
            @toggle-collapse="onToggleChatCollapse"
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
import GenProgressBar from './caseGen/GenProgressBar.vue';
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
// 工作台卡片跳转可能只带 recordNo（不带 reqId）
const queryRecordNo = String(route.query.recordNo || '');

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

// ==================== 进度看板状态 ====================
// 进度指挥条：阶段 / 开始时间 / 当前生成维度名；跑马灯：最近一条事件
const genStage = ref('');
const genStartTime = ref(0);
const currentGenTitle = ref('');
const latestEvent = ref('');
// 过程日志折叠：批量生成中默认折叠，用户手动展开后不再自动收起（直到下一次生成开始）
const chatCollapsed = ref(false);
const chatUserToggled = ref(false);
// 仅批量生成结束时自动折叠；逐点对话是用户主动发起的会话，不收起
const chatAutoCollapseOnEnd = ref(false);
function onToggleChatCollapse() {
  chatCollapsed.value = !chatCollapsed.value;
  chatUserToggled.value = true;
}
function autoCollapseChat() {
  if (chatAutoCollapseOnEnd.value && !chatUserToggled.value) chatCollapsed.value = true;
}
function resetProgress() {
  genStage.value = '';
  currentGenTitle.value = '';
}

// 维度 → 草稿产出计数（按 coverPoints 聚合，含流式与已采纳）
const draftCounts = computed(() => {
  const map: Record<string, number> = {};
  const all = [...workbench.drafts, ...workbench.streamingDrafts, ...workbench.adoptedDrafts];
  for (const d of all) {
    for (const p of d.coverPoints || []) map[p] = (map[p] || 0) + 1;
  }
  return map;
});

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
  // 主动查看某个测试点的对话：展开且记为用户手动操作（随后的 onStopped 不再自动收起）
  chatCollapsed.value = false;
  chatUserToggled.value = true;
}

// ==================== 发送（逐点补充） ====================
function onPointSend(text: string) {
  if (!text.trim() || generating.value) return;
  if (!recordNo.value) { Message.warning(t('qa.ai.caseGen.generatePointsFirst')); return; }
  if (!activeTpId.value) { Message.warning(t('qa.ai.caseGen.selectPointFirst')); return; }
  pushMsg({ role: 'user', content: text });
  startStream({ recordNo: recordNo.value, targetTpId: activeTpId.value, instruction: text });
}

// ==================== SSE 回调共享工厂 ====================
// startStream 与 reattachStream 共用的事件处理：阶段/维度/逐点进度驱动进度条 + 跑马灯。
// writeToBubble：逐点/问答流保留旧的聊天气泡进度叙事；批量流（_init）只进进度条和跑马灯。
function createStreamHandlers(assistantMsg: any, opts: { writeToBubble: boolean }) {
  return {
    onStage: (stage: string) => {
      genStage.value = stage;
      if (opts.writeToBubble) {
        if (stage === 'gen') assistantMsg.value.content = t('qa.ai.caseGen.generatingCases');
        if (stage === 'context') assistantMsg.value.content = t('qa.ai.caseGen.analyzingDims');
        if (stage === 'audit') assistantMsg.value.content = t('qa.ai.caseGen.auditing');
      }
      pointChatRef.value?.scrollToBottom();
    },
    onDimensions: (dims: any[]) => {
      workbench.dimensions = dims || workbench.dimensions;
      if (!activeTpId.value && workbench.dimensions.length) activeTpId.value = workbench.dimensions[0].id;
    },
    onPointStart: (d: any) => {
      const dim = workbench.dimensions.find((x: any) => x.id === d.tpId);
      if (dim) dim.status = 'GENERATING';
      currentGenTitle.value = d.title || '';
      latestEvent.value = t('qa.ai.caseGen.marquee.pointStart', { title: d.title || d.tpId });
      if (opts.writeToBubble && d.tpId === activeTpId.value) assistantMsg.value.content = t('qa.ai.caseGen.generatingDim', { title: d.title });
      pointChatRef.value?.scrollToBottom();
    },
    onPointDone: (d: any) => {
      const dim = workbench.dimensions.find((x: any) => x.id === d.tpId);
      if (dim) dim.status = d.failed ? 'FAILED' : 'COVERED';
      const title = dim?.title || d.tpId;
      latestEvent.value = d.failed
        ? t('qa.ai.caseGen.marquee.pointFailed', { title })
        : t('qa.ai.caseGen.marquee.pointDone', { title, count: d.caseCount || 0 });
    },
    onCase: (draft: any) => {
      workbench.streamingDrafts.push(draft);
      latestEvent.value = t('qa.ai.caseGen.marquee.caseCount', { count: workbench.streamingDrafts.length });
    },
    onAudit: (audit: any) => {
      workbench.audit = { ...audit, time: new Date().toLocaleTimeString() };
      latestEvent.value = t('qa.ai.caseGen.marquee.auditDone');
    },
  };
}

/** onResult 公共落库：草稿池/维度/审计（含 poolDrafts 为空时 streamingDrafts 兜底） */
function applyResultData(data: any) {
  if (data.poolDrafts && data.poolDrafts.length > 0) {
    workbench.drafts = data.poolDrafts.map((x: any) => ({ ...x, _selected: true, _adoptStatus: null, _round: x._round || 1 }));
  } else if (workbench.streamingDrafts.length > 0) {
    workbench.drafts = workbench.streamingDrafts.map((x: any) => ({ ...x, _selected: true, _adoptStatus: null, _round: x._round || 1 }));
  }
  if (data.dimensions) workbench.dimensions = data.dimensions;
  if (data.audit) workbench.audit = { ...data.audit, time: new Date().toLocaleTimeString() };
  workbench.streamingDrafts = [];
  workbench.citations = data.citations || [];
  workbench.converged = false;
}

/** 完成总结文案：逐点路径显示当前测试点草稿数，批量路径显示全局维度统计 */
function dimSummaryText(data: any): string {
  if (data.type === 'point') {
    const count = data.pointCaseCount || 0;
    return t('qa.ai.caseGen.pointDraftsDone', { count });
  }
  const dimTotal = workbench.dimensions.length;
  const dimCovered = coveredCount.value;
  const issueC = issueCount.value;
  return dimTotal
    ? (issueC
      ? t('qa.ai.caseGen.dimSummaryWithIssue', { total: dimTotal, covered: dimCovered, issues: issueC })
      : t('qa.ai.caseGen.dimSummary', { total: dimTotal, covered: dimCovered }))
    : t('qa.ai.caseGen.draftsGenerated', { count: workbench.drafts.length });
}

function startStream(body: any) {
  generating.value = true;
  abortCtrl = new AbortController();
  genStartTime.value = Date.now();
  resetProgress();
  latestEvent.value = '';
  // 批量生成（无 targetTpId）：默认折叠过程日志；逐点补充：展开聊天。每轮重置用户手动偏好
  const isPointMode = !!body.targetTpId;
  chatUserToggled.value = false;
  chatAutoCollapseOnEnd.value = !isPointMode;
  chatCollapsed.value = !isPointMode;
  const assistantMsg = ref<any>({
    role: 'assistant', content: t('qa.ai.caseGen.generating'), _done: false, _placeholder: true,
    _isQa: false,
  });
  pushMsg(assistantMsg.value);
  workbench.streamingDrafts = [];
  const shared = createStreamHandlers(assistantMsg, { writeToBubble: isPointMode });

  streamCases(body, {
    onRecord: (no: string) => { recordNo.value = no || ''; },
    ...shared,
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
    onResult: (data: any) => {
      generating.value = false;
      resetProgress();
      autoCollapseChat();
      assistantMsg.value._done = true;
      if (data.type === 'qa') {
        assistantMsg.value._isQa = true;
        pointChatRef.value?.forceScrollToBottom();
        return;
      }
      applyResultData(data);
      // 只有当 delta 未写入文字时才覆盖（V2 批量路径），逐点路径的 delta 文字保留
      if (assistantMsg.value._placeholder) {
        assistantMsg.value.content = dimSummaryText(data);
        // 结束折叠态跑马灯显示单行总结
        latestEvent.value = assistantMsg.value.content;
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
      resetProgress();
      autoCollapseChat();
      assistantMsg.value._done = true;
      assistantMsg.value.content = t('qa.ai.caseGen.genFailed', { msg });
      latestEvent.value = assistantMsg.value.content;
      pointChatRef.value?.forceScrollToBottom();
    },
    onStopped: () => {
      generating.value = false;
      resetProgress();
      autoCollapseChat();
      assistantMsg.value._done = true;
      if (workbench.streamingDrafts.length) {
        workbench.drafts = [...workbench.drafts, ...workbench.streamingDrafts.map((d: any) => ({ ...d, _selected: true, _adoptStatus: null }))];
        assistantMsg.value.content = t('qa.ai.caseGen.genStoppedWithDrafts', { count: workbench.streamingDrafts.length });
        workbench.streamingDrafts = [];
      } else {
        assistantMsg.value.content = t('qa.ai.caseGen.genStopped');
      }
      latestEvent.value = assistantMsg.value.content;
      pointChatRef.value?.forceScrollToBottom();
    },
  }, abortCtrl.signal);
}

function stopGeneration() {
  if (recordNo.value) {
    // 逐点对话停止：带上当前 tpId，后端按 recordNo_tpId 精确取消该测试点的生成缓冲
    const tpId = activeTpId.value && activeTpId.value !== '_init' ? activeTpId.value : undefined;
    stopAiCaseStream(recordNo.value, tpId).catch(() => {});
  }
  if (abortCtrl) { abortCtrl.abort(); abortCtrl = null; }
  generating.value = false;
  resetProgress();
  // 把当前 assistant 气泡标记为已停止，避免永久停在"正在生成…"
  const msgs = pointMessages.value;
  const last = msgs[msgs.length - 1];
  if (last && last.role === 'assistant' && !last._done) {
    last._done = true;
    last._stopped = true;
    last._placeholder = false;
    if (!last.content || last.content === t('qa.ai.caseGen.generating')) last.content = t('qa.ai.caseGen.genStopped');
    pointChatRef.value?.forceScrollToBottom();
  }
}

/** 断线重连：通过 attachAiCaseStream 恢复 SSE 连接，后端回放 buffer 事件 */
function reattachStream(assistantMsg: any) {
  if (!recordNo.value) return;
  abortCtrl = new AbortController();
  const shared = createStreamHandlers(assistantMsg, { writeToBubble: false });
  attachAiCaseStream(recordNo.value, {
    onRestore: (fullText: string) => {
      // 后端回放全量已累积文本，恢复 assistant 消息内容
      if (fullText) {
        assistantMsg.value.content = fullText;
        assistantMsg.value._placeholder = false;
      }
      pointChatRef.value?.scrollToBottom();
    },
    ...shared,
    onResult: (data: any) => {
      generating.value = false;
      resetProgress();
      autoCollapseChat();
      assistantMsg.value._done = true;
      applyResultData(data);
      assistantMsg.value.content = dimSummaryText(data);
      latestEvent.value = assistantMsg.value.content;
      pointChatRef.value?.forceScrollToBottom();
    },
    onError: (msg: string) => {
      if (msg === '__STOPPED__') {
        // 后端确认已停止：必须复位生成状态，否则永久卡在生成中
        generating.value = false;
        resetProgress();
        autoCollapseChat();
        assistantMsg.value._done = true;
        assistantMsg.value._stopped = true;
        return;
      }
      generating.value = false;
      resetProgress();
      autoCollapseChat();
      assistantMsg.value._done = true;
      assistantMsg.value.content = t('qa.ai.caseGen.resumeFailed', { msg });
      latestEvent.value = assistantMsg.value.content;
      pointChatRef.value?.forceScrollToBottom();
    },
    onExpired: () => {
      generating.value = false;
      resetProgress();
      autoCollapseChat();
      // 记录已结束/缓冲已过期：清掉"正在生成…"占位气泡，从 DB 恢复最终状态
      assistantMsg.value._done = true;
      if (assistantMsg.value._placeholder) {
        for (const key of Object.keys(pointMessagesMap.value)) {
          const arr = pointMessagesMap.value[key];
          const idx = arr.indexOf(assistantMsg.value);
          if (idx >= 0) { arr.splice(idx, 1); break; }
        }
      }
      getRecordState(recordNo.value).then((res: any) => {
        if (res.data) restoreFromRecord(res.data);
      }).catch(() => {});
    },
  }, abortCtrl.signal);
}

/** FAILED 维度重试：对该维度发逐点重生 */
function retryDimension(dim: any) {
  if (generating.value || !recordNo.value) return;
  activeTpId.value = dim.id;
  if (!pointMessagesMap.value[dim.id]) pointMessagesMap.value[dim.id] = [];
  const text = t('qa.ai.caseGen.retryInstruction', { title: dim.title });
  pushMsg({ role: 'user', content: text });
  startStream({ recordNo: recordNo.value, targetTpId: dim.id, instruction: text });
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
// 同一记录只重建一次对话消息（onMounted 恢复与 onExpired 兜底可能重复触发）
let restoredRecordNo = '';
async function restoreFromRecord(state: any) {
  if (!state) return;
  const alreadyRestored = !!state.recordNo && state.recordNo === restoredRecordNo;
  if (state.recordNo) restoredRecordNo = state.recordNo;
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
  // 对话历史留存：从 rounds 重建按测试点存储的消息（同一记录只重建一次，防重复）
  if (!alreadyRestored && state.rounds && Array.isArray(state.rounds)) {
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

/** 判断记录是否仍在生成中（决定静态恢复后是否重连续流，而不是另起新生成） */
function isGeneratingState(state: any): boolean {
  if (!state) return false;
  if (state.currentStage === 'CASE_GEN' || state.currentStage === 'POINTS_GEN') return true;
  const latest = state.latestRound;
  return !!latest && latest.status === 'RUNNING';
}

/** 记录仍在生成中：静态恢复落库内容后重连续流（不另起新生成，避免同一需求并行两个生成任务） */
function resumeActiveStream() {
  generating.value = true;
  genStartTime.value = Date.now();
  resetProgress();
  latestEvent.value = '';
  chatUserToggled.value = false;
  chatAutoCollapseOnEnd.value = true;
  chatCollapsed.value = true;
  if (!activeTpId.value) activeTpId.value = '_init';
  if (!pointMessagesMap.value[activeTpId.value]) pointMessagesMap.value[activeTpId.value] = [];
  const assistantMsg = ref<any>({
    role: 'assistant', content: t('qa.ai.caseGen.generating'), _done: false, _placeholder: true,
    _isQa: false,
  });
  pushMsg(assistantMsg.value);
  reattachStream(assistantMsg);
}

onMounted(async () => {
  if (reqId.value) {
    try {
      const res: any = await getReqSummary(reqId.value);
      reqSummary.value = res.data;
      archivedAudit.value = res.data?.archivedAudit || null;
    } catch {}
    // 检查是否有活跃记录（断线恢复）；卡片跳转带了 recordNo 时优先匹配该记录
    try {
      const recordsRes: any = await listAiCaseRecords(reqId.value);
      const records = recordsRes.data || [];
      const active = records.find((r: any) => r.status === 'ACTIVE' && (!queryRecordNo || r.recordNo === queryRecordNo))
        || records.find((r: any) => r.status === 'ACTIVE');
      if (active) {
        const stateRes: any = await getRecordState(active.recordNo);
        restoreFromRecord(stateRes.data);
        // 仍在生成中：重连 SSE 续流后直接返回，绝不触发新生成
        if (isGeneratingState(stateRes.data)) {
          resumeActiveStream();
          return;
        }
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
    return;
  }
  // 仅带 recordNo 进入（工作台卡片跳转）：按记录恢复状态；进行中则重连续流。
  // 无 reqId 时后端现有接口拿不到需求摘要，头部需求信息留空，且绝不自动发起新生成
  if (queryRecordNo) {
    try {
      const stateRes: any = await getRecordState(queryRecordNo);
      if (stateRes.data) {
        restoreFromRecord(stateRes.data);
        if (isGeneratingState(stateRes.data)) resumeActiveStream();
      }
    } catch {}
  }
});

onBeforeUnmount(() => { if (abortCtrl) abortCtrl.abort(); });
</script>

<style scoped>
.case-gen-page { height: var(--page-container-height, calc(100vh - 60px)); display: flex; flex-direction: column; }
.cg-header { display: flex; justify-content: space-between; align-items: center; padding: 0 16px; height: 48px; flex-shrink: 0; border-bottom: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.cg-req-code { font-size: 12px; color: var(--color-text-3); }
.cg-req-title { font-size: 16px; font-weight: 600; }
.cg-coverage-bar { flex: 1; display: flex; align-items: center; justify-content: center; gap: 12px; font-size: 12px; margin: 0 16px; min-width: 0; }
.cb-ok { color: rgb(var(--success-6)); font-weight: 600; }
.cb-warn { color: rgb(var(--warning-6)); }
.cb-pending { color: var(--color-text-3); }
.cb-empty { color: var(--color-text-4); }
.cg-body { flex: 1; min-height: 0; display: flex; overflow: hidden; }
.cg-right { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.cg-right-top { flex: 1; min-height: 0; border-bottom: 1px solid var(--color-border-2); display: flex; flex-direction: column; overflow: hidden; }
.cg-right-bottom { height: 240px; flex-shrink: 0; display: flex; flex-direction: column; }
</style>
