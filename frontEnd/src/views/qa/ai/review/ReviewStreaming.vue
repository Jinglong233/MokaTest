<template>
  <div class="streaming-area">
    <!-- ① 断线重连提示条 -->
    <div v-if="reconnecting" class="reconnect-hint">
      <a-spin :size="12" /> {{ $t('qa.ai.review.reconnecting') }}
    </div>

    <!-- ② 停止按钮 + 不完整横幅 -->
    <div class="streaming-toolbar">
      <a-progress :percent="percent / 100" :show-text="true"
        :status="doneCount >= total ? 'success' : 'normal'" style="flex: 1; min-width: 200px" />
      <a-popconfirm v-if="reviewing && !stopped" :content="$t('qa.ai.review.stopConfirm')" :ok-text="$t('qa.ai.common.stop')" :cancel-text="$t('qa.common.cancel')" @ok="$emit('stop')">
        <a-button status="danger" size="small">{{ $t('qa.ai.review.stopReview') }}</a-button>
      </a-popconfirm>
    </div>
    <!-- 停止后部分报告横幅 + 继续评审 -->
    <div v-if="stopped" class="stopped-banner">
      <IconExclamation style="color: rgb(var(--warning-6)); font-size: 16px" />
      <span class="stopped-text">{{ $t('qa.ai.review.incomplete', { done: doneCount, total }) }}</span>
      <a-button type="primary" size="mini" :loading="resuming" @click="$emit('resume')">{{ $t('qa.ai.review.resumeReview') }}</a-button>
    </div>

    <!-- 4 专家并行评审进度 -->
    <div v-for="expert in EXPERTS" :key="expert.key" class="dim-card-wrap">
      <a-card size="small" :bordered="false" class="rv-card">
        <template #title>
          <span>{{ expert.label }}</span>
          <a-tag v-if="statusOf(expert.key) === 'DONE'" color="green" size="small">{{ $t('qa.ai.review.expertDone') }}</a-tag>
          <a-tag v-else-if="statusOf(expert.key) === 'RUNNING'" color="arcoblue" size="small">{{ $t('qa.ai.review.expertRunning') }}</a-tag>
          <a-tag v-else-if="statusOf(expert.key) === 'RETRYING'" color="orange" size="small">{{ $t('qa.ai.review.expertRetrying') }}</a-tag>
          <a-tag v-else-if="statusOf(expert.key) === 'FAILED'" color="red" size="small">{{ $t('qa.ai.common.failed') }}</a-tag>
          <!-- ⑤ 失败专家重试按钮 -->
          <a-button v-if="statusOf(expert.key) === 'FAILED'" size="mini" type="text" @click="$emit('retry-expert', expert.key)">{{ $t('qa.ai.common.retry') }}</a-button>
        </template>
        <div class="expert-dims">
          {{ $t('qa.ai.review.expertDimsPrefix', { dims: expert.dims }) }}
          <a-spin v-if="['RUNNING', 'RETRYING'].includes(statusOf(expert.key))" :size="14" style="margin-left: 8px" />
        </div>

        <!-- ① DONE 结论态：显示结论摘要，原文收到折叠区 -->
        <template v-if="statusOf(expert.key) === 'DONE' && resultOf(expert.key)">
          <div class="expert-conclusion">
            <span v-if="resultOf(expert.key).issues" class="conclusion-stat" style="color: rgb(var(--warning-6))">
              {{ $t('qa.ai.review.issuesFound', { count: resultOf(expert.key).issues }) }}
            </span>
            <span v-if="resultOf(expert.key).passed" class="conclusion-stat" style="color: rgb(var(--success-6))">
              {{ $t('qa.ai.review.passedCount', { count: resultOf(expert.key).passed }) }}
            </span>
            <span v-if="!resultOf(expert.key).issues && !resultOf(expert.key).passed" class="conclusion-stat" style="color: rgb(var(--success-6))">
              {{ $t('qa.ai.review.expertComplete') }}
            </span>
          </div>
          <!-- 折叠查看过程 -->
          <div v-if="textOf(expert.key)" class="raw-toggle" @click="toggleRaw(expert.key)">
            {{ rawCollapsed[expert.key] === false ? $t('qa.ai.common.collapse') : $t('qa.ai.review.viewProcess') }}
          </div>
          <div v-if="textOf(expert.key) && rawCollapsed[expert.key] === false" class="expert-stream">{{ textOf(expert.key) }}</div>
        </template>

        <!-- RUNNING 流式原文（现状） -->
        <div v-else-if="textOf(expert.key)" class="expert-stream">{{ textOf(expert.key) }}</div>
      </a-card>
    </div>

    <div v-if="aggregating" class="summarizing">
      <a-spin :size="16" /> {{ $t('qa.ai.review.aggregating') }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { IconExclamation } from '@arco-design/web-vue/es/icon';

const { t } = useI18n();

const props = defineProps<{
  expertStatus: Record<string, string>;
  expertTexts?: Record<string, string>;
  /** 已完成专家的结构化结论 {key: {issues, passed}} */
  expertResults?: Record<string, any>;
  aggregating: boolean;
  /** 是否在评审中（有 RUNNING 专家） */
  reviewing?: boolean;
  /** 是否被停止 */
  stopped?: boolean;
  /** 是否在续跑中 */
  resuming?: boolean;
  /** 断线重连中 */
  reconnecting?: boolean;
}>();

defineEmits<{
  stop: [];
  resume: [];
  'retry-expert': [expertKey: string];
}>();

const EXPERTS = computed(() => [
  { key: 'completeness', label: t('qa.ai.review.expert.completeness.label'), dims: t('qa.ai.review.expert.completeness.dims') },
  { key: 'testability', label: t('qa.ai.review.expert.testability.label'), dims: t('qa.ai.review.expert.testability.dims') },
  { key: 'boundary', label: t('qa.ai.review.expert.boundary.label'), dims: t('qa.ai.review.expert.boundary.dims') },
  { key: 'risk', label: t('qa.ai.review.expert.risk.label'), dims: t('qa.ai.review.expert.risk.dims') },
]);

const EXPERT_KEYS = ['completeness', 'testability', 'boundary', 'risk'];

const total = EXPERT_KEYS.length;
const doneCount = computed(() => EXPERT_KEYS.filter(e => props.expertStatus[e] === 'DONE').length);
const percent = computed(() => Math.min(100, Math.round(doneCount.value / total * 100)));

// ① 折叠原始输出（默认收起）
const rawCollapsed = reactive<Record<string, boolean>>({});
for (const e of EXPERT_KEYS) { rawCollapsed[e] = true; }
function toggleRaw(key: string) {
  rawCollapsed[key] = !rawCollapsed[key];
}

function statusOf(key: string) {
  return props.expertStatus[key] || 'PENDING';
}

function textOf(key: string) {
  return props.expertTexts?.[key] || '';
}

function resultOf(key: string) {
  return props.expertResults?.[key];
}
</script>

<script lang="ts">
export default { name: 'ReviewStreaming' };
</script>

<style scoped>
.streaming-area { padding: 0 2px; }
.reconnect-hint { display: flex; align-items: center; gap: 8px; padding: 6px 12px; background: rgb(var(--warning-1)); border: 1px solid rgb(var(--warning-3)); border-radius: 6px; margin-bottom: 8px; font-size: 12px; color: rgb(var(--warning-6)); }
.streaming-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.stopped-banner { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: rgb(var(--warning-1)); border: 1px solid rgb(var(--warning-3)); border-radius: 6px; margin-bottom: 12px; }
.stopped-text { font-size: 13px; color: rgb(var(--warning-6)); flex: 1; }
.dim-card-wrap { margin-top: 8px; }
.expert-dims { font-size: 12px; color: var(--color-text-3); display: flex; align-items: center; }
.expert-stream { margin-top: 6px; padding: 6px 8px; background: var(--color-fill-1); border-radius: 4px; font-size: 12px; color: var(--color-text-3); white-space: pre-wrap; word-break: break-all; max-height: 120px; overflow-y: auto; }
.expert-conclusion { margin-top: 6px; display: flex; gap: 16px; font-size: 13px; }
.conclusion-stat { font-weight: 500; }
.raw-toggle { margin-top: 4px; font-size: 12px; color: rgb(var(--primary-6)); cursor: pointer; user-select: none; }
.raw-toggle:hover { text-decoration: underline; }
.summarizing { display: flex; align-items: center; gap: 8px; margin-top: 12px; font-size: 13px; color: var(--color-text-2); }
</style>
