<template>
  <a-form :model="form" layout="vertical">
    <a-form-item label="数据类型" field="type" required>
      <a-cascader
          v-model="selectedCategoryPath"
          :options="mockCategories"
          placeholder="请选择数据类型"
          @change="onTypeChange"
      />
      <template #extra>
        <span class="preview-expression">{{ previewExpression }}</span>
      </template>
    </a-form-item>

    <a-form-item v-if="showLocale" label="语言/地区" field="locale">
      <a-select v-model="form.locale" placeholder="请选择">
        <a-option value="zh">中文</a-option>
        <a-option value="en">英文</a-option>
      </a-select>
    </a-form-item>

    <a-form-item v-if="showCaseType" label="字符类型" field="caseType">
      <a-select v-model="form.caseType" placeholder="请选择">
        <a-option value="lower">小写字母</a-option>
        <a-option value="upper">大写字母</a-option>
        <a-option value="number">数字</a-option>
        <a-option value="mixed">字母数字混合</a-option>
      </a-select>
    </a-form-item>

    <a-form-item v-if="showMinMax" label="最小值" field="min">
      <a-input-number v-model="form.min" placeholder="最小值"/>
    </a-form-item>

    <a-form-item v-if="showMinMax" label="最大值" field="max">
      <a-input-number v-model="form.max" placeholder="最大值"/>
    </a-form-item>

    <a-form-item v-if="showScale" label="小数位数" field="scale">
      <a-input-number v-model="form.scale" :min="0" :max="10" placeholder="小数位数"/>
    </a-form-item>

    <a-form-item v-if="showLength" label="长度" field="length">
      <a-input-number v-model="form.length" :min="1" :max="1000" placeholder="字符串长度"/>
    </a-form-item>

    <a-form-item v-if="showFormat" label="日期格式" field="format">
      <a-select
          v-model="form.format"
          placeholder="选择或输入格式"
          allow-create
          allow-clear
      >
        <a-option v-for="fmt in formatPresets" :key="fmt.value" :value="fmt.value">{{ fmt.label }}</a-option>
      </a-select>
    </a-form-item>

    <a-form-item v-if="showChoices" label="选项（英文逗号分隔）" field="choices">
      <a-input v-model="form.choices" placeholder="例如：A,B,C"/>
    </a-form-item>

    <a-form-item v-if="showFixed" label="固定值" field="fixedValue" required>
      <a-input v-model="form.fixedValue" placeholder="请输入固定值"/>
    </a-form-item>

    <a-form-item v-if="showTemplate" label="选择模板" field="templateId" required>
      <a-select v-model="form.templateId" placeholder="请选择数据模板" :loading="templateLoading">
        <a-option v-for="item in templateList" :key="item.id" :value="item.id"
        >{{ item.templateName }}
        </a-option>
      </a-select>
    </a-form-item>

    <a-form-item label="预览结果">
      <a-input :model-value="previewResult" readonly :loading="previewLoading" placeholder="生成中..."
      >
        <template #suffix>
          <a-button
              type="text"
              size="mini"
              :loading="previewLoading"
              :disabled="previewLoading"
              @click="emit('refreshPreview')"
          >
            刷新
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
