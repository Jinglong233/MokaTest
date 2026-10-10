<template>
  <div class="card adopt-result-card">
    <div class="card-header">
      <span class="card-tag">{{ $t('qa.ai.cards.adoptResult') }}</span>
      <span class="result-summary">
        <span class="r-success">{{ $t('qa.ai.cards.successCount', { count: result.success }) }}</span>
        <span v-if="result.failed" class="r-failed">{{ $t('qa.ai.cards.failedCount', { count: result.failed }) }}</span>
        <span v-if="result.skipped" class="r-skipped">{{ $t('qa.ai.cards.skippedCount', { count: result.skipped }) }}</span>
      </span>
    </div>
    <div class="card-body">
      <div v-for="(d, i) in (result.details || [])" :key="i" class="detail-item">
        <span class="detail-status" :class="'st-' + d.status">
          {{ d.status === 'success' ? '✓' : d.status === 'failed' ? '✗' : '−' }}
        </span>
        <span class="detail-name">{{ d.caseName }}</span>
        <span v-if="d.caseCode" class="detail-code">{{ d.caseCode }}</span>
        <span v-if="d.reason" class="detail-reason">{{ d.reason }}</span>
      </div>
    </div>
    <div v-if="result.failed" class="card-actions">
      <a-button size="small" @click="$emit('retryFailed')">{{ $t('qa.ai.cards.retryFailed') }}</a-button>
      <a-button size="small" @click="$emit('viewCases')">{{ $t('qa.ai.cards.viewSaved') }}</a-button>
    </div>
    <div v-else class="card-actions">
      <a-button size="small" @click="$emit('viewCases')">{{ $t('qa.ai.cards.viewSaved') }}</a-button>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'AdoptResultCard' };
</script>

<script setup lang="ts">
defineProps<{ result: { success: number; failed: number; skipped: number; details: any[] } }>();
defineEmits<{ retryFailed: []; viewCases: [] }>();
</script>

<style scoped>
.adopt-result-card {
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 12px 16px;
  max-width: 90%;
}
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.card-tag { font-size: 12px; color: rgb(var(--primary-6)); font-weight: 600; }
.result-summary { display: flex; gap: 8px; font-size: 12px; }
.r-success { color: rgb(var(--success-6)); font-weight: 600; }
.r-failed { color: rgb(var(--danger-6)); font-weight: 600; }
.r-skipped { color: var(--color-text-3); }
.detail-item { display: flex; align-items: center; gap: 6px; padding: 3px 0; font-size: 13px; }
.detail-status { width: 18px; text-align: center; flex-shrink: 0; font-weight: 700; }
.st-success { color: rgb(var(--success-6)); }
.st-failed { color: rgb(var(--danger-6)); }
.st-skipped { color: var(--color-text-3); }
.detail-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.detail-code { font-size: 11px; color: rgb(var(--primary-6)); flex-shrink: 0; }
.detail-reason { font-size: 11px; color: var(--color-text-3); flex-shrink: 0; }
.card-actions { margin-top: 10px; display: flex; gap: 8px; }
</style>
