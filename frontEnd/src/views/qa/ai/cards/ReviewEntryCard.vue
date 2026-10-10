<template>
  <div class="entry-card">
    <div class="ec-header">
      <IconCheckCircle class="ec-icon" />
      <div class="ec-info">
        <div class="ec-title">{{ data?.reqCode || data?.title || $t('qa.ai.cards.reviewTitle') }}</div>
        <div v-if="data?.lastScore != null" class="ec-subtitle">
          {{ $t('qa.ai.cards.lastReview', { score: data.lastScore, time: data.lastTime }) }}
        </div>
        <div v-else class="ec-subtitle">{{ $t('qa.ai.cards.neverReviewed') }}</div>
      </div>
      <a-tag v-if="data?.lastStatus === 'SUCCESS'" color="green" size="small">{{ $t('qa.ai.cards.reviewed') }}</a-tag>
      <a-tag v-else-if="data?.lastStatus === 'RUNNING'" color="arcoblue" size="small">{{ $t('qa.ai.review.expertRunning') }}</a-tag>
      <a-tag v-else-if="data?.lastStatus === 'STOPPED'" color="orange" size="small">{{ $t('qa.ai.cards.unfinished') }}</a-tag>
    </div>
    <div class="ec-actions">
      <a-button type="primary" size="small" @click="navigate">
        {{ data?.lastStatus === 'RUNNING' ? $t('qa.ai.cards.viewProgress') : $t('qa.ai.cards.goReview') }}
      </a-button>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'ReviewEntryCard' };
</script>

<script setup lang="ts">
import { useRouter } from 'vue-router';
import { IconCheckCircle } from '@arco-design/web-vue/es/icon';

const props = defineProps<{
  data: any;
  disabled?: boolean;
}>();

const router = useRouter();

function navigate() {
  const reqId = props.data?.requirementId || props.data?.id || 0;
  if (reqId) {
    router.push({ name: 'AiReview', query: { reqId: String(reqId), from: 'AiWorkbench' } });
  }
}
</script>

<style scoped>
.entry-card { padding: 12px 16px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.ec-header { display: flex; align-items: center; gap: 8px; }
.ec-icon { color: rgb(var(--primary-6)); font-size: 18px; flex-shrink: 0; }
.ec-info { flex: 1; min-width: 0; }
.ec-title { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ec-subtitle { font-size: 12px; color: var(--color-text-3); }
.ec-actions { margin-top: 10px; }
</style>
