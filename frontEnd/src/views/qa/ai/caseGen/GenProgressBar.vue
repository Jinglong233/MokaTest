<template>
  <div class="gen-progress">
    <span class="gp-stage">{{ stageText }}</span>
    <a-progress
      class="gp-bar"
      :percent="percent"
      :show-text="false"
      :stroke-width="6"
      status="normal"
    />
    <span v-if="currentTitle" class="gp-current">{{ currentTitle }}</span>
    <span class="gp-elapsed">{{ $t('qa.ai.caseGen.progress.elapsed', { time: elapsedText }) }}</span>
    <a-button v-if="generating" status="danger" size="mini" @click="$emit('stop')">{{ $t('qa.ai.common.stop') }}</a-button>
  </div>
</template>

<script lang="ts">
export default { name: 'GenProgressBar' };
</script>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { useI18n } from 'vue-i18n';

const { t } = useI18n();

const props = withDefaults(defineProps<{
  stage?: string;
  dimensions?: any[];
  currentTitle?: string;
  generating?: boolean;
  startTime?: number;
}>(), {
  stage: '',
  dimensions: () => [] as any[],
  currentTitle: '',
  generating: false,
  startTime: 0,
});
defineEmits(['stop']);

// 已完成维度数（失败也算处理完，进度不退回）
const doneCount = computed(() => props.dimensions.filter((d: any) => ['COVERED', 'FAILED', 'ISSUE'].includes(d.status)).length);
const total = computed(() => props.dimensions.length);
const percent = computed(() => (total.value ? doneCount.value / total.value : 0.05));

const stageText = computed(() => {
  if (props.stage === 'context') return t('qa.ai.caseGen.progress.context');
  if (props.stage === 'audit') return t('qa.ai.caseGen.progress.audit');
  if (props.stage === 'qa') return t('qa.ai.caseGen.progress.qa');
  // gen：维度未下发前在规划维度，下发后逐点生成
  return total.value
    ? t('qa.ai.caseGen.progress.genPoints', { done: doneCount.value, total: total.value })
    : t('qa.ai.caseGen.progress.planning');
});

// 已用时间：组件内每秒刷新，卸载清理
const now = ref(Date.now());
let timer: number | undefined;
onMounted(() => { timer = window.setInterval(() => { now.value = Date.now(); }, 1000); });
onBeforeUnmount(() => { if (timer) window.clearInterval(timer); });
const elapsedText = computed(() => {
  const sec = Math.max(0, Math.floor((now.value - (props.startTime || now.value)) / 1000));
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${String(s).padStart(2, '0')}`;
});
</script>

<style scoped>
.gen-progress { display: flex; align-items: center; gap: 10px; flex: 1; min-width: 0; max-width: 640px; }
.gp-stage { font-size: 12px; font-weight: 600; color: rgb(var(--primary-6)); white-space: nowrap; }
.gp-bar { flex: 1; min-width: 80px; }
.gp-current { font-size: 12px; color: var(--color-text-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 180px; }
.gp-elapsed { font-size: 12px; color: var(--color-text-3); white-space: nowrap; font-variant-numeric: tabular-nums; }
</style>
