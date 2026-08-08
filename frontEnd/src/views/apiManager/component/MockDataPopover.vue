<template>
  <div class="mock-data-popover">
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
    <div class="popover-footer">
      <a-space>
        <a-button type="primary" size="small" :disabled="!canInsert" @click="handleInsert">插入</a-button>
        <a-button size="small" @click="handleCancel">取消</a-button>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ParameterType} from '@/types/domain/api/apiEnum/ParameterType';
import {MockConfig} from '@/types/domain/api/requestModel/MockConfig';
import MockDataForm from './MockDataForm.vue';
import {useMockDataForm} from './useMockDataForm';

interface Props {
  parameterType?: ParameterType;
  mockConfig?: MockConfig;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'select', config: MockConfig): void;
  (e: 'cancel'): void;
}>();

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
  currentMockConfig,
  previewExpression,
  previewResult,
  previewLoading,
  refreshPreview,
  onTypeChange,
} = useMockDataForm({parameterType: props.parameterType, mockConfig: props.mockConfig});

const handleInsert = () => {
  emit('select', currentMockConfig.value);
};

const handleCancel = () => {
  emit('cancel');
};
</script>

<style scoped lang="less">
.mock-data-popover {
  width: 320px;
  padding: 8px 4px;
}

.popover-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid var(--color-neutral-3);
}
</style>
