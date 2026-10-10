<template>
  <div class="card req-confirm-card" :class="{ 'card-loading': loading }">
    <div class="card-header">
      <span class="card-tag">{{ $t('qa.ai.cards.reqConfirm') }}</span>
      <span v-if="reqData.aiReviewScore != null" class="card-score" :class="scoreClass">{{ $t('qa.ai.review.scoreSuffix', { score: reqData.aiReviewScore }) }}</span>
    </div>

    <!-- loading 态 -->
    <div v-if="loading" class="card-body loading-body">
      <a-spin :loading="true" :tip="$t('qa.ai.cards.analyzingReq')" style="width: 100%" />
    </div>

    <!-- 正常态 -->
    <template v-else>
      <!-- 内联换需求输入 -->
      <div v-if="changingReq" class="change-req-box">
        <a-input v-model="newReqId" :placeholder="$t('qa.ai.cards.reqIdPlaceholder')" size="small" @keydown.enter="confirmChangeReq" @keydown.esc="changingReq = false" />
        <a-button type="primary" size="mini" @click="confirmChangeReq">{{ $t('qa.ai.common.ok') }}</a-button>
        <a-button size="mini" @click="changingReq = false">{{ $t('qa.common.cancel') }}</a-button>
      </div>

      <div v-else class="card-body">
        <div class="req-title">{{ reqData.title || $t('qa.ai.cards.unknownReq') }}</div>
        <div class="req-meta">
          <span class="meta-item">{{ reqData.reqCode || '--' }}</span>
          <span class="meta-sep">|</span>
          <span class="meta-item">{{ reqData.status || '--' }}</span>
          <span class="meta-sep">|</span>
          <span class="meta-item">{{ $t('qa.requirement.priority') }} {{ reqData.priority || '--' }}</span>
          <span class="meta-sep">|</span>
          <span class="meta-item">{{ $t('qa.ai.cards.existingCases', { count: reqData.caseCount ?? 0 }) }}</span>
        </div>
      </div>

      <div v-if="!changingReq" class="card-actions">
        <a-button type="primary" size="small" :disabled="disabled" @click="$emit('start')">{{ $t('qa.ai.cards.startGen') }}</a-button>
        <a-button size="small" :disabled="disabled" @click="changingReq = true">{{ $t('qa.ai.cards.changeReq') }}</a-button>
      </div>
    </template>
  </div>
</template>

<script lang="ts">
export default { name: 'ReqConfirmCard' };
</script>

<script setup lang="ts">
import { ref, computed } from 'vue';
const props = defineProps<{ reqData: any; disabled?: boolean; loading?: boolean }>();
const emit = defineEmits<{ start: []; cancel: []; changeReq: [reqId: string] }>();

const changingReq = ref(false);
const newReqId = ref('');

const scoreClass = computed(() => {
  const s = props.reqData.aiReviewScore;
  if (s == null) return '';
  if (s >= 70) return 'score-good';
  if (s >= 50) return 'score-warn';
  return 'score-bad';
});

function confirmChangeReq() {
  if (!newReqId.value.trim()) return;
  emit('changeReq', newReqId.value.trim());
  changingReq.value = false;
  newReqId.value = '';
}
</script>

<style scoped>
.req-confirm-card {
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 12px 16px;
  max-width: 100%;
  transition: border-color 0.2s;
}
.card-loading { border-color: rgb(var(--primary-5)); }
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.card-tag { font-size: 12px; color: rgb(var(--primary-6)); font-weight: 600; }
.card-score { font-size: 12px; font-weight: 600; }
.score-good { color: rgb(var(--success-6)); }
.score-warn { color: rgb(var(--warning-6)); }
.score-bad { color: rgb(var(--danger-6)); }
.loading-body { padding: 24px 0; text-align: center; }
.change-req-box { display: flex; gap: 6px; align-items: center; margin-bottom: 8px; }
.req-title { font-size: 14px; font-weight: 500; margin-bottom: 4px; }
.req-meta { font-size: 12px; color: var(--color-text-3); display: flex; gap: 4px; flex-wrap: wrap; }
.meta-sep { color: var(--color-fill-4); }
.card-actions { margin-top: 10px; display: flex; gap: 8px; }
</style>
