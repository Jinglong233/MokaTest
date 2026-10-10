<template>
  <div class="card adopt-workspace-card">
    <div class="card-header">
      <span class="card-tag">{{ $t('qa.ai.cards.adoptCases') }}</span>
      <span class="card-summary">{{ $t('qa.ai.cards.totalPending', { count: drafts.length }) }}</span>
    </div>

    <!-- 工具条 -->
    <div class="toolbar">
      <a-checkbox v-model="selectAll" @change="toggleAll">{{ $t('qa.ai.common.selectAll') }}</a-checkbox>
      <a-popconfirm :content="$t('qa.ai.cards.batchAdoptConfirm', { count: selectedCount })" @ok="batchAdopt">
        <a-button type="primary" size="mini" :loading="batching" :disabled="!selectedCount">{{ $t('qa.ai.cards.batchAdopt', { count: selectedCount }) }}</a-button>
      </a-popconfirm>
      <a-select v-model="sortBy" size="mini" style="width: 110px" :placeholder="$t('qa.ai.cards.sort')">
        <a-option value="priority">{{ $t('qa.ai.cards.sortByPriority') }}</a-option>
        <a-option value="name">{{ $t('qa.ai.cards.sortByName') }}</a-option>
      </a-select>
      <a-select v-model="filter" size="mini" style="width: 100px" :placeholder="$t('qa.ai.cards.filter')">
        <a-option value="all">{{ $t('qa.common.all') }}</a-option>
        <a-option value="idle">{{ $t('qa.ai.cards.pending') }}</a-option>
        <a-option value="adopted">{{ $t('qa.ai.common.adopted') }}</a-option>
        <a-option value="failed">{{ $t('qa.ai.common.failed') }}</a-option>
      </a-select>
      <a-input v-model="searchText" size="mini" :placeholder="$t('qa.common.search')" style="width: 120px" allow-clear />
    </div>

    <!-- 用例列表 -->
    <div class="draft-list">
      <div v-for="(d, i) in filteredDrafts" :key="(d.draftId as any) || i" class="draft-row" :class="'row-' + (d._status || 'idle')">
        <!-- 待处理 -->
        <template v-if="(d._status || 'idle') === 'idle'">
          <a-checkbox v-model="d._selected" />
          <span class="draft-pri" :class="'pri-' + (d.priority || 'P2').toLowerCase()">{{ d.priority || 'P2' }}</span>
          <span class="draft-name" @click="d._expanded = !d._expanded">{{ d.caseName }}</span>
          <a-button size="mini" type="text" @click="adoptSingle(d)">{{ $t('qa.ai.cards.adopt') }}</a-button>
          <a-button size="mini" type="text" @click="d._editing = true">{{ $t('qa.ai.common.edit') }}</a-button>
          <a-button size="mini" type="text" status="danger" @click="deleteDraft(d, i)">{{ $t('qa.common.delete') }}</a-button>
        </template>

        <!-- 提交中 -->
        <template v-else-if="d._status === 'submitting'">
          <a-spin :loading="true" :size="16" />
          <span class="draft-name">{{ d.caseName }}</span>
        </template>

        <!-- 已采纳 -->
        <template v-else-if="d._status === 'adopted'">
          <IconCheckCircle class="row-icon-success" />
          <span class="draft-name">{{ d.caseName }}</span>
          <span class="case-code">{{ d._caseCode || '' }}</span>
        </template>

        <!-- 失败 -->
        <template v-else-if="d._status === 'failed'">
          <IconCloseCircle class="row-icon-error" />
          <span class="draft-name">{{ d.caseName }}</span>
          <span class="error-msg">{{ d._error }}</span>
          <a-button size="mini" type="text" @click="adoptSingle(d)">{{ $t('qa.ai.common.retry') }}</a-button>
          <a-button size="mini" type="text" status="danger" @click="deleteDraft(d, i)">{{ $t('qa.common.delete') }}</a-button>
        </template>

        <!-- 编辑模式 -->
        <div v-if="d._editing" class="edit-mode">
          <a-input v-model="d.caseName" size="mini" :placeholder="$t('qa.ai.cards.caseNamePlaceholder')" />
          <div v-for="(s, si) in (d.testSteps || [])" :key="si" class="edit-step">
            <a-input v-model="s.step" size="mini" :placeholder="$t('qa.ai.cards.stepAction')" style="flex: 1" />
            <a-input v-model="s.expected" size="mini" :placeholder="$t('qa.ai.common.expected')" style="flex: 1" />
            <a-button size="mini" type="text" status="danger" @click="d.testSteps.splice(si, 1)">x</a-button>
          </div>
          <a-button size="mini" @click="d.testSteps = d.testSteps || []; d.testSteps.push({ step: '', expected: '' })">{{ $t('qa.ai.cards.addStep') }}</a-button>
          <div class="edit-actions">
            <a-button size="mini" type="primary" @click="d._editing = false">{{ $t('qa.common.save') }}</a-button>
            <a-button size="mini" @click="d._editing = false">{{ $t('qa.common.cancel') }}</a-button>
          </div>
        </div>

        <!-- 展开步骤 -->
        <div v-if="d._expanded && !d._editing && (d._status || 'idle') === 'idle'" class="draft-detail">
          <div v-for="(s, si) in (d.testSteps || [])" :key="si" class="detail-step">
            <span class="step-num">{{ Number(si) + 1 }}</span>
            <span>{{ s.step }}</span>
            <span class="step-expected">{{ $t('qa.ai.cards.expectedWithColon', { text: s.expected }) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部操作 -->
    <div class="bottom-bar">
      <span class="selected-count">{{ $t('qa.ai.cards.selectedCount', { count: selectedCount }) }}</span>
      <a-button size="small" @click="confirmRegenerate">{{ $t('qa.ai.cards.regenerateAll') }}</a-button>
      <a-button size="small" @click="showFeedback = !showFeedback">{{ $t('qa.ai.cards.appendFeedback') }}</a-button>
    </div>

    <!-- 追加反馈输入 -->
    <div v-if="showFeedback" class="feedback-box">
      <a-textarea v-model="feedbackText" :placeholder="$t('qa.ai.cards.feedbackPlaceholder')" :auto-size="{ minRows: 2, maxRows: 4 }" />
      <a-button type="primary" size="small" @click="submitFeedback" :disabled="!feedbackText.trim()">{{ $t('qa.ai.common.send') }}</a-button>
    </div>

    <!-- 批量进度 -->
    <div v-if="batchProgress" class="batch-progress">
      <a-progress :percent="batchProgress.percent" size="mini" />
      <span>{{ $t('qa.ai.cards.batchProgress', { done: batchProgress.done, total: batchProgress.total, failed: batchProgress.failed }) }}</span>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'AdoptWorkspaceCard' };
</script>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message, Modal } from '@arco-design/web-vue';
import { IconCheckCircle, IconCloseCircle } from '@arco-design/web-vue/es/icon';
import { adoptAiCases } from '@/api/MyApi/aiCase';

const { t } = useI18n();

const props = defineProps<{ drafts: any[]; recordNo: string }>();
const emit = defineEmits<{ adopted: [result: any]; regenerate: []; appendGen: [instruction: string] }>();

// 初始化 drafts 状态
// 初始化 drafts 状态
props.drafts.forEach((d: any) => {
  if (!d._status) d._status = 'idle';
  if (d._selected === undefined) d._selected = true;
});

const selectAll = ref(true);
const sortBy = ref('priority');
const filter = ref('all');
const searchText = ref('');
const batching = ref(false);
const batchProgress = ref<{ percent: number; done: number; total: number; failed: number } | null>(null);
const showFeedback = ref(false);
const feedbackText = ref('');

const selectedCount = computed(() => props.drafts.filter((d: any) => d._selected && (d._status || 'idle') === 'idle').length);

const filteredDrafts = computed(() => {
  let list = [...props.drafts];
  // 过滤
  if (filter.value !== 'all') list = list.filter((d: any) => (d._status || 'idle') === filter.value);
  // 搜索
  if (searchText.value.trim()) list = list.filter((d: any) => d.caseName?.includes(searchText.value));
  // 排序
  if (sortBy.value === 'priority') {
    const order: Record<string, number> = { P0: 0, P1: 1, P2: 2 };
    list.sort((a, b) => (order[a.priority || 'P2'] ?? 2) - (order[b.priority || 'P2'] ?? 2));
  } else {
    list.sort((a, b) => (a.caseName || '').localeCompare(b.caseName || ''));
  }
  return list;
});

function toggleAll(val: any) {
  props.drafts.forEach((d: any) => { if ((d._status || 'idle') === 'idle') d._selected = val; });
}

async function adoptSingle(d: any) {
  d._status = 'submitting';
  try {
    const res: any = await adoptAiCases({ recordNo: props.recordNo, items: [{
      draftId: d.draftId, caseName: d.caseName, preCondition: d.preCondition,
      testSteps: d.testSteps, caseType: d.caseType, priority: d.priority, tags: d.tags, expectDuration: d.expectDuration,
    }] as any });
    const savedIds = res.data?.savedIds || [];
    if (savedIds.length > 0) {
      d._status = 'adopted';
      d._caseCode = res.data?.savedCodes?.[0] || '';
    } else {
      d._status = 'failed';
      d._error = t('qa.ai.common.adoptFailed');
    }
  } catch (e: any) {
    d._status = 'failed';
    d._error = e?.message || t('qa.ai.common.networkError');
  }
}

async function batchAdopt() {
  const selected = props.drafts.filter((d: any) => d._selected && (d._status || 'idle') === 'idle');
  if (!selected.length) return;
  batching.value = true;
  batchProgress.value = { percent: 0, done: 0, total: selected.length, failed: 0 };
  let success = 0, failed = 0;
  for (const d of selected) {
    d._status = 'submitting';
    d._selected = false;
    try {
      const res: any = await adoptAiCases({ recordNo: props.recordNo, items: [{
        draftId: d.draftId, caseName: d.caseName, preCondition: d.preCondition,
        testSteps: d.testSteps, caseType: d.caseType, priority: d.priority, tags: d.tags, expectDuration: d.expectDuration,
      }] as any });
      const savedIds = res.data?.savedIds || [];
      if (savedIds.length > 0) {
        d._status = 'adopted';
        d._caseCode = res.data?.savedCodes?.[0] || '';
        success++;
      } else {
        d._status = 'failed';
        d._error = t('qa.ai.cards.skipExists');
        failed++;
      }
    } catch (e: any) {
      d._status = 'failed';
      d._error = e?.message || t('qa.ai.common.networkError');
      failed++;
    }
    batchProgress.value.done++;
    batchProgress.value.failed = failed;
    batchProgress.value.percent = Math.round((batchProgress.value.done / batchProgress.value.total) * 100);
  }
  batching.value = false;
  if (failed === 0) Message.success(t('qa.ai.cards.batchAdoptSuccess', { count: success }));
  else Message.warning(t('qa.ai.cards.batchAdoptPartial', { success, failed }));
  emit('adopted', { success, failed, skipped: 0, details: [] });
}

function deleteDraft(d: any, i: number) {
  props.drafts.splice(i, 1);
  Message.info({ content: t('qa.ai.cards.deletedOne', { count: 1 }), closable: true, duration: 5000 });
}

function confirmRegenerate() {
  Modal.confirm({ title: t('qa.ai.common.confirm'), content: t('qa.ai.cards.regenerateConfirm'), onOk: () => emit('regenerate') });
}

function submitFeedback() {
  if (!feedbackText.value.trim()) return;
  emit('appendGen', feedbackText.value);
  feedbackText.value = '';
  showFeedback.value = false;
}
</script>

<style scoped>
.adopt-workspace-card {
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 12px 16px;
  max-width: 100%;
}
.card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.card-tag { font-size: 12px; color: rgb(var(--primary-6)); font-weight: 600; }
.card-summary { font-size: 12px; color: var(--color-text-3); }
.toolbar { display: flex; gap: 8px; align-items: center; padding: 8px 0; border-bottom: 1px solid var(--color-fill-2); flex-wrap: wrap; }
.draft-list { max-height: 400px; overflow-y: auto; }
.draft-row { display: flex; align-items: center; gap: 6px; padding: 8px 0; border-bottom: 1px solid var(--color-fill-2); font-size: 13px; }
.row-adopted { background: rgb(var(--success-1)); border-left: 3px solid rgb(var(--success-6)); padding-left: 8px; }
.row-failed { background: rgb(var(--danger-1)); border-left: 3px solid rgb(var(--danger-6)); padding-left: 8px; }
.row-submitting { opacity: 0.6; }
.draft-pri { font-size: 11px; font-weight: 600; flex-shrink: 0; }
.pri-p0 { color: rgb(var(--danger-6)); }
.pri-p1 { color: rgb(var(--warning-6)); }
.pri-p2 { color: var(--color-text-3); }
.draft-name { flex: 1; cursor: pointer; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.case-code { font-size: 11px; color: rgb(var(--primary-6)); }
.error-msg { font-size: 11px; color: rgb(var(--danger-6)); }
.row-icon-success { color: rgb(var(--success-6)); flex-shrink: 0; }
.row-icon-error { color: rgb(var(--danger-6)); flex-shrink: 0; }
.draft-detail { margin-top: 4px; padding-left: 28px; }
.detail-step { display: flex; gap: 8px; padding: 2px 0; font-size: 12px; }
.step-num { width: 18px; height: 18px; border-radius: 3px; background: var(--color-fill-3); font-size: 11px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.step-expected { color: var(--color-text-3); }
.edit-mode { margin-top: 4px; padding: 8px; background: var(--color-fill-1); border-radius: 4px; }
.edit-step { display: flex; gap: 6px; margin-top: 4px; }
.edit-actions { display: flex; gap: 6px; margin-top: 8px; }
.bottom-bar { display: flex; justify-content: space-between; align-items: center; padding: 8px 0; border-top: 1px solid var(--color-fill-2); }
.selected-count { font-size: 12px; color: var(--color-text-3); }
.feedback-box { display: flex; gap: 8px; margin-top: 8px; }
.batch-progress { margin-top: 8px; display: flex; align-items: center; gap: 8px; }
</style>
