<template>
  <div class="point-chat">
    <div v-if="collapsible" class="pc-log-bar" :class="{ collapsed }" @click="$emit('toggle-collapse')">
      <template v-if="collapsed">
        <IconLoading v-if="generating" class="pc-ticker-spin" />
        <span class="pc-ticker-text">{{ ticker || lastSummary }}</span>
        <span class="pc-ticker-toggle">{{ $t('qa.ai.common.expand') }}</span>
      </template>
      <template v-else>
        <span class="pc-log-label">{{ $t('qa.ai.caseGen.processLog') }}</span>
        <span class="pc-ticker-toggle">{{ $t('qa.ai.common.collapse') }}</span>
      </template>
    </div>
    <div v-show="!collapsed" ref="bodyRef" class="pc-body" @scroll="onScroll">
      <div class="pc-inner">
        <div v-for="(msg, idx) in messages" :key="msg._uid ?? idx" class="pc-msg" :class="msg.role">
          <template v-if="msg.role === 'user'">
            <div class="pc-bubble user-bubble">{{ msg.content }}</div>
          </template>
          <template v-else-if="msg.role === 'assistant'">
            <div class="pc-bubble ai-bubble">
              <MarkdownRender
                v-if="msg._done || msg._isQa"
                mode="chat"
                :content="msg.content || ''"
                :final="!!msg._done"
                :fade="false"
                custom-id="point-chat"
                @copy-code="handleCopyCode"
              />
              <template v-else>{{ msg.content }}</template>
            </div>
          </template>
        </div>
        <div v-if="generating && !hasActiveAiMsg" class="pc-msg assistant">
          <div class="pc-bubble ai-bubble">
            <span class="thinking-dots"><span class="dot"></span><span class="dot"></span><span class="dot"></span></span>
          </div>
        </div>
      </div>
    </div>
    <div class="pc-input">
      <a-textarea
        v-model="inputModel"
        :placeholder="placeholder"
        :auto-size="{ minRows: 1, maxRows: 3 }"
        :disabled="generating"
        @keydown.enter.exact.prevent="onSend"
      />
      <a-button v-if="!generating" type="primary" size="small" @click="onSend" :disabled="!inputModel.trim()">{{ $t('qa.ai.common.send') }}</a-button>
      <a-button v-else status="danger" size="small" @click="$emit('stop')">{{ $t('qa.ai.common.stop') }}</a-button>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'PointChat' };
</script>

<script setup lang="ts">
import { ref, nextTick, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { IconLoading } from '@arco-design/web-vue/es/icon';
import MarkdownRender from 'markstream-vue';
import 'markstream-vue/index.css';

const { t } = useI18n();

const props = withDefaults(defineProps<{
  messages: any[];
  generating: boolean;
  placeholder?: string;
  collapsible?: boolean;
  collapsed?: boolean;
  ticker?: string;
}>(), {
  collapsible: false,
  collapsed: false,
  ticker: '',
});
const emit = defineEmits(['send', 'stop', 'toggle-collapse']);

const inputModel = ref('');
const bodyRef = ref<HTMLElement>();
const isAtBottom = ref(true);

const hasActiveAiMsg = computed(() => {
  const last = props.messages[props.messages.length - 1];
  return last && ['assistant'].includes(last.role);
});

// 折叠态跑马灯无最新事件时，退化为最近一条 assistant 消息摘要
const lastSummary = computed(() => {
  for (let i = props.messages.length - 1; i >= 0; i -= 1) {
    const m = props.messages[i];
    if (m.role === 'assistant' && m.content) return m.content;
  }
  return '';
});

function onScroll() {
  if (!bodyRef.value) return;
  const el = bodyRef.value;
  isAtBottom.value = el.scrollHeight - el.scrollTop - el.clientHeight < 50;
}
function scrollToBottom() {
  if (!isAtBottom.value) return;
  nextTick(() => { if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight; });
}
function forceScrollToBottom() {
  isAtBottom.value = true;
  nextTick(() => { if (bodyRef.value) bodyRef.value.scrollTop = bodyRef.value.scrollHeight; });
}
function onSend() {
  if (!inputModel.value.trim() || props.generating) return;
  emit('send', inputModel.value.trim());
  inputModel.value = '';
}
function handleCopyCode(text: string) {
  navigator.clipboard.writeText(text).then(() => Message.success(t('qa.ai.common.copied')));
}

defineExpose({ scrollToBottom, forceScrollToBottom });
</script>

<style scoped>
.point-chat { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.pc-log-bar { display: flex; align-items: center; gap: 6px; padding: 4px 12px; flex-shrink: 0; cursor: pointer; border-bottom: 1px solid var(--color-border-2); font-size: 12px; color: var(--color-text-3); user-select: none; }
.pc-log-bar:hover { background: var(--color-fill-1); }
.pc-ticker-spin { color: rgb(var(--primary-6)); flex-shrink: 0; }
.pc-ticker-text { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pc-log-label { flex: 1; font-weight: 500; }
.pc-ticker-toggle { flex-shrink: 0; color: rgb(var(--primary-6)); }
.pc-body { flex: 1; overflow-y: auto; padding: 12px; }
.pc-inner { max-width: 100%; margin: 0 auto; }
.pc-msg { margin-bottom: 8px; display: flex; gap: 6px; }
.pc-msg.user { justify-content: flex-end; }
.pc-bubble { display: inline-block; max-width: calc(100% - 20px); padding: 6px 10px; border-radius: 8px; font-size: 13px; line-height: 1.6; word-break: break-word; }
.user-bubble { background: rgb(var(--primary-6)); color: #fff; }
.ai-bubble { display: block; max-width: 100%; background: var(--color-bg-2); border: 1px solid var(--color-border-2); }
.ai-bubble :deep(h1),.ai-bubble :deep(h2),.ai-bubble :deep(h3) { margin: 8px 0 4px; font-weight: 600; }
.ai-bubble :deep(p) { margin: 4px 0; }
.ai-bubble :deep(ul),.ai-bubble :deep(ol) { margin: 4px 0; padding-left: 20px; }
.ai-bubble :deep(code) { background: var(--color-fill-3); padding: 2px 4px; border-radius: 2px; font-size: 12px; }
.ai-bubble :deep(pre) { background: var(--color-fill-3); padding: 8px; border-radius: 4px; overflow-x: auto; margin: 4px 0; }
.thinking-dots { display: inline-flex; gap: 4px; padding: 4px 0; }
.thinking-dots .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--color-text-3); animation: bounce 1.4s infinite ease-in-out; }
.thinking-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%,80%,100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }
.pc-input { display: flex; gap: 6px; padding: 8px; flex-shrink: 0; border-top: 1px solid var(--color-border-2); }
</style>
