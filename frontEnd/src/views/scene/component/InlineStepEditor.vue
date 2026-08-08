<template>
  <!--
    节点内联表单：直接复用抽屉的 DynamicForm + STEP_REGISTRY[stepType] 配置，
    保证「字段、label、条件联动、元素选择器」等交互逻辑与抽屉完全一致。
    注意：STEP_REGISTRY 中只包含主表单字段，不含「设置 / 关联提取 / 断言」
    （那些来自 StepSetting / PostExtract / PostAssert，由抽屉的 SettingTab 单独承载），
    因此这里天然就排除了抽屉底部的那三块内容。
  -->
  <div
      v-if="formFields.length > 0"
      class="inline-step-form"
      @click.stop
      @mousedown.stop
      @dblclick.stop
  >
    <DynamicForm
        :fields="formFields"
        v-model="localDetail"
        :disabled="!editable"
        compact-element-select
        @change="handleChange"
    />
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onMounted, ref, watch} from 'vue';
import DynamicForm from './DynamicForm.vue';
import {STEP_REGISTRY} from "@/schema/stepFormConfig/FormConfig";

interface Props {
  stepType?: string;
  stepDetail?: string | Record<string, any>;
  editable?: boolean;
}

const props = defineProps<Props>();
const emits = defineEmits<{
  (e: 'update:stepDetail', detail: Record<string, any>): void;
  (e: 'dirty', dirty: boolean): void;
}>();

// 解析外部传入的步骤详情（兼容字符串 / 对象）
const parsedDetail = computed(() => {
  try {
    return typeof props.stepDetail === 'string'
        ? JSON.parse(props.stepDetail || '{}')
        : (props.stepDetail || {});
  } catch (e) {
    return {};
  }
});

// 字段配置：与抽屉主表单完全一致
const formFields = computed(() => {
  const registry = STEP_REGISTRY as Record<string, any[]>;
  return registry[props.stepType || ''] || [];
});

// 本地完整副本（含 assertList / extractList / setting，保存时一并回写，避免丢失）
const localDetail = ref<Record<string, any>>({});

// 外部数据基线：用于判断「外部更新」与「自身编辑」
let lastSyncJson = '';
// 已提交基线：包含 DynamicForm 初始化补齐的默认值，作为判断 dirty 的对照
// （否则仅聚焦输入框再失焦、或默认值补齐都会被误判为修改）
let committedJson = '';

const syncFromProps = () => {
  const clone = JSON.parse(JSON.stringify(parsedDetail.value));
  localDetail.value = clone;
  lastSyncJson = JSON.stringify(clone);
  // DynamicForm 会在初始化时补默认值，等其结算后再记基线并清除 dirty
  nextTick(() => {
    committedJson = JSON.stringify(localDetail.value);
    emits('dirty', false);
  });
};

// 外部数据变化时同步本地副本；仅当确实不同才重置，避免覆盖正在编辑的内容
watch(parsedDetail, () => {
  const incoming = JSON.stringify(parsedDetail.value);
  if (incoming !== lastSyncJson) {
    syncFromProps();
  }
}, {immediate: true, deep: true});

onMounted(() => {
  // 兜底：确保基线在 DynamicForm 默认值补齐后再记录一次
  nextTick(() => {
    committedJson = JSON.stringify(localDetail.value);
  });
});

// 用户编辑：与已提交基线对比判断是否真的有改动，再决定 dirty 与草稿
const handleChange = (newData: Record<string, any>) => {
  localDetail.value = {...newData};
  const current = JSON.stringify(localDetail.value);
  const changed = current !== committedJson;
  emits('dirty', changed);
  emits('update:stepDetail', JSON.parse(JSON.stringify(localDetail.value)));
};
</script>

<style scoped>
.inline-step-form {
  width: 100%;
  margin-top: 6px;
  cursor: default;
}

/* 紧凑化抽屉表单在节点上的展示 */
.inline-step-form :deep(.arco-form-item) {
  margin-bottom: 8px;
}

.inline-step-form :deep(.arco-form-item-label-col) {
  padding-bottom: 2px;
}

.inline-step-form :deep(.arco-form-item-label) {
  font-size: 12px;
  color: #86909c;
  line-height: 18px;
}

.inline-step-form :deep(.arco-input),
.inline-step-form :deep(.arco-select-view),
.inline-step-form :deep(.arco-input-number) {
  font-size: 12px;
}
</style>
