<template>
  <div class="result-area">
    <!-- confidence/reused 横幅 -->
    <a-alert v-if="reused" type="info" class="rv-banner" :action="false">
      {{ $t('qa.ai.review.reusedTip') }}
      <a-button size="mini" type="text" @click="$emit('force-review')">{{ $t('qa.ai.review.forceReview') }}</a-button>
    </a-alert>
    <a-alert v-if="review.confidence === 'LOW'" type="warning" class="rv-banner">
      {{ $t('qa.ai.review.lowConfidence') }}
    </a-alert>

    <!-- 评分总览 -->
    <a-card size="small" :bordered="false" class="rv-card">
      <a-row align="center" :gutter="20">
        <a-col flex="120px">
          <a-statistic :title="$t('qa.ai.review.overallScore')" :value="review.overall ?? 0" suffix="/100"
            :value-style="{ fontSize: '32px', fontWeight: 800, color: scoreColorStr(review.overall) }" />
        </a-col>
        <a-col flex="200px">
          <div ref="radarRef" class="radar-chart" />
        </a-col>
        <a-col flex="1">
          <a-grid :cols="3" :row-gap="6" :col-gap="6">
            <div v-for="d in dims" :key="d.key" class="dim-score-item">
              <a-statistic :value="review.scores?.[d.key] ?? 0"
                :value-style="{ fontSize: '16px', fontWeight: 700, color: scoreColorStr(review.scores?.[d.key]) }" />
              <span class="dim-score-label">{{ d.label }}</span>
            </div>
          </a-grid>
        </a-col>
      </a-row>
    </a-card>

    <!-- 检查表明细（证据面板，默认折叠，维度行带缺失计数） -->
    <a-card v-if="review.checklist?.length" size="small" :bordered="false" class="rv-card" :title="$t('qa.ai.review.checklistTitle')">
      <a-collapse :bordered="false" :default-active-key="[]">
        <a-collapse-item v-for="d in dims" :key="d.key">
          <template #header>
            <span>{{ d.label }}</span>
            <a-tag v-if="missingCount(d.key)" color="red" size="small">{{ $t('qa.ai.review.missingCount', { count: missingCount(d.key) }) }}</a-tag>
            <a-tag :color="scoreTagColor(review.scores?.[d.key])" size="small">{{ review.scores?.[d.key] ?? '—' }}</a-tag>
          </template>
          <a-list size="small" :bordered="false">
            <a-list-item v-for="item in (checklistByDim[d.key] || [])" :key="item.id">
              <a-space size="small" wrap>
                <a-tag :color="answerColor(item.answer)" size="small">{{ answerText(item.answer) }}</a-tag>
                <span>{{ checklistText(item.id) }}</span>
                <a-tag v-if="item.evidenceInvalid" color="red" size="small">{{ $t('qa.ai.review.evidenceInvalid') }}</a-tag>
              </a-space>
              <div v-if="item.quote">
                <a-link @click="$emit('locate', item.quote)">"{{ item.quote }}"</a-link>
              </div>
            </a-list-item>
          </a-list>
        </a-collapse-item>
      </a-collapse>
    </a-card>

    <!-- AI 总评（markstream-vue 渲染 Markdown） -->
    <a-card v-if="review.summary" size="small" :bordered="false" class="rv-card" :title="$t('qa.ai.review.summaryTitle')">
      <MarkdownRender :content="review.summary || ''" :final="true" custom-id="ai-review" />
    </a-card>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onBeforeUnmount } from 'vue';
import * as echarts from 'echarts/core';
import MarkdownRender from 'markstream-vue';
import 'markstream-vue/index.css';
import { checklistText, getDims, dimOfId, answerColor, answerText, scoreColorStr, scoreTagColor } from './constants';

const props = defineProps<{
  review: any;
  risks: any[];
  reused: boolean;
}>();

const emit = defineEmits<{
  locate: [quote: string];
  'force-review': [];
}>();

const radarRef = ref<HTMLElement>();
let chart: echarts.ECharts | null = null;

const dims = computed(() => getDims());

function missingCount(dimKey: string) {
  return (checklistByDim.value[dimKey] || []).filter((it: any) => it.answer !== 'YES').length;
}

const checklistByDim = computed(() => {
  const map: Record<string, any[]> = {};
  for (const item of (props.review.checklist || [])) {
    const dim = dimOfId(item.id);
    if (!map[dim]) map[dim] = [];
    map[dim].push(item);
  }
  return map;
});

function renderRadar() {
  if (!radarRef.value || !props.review.scores) return;
  if (!chart) chart = echarts.init(radarRef.value);
  chart.setOption({
    radar: { indicator: getDims().map(d => ({ name: d.label, max: 100 })), radius: 50, splitArea: { areaStyle: { opacity: 0 } } },
    series: [{ type: 'radar', data: [{ value: getDims().map(d => props.review.scores?.[d.key] ?? 0), areaStyle: { opacity: 0.2 }, lineStyle: { width: 1.5 } }] }],
  });
}

watch(() => props.review, () => {
  nextTick(() => renderRadar());
}, { immediate: true });

onBeforeUnmount(() => {
  if (chart) { chart.dispose(); chart = null; }
});
</script>

<style scoped>
.result-area { padding: 0 2px; }
.rv-banner { margin-bottom: 10px; }
.rv-card + .rv-card { margin-top: 10px; }
.dim-score-item { display: flex; flex-direction: column; align-items: center; padding: 4px; }
.dim-score-label { font-size: 11px; color: var(--color-text-3); margin-top: 2px; }
.radar-chart { width: 200px; height: 160px; }
.summary-text { white-space: pre-wrap; word-break: break-word; font-size: 12px; line-height: 1.5; color: var(--color-text-2); }
</style>
