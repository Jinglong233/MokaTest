<template>
  <div
      class="workflow-step-node"
      :class="{
      'is-selected': selected,
      'is-disabled': isDisabled,
      'is-container': isContainer,
      'is-dirty': isDirty,
      'status-success': debugStatus === 'SUCCESS',
      'status-failure': debugStatus === 'FAILURE',
      'status-skipped': debugStatus === 'SKIPPED',
      'status-running': isRunning,
    }"
  >
    <!-- dirty 标记 -->
    <div v-if="isDirty" class="dirty-dot"/>

    <!-- 左侧连接桩 -->
    <Handle type="target" :position="Position.Left" class="workflow-handle"/>

    <!-- 头部：图标 + 类型 + 序号 + 设置/调试状态（拖拽与打开抽屉的热区） -->
    <div class="node-header" @click="handleClick">
      <div class="node-icon">
        <component :is="stepIcon" :style="{ fontSize: '16px' }"/>
      </div>
      <a-tag :color="stepTagColor" size="small" class="node-type-tag">
        {{ stepTypeChinese }}
      </a-tag>
      <span class="node-order">#{{ data.orderIndex }}</span>

      <div class="node-header-right">
        <!-- 调试状态 -->
        <span class="node-status" v-if="hasDebugResult || isRunning" @click.stop="handleViewResult">
          <a-spin v-if="isRunning" :size="14"/>
          <icon-check-circle-fill v-else-if="debugStatus === 'SUCCESS'" style="color: #3cc071; font-size: 16px;"/>
          <icon-close-circle-fill v-else-if="debugStatus === 'FAILURE'" style="color: #f53f3f; font-size: 16px;"/>
          <icon-minus-circle v-else-if="debugStatus === 'SKIPPED'" style="color: #86909c; font-size: 16px;"/>
        </span>
        <!-- 打开抽屉：编辑「设置 / 关联提取 / 断言」等节点上不展示的内容 -->
        <a-tooltip content="更多设置（设置 / 关联提取 / 断言）">
          <icon-settings class="node-setting-btn" @click.stop="handleClick"/>
        </a-tooltip>
      </div>
    </div>

    <!-- 步骤名称（仅当主表单无字段，如 API 请求步骤时展示，其余由内联表单的「步骤名称」字段承载） -->
    <a-typography-text v-if="!hasInlineFields" class="node-name" :ellipsis="{rows: 1}">
      {{ data.stepName || '未命名步骤' }}
    </a-typography-text>

    <!-- 内联必填项编辑：复用抽屉的字段与交互逻辑 -->
    <InlineStepEditor
        v-if="hasInlineFields"
        :key="editorKey"
        :step-type="data.stepType"
        :step-detail="data.stepDetail"
        :editable="isEditable"
        @update:step-detail="handleStepDetailChange"
        @dirty="handleDetailDirty"
    />

    <div class="node-meta-row">
      <a-tooltip v-if="extractVars.length > 0" :content="`提取变量：${extractVars.map(v => '${' + v + '}').join(' ')}`">
        <a-tag color="arcoblue" size="small" class="meta-tag">
          <template #icon><icon-export/></template>
          {{ extractVars.length }}
        </a-tag>
      </a-tooltip>
      <a-tag v-if="isDisabled" color="gray" size="small" class="meta-tag">已禁用</a-tag>
      <a-tag v-if="isDirty" color="orange" size="small" class="meta-tag">未保存</a-tag>

      <!-- 显式保存 / 撤销 -->
      <div v-if="isEditable && isDirty" class="node-actions" @click.stop @mousedown.stop>
        <a-button size="mini" type="primary" :loading="saving" @click="handleSave">保存</a-button>
        <a-button size="mini" :disabled="saving" @click="handleRevert">撤销</a-button>
      </div>
    </div>

    <!-- 右侧连接桩 -->
    <Handle type="source" :position="Position.Right" class="workflow-handle"/>
  </div>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {Handle, Position} from '@vue-flow/core';
import {
  IconCode,
  IconCodeSandbox,
  IconDelete,
  IconDesktop,
  IconExport,
  IconEye,
  IconPause,
  IconRefresh,
  IconSafe,
  IconSettings,
  IconStorage,
  IconSwap,
  IconTool,
} from '@arco-design/web-vue/es/icon';
import {getStepTypeChinese} from "@/types/enum/StepType";
import {updateStep} from "@/api/MyApi/step";
import {TestStep} from "@/types/domain/TestStep";
import {Message} from "@arco-design/web-vue";
import InlineStepEditor from "./InlineStepEditor.vue";
import {STEP_REGISTRY} from "@/schema/stepFormConfig/FormConfig";
import type {StepVO} from "@/types/vo/StepVO";

interface StepNodeData extends StepVO {
  isDebugging?: boolean;
  stepDebugList?: any[];
  isDisable?: number;
  stepDetail?: string | Record<string, any>;
}

interface Props {
  id: string;
  data: StepNodeData;
  selected?: boolean;
  editable?: boolean;
}

const props = defineProps<Props>();
const emits = defineEmits<{
  (e: 'nodeClick', step: StepVO): void;
  (e: 'viewResult', step: StepVO): void;
  (e: 'change', step: StepVO): void;
  (e: 'dirty', nodeId: string, dirty: boolean): void;
}>();

const isContainer = computed(() => {
  const t = props.data.stepType;
  return t === 'IF' || t === 'FOR' || t === 'WHILE';
});

const isDisabled = computed(() => props.data.isDisable === 1);

const isEditable = computed(() => !!props.editable && !isTempNodeId(props.data.id));

// 是否有可在节点上内联编辑的主表单字段：注册表里有字段才内联编辑，
// 否则（API 请求步骤、或未注册的类型）退化为仅展示名称，走抽屉编辑，避免空白节点
const hasInlineFields = computed(() => {
  const registry = STEP_REGISTRY as Record<string, any[]>;
  return (registry[props.data.stepType || '']?.length ?? 0) > 0;
});

const stepTypeChinese = computed(() => getStepTypeChinese(props.data.stepType as any));

const stepTagColor = computed(() => {
  switch (props.data.stepType) {
    case 'API_REQUEST':
      return 'green';
    case 'SQL':
      return 'blue';
    case 'SCRIPT':
      return 'orangered';
    case 'IF':
      return 'orange';
    case 'FOR':
    case 'WHILE':
      return 'purple';
    case 'ASSERT':
      return 'red';
    case 'EXTRACT':
      return 'arcoblue';
    default:
      return 'cyan';
  }
});

const stepIcon = computed(() => {
  switch (props.data.stepType) {
    case 'API_REQUEST':
      return IconCode;
    case 'SQL':
      return IconStorage;
    case 'SCRIPT':
      return IconCodeSandbox;
    case 'OPEN_PAGE':
      return IconDesktop;
    case 'CLOSE_PAGE':
      return IconDelete;
    case 'CLICK':
      return IconTool;
    case 'HOVER':
      return IconEye;
    case 'ASSERT':
      return IconSafe;
    case 'EXTRACT':
      return IconExport;
    case 'IF':
      return IconSwap;
    case 'FOR':
    case 'WHILE':
      return IconRefresh;
    case 'WAIT':
      return IconPause;
    default:
      return IconCodeSandbox;
  }
});

// ==================== 内联表单自动保存 ====================

const saving = ref(false);
const detailDirty = ref(false);
// 内联表单 remount key：撤销时 +1，强制表单从 props 重新同步，丢弃本地草稿
const editorKey = ref(0);
// 当前未保存的草稿（完整详情），点「保存」时落库
const draftDetail = ref<Record<string, any> | null>(null);

// 解析当前 stepDetail
const parsedDetail = computed(() => {
  try {
    return typeof props.data.stepDetail === 'string'
        ? JSON.parse(props.data.stepDetail || '{}')
        : (props.data.stepDetail || {});
  } catch (e) {
    return {};
  }
});

// dirty 状态：内联表单有未保存的修改
const isDirty = computed(() => isEditable.value && detailDirty.value);

watch(isDirty, (dirty) => {
  emits('dirty', props.id, dirty);
});

// InlineStepEditor 抛出的完整详情变更：仅缓存为草稿，不立即保存
const handleStepDetailChange = (newDetail: Record<string, any>) => {
  draftDetail.value = newDetail;
};

const handleDetailDirty = (dirty: boolean) => {
  detailDirty.value = dirty;
};

// 点「保存」：把草稿落库
const handleSave = async () => {
  if (!props.data.id || isTempNodeId(props.data.id) || saving.value) return;
  if (!draftDetail.value) return;

  // 完整详情（含 assertList / extractList / setting），仅剔除 parentId：
  // 拖拽只改 TestStep.parentId，不改 detail 内的 parentId，避免编辑保存后顺序错乱
  const detail = {...draftDetail.value};
  delete (detail as any).parentId;

  const testStep = new TestStep();
  testStep.id = props.data.id as number;
  testStep.projectId = props.data.projectId || '';
  testStep.scenarioId = props.data.scenarioId || '';
  testStep.stepName = detail.stepName;
  testStep.stepType = props.data.stepType;
  testStep.parentId = props.data.parentId != null ? String(props.data.parentId) : '0';
  testStep.stepDetail = detail;

  saving.value = true;
  try {
    const result = await updateStep(testStep);
    if (result.data) {
      Message.success({content: '保存成功', duration: 1000});
      draftDetail.value = null;
      detailDirty.value = false;
      emits('change', {
        ...props.data,
        stepName: detail.stepName,
        stepDetail: detail,
      } as StepVO);
    }
  } catch (e) {
    Message.error('保存失败');
  } finally {
    saving.value = false;
  }
};

// 点「撤销」：丢弃草稿，表单回到已保存状态
const handleRevert = () => {
  draftDetail.value = null;
  detailDirty.value = false;
  editorKey.value++;
};

// 提取变量列表
const extractVars = computed(() => {
  try {
    const detail = parsedDetail.value;
    const list = detail?.apiConfig?.associationExtraction || detail?.extractList;
    if (!Array.isArray(list)) return [];
    return list
        .map((r: any) => r?.variableName)
        .filter((n: any) => n != null && String(n).trim() !== '');
  } catch (e) {
    return [];
  }
});

// 调试状态相关
const debugResultMap = computed(() => {
  const map = new Map<string | number, any>();
  const list = props.data.stepDebugList || [];
  const traverse = (arr: any[]) => {
    for (const item of arr) {
      if (item.step?.id != null && !map.has(item.step.id)) {
        map.set(item.step.id, item);
      }
      if (item.childrenResults?.length > 0) {
        traverse(item.childrenResults);
      }
    }
  };
  traverse(list);
  return map;
});

const isCurrentStepHasResult = computed(() => {
  const stepId = props.data.id;
  if (!stepId) return false;
  const item = debugResultMap.value.get(stepId);
  if (!item) return false;
  if (item.childrenResults?.length > 0) return true;
  if (item.result && Object.keys(item.result).length > 0) return true;
  if (item.iterations && Object.keys(item.iterations).length > 0) return true;
  return false;
});

const debugStatus = computed(() => {
  const stepId = props.data.id;
  if (!stepId || !props.data.stepDebugList?.length) return undefined;

  const item = debugResultMap.value.get(stepId);
  if (!item) {
    // 容器条件不成立被跳过的后代步骤：按 SKIPPED 展示，不再显示执行中
    if (props.data.isSkippedByParent) return 'SKIPPED';
    return undefined;
  }

  if (item.childrenResults?.length > 0) {
    if (item.childrenResults.some((r: any) => (r.result?.status || r.status) === 'FAILURE')) return 'FAILURE';
    if (item.childrenResults.every((r: any) => (r.result?.status || r.status) === 'SUCCESS')) return 'SUCCESS';
    if (item.childrenResults.every((r: any) => (r.result?.status || r.status) === 'SKIPPED')) return 'SKIPPED';
    return 'FAILURE';
  }

  if (item.isLoop > 0) {
    if (item.iterations && Object.keys(item.iterations).length > 0) {
      const values = Object.values(item.iterations);
      if (values.some((iter: any) => iter.status === 'FAILURE')) return 'FAILURE';
      if (values.every((iter: any) => iter.status === 'SKIPPED')) return 'SKIPPED';
      if (values.every((iter: any) => iter.status === 'SUCCESS')) return 'SUCCESS';
      return 'FAILURE';
    }
    return 'SKIPPED';
  }

  const hasResult = item.result && Object.keys(item.result).length > 0;
  if (!hasResult) return 'SKIPPED';
  return item.result.status;
});

const isRunning = computed(() => {
  return props.data.isDebugging
      && !isDisabled.value
      && !props.data.isSkippedByParent
      && !debugResultMap.value.has(props.data.id!)
      && !isCurrentStepHasResult.value;
});

const handleClick = () => {
  emits('nodeClick', props.data);
};

const hasDebugResult = computed(() => debugStatus.value != null);

const handleViewResult = () => {
  if (hasDebugResult.value) {
    emits('viewResult', props.data);
  }
};

function isTempNodeId(id?: string | number): boolean {
  return id != null && String(id).startsWith('temp-step-');
}
</script>

<style scoped>
.workflow-step-node {
  width: 320px;
  background: #fff;
  border: 2px solid #e5e6eb;
  border-radius: 8px;
  padding: 8px 10px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.workflow-step-node:hover {
  border-color: #165dff;
  box-shadow: 0 4px 12px rgba(22, 93, 255, 0.15);
}

.workflow-step-node.is-selected {
  border-color: #165dff;
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.15);
}

.workflow-step-node.is-dirty {
  border-color: #ff7d00;
}

.workflow-step-node.is-disabled {
  opacity: 0.6;
  background: #f7f8fa;
}

.workflow-step-node.is-container {
  background: #f8f9fb;
  border-style: dashed;
}

.workflow-step-node.status-success {
  border-color: #3cc071;
}

.workflow-step-node.status-failure {
  border-color: #f53f3f;
}

.workflow-step-node.status-skipped {
  border-color: #a9aeb8;
}

.workflow-step-node.status-running {
  border-color: #165dff;
}

.dirty-dot {
  position: absolute;
  top: -4px;
  right: -4px;
  width: 8px;
  height: 8px;
  background: #ff7d00;
  border-radius: 50%;
  z-index: 1;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  min-width: 0;
  cursor: pointer;
}

.node-icon {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #f2f3f5;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #165dff;
}

.node-type-tag {
  flex-shrink: 0;
}

.node-order {
  font-size: 11px;
  color: #86909c;
}

.node-header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.node-status {
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.node-setting-btn {
  font-size: 16px;
  color: #86909c;
  cursor: pointer;
}

.node-setting-btn:hover {
  color: #165dff;
}

.node-name {
  font-size: 13px;
  font-weight: 500;
  color: #1d2129;
}

.node-meta-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex-wrap: wrap;
}

.meta-tag {
  flex-shrink: 0;
}

.node-actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 6px;
}

.workflow-handle {
  width: 8px !important;
  height: 8px !important;
  background: #fff !important;
  border: 2px solid #165dff !important;
  border-radius: 50% !important;
}

.workflow-handle:hover {
  background: #165dff !important;
}
</style>
