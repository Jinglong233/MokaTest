<template>
  <div class="rp-bar" :class="`is-${status}`">
    <div class="rp-bar-fill" :style="fillStyle"></div>
    <div class="rp-bar-content">
      <span class="rp-icon" v-html="iconSvg"></span>
      <span class="rp-dim">{{ dim }}</span>
      <span v-if="score !== '' && status === 'done'" class="rp-score" :style="{ color: scoreColor }">{{ score }}</span>
      <span v-if="status === 'running'" class="rp-status-text">{{ $t('qa.ai.review.checking') }}</span>
      <span v-if="status === 'pending'" class="rp-status-text rp-pending">{{ $t('qa.ai.review.waiting') }}</span>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'ReviewProgressNode', inheritAttrs: false };
</script>

<script setup lang="ts">
import { computed } from 'vue';

// 兼容两种使用方式：markstream-vue 自定义组件（node.attrs）和 Vue 直接渲染（props）
const props = defineProps<{ node?: any; dim?: string; status?: string; score?: string | number }>();

const dim = computed(() => props.node?.attrs?.dim || props.dim || '');
const status = computed(() => props.node?.attrs?.status || props.status || 'pending');
const score = computed(() => {
  const raw = props.node?.attrs?.score ?? props.score ?? '';
  return raw !== undefined && raw !== null ? String(raw) : '';
});

const fillStyle = computed(() => {
  if (status.value === 'done') return { width: '100%' };
  if (status.value === 'running') return { width: '45%' };
  return { width: '0%' };
});

const iconSvg = computed(() => {
  if (status.value === 'done') return '<span style="color:#00b42a">✓</span>';
  if (status.value === 'running') return '<span class="rp-spin">⟳</span>';
  return '<span style="color:var(--color-text-4)">○</span>';
});

const scoreColor = computed(() => {
  const s = Number(score.value);
  if (isNaN(s)) return 'var(--color-text-2)';
  return s >= 70 ? '#00b42a' : s >= 50 ? '#ff7d00' : '#f53f3f';
});
</script>

<style scoped>
.rp-bar {
  position: relative;
  height: 30px;
  border-radius: 4px;
  overflow: hidden;
  margin-bottom: 6px;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
}
.rp-bar-fill {
  position: absolute;
  top: 0; left: 0; bottom: 0;
  transition: width 0.6s ease;
}
.is-pending .rp-bar-fill { background: var(--color-fill-3); }
.is-running .rp-bar-fill {
  background: linear-gradient(90deg, rgb(var(--primary-3)), rgb(var(--primary-5)));
  animation: rp-pulse 1.5s ease-in-out infinite;
}
.is-done .rp-bar-fill { background: linear-gradient(90deg, var(--color-success-3), var(--color-success-5)); }
.is-running { border-color: rgb(var(--primary-5)); }
.is-done { border-color: var(--color-success-3); }

.rp-bar-content {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 100%;
  padding: 0 12px;
  z-index: 1;
}
.rp-icon { font-weight: bold; flex-shrink: 0; width: 14px; text-align: center; font-size: 12px; }
.rp-spin { display: inline-block; animation: rp-rotate 1s linear infinite; color: rgb(var(--primary-6)); }
@keyframes rp-rotate { from { transform: rotate(0); } to { transform: rotate(360deg); } }
@keyframes rp-pulse { 0%, 100% { opacity: 0.7; } 50% { opacity: 1; } }
.rp-dim { font-weight: 500; font-size: 13px; }
.rp-score { font-weight: 700; flex-shrink: 0; margin-left: auto; font-size: 13px; }
.rp-status-text { margin-left: auto; font-size: 11px; color: var(--color-text-3); }
.rp-pending { color: var(--color-text-4); }
</style>
