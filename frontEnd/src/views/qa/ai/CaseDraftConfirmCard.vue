<template>
  <div class="entry-card">
    <div class="cdc-header">
      <span class="cdc-title">{{ $t('qa.ai.cards.caseDraftTitle') }}</span>
      <a-tag v-if="saved" color="green" size="small">{{ $t('qa.ai.cards.caseDraftSaved', { code: data?.caseCode }) }}</a-tag>
    </div>

    <!-- 已保存：只读摘要，防止重复提交 -->
    <template v-if="saved">
      <div class="cdc-saved-name">{{ data?.caseName }}</div>
      <div class="cdc-saved-meta">
        <a-tag size="small">{{ $t(typeLabelKey(data?.caseType)) }}</a-tag>
        <a-tag size="small">{{ data?.priority }}</a-tag>
        <span v-if="data?.requirementId" class="cdc-req">{{ $t('qa.ai.cards.caseDraftRelatedReq', { id: data.requirementId }) }}</span>
      </div>
    </template>

    <!-- 草稿：可编辑表单 -->
    <template v-else>
      <div v-if="data?.notice" class="cdc-notice">{{ data.notice }}</div>
      <a-form :model="form" layout="vertical" class="cdc-form">
        <a-form-item :label="$t('qa.testCase.name')" required>
          <a-input v-model="form.caseName" :disabled="disabled" :placeholder="$t('qa.testCase.namePlaceholder')" />
        </a-form-item>
        <div class="cdc-row">
          <a-form-item :label="$t('qa.testCase.type')" class="cdc-col">
            <a-select v-model="form.caseType" :disabled="disabled">
              <a-option v-for="ct in CASE_TYPES" :key="ct" :value="ct">{{ $t(typeLabelKey(ct)) }}</a-option>
            </a-select>
          </a-form-item>
          <a-form-item :label="$t('qa.testCase.priorityLevel')" class="cdc-col">
            <a-select v-model="form.priority" :disabled="disabled">
              <a-option v-for="p in PRIORITIES" :key="p" :value="p">{{ p }}</a-option>
            </a-select>
          </a-form-item>
        </div>
        <a-form-item :label="$t('qa.testCase.preCondition')">
          <a-textarea v-model="form.preCondition" :disabled="disabled" :auto-size="{ minRows: 2, maxRows: 4 }" />
        </a-form-item>
        <div v-if="data?.requirementId" class="cdc-req">{{ $t('qa.ai.cards.caseDraftRelatedReq', { id: data.requirementId }) }}</div>
        <a-form-item :label="$t('qa.testCase.steps')">
          <div class="cdc-steps">
            <div v-for="(s, i) in steps" :key="i" class="cdc-step-row">
              <span class="cdc-step-index">{{ i + 1 }}</span>
              <a-textarea v-model="s.step" :disabled="disabled" :placeholder="$t('qa.testCase.stepDesc')" :auto-size="{ minRows: 1, maxRows: 4 }" />
              <a-textarea v-model="s.expected" :disabled="disabled" :placeholder="$t('qa.testCase.expected')" :auto-size="{ minRows: 1, maxRows: 4 }" />
              <a-button type="text" status="danger" size="mini" :disabled="disabled || steps.length <= 1" @click="steps.splice(i, 1)">
                <IconDelete />
              </a-button>
            </div>
            <a-button type="dashed" long size="small" :disabled="disabled" @click="steps.push({ step: '', expected: '' })">
              <IconPlus /> {{ $t('qa.testCase.addStep') }}
            </a-button>
          </div>
        </a-form-item>
      </a-form>
      <div class="cdc-actions">
        <a-button type="primary" size="small" :loading="saving" :disabled="disabled" @click="handleSave">
          {{ $t('qa.ai.cards.caseDraftConfirm') }}
        </a-button>
      </div>
    </template>
  </div>
</template>

<script lang="ts">
export default { name: 'CaseDraftConfirmCard' };
</script>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { IconDelete, IconPlus } from '@arco-design/web-vue/es/icon';
import { saveTestCase } from '@/api/MyApi/qa';

/**
 * create_test_case 工具草稿卡片：聊天内编辑 + 确认入库（POST /qa/testCase/save）
 * 入库成功后 emit saved，由父级更新卡片数据并回写持久化，刷新后保持已保存态
 */
const props = defineProps<{
  data?: any;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  saved: [data: any];
}>();

const { t } = useI18n();

const CASE_TYPES = ['FUNCTION', 'API', 'PERFORMANCE', 'COMPATIBILITY', 'SMOKE'];
const PRIORITIES = ['P0', 'P1', 'P2'];

const TYPE_LABEL_KEYS: Record<string, string> = {
  FUNCTION: 'qa.ai.caseType.FUNCTION',
  API: 'qa.ai.caseType.API',
  PERFORMANCE: 'qa.ai.caseType.PERFORMANCE',
  COMPATIBILITY: 'qa.ai.caseType.COMPATIBILITY',
  SMOKE: 'qa.ai.caseType.SMOKE',
};

function typeLabelKey(type?: string) {
  return TYPE_LABEL_KEYS[type || ''] || TYPE_LABEL_KEYS.FUNCTION;
}

// LLM 可能返回非法枚举值：回退默认值并提示（与工具内默认值一致 FUNCTION/P2）
let enumFallback = false;
function sanitizeEnum(value: any, allowed: string[], fallback: string): string {
  const v = String(value || '').toUpperCase();
  if (allowed.includes(v)) return v;
  if (value != null && value !== '') enumFallback = true;
  return fallback;
}

const form = reactive({
  caseName: props.data?.caseName || '',
  caseType: sanitizeEnum(props.data?.caseType, CASE_TYPES, 'FUNCTION'),
  priority: sanitizeEnum(props.data?.priority, PRIORITIES, 'P2'),
  preCondition: props.data?.preCondition || '',
});

if (enumFallback) {
  Message.warning(t('qa.ai.cards.caseDraftEnumFallback'));
}

const steps = ref<{ step: string; expected: string }[]>(
  Array.isArray(props.data?.testSteps) && props.data.testSteps.length
    ? props.data.testSteps.map((s: any) => ({ step: s?.step || '', expected: s?.expected || '' }))
    : [{ step: '', expected: '' }],
);

const saved = computed(() => !!props.data?.saved);
const saving = ref(false);

async function handleSave() {
  if (!form.caseName.trim()) {
    Message.warning(t('qa.testCase.nameRequired'));
    return;
  }
  const payload: any = {
    caseName: form.caseName.trim(),
    caseType: form.caseType,
    priority: form.priority,
    preCondition: form.preCondition,
    testSteps: steps.value
      .filter((s) => s.step || s.expected)
      .map((s) => ({ step: s.step, expected: s.expected })),
  };
  if (props.data?.requirementId) payload.requirementId = props.data.requirementId;
  saving.value = true;
  try {
    const res: any = await saveTestCase(payload);
    if (res.code === 200) {
      const caseCode = `CASE-${res.data}`;
      Message.success(t('qa.ai.cards.caseDraftSaveSuccess', { code: caseCode }));
      // 回写完整快照（含用户编辑后的内容），父级据此更新并持久化已保存态
      emit('saved', { ...props.data, ...payload, saved: true, caseCode });
    } else {
      Message.error(res.msg || t('qa.ai.cards.caseDraftSaveFailed'));
    }
  } catch (e: any) {
    Message.error(e?.message || t('qa.ai.cards.caseDraftSaveFailed'));
  } finally {
    saving.value = false;
  }
}
</script>

<style scoped>
.entry-card { padding: 12px 16px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); max-width: 640px; }
.cdc-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.cdc-title { font-size: 14px; font-weight: 600; }
.cdc-notice { font-size: 12px; color: var(--color-text-3); margin-bottom: 8px; }
.cdc-form :deep(.arco-form-item) { margin-bottom: 10px; }
.cdc-row { display: flex; gap: 12px; }
.cdc-col { flex: 1; }
.cdc-req { font-size: 12px; color: var(--color-text-3); margin-bottom: 10px; }
.cdc-steps { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.cdc-step-row { display: flex; gap: 6px; align-items: flex-start; }
.cdc-step-index { font-size: 12px; color: var(--color-text-3); line-height: 28px; width: 16px; flex-shrink: 0; text-align: right; }
.cdc-actions { display: flex; justify-content: flex-end; }
.cdc-saved-name { font-size: 13px; font-weight: 500; }
.cdc-saved-meta { display: flex; align-items: center; gap: 6px; margin-top: 6px; }
</style>
