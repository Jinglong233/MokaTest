<template>
  <a-card size="small" :bordered="false" class="rv-card" :title="$t('qa.ai.review.history')">
    <a-timeline>
      <!-- 进行中虚拟条目 -->
      <a-timeline-item v-if="streaming" dot-color="arcoblue">
        <div class="history-item" :class="{ active: selectedId === 'streaming' }" @click="$emit('select', 'streaming')">
          <span class="history-time">{{ $t('qa.ai.review.inProgress') }}</span>
          <a-tag color="arcoblue" size="small">{{ streamProgress.done }}/{{ streamProgress.total }}</a-tag>
        </div>
      </a-timeline-item>

      <a-timeline-item
        v-for="(h, i) in history"
        :key="h.id"
        :dot-color="selectedId === h.id ? 'arcoblue' : 'gray'"
      >
        <div class="history-item" :class="{ active: selectedId === h.id }" @click="$emit('select', h.id)">
          <span class="history-time">{{ h.createTime?.slice(0, 16) }}</span>
          <a-tag :color="scoreTagColor(h.overallScore)" size="small">{{ h.overallScore != null ? $t('qa.ai.review.scoreSuffix', { score: h.overallScore }) : '-' }}</a-tag>
          <span v-if="i < history.length - 1 && h.overallScore != null && history[i + 1].overallScore != null"
            class="score-diff"
            :class="scoreDiffClass(h.overallScore, history[i + 1].overallScore)">
            {{ scoreDiffText(h.overallScore, history[i + 1].overallScore) }}
          </span>
          <a-popconfirm :content="$t('qa.ai.review.deleteConfirm')" position="tr" @ok="handleDelete(h)">
            <a-button size="mini" type="text" class="history-del-btn" @click.stop>
              <template #icon><icon-delete /></template>
            </a-button>
          </a-popconfirm>
        </div>
      </a-timeline-item>
    </a-timeline>

    <a-button v-if="hasMore" size="small" long @click="$emit('load-more')" :loading="loadingMore">
      {{ $t('qa.ai.review.loadMore') }}
    </a-button>
  </a-card>
</template>

<script setup lang="ts">
import { IconDelete } from '@arco-design/web-vue/es/icon';

defineProps<{
  history: any[];
  selectedId: number | string | undefined;
  streaming: boolean;
  streamProgress: { done: number; total: number };
  hasMore: boolean;
  loadingMore: boolean;
}>();

const emit = defineEmits<{
  select: [id: number | string];
  delete: [id: number];
  'load-more': [];
}>();

function handleDelete(h: any) {
  if (h && h.id) emit('delete', h.id);
}

function scoreTagColor(score: number | null | undefined) {
  if (score == null) return 'gray';
  if (score >= 70) return 'green';
  if (score >= 50) return 'orange';
  return 'red';
}
function scoreDiffClass(curr: number, prev: number) {
  const diff = curr - prev;
  if (diff > 0) return 'diff-up';
  if (diff < 0) return 'diff-down';
  return '';
}
function scoreDiffText(curr: number, prev: number) {
  const diff = curr - prev;
  if (diff > 0) return `↑${diff}`;
  if (diff < 0) return `↓${Math.abs(diff)}`;
  return '–';
}
</script>

<style scoped>
.history-item {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  padding: 2px 0;
}
.history-item.active {
  font-weight: 600;
}
.history-time {
  font-size: 12px;
  color: var(--color-text-2);
}
.score-diff {
  font-size: 11px;
  font-weight: 700;
}
.diff-up {
  color: rgb(var(--green-6));
}
.diff-down {
  color: rgb(var(--red-6));
}
.history-del-btn {
  margin-left: auto;
  opacity: 0;
  transition: opacity 0.2s;
}
.history-item:hover .history-del-btn {
  opacity: 1;
}
</style>
