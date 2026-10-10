<template>
  <div
    class="mock-rule-item"
    :class="{
      'is-root': isRoot,
      'is-disabled': disabled,
      'is-container': rule.fieldType === 'OBJECT' || rule.fieldType === 'ARRAY'
    }"
    :style="{ '--depth': depth }"
  >
    <!-- 拖拽手柄（根节点不显示） -->
    <div v-if="!isRoot" class="col-drag">
      <icon-drag-dot-vertical />
    </div>
    <div v-else class="col-drag"></div>

    <!-- 字段名 -->
    <div class="col-name">
      <span v-if="isRoot" class="root-label">{{ rootLabel || $t('api.responseSchema.rootLabel') }}</span>
      <a-input
        v-else
        :model-value="rule.fieldName"
        :placeholder="$t('api.mockField.namePlaceholder')"
        size="mini"
        :disabled="disabled"
        @update:model-value="(v) => updateField('fieldName', v)"
      />
    </div>

    <!-- 类型 -->
    <div class="col-type">
      <a-select
        :model-value="rule.fieldType"
        :placeholder="$t('api.mockField.typePlaceholder')"
        size="mini"
        :disabled="disabled"
        class="type-select"
        @change="(v) => onFieldTypeChange(v as string)"
      >
        <a-option
          v-for="opt in fieldTypeOptions"
          :key="opt.value"
          :value="opt.value"
          >{{ opt.label }}</a-option
        >
      </a-select>
      <!-- 当数据类型为“数据模板”时，类型列内联显示模板选择下拉 -->
      <a-select
        v-if="rule.fieldType === 'TEMPLATE'"
        :model-value="rule.templateId"
        :placeholder="$t('api.template.selectTemplate')"
        size="mini"
        :disabled="disabled"
        class="template-select"
        @change="(v) => updateField('templateId', v as number)"
      >
        <a-option
          v-for="tmpl in availableTemplateList"
          :key="tmpl.id"
          :value="tmpl.id"
          >{{ tmpl.templateName }}</a-option
        >
      </a-select>
    </div>

    <!-- 必填 * -->
    <div class="col-bool required-col">
      <span
        class="bool-icon"
        :class="{ active: rule.required, disabled: disabled }"
        @click="toggleRequired"
        >*</span
      >
    </div>

    <!-- 可空 N -->
    <div class="col-bool nullable-col">
      <span
        class="bool-icon null"
        :class="{ active: rule.nullable, disabled: disabled }"
        @click="toggleNullable"
        >N</span
      >
    </div>

    <!-- Mock 数据 / 规则 -->
    <div class="col-rule">
      <!-- 对象 -->
      <template v-if="rule.fieldType === 'OBJECT'">
        <a-tag size="small" color="arcoblue">Object</a-tag>
      </template>

      <!-- 数据模板引用：Mock 值由所选模板决定，这里禁用占位 -->
      <template v-else-if="rule.fieldType === 'TEMPLATE'">
        <a-input
          :placeholder="$t('api.mockField.templatePlaceholder')"
          size="mini"
          disabled
          class="template-mock-placeholder"
        />
      </template>

      <!-- 数组 -->
      <template v-else-if="rule.fieldType === 'ARRAY'">
        <a-tag size="small" color="arcoblue">Array[]</a-tag>
      </template>

      <!-- 标量 -->
      <template v-else-if="!isRoot">
        <a-cascader
          v-model="selectedCategoryPath"
          :options="cascaderOptions"
          :placeholder="$t('api.mockField.rulePlaceholder')"
          size="mini"
          :disabled="disabled"
          allow-search
          style="width: 200px"
        />
        <RuleParams
          :rule="rule"
          :disabled="disabled"
          @update-field="updateField"
        />
      </template>
    </div>

    <!-- 描述 -->
    <div class="col-desc">
      <a-input
        :model-value="rule.description"
        :placeholder="$t('api.sqlAssertion.descPlaceholder')"
        size="mini"
        :disabled="disabled"
        @update:model-value="(v) => updateField('description', v)"
      />
    </div>

    <!-- 高级设置 -->
    <div class="col-advanced">
      <MockFieldAdvancedPopover
        :rule="rule"
        :disabled="disabled"
        @update-field="updateField"
      />
    </div>

    <!-- 操作 -->
    <div class="col-ops">
      <a-space size="mini">
        <a-tooltip :content="$t('api.mockField.addChild')">
          <a-button
            v-if="canAddChild"
            type="text"
            size="mini"
            :disabled="disabled"
            @click="$emit('add-child')"
          >
            <template #icon>
              <icon-plus-circle />
            </template>
          </a-button>
        </a-tooltip>
        <a-tooltip v-if="!isRoot" :content="$t('api.mockField.addSibling')">
          <a-button
            type="text"
            size="mini"
            :disabled="disabled"
            @click="$emit('add-sibling')"
          >
            <template #icon>
              <icon-plus />
            </template>
          </a-button>
        </a-tooltip>
        <a-tooltip v-if="!isRoot" :content="$t('qa.common.delete')">
          <a-button
            type="text"
            status="danger"
            size="mini"
            :disabled="disabled"
            @click="$emit('delete')"
          >
            <template #icon>
              <icon-minus-circle />
            </template>
          </a-button>
        </a-tooltip>
      </a-space>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { useI18n } from 'vue-i18n';
  import {
    IconPlusCircle,
    IconMinusCircle,
    IconPlus,
    IconDragDotVertical,
  } from '@arco-design/web-vue/es/icon';
  import { MockFieldRule } from '@/types/domain/api/requestModel/MockFieldRule';
  import { DataTemplate } from '@/types/domain/api/DataTemplate';
  import { ParameterType } from '@/types/domain/api/apiEnum/ParameterType';
  import RuleParams from './RuleParams.vue';
  import MockFieldAdvancedPopover from './MockFieldAdvancedPopover.vue';

  // Mock 字段类型 -> API 参数类型 映射（用于过滤 mockCategories）
  const FIELD_TYPE_TO_PARAM: Record<string, ParameterType> = {
    STRING: ParameterType.STRING,
    INT: ParameterType.INTEGER,
    LONG: ParameterType.INTEGER,
    FLOAT: ParameterType.NUMBER,
    DOUBLE: ParameterType.NUMBER,
    BOOLEAN: ParameterType.BOOLEAN,
  };

  const { t } = useI18n();

  // 复用 API 测试的分类定义，补充数据模板专属的 template / null
  const mockCategories = computed(() => [
    {
      value: 'basic',
      label: t('api.mockType.catBasic'),
      children: [
        {value: 'fixed', label: t('api.mockType.fixed'), types: [ParameterType.STRING, ParameterType.INTEGER, ParameterType.NUMBER, ParameterType.BOOLEAN, ParameterType.JSON, ParameterType.ARRAY]},
        {value: 'choice', label: t('api.mockType.choice'), types: [ParameterType.STRING, ParameterType.INTEGER, ParameterType.NUMBER, ParameterType.BOOLEAN, ParameterType.JSON, ParameterType.ARRAY]},
        {value: 'boolean', label: t('api.mockType.boolean'), types: [ParameterType.STRING, ParameterType.BOOLEAN]},
        {value: 'timestamp', label: t('api.mockType.timestamp'), types: [ParameterType.STRING]},
      ],
    },
    {
      value: 'string',
      label: t('api.mockType.catString'),
      children: [
        {value: 'text', label: t('api.mockType.text'), types: [ParameterType.STRING]},
        {value: 'character', label: t('api.mockType.character'), types: [ParameterType.STRING]},
        {value: 'uuid', label: 'UUID', types: [ParameterType.STRING]},
      ],
    },
    {
      value: 'personal',
      label: t('api.mockType.catPersonal'),
      children: [
        {value: 'name', label: t('api.mockType.name'), types: [ParameterType.STRING]},
        {value: 'cname', label: t('api.mockType.cname'), types: [ParameterType.STRING]},
        {value: 'ename', label: t('api.mockType.ename'), types: [ParameterType.STRING]},
        {value: 'phone', label: t('api.mockType.phone'), types: [ParameterType.STRING]},
        {value: 'email', label: t('api.mockType.email'), types: [ParameterType.STRING]},
        {value: 'idCard', label: t('api.mockType.idCard'), types: [ParameterType.STRING]},
        {value: 'bankcard', label: t('api.mockType.bankcard'), types: [ParameterType.STRING]},
      ],
    },
    {
      value: 'organization',
      label: t('api.mockType.catOrg'),
      children: [
        {value: 'company', label: t('api.mockType.company'), types: [ParameterType.STRING]},
        {value: 'address', label: t('api.mockType.address'), types: [ParameterType.STRING]},
      ],
    },
    {
      value: 'number',
      label: t('api.mockType.catNumber'),
      children: [
        {value: 'int', label: t('api.mockType.int'), types: [ParameterType.STRING, ParameterType.INTEGER, ParameterType.NUMBER]},
        {value: 'long', label: t('api.mockType.long'), types: [ParameterType.STRING, ParameterType.INTEGER, ParameterType.NUMBER]},
        {value: 'float', label: t('api.mockType.float'), types: [ParameterType.STRING, ParameterType.NUMBER]},
        {value: 'double', label: t('api.mockType.double'), types: [ParameterType.STRING, ParameterType.NUMBER]},
      ],
    },
    {
      value: 'datetime',
      label: t('api.mockType.catDatetime'),
      children: [
        {value: 'date', label: t('api.mockType.date'), types: [ParameterType.STRING]},
        {value: 'datetime', label: t('api.mockType.datetime'), types: [ParameterType.STRING]},
        {value: 'time', label: t('api.mockType.time'), types: [ParameterType.STRING]},
      ],
    },
    {
      value: 'extended',
      label: t('api.mockType.catExtended'),
      children: [
        {value: 'null', label: t('api.mockType.null'), types: [ParameterType.STRING, ParameterType.INTEGER, ParameterType.NUMBER, ParameterType.BOOLEAN, ParameterType.JSON]},
      ],
    },
  ]);

  interface Props {
    rule: MockFieldRule;
    depth: number;
    templateList: DataTemplate[];
    disabled?: boolean;
    isRoot?: boolean;
    rootLabel?: string;
    excludeTemplateId?: number;
  }

  const props = withDefaults(defineProps<Props>(), {
    disabled: false,
    isRoot: false,
    rootLabel: '',
  });

  const emit = defineEmits<{
    (e: 'update-field', field: keyof MockFieldRule, value: any): void;
    (e: 'field-type-change', type: string): void;
    (e: 'rule-type-change', type: string): void;
    (e: 'add-child'): void;
    (e: 'add-sibling'): void;
    (e: 'delete'): void;
  }>();

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

  const canAddChild = computed(() => {
    return (
      props.rule.fieldType === 'OBJECT' ||
      props.rule.fieldType === 'ARRAY' ||
      // TEMPLATE 节点允许添加子字段作为覆盖字段
      props.rule.fieldType === 'TEMPLATE'
    );
  });

  const availableTemplateList = computed(() => {
    return props.templateList.filter((tmpl) => tmpl.id !== props.excludeTemplateId);
  });

  // 根据当前字段类型过滤出可用的级联选项（只保留有适用子项的分类）
  const cascaderOptions = computed(() => {
    const paramType = FIELD_TYPE_TO_PARAM[props.rule.fieldType || 'STRING'];
    if (paramType === undefined) return [];
    return mockCategories.value
      .map((category) => ({
        value: category.value,
        label: category.label,
        children: category.children.filter((item) => item.types.includes(paramType)),
      }))
      .filter((category) => category.children.length > 0);
  });

  // cascader v-model：[分类value, 规则value] <-> rule.ruleType
  const selectedCategoryPath = computed({
    get: () => {
      const type = props.rule.ruleType;
      if (!type) return [];
      for (const category of mockCategories.value) {
        if (category.children.some((item) => item.value === type)) {
          return [category.value, type];
        }
      }
      return [];
    },
    set: (val: any) => {
      if (Array.isArray(val) && val.length >= 2) {
        const newType = String(val[val.length - 1]);
        emit('rule-type-change', newType);
      } else if (typeof val === 'string' && val) {
        emit('rule-type-change', val);
      }
    },
  });

  function updateField(field: keyof MockFieldRule, value: any) {
    emit('update-field', field, value);
  }

  function toggleRequired() {
    if (props.disabled) return;
    updateField('required', !props.rule.required);
  }

  function toggleNullable() {
    if (props.disabled) return;
    updateField('nullable', !props.rule.nullable);
  }

  function onFieldTypeChange(type: string) {
    emit('field-type-change', type);
  }
</script>

<style scoped>
  .mock-rule-item {
    display: flex;
    align-items: center;
    width: 100%;
    box-sizing: border-box;
    padding: 6px 12px;
    transition: background 0.15s;
  }

  .mock-rule-item.is-root {
    font-weight: 500;
  }

  .mock-rule-item.is-container {
    background: var(--color-fill-1);
    border-radius: 4px;
  }

  .mock-rule-item.is-disabled {
    opacity: 0.7;
  }

  .root-label {
    color: #666;
    font-size: 13px;
  }

  .col-drag {
    width: 20px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: #c9cdd4;
    cursor: grab;
  }

  .col-drag :deep(svg) {
    width: 14px;
    height: 14px;
  }

  .col-name {
    width: 200px;
    flex-shrink: 0;
    margin-left: 4px;
  }

  .col-type {
    width: 220px;
    flex-shrink: 0;
    margin-left: 8px;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  /* 类型下拉：TEMPLATE 时收窄到 80px，给模板下拉留位置；其余占满 */
  .col-type .type-select {
    flex: 1;
    min-width: 0;
  }

  .col-type:has(.template-select) .type-select {
    flex: 0 0 80px;
  }

  .col-type .template-select {
    flex: 1;
    min-width: 0;
  }

  .col-bool {
    width: 28px;
    flex-shrink: 0;
    margin-left: 8px;
    display: flex;
    justify-content: center;
    align-items: center;
  }

  .bool-icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 20px;
    height: 20px;
    border-radius: 4px;
    font-size: 14px;
    font-weight: bold;
    color: #c9cdd4;
    cursor: pointer;
    user-select: none;
    transition: all 0.15s;
  }

  .bool-icon:hover:not(.disabled) {
    background: #f2f3f5;
  }

  .bool-icon.active {
    color: #f53f3f;
  }

  /* 可空图标激活时用橙色，与必填的红色区分 */
  .bool-icon.null.active {
    color: #ff7d00;
  }

  .bool-icon.disabled {
    cursor: not-allowed;
    opacity: 0.6;
  }

  .col-rule {
    flex: 1;
    min-width: 180px;
    margin-left: 8px;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
  }

  .col-desc {
    width: 180px;
    flex-shrink: 0;
    margin-left: 8px;
  }

  .col-advanced {
    width: 50px;
    flex-shrink: 0;
    margin-left: 8px;
    display: flex;
    justify-content: center;
  }

  .col-ops {
    width: 110px;
    flex-shrink: 0;
    margin-left: 8px;
    display: flex;
    justify-content: flex-end;
  }

  :deep(.arco-input-wrapper),
  :deep(.arco-select),
  :deep(.arco-input-number) {
    width: 100%;
  }
</style>
