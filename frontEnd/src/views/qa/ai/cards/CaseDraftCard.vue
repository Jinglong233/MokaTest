<template>
  <div class="cdc" :class="[mode, { 'cdc-streaming': mode === 'streaming', 'cdc-dup': draft.dupWithExisting }]">
    <!-- 头部 -->
    <div class="cdc-header" @click="onHeaderClick">
      <a-checkbox v-if="mode === 'adopt' && isPending" v-model="draft._selected" @click.stop />
      <a-tag :color="tagColor" size="small">{{ draft.priority || 'P2' }}</a-tag>
      <a-tag v-if="draft.caseType" size="small">{{ typeLabel }}</a-tag>
      <span class="cdc-name">{{ draft.caseName }}</span>
      <a-tag v-if="draft.dupWithExisting" color="orange" size="small">{{ $t('qa.ai.cards.duplicate') }}</a-tag>
      <!-- 操作区 -->
      <div v-if="mode === 'adopt' && isPending && !editing" class="cdc-ops" @click.stop>
        <a-button size="small" type="text" @click="editing = true">{{ $t('qa.ai.common.edit') }}</a-button>
        <a-button size="small" type="text" status="success" @click="$emit('adopt-single', draft)">{{ $t('qa.ai.cards.adopt') }}</a-button>
        <a-button size="small" type="text" status="danger" @click="$emit('delete', draft)">{{ $t('qa.common.delete') }}</a-button>
      </div>
      <template v-else-if="draft._adoptStatus === 'success'">
        <IconCheckCircle class="cdc-icon-ok" />
        <a-tag color="green" size="small">{{ $t('qa.ai.cards.saved', { code: draft._caseCode }) }}</a-tag>
      </template>
      <template v-else-if="draft._adoptStatus === 'failed'">
        <IconCloseCircle class="cdc-icon-err" />
        <span class="cdc-err-msg">{{ draft._error }}</span>
        <a-button size="mini" type="text" @click.stop="$emit('adopt-single', draft)">{{ $t('qa.ai.common.retry') }}</a-button>
      </template>
      <a-spin v-else-if="draft._adoptStatus === 'submitting'" :size="14" />
      <span v-if="mode === 'streaming'" class="cdc-live-hint">{{ $t('qa.ai.cards.streaming') }}</span>
      <!-- 折叠条 -->
      <span v-if="draft.testSteps?.length && !editing" class="cdc-toggle" @click.stop="expanded = !expanded">
        {{ expanded ? $t('qa.ai.common.collapse') : $t('qa.ai.cards.stepsCount', { count: draft.testSteps.length }) }}
      </span>
    </div>
    <!-- 编辑模式 -->
    <div v-if="editing && isPending" class="cdc-edit" @click.stop>
      <a-input v-model="draft.caseName" size="small" :placeholder="$t('qa.testCase.name')" />
      <div class="cdc-edit-row"><span class="cdc-edit-label">{{ $t('qa.testCase.preCondition') }}</span>
        <a-textarea v-model="draft.preCondition" :auto-size="{ minRows: 1, maxRows: 3 }" size="small" /></div>
      <div v-for="(s, si) in (draft.testSteps || [])" :key="si" class="cdc-edit-step">
        <a-input v-model="s.step" size="small" :placeholder="$t('qa.ai.cards.stepActionFull')" style="flex:1" />
        <a-input v-model="s.expected" size="small" :placeholder="$t('qa.testCase.expected')" style="flex:1" />
        <a-button size="small" type="text" status="danger" @click="draft.testSteps.splice(si, 1)">{{ $t('qa.common.delete') }}</a-button>
      </div>
      <a-button size="small" @click="draft.testSteps = draft.testSteps || []; draft.testSteps.push({ step: '', expected: '' })">{{ $t('qa.ai.cards.addStep') }}</a-button>
      <div class="cdc-edit-row"><span class="cdc-edit-label">{{ $t('qa.ai.cards.tags') }}</span>
        <a-input v-model="draft.tags" size="small" style="max-width: 300px" /></div>
      <div class="cdc-edit-actions">
        <a-button size="small" type="primary" @click="saveEdit">{{ $t('qa.common.save') }}</a-button>
        <a-button size="small" @click="editing = false">{{ $t('qa.common.cancel') }}</a-button>
      </div>
    </div>
    <!-- 只读内容 -->
    <template v-if="!editing">
      <div v-if="draft.preCondition && expanded" class="cdc-pre">
        <span class="cdc-pre-label">{{ $t('qa.testCase.preCondition') }}</span>{{ draft.preCondition }}
      </div>
      <div v-if="expanded && draft.testSteps?.length" class="cdc-steps">
        <div v-for="(s, si) in draft.testSteps" :key="si" class="cdc-step">
          <span class="cdc-step-num">{{ si + 1 }}</span>
          <div class="cdc-step-body">
            <div class="cdc-step-act">{{ s.step }}</div>
            <div v-if="s.expected" class="cdc-step-exp">
              <span class="cdc-step-exp-tag">{{ $t('qa.ai.common.expected') }}</span>{{ s.expected }}
            </div>
          </div>
        </div>
      </div>
      <div v-if="expanded && draft.coverPoints?.length" class="cdc-covers">
        <a-tag v-for="cp in draft.coverPoints" :key="cp" size="small" color="arcoblue">{{ cp }}</a-tag>
      </div>
    </template>
  </div>
</template>

<script lang="ts">
export default { name: 'CaseDraftCard', inheritAttrs: false };
</script>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { IconCheckCircle, IconCloseCircle } from '@arco-design/web-vue/es/icon';

const { t } = useI18n();

const props = defineProps<{
  draft: any;
  mode: 'streaming' | 'adopt' | 'adopted' | 'failed';
}>();
const emit = defineEmits(['adopt-single', 'delete', 'edited']);

const expanded = ref(false);
const editing = ref(false);

const isPending = computed(() => (props.draft._adoptStatus || 'pending') === 'pending');
const tagColor = computed(() => {
  const p = (props.draft.priority || 'P2').toUpperCase();
  if (p === 'P0') return 'red';
  if (p === 'P1') return 'orange';
  return 'blue';
});

const TYPE_KEY_MAP: Record<string, string> = { FUNCTION: 'qa.ai.caseType.FUNCTION', API: 'qa.ai.caseType.API', PERFORMANCE: 'qa.ai.caseType.PERFORMANCE', COMPATIBILITY: 'qa.ai.caseType.COMPATIBILITY', SMOKE: 'qa.ai.caseType.SMOKE' };
const typeLabel = computed(() => (TYPE_KEY_MAP[props.draft.caseType] ? t(TYPE_KEY_MAP[props.draft.caseType]) : props.draft.caseType || ''));

function onHeaderClick() {
  if (!editing.value && isPending.value && props.mode !== 'streaming') {
    expanded.value = !expanded.value;
  } else if (props.mode === 'streaming') {
    expanded.value = !expanded.value;
  }
}

function saveEdit() {
  editing.value = false;
  emit('edited', props.draft);
}
</script>

<style scoped>
.cdc { border: 1px solid var(--color-fill-3); border-radius: 8px; padding: 12px 16px; margin-bottom: 8px; background: var(--color-bg-1); }
.cdc-streaming { border-color: rgb(var(--primary-5)); animation: cdc-breathe 1.5s ease-in-out infinite; }
.cdc-dup { border-color: rgb(var(--warning-4)); background: rgb(var(--warning-1)); }
@keyframes cdc-breathe { 0%,100% { box-shadow: 0 0 0 0 rgba(var(--primary-6),0.15); } 50% { box-shadow: 0 0 0 4px rgba(var(--primary-6),0.08); } }
.cdc-header { display: flex; align-items: center; gap: 8px; }
.cdc-name { font-size: 14px; font-weight: 600; flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cdc-ops { display: flex; gap: 4px; flex-shrink: 0; }
.cdc-icon-ok { color: rgb(var(--success-6)); flex-shrink: 0; } .cdc-icon-err { color: rgb(var(--danger-6)); flex-shrink: 0; }
.cdc-err-msg { font-size: 12px; color: var(--danger-6); }
.cdc-live-hint { font-size: 12px; color: rgb(var(--primary-6)); }
.cdc-toggle { font-size: 12px; color: var(--color-text-3); cursor: pointer; flex-shrink: 0; }

.cdc-pre { font-size: 13px; color: var(--color-text-3); margin-top: 10px; display: flex; gap: 6px; }
.cdc-pre-label { color: var(--color-text-4); flex-shrink: 0; font-weight: 500; }

.cdc-steps { display: flex; flex-direction: column; gap: 10px; margin-top: 10px; }
.cdc-step { display: flex; gap: 10px; }
.cdc-step-num { flex-shrink: 0; width: 24px; height: 24px; border-radius: 4px; background: var(--color-fill-3); font-size: 13px; display: flex; align-items: center; justify-content: center; font-weight: 600; color: var(--color-text-2); }
.cdc-step-body { flex: 1; min-width: 0; }
.cdc-step-act { font-size: 13px; line-height: 1.6; color: var(--color-text-1); }
.cdc-step-exp { font-size: 13px; line-height: 1.6; color: var(--color-text-3); border-left: 3px solid rgb(var(--success-5)); padding-left: 8px; margin-top: 4px; display: flex; gap: 4px; }
.cdc-step-exp-tag { font-size: 11px; padding: 1px 6px; border-radius: 3px; background: rgb(var(--success-1)); color: rgb(var(--success-6)); flex-shrink: 0; font-weight: 500; }

.cdc-covers { display: flex; gap: 4px; flex-wrap: wrap; margin-top: 10px; }

.cdc-edit { display: flex; flex-direction: column; gap: 10px; margin-top: 10px; }
.cdc-edit-row { display: flex; align-items: flex-start; gap: 8px; }
.cdc-edit-label { font-size: 13px; color: var(--color-text-3); width: 64px; flex-shrink: 0; padding-top: 6px; }
.cdc-edit-step { display: flex; gap: 6px; align-items: center; }
.cdc-edit-actions { display: flex; gap: 8px; justify-content: flex-end; }
</style>
