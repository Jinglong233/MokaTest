<template>
  <a-form :model="form" layout="vertical">
    <a-form-item :label="$t('api.mock.dataType')" field="type" required>
      <a-cascader
          v-model="selectedCategoryPath"
          :options="mockCategories"
          :placeholder="$t('api.mock.dataTypePlaceholder')"
          @change="onTypeChange"
      />
      <template #extra>
        <span class="preview-expression">{{ previewExpression }}</span>
      </template>
    </a-form-item>

    <a-form-item v-if="showLocale" :label="$t('api.mock.locale')" field="locale">
      <a-select v-model="form.locale" :placeholder="$t('api.common.pleaseSelect')">
        <a-option value="zh">{{ $t('api.mock.localeZh') }}</a-option>
        <a-option value="en">{{ $t('api.mock.localeEn') }}</a-option>
      </a-select>
    </a-form-item>

    <a-form-item v-if="showCaseType" :label="$t('api.mock.caseType')" field="caseType">
      <a-select v-model="form.caseType" :placeholder="$t('api.common.pleaseSelect')">
        <a-option value="lower">{{ $t('api.mock.caseLower') }}</a-option>
        <a-option value="upper">{{ $t('api.mock.caseUpper') }}</a-option>
        <a-option value="number">{{ $t('api.mock.caseNumber') }}</a-option>
        <a-option value="mixed">{{ $t('api.mock.caseMixed') }}</a-option>
      </a-select>
    </a-form-item>

    <a-form-item v-if="showMinMax" :label="$t('api.mock.min')" field="min">
      <a-input-number v-model="form.min" :placeholder="$t('api.mock.min')"/>
    </a-form-item>

    <a-form-item v-if="showMinMax" :label="$t('api.mock.max')" field="max">
      <a-input-number v-model="form.max" :placeholder="$t('api.mock.max')"/>
    </a-form-item>

    <a-form-item v-if="showScale" :label="$t('api.mock.scale')" field="scale">
      <a-input-number v-model="form.scale" :min="0" :max="10" :placeholder="$t('api.mock.scale')"/>
    </a-form-item>

    <a-form-item v-if="showLength" :label="$t('api.mock.length')" field="length">
      <a-input-number v-model="form.length" :min="1" :max="1000" :placeholder="$t('api.mock.lengthPlaceholder')"/>
    </a-form-item>

    <a-form-item v-if="showFormat" :label="$t('api.mock.format')" field="format">
      <a-select
          v-model="form.format"
          :placeholder="$t('api.mock.formatPlaceholder')"
          allow-create
          allow-clear
      >
        <a-option v-for="fmt in formatPresets" :key="fmt.value" :value="fmt.value">{{ fmt.label }}</a-option>
      </a-select>
    </a-form-item>

    <a-form-item v-if="showChoices" :label="$t('api.mock.choices')" field="choices">
      <a-input v-model="form.choices" :placeholder="$t('api.mock.choicesPlaceholder')"/>
    </a-form-item>

    <a-form-item v-if="showFixed" :label="$t('api.mock.fixedValue')" field="fixedValue" required>
      <a-input v-model="form.fixedValue" :placeholder="$t('api.mock.fixedValueInputPlaceholder')"/>
    </a-form-item>

    <a-form-item v-if="showTemplate" :label="$t('api.template.selectTemplate')" field="templateId" required>
      <a-select v-model="form.templateId" :placeholder="$t('api.template.selectPlaceholder')" :loading="templateLoading">
        <a-option v-for="item in templateList" :key="item.id" :value="item.id"
        >{{ item.templateName }}
        </a-option>
      </a-select>
    </a-form-item>

    <a-form-item :label="$t('api.mock.previewResult')">
      <a-input :model-value="previewResult" readonly :loading="previewLoading" :placeholder="$t('api.mock.generating')"
      >
        <template #suffix>
          <a-button
              type="text"
              size="mini"
              :loading="previewLoading"
              :disabled="previewLoading"
              @click="emit('refreshPreview')"
          >
            {{ $t('api.common.refresh') }}
          </a-button>
        </template>
      </a-input>
    </a-form-item>
  </a-form>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {ParameterType} from '@/types/domain/api/apiEnum/ParameterType';
import {DataTemplate} from '@/types/domain/api/DataTemplate';
import {MockCategory} from './useMockDataForm';

interface Props {
  form: any;
  parameterType?: ParameterType;
  mockTypes: { value: string; label: string; types: ParameterType[] }[];
  mockCategories: MockCategory[];
  templateList: DataTemplate[];
  templateLoading: boolean;
  showLocale: boolean;
  showCaseType: boolean;
  showMinMax: boolean;
  showScale: boolean;
  showLength: boolean;
  showFormat: boolean;
  showChoices: boolean;
  showFixed: boolean;
  showTemplate: boolean;
  formatPresets: { value: string; label: string }[];
  previewExpression: string;
  previewResult: string;
  previewLoading: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'typeChange'): void;
  (e: 'refreshPreview'): void;
}>();

const selectedCategoryPath = computed({
  get: () => {
    const type = props.form.type;
    for (const category of props.mockCategories) {
      if (category.children.some(item => item.value === type)) {
        return [category.value, type];
      }
    }
    return [];
  },
  set: (val: any) => {
    if (Array.isArray(val) && val.length >= 2) {
      props.form.type = String(val[val.length - 1]);
      onTypeChange();
    } else if (typeof val === 'string' && val) {
      // 兼容 cascader 可能直接返回叶子值的情况
      props.form.type = val;
      onTypeChange();
    }
  },
});

const onTypeChange = () => {
  emit('typeChange');
};
</script>

<style scoped>
.preview-expression {
  color: var(--color-text-3);
  font-size: 12px;
}
</style>
