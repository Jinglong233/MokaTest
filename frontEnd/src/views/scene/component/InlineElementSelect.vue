<!--
  节点内联场景下的「元素选择」紧凑展示：
  - 平时只显示一个摘要标签（目标元素：xxx）
  - 点击弹出与抽屉完全一致的 ElementSelect 选择器进行编辑
  使用 a-modal 作为容器（宽度与抽屉一致），保证「选择元素 / 自定义元素」两个 tab
  的目录树、元素列表都能正常渲染，避免在窄弹层里错位。
  仅用于工作流节点，抽屉中仍使用完整内联的 ElementSelect。
-->
<template>
  <div class="inline-element-trigger" :class="{ disabled }" @click.stop="open" @mousedown.stop>
    <span class="inline-element-text" :title="summary">{{ summary }}</span>
    <icon-edit v-if="!disabled" class="inline-element-edit"/>
  </div>

  <a-modal
      v-model:visible="visible"
      title="选择元素"
      :width="640"
      :mask-closable="true"
      ok-text="完成"
      hide-cancel
      @ok="visible = false"
  >
    <ElementSelect
        v-if="visible"
        v-model="innerValue"
        project-id="1"
        :disabled="disabled"
        @change="handleChange"
    />
  </a-modal>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {IconEdit} from '@arco-design/web-vue/es/icon';
import ElementSelect from './ElementSelect.vue';

interface Props {
  modelValue?: any;
  disabled?: boolean;
}

const props = defineProps<Props>();
const emits = defineEmits<{
  (e: 'update:modelValue', value: any): void;
  (e: 'change', value: any): void;
}>();

const visible = ref(false);

const open = () => {
  if (props.disabled) return;
  visible.value = true;
};

const innerValue = computed({
  get: () => props.modelValue,
  set: (val) => emits('update:modelValue', val),
});

const handleChange = () => {
  emits('change', props.modelValue);
};

// 摘要文案：「定位方式-定位值」，如 css-#loginBtn
// 库选元素取 locator，自定义元素取 customLocator
const summary = computed(() => {
  const v = props.modelValue;
  if (!v || typeof v !== 'object') return '未选择元素';

  const pick = (loc: any): string => {
    const value = loc?.locatorValue;
    if (!value || !String(value).trim()) return '';
    const type = loc?.locatorType ? `${String(loc.locatorType).toLowerCase()}-` : '';
    return `${type}${String(value).trim()}`;
  };

  return pick(v.locator) || pick(v.customLocator) || '未选择元素';
});
</script>

<style scoped>
.inline-element-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-height: 30px;
  padding: 2px 8px;
  border: 1px solid var(--color-neutral-3);
  border-radius: 4px;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s ease;
}

.inline-element-trigger:hover {
  border-color: rgb(var(--arcoblue-6));
}

.inline-element-trigger.disabled {
  cursor: not-allowed;
  background: var(--color-fill-2);
}

.inline-element-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-text-1);
}

.inline-element-edit {
  flex-shrink: 0;
  color: var(--color-text-3);
  font-size: 14px;
}
</style>
