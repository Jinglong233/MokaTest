<template>
  <div class="workbench-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.ai', 'menu.qa.aiWorkbench']" />
    <div class="wb-layout">
      <!-- 左栏：会话列表 -->
      <div class="wb-sessions">
        <a-button type="primary" long size="small" @click="newSession">
          <template #icon><IconPlus /></template>
          {{ $t('qa.ai.workbench.newSession') }}
        </a-button>
        <a-scrollbar outer-style="flex: 1; min-height: 0; overflow: hidden" style="height: 100%; overflow: auto;">
          <a-spin :loading="sessionsLoading" style="width: 100%; min-height: 100%">
            <div class="session-list">
            <div
              v-for="s in sessions"
              :key="s.id"
              class="session-item"
              :class="{ active: s.id === currentSessionId }"
              @click="selectSession(s)"
            >
              <div class="session-title">{{ s.title || $t('qa.ai.workbench.newSession') }}</div>
              <div class="session-meta">{{ s.updateTime?.slice(0, 16) }}</div>
              <a-popconfirm :content="$t('qa.ai.workbench.deleteSessionConfirm')" @ok="deleteSession(s)" position="tr">
                <IconDelete class="session-delete" @click.stop />
              </a-popconfirm>
            </div>
            <a-empty v-if="!sessions.length" :description="$t('qa.ai.workbench.noSessions')" :image-size="50" />
          </div>
          </a-spin>
        </a-scrollbar>
      </div>

      <!-- 右栏：对话区 -->
      <div class="wb-chat">
        <AiChatPanel
          ref="chatPanelRef"
          :messages="messages"
          :generating="generating"
          :isAtBottom="isAtBottom"
          :suggestions="suggestions"
          :skills="customSkills"
          @scroll="onScrollChange"
          @regenerate="regenerate"
          @retry="retry"
          @continue="continueGeneration"
          @suggestion="useSuggestion"
        />
        <!-- P1-2 绑定会话常驻横幅 -->
        <div v-if="skillBound" class="skill-banner">
          <span class="skill-banner-text">{{ $t('qa.ai.workbench.boundSkill', { name: boundSkillName }) }}</span>
        </div>
        <!-- 输入区 -->
        <div class="chat-input">
          <a-tooltip :content="$t('qa.ai.workbench.manageSkills')" position="tl">
            <a-button size="small" @click="goToSkills" style="flex-shrink: 0">
              <template #icon><IconSettings /></template>
            </a-button>
          </a-tooltip>
          <a-select
            v-model="selectedSkillScene"
            :placeholder="$t('qa.ai.workbench.skillPlaceholder')"
            size="small"
            style="width: 140px; flex-shrink: 0"
            allow-clear
            :disabled="skillBound || !customSkills.length"
            :title="customSkills.length ? '' : $t('qa.ai.workbench.noSkillsTip')"
          >
            <a-option v-for="s in customSkills" :key="s.scene" :value="s.scene">{{ s.name }}</a-option>
          </a-select>
          <a-tag v-if="skillBound" size="small" color="arcoblue" style="flex-shrink: 0">
            {{ boundSkillName }}
          </a-tag>
          <a-textarea
            v-model="inputText"
            :placeholder="skillBound ? $t('qa.ai.workbench.inputPlaceholderBound', { name: boundSkillName }) : selectedSkillScene ? $t('qa.ai.workbench.inputPlaceholderSkill') : $t('qa.ai.workbench.inputPlaceholder')"
            :auto-size="{ minRows: 1, maxRows: 4 }"
            :disabled="generating"
            @keydown.enter.exact.prevent="sendMessage"
          />
          <a-button v-if="!generating" type="primary" @click="sendMessage" :disabled="!inputText.trim()">
            <template #icon><IconSend /></template>
          </a-button>
          <a-button v-else status="danger" @click="stopGenerating">
            <template #icon><IconStop /></template>
            {{ $t('qa.ai.common.stop') }}
          </a-button>
        </div>
      </div>
    </div>

    <!-- AI 工作台全部操作在卡片内闭环，不再使用抽屉 -->
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  export default { name: 'AiWorkbench' };
</script>

<script setup lang="ts">
import { ref, reactive, computed, nextTick, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { IconPlus, IconDelete, IconSend, IconStop, IconSettings } from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import AiChatPanel from './AiChatPanel.vue';
import { useProjectStore } from '@/store';
import { createAgentSession, listAgentSessions, deleteAgentSession, getAgentMessages, streamAgentChat, saveCardMessage, stopAgentChat, getStreamStatus, resumeStreamChat } from '@/api/MyApi/aiAgent';
import { listSkillDefinitions } from '@/api/MyApi/ai';

const router = useRouter();
const { t } = useI18n();

function goToSkills() {
  router.push({ name: 'AiSkills' });
}

const projectStore = useProjectStore();
const chatPanelRef = ref<InstanceType<typeof AiChatPanel>>();

const sessions = ref<any[]>([]);
const sessionsLoading = ref(false);
const currentSessionId = ref<number | null>(null);
const messages = ref<any[]>([]);
const inputText = ref('');
const generating = ref(false);
const isAtBottom = ref(true);
let abortCtrl: AbortController | null = null;
// RAG 引用缓冲：onRetrieved 在首个 delta 之前触发，此时 assistant 消息还没建，先暂存再挂载
let pendingCitations: string[] | null = null;

// 消息稳定 key：index key 在中间插入工具卡片时会导致 Vue 复用错 DOM（v-html 渲染错乱）
let msgUid = 0;
function pushMsg(msg: any) {
  msg._uid = ++msgUid;
  messages.value.push(msg);
}

/** 把缓冲的引用挂到消息上（无缓冲则不动） */
function attachPendingCitations(msg: any) {
  if (msg && pendingCitations?.length) {
    msg.citations = pendingCitations;
    pendingCitations = null;
  }
}

// rAF 节流的 delta 追加：每帧最多追加一次，避免高频 delta 导致 markdown 重复解析卡顿
let pendingDelta = '';
let deltaRaf: number | null = null;

/** 同步刷新 pendingDelta 到消息（onDone/onError/onStopped 前必须调用，否则末尾 token 丢失） */
function flushPendingDelta() {
  if (deltaRaf !== null) { cancelAnimationFrame(deltaRaf); deltaRaf = null; }
  if (!pendingDelta) return;
  const last = messages.value[messages.value.length - 1];
  if (last && last.role === 'assistant' && !last._done) {
    last.content += pendingDelta;
  }
  pendingDelta = '';
}

function appendDelta(t: string, withCitations = false) {
  pendingDelta += t;
  if (deltaRaf !== null) return;
  deltaRaf = requestAnimationFrame(() => {
    deltaRaf = null;
    const last = messages.value[messages.value.length - 1];
    if (last && last.role === 'assistant' && !last._done) {
      last.content += pendingDelta;
      if (withCitations) attachPendingCitations(last);
    } else {
      const msg = { role: 'assistant', content: pendingDelta, _done: false };
      pushMsg(msg);
      if (withCitations) attachPendingCitations(msg);
    }
    pendingDelta = '';
    scrollToBottom();
  });
}

// 自定义 Skill 选择
const customSkills = ref<any[]>([]);     // 启用中的指令（选择器用）
const allSkills = ref<any[]>([]);           // 全量（含禁用，名称 fallback 用）
const selectedSkillScene = ref<string>('');

// §7.2 会话级绑定：已绑定的 skillScene（从 session 恢复）
const boundSkillScene = ref<string>('');
const skillBound = computed(() => !!boundSkillScene.value);
const boundSkillName = computed(() => {
  // P1-2 fallback：启用列表 → 全量列表 → scene 原值
  const s = customSkills.value.find((sk: any) => sk.scene === boundSkillScene.value)
    || allSkills.value.find((sk: any) => sk.scene === boundSkillScene.value);
  return s?.name || boundSkillScene.value;
});

const suggestions = computed(() => [
  t('qa.ai.workbench.suggestion1'),
  t('qa.ai.workbench.suggestion2'),
  t('qa.ai.workbench.suggestion3'),
  t('qa.ai.workbench.suggestion4'),
  t('qa.ai.workbench.suggestion5'),
  t('qa.ai.workbench.suggestion6'),
]);

// ==================== 会话管理 ====================
function useSuggestion(text: string) {
  inputText.value = text;
}

async function loadSessions() {
  sessionsLoading.value = true;
  try {
    const res: any = await listAgentSessions();
    sessions.value = res.data || [];
  } catch { /* ignore */ } finally {
    sessionsLoading.value = false;
  }
}

async function newSession() {
  try {
    const res: any = await createAgentSession();
    sessions.value.unshift(res.data);
    selectSession(res.data);
  } catch {
    Message.error(t('qa.ai.workbench.createSessionFailed'));
  }
}

async function selectSession(s: any) {
  // 中断任何进行中的 SSE
  if (abortCtrl) { abortCtrl.abort(); abortCtrl = null; }
  // 清理 rAF pending delta，防止回调在会话切换后追加内容到新消息
  if (deltaRaf !== null) { cancelAnimationFrame(deltaRaf); deltaRaf = null; }
  pendingDelta = '';
  generating.value = false;

  currentSessionId.value = s.id;
  messages.value = [];
  boundSkillScene.value = '';
  selectedSkillScene.value = '';
  // §3.7② 会话能力快照：优先从 session 恢复绑定状态
  if (s.skillScene) {
    boundSkillScene.value = s.skillScene;
    selectedSkillScene.value = s.skillScene;
  }
  try {
    const res: any = await getAgentMessages(s.id);
    const list = res.data || [];
    // 兼容旧数据：session 无快照时从消息历史恢复
    if (!boundSkillScene.value) {
      const boundMsg = list.find((m: any) => m.role === 'user' && m.skillScene);
      if (boundMsg) {
        boundSkillScene.value = boundMsg.skillScene;
        selectedSkillScene.value = boundMsg.skillScene;
      }
    }
    messages.value = list.map((m: any) => {
      // 卡片消息从 content JSON 恢复
      if (m.role === 'card') {
        try {
          const parsed = JSON.parse(m.content);
          return { role: 'card', cardType: parsed.cardType, data: parsed.data, _done: true, _uid: ++msgUid };
        } catch { return { role: 'card', cardType: 'unknown', data: {}, _done: true, _uid: ++msgUid }; }
      }
      return {
        role: m.role,
        content: m.content || '',
        skillScene: m.skillScene,
        toolName: m.toolName,
        toolResult: m.toolResult,
        toolArgs: m.toolArgs,
        error: m.toolResult?.startsWith('{"error"') || (m.toolResult?.startsWith('工具 ') && m.toolResult?.includes('本次调用失败')) || m.toolResult === '工具不存在' || m.toolResult === '无权调用',
        _error: !!m.errorMsg, _errorMsg: m.errorMsg,
        _done: true, _pending: false, _collapsed: true, _uid: ++msgUid,
      };
    });
    // 旧版卡片兼容：旧数据可能有 drafts/genStreaming 等字段，CasegenEntryCard 会自动忽略
    forceScrollToBottom();
    // 检查是否有进行中的流式回复（断线恢复）
    try {
      const statusRes: any = await getStreamStatus(s.id);
      if (statusRes.data?.active) {
        // 有进行中的回复：创建 assistant 占位消息，建立恢复 SSE 连接
        pushMsg({ role: 'assistant', content: '', _done: false, _uid: ++msgUid });
        generating.value = true;
        resumeStream(s.id);
        return;
      }
    } catch { /* ignore */ }
  } catch { /* ignore */ }
}

async function deleteSession(s: any) {
  try {
    await deleteAgentSession(s.id);
    sessions.value = sessions.value.filter((x: any) => x.id !== s.id);
    if (currentSessionId.value === s.id) {
      currentSessionId.value = null;
      messages.value = [];
    }
  } catch {
    Message.error(t('qa.ai.workbench.deleteFailed'));
  }
}

// ==================== 消息发送 ====================
async function sendMessage() {
  if (!inputText.value.trim() || generating.value) return;
  // 无会话时自动创建（输入即开始，无需先点「新会话」）
  if (!currentSessionId.value) {
    try {
      const res: any = await createAgentSession();
      sessions.value.unshift(res.data);
      currentSessionId.value = res.data.id;
      messages.value = [];
      boundSkillScene.value = '';
    } catch {
      Message.error(t('qa.ai.workbench.createSessionFailed'));
      return;
    }
  }
  const text = inputText.value.trim();
  inputText.value = '';
  // §3.5 首次发送带 skill → 绑定会话；已绑定 → 后端校验一致性
  let scene = selectedSkillScene.value || undefined;
  if (scene && !skillBound.value) {
    boundSkillScene.value = scene; // 首次绑定
  }
  if (skillBound.value) {
    scene = boundSkillScene.value || undefined; // 已绑定：用绑定的值
  }
  pushMsg({ role: 'user', content: text, skillScene: scene });
  // 统一走 Agent 通道（快捷指令通过 skillScene 参数后端注入 system prompt）
  startStream(text, scene);
}

// ==================== Agent 模式（普通对话） ====================
function startStream(message: string, skillScene?: string, retry = false) {
  if (!currentSessionId.value) return;
  const streamSessionId = currentSessionId.value; // 闭包捕获：回调时校验会话是否已切换
  generating.value = true;
  abortCtrl = new AbortController();
  streamAgentChat(
    { sessionId: streamSessionId, message, skillScene, retry: retry || undefined },
    {
      onDelta: (t) => appendDelta(t, true),
      onToolStart: () => {
        scrollToBottom();
      },
      onToolEnd: (data) => {
        // 工具状态嵌入 assistant content（markstream-vue 自定义标签渲染）
        appendDelta(`<tool-status name="${data.tool}" status="done" duration="${data.duration || ''}" error="${!!data.error}"></tool-status>\n\n`, true);
        // Agent 识别到生成意图 → 推入口卡片（状态橱窗+跳转入口，不包含生成逻辑）
        if (data.tool === 'start_case_generation' && data.result) {
          try {
            const summary = typeof data.result === 'string' ? JSON.parse(data.result) : data.result;
            if (summary.mode === 'casegen' && summary.requirementId) {
              if (messages.value.some((m: any) => m.cardType === 'casegen-workflow')) {
                scrollToBottom();
                return;
              }
              const wfCard = reactive({
                role: 'card',
                cardType: 'casegen-workflow',
                data: { requirementId: summary.requirementId, reqCode: summary.reqCode || '', title: summary.title || '', caseCount: summary.caseCount ?? null },
                _done: true,
              });
              pushMsg(wfCard);
              if (currentSessionId.value) saveCardMessage(currentSessionId.value, 'casegen-workflow', wfCard.data).catch(() => {});
              forceScrollToBottom();
            }
          } catch { /* ignore */ }
        }
        // Agent 识别到评审意图 → 推评审入口卡片
        if (data.tool === 'start_requirement_review' && data.result) {
          try {
            const summary = typeof data.result === 'string' ? JSON.parse(data.result) : data.result;
            if (summary.mode === 'review' && summary.requirementId) {
              if (messages.value.some((m: any) => m.cardType === 'review-entry' && m.data?.requirementId === summary.requirementId)) {
                scrollToBottom();
                return;
              }
              const rvCard = reactive({
                role: 'card',
                cardType: 'review-entry',
                data: { requirementId: summary.requirementId, reqCode: summary.reqCode || '', title: summary.title || '', lastScore: summary.lastScore ?? null, lastTime: summary.lastTime || null, lastStatus: summary.lastStatus || null },
                _done: true,
              });
              pushMsg(rvCard);
              if (currentSessionId.value) saveCardMessage(currentSessionId.value, 'review-entry', rvCard.data).catch(() => {});
              forceScrollToBottom();
            }
          } catch { /* ignore */ }
        }
        scrollToBottom();
      },
      onCitations: (titles) => {
        const last = messages.value[messages.value.length - 1];
        if (last && last.role === 'assistant') {
          last.citations = titles;
        } else {
          pendingCitations = titles; // assistant 消息还没建，先缓冲
        }
      },
      onDone: () => {
        flushPendingDelta();
        generating.value = false;
        const last = messages.value[messages.value.length - 1];
        if (last && last.role === 'assistant') {
          last._done = true;
          attachPendingCitations(last);
        }
        pendingCitations = null;
        forceScrollToBottom();
        loadSessions();
      },
      onStopped: () => {
        // 后端确认停止（前端已乐观置停，这里兜底）
        flushPendingDelta();
        generating.value = false;
        pendingCitations = null;
      },
      onError: (msg) => {
        flushPendingDelta();
        generating.value = false;
        pendingCitations = null;
        // 会话隔离：流式中途切了会话则丢弃错误，不污染新会话视图
        if (streamSessionId !== currentSessionId.value) return;
        let last = messages.value[messages.value.length - 1];
        if (!last || last.role !== 'assistant') {
          last = { role: 'assistant', content: '' };
          pushMsg(last);
        }
        last._done = true;
        last._error = true;
        last._errorMsg = msg || t('qa.ai.workbench.generateFailed');
        Message.error(last._errorMsg);
      },
      onSkillInvalid: (data) => {
        // P2-1 模板不存在/禁用 → 移除绑定状态
        boundSkillScene.value = '';
        selectedSkillScene.value = '';
        Message.warning(data.msg || t('qa.ai.workbench.skillInvalid'));
      },
    },
    abortCtrl.signal,
  );
}

// ==================== 快捷指令（chip 模式，后端注入） ====================

// selectedSkillScene 选中后持续生效，用户可 allow-clear 移除

async function loadCustomSkills() {
  try {
    const res: any = await listSkillDefinitions();
    if (res.code === 200) {
      allSkills.value = res.data || [];
      customSkills.value = allSkills.value.filter((s: any) => s.enabled === 1);
    } else {
      console.warn('[快捷指令] 加载失败:', res.code, res.msg);
    }
  } catch (e: any) {
    console.warn('[快捷指令] 加载异常:', e?.message || e);
  }
}

// ==================== 停止/继续/重新生成 ====================

function stopGenerating() {
  // 先 flush 未刷新的 delta 到 content，再清理 rAF，确保已生成内容完整
  flushPendingDelta();
  // Agent/Skill 对话：先通知后端协作式取消（前端 abort 只断连接，后端 LLM 还在跑会烧 token）
  if (currentSessionId.value) stopAgentChat(currentSessionId.value).catch(() => {});
  if (abortCtrl) { abortCtrl.abort(); abortCtrl = null; }
  generating.value = false;
  const last = messages.value[messages.value.length - 1];
  if (last && last.role === 'assistant' && !last._done) {
    last._done = true;
    last._stopped = true;
    if (!last.content) last.content = t('qa.ai.workbench.stoppedContent');
  }
}

/** 断线恢复：建立恢复 SSE 连接，后端一次性回放 fullText（restore），后续 delta 正常追加 */
function resumeStream(sessionId: number) {
  abortCtrl = new AbortController();
  resumeStreamChat(
    { sessionId },
    {
      onRestore: (fullContent: string) => {
        // 后端一次性推送全量已累积文本，直接替换 content（不追加，避免重复）
        const last = messages.value[messages.value.length - 1];
        if (last && last.role === 'assistant' && !last._done) {
          last.content = fullContent;
        }
        // 清空 pendingDelta：恢复后的后续 delta 正常追加
        pendingDelta = '';
        if (deltaRaf !== null) { cancelAnimationFrame(deltaRaf); deltaRaf = null; }
        scrollToBottom();
      },
      onDelta: (t) => appendDelta(t, true),
      onDone: () => {
        flushPendingDelta();
        generating.value = false;
        const last = messages.value[messages.value.length - 1];
        if (last && last.role === 'assistant') last._done = true;
        forceScrollToBottom();
        loadSessions();
      },
      onError: (msg) => {
        flushPendingDelta();
        generating.value = false;
        if (sessionId !== currentSessionId.value) return;
        let last = messages.value[messages.value.length - 1];
        if (!last || last.role !== 'assistant') {
          last = { role: 'assistant', content: '' };
          pushMsg(last);
        }
        last._done = true;
        last._error = true;
        last._errorMsg = msg || t('qa.ai.workbench.resumeFailed');
        Message.error(last._errorMsg);
      },
    },
    abortCtrl.signal,
  );
}

function continueGeneration() {
  if (generating.value || !currentSessionId.value) return;
  const streamSessionId = currentSessionId.value;
  const last = messages.value[messages.value.length - 1];
  if (!last || last.role !== 'assistant' || !last._stopped) return;
  const partialContent = last.content;
  last._done = false;
  last._stopped = false;
  generating.value = true;
  abortCtrl = new AbortController();
  streamAgentChat(
    { sessionId: streamSessionId, message: '继续', partialContent },
    {
      onDelta: (t) => appendDelta(t, false),
      onToolStart: () => {
        scrollToBottom();
      },
      onToolEnd: (data) => {
        appendDelta(`<tool-status name="${data.tool}" status="done" duration="${data.duration || ''}" error="${!!data.error}"></tool-status>\n\n`, false);
        scrollToBottom();
      },
      onDone: () => {
        flushPendingDelta();
        generating.value = false;
        const t = messages.value[messages.value.length - 1];
        if (t && t.role === 'assistant') t._done = true;
        forceScrollToBottom();
      },
      onError: (msg) => {
        flushPendingDelta();
        generating.value = false;
        if (streamSessionId !== currentSessionId.value) return;
        let lastMsg = messages.value[messages.value.length - 1];
        if (!lastMsg || lastMsg.role !== 'assistant') {
          lastMsg = { role: 'assistant', content: '' };
          pushMsg(lastMsg);
        }
        lastMsg._done = true;
        lastMsg._error = true;
        lastMsg._errorMsg = msg || t('qa.ai.workbench.continueFailed');
        Message.error(lastMsg._errorMsg);
      },
    },
    abortCtrl.signal,
  );
}

function regenerate() {
  if (generating.value || !currentSessionId.value) return;
  let lastUserIdx = -1;
  for (let i = messages.value.length - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') { lastUserIdx = i; break; }
  }
  if (lastUserIdx < 0) return;
  const userMsg = messages.value[lastUserIdx];
  messages.value = messages.value.slice(0, lastUserIdx + 1);
  startStream(userMsg.content, userMsg.skillScene, true);
}

/** 重试：复用 regenerate 语义（截掉失败消息，后端取库里最后一条 user 重跑） */
function retry() {
  regenerate();
}

// ==================== 滚动 & 复制按钮 ====================
function onScrollChange(atBottom: boolean) {
  isAtBottom.value = atBottom;
}

function scrollToBottom() {
  if (!isAtBottom.value) return;
  nextTick(() => {
    const body = chatPanelRef.value?.getChatBody?.();
    if (body) body.scrollTop = body.scrollHeight;
  });
}

function forceScrollToBottom() {
  isAtBottom.value = true;
  nextTick(() => {
    const body = chatPanelRef.value?.getChatBody?.();
    if (body) body.scrollTop = body.scrollHeight;
  });
}


onMounted(() => {
  loadSessions();
  loadCustomSkills();
});
</script>

<style scoped>
.workbench-page {
  padding: 0 20px 20px;
  height: var(--page-container-height, calc(100vh - 60px));
  display: flex;
  flex-direction: column;
}
.wb-layout {
  display: flex; gap: 12px; flex: 1; min-height: 0; margin-top: 15px;
}
.wb-sessions {
  width: 260px; flex-shrink: 0;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  display: flex; flex-direction: column; overflow: hidden; padding: 12px;
}
.session-list { margin-top: 8px; padding: 0 8px 8px; }
.session-item { padding: 8px 12px; border-radius: 6px; cursor: pointer; position: relative; margin-bottom: 2px; }
.session-item:hover { background: var(--color-fill-2); }
.session-item.active { background: rgb(var(--primary-1)); }
.session-title { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; padding-right: 20px; }
.session-meta { font-size: 11px; color: var(--color-text-3); margin-top: 2px; }
.session-delete { position: absolute; right: 8px; top: 8px; opacity: 0; font-size: 12px; }
.session-item:hover .session-delete { opacity: 1; }
.wb-chat {
  flex: 1; display: flex; flex-direction: column; min-width: 0;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  overflow: hidden;
}
.chat-input {
  display: flex; gap: 8px; padding: 12px; flex-shrink: 0;
  border-top: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}
.skill-banner {
  padding: 6px 12px;
  background: var(--color-fill-1);
  border-top: 1px solid var(--color-border-2);
  font-size: 12px;
  color: var(--color-text-2);
  flex-shrink: 0;
}
</style>
