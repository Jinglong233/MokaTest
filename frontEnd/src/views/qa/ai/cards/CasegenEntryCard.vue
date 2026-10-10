<template>
  <div class="entry-card">
    <div class="ec-header">
      <IconFile class="ec-icon" />
      <div class="ec-info">
        <div class="ec-title">{{ data?.reqCode || data?.title || $t('qa.ai.cards.casegenTitle') }}</div>
        <div v-if="data?.caseCount != null" class="ec-subtitle">{{ $t('qa.ai.cards.existingCases', { count: data.caseCount }) }}</div>
      </div>
      <a-tag v-if="status === 'converged'" color="green" size="small">{{ $t('qa.ai.cards.statusDone') }}</a-tag>
      <a-tag v-else-if="status === 'generating'" color="arcoblue" size="small">{{ $t('qa.ai.cards.statusGenerating') }}</a-tag>
      <a-tag v-else-if="status === 'pending'" color="orange" size="small">{{ $t('qa.ai.cards.pendingAdopt') }}</a-tag>
    </div>
    <!-- 状态摘要 -->
    <div v-if="statusText" class="ec-status">{{ statusText }}</div>
    <!-- 操作按钮 -->
    <div class="ec-actions">
      <a-button v-if="!recordNo" type="primary" size="small" @click="navigate('generate')">
        {{ $t('qa.ai.cards.goGenerate') }}
      </a-button>
      <a-button v-else-if="status === 'converged'" size="small" @click="navigate('view')">
        {{ $t('qa.ai.cards.viewDetail') }}
      </a-button>
      <a-button v-else type="primary" size="small" @click="navigate('resume')">
        {{ $t('qa.ai.cards.viewProgress') }}
      </a-button>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'CasegenEntryCard' };
</script>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { IconFile } from '@arco-design/web-vue/es/icon';
import { listAiCaseRecords, getRecordState } from '@/api/MyApi/aiCase';

const { t } = useI18n();

const props = defineProps<{
  data: any;
  disabled?: boolean;
}>();

const router = useRouter();
const recordNo = ref('');
const status = ref<'idle' | 'generating' | 'pending' | 'converged'>('idle');
const statusText = ref('');
const reqId = computed(() => props.data?.requirementId || props.data?.reqId || 0);

const STATUS_LABELS: Record<string, string> = {
  idle: '',
  generating: t('qa.ai.cards.aiGenerating'),
  pending: t('qa.ai.cards.draftsPending'),
  converged: t('qa.ai.cards.coveragePassed'),
};

onMounted(async () => {
  if (!reqId.value) return;
  try {
    const res: any = await listAiCaseRecords(reqId.value);
    const records = res.data || [];
    const active = records.find((r: any) => r.status === 'ACTIVE');
    if (!active) {
      status.value = 'idle';
      statusText.value = '';
      return;
    }
    recordNo.value = active.recordNo;
    const stateRes: any = await getRecordState(active.recordNo);
    const state = stateRes.data;
    if (!state) return;
    // 解析状态
    const stage = state.currentStage;
    if (stage === 'CASE_GEN') {
      status.value = 'generating';
    } else if (stage === 'DONE') {
      // 维度清单状态（后端派生）：已覆盖 x/y + 问题数
      const dims: any[] = state.dimensions || [];
      const covered = dims.filter((d: any) => d.status === 'COVERED').length;
      const issues = dims.filter((d: any) => d.status === 'ISSUE' || d.status === 'FAILED').length;
      let payload: any = null;
      if (state.stagePayload) {
        try { payload = JSON.parse(state.stagePayload); } catch {}
      }
      const audit = payload?.audit ? JSON.parse(payload.audit) : null;
      const poolCount = Array.isArray(payload?.pool) ? payload.pool.length : 0;
      if (dims.length && covered === dims.length && !issues && audit?.overallVerdict === 'SUFFICIENT') {
        status.value = 'converged';
        statusText.value = t('qa.ai.cards.allDimsCovered', { count: dims.length });
      } else if (dims.length) {
        status.value = poolCount > 0 ? 'pending' : 'idle';
        statusText.value = t('qa.ai.cards.dimsCovered', { covered, total: dims.length })
          + (issues ? t('qa.ai.cards.issuesSuffix', { count: issues }) : '')
          + (poolCount ? t('qa.ai.cards.poolSuffix', { count: poolCount }) : '');
      } else {
        status.value = poolCount > 0 ? 'pending' : 'idle';
        if (poolCount > 0) statusText.value = t('qa.ai.cards.poolPending', { count: poolCount });
      }
    }
  } catch { /* ignore */ }
});

function navigate(mode: string) {
  if (mode === 'generate') {
    router.push({ name: 'AiCaseGen', query: { reqId: reqId.value, from: 'AiWorkbench' } });
  } else {
    router.push({ name: 'AiCaseGen', query: { recordNo: recordNo.value, from: 'AiWorkbench' } });
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
.ec-status { font-size: 12px; color: var(--color-text-2); margin-top: 6px; }
.ec-actions { margin-top: 10px; display: flex; gap: 8px; }
</style>
