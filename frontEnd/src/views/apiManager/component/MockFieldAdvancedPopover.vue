<template>
  <a-popover
    trigger="click"
    position="right"
    :content-style="{ width: '420px', padding: '16px' }"
    @popup-visible-change="onVisibleChange"
  >
    <a-button type="text" size="mini" :disabled="disabled">
      <template #icon>
        <icon-settings />
      </template>
      {{ $t('api.mockAdv.title') }}
    </a-button>
    <template #content>
      <div class="mock-advanced-form">
        <a-row :gutter="[16, 12]">
          <a-col :span="12">
            <div class="form-item">
              <label>{{ $t('api.mock.dataType') }}</label>
              <a-select
                :model-value="localRule.fieldType"
                size="mini"
                :disabled="disabled"
                @change="(v) => updateField('fieldType', v as string)"
              >
                <a-option
                  v-for="opt in fieldTypeOptions"
                  :key="opt.value"
                  :value="opt.value"
                  >{{ opt.label }}</a-option
                >
              </a-select>
            </div>
          </a-col>
          <a-col :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.ruleType') }}</label>
              <a-select
                :model-value="localRule.ruleType"
                size="mini"
                :disabled="disabled || isContainer"
                @change="(v) => updateField('ruleType', v as string)"
              >
                <a-option
                  v-for="opt in applicableRuleTypes"
                  :key="opt.value"
                  :value="opt.value"
                  >{{ opt.label }}</a-option
                >
              </a-select>
            </div>
          </a-col>

          <a-col :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.nullable') }}</label>
              <a-switch
                :model-value="localRule.nullable"
                size="small"
                :disabled="disabled"
                @change="(v) => updateField('nullable', v as boolean)"
              />
            </div>
          </a-col>
          <a-col :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.required') }}</label>
              <a-switch
                :model-value="localRule.required"
                size="small"
                :disabled="disabled"
                @change="(v) => updateField('required', v as boolean)"
              />
            </div>
          </a-col>

          <a-col :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.constant') }}</label>
              <a-switch
                :model-value="localRule.isConstant"
                size="small"
                :disabled="disabled || isContainer"
                @change="(v) => updateField('isConstant', v as boolean)"
              />
            </div>
          </a-col>

          <a-col v-if="showChoices" :span="24">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.choices') }}</label>
              <a-input
                :model-value="localRule.choices"
                size="mini"
                placeholder="A,B,C"
                :disabled="disabled"
                @update:model-value="(v) => updateField('choices', v)"
              />
            </div>
          </a-col>

          <a-col v-if="showFixedValue" :span="24">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.fixedValue') }}</label>
              <a-input
                :model-value="localRule.fixedValue"
                size="mini"
                :disabled="disabled"
                @update:model-value="(v) => updateField('fixedValue', v)"
              />
            </div>
          </a-col>

          <a-col v-if="showFormat" :span="24">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.format') }}</label>
              <a-select
                :model-value="localRule.format"
                :placeholder="$t('api.mock.formatPlaceholder')"
                size="mini"
                allow-create
                allow-clear
                :disabled="disabled"
                @change="(v) => updateField('format', v as string)"
              >
                <a-option
                  v-for="fmt in formatPresets"
                  :key="fmt.value"
                  :value="fmt.value"
                  >{{ fmt.label }}</a-option
                >
              </a-select>
            </div>
          </a-col>

          <a-col v-if="showTimestampUnit" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.unit') }}</label>
              <a-select
                :model-value="localRule.format"
                size="mini"
                :placeholder="$t('api.mockAdv.ms')"
                allow-clear
                :disabled="disabled"
                @change="(v) => updateField('format', v as string)"
              >
                <a-option value="ms">{{ $t('api.mockAdv.msOption') }}</a-option>
                <a-option value="s">{{ $t('api.mockAdv.sOption') }}</a-option>
                <a-option value="ns">{{ $t('api.mockAdv.nsOption') }}</a-option>
              </a-select>
            </div>
          </a-col>

          <a-col v-if="showCaseType" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mock.caseType') }}</label>
              <a-select
                :model-value="localRule.caseType"
                size="mini"
                :disabled="disabled"
                @change="(v) => updateField('caseType', v as string)"
              >
                <a-option value="lower">{{ $t('api.mockAdv.lower') }}</a-option>
                <a-option value="upper">{{ $t('api.mockAdv.upper') }}</a-option>
                <a-option value="number">{{ $t('api.mockType.catNumber') }}</a-option>
                <a-option value="mixed">{{ $t('api.mockAdv.mixed') }}</a-option>
              </a-select>
            </div>
          </a-col>
          <a-col v-if="showCaseType" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mock.length') }}</label>
              <a-input-number
                :model-value="localRule.length"
                size="mini"
                :min="1"
                :max="1000"
                :disabled="disabled"
                @change="(v) => updateField('length', v as number)"
              />
            </div>
          </a-col>

          <a-col v-if="showLocale" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.locale') }}</label>
              <a-select
                :model-value="localRule.locale"
                size="mini"
                :disabled="disabled"
                @change="(v) => updateField('locale', v as string)"
              >
                <a-option value="zh">{{ $t('api.mock.localeZh') }}</a-option>
                <a-option value="en">{{ $t('api.mock.localeEn') }}</a-option>
              </a-select>
            </div>
          </a-col>

          <a-col v-if="showLength" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.randomLength') }}</label>
              <a-input-number
                :model-value="localRule.length"
                size="mini"
                :min="1"
                :max="1000"
                :disabled="disabled"
                @change="(v) => updateField('length', v as number)"
              />
            </div>
          </a-col>
          <a-col v-if="showLength" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.charset') }}</label>
              <a-input
                :model-value="localRule.charset"
                size="mini"
                placeholder="abcdefghijklmnopqrstuvwxyz0123456789"
                :disabled="disabled"
                @update:model-value="(v) => updateField('charset', v)"
              />
            </div>
          </a-col>

          <a-col v-if="showArrayConstraints" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.minItems') }}</label>
              <a-input-number
                :model-value="localRule.minItems"
                size="mini"
                :min="0"
                :max="1000"
                :disabled="disabled"
                @change="(v) => updateField('minItems', v as number)"
              />
            </div>
          </a-col>
          <a-col v-if="showArrayConstraints" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.maxItems') }}</label>
              <a-input-number
                :model-value="localRule.maxItems"
                size="mini"
                :min="0"
                :max="1000"
                :disabled="disabled"
                @change="(v) => updateField('maxItems', v as number)"
              />
            </div>
          </a-col>
          <a-col v-if="showArrayConstraints" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.uniqueItems') }}</label>
              <a-switch
                :model-value="localRule.uniqueItems"
                size="small"
                :disabled="disabled"
                @change="(v) => updateField('uniqueItems', v as boolean)"
              />
            </div>
          </a-col>

          <a-col v-if="showMinMaxLength" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.minLength') }}</label>
              <a-input-number
                :model-value="localRule.minLength"
                size="mini"
                :min="0"
                :disabled="disabled"
                @change="(v) => updateField('minLength', v as number)"
              />
            </div>
          </a-col>
          <a-col v-if="showMinMaxLength" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.maxLength') }}</label>
              <a-input-number
                :model-value="localRule.maxLength"
                size="mini"
                :min="0"
                :disabled="disabled"
                @change="(v) => updateField('maxLength', v as number)"
              />
            </div>
          </a-col>

          <a-col v-if="showPattern" :span="24">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.pattern') }}</label>
              <a-input
                :model-value="localRule.pattern"
                size="mini"
                :placeholder="$t('api.mockAdv.patternPlaceholder')"
                :disabled="disabled"
                @update:model-value="(v) => updateField('pattern', v)"
              />
            </div>
          </a-col>

          <a-col v-if="showMinMax" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mock.min') }}</label>
              <a-input-number
                :model-value="localRule.min"
                size="mini"
                :disabled="disabled"
                @change="(v) => updateField('min', v as number)"
              />
            </div>
          </a-col>
          <a-col v-if="showMinMax" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mock.max') }}</label>
              <a-input-number
                :model-value="localRule.max"
                size="mini"
                :disabled="disabled"
                @change="(v) => updateField('max', v as number)"
              />
            </div>
          </a-col>

          <a-col v-if="showScale" :span="12">
            <div class="form-item">
              <label>{{ $t('api.mockAdv.scale') }}</label>
              <a-input-number
                :model-value="localRule.scale"
                size="mini"
                :min="0"
                :max="10"
                :disabled="disabled"
                @change="(v) => updateField('scale', v as number)"
              />
            </div>
          </a-col>

          <a-col v-if="showDefaultValue" :span="24">
            <div class="form-item">
              <label>{{ $t('api.sqlExtraction.colDefaultValue') }}</label>
              <a-input
                :model-value="localRule.defaultValue"
                size="mini"
                :placeholder="$t('api.mockAdv.defaultValuePlaceholder')"
                :disabled="disabled"
                @update:model-value="(v) => updateField('defaultValue', v)"
              />
            </div>
          </a-col>

          <a-col v-if="isTemplate" :span="24">
            <div class="form-item">
              <label>{{ $t('api.responseSchema.hiddenFields') }}</label>
              <a-input-tag
                :model-value="localRule.excludedFields"
                size="mini"
                :placeholder="$t('api.mockAdv.excludedFieldsPlaceholder')"
                :disabled="disabled"
                allow-clear
                @update:model-value="(v) => updateField('excludedFields', v as string[])"
              />
            </div>
          </a-col>
        </a-row>
      </div>
    </template>
  </a-popover>
</template>

<script setup lang="ts">
  import { computed, reactive, watch } from 'vue';
  import { IconSettings } from '@arco-design/web-vue/es/icon';
  import { MockFieldRule } from '@/types/domain/api/requestModel/MockFieldRule';
  import { useI18n } from 'vue-i18n';

  interface Props {
    rule: MockFieldRule;
    disabled?: boolean;
  }

  const { t } = useI18n();

  const props = defineProps<Props>();

  const emit = defineEmits<{
    (e: 'update-field', field: keyof MockFieldRule, value: any): void;
  }>();

  const localRule = reactive<MockFieldRule>(new MockFieldRule());

  watch(
    () => props.rule,
    (newVal) => {
      Object.assign(localRule, newVal || new MockFieldRule());
    },
    { immediate: true, deep: true }
  );

  const fieldTypeOptions = [
    { label: 'string', value: 'STRING' },
    { label: 'int', value: 'INT' },
    { label: 'long', value: 'LONG' },
    { label: 'float', value: 'FLOAT' },
    { label: 'double', value: 'DOUBLE' },
    { label: 'boolean', value: 'BOOLEAN' },
    { label: 'Object', value: 'OBJECT' },
    { label: 'array', value: 'ARRAY' },
    { label: 'template', value: 'TEMPLATE' },
  ];

  const ruleTypeOptions = computed(() => [
    { label: t('api.mockType.cname'), value: 'cname', types: ['STRING'] },
    { label: t('api.mockType.ename'), value: 'ename', types: ['STRING'] },
    { label: t('api.mockType.name'), value: 'name', types: ['STRING'] },
    { label: t('api.mockType.phone'), value: 'phone', types: ['STRING'] },
    { label: t('api.mockType.email'), value: 'email', types: ['STRING'] },
    { label: t('api.mockType.idCard'), value: 'idCard', types: ['STRING'] },
    { label: t('api.mockType.bankcard'), value: 'bankcard', types: ['STRING'] },
    { label: t('api.mockType.character'), value: 'character', types: ['STRING'] },
    { label: t('api.mockType.text'), value: 'text', types: ['STRING'] },
    { label: 'UUID', value: 'uuid', types: ['STRING'] },
    { label: t('api.mockType.int'), value: 'int', types: ['INT'] },
    { label: t('api.mockType.long'), value: 'long', types: ['LONG'] },
    { label: t('api.mockType.float'), value: 'float', types: ['FLOAT'] },
    { label: t('api.mockType.double'), value: 'double', types: ['DOUBLE'] },
    { label: t('api.mockType.boolean'), value: 'boolean', types: ['BOOLEAN'] },
    { label: t('api.mockType.timestamp'), value: 'timestamp', types: ['STRING', 'INT', 'LONG'] },
    { label: t('api.mockType.date'), value: 'date', types: ['STRING'] },
    { label: t('api.mockType.datetime'), value: 'datetime', types: ['STRING'] },
    { label: t('api.mockType.time'), value: 'time', types: ['STRING'] },
    { label: t('api.mockType.choice'), value: 'choice', types: ['STRING'] },
    { label: t('api.mockType.company'), value: 'company', types: ['STRING'] },
    { label: t('api.mockType.address'), value: 'address', types: ['STRING'] },
    {
      label: t('api.mockType.fixed'),
      value: 'fixed',
      types: ['STRING', 'INT', 'LONG', 'FLOAT', 'DOUBLE', 'BOOLEAN'],
    },
    { label: t('api.mockType.null'), value: 'null', types: ['STRING', 'INT', 'LONG', 'FLOAT', 'DOUBLE', 'BOOLEAN', 'OBJECT'] },
  ]);

  const isContainer = computed(() => {
    const ft = localRule.fieldType;
    return ft === 'OBJECT' || ft === 'ARRAY';
  });

  const applicableRuleTypes = computed(() => {
    const ft = localRule.fieldType;
    if (!ft || ft === 'ARRAY' || ft === 'OBJECT') return [];
    return ruleTypeOptions.value.filter((rt) => rt.types.includes(ft));
  });

  const showChoices = computed(() => localRule.ruleType === 'choice');

  const showFixedValue = computed(
    () => localRule.isConstant && !isContainer.value
  );

  const showFormat = computed(() => {
    const rt = localRule.ruleType;
    return rt === 'date' || rt === 'datetime' || rt === 'time';
  });

  const formatPresets = computed(() => {
    switch (localRule.ruleType) {
      case 'date':
        return [
          { label: 'yyyy-MM-dd', value: 'yyyy-MM-dd' },
          { label: 'yyyy/MM/dd', value: 'yyyy/MM/dd' },
          { label: 'yyyyMMdd', value: 'yyyyMMdd' },
          { label: 'yyyy年MM月dd日', value: 'yyyy年MM月dd日' },
        ];
      case 'time':
        return [
          { label: 'HH:mm:ss', value: 'HH:mm:ss' },
          { label: 'HH:mm', value: 'HH:mm' },
          { label: 'HH时mm分ss秒', value: 'HH时mm分ss秒' },
          { label: 'HHmmss', value: 'HHmmss' },
        ];
      case 'datetime':
      default:
        return [
          { label: 'yyyy-MM-dd HH:mm:ss', value: 'yyyy-MM-dd HH:mm:ss' },
          { label: 'yyyy/MM/dd HH:mm:ss', value: 'yyyy/MM/dd HH:mm:ss' },
          { label: "yyyy-MM-dd'T'HH:mm:ss'Z'", value: "yyyy-MM-dd'T'HH:mm:ss'Z'" },
          { label: 'yyyy-MM-dd HH:mm:ss.SSS', value: 'yyyy-MM-dd HH:mm:ss.SSS' },
        ];
    }
  });

  const showLength = computed(() => localRule.ruleType === 'text');

  const showLocale = computed(() =>
    ['name', 'company', 'address'].includes(localRule.ruleType || '')
  );

  const showArrayConstraints = computed(() => localRule.fieldType === 'ARRAY');

  const showMinMaxLength = computed(() =>
    ['text', 'character'].includes(localRule.ruleType || '')
  );

  const showTimestampUnit = computed(() => localRule.ruleType === 'timestamp');

  const showCaseType = computed(() => localRule.ruleType === 'character');

  const showPattern = computed(() => localRule.fieldType === 'STRING');

  const showMinMax = computed(() => {
    const ft = localRule.fieldType;
    return ['INT', 'LONG', 'FLOAT', 'DOUBLE'].includes(ft || '');
  });

  const showScale = computed(() => {
    const ft = localRule.fieldType;
    return ft === 'FLOAT' || ft === 'DOUBLE';
  });

  const showDefaultValue = computed(() => !isContainer.value);

  const isTemplate = computed(() => localRule.fieldType === 'TEMPLATE');

  function updateField(field: keyof MockFieldRule, value: any) {
    (localRule as any)[field] = value;
    emit('update-field', field, value);
  }

  function onVisibleChange(visible: boolean) {
    if (!visible) {
      Object.assign(localRule, props.rule || new MockFieldRule());
    }
  }
</script>

<style scoped>
  .mock-advanced-form label {
    display: block;
    font-size: 12px;
    color: #666;
    margin-bottom: 4px;
  }

  .mock-advanced-form :deep(.arco-input-wrapper),
  .mock-advanced-form :deep(.arco-select),
  .mock-advanced-form :deep(.arco-input-number) {
    width: 100%;
  }
</style>
