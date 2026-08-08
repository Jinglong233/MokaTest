<template>
  <div
      class="workflow-group-node"
      :class="{
        'is-selected': selected,
        'is-disabled': isDisabled,
        'status-success': debugStatus === 'SUCCESS',
        'status-failure': debugStatus === 'FAILURE',
        'status-skipped': debugStatus === 'SKIPPED',
        'status-running': isRunning,
        ['type-' + (data.stepType || '').toLowerCase()]: true,
      }"
  >
    <!-- 左侧连接桩 -->
    <Handle type="target" :position="Position.Left" class="workflow-handle"/>

    <!-- 头部：紧凑摘要，点击/⚙ 打开抽屉编辑容器条件 -->
    <div class="group-header" @click="handleClick">
      <div
          v-if="childCount > 0"
          class="group-collapse-btn"
          @click.stop="handleToggleCollapse"
      >
        <icon-right v-if="isCollapsed"/>
        <icon-down v-else/>
      </div>
      <div class="group-icon">
        <component :is="stepIcon" :style="{ fontSize: '16px' }"/>
      </div>
      <a-tag :color="stepTagColor" size="small" class="group-type-tag">{{ stepTypeChinese }}</a-tag>
      <a-typography-text class="group-name" :ellipsis="{rows: 1}">
        {{ data.stepName || '未命名' }}
      </a-typography-text>
      <a-tag color="gray" size="small" class="group-summary">{{ summary }}</a-tag>
      <a-tag v-if="isCollapsed && childCount > 0" color="arcoblue" size="small" class="group-collapsed-count">
        {{ childCount }} 步
      </a-tag>

      <div class="group-header-right">
        <span class="group-status" v-if="hasDebugResult || isRunning" @click.stop="handleViewResult">
          <a-spin v-if="isRunning" :size="14"/>
          <icon-check-circle-fill v-else-if="debugStatus === 'SUCCESS'" style="color: #3cc071; font-size: 16px;"/>
          <icon-close-circle-fill v-else-if="debugStatus === 'FAILURE'" style="color: #f53f3f; font-size: 16px;"/>
          <icon-minus-circle v-else-if="debugStatus === 'SKIPPED'" style="color: #86909c; font-size: 16px;"/>
        </span>
        <a-tag v-if="isDisabled" color="gray" size="small">已禁用</a-tag>
        <a-tooltip content="编辑条件 / 设置（抽屉）">
          <icon-settings class="group-setting-btn" @click.stop="handleClick"/>
        </a-tooltip>
      </div>
    </div>

    <!-- 框体：子步骤由 Vue Flow 按相对坐标叠加在此区域内；折叠时隐藏 -->
    <div v-if="!isCollapsed" class="group-body">
      <div v-if="childCount === 0" class="group-empty">
        <icon-drag-arrow class="group-empty-icon"/>
        <span>拖拽步骤到此处</span>
      </div>
    </div>

    <!-- 右侧连接桩 -->
    <Handle type="source" :position="Position.Right" class="workflow-handle"/>
  </div>
</template>

<script setup lang="ts">
import {computed, inject} from 'vue';
import {Handle, Position} from '@vue-flow/core';
import {IconDragArrow, IconRefresh, IconSwap, IconSettings, IconRight, IconDown} from '@arco-design/web-vue/es/icon';
import {getStepTypeChinese} from "@/types/enum/StepType";
import type {StepVO} from "@/types/vo/StepVO";
import type {Ref} from 'vue';

interface GroupNodeData extends StepVO {
  isDebugging?: boolean;
  stepDebugList?: any[];
  isDisable?: number;
  stepDetail?: string | Record<string, any>;
}

interface Props {
  id: string;
  data: GroupNodeData;
  selected?: boolean;
  editable?: boolean;
}

const props = defineProps<Props>();
const emits = defineEmits<{
  (e: 'nodeClick', step: StepVO): void;
  (e: 'viewResult', step: StepVO): void;
}>();

const isDisabled = computed(() => props.data.isDisable === 1);
const stepTypeChinese = computed(() => getStepTypeChinese(props.data.stepType as any));
const childCount = computed(() => props.data.children?.length ?? 0);

// 折叠状态（由画布通过 provide 注入）
const collapsedIds = inject<Ref<Set<string>>>('workflowCollapsedIds');
const toggleCollapse = inject<(id: string) => void>('workflowToggleCollapse');
const isCollapsed = computed(() => !!collapsedIds?.value?.has(props.id));
const handleToggleCollapse = () => toggleCollapse?.(props.id);

const stepTagColor = computed(() => {
  switch (props.data.stepType) {
    case 'IF':
      return 'orange';
    case 'FOR':
    case 'WHILE':
      return 'purple';
    default:
      return 'cyan';
  }
});

const stepIcon = computed(() => (props.data.stepType === 'IF' ? IconSwap : IconRefresh));

// 解析详情用于生成摘要
const parsedDetail = computed(() => {
  try {
    return typeof props.data.stepDetail === 'string'
        ? JSON.parse(props.data.stepDetail || '{}')
        : (props.data.stepDetail || {});
  } catch (e) {
    return {};
  }
});

// 摘要：FOR → 循环 N 次 / WHILE → 最多 N 次 · M 条件 / IF → N 个条件
const summary = computed(() => {
  const d = parsedDetail.value;
  const condCount = Array.isArray(d.conditionList) ? d.conditionList.length : 0;
  switch (props.data.stepType) {
    case 'FOR':
      return d.cycleTimes != null ? `循环 ${d.cycleTimes} 次` : '循环';
    case 'WHILE': {
      const parts: string[] = [];
      if (d.maxLoopCount != null) parts.push(`最多 ${d.maxLoopCount} 次`);
      if (condCount) parts.push(`${condCount} 条件`);
      return parts.join(' · ') || 'while 循环';
    }
    case 'IF':
      return condCount ? `${condCount} 个条件` : 'if 判断';
    default:
      return '';
  }
});

// ==================== 调试状态 ====================
const debugResultMap = computed(() => {
  const map = new Map<string | number, any>();
  const traverse = (arr: any[]) => {
    for (const item of arr) {
      if (item.step?.id != null && !map.has(item.step.id)) map.set(item.step.id, item);
      if (item.childrenResults?.length > 0) traverse(item.childrenResults);
    }
  };
  traverse(props.data.stepDebugList || []);
  return map;
});

const debugStatus = computed(() => {
  const stepId = props.data.id;
  if (!stepId || !props.data.stepDebugList?.length) return undefined;
  const item = debugResultMap.value.get(stepId);
  if (!item) return undefined;
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

const isRunning = computed(() => props.data.isDebugging
    && !isDisabled.value
    && !debugResultMap.value.has(props.data.id!));

const hasDebugResult = computed(() => debugStatus.value != null);

const handleClick = () => emits('nodeClick', props.data);
const handleViewResult = () => {
  if (hasDebugResult.value) emits('viewResult', props.data);
};
</script>

<style scoped>
.workflow-group-node {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  background: rgba(247, 248, 250, 0.6);
  border: 2px dashed #c9cdd4;
  border-radius: 10px;
  position: relative;
  display: flex;
  flex-direction: column;
}

.workflow-group-node.type-if {
  border-color: #ff9a2e;
  background: rgba(255, 247, 232, 0.5);
}

.workflow-group-node.type-for,
.workflow-group-node.type-while {
  border-color: #b37feb;
  background: rgba(245, 240, 255, 0.5);
}

.workflow-group-node:hover {
  box-shadow: 0 4px 12px rgba(22, 93, 255, 0.12);
}

.workflow-group-node.is-selected {
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.15);
}

.workflow-group-node.is-disabled {
  opacity: 0.6;
}

.workflow-group-node.status-success {
  border-color: #3cc071;
}

.workflow-group-node.status-failure {
  border-color: #f53f3f;
}

.group-header {
  height: 64px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 12px;
  border-bottom: 1px dashed #e5e6eb;
  cursor: pointer;
}

.group-icon {
  flex-shrink: 0;
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #165dff;
}

.group-type-tag {
  flex-shrink: 0;
}

.group-name {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
  max-width: 140px;
}

.group-summary {
  flex-shrink: 0;
}

.group-header-right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-status {
  display: flex;
  align-items: center;
  cursor: pointer;
}

.group-setting-btn {
  font-size: 16px;
  color: #86909c;
  cursor: pointer;
}

.group-setting-btn:hover {
  color: #165dff;
}

.group-body {
  flex: 1;
  min-height: 0;
  position: relative;
}

.group-empty {
  position: absolute;
  inset: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px dashed #c9cdd4;
  border-radius: 6px;
  font-size: 12px;
  color: #c9cdd4;
  background: rgba(255, 255, 255, 0.4);
}

.group-empty-icon {
  font-size: 18px;
}

.group-collapse-btn {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #86909c;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.group-collapse-btn:hover {
  background: rgba(22, 93, 255, 0.1);
  color: #165dff;
}

.group-collapsed-count {
  flex-shrink: 0;
}

:deep(.workflow-handle) {
  width: 8px !important;
  height: 8px !important;
  background: #fff !important;
  border: 2px solid #165dff !important;
  border-radius: 50% !important;
}
</style>
