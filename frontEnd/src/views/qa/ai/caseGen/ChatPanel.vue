<template>
  <div class="chat-panel">
    <div ref="chatBodyRef" class="chat-body" @scroll="onScroll">
      <div class="chat-inner">
        <div v-for="(msg, idx) in messages" :key="msg._uid ?? idx" class="msg" :class="msg.role">
          <!-- 用户消息 -->
          <template v-if="msg.role === 'user'">
            <div class="bubble user-bubble">{{ msg.content }}</div>
            <a-avatar :size="32" class="msg-avatar user-avatar">{{ $t('qa.ai.chat.userAvatar') }}</a-avatar>
          </template>
          <!-- AI 消息 -->
          <template v-else-if="msg.role === 'assistant'">
            <a-avatar :size="32" class="msg-avatar ai-avatar">AI</a-avatar>
            <div class="msg-content">
              <!-- 思考过程 -->
              <div v-if="msg._hasThinking" class="thinking-section" :class="{ 'thinking-collapsed': msg._thinkingCollapsed }">
                <div class="thinking-header" @click="msg._thinkingCollapsed = !msg._thinkingCollapsed">
                  <span v-if="msg._thinkingCollapsed" class="thinking-label">{{ $t('qa.ai.chat.thinkingDone', { duration: msg._thinkingDuration }) }}</span>
                  <span v-else class="thinking-label"><span class="thinking-spinner"></span>{{ $t('qa.ai.chat.thinking') }}</span>
                  <span class="thinking-toggle">{{ msg._thinkingCollapsed ? $t('qa.ai.common.expand') : $t('qa.ai.common.collapse') }}</span>
                </div>
                <div v-show="!msg._thinkingCollapsed" class="thinking-body">{{ msg.thinking }}<span v-if="!msg._done" class="typing-cursor">▋</span></div>
              </div>
              <!-- 生成进度时间线 -->
              <div v-if="msg._genStage" class="gen-progress">
                <div class="gen-progress-header">
                  <span class="gen-progress-dot"></span>
                  <span class="gen-progress-title">{{ $t('qa.ai.caseGen.aiGenerating') }}</span>
                  <span class="gen-progress-count">{{ msg.content }}</span>
                </div>
                <div class="gen-timeline">
                  <div v-for="s in GEN_STAGES" :key="s" class="gen-step" :class="stepClass(msg._genStage, s)">
                    <span class="step-dot"></span> {{ stageLabel(s) }}
                    <span v-if="stageDuration(msg, s)" class="step-duration">{{ stageDuration(msg, s) }}</span>
                  </div>
                </div>
                <div class="gen-bar"><div class="gen-bar-fill" :style="{ width: progressWidth(msg._genStage) }"></div></div>
              </div>
              <!-- 正文（轻量文字 + QA MarkdownRender） -->
              <div class="bubble ai-bubble" :class="{ 'bubble-clickable': msg._doneAction }" v-if="msg.content || msg._isQa" @click="msg._doneAction && $emit('done-action', msg._doneAction)">
                <MarkdownRender
                  v-if="msg._isQa || (msg._done && !msg._genStage)"
                  mode="chat"
                  :content="msg.content || ''"
                  :final="!!msg._done"
                  :fade="false"
                  custom-id="case-gen"
                  :custom-html-tags="['tool-status']"
                  @copy-code="handleCopyCode"
                />
                <template v-else>{{ msg.content }}</template>
              </div>
              <!-- 原始输出兜底 -->
              <div v-if="msg._showRawText && msg._rawText" class="raw-text-section">
                <div class="raw-text-header" @click="msg._rawTextCollapsed = !msg._rawTextCollapsed">
                  <span class="raw-text-label">{{ $t('qa.ai.chat.rawOutputLabel') }}</span>
                  <span class="raw-text-toggle">{{ msg._rawTextCollapsed ? $t('qa.ai.common.expand') : $t('qa.ai.common.collapse') }}</span>
                </div>
                <div v-show="!msg._rawTextCollapsed" class="raw-text-body-wrapper">
                  <pre class="raw-text-body">{{ msg._rawText }}</pre>
                  <a-button size="mini" type="text" @click="copyRawText(msg._rawText)">{{ $t('qa.ai.chat.copy') }}</a-button>
                </div>
              </div>
              <!-- 继续生成按钮 -->
              <div v-if="!generating && idx === messages.length - 1 && msg._done && msg._stopped" class="msg-actions">
                <a-button size="mini" type="outline" @click="$emit('continue')">{{ $t('qa.ai.chat.continue') }}</a-button>
              </div>
            </div>
          </template>
        </div>
        <!-- 空状态 -->
        <div v-if="!messages.length" class="empty-hint">
          <p>{{ $t('qa.ai.caseGen.emptyHint1') }}</p>
          <p>{{ $t('qa.ai.caseGen.emptyHint2') }}</p>
          <p>{{ $t('qa.ai.caseGen.emptyHint3') }}</p>
          <p>{{ $t('qa.ai.caseGen.emptyHint4') }}</p>
        </div>
        <div v-if="!isAtBottom && messages.length" class="scroll-bottom-btn" @click="forceScrollToBottom"><IconArrowDown /></div>
      </div>
    </div>
    <!-- 输入区 -->
    <div class="chat-input">
      <a-textarea
        v-model="inputModel"
        :placeholder="props.converged ? $t('qa.ai.caseGen.inputPlaceholderConverged') : $t('qa.ai.caseGen.inputPlaceholder')"
        :auto-size="{ minRows: 1, maxRows: 4 }"
        :disabled="generating"
        @keydown.enter.exact.prevent="onSend"
      />
      <a-button v-if="!generating" type="primary" @click="onSend" :disabled="!inputModel.trim()">{{ $t('qa.ai.common.send') }}</a-button>
      <a-button v-else status="danger" @click="$emit('stop')">{{ $t('qa.ai.common.stop') }}</a-button>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'ChatPanel' };
</script>

<script setup lang="ts">
import { ref, nextTick } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { IconArrowDown } from '@arco-design/web-vue/es/icon';
import MarkdownRender, { setCustomComponents } from 'markstream-vue';
import 'markstream-vue/index.css';
import ToolStatusNode from '../ToolStatusNode.vue';

setCustomComponents('case-gen', { 'tool-status': ToolStatusNode });

const { t } = useI18n();

const props = defineProps<{
  messages: any[];
  generating: boolean;
  converged?: boolean;
}>();
const emit = defineEmits(['send', 'stop', 'continue', 'done-action']);

const inputModel = ref('');
const chatBodyRef = ref<HTMLElement>();
const isAtBottom = ref(true);

const GEN_STAGES = ['context', 'gen', 'parse', 'audit'] as const;

const STAGE_LABEL_KEYS: Record<string, string> = {
  context: 'qa.ai.caseGen.stage.context',
  gen: 'qa.ai.caseGen.stage.gen',
  parse: 'qa.ai.caseGen.stage.parse',
  audit: 'qa.ai.caseGen.stage.audit',
};

function stageLabel(stage: string) {
  const key = STAGE_LABEL_KEYS[stage];
  return key ? t(key) : stage;
}

function stepClass(curStage: string, step: string) {
  const curIdx = GEN_STAGES.indexOf(curStage as any);
  const stepIdx = GEN_STAGES.indexOf(step as any);
  if (stepIdx < curIdx) return 'done';
  if (stepIdx === curIdx) return 'active';
  return 'pending';
}

function progressWidth(stage: string) {
  const idx = GEN_STAGES.indexOf(stage as any);
  if (idx < 0) return '100%';
  return `${20 + idx * 26}%`;
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`;
  return `${Math.floor(ms / 60000)}m${Math.floor((ms % 60000) / 1000)}s`;
}

function stageDuration(msg: any, stage: string): string {
  if (!msg?._stageTimings) return '';
  const t = msg._stageTimings;
  const order = ['context', 'gen', 'parse', 'dedup'];
  const idx = order.indexOf(stage);
  const startTs = t[stage];
  const endTs = idx < order.length - 1 ? t[order[idx + 1]] : t._done;
  if (!startTs || !endTs) return '';
  return formatDuration(endTs - startTs);
}

function onScroll() {
  if (!chatBodyRef.value) return;
  const el = chatBodyRef.value;
  isAtBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 50;
}

function scrollToBottom() {
  if (!isAtBottom.value) return;
  nextTick(() => {
    if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight;
  });
}

function forceScrollToBottom() {
  isAtBottom.value = true;
  nextTick(() => {
    if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight;
  });
}

function onSend() {
  if (!inputModel.value.trim() || props.generating) return;
  const text = inputModel.value.trim();
  inputModel.value = '';
  emit('send', text);
}

function handleCopyCode(text: string) {
  navigator.clipboard.writeText(text).then(() => { Message.success(t('qa.ai.common.copied')); });
}

function copyRawText(text: string) {
  navigator.clipboard.writeText(text).then(() => { Message.success(t('qa.ai.chat.rawCopied')); });
}

defineExpose({ scrollToBottom, forceScrollToBottom, isAtBottom });
</script>

<style scoped>
.chat-panel { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.chat-body { flex: 1; overflow-y: auto; padding: 16px; }
.chat-inner { max-width: 100%; margin: 0 auto; }
.msg { margin-bottom: 12px; display: flex; gap: 8px; align-items: flex-start; }
.msg.user { justify-content: flex-end; }
.msg-content { flex: 1; min-width: 0; }
.msg-actions { margin-top: 4px; }
.msg-avatar { flex-shrink: 0; font-size: 12px; font-weight: 600; }
.ai-avatar { background: rgb(var(--primary-6)); }
.user-avatar { background: rgb(var(--success-6)); }
.bubble { display: inline-block; max-width: calc(100% - 40px); padding: 8px 12px; border-radius: 8px; font-size: 13px; line-height: 1.6; text-align: left; word-break: break-word; }
.user-bubble { background: rgb(var(--primary-6)); color: #fff; }
.ai-bubble { display: block; max-width: 100%; padding: 12px 16px; background: var(--color-bg-1); border: 1px solid var(--color-border-2); border-radius: 8px; color: var(--color-text-1); }
.ai-bubble.bubble-clickable { cursor: pointer; }
.ai-bubble.bubble-clickable:hover { border-color: rgb(var(--primary-5)); }
.empty-hint { padding: 60px 0; text-align: center; color: var(--color-text-3); font-size: 13px; line-height: 2; }
.scroll-bottom-btn { position: sticky; bottom: 12px; left: 50%; transform: translateX(-50%); width: 32px; height: 32px; border-radius: 50%; background: var(--color-bg-1); border: 1px solid var(--color-border-2); box-shadow: 0 2px 8px rgba(0,0,0,0.12); display: flex; align-items: center; justify-content: center; cursor: pointer; z-index: 10; }
.chat-input { display: flex; gap: 8px; padding: 12px; flex-shrink: 0; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.typing-cursor { display: inline-block; font-weight: bold; animation: blink 1s step-end infinite; }
@keyframes blink { 0%, 50% { opacity: 1; } 50%, 100% { opacity: 0; } }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
.thinking-section { margin-bottom: 8px; border: 1px solid var(--color-fill-3); border-radius: 6px; overflow: hidden; background: var(--color-fill-1); }
.thinking-header { display: flex; justify-content: space-between; align-items: center; padding: 6px 10px; cursor: pointer; font-size: 12px; color: var(--color-text-3); user-select: none; }
.thinking-header:hover { background: var(--color-fill-2); }
.thinking-label { display: flex; align-items: center; gap: 4px; }
.thinking-spinner { display: inline-block; width: 12px; height: 12px; border: 2px solid var(--color-text-4); border-top-color: transparent; border-radius: 50%; animation: spin 0.8s linear infinite; }
.thinking-toggle { font-size: 11px; color: var(--color-text-4); }
.thinking-body { padding: 8px 12px; font-size: 12px; line-height: 1.6; color: var(--color-text-3); white-space: pre-wrap; word-break: break-word; max-height: 300px; overflow-y: auto; }
.thinking-collapsed .thinking-header { opacity: 0.7; }
.gen-progress { border: 1px solid var(--color-border-2); border-radius: 8px; padding: 12px 14px; margin-bottom: 8px; background: var(--color-bg-1); }
.gen-progress-header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.gen-progress-dot { width: 8px; height: 8px; border-radius: 50%; background: rgb(var(--primary-6)); animation: gen-pulse 1.5s ease-in-out infinite; }
@keyframes gen-pulse { 0%, 100% { opacity: 1; } 50% { opacity: .3; } }
.gen-progress-title { font-size: 13px; font-weight: 500; }
.gen-progress-count { font-size: 12px; color: rgb(var(--primary-6)); font-weight: 600; margin-left: auto; }
.gen-timeline { display: flex; flex-direction: column; gap: 6px; }
.gen-step { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--color-text-4); }
.gen-step .step-dot { width: 6px; height: 6px; border-radius: 50%; flex-shrink: 0; background: var(--color-fill-4); }
.gen-step.done { color: var(--color-text-2); }
.gen-step.done .step-dot { background: rgb(var(--success-6)); }
.gen-step.active { color: rgb(var(--primary-6)); font-weight: 500; }
.gen-step.active .step-dot { background: rgb(var(--primary-6)); animation: gen-pulse 1s infinite; }
.step-duration { font-size: 11px; color: var(--color-text-4); margin-left: auto; }
.gen-step.done .step-duration { color: var(--color-text-3); }
.gen-step.active .step-duration { color: rgb(var(--primary-6)); }
.gen-bar { height: 4px; background: var(--color-fill-3); border-radius: 2px; margin-top: 10px; overflow: hidden; }
.gen-bar-fill { height: 100%; background: rgb(var(--primary-6)); border-radius: 2px; transition: width .5s ease; }
.raw-text-section { margin-top: 8px; border: 1px solid var(--color-border-2); border-radius: 6px; overflow: hidden; background: var(--color-fill-1); }
.raw-text-header { display: flex; justify-content: space-between; align-items: center; padding: 6px 10px; cursor: pointer; font-size: 12px; color: var(--color-text-3); user-select: none; }
.raw-text-header:hover { background: var(--color-fill-2); }
.raw-text-label { font-weight: 500; }
.raw-text-toggle { font-size: 11px; color: var(--color-text-4); }
.raw-text-body-wrapper { border-top: 1px solid var(--color-border-2); padding: 8px 12px; }
.raw-text-body { font-size: 12px; line-height: 1.5; color: var(--color-text-3); white-space: pre-wrap; word-break: break-all; max-height: 300px; overflow-y: auto; margin: 0 0 8px 0; font-family: 'SF Mono', 'Fira Code', monospace; }
</style>
