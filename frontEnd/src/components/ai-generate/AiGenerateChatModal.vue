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
          新会话
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
                  <a-popconfirm content="删除该会话？（不影响已入库用例）" @ok="handleDeleteSession(s)">
                    <icon-delete class="session-delete" @click.stop/>
                  </a-popconfirm>
                </div>
                <div class="session-meta">
                  <span>{{ formatTime(s.createTime) }}</span>
                  <span v-if="adoptCount(s) > 0" class="adopted">已采纳 {{ adoptCount(s) }}</span>
                  <a-tag v-if="isExpired(s)" size="small">已过期</a-tag>
                </div>
              </div>
            </template>
            <a-empty v-if="!sessions.length" description="暂无历史会话" :image-size="60"/>
          </div>
        </a-spin>
      </div>

      <!-- 右侧：对话区 -->
      <div class="chat-panel">
        <div ref="chatBodyRef" class="chat-body">
          <template v-if="rounds.length > 0">
            <div v-for="(round, idx) in rounds" :key="idx" class="round">
              <!-- 用户消息 -->
              <div class="msg user-msg">
                <div class="bubble">{{ round.instruction || '（无指令）' }}</div>
              </div>
              <!-- AI 消息 -->
              <div class="msg ai-msg">
                <div class="bubble ai-bubble" :class="{ 'error-bubble': isFailed(round) }">
                  <!-- 生成中：流式文字实时渲染 -->
                  <template v-if="round.status === 'GENERATING'">
                    <div class="round-tag"><icon-loading/> AI 正在生成…</div>
                    <pre class="stream-text">{{ round.rawText || ' ' }}<span class="cursor">▍</span></pre>
                  </template>
                  <!-- 失败 -->
                  <template v-else-if="isFailed(round)">
                    <div class="round-tag">生成失败</div>
                    <div class="error-text">{{ round.error }}</div>
                  </template>
                  <!-- 停止/完成：原文（可折叠） + 草稿卡片 -->
                  <template v-else>
                    <div v-if="round.status === 'STOPPED'" class="round-tag stopped-tag">已停止（保留部分内容）</div>
                    <!-- 问答轮：markdown 渲染展示回答 -->
                    <div v-if="round.type === 'qa'" class="stream-text md-body" v-html="renderMarkdown(round.rawText)"></div>
                    <template v-else>
                      <pre v-if="round.rawText && round._showRaw" class="stream-text">{{ round.rawText }}</pre>
                      <a-link v-if="round.rawText" class="raw-toggle" @click="round._showRaw = !round._showRaw">
                        {{ round._showRaw ? '收起原文' : '查看原文' }}
                      </a-link>
                    </template>

                    <!-- 知识库引用：生成时检索命中的文档片段，可展开查看 -->
                    <div v-if="round.citations?.length" class="cite-banner">
                      <div class="cite-title" @click="round._showCites = !round._showCites">
                        <icon-book/>
                        <span>依据 {{ citeTitles(round) }}</span>
                        <icon-down v-if="!round._showCites"/>
                        <icon-up v-else/>
                      </div>
                      <ul v-if="round._showCites" class="cite-list">
                        <li v-for="(c, i) in round.citations" :key="i">
                          <span class="cite-doc">《{{ c.title }}》</span>{{ c.snippet }}
                        </li>
                      </ul>
                    </div>

                    <!-- 需求不确定点提示：模型主动暴露的需求缺陷，引导补充后重新生成 -->
                    <div v-if="round.uncertainties?.length" class="unc-banner">
                      <div class="unc-title">
                        <icon-exclamation-circle-fill/>
                        需求存在 {{ round.uncertainties.length }} 处不明确，可能影响用例准确性：
                      </div>
                      <ul class="unc-list">
                        <li v-for="(u, i) in round.uncertainties" :key="i">{{ u }}</li>
                      </ul>
                      <a-button
                          v-if="isLatest(idx) && !currentExpired"
                          size="mini"
                          type="outline"
                          @click="fillSupplement"
                      >
                        补充说明后重新生成
                      </a-button>
                    </div>

                    <a-table
                        v-if="round.drafts.length"
                        :data="round.drafts"
                        :pagination="false"
                        size="small"
                        :scroll="{ y: 260 }"
                        style="margin-top: 6px"
                    >
                      <template #columns>
                        <a-table-column v-if="isLatest(idx) && !currentExpired" :width="40" title="">
                          <template #cell="{ record }">
                            <a-checkbox v-model="record._checked" :disabled="record._adopted"/>
                          </template>
                        </a-table-column>
                        <a-table-column title="名称" :width="200">
                          <template #cell="{ record }">
                            <a-input v-if="isLatest(idx) && !currentExpired && !record._adopted" v-model="record.caseName" size="mini"/>
                            <span v-else>{{ record.caseName }}</span>
                            <a-tag v-if="record._adopted" size="small" color="green" style="margin-left: 4px">已入库</a-tag>
                          </template>
                        </a-table-column>
                        <template v-if="scene === 'case'">
                          <a-table-column title="类型" :width="90">
                            <template #cell="{ record }">
                              <a-tag size="small" :color="record.enumFallback ? 'orange' : undefined">{{ record.caseType }}</a-tag>
                            </template>
                          </a-table-column>
                          <a-table-column title="优先级" :width="70">
                            <template #cell="{ record }">
                              <a-tag size="small" :color="record.enumFallback ? 'orange' : undefined">{{ record.priority }}</a-tag>
                            </template>
                          </a-table-column>
                          <a-table-column title="步骤数" :width="70">
                            <template #cell="{ record }">{{ record.testSteps?.length || 0 }}</template>
                          </a-table-column>
                        </template>
                        <template v-else>
                          <a-table-column title="场景说明" :width="200">
                            <template #cell="{ record }">{{ record.description || '-' }}</template>
                          </a-table-column>
                          <a-table-column title="断言" :width="60">
                            <template #cell="{ record }">{{ record.assertions?.length || 0 }}</template>
                          </a-table-column>
                          <a-table-column title="Body" :width="70">
                            <template #cell="{ record }">
                              <a-tag v-if="record.bodyJson" size="small" color="blue">覆盖</a-tag>
                              <span v-else style="color: var(--color-text-3)">沿用</span>
                            </template>
                          </a-table-column>
                        </template>
                      </template>
                    </a-table>
                  </template>

                  <!-- 消息动作条（最新一轮） -->
                  <div v-if="isLatest(idx) && !currentExpired && round.status !== 'GENERATING'" class="round-actions">
                    <a-tooltip content="按同一指令重新生成">
                      <a-button size="mini" :loading="generating" :disabled="!recordNo" @click="handleRegenerate">
                        <template #icon><icon-refresh/></template>
                        重新生成
                      </a-button>
                    </a-tooltip>
                    <a-button v-if="isFailed(round) || round.status === 'STOPPED'" size="mini" type="primary"
                              @click="handleRetry(round)">
                      {{ round.status === 'STOPPED' ? '继续生成' : '重试' }}
                    </a-button>
                    <template v-if="round.drafts.length">
                      <a-button size="mini" :disabled="adoptableCount(round) === 0" @click="toggleAll(round)">
                        {{ allChecked(round) ? '取消全选' : '全选' }}
                      </a-button>
                      <a-button
                          v-if="adoptableCount(round) > 0"
                          type="primary"
                          size="mini"
                          :loading="adopting"
                          :disabled="checkedCount(round) === 0"
                          @click="handleAdopt(round)"
                      >
                        入库所选（{{ checkedCount(round) }}）
                      </a-button>
                      <a-tag v-else color="green">已全部入库 ✓</a-tag>
                    </template>
                  </div>
                </div>
              </div>
            </div>
          </template>

          <!-- 新会话流式输出中 -->
          <div v-if="generating && !rounds.length" class="msg ai-msg">
            <div class="bubble ai-bubble">
              <div class="round-tag"><icon-loading/> AI 正在生成…</div>
              <pre class="stream-text">{{ streamText || ' ' }}<span class="cursor">▍</span></pre>
            </div>
          </div>

          <a-empty v-if="!rounds.length && !generating"
                   :description="`在下方输入指令，AI 将基于当前${sceneConfig.entityName}设计用例`"/>
        </div>

        <!-- 输入区 -->
        <div class="chat-input">
          <a-alert v-if="currentExpired" type="info" style="margin-bottom: 8px">
            该会话已过期（24h），仅供查看；点击左侧「新会话」重新开始。
          </a-alert>
          <div class="input-row">
            <a-textarea
                v-model="form.instruction"
                :placeholder="recordNo && !currentExpired ? '追加指令，如：再补充 3 条异常场景' : '生成指令，如：生成 5 条，重点覆盖边界场景'"
                :auto-size="{ minRows: 2, maxRows: 4 }"
                :disabled="generating || currentExpired"
                @keydown.enter.ctrl.exact="handleSend"
            />
            <div class="send-btns">
              <a-button v-if="generating" status="danger" @click="handleStop">
                <template #icon><icon-stop/></template>
                停止
              </a-button>
              <a-button
                  v-else
                  type="primary"
                  :disabled="currentExpired || !form.instruction.trim()"
                  @click="handleSend"
              >
                <template #icon><icon-send/></template>
                {{ recordNo ? '追加' : '生成' }}
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
import {renderMarkdown} from '@/utils/markdown';
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

const SCENE_CONFIG = {
    case: {
        title: 'AI 生成测试用例',
        entityName: '需求',
    },
    apiCase: {
        title: 'AI 生成接口用例',
        entityName: '接口',
    },
};

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

const sceneConfig = computed(() => SCENE_CONFIG[props.scene]);

const visible = ref(false);
const generating = ref(false);
const adopting = ref(false);
const sessionsLoading = ref(false);
const sessions = ref<any[]>([]);
const recordNo = ref('');
const rounds = ref<any[]>([]);
const currentExpired = ref(false);
const streamText = ref('');
const chatBodyRef = ref<HTMLElement>();
let abortController: AbortController | null = null;
let pollTimer: ReturnType<typeof setInterval> | null = null;

const form = reactive<any>({
    instruction: '',
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

function open() {
    visible.value = true;
    recordNo.value = '';
    rounds.value = [];
    currentExpired.value = false;
    form.instruction = '';
    loadSessions();
}

function startNewSession() {
    abortController?.abort();
    stopPolling();
    generating.value = false;
    recordNo.value = '';
    rounds.value = [];
    currentExpired.value = false;
    streamText.value = '';
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
        _showRaw: false,
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
    Message.success('会话已删除');
}

// ==================== 会话列表展示 ====================

function sessionTitle(s: any) {
    const roundsArr = parseRounds(s.outputSnapshot);
    const first = roundsArr.find((r: any) => r.instruction);
    const text = first?.instruction || s.inputSummary || '未命名会话';
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
        {label: '今天', items: [] as any[]},
        {label: '昨天', items: [] as any[]},
        {label: '7 天内', items: [] as any[]},
        {label: '更早', items: [] as any[]},
    ];
    for (const s of sessions.value) {
        const t = s.createTime ? new Date(s.createTime).getTime() : 0;
        if (t >= startOfDay) groups[0].items.push(s);
        else if (t >= startOfDay - 86400000) groups[1].items.push(s);
        else if (t >= startOfDay - 7 * 86400000) groups[2].items.push(s);
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

function formatTime(t: any) {
    if (!t) return '';
    const d = new Date(t);
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
    streamText.value = '';
    const stale = () => abortController !== ctrl;
    return {
        signal: ctrl.signal,
        callbacks: {
            onDelta: (chunk: string) => {
                if (stale()) return;
                streamText.value += chunk;
                if (targetRound) {
                    targetRound.rawText = (targetRound.rawText || '') + chunk;
                }
                scrollToBottom();
            },
            onResult: (no: string, drafts: any[]) => {
                if (stale()) return;
                recordNo.value = no;
                generating.value = false;
                streamText.value = '';
                form.instruction = '';
                // 以服务端为准刷新整个会话（rawText/draftId 完整）
                refreshCurrentSession(no);
                loadSessions();
                Message.success(`生成 ${drafts.length} 条草稿`);
            },
            onError: (msg: string) => {
                if (stale()) return;
                generating.value = false;
                streamText.value = '';
                if (msg === '__STOPPED__') {
                    if (targetRound) targetRound.status = 'STOPPED';
                    if (recordNo.value) refreshCurrentSession(recordNo.value);
                    Message.info('已停止，保留已生成内容');
                } else {
                    // 本地轮次标记失败，首轮失败也能就地看到失败卡片并重试，
                    // 不必关弹窗重进后再从会话列表找回
                    if (targetRound) {
                        targetRound.status = 'FAILED';
                        targetRound.error = msg || '生成失败';
                    }
                    if (recordNo.value) refreshCurrentSession(recordNo.value);
                    loadSessions();
                    Message.error(msg || '生成失败');
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
    streamText.value = '';
    if (no) {
        try {
            await api.value.stop(no);
        } finally {
            refreshCurrentSession(no);
        }
    }
    Message.info('已停止，保留已生成内容');
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

/** 引用文档标题去重拼接：依据《XX》《YY》 */
function citeTitles(round: any) {
    const titles = [...new Set<string>((round.citations || []).map((c: any) => c.title))];
    return titles.map(t => `《${t}》`).join('');
}

/** uncertainties 引导：回填「补充说明：」前缀并聚焦输入框，用户补完按追加发送 */
function fillSupplement() {
    form.instruction = '补充说明：';
    nextTick(() => {
        const el = document.querySelector<HTMLElement>('.chat-input textarea');
        el?.focus();
    });
}

// ==================== 入库 ====================

function isLatest(idx: number) {
    return idx === rounds.value.length - 1;
}

function checkedCount(round: any) {
    return round.drafts.filter((d: any) => d._checked && !d._adopted).length;
}

function adoptableCount(round: any) {
    return round.drafts.filter((d: any) => !d._adopted).length;
}

function allChecked(round: any) {
    return adoptableCount(round) > 0 && checkedCount(round) === adoptableCount(round);
}

function toggleAll(round: any) {
    const target = !allChecked(round);
    round.drafts.forEach((d: any) => { if (!d._adopted) d._checked = target; });
}

async function handleAdopt(round: any) {
    const items = round.drafts
        .filter((d: any) => d._checked && !d._adopted)
        .map(({_checked, _adopted, _showRaw, ...rest}: any) => rest);
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
        let msg = res.msg || '入库成功';
        if (data.duplicateNames?.length) {
            msg += `；注意：${data.duplicateNames.length} 条与已存在用例同名`;
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

function scrollToBottom() {
    nextTick(() => {
        if (chatBodyRef.value) {
            chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight;
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

.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 4px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.round {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.msg {
  display: flex;
}

.user-msg {
  justify-content: flex-end;
}

.user-msg .bubble {
  background: rgb(var(--primary-6));
  color: #fff;
  border-radius: 12px 12px 2px 12px;
  padding: 8px 12px;
  max-width: 70%;
  white-space: pre-wrap;
}

.ai-msg {
  justify-content: flex-start;
}

.ai-bubble {
  background: var(--color-fill-1);
  border-radius: 12px 12px 12px 2px;
  padding: 8px 12px;
  max-width: 100%;
  width: 100%;
}

.round-tag {
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 6px;
}

.stopped-tag {
  color: rgb(var(--orange-6));
}

.stream-text {
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
  max-height: 260px;
  overflow-y: auto;
  line-height: 1.6;
}

.cursor {
  animation: blink 1s step-end infinite;
  color: rgb(var(--primary-6));
}

/* markdown 渲染内容（问答轮） */
.md-body {
  line-height: 1.6;
}

.md-body :deep(p) {
  margin: 0 0 4px;
}

.md-body :deep(p:last-child) {
  margin-bottom: 0;
}

.md-body :deep(ul),
.md-body :deep(ol) {
  margin: 2px 0 4px;
  padding-left: 20px;
}

.md-body :deep(li) {
  margin: 0;
}

/* 松散列表（列表项间有空行）会被渲染成 li>p，去掉段落间距避免每行空一行 */
.md-body :deep(li > p) {
  margin: 0;
}

.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3),
.md-body :deep(h4) {
  margin: 8px 0 4px;
  font-size: 14px;
}

.md-body :deep(code) {
  background: var(--color-fill-3);
  border-radius: 3px;
  padding: 1px 5px;
  font-size: 12px;
}

.md-body :deep(pre) {
  background: var(--color-fill-2);
  border-radius: 6px;
  padding: 8px 10px;
  overflow-x: auto;
  margin: 6px 0;
}

.md-body :deep(pre code) {
  background: none;
  padding: 0;
}

.md-body :deep(blockquote) {
  border-left: 3px solid var(--color-border-3);
  margin: 6px 0;
  padding: 2px 10px;
  color: var(--color-text-3);
}

.md-body :deep(table) {
  border-collapse: collapse;
  margin: 6px 0;
  font-size: 12px;
}

.md-body :deep(th),
.md-body :deep(td) {
  border: 1px solid var(--color-border-2);
  padding: 4px 8px;
}

.md-body :deep(a) {
  color: rgb(var(--primary-6));
}

@keyframes blink {
  50% { opacity: 0; }
}

.raw-toggle {
  font-size: 12px;
  margin-top: 4px;
  display: inline-block;
}

.round-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
  align-items: center;
}

.error-bubble {
  border: 1px solid rgb(var(--red-3));
  background: rgb(var(--red-1));
}

.error-text {
  color: rgb(var(--red-6));
  font-size: 13px;
  white-space: pre-wrap;
  word-break: break-all;
}

.unc-banner {
  margin-top: 6px;
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

.cite-banner {
  margin-top: 6px;
  padding: 6px 10px;
  border: 1px solid rgb(var(--primary-3));
  background: rgb(var(--primary-1));
  border-radius: 6px;
  font-size: 12px;
}

.cite-title {
  color: rgb(var(--primary-7));
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  user-select: none;
}

.cite-list {
  margin: 6px 0 2px;
  padding-left: 18px;
  color: var(--color-text-3);
  line-height: 1.7;
}

.cite-doc {
  color: rgb(var(--primary-6));
  margin-right: 4px;
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
