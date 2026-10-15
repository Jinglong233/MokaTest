<template>
  <a-modal
      v-model:visible="visible"
      :title="sceneConfig.title"
      width="1080px"
      :mask-closable="false"
      :footer="false"
      @close="handleClose"
  >
    <div class="chat-layout">
      <!-- 左侧：会话历史（ChatGPT 侧栏风格） -->
      <div class="session-panel">
        <a-button type="primary" long size="small" @click="startNewSession">
          <template #icon><icon-plus/></template>
          {{ $t('components.aiGenerate.newSession') }}
        </a-button>
        <a-spin :loading="sessionsLoading" style="width: 100%">
          <div class="session-list">
            <template v-for="group in groupedSessions" :key="group.label">
              <div v-if="group.items.length" class="session-group-label">{{ group.label }}</div>
              <div
                  v-for="s in group.items"
                  :key="s.recordNo"
                  class="session-item"
                  :class="{ active: s.recordNo === recordNo }"
                  @click="loadSession(s)"
              >
                <div class="session-title-row">
                  <icon-loading v-if="sessionGenerating(s)" class="session-spin"/>
                  <span class="session-title">{{ sessionTitle(s) }}</span>
                  <a-popconfirm :content="$t('components.aiGenerate.deleteSessionConfirm')" @ok="handleDeleteSession(s)">
                    <icon-delete class="session-delete" @click.stop/>
                  </a-popconfirm>
                </div>
                <div class="session-meta">
                  <span>{{ formatTime(s.createTime) }}</span>
                  <span v-if="adoptCount(s) > 0" class="adopted">{{ $t('components.aiGenerate.adoptedCount', { count: adoptCount(s) }) }}</span>
                  <a-tag v-if="isExpired(s)" size="small">{{ $t('components.aiGenerate.expired') }}</a-tag>
                </div>
              </div>
            </template>
            <a-empty v-if="!sessions.length" :description="$t('components.aiGenerate.emptySessions')" :image-size="60"/>
          </div>
        </a-spin>
      </div>

      <!-- 右侧：对话区（消息列表复用 AI 工作台聊天面板，卡片经 #card 插槽注入） -->
      <div class="chat-panel">
        <AiChatPanel
            ref="chatPanelRef"
            :messages="chatMessages"
            :generating="generating"
            :isAtBottom="isAtBottom"
            @scroll="onScrollChange"
        >
          <template #empty>
            <a-empty :description="$t('components.aiGenerate.emptyHint', { entity: sceneConfig.entityName })"/>
          </template>
          <template #card="{ msg }">
            <!-- 需求不确定点提示：模型主动暴露的需求缺陷，引导补充后重新生成 -->
            <div v-if="msg.cardType === 'uncertainties'" class="unc-banner">
              <div class="unc-title">
                <icon-exclamation-circle-fill/>
                {{ $t('components.aiGenerate.uncertaintiesTitle', { count: msg.data.items.length }) }}
              </div>
              <ul class="unc-list">
                <li v-for="(u, i) in msg.data.items" :key="i">{{ u }}</li>
              </ul>
              <a-button
                  v-if="msg.data.actionable"
                  size="mini"
                  type="outline"
                  @click="fillSupplement"
              >
                {{ $t('components.aiGenerate.supplementRegenerate') }}
              </a-button>
            </div>
            <!-- 草稿表格卡片 -->
            <ApiCaseDraftCard
                v-else-if="msg.cardType === 'drafts'"
                :scene="scene"
                :drafts="msg.data.round.drafts"
                :editable="msg.data.editable"
                :adopting="adopting"
                @adopt="(items) => handleAdopt(msg.data.round, items)"
            />
            <!-- 轮次页脚：已停止标记 + 最新一轮的重新生成/重试动作 -->
            <div v-else-if="msg.cardType === 'round-footer'" class="round-footer">
              <div v-if="msg.data.stopped" class="round-tag stopped-tag">{{ $t('components.aiGenerate.stoppedTag') }}</div>
              <div v-if="msg.data.showActions" class="round-actions">
                <a-tooltip :content="$t('components.aiGenerate.regenerateTip')">
                  <a-button size="mini" :loading="generating" :disabled="!recordNo" @click="handleRegenerate">
                    <template #icon><icon-refresh/></template>
                    {{ $t('components.aiGenerate.regenerate') }}
                  </a-button>
                </a-tooltip>
                <a-button v-if="msg.data.failed || msg.data.stopped" size="mini" type="primary"
                          @click="handleRetry(msg.data.round)">
                  {{ msg.data.stopped ? $t('components.aiGenerate.continueGenerate') : $t('components.aiGenerate.retry') }}
                </a-button>
              </div>
            </div>
          </template>
        </AiChatPanel>

        <!-- 输入区 -->
        <div class="chat-input">
          <a-alert v-if="currentExpired" type="info" style="margin-bottom: 8px">
            {{ $t('components.aiGenerate.expiredAlert') }}
          </a-alert>
          <div class="input-row">
            <a-textarea
                v-model="form.instruction"
                :placeholder="recordNo && !currentExpired ? $t('components.aiGenerate.appendPlaceholder') : $t('components.aiGenerate.generatePlaceholder')"
                :auto-size="{ minRows: 2, maxRows: 4 }"
                :disabled="generating || currentExpired"
                @keydown.enter.ctrl.exact="handleSend"
            />
            <div class="send-btns">
              <a-button v-if="generating" status="danger" @click="handleStop">
                <template #icon><icon-stop/></template>
                {{ $t('components.aiGenerate.stop') }}
              </a-button>
              <a-button
                  v-else
                  type="primary"
                  :disabled="currentExpired || !form.instruction.trim()"
                  @click="handleSend"
              >
                <template #icon><icon-send/></template>
                {{ recordNo ? $t('components.aiGenerate.append') : $t('components.aiGenerate.generate') }}
              </a-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, nextTick, reactive, ref} from 'vue';
import {Message} from '@arco-design/web-vue';
import {useI18n} from 'vue-i18n';
import AiChatPanel from '@/views/qa/ai/AiChatPanel.vue';
import ApiCaseDraftCard from '@/components/ai-generate/ApiCaseDraftCard.vue';
import {
  adoptAiCases,
  appendCaseStream,
  attachAiCaseStream,
  deleteAiCaseRecord,
  generateCaseStream,
  listAiCaseRecords,
  regenerateAiCaseStream,
  stopAiCaseStream,
} from '@/api/MyApi/aiCase';
import {
  adoptAiApiCases,
  appendApiCaseStream,
  attachAiApiCaseStream,
  deleteAiApiCaseRecord,
  generateApiCaseStream,
  listAiApiCaseRecords,
  regenerateAiApiCaseStream,
  stopAiApiCaseStream,
} from '@/api/MyApi/aiApiCase';

/**
 * AI 生成聊天弹窗（场景一/四通用，对齐 ChatGPT/DeepSeek 交互）
 *
 * 消息流：用户气泡 + AI 气泡（流式文字实时渲染，完成折叠原文出草稿卡片）；
 * 停止保留部分内容；重新生成；跨关闭重连续流；会话列表分组/删除。
 */
const props = defineProps<{
  scene: 'case' | 'apiCase';
  entityId: number;
}>();

const emit = defineEmits<{
  (e: 'adopted', caseIds: number[]): void;
}>();

const {t} = useI18n();

/** 场景 API 选择器 */
const api = computed(() => props.scene === 'case' ? {
    list: listAiCaseRecords,
    generate: (body: any, cb: any, sig?: AbortSignal) => generateCaseStream({requirementId: props.entityId, ...body}, cb, sig),
    append: appendCaseStream,
    adopt: adoptAiCases,
    stop: stopAiCaseStream,
    regenerate: regenerateAiCaseStream,
    attach: attachAiCaseStream,
    deleteRecord: deleteAiCaseRecord,
} : {
    list: listAiApiCaseRecords,
    generate: (body: any, cb: any, sig?: AbortSignal) => generateApiCaseStream({apiId: props.entityId, ...body}, cb, sig),
    append: appendApiCaseStream,
    adopt: adoptAiApiCases,
    stop: stopAiApiCaseStream,
    regenerate: regenerateAiApiCaseStream,
    attach: attachAiApiCaseStream,
    deleteRecord: deleteAiApiCaseRecord,
});

const sceneConfig = computed(() => props.scene === 'case'
    ? {title: t('components.aiGenerate.titleCase'), entityName: t('components.aiGenerate.entityRequirement')}
    : {title: t('components.aiGenerate.titleApiCase'), entityName: t('components.aiGenerate.entityApi')});

const visible = ref(false);
const generating = ref(false);
const adopting = ref(false);
const sessionsLoading = ref(false);
const sessions = ref<any[]>([]);
const recordNo = ref('');
const rounds = ref<any[]>([]);
const currentExpired = ref(false);
const isAtBottom = ref(true);
const chatPanelRef = ref<InstanceType<typeof AiChatPanel>>();
let abortController: AbortController | null = null;
let pollTimer: ReturnType<typeof setInterval> | null = null;

const form = reactive<any>({
    instruction: '',
});

/**
 * 轮次模型 → AiChatPanel 消息模型：
 * 每轮映射为 用户气泡 + AI 气泡（rawText 流式渲染/失败标记/引用标题），
 * 之后按需追加 uncertainties 提示卡、草稿表格卡、轮次页脚卡（已停止标记 + 最新一轮动作条）。
 * 最新一轮始终垫一个页脚卡，保证 AI 气泡不是最后一条消息，
 * 避免 AiChatPanel 内置的 regenerate/retry 按钮在过期会话上误显示。
 */
const chatMessages = computed(() => {
    const msgs: any[] = [];
    rounds.value.forEach((round: any, idx: number) => {
        const latest = idx === rounds.value.length - 1;
        const actionable = latest && !currentExpired.value;
        const failed = isFailed(round);
        msgs.push({role: 'user', content: round.instruction || t('components.aiGenerate.noInstruction')});
        msgs.push({
            role: 'assistant',
            content: round.rawText || '',
            _done: round.status !== 'GENERATING',
            _error: failed,
            _errorMsg: round.error,
            citations: failed ? [] : [...new Set<string>((round.citations || []).map((c: any) => c.title))],
        });
        if (round.status !== 'GENERATING' && !failed) {
            if (round.uncertainties?.length) {
                msgs.push({role: 'card', cardType: 'uncertainties', data: {items: round.uncertainties, actionable}});
            }
            if (round.drafts?.length) {
                msgs.push({role: 'card', cardType: 'drafts', data: {round, editable: actionable}});
            }
        }
        if (latest && round.status !== 'GENERATING') {
            msgs.push({role: 'card', cardType: 'round-footer', data: {round, stopped: round.status === 'STOPPED', failed, showActions: actionable}});
        } else if (round.status === 'STOPPED') {
            msgs.push({role: 'card', cardType: 'round-footer', data: {round, stopped: true, failed: false, showActions: false}});
        }
    });
    return msgs;
});

// ==================== 会话管理 ====================

async function loadSessions() {
    sessionsLoading.value = true;
    try {
        const res: any = await api.value.list(props.entityId);
        sessions.value = res.data || [];
    } finally {
        sessionsLoading.value = false;
    }
}

function open(prefillInstruction?: string) {
    visible.value = true;
    recordNo.value = '';
    rounds.value = [];
    currentExpired.value = false;
    form.instruction = prefillInstruction || '';
    loadSessions();
}

function startNewSession() {
    abortController?.abort();
    stopPolling();
    generating.value = false;
    recordNo.value = '';
    rounds.value = [];
    currentExpired.value = false;
}

function loadSession(session: any) {
    abortController?.abort();
    stopPolling();
    generating.value = false;
    recordNo.value = session.recordNo;
    currentExpired.value = isExpired(session);
    renderSession(session);
    // 最新轮仍在生成中：优先重连续流（实时看到生成过程），缓冲过期则降级轮询
    const latest = rounds.value[rounds.value.length - 1];
    if (latest?.status === 'GENERATING' && !currentExpired.value) {
        attachStream(session.recordNo);
    }
    scrollToBottom();
}

/** 把一条会话记录渲染为对话轮次 */
function renderSession(session: any) {
    const adoptedIds = parseAdoptedDraftIds(session.adoptedDetail);
    rounds.value = parseRounds(session.outputSnapshot).map((r: any) => ({
        ...r,
        drafts: (r.drafts || []).map((d: any) => ({
            ...d,
            _checked: !adoptedIds.has(d.draftId),
            _adopted: adoptedIds.has(d.draftId),
        })),
    }));
}

/** 重连生成流：回放缓冲 + 接实时流 */
function attachStream(no: string) {
    abortController?.abort();
    const ctrl = new AbortController();
    abortController = ctrl;
    generating.value = true;
    // 从服务端记录的 rawText 续显
    const latest = rounds.value[rounds.value.length - 1];
    api.value.attach(no, {
        onDelta: (chunk: string) => {
            if (abortController !== ctrl) return;
            if (latest) {
                latest.rawText = (latest.rawText || '') + chunk;
            }
            scrollToBottom();
        },
        onResult: async (_no: string) => {
            if (abortController !== ctrl) return;
            generating.value = false;
            await refreshCurrentSession(no);
            loadSessions();
        },
        onError: (msg: string) => {
            if (abortController !== ctrl) return;
            generating.value = false;
            if (msg !== '__STOPPED__') {
                refreshCurrentSession(no);
            } else {
                refreshCurrentSession(no);
            }
        },
        onExpired: () => {
            // 服务端缓冲已失效（重启/超时）：降级轮询
            if (abortController !== ctrl) return;
            startPolling(no);
        },
    }, ctrl.signal);
}

/** 刷新当前会话内容（重连结束/降级轮询用） */
async function refreshCurrentSession(no: string) {
    try {
        const res: any = await api.value.list(props.entityId);
        sessions.value = res.data || [];
        const current = sessions.value.find((s: any) => s.recordNo === no);
        if (current) {
            renderSession(current);
            scrollToBottom();
        }
    } catch { /* 忽略 */ }
}

function startPolling(no: string) {
    stopPolling();
    pollTimer = setInterval(async () => {
        await refreshCurrentSession(no);
        const latest = rounds.value[rounds.value.length - 1];
        if (latest?.status !== 'GENERATING') {
            stopPolling();
            generating.value = false;
            loadSessions();
        }
    }, 3000);
}

function stopPolling() {
    if (pollTimer) {
        clearInterval(pollTimer);
        pollTimer = null;
    }
}

async function handleDeleteSession(s: any) {
    await api.value.deleteRecord(s.recordNo);
    if (recordNo.value === s.recordNo) {
        startNewSession();
    }
    loadSessions();
    Message.success(t('components.aiGenerate.sessionDeleted'));
}

// ==================== 会话列表展示 ====================

function sessionTitle(s: any) {
    const roundsArr = parseRounds(s.outputSnapshot);
    const first = roundsArr.find((r: any) => r.instruction);
    const text = first?.instruction || s.inputSummary || t('components.aiGenerate.unnamedSession');
    return text.length > 20 ? text.slice(0, 20) + '…' : text;
}

function sessionGenerating(s: any) {
    const rs = parseRounds(s.outputSnapshot);
    return rs.length && rs[rs.length - 1]?.status === 'GENERATING' && !isExpired(s);
}

const groupedSessions = computed(() => {
    const now = new Date();
    const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const groups = [
        {label: t('components.aiGenerate.groupToday'), items: [] as any[]},
        {label: t('components.aiGenerate.groupYesterday'), items: [] as any[]},
        {label: t('components.aiGenerate.group7Days'), items: [] as any[]},
        {label: t('components.aiGenerate.groupEarlier'), items: [] as any[]},
    ];
    for (const s of sessions.value) {
        const createTs = s.createTime ? new Date(s.createTime).getTime() : 0;
        if (createTs >= startOfDay) groups[0].items.push(s);
        else if (createTs >= startOfDay - 86400000) groups[1].items.push(s);
        else if (createTs >= startOfDay - 7 * 86400000) groups[2].items.push(s);
        else groups[3].items.push(s);
    }
    return groups;
});

function isExpired(s: any) {
    if (s.status === 'EXPIRED') return true;
    if (s.expireTime && new Date(s.expireTime).getTime() < Date.now()) return true;
    return false;
}

function parseRounds(snapshot: string): any[] {
    if (!snapshot) return [];
    try {
        const arr = JSON.parse(snapshot);
        if (!Array.isArray(arr) || !arr.length) return [];
        if (arr[0] && typeof arr[0] === 'object' && 'drafts' in arr[0]) {
            return arr;
        }
        return [{round: 1, instruction: null, time: null, status: 'DONE', drafts: arr}];
    } catch {
        return [];
    }
}

function parseAdoptedDraftIds(adoptedDetail: string): Set<string> {
    const ids = new Set<string>();
    if (!adoptedDetail) return ids;
    try {
        const parsed = JSON.parse(adoptedDetail);
        if (Array.isArray(parsed)) {
            for (const entry of parsed) {
                if (entry?.draftId) ids.add(entry.draftId);
            }
        }
    } catch { /* 忽略 */ }
    return ids;
}

function adoptCount(s: any) {
    if (!s.adoptedDetail) return 0;
    try {
        const parsed = JSON.parse(s.adoptedDetail);
        if (Array.isArray(parsed)) return parsed.length;
        return parsed.count || 0;
    } catch {
        return 0;
    }
}

function formatTime(time: any) {
    if (!time) return '';
    const d = new Date(time);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getMonth() + 1}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function isFailed(round: any) {
    return round.status === 'FAILED' || (!!round.error && round.status !== 'STOPPED');
}

// ==================== 生成 ====================

function makeCallbacks(instruction: string, targetRound?: any) {
    const ctrl = new AbortController();
    abortController = ctrl;
    generating.value = true;
    const stale = () => abortController !== ctrl;
    return {
        signal: ctrl.signal,
        callbacks: {
            onDelta: (chunk: string) => {
                if (stale()) return;
                if (targetRound) {
                    targetRound.rawText = (targetRound.rawText || '') + chunk;
                }
                scrollToBottom();
            },
            onResult: (no: string, drafts: any[]) => {
                if (stale()) return;
                recordNo.value = no;
                generating.value = false;
                form.instruction = '';
                // 以服务端为准刷新整个会话（rawText/draftId 完整）
                refreshCurrentSession(no);
                loadSessions();
                Message.success(t('components.aiGenerate.draftsGenerated', {count: drafts.length}));
            },
            onError: (msg: string) => {
                if (stale()) return;
                generating.value = false;
                if (msg === '__STOPPED__') {
                    if (targetRound) targetRound.status = 'STOPPED';
                    if (recordNo.value) refreshCurrentSession(recordNo.value);
                    Message.info(t('components.aiGenerate.stoppedInfo'));
                } else {
                    // 本地轮次标记失败，首轮失败也能就地看到失败卡片并重试，
                    // 不必关弹窗重进后再从会话列表找回
                    if (targetRound) {
                        targetRound.status = 'FAILED';
                        targetRound.error = msg || t('components.aiGenerate.generateFailed');
                    }
                    if (recordNo.value) refreshCurrentSession(recordNo.value);
                    loadSessions();
                    Message.error(msg || t('components.aiGenerate.generateFailed'));
                }
            },
        },
    };
}

function handleSend() {
    if (generating.value || !form.instruction.trim()) return;
    const instruction = form.instruction;
    const body = {instruction};
    // 本地先推一轮（用户气泡 + AI 生成中气泡），流式内容实时渲染进这一轮，
    // 避免追加期间界面无反馈、看起来像触发了上一轮的「重新生成」
    const newRound = reactive({status: 'GENERATING', instruction, rawText: '', drafts: []});
    rounds.value.push(newRound);
    form.instruction = '';
    scrollToBottom();
    const {callbacks, signal} = makeCallbacks(instruction, newRound);
    if (recordNo.value) {
        api.value.append({recordNo: recordNo.value, ...body}, callbacks, signal);
    } else {
        api.value.generate(body, callbacks, signal);
    }
}

async function handleStop() {
    const no = recordNo.value;
    abortController?.abort();
    generating.value = false;
    if (no) {
        try {
            await api.value.stop(no);
        } finally {
            refreshCurrentSession(no);
        }
    }
    Message.info(t('components.aiGenerate.stoppedInfo'));
}

/** 重新生成：删除最后一轮，按同一指令重跑 */
function handleRegenerate() {
    if (!recordNo.value || generating.value) return;
    const latest = rounds.value[rounds.value.length - 1];
    rounds.value.pop(); // 本地先移除，流式期间展示新轮
    const newRound = reactive({status: 'GENERATING', instruction: latest?.instruction, rawText: '', drafts: []});
    rounds.value.push(newRound);
    const {callbacks, signal} = makeCallbacks(latest?.instruction || '', newRound);
    api.value.regenerate(recordNo.value, callbacks, signal);
}

/** 失败/停止重试：回填指令按追加发送 */
function handleRetry(round: any) {
    form.instruction = round.instruction || '';
    handleSend();
}

/** uncertainties 引导：回填「补充说明：」前缀并聚焦输入框，用户补完按追加发送 */
function fillSupplement() {
    form.instruction = t('components.aiGenerate.supplementPrefix');
    nextTick(() => {
        const el = document.querySelector<HTMLElement>('.chat-input textarea');
        el?.focus();
    });
}

// ==================== 入库 ====================

/** 草稿卡片勾选态洗净后触发，父级负责调入库接口并按结果回写草稿行 */
async function handleAdopt(round: any, items: any[]) {
    adopting.value = true;
    try {
        const res: any = await api.value.adopt({recordNo: recordNo.value, items});
        const data = res.data || {};
        const adoptedSet = new Set<string>(data.adoptedDraftIds || []);
        round.drafts.forEach((d: any) => {
            if (adoptedSet.has(d.draftId)) {
                d._adopted = true;
                d._checked = false;
            }
        });
        let msg = res.msg || t('components.aiGenerate.adoptSuccess');
        if (data.duplicateNames?.length) {
            msg += t('components.aiGenerate.duplicateNote', {count: data.duplicateNames.length});
        }
        Message.success(msg);
        emit('adopted', data.savedIds || []);
        loadSessions();
    } finally {
        adopting.value = false;
    }
}

// ==================== 其他 ====================

function handleClose() {
    // 关闭弹窗不中断生成：后端继续跑完并落库，重开时可从会话列表重连续流
    generating.value = false;
    stopPolling();
}

function onScrollChange(atBottom: boolean) {
    isAtBottom.value = atBottom;
}

function scrollToBottom() {
    nextTick(() => {
        const el = chatPanelRef.value?.getChatBody?.();
        if (el) {
            el.scrollTop = el.scrollHeight;
        }
    });
}

defineExpose({open});
</script>

<style scoped>
.chat-layout {
  display: flex;
  gap: 12px;
  height: 560px;
}

.session-panel {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  max-height: 500px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-right: 2px;
}

.session-list::-webkit-scrollbar {
  width: 6px;
}
.session-list::-webkit-scrollbar-thumb {
  background: var(--color-fill-4);
  border-radius: 3px;
}
.session-list::-webkit-scrollbar-track {
  background: var(--color-fill-1);
  border-radius: 3px;
}

.session-group-label {
  font-size: 12px;
  color: var(--color-text-3);
  padding: 6px 4px 2px;
}

.session-item {
  border: 1px solid transparent;
  border-radius: 6px;
  padding: 6px 8px;
  cursor: pointer;
  transition: background 0.15s;
}

.session-item:hover {
  background: var(--color-fill-2);
}

.session-item.active {
  background: rgb(var(--primary-1));
  border-color: rgb(var(--primary-5));
}

.session-title-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.session-spin {
  color: rgb(var(--primary-6));
  flex-shrink: 0;
}

.session-title {
  font-size: 13px;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-delete {
  color: var(--color-text-3);
  visibility: hidden;
  flex-shrink: 0;
}

.session-item:hover .session-delete {
  visibility: visible;
}

.session-delete:hover {
  color: rgb(var(--red-6));
}

.session-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: var(--color-text-3);
  margin-top: 2px;
  align-items: center;
}

.session-meta .adopted {
  color: rgb(var(--green-6));
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.round-tag {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 6px;
}

.stopped-tag {
  color: rgb(var(--orange-6));
}

.round-footer {
  max-width: 100%;
}

.round-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
  align-items: center;
}

.unc-banner {
  padding: 8px 10px;
  border: 1px solid rgb(var(--orange-3));
  background: rgb(var(--orange-1));
  border-radius: 6px;
  font-size: 12px;
}

.unc-title {
  color: rgb(var(--orange-7));
  font-weight: 500;
  display: flex;
  align-items: center;
  gap: 4px;
}

.unc-list {
  margin: 6px 0;
  padding-left: 18px;
  color: var(--color-text-2);
  line-height: 1.7;
}

.unc-banner .arco-btn {
  margin-top: 2px;
}

.chat-input {
  border-top: 1px solid var(--color-border-2);
  padding-top: 8px;
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-row :deep(.arco-textarea-wrapper) {
  flex: 1;
}

.send-btns {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
</style>
