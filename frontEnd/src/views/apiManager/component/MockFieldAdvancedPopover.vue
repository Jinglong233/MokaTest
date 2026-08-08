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
      高级
    </a-button>
    <template #content>
      <div class="mock-advanced-form">
        <a-row :gutter="[16, 12]">
          <a-col :span="12">
            <div class="form-item">
              <label>数据类型</label>
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
              <label>规则类型</label>
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
              <label>可空</label>
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
              <label>必填</label>
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
              <label>常量</label>
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
              <label>枚举选项（逗号分隔）</label>
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
              <label>常量/固定值</label>
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
              <label>格式</label>
              <a-select
                :model-value="localRule.format"
                placeholder="选择或输入格式"
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
              <label>单位</label>
              <a-select
                :model-value="localRule.format"
                size="mini"
                placeholder="毫秒"
                allow-clear
                :disabled="disabled"
                @change="(v) => updateField('format', v as string)"
              >
                <a-option value="ms">毫秒（ms）</a-option>
                <a-option value="s">秒（s）</a-option>
                <a-option value="ns">纳秒（ns）</a-option>
              </a-select>
            </div>
          </a-col>

          <a-col v-if="showCaseType" :span="12">
            <div class="form-item">
              <label>字符类型</label>
              <a-select
                :model-value="localRule.caseType"
                size="mini"
                :disabled="disabled"
                @change="(v) => updateField('caseType', v as string)"
              >
                <a-option value="lower">小写</a-option>
                <a-option value="upper">大写</a-option>
                <a-option value="number">数字</a-option>
                <a-option value="mixed">混合</a-option>
              </a-select>
            </div>
          </a-col>
          <a-col v-if="showCaseType" :span="12">
            <div class="form-item">
              <label>长度</label>
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
              <label>语言</label>
              <a-select
                :model-value="localRule.locale"
                size="mini"
                :disabled="disabled"
                @change="(v) => updateField('locale', v as string)"
              >
                <a-option value="zh">中文</a-option>
                <a-option value="en">英文</a-option>
              </a-select>
            </div>
          </a-col>

          <a-col v-if="showLength" :span="12">
            <div class="form-item">
              <label>随机长度</label>
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
              <label>字符集</label>
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
              <label>最小元素个数</label>
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
              <label>最大元素个数</label>
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
              <label>元素唯一</label>
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
              <label>最小长度（配置后按区间随机长度，优先于固定长度）</label>
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
              <label>最大长度</label>
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
              <label>正则表达式</label>
              <a-input
                :model-value="localRule.pattern"
                size="mini"
                placeholder="^\\d+$（配置后优先生效，忽略规则类型）"
                :disabled="disabled"
                @update:model-value="(v) => updateField('pattern', v)"
              />
            </div>
          </a-col>

          <a-col v-if="showMinMax" :span="12">
            <div class="form-item">
              <label>最小值</label>
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
              <label>最大值</label>
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
              <label>精度（小数位）</label>
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
              <label>默认值</label>
              <a-input
                :model-value="localRule.defaultValue"
                size="mini"
                placeholder="生成失败或规则不匹配时的兜底值"
                :disabled="disabled"
                @update:model-value="(v) => updateField('defaultValue', v)"
              />
            </div>
          </a-col>

          <a-col v-if="isTemplate" :span="24">
            <div class="form-item">
              <label>隐藏字段</label>
              <a-input-tag
                :model-value="localRule.excludedFields"
                size="mini"
                placeholder="从模板中剔除的字段名，回车添加"
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

  interface Props {
    rule: MockFieldRule;
    disabled?: boolean;
  }

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

  const ruleTypeOptions = [
    { label: '中文姓名', value: 'cname', types: ['STRING'] },
    { label: '英文姓名', value: 'ename', types: ['STRING'] },
    { label: '姓名', value: 'name', types: ['STRING'] },
    { label: '手机号', value: 'phone', types: ['STRING'] },
    { label: '邮箱', value: 'email', types: ['STRING'] },
    { label: '身份证号', value: 'idCard', types: ['STRING'] },
    { label: '银行卡号', value: 'bankcard', types: ['STRING'] },
    { label: '随机字符', value: 'character', types: ['STRING'] },
    { label: '随机字符串', value: 'text', types: ['STRING'] },
    { label: 'UUID', value: 'uuid', types: ['STRING'] },
    { label: '整数', value: 'int', types: ['INT'] },
    { label: '长整数', value: 'long', types: ['LONG'] },
    { label: '浮点数', value: 'float', types: ['FLOAT'] },
    { label: '双精度', value: 'double', types: ['DOUBLE'] },
    { label: '布尔值', value: 'boolean', types: ['BOOLEAN'] },
    { label: '时间戳', value: 'timestamp', types: ['STRING', 'INT', 'LONG'] },
    { label: '日期', value: 'date', types: ['STRING'] },
    { label: '日期时间', value: 'datetime', types: ['STRING'] },
    { label: '时间', value: 'time', types: ['STRING'] },
    { label: '枚举选择', value: 'choice', types: ['STRING'] },
    { label: '公司名', value: 'company', types: ['STRING'] },
    { label: '地址', value: 'address', types: ['STRING'] },
    {
      label: '固定值',
      value: 'fixed',
      types: ['STRING', 'INT', 'LONG', 'FLOAT', 'DOUBLE', 'BOOLEAN'],
    },
    { label: '空值', value: 'null', types: ['STRING', 'INT', 'LONG', 'FLOAT', 'DOUBLE', 'BOOLEAN', 'OBJECT'] },
  ];

  const isContainer = computed(() => {
    const ft = localRule.fieldType;
    return ft === 'OBJECT' || ft === 'ARRAY';
  });

  const applicableRuleTypes = computed(() => {
    const ft = localRule.fieldType;
    if (!ft || ft === 'ARRAY' || ft === 'OBJECT') return [];
    return ruleTypeOptions.filter((rt) => rt.types.includes(ft));
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
