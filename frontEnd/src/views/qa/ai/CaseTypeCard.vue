<template>
  <div class="entry-card">
    <div class="ct-title">{{ $t('qa.ai.cards.caseTypeTitle') }}</div>
    <div class="ct-options">
      <div class="ct-option" :class="{ 'ct-option-muted': disabled }" @click="onSelect('case')">
        <div class="ct-option-name">{{ $t('qa.ai.cards.caseTypeFunction') }}</div>
        <div class="ct-option-desc">{{ $t('qa.ai.cards.caseTypeFunctionDesc') }}</div>
      </div>
      <div class="ct-option" :class="{ 'ct-option-muted': disabled }" @click="onSelect('apiCase')">
        <div class="ct-option-name">{{ $t('qa.ai.cards.caseTypeApi') }}</div>
        <div class="ct-option-desc">{{ $t('qa.ai.cards.caseTypeApiDesc') }}</div>
      </div>
      <div class="ct-option ct-option-disabled">
        <div class="ct-option-name">
          {{ $t('qa.ai.cards.caseTypeUi') }}
          <a-tag size="small" style="margin-left: 6px">{{ $t('qa.ai.cards.comingSoon') }}</a-tag>
        </div>
        <div class="ct-option-desc">{{ $t('qa.ai.cards.caseTypeUiDesc') }}</div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'CaseTypeCard' };
</script>

<script setup lang="ts">
const props = defineProps<{
  data?: any;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  select: [type: string];
}>();

function onSelect(type: string) {
  if (props.disabled) return;
  emit('select', type);
}
</script>

<style scoped>
.entry-card { padding: 12px 16px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); max-width: 480px; }
.ct-title { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.ct-options { display: flex; flex-direction: column; gap: 8px; }
.ct-option { padding: 8px 12px; border: 1px solid var(--color-border-2); border-radius: 6px; cursor: pointer; transition: all 0.15s; }
.ct-option:hover { border-color: rgb(var(--primary-5)); background: rgb(var(--primary-1)); }
.ct-option-muted { opacity: 0.5; cursor: not-allowed; }
.ct-option-muted:hover { border-color: var(--color-border-2); background: transparent; }
.ct-option-disabled { opacity: 0.5; cursor: not-allowed; }
.ct-option-disabled:hover { border-color: var(--color-border-2); background: transparent; }
.ct-option-name { font-size: 13px; font-weight: 500; display: flex; align-items: center; }
.ct-option-desc { font-size: 12px; color: var(--color-text-3); margin-top: 2px; }
</style>
