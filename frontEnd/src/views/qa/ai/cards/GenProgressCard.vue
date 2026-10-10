<template>
  <div class="card gen-progress-card">
    <div class="card-header">
      <span class="card-tag">{{ $t('qa.ai.cards.caseGen') }}</span>
      <span v-if="streaming" class="card-progress">{{ progress || $t('qa.ai.cards.generatingDefault') }}{{ $t('qa.ai.cards.elapsed', { s: elapsed }) }}</span>
      <span v-else-if="stopped" class="card-stopped">{{ $t('qa.ai.cards.stoppedCount', { count: drafts.length }) }}</span>
      <span v-else-if="drafts.length" class="card-count">{{ $t('qa.ai.cards.totalCount', { count: drafts.length }) }}</span>
    </div>

    <!-- 进度条 -->
    <div v-if="streaming || stopped" class="progress-bar">
      <a-progress :percent="streaming ? 0 : 100" :status="streaming ? 'normal' : 'warning'" size="mini" />
    </div>

    <div class="card-body">
      <!-- 流式期间：loading 状态（跳动点） -->
      <div v-if="streaming && !drafts.length" class="thinking-dots">
        <span class="dot"></span>
        <span class="dot"></span>
        <span class="dot"></span>
      </div>

      <!-- 完成后：markstream-vue 渲染 JSON→Markdown 转换的 <case-draft> 标签 -->
      <MarkdownRender v-if="caseMarkdown && !streaming"
        :content="caseMarkdown" :final="true"
        custom-id="case-gen" :custom-html-tags="['case-draft']" />

      <!-- 停止后：显示已解析的 drafts 摘要 -->
      <div v-if="stopped && drafts.length" class="draft-summary-list">
        <div v-for="(d, i) in drafts.slice(0, 5)" :key="i" class="draft-summary-item">
          <span class="draft-pri" :class="'pri-' + (d.priority || 'P2').toLowerCase()">{{ d.priority || 'P2' }}</span>
          <span class="draft-name">{{ d.caseName || d.title }}</span>
        </div>
        <div v-if="drafts.length > 5" class="summary-more">{{ $t('qa.ai.cards.moreCount', { count: drafts.length - 5 }) }}</div>
      </div>
    </div>

    <div class="card-actions">
      <template v-if="streaming">
        <a-button status="danger" size="small" @click="$emit('stop')">{{ $t('qa.ai.cards.stopGen') }}</a-button>
      </template>
      <template v-else-if="stopped">
        <a-button type="primary" size="small" :disabled="disabled" @click="$emit('continueCases')">{{ $t('qa.ai.cards.continueRest') }}</a-button>
        <a-button size="small" :disabled="disabled" @click="$emit('retry')">{{ $t('qa.ai.common.regenerate') }}</a-button>
      </template>
      <template v-else-if="interrupted">
        <a-button type="primary" size="small" :disabled="disabled" @click="$emit('retry')">{{ $t('qa.ai.common.regenerate') }}</a-button>
      </template>
      <template v-else>
        <a-button type="primary" size="small" :disabled="disabled" @click="$emit('adopt')">{{ $t('qa.ai.cards.enterAdopt') }}</a-button>
      </template>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'GenProgressCard' };
</script>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import MarkdownRender from 'markstream-vue';
import 'markstream-vue/index.css';

const { t } = useI18n();

const props = defineProps<{
  drafts: any[];
  streaming: boolean;
  progress?: string;
  rawText?: string;
  interrupted?: boolean;
  stopped?: boolean;
  disabled?: boolean;
}>();
defineEmits<{ stop: []; retry: []; continueCases: []; adopt: [] }>();

const elapsed = ref(0);
let timer: ReturnType<typeof setInterval> | null = null;

watch(() => props.streaming, (streaming) => {
  if (streaming) {
    elapsed.value = 0;
    timer = setInterval(() => { elapsed.value++; }, 1000);
  } else if (timer) {
    clearInterval(timer);
    timer = null;
  }
}, { immediate: true });

onUnmounted(() => { if (timer) clearInterval(timer); });

/** JSON drafts → Markdown with <case-draft> tags（供 markstream-vue 渲染） */
const caseMarkdown = computed(() => {
  if (!props.drafts.length) return '';
  const none = t('qa.ai.common.none');
  const mdField = (label: string) => t('qa.ai.cards.mdField', { label });
  return props.drafts.map(d => {
    const steps = (d.testSteps || []).map((s: any, i: number) =>
      `${i + 1}. ${s.step || s.action}${t('qa.ai.cards.mdStepExpected', { expected: s.expected || '' })}`
    ).join('\n');
    return `<case-draft name="${d.caseName || d.title || ''}" priority="${d.priority || 'P2'}" type="${d.caseType || 'FUNCTION'}" method="${d.designMethod || ''}">\n\n${mdField(t('qa.testCase.preCondition'))} ${d.preCondition || none}\n\n${mdField(t('qa.testCase.steps'))}\n${steps || none}\n\n${mdField(t('qa.ai.cards.tags'))} ${d.tags || none}\n\n</case-draft>`;
  }).join('\n\n');
});
</script>

<style scoped>
.gen-progress-card {
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 12px 16px;
  max-width: 100%;
}
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.card-tag { font-size: 12px; color: rgb(var(--primary-6)); font-weight: 600; }
.card-progress { font-size: 12px; color: var(--color-text-3); }
.card-stopped { font-size: 12px; color: rgb(var(--warning-6)); }
.card-count { font-size: 12px; color: var(--color-text-3); }
.progress-bar { margin-bottom: 8px; }
.card-body { min-height: 40px; }
.card-actions { margin-top: 10px; display: flex; gap: 8px; }

/* loading 跳动点 */
.thinking-dots { display: inline-flex; gap: 4px; padding: 8px 0; }
.thinking-dots .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--color-text-3); animation: thinking-bounce 1.4s infinite ease-in-out; }
.thinking-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes thinking-bounce { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }

/* 停止后摘要 */
.draft-summary-list { display: flex; flex-direction: column; gap: 4px; }
.draft-summary-item { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--color-text-2); }
.draft-pri { font-size: 10px; font-weight: 600; flex-shrink: 0; }
.pri-p0 { color: rgb(var(--danger-6)); }
.pri-p1 { color: rgb(var(--warning-6)); }
.pri-p2 { color: var(--color-text-3); }
.draft-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.summary-more { font-size: 11px; color: rgb(var(--primary-6)); }
</style>
