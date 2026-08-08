<template>
  <a-modal
      v-model:visible="visible"
      title="插入 Mock 数据"
      @ok="handleOk"
      @cancel="handleCancel"
      :ok-button-props="{ disabled: !canInsert }"
  >
    <MockDataForm
        :parameter-type="parameterType"
        :template-list="templateList"
        :template-loading="templateLoading"
        :form="form"
        :mock-types="mockTypes"
        :mock-categories="mockCategories"
        :show-locale="showLocale"
        :show-case-type="showCaseType"
        :show-min-max="showMinMax"
        :show-scale="showScale"
        :show-length="showLength"
        :show-format="showFormat"
        :show-choices="showChoices"
        :show-fixed="showFixed"
        :show-template="showTemplate"
        :format-presets="formatPresets"
        :preview-expression="previewExpression"
        :preview-result="previewResult"
        :preview-loading="previewLoading"
        @type-change="onTypeChange"
        @refresh-preview="refreshPreview"
    />
  </a-modal>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {ParameterType} from '@/types/domain/api/apiEnum/ParameterType';
import MockDataForm from './MockDataForm.vue';
import {useMockDataForm} from './useMockDataForm';

interface Props {
  visible?: boolean;
  parameterType?: ParameterType;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'select', expression: string): void;
}>();

const visible = computed({
  get: () => props.visible ?? false,
  set: (val) => emit('update:visible', val)
});

const {
  form,
  templateList,
  templateLoading,
  mockTypes,
  mockCategories,
  showLocale,
  showCaseType,
  showMinMax,
  showScale,
  showLength,
  showFormat,
  showChoices,
  showFixed,
  showTemplate,
  formatPresets,
  canInsert,
  previewExpression,
  previewResult,
  previewLoading,
  refreshPreview,
  onTypeChange,
} = useMockDataForm({parameterType: props.parameterType});

const handleOk = () => {
  emit('select', previewExpression.value);
  visible.value = false;
};

const handleCancel = () => {
  visible.value = false;
};
</script>
