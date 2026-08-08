<template>
  <template v-if="hasQuickInput">
    <!-- 固定值 -->
    <template v-if="rule.ruleType === 'fixed'">
      <a-select
        v-if="rule.fieldType === 'BOOLEAN'"
        class="short-input"
        :model-value="rule.fixedValue"
        placeholder="固定值"
        size="mini"
        :disabled="disabled"
        @change="(v) => updateField('fixedValue', v as string)"
      >
        <a-option value="true">true</a-option>
        <a-option value="false">false</a-option>
      </a-select>
      <a-input
        v-else
        class="short-input"
        :model-value="rule.fixedValue"
        placeholder="Mock 数据"
        size="mini"
        :disabled="disabled"
        @update:model-value="(v) => updateField('fixedValue', v)"
      />
    </template>
  </template>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { MockFieldRule } from '@/types/domain/api/requestModel/MockFieldRule';

  interface Props {
    rule: MockFieldRule;
    disabled?: boolean;
  }

  const props = defineProps<Props>();

  const emit = defineEmits<{
    (e: 'update-field', field: keyof MockFieldRule, value: any): void;
  }>();

  const hasQuickInput = computed(() => {
    if (props.rule.fieldType === 'OBJECT') return false;
    const rt = props.rule.ruleType;
    if (!rt) return false;
    return rt === 'fixed';
  });

  function updateField(field: keyof MockFieldRule, value: any) {
    emit('update-field', field, value);
  }
</script>

<style scoped>
  :deep(.short-input) {
    width: 120px !important;
    min-width: 80px !important;
  }

  :deep(.short-input .arco-input-wrapper),
  :deep(.short-input .arco-select),
  :deep(.short-input .arco-input-number) {
    width: 120px !important;
    min-width: 80px !important;
  }
</style>
