<template>
  <div ref="canvasRootRef" class="scene-workflow-canvas">
    <!-- 顶部工具栏 -->
    <div class="workflow-toolbar">
      <a-space>
        <a-dropdown v-if="hasUpdatePermission" position="bottom">
          <a-button size="small" type="primary" :disabled="mutationsLocked">
            <template #icon><icon-plus/></template>
            添加步骤
          </a-button>
          <template #content>
            <template v-for="item in availableStepTypes" :key="item.value">
              <a-dsubmenu v-if="item.value === 'api_request' || item.value === 'sql'" :value="item.value">
                <template #default>{{ item.label }}</template>
                <template #content>
                  <a-doption @click="handleAddRootStep(item.value, 'new')">
                    {{ item.value === 'sql' ? '新建SQL接口' : '新建接口' }}
                  </a-doption>
                  <a-doption @click="handleAddRootStep(item.value, 'import')">
                    {{ item.value === 'sql' ? '引入已有SQL接口' : '引入已有用例' }}
                  </a-doption>
                </template>
              </a-dsubmenu>
              <a-doption v-else @click="handleAddRootStep(item.value)">{{ item.label }}</a-doption>
            </template>
          </template>
        </a-dropdown>
        <a-button size="small" @click="handleFitView">
          <template #icon><icon-expand/></template>
          适应画布
        </a-button>
        <a-button size="small" @click="handleAutoLayout">
          <template #icon><icon-refresh/></template>
          自动布局
        </a-button>
      </a-space>
    </div>

    <!-- Vue Flow 画布 -->
    <VueFlow
        v-model:nodes="nodes"
        v-model:edges="edges"
        :default-edge-options="defaultEdgeOptions"
        :min-zoom="0.2"
        :max-zoom="2"
        :nodes-draggable="hasUpdatePermission && !isDebugging"
        :nodes-connectable="false"
        :edges-updatable="false"
        :node-types="nodeTypes"
        class="workflow-flow"
        @node-drag-start="onNodeDragStart"
        @node-drag="onNodeDrag"
        @node-drag-stop="onNodeDragStop"
        @pane-ready="onPaneReady"
    >
      <template #node-step="nodeProps">
        <WorkflowStepNode
            v-bind="nodeProps"
            :editable="hasUpdatePermission && !isStepLocked(nodeProps.data?.id)"
            @node-click="editStep(nodeProps.data as StepVO)"
            @view-result="handleViewResult"
            @change="handleNodeChange"
            @dirty="handleNodeDirty"
        />
      </template>
      <template #node-group="nodeProps">
        <WorkflowGroupNode
            v-bind="nodeProps"
            :editable="hasUpdatePermission && !isStepLocked(nodeProps.data?.id)"
            @node-click="editStep(nodeProps.data as StepVO)"
            @view-result="handleViewResult"
        />
      </template>
      <Background pattern-color="#e5e6eb" :gap="16"/>
      <MiniMap/>
      <Controls/>
    </VueFlow>

    <!-- 节点右键菜单 -->
    <div
        v-if="contextMenu.visible"
        class="workflow-context-menu"
        :style="{left: contextMenu.x + 'px', top: contextMenu.y + 'px'}"
    >
      <a-dropdown
          popup-container=".scene-workflow-canvas"
          :popup-visible="true"
          position="br"
          @select="handleContextMenuSelect"
          @popup-visible-change="onContextMenuVisibleChange"
      >
        <div style="width: 1px; height: 1px;"></div>
        <template #content>
          <a-doption value="edit" v-if="contextMenu.nodeId">编辑步骤</a-doption>
          <a-doption value="runUntil" v-if="contextMenu.nodeId && !mutationsLocked && !isStepLocked(contextMenu.nodeId)">
            执行到此步骤
          </a-doption>
          <a-dsubmenu value="addAdjacent" v-if="contextMenu.nodeId && !mutationsLocked">
            <template #default>添加相邻步骤</template>
            <template #content>
              <template v-for="item in availableStepTypes" :key="'adj-' + item.value">
                <a-dsubmenu v-if="item.value === 'api_request' || item.value === 'sql'" :value="'add-adj:' + item.value">
                  <template #default>{{ item.label }}</template>
                  <template #content>
                    <a-doption :value="'add-adj:' + item.value + ':new'">
                      {{ item.value === 'sql' ? '新建SQL接口' : '新建接口' }}
                    </a-doption>
                    <a-doption :value="'add-adj:' + item.value + ':import'">
                      {{ item.value === 'sql' ? '引入已有SQL接口' : '引入已有用例' }}
                    </a-doption>
                  </template>
                </a-dsubmenu>
                <a-doption v-else :value="'add-adj:' + item.value">{{ item.label }}</a-doption>
              </template>
            </template>
          </a-dsubmenu>
          <a-dsubmenu value="addChild" v-if="contextMenu.nodeId && contextMenu.isContainer && !mutationsLocked"
          >
            <template #default>添加子步骤</template>
            <template #content>
              <template v-for="item in availableStepTypes" :key="'child-' + item.value">
                <a-dsubmenu v-if="item.value === 'api_request' || item.value === 'sql'" :value="'add-child:' + item.value">
                  <template #default>{{ item.label }}</template>
                  <template #content>
                    <a-doption :value="'add-child:' + item.value + ':new'">
                      {{ item.value === 'sql' ? '新建SQL接口' : '新建接口' }}
                    </a-doption>
                    <a-doption :value="'add-child:' + item.value + ':import'">
                      {{ item.value === 'sql' ? '引入已有SQL接口' : '引入已有用例' }}
                    </a-doption>
                  </template>
                </a-dsubmenu>
                <a-doption v-else :value="'add-child:' + item.value">{{ item.label }}</a-doption>
              </template>
            </template>
          </a-dsubmenu>
          <a-dsubmenu value="moveInto" v-if="contextMenu.nodeId && moveIntoTargets.length > 0 && !isStepLocked(contextMenu.nodeId)">
            <template #default>移入容器</template>
            <template #content>
              <a-doption
                  v-for="t in moveIntoTargets"
                  :key="'move-into-' + t.id"
                  :value="'move-into:' + t.id"
              >
                {{ t.name }}
              </a-doption>
            </template>
          </a-dsubmenu>
          <a-doption value="moveOut" v-if="contextMenu.nodeId && canMoveOut && !isStepLocked(contextMenu.nodeId)">移出当前容器</a-doption>
          <a-doption value="delete" v-if="contextMenu.nodeId && hasDeletePermission && !isStepLocked(contextMenu.nodeId)" status="danger"
          >
            删除步骤
          </a-doption>
          <template v-if="!contextMenu.nodeId && !mutationsLocked">
            <a-dsubmenu value="addRoot">
              <template #default>添加步骤</template>
              <template #content>
                <template v-for="item in availableStepTypes" :key="'root-' + item.value">
                  <a-dsubmenu v-if="item.value === 'api_request' || item.value === 'sql'" :value="'add-root:' + item.value">
                    <template #default>{{ item.label }}</template>
                    <template #content>
                      <a-doption :value="'add-root:' + item.value + ':new'">
                        {{ item.value === 'sql' ? '新建SQL接口' : '新建接口' }}
                      </a-doption>
                      <a-doption :value="'add-root:' + item.value + ':import'">
                        {{ item.value === 'sql' ? '引入已有SQL接口' : '引入已有用例' }}
                      </a-doption>
                    </template>
                  </a-dsubmenu>
                  <a-doption v-else :value="'add-root:' + item.value">{{ item.label }}</a-doption>
                </template>
              </template>
            </a-dsubmenu>
          </template>
        </template>
      </a-dropdown>
    </div>

    <!-- 拖拽落点指示器 -->
    <DropIndicator
        :line="dropIndicator.line"
        :container="dropIndicator.container"
        :label="dropIndicator.label"
    />

    <!-- 步骤编辑抽屉：复用现有组件 -->
    <StepDetailDraw
        :width="520"
        v-model:visible="stepFormVisible"
        :title="`${stepTitle}${!hasUpdatePermission ? '（只读）' : (isCurrentStepLocked ? '（调试中只读）' : '')}`"
        :form-fields="currentOperationFormConfig"
        :form-data="currentStepDetail"
        :show-setting-tab="showSettingTab"
        :scene-environment-id="sceneEnvironmentId"
        :submit-disabled="!hasUpdatePermission || isCurrentStepLocked"
        :disabled="!hasUpdatePermission || isCurrentStepLocked"
        @cancel="handleCancel"
        @submit="handleSubmit"
    />

    <!-- API/SQL 步骤「引入已有」来源选择器 -->
    <ApiSourcePickerModal
        v-model:visible="apiSourcePickerVisible"
        :api-type="apiSourcePickerType"
        @select="handleApiSourcePicked"
    />

    <!-- UI 步骤调试结果抽屉 -->
    <StepDebugResultDraw
        :visible="debugResultDrawerVisible"
        :step-result="currentStepDebugResult"
        @cancel="debugResultDrawerVisible = false"
    />

    <!-- API/循环/条件 步骤调试结果抽屉 -->
    <ApiStepDebugResult
        v-if="currentApiStepResult"
        :visible="apiStepDebugResultVisible"
        :step-result="currentApiStepResult"
        @update:visible="apiStepDebugResultVisible = $event"
    />
  </div>
</template>

<script setup lang="ts">
import {computed, ref, watch, nextTick, markRaw, provide, toRef} from 'vue';
import {collectSkippedDescendantIds} from '@/views/scene/component/skippedSteps';
import {VueFlow, useVueFlow} from '@vue-flow/core';
import {Background} from '@vue-flow/background';
import {MiniMap} from '@vue-flow/minimap';
import {Controls} from '@vue-flow/controls';
import '@vue-flow/core/dist/style.css';
import '@vue-flow/core/dist/theme-default.css';
import '@vue-flow/controls/dist/style.css';
import '@vue-flow/minimap/dist/style.css';
import {Message, Modal} from "@arco-design/web-vue";
import {IconExpand, IconRefresh, IconPlus} from '@arco-design/web-vue/es/icon';
import WorkflowStepNode from './WorkflowStepNode.vue';
import WorkflowGroupNode from './WorkflowGroupNode.vue';
import StepDetailDraw from './StepDetailDraw.vue';
import ApiSourcePickerModal from './ApiSourcePickerModal.vue';
import {
  buildImportedStepData,
  buildNewHttpStepData,
  buildNewSqlStepData,
  type ApiStepSourceData
} from './apiStepTemplate';
import StepDebugResultDraw from './StepDebugResultDraw.vue';
import ApiStepDebugResult from './ApiStepDebugResult.vue';
import DropIndicator from './DropIndicator.vue';
import {
  stepsToNodes,
  nodesToEdges,
  layoutGraph,
  graphToSteps,
  isContainerNode,
} from '../utils/workflowAdapter';
import {STEP_REGISTRY} from "@/schema/stepFormConfig/FormConfig";
import {addStep, deleteStepById, getStepDetail, updateStep, updateStepSort} from "@/api/MyApi/step";
import {StepVO} from "@/types/vo/StepVO";
import {TestStep} from "@/types/domain/TestStep";
import {getStepTypeChinese, StepType} from "@/types/enum/StepType";
import {createStep} from "@/types/dto/StepDetailDTO";
import {useProjectStore} from "@/store";
import {useDebugStepLock} from "@/views/scene/component/useDebugStepLock";
import type {Node, Edge, NodeMouseEvent, NodeDragEvent, NodeTypesObject} from "@vue-flow/core";

interface Props {
  sceneId?: number;
  steps?: StepVO[];
  isDebugging?: boolean;
  stepDebugList?: any[];
  isApiScene?: boolean;
  sceneEnvironmentId?: number;
  hasUpdatePermission?: boolean;
  hasDeletePermission?: boolean;
  // 调试状态（未运行/运行中/暂停/失败挂起）
  debugStatus?: string;
  // 失败挂起的失败步骤id（该步骤在挂起期间允许修改）
  pausedFailureStepId?: number | null;
}

const props = withDefaults(defineProps<Props>(), {
  steps: () => [],
  isDebugging: false,
  stepDebugList: () => [],
  isApiScene: false,
  hasUpdatePermission: false,
  hasDeletePermission: false,
  debugStatus: '未运行',
  pausedFailureStepId: null,
});

const emits = defineEmits<{
  (e: 'refresh'): void;
  (e: 'dirty-change', hasDirty: boolean): void;
  (e: 'run-until-step', step: StepVO): void;
}>();

const projectStore = useProjectStore();

// ===== 调试期间的步骤编辑锁定 =====
const {mutationsLocked, isStepLocked} = useDebugStepLock({
  isDebugging: toRef(props, 'isDebugging'),
  debugStatus: toRef(props, 'debugStatus'),
  stepDebugList: toRef(props, 'stepDebugList'),
  pausedFailureStepId: toRef(props, 'pausedFailureStepId'),
  isApiScene: toRef(props, 'isApiScene'),
});

// 当前抽屉中步骤是否被调试锁定（只读）
const isCurrentStepLocked = computed(() => {
  const id = (currentStep.value as any)?.id;
  return id != null && isStepLocked(id);
});

// Vue Flow 实例
const {fitView, onPaneClick, onNodesInitialized, onNodesChange, findNode} = useVueFlow();

// ==================== 实测重排 ====================
// 容器框尺寸需要根据子节点实测高度撑开，首屏用估算值，渲染后用真实高度重排
let relayoutTimer: any = null;

const applyMeasuredLayout = () => {
  if (!props.steps || props.steps.length === 0) return;
  const laid = stepsToNodes(props.steps, (id) => findNode(String(id))?.dimensions?.height, collapsedIds.value);
  const existingById = new Map(nodes.value.map(n => [n.id, n]));
  // 按 laid 顺序重建（父先于子），保留已有节点对象（含调试态 data）；
  // 折叠后消失的子节点不在 laid 中会被丢弃，展开后重新出现的子节点会被补回。
  let changed = laid.length !== nodes.value.length;
  const updated = laid.map(l => {
    const n = existingById.get(l.id);
    if (!n) {
      changed = true;
      return l;
    }
    const samePos = n.position?.x === l.position.x && n.position?.y === l.position.y;
    const sameStyle = JSON.stringify(n.style || {}) === JSON.stringify(l.style || {});
    const sameParent = String((n as any).parentNode || '') === String((l as any).parentNode || '');
    if (samePos && sameStyle && sameParent && n.type === l.type) return n;
    changed = true;
    return {
      ...n,
      type: l.type,
      position: l.position,
      style: l.style,
      parentNode: (l as any).parentNode,
      extent: (l as any).extent,
    };
  });
  if (changed) nodes.value = updated as any;
};

const scheduleRelayout = () => {
  clearTimeout(relayoutTimer);
  relayoutTimer = setTimeout(applyMeasuredLayout, 80);
};

onNodesInitialized(() => scheduleRelayout());
onNodesChange((changes: any[]) => {
  if (changes.some(c => c.type === 'dimensions')) scheduleRelayout();
});

// 节点类型注册
const nodeTypes: NodeTypesObject = {
  step: markRaw(WorkflowStepNode) as any,
  group: markRaw(WorkflowGroupNode) as any,
};

// 默认边样式
const defaultEdgeOptions = {
  type: 'smoothstep',
  animated: false,
  style: {stroke: '#C2C8D5', strokeWidth: 2},
};

// 画布元素
const nodes = ref<Node[]>([]);
const edges = ref<Edge[]>([]);
const isPaneReady = ref(false);
const canvasRootRef = ref<HTMLElement | null>(null);

// 已折叠的容器 id
const collapsedIds = ref<Set<string>>(new Set());
provide('workflowCollapsedIds', collapsedIds);
provide('workflowToggleCollapse', (id: string) => {
  if (collapsedIds.value.has(id)) {
    collapsedIds.value.delete(id);
  } else {
    collapsedIds.value.add(id);
  }
  collapsedIds.value = new Set(collapsedIds.value);
});

// 折叠状态变化：重建画布（折叠容器隐藏子节点 + 收缩）并自适应视图
watch(collapsedIds, () => {
  const {newNodes, newEdges} = buildFlowData(props.steps || []);
  nodes.value = newNodes;
  edges.value = newEdges;
  scheduleRelayout();
  if (isPaneReady.value) {
    nextTick(() => fitView({padding: 0.2}));
  }
});

// 编辑抽屉状态
const stepFormVisible = ref(false);
const currentStep = ref<StepVO | null>(null);
const currentStepDetail = ref<any>({});
const currentOperationFormConfig = ref<any[]>([]);

// 控制流步骤不需要设置/断言/关联提取标签页（仅 API 场景）
const showSettingTab = computed(() => {
  if (!props.isApiScene) return true;
  const controlStepTypes = ['FOR', 'WHILE', 'IF', 'WAIT'];
  return !controlStepTypes.includes(currentStepDetail.value?.stepType);
});

// 调试结果抽屉状态
const debugResultDrawerVisible = ref(false);
const currentStepDebugResult = ref<any>(null);
const apiStepDebugResultVisible = ref(false);
const currentApiStepResult = ref<any>(null);

// dirty 节点跟踪
const dirtyNodeIds = ref<Set<string>>(new Set());

// 右键菜单状态
const contextMenu = ref({
  visible: false,
  x: 0,
  y: 0,
  nodeId: undefined as string | undefined,
  isContainer: false,
});

const stepTitle = computed(() => {
  if (!currentStep.value?.stepType) return '步骤详情';
  return getStepTypeChinese(currentStep.value.stepType as any);
});

// 右键菜单：当前节点 / 能否移出 / 可移入的容器列表
const contextNode = computed<Node | undefined>(
    () => (nodes.value as any[]).find((n) => n.id === contextMenu.value.nodeId),
);

const canMoveOut = computed(() => {
  const n = contextNode.value;
  return !!n && !!String((n as any).parentNode || '');
});

const moveIntoTargets = computed<{ id: string; name: string }[]>(() => {
  const n = contextNode.value;
  if (!n) return [];
  const excluded = getSubtreeIds(n.id); // 不能移入自身或其子孙
  const currentParent = String((n as any).parentNode || '') || null;
  return (nodes.value as any[])
      .filter(c => isContainerNode(c) && !excluded.has(c.id) && c.id !== currentParent)
      .map(c => ({
        id: c.id,
        name: c.data?.stepName || getStepTypeChinese(c.data?.stepType),
      }));
});

// API 场景可用步骤类型
const apiStepTypes = [
  {value: 'api_request', label: 'HTTP请求'},
  {value: 'sql', label: 'SQL查询'},
  {value: 'script', label: '脚本'},
  {value: 'for', label: 'FOR循环'},
  {value: 'while', label: 'WHILE循环'},
  {value: 'if', label: 'IF判断'},
  {value: 'wait', label: '等待'},
];

// UI 场景可用步骤类型
const uiStepTypes = [
  {value: 'open_page', label: '打开页面'},
  {value: 'close_page', label: '关闭页面'},
  {value: 'switch_tab', label: '切换标签页'},
  {value: 'forward', label: '前进'},
  {value: 'back', label: '后退'},
  {value: 'refresh', label: '刷新'},
  {value: 'click', label: '点击'},
  {value: 'hover', label: '悬停'},
  {value: 'drag', label: '拖拽元素'},
  {value: 'keyboard', label: '键盘操作'},
  {value: 'element_dom_operation', label: '元素DOM操作'},
  {value: 'wait', label: '等待'},
  {value: 'assert', label: '断言'},
  {value: 'extract', label: '关联提取'},
  {value: 'if', label: 'IF判断'},
  {value: 'for', label: 'FOR循环'},
  {value: 'while', label: 'WHILE循环'},
  {value: 'iframe', label: 'iframe切换'},
  {value: 'dialog', label: '对话框'},
  {value: 'api_request', label: 'HTTP请求'},
  {value: 'sql', label: 'SQL查询'},
  {value: 'script', label: '脚本'},
];

const availableStepTypes = computed(() => props.isApiScene ? apiStepTypes : uiStepTypes);

/**
 * 节点数据变更（inline 自动保存成功）
 */
const handleNodeChange = (updatedStep: StepVO) => {
  const idx = nodes.value.findIndex(n => n.id === String(updatedStep.id));
  if (idx >= 0) {
    nodes.value[idx] = {
      ...nodes.value[idx],
      data: {
        ...nodes.value[idx].data,
        ...updatedStep,
      }
    };
  }
};

/**
 * 节点 dirty 状态变化
 */
const handleNodeDirty = (nodeId: string, dirty: boolean) => {
  if (dirty) {
    dirtyNodeIds.value.add(nodeId);
  } else {
    dirtyNodeIds.value.delete(nodeId);
  }
  emits('dirty-change', dirtyNodeIds.value.size > 0);
};

/**
 * 将步骤数据转换为 Vue Flow 元素
 */
// IF 条件不成立被跳过的后代步骤：无结果推送，标记后节点按 SKIPPED 展示，避免一直显示执行中
const skippedDescendantIds = computed(() =>
    collectSkippedDescendantIds(props.stepDebugList, props.steps as any[]));

const buildFlowData = (steps: StepVO[]) => {
  const newNodes = stepsToNodes(steps, undefined, collapsedIds.value).map(n => ({
    ...n,
    data: {
      ...n.data,
      isDebugging: props.isDebugging,
      stepDebugList: props.stepDebugList,
      isSkippedByParent: skippedDescendantIds.value.has(n.data?.id),
    }
  }));
  const newEdges = nodesToEdges(newNodes);
  return {newNodes, newEdges};
};

/**
 * 监听步骤数据变化，重新渲染画布
 */
watch(() => props.steps, (newSteps) => {
  const {newNodes, newEdges} = buildFlowData(newSteps || []);
  nodes.value = newNodes;
  edges.value = newEdges;
  // 重建后用实测高度重排容器框，并适应画布
  scheduleRelayout();
  if (isPaneReady.value) {
    nextTick(() => fitView({padding: 0.2}));
  }
}, {immediate: true, deep: true});

/**
 * 监听调试状态变化，更新节点数据
 */
watch(() => [props.isDebugging, props.stepDebugList], () => {
  nodes.value = nodes.value.map(n => ({
    ...n,
    data: {
      ...n.data,
      isDebugging: props.isDebugging,
      stepDebugList: props.stepDebugList,
      isSkippedByParent: skippedDescendantIds.value.has(n.data?.id),
    }
  }));
}, {deep: true});

/**
 * Pane 点击：隐藏右键菜单
 */
onPaneClick(() => {
  hideContextMenu();
});

/**
 * 拖拽落点状态：实时展示插入线与容器高亮
 */
interface DropState {
  draggingId: string | null;
  originParentId: string | null;  // 拖拽开始时的父级
  originIndex: number;            // 拖拽开始时在同级中的下标
  targetParentId: string | null; // null = 根
  insertIndex: number | null;    // 在该父节点的子列表中插入到第几个之前（从0开始）
  line: { x: number; y: number; height: number } | null;
  container: { x: number; y: number; width: number; height: number } | null;
  label: string;
}

const dropIndicator = ref<DropState>({
  draggingId: null,
  originParentId: null,
  originIndex: -1,
  targetParentId: null,
  insertIndex: null,
  line: null,
  container: null,
  label: '插入',
});

// 节点容器 DOM id → 节点数据
const getNodeElById = (id: string): HTMLElement | null =>
    document.querySelector(`.scene-workflow-canvas .vue-flow__node[data-id="${id}"]`);

const getCanvasRect = () => canvasRootRef.value?.getBoundingClientRect();

const getNodeRect = (id: string) => {
  const el = getNodeElById(id);
  if (!el) return null;
  const rect = el.getBoundingClientRect();
  const canvasRect = getCanvasRect();
  if (!canvasRect) return null;
  return {
    left: rect.left - canvasRect.left,
    top: rect.top - canvasRect.top,
    right: rect.right - canvasRect.left,
    bottom: rect.bottom - canvasRect.top,
    width: rect.width,
    height: rect.height,
  };
};

const resetDropIndicator = () => {
  dropIndicator.value = {
    draggingId: null,
    originParentId: null,
    originIndex: -1,
    targetParentId: null,
    insertIndex: null,
    line: null,
    container: null,
    label: '插入',
  };
};

/** 取节点在嵌套树中的深度（根节点深度 0） */
const getNodeDepth = (nodeId: string): number => {
  let depth = 0;
  let current = nodes.value.find(n => n.id === nodeId);
  while (current && (current as any).parentNode) {
    depth++;
    current = nodes.value.find(n => n.id === String((current as any).parentNode));
    if (!current) break;
  }
  return depth;
};

/**
 * 判断某点是否在容器框内部（不含头部，指可放子步骤的子区域）
 */
const pointInContainerBody = (pointX: number, pointY: number, id: string) => {
  const rect = getNodeRect(id);
  if (!rect) return false;
  const bodyTop = rect.top + 64;
  return pointX >= rect.left && pointX <= rect.right
      && pointY >= bodyTop && pointY <= rect.bottom;
};

/**
 * 判断某点是否在容器头部
 */
const pointInContainerHeader = (pointX: number, pointY: number, id: string) => {
  const rect = getNodeRect(id);
  if (!rect) return false;
  return pointX >= rect.left && pointX <= rect.right
      && pointY >= rect.top && pointY <= rect.top + 64;
};

/** 取被拖拽节点的整个子树 id（含自身），用于排除：不能把节点拖进自己的子孙里 */
const getSubtreeIds = (rootId: string): Set<string> => {
  const result = new Set<string>([rootId]);
  const stack = [rootId];
  while (stack.length) {
    const cur = stack.pop()!;
    nodes.value.forEach(n => {
      if ((String((n as any).parentNode || '') || '') === cur && !result.has(n.id)) {
        result.add(n.id);
        stack.push(n.id);
      }
    });
  }
  return result;
};

/** 通过 DOM 反查鼠标下方的所有节点 id（从顶到底），排除拖拽节点自身及其子树 */
const getNodeIdsUnderMouse = (clientX: number, clientY: number, draggingId: string): string[] => {
  const excluded = getSubtreeIds(draggingId);
  const elements = document.elementsFromPoint(clientX, clientY);
  const ids: string[] = [];
  for (const el of elements) {
    const nodeEl = el.closest('.scene-workflow-canvas .vue-flow__node[data-id]') as HTMLElement | null;
    if (!nodeEl) continue;
    const id = nodeEl.getAttribute('data-id');
    if (id && !excluded.has(id) && !ids.includes(id)) {
      ids.push(id);
    }
  }
  return ids;
};

/**
 * 按当前鼠标位置计算最佳落点（支持多层嵌套）。
 * 策略：直接看鼠标下方有哪些 Vue Flow 节点，取最深的容器。
 * - 命中容器体部 → 拖入该容器
 * - 命中容器头部 → 拖出到该容器父级
 * - 未命中容器 / 命中当前父级体部 → 留在当前父级同层排序
 */
const computeDropTarget = (mouseClientX: number, mouseClientY: number, draggingId: string) => {
  const draggingNode = nodes.value.find(n => n.id === draggingId);
  const currentParentId = String((draggingNode as any).parentNode || '') || null;

  // 默认：留在当前父级内排序
  let effectiveParentId: string | null = currentParentId;
  let label = '插入';
  let containerHighlight: { x: number; y: number; width: number; height: number } | null = null;

  // 鼠标下方所有节点 id，按 DOM 层叠顺序（顶→底）
  const underMouseIds = getNodeIdsUnderMouse(mouseClientX, mouseClientY, draggingId);
  // 过滤出容器，并按嵌套深度取最深的
  const hitContainers = underMouseIds
      .map(id => nodes.value.find(n => n.id === id))
      .filter((n): n is Node => !!n && isContainerNode(n))
      .sort((a, b) => getNodeDepth(b.id) - getNodeDepth(a.id));

  if (hitContainers.length > 0) {
    const deepest = hitContainers[0];
    const canvasMouseX = mouseClientX - (getCanvasRect()?.left ?? 0);
    const canvasMouseY = mouseClientY - (getCanvasRect()?.top ?? 0);

    if (pointInContainerHeader(canvasMouseX, canvasMouseY, deepest.id)) {
      effectiveParentId = String((deepest as any).parentNode || '') || null;
      label = effectiveParentId ? '拖出容器' : '拖到根层级';
    } else if (pointInContainerBody(canvasMouseX, canvasMouseY, deepest.id)) {
      if (deepest.id !== currentParentId) {
        effectiveParentId = deepest.id;
        label = '放入容器';
        const r = getNodeRect(deepest.id);
        if (r) containerHighlight = {x: r.left, y: r.top, width: r.width, height: r.height};
      }
    }
  }

  // 同级缝隙（同一 effectiveParentId，按 DOM x 排序，与鼠标 canvas x 比较）
  const siblings = nodes.value
      .filter(n => n.id !== draggingId && (String((n as any).parentNode || '') || null) === effectiveParentId)
      .sort((a, b) => (a.position?.x ?? 0) - (b.position?.x ?? 0));

  const canvasMouseX = mouseClientX - (getCanvasRect()?.left ?? 0);
  let insertIndex = siblings.length;
  let line: { x: number; y: number; height: number } | null = null;

  if (siblings.length === 0) {
    if (effectiveParentId) {
      const p = getNodeRect(effectiveParentId);
      if (p) line = {x: p.left + 16, y: p.top + 64 + 16, height: Math.max(p.height - 64 - 32, 40)};
    } else {
      const canvasMouseY = mouseClientY - (getCanvasRect()?.top ?? 0);
      line = {x: canvasMouseX, y: canvasMouseY - 20, height: 40};
    }
    insertIndex = 0;
  } else {
    let placed = false;
    for (let i = 0; i < siblings.length; i++) {
      const r = getNodeRect(siblings[i].id);
      if (!r) continue;
      const center = r.left + r.width / 2;
      if (canvasMouseX < center) {
        insertIndex = i;
        line = {x: r.left - 6, y: r.top, height: r.height};
        placed = true;
        break;
      }
    }
    if (!placed) {
      const lastRect = getNodeRect(siblings[siblings.length - 1].id);
      if (lastRect) line = {x: lastRect.right + 6, y: lastRect.top, height: lastRect.height};
      insertIndex = siblings.length;
    }
  }

  return {
    targetParentId: effectiveParentId,
    insertIndex,
    line,
    container: containerHighlight,
    label,
  };
};

/**
 * 拖拽开始：记录起始父级与下标，用于松手时判断是否真的换了位置
 */
const onNodeDragStart = (event: NodeMouseEvent) => {
  if (!props.hasUpdatePermission) return;
  // 调试期间不允许拖拽（nodes-draggable 已禁用，这里兜底防止产生悬挂的拖拽指示态）
  if (props.isDebugging) return;
  const id = event.node.id;
  const parentKey = String((event.node as any).parentNode || '') || null;
  const siblings = nodes.value
      .filter(n => (String((n as any).parentNode || '') || null) === parentKey)
      .sort((a, b) => (a.position?.x ?? 0) - (b.position?.x ?? 0));
  dropIndicator.value.draggingId = id;
  dropIndicator.value.originParentId = parentKey;
  dropIndicator.value.originIndex = siblings.findIndex(n => n.id === id);
  setDraggingNodeStyle(id);
};

/**
 * 拖拽中：计算落点并高亮显示。
 * 用 requestAnimationFrame 节流，避免每个 mousemove 都触发 elementsFromPoint（昂贵的 DOM 命中测试）。
 */
let dragRaf = 0;
let lastDragEvent: NodeMouseEvent | null = null;

const processDrag = (event: NodeMouseEvent) => {
  if (!dropIndicator.value.draggingId) return;
  const draggingId = dropIndicator.value.draggingId;
  const evt = event.event as MouseEvent;
  const target = computeDropTarget(evt.clientX, evt.clientY, draggingId);
  dropIndicator.value = {
    ...dropIndicator.value,
    draggingId,
    targetParentId: target.targetParentId,
    insertIndex: target.insertIndex,
    line: target.line,
    container: target.container,
    label: target.label,
  };

  // 高亮插入位置两侧的节点
  highlightSiblings(draggingId, target.targetParentId);
};

const onNodeDrag = (event: NodeMouseEvent) => {
  if (props.isDebugging) return;
  if (!dropIndicator.value.draggingId) return;
  lastDragEvent = event;
  if (dragRaf) return;
  dragRaf = requestAnimationFrame(() => {
    dragRaf = 0;
    const ev = lastDragEvent;
    lastDragEvent = null;
    if (ev) processDrag(ev);
  });
};

const cancelDragRaf = () => {
  if (dragRaf) {
    cancelAnimationFrame(dragRaf);
    dragRaf = 0;
  }
  lastDragEvent = null;
};

/**
 * 拖拽结束：根据落点执行重排序/改父级，然后保存
 */
const onNodeDragStop = async (_event: NodeDragEvent) => {
  // 清理必须无条件执行：保存失败/无权限/调试中等任何路径都不能留下悬挂的蓝框高亮和落点指示
  cancelDragRaf();
  try {
    if (!props.hasUpdatePermission) return;
    if (props.isDebugging) return;
    const {draggingId, targetParentId, insertIndex, originParentId, originIndex} = dropIndicator.value;
    if (!draggingId || insertIndex == null) {
      return;
    }

    // 落点与起点相同 → 视为只是挪动画布位置，不改顺序
    const samePlace = targetParentId === originParentId && insertIndex === originIndex;
    if (samePlace) {
      return;
    }

    await applyReorder(draggingId, targetParentId, insertIndex);
  } finally {
    resetDropIndicator();
    clearSiblingHighlight();
    setDraggingNodeStyle(null);
  }
};

/**
 * 在本地构建新的节点顺序，并保存到后端。
 * 直接使用拖拽时计算出的 insertIndex 插入到目标父级；
 * graphToStepsFromGroups 会按 grouped 中已排好的顺序生成 orderIndex，不再二次按 x 重排。
 */
const applyReorder = async (draggingId: string, targetParentId: string | null, insertIndex: number) => {
  // 按当前 parentNode 关系分组，组内保持当前顺序
  const grouped = new Map<string, Node[]>();
  nodes.value.forEach(n => {
    const key = String((n as any).parentNode || '') || 'root';
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key)!.push(n);
  });

  // 提取被拖拽节点
  let dragNode: Node | undefined;
  for (const [key, list] of grouped) {
    const idx = list.findIndex(n => n.id === draggingId);
    if (idx >= 0) {
      dragNode = list[idx];
      list.splice(idx, 1);
      if (list.length === 0) grouped.delete(key);
    }
  }
  if (!dragNode) return;

  // 放到目标父级的新位置
  const parentKey = targetParentId ?? 'root';
  const targetList = grouped.get(parentKey) || [];
  // 目标父级内按 x 排序，作为插入参照
  const sortedTarget = [...targetList].sort((a, b) => (a.position?.x ?? 0) - (b.position?.x ?? 0));
  const safeIndex = Math.max(0, Math.min(insertIndex, sortedTarget.length));
  sortedTarget.splice(safeIndex, 0, dragNode);
  grouped.set(parentKey, sortedTarget);

  // 把所有节点重新摆成树，并更新 parentNode / orderIndex
  const steps = graphToStepsFromGroups(grouped);
  const flatSteps: StepVO[] = [];
  const flatten = (list: StepVO[]) => {
    for (const s of list) {
      flatSteps.push(s);
      if (s.children) flatten(s.children);
    }
  };
  flatten(steps);

  const realSteps = flatSteps.filter(s => !String(s.id).startsWith('temp-step-'));
  if (realSteps.length === 0) return;

  try {
    const payload = realSteps.map(s => ({
      ...s,
      children: undefined,
    }));
    const result = await updateStepSort(payload);
    if (result.data) {
      emits('refresh');
    }
  } catch (e) {
    Message.error('保存步骤顺序失败');
  }
};

/**
 * 从分组后的 Map 重新构建 StepVO 树：组内直接使用 grouped 中的顺序生成 orderIndex，
 * 不再按 x 坐标重排，避免覆盖拖拽时显式指定的插入位置。
 */
const graphToStepsFromGroups = (grouped: Map<string, Node[]>): StepVO[] => {
  const build = (parentKey: string, parentId: number): StepVO[] => {
    const list = [...(grouped.get(parentKey) || [])];
    return list.map((n, index) => {
      const data = n.data || {};
      const idStr = String(data.id);
      const node: StepVO = {
        ...data,
        id: data.id,
        stepType: data.stepType,
        stepName: data.stepName,
        parentId: parentId as number,
        orderIndex: index + 1,
        children: build(idStr, data.id),
      };
      return node;
    });
  };
  return build('root', 0);
};

/**
 * 高亮落点两侧的节点
 */
const highlightedIds = ref<Set<string>>(new Set());

const highlightSiblings = (draggingId: string, targetParentId: string | null) => {
  const parentKey = targetParentId ?? 'root';
  const siblings = nodes.value
      .filter(n => n.id !== draggingId && (String((n as any).parentNode || '') || 'root') === parentKey)
      .sort((a, b) => (a.position?.x ?? 0) - (b.position?.x ?? 0));

  // 计算当前鼠标应该插入到哪两个之间，给这两个加高亮类
  const target = dropIndicator.value;
  const index = target.insertIndex ?? 0;
  const ids: string[] = [];
  if (siblings[index]) ids.push(siblings[index].id);
  if (siblings[index - 1]) ids.push(siblings[index - 1].id);

  // 同步到 DOM
  clearSiblingHighlight();
  ids.forEach(id => {
    highlightedIds.value.add(id);
    const el = getNodeElById(id);
    if (el) el.classList.add('drop-sibling-highlight');
  });
};

const clearSiblingHighlight = () => {
  highlightedIds.value.forEach(id => {
    const el = getNodeElById(id);
    if (el) el.classList.remove('drop-sibling-highlight');
  });
  highlightedIds.value.clear();
};

// ==================== 被拖拽节点半透明效果 ====================
// 调试开始时清理所有拖拽反馈状态（落点指示线、兄弟高亮蓝框、拖拽半透明），避免蓝条残留
watch(() => props.isDebugging, (debugging) => {
  if (debugging) {
    cancelDragRaf();
    resetDropIndicator();
    clearSiblingHighlight();
    setDraggingNodeStyle(null);
  }
});

let currentDraggingId: string | null = null;

const setDraggingNodeStyle = (id: string | null) => {
  if (currentDraggingId) {
    const prev = getNodeElById(currentDraggingId);
    if (prev) prev.classList.remove('is-dragging');
  }
  currentDraggingId = id;
  if (id) {
    const el = getNodeElById(id);
    if (el) el.classList.add('is-dragging');
  }
};

/**
 * 保存顺序和父节点关系（兼容旧调用）
 */
const saveOrderAndParent = async () => {
  if (!props.hasUpdatePermission) return;
  const grouped = new Map<string, Node[]>();
  nodes.value.forEach(n => {
    const key = String((n as any).parentNode || '') || 'root';
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key)!.push(n);
  });
  const steps = graphToStepsFromGroups(grouped);
  const flatSteps: StepVO[] = [];
  const flatten = (list: StepVO[]) => {
    for (const s of list) {
      flatSteps.push(s);
      if (s.children) flatten(s.children);
    }
  };
  flatten(steps);
  const realSteps = flatSteps.filter(s => !String(s.id).startsWith('temp-step-'));
  if (realSteps.length === 0) return;
  try {
    const payload = realSteps.map(s => ({
      ...s,
      children: undefined,
    }));
    const result = await updateStepSort(payload);
    if (result.data) emits('refresh');
  } catch (e) {
    Message.error('保存步骤顺序失败');
  }
};

/**
 * Pane 准备就绪：首次适应画布
 */
const onPaneReady = () => {
  isPaneReady.value = true;
  nextTick(() => fitView({padding: 0.2}));
};

/**
 * 打开步骤编辑抽屉
 */
const editStep = async (step: StepVO) => {
  if (!step.id || String(step.id).startsWith('temp-step-')) return;
  try {
    const result = await getStepDetail(step.id as number);
    const testStepData = result.data as unknown as TestStep;
    currentStep.value = testStepData as unknown as StepVO;
    currentStepDetail.value = {};
    const rawDetail = testStepData.stepDetail;
    currentStepDetail.value = typeof rawDetail === 'string' ? JSON.parse(rawDetail || '{}') : (rawDetail || {});
    currentStepDetail.value.orderIndex = testStepData.orderIndex;
    currentStepDetail.value.stepType = testStepData.stepType;
    currentOperationFormConfig.value = STEP_REGISTRY[testStepData.stepType as keyof typeof STEP_REGISTRY] || [];
    stepFormVisible.value = true;
  } catch (e) {
    Message.error('获取步骤详情失败');
  }
};

/**
 * 保存步骤：新增或更新
 */
const handleSubmit = async (stepDetail: any) => {
  if (!currentStep.value) return;
  // 调试锁定兜底：运行中禁止一切修改；暂停/失败挂起仅未执行步骤可保存
  if (mutationsLocked.value || isCurrentStepLocked.value) {
    Message.warning('调试中，该步骤不可修改');
    return;
  }

  const isNew = !currentStep.value.id;
  const testStep = new TestStep();
  testStep.stepName = stepDetail.stepName;
  testStep.stepType = currentStep.value.stepType;
  testStep.parentId = currentStep.value.parentId != null ? String(currentStep.value.parentId) : '0';
  testStep.stepDetail = stepDetail;
  testStep.projectId = currentStep.value.projectId || String((projectStore as any).getProjectId || '');
  testStep.scenarioId = currentStep.value.scenarioId || String(props.sceneId || '');

  if (!isNew) {
    testStep.id = currentStep.value.id;
  }

  try {
    const result = isNew ? await addStep(testStep) : await updateStep(testStep);
    if (result.data) {
      Message.success({content: isNew ? '添加成功' : '更新成功', duration: 1000});
      stepFormVisible.value = false;
      currentStep.value = null;
      currentStepDetail.value = {};
      emits('refresh');
    }
  } catch (e) {
    Message.error(isNew ? '添加失败' : '更新失败');
  }
};

const handleCancel = () => {
  currentStep.value = null;
  currentStepDetail.value = {};
};

/**
 * 查看步骤调试结果
 */
const handleViewResult = (step: StepVO) => {
  if (!step.id || props.stepDebugList.length === 0) return;

  // 构建 debugResultMap（和 NestedComponent 一致）
  const debugResultMap = new Map<string | number, any>();
  const traverse = (arr: any[]) => {
    for (const item of arr) {
      if (item.step?.id != null && !debugResultMap.has(item.step.id)) {
        debugResultMap.set(item.step.id, item);
      }
      if (item.childrenResults?.length > 0) {
        traverse(item.childrenResults);
      }
    }
  };
  traverse(props.stepDebugList);

  const result = debugResultMap.get(step.id);
  if (!result) return;

  const stepType = step.stepType;
  const isApiOrLoop = stepType === 'API_REQUEST' || stepType === 'SQL'
      || result.step?.stepType === 'API_REQUEST' || result.step?.stepType === 'SQL'
      || ['FOR', 'WHILE', 'IF'].includes(stepType || result.step?.stepType);

  if (isApiOrLoop) {
    // 补充 stepDetail 用于重新发送
    result.step.stepDetail = (step as any).stepDetail;

    // 如果该步骤是子步骤，收集所有执行记录
    const isTopLevel = props.stepDebugList.some((s: any) => s.step?.id == step.id);
    if (!isTopLevel) {
      const allExecutions: any[] = [];
      props.stepDebugList.forEach((s: any) => {
        if (s.childrenResults) {
          s.childrenResults.forEach((r: any) => {
            if (r.step?.id == step.id) {
              allExecutions.push(r);
            }
          });
        }
      });
      if (allExecutions.length > 1) {
        currentApiStepResult.value = {
          step: result.step,
          result: result.result,
          childrenResults: allExecutions
        };
        apiStepDebugResultVisible.value = true;
        return;
      }
    }

    currentApiStepResult.value = result;
    apiStepDebugResultVisible.value = true;
  } else {
    currentStepDebugResult.value = result;
    debugResultDrawerVisible.value = true;
  }
};

/**
 * 适应画布
 */
const handleFitView = () => {
  fitView({padding: 0.2});
};

/**
 * 自动布局
 */
const handleAutoLayout = () => {
  const layouted = layoutGraph(nodes.value);
  nodes.value = layouted;
  edges.value = nodesToEdges(layouted);
  nextTick(() => fitView({padding: 0.2}));
};

/**
 * 在根下添加步骤
 */
const handleAddRootStep = (type: string, source?: 'new' | 'import') => {
  addNewStep(type, 0, source);
};

// ===== API/SQL 步骤：「新建 / 引入已有」入口拆分 =====
const apiSourcePickerVisible = ref(false);
const apiSourcePickerType = ref<'HTTP' | 'SQL'>('HTTP');
// 引入模式挂起的上下文：选定来源后继续打开步骤抽屉
const pendingCanvasStepContext = ref<{ type: string; parentId: number } | null>(null);

// 来源选择器确认：带着引入副本继续打开抽屉
const handleApiSourcePicked = (apiDetail: any) => {
  const ctx = pendingCanvasStepContext.value;
  pendingCanvasStepContext.value = null;
  if (!ctx) return;
  openNewStepDrawer(ctx.type, ctx.parentId, buildImportedStepData(apiDetail));
};

/**
 * 添加新步骤：source='new' 带空白模板直接开抽屉；source='import' 先弹来源选择器
 */
const addNewStep = (type: string, parentId: number, source?: 'new' | 'import') => {
  if (!props.hasUpdatePermission || !props.sceneId) return;
  if (mutationsLocked.value) {
    Message.warning('调试运行中，暂停后可新增步骤');
    return;
  }

  if (type === 'api_request' || type === 'sql') {
    if (source === 'import') {
      apiSourcePickerType.value = type === 'sql' ? 'SQL' : 'HTTP';
      pendingCanvasStepContext.value = {type, parentId};
      apiSourcePickerVisible.value = true;
      return;
    }
    openNewStepDrawer(type, parentId, type === 'sql' ? buildNewSqlStepData() : buildNewHttpStepData());
    return;
  }
  openNewStepDrawer(type, parentId, null);
};

/**
 * 打开新步骤编辑抽屉（可携带 API/SQL 来源数据：新建模板 / 引入副本）
 */
const openNewStepDrawer = (type: string, parentId: number, sourceData: ApiStepSourceData | null) => {
  const defaultDetail = {...createStep(type as any)} as any;
  // 新建/引入时用来源名称作为默认步骤名
  const stepName = sourceData?.apiName || defaultDetail.stepName || getStepTypeChinese(type.toUpperCase() as StepType);

  // 构造一个新步骤对象用于编辑
  currentStep.value = {
    id: undefined,
    stepType: type.toUpperCase(),
    stepName: stepName,
    parentId: parentId,
    orderIndex: 1,
    projectId: String((projectStore as any).getProjectId || ''),
    scenarioId: String(props.sceneId),
    children: []
  } as StepVO;

  currentStepDetail.value = {
    ...defaultDetail,
    stepType: type.toUpperCase(),
    stepName: stepName,
    orderIndex: 1,
  };
  if (sourceData) {
    currentStepDetail.value.apiRequestId = sourceData.apiRequestId;
    currentStepDetail.value.apiName = sourceData.apiName;
    currentStepDetail.value.apiConfig = sourceData.apiConfig;
  }

  currentOperationFormConfig.value = STEP_REGISTRY[type.toUpperCase() as keyof typeof STEP_REGISTRY] || [];
  stepFormVisible.value = true;
};

/**
 * 删除步骤
 */
const deleteStep = async (nodeId: string | number) => {
  if (!props.hasDeletePermission) return;
  if (String(nodeId).startsWith('temp-step-')) return;
  if (isStepLocked(nodeId)) {
    Message.warning('调试中，已执行步骤不可删除');
    return;
  }

  Modal.confirm({
    title: '确认删除',
    content: '删除后将不可恢复，是否继续？',
    okText: '删除',
    cancelText: '取消',
    okButtonProps: {status: 'danger'},
    onOk: async () => {
      try {
        const result = await deleteStepById(nodeId as number);
        if (result.data) {
          Message.success({content: '删除成功', duration: 1000});
          emits('refresh');
        }
      } catch (e) {
        Message.error('删除失败');
      }
    }
  });
};

/**
 * 右键菜单处理
 */
const showContextMenu = (event: MouseEvent, node?: Node) => {
  event.preventDefault();
  event.stopPropagation();
  contextMenu.value = {
    visible: true,
    x: event.clientX,
    y: event.clientY,
    nodeId: node?.id,
    isContainer: node ? isContainerNode(node) : false,
  };
};

const hideContextMenu = () => {
  contextMenu.value.visible = false;
};

const onContextMenuVisibleChange = (visible: boolean) => {
  if (!visible) {
    contextMenu.value.visible = false;
  }
};

// 解析添加步骤的菜单值：'api_request:new' → {type: 'api_request', source: 'new'}
const parseAddStepValue = (raw: string): { type: string; source?: 'new' | 'import' } => {
  const [type, source] = raw.split(':');
  return {type, source: source as 'new' | 'import' | undefined};
};

const handleContextMenuSelect = (value: any) => {
  hideContextMenu();
  const str = String(value);
  if (str === 'edit' && contextMenu.value.nodeId) {
    const node = nodes.value.find(n => n.id === contextMenu.value.nodeId);
    if (node) editStep(node.data as StepVO);
  } else if (str === 'runUntil' && contextMenu.value.nodeId) {
    const node = nodes.value.find(n => n.id === contextMenu.value.nodeId);
    if (node) emits('run-until-step', node.data as StepVO);
  } else if (str === 'delete' && contextMenu.value.nodeId) {
    deleteStep(contextMenu.value.nodeId);
  } else if (str === 'moveOut' && contextMenu.value.nodeId) {
    moveOutOfContainer(contextMenu.value.nodeId);
  } else if (str.startsWith('move-into:')) {
    moveIntoContainer(contextMenu.value.nodeId!, str.replace('move-into:', ''));
  } else if (str.startsWith('add-root:')) {
    const {type, source} = parseAddStepValue(str.replace('add-root:', ''));
    addNewStep(type, 0, source);
  } else if (str.startsWith('add-adj:') && contextMenu.value.nodeId) {
    const node = nodes.value.find(n => n.id === contextMenu.value.nodeId);
    if (node) {
      const {type, source} = parseAddStepValue(str.replace('add-adj:', ''));
      addNewStep(type, node.data?.parentId || 0, source);
    }
  } else if (str.startsWith('add-child:') && contextMenu.value.nodeId) {
    const node = nodes.value.find(n => n.id === contextMenu.value.nodeId);
    if (node) {
      const {type, source} = parseAddStepValue(str.replace('add-child:', ''));
      addNewStep(type, node.data?.id || 0, source);
    }
  }
};

/**
 * 移出当前容器：移到祖父层级（容器的父级），追加到末尾。
 */
const moveOutOfContainer = async (nodeId: string) => {
  if (!props.hasUpdatePermission) return;
  if (isStepLocked(nodeId)) {
    Message.warning('调试中，已执行步骤不可移动');
    return;
  }
  const node = nodes.value.find(n => n.id === nodeId);
  const parentId = String((node as any)?.parentNode || '') || null;
  if (!parentId) return;
  const parentNode = nodes.value.find(n => n.id === parentId);
  const grandParentId = String((parentNode as any)?.parentNode || '') || null;
  await applyReorder(nodeId, grandParentId, Number.MAX_SAFE_INTEGER);
};

/**
 * 移入指定容器：追加到该容器子列表末尾。
 */
const moveIntoContainer = async (nodeId: string, containerId: string) => {
  if (!props.hasUpdatePermission || !nodeId) return;
  if (isStepLocked(nodeId)) {
    Message.warning('调试中，已执行步骤不可移动');
    return;
  }
  await applyReorder(nodeId, containerId, Number.MAX_SAFE_INTEGER);
};

// 为画布和节点绑定右键事件
watch(() => isPaneReady.value, (ready) => {
  if (!ready) return;
  nextTick(() => {
    const canvasEl = document.querySelector('.scene-workflow-canvas .vue-flow__pane');
    if (canvasEl) {
      canvasEl.addEventListener('contextmenu', (e) => {
        // 如果点击的是节点，不处理
        const target = e.target as HTMLElement;
        if (target.closest('.vue-flow__node')) return;
        showContextMenu(e as MouseEvent);
      });
    }
  });
});

// 监听 nodes 变化，为新节点绑定右键事件
watch(() => nodes.value, () => {
  nextTick(() => {
    document.querySelectorAll('.scene-workflow-canvas .vue-flow__node').forEach(el => {
      el.removeEventListener('contextmenu', handleNodeContextMenu as any);
      el.addEventListener('contextmenu', handleNodeContextMenu as any);
    });
  });
}, {deep: true});

const handleNodeContextMenu = (event: MouseEvent) => {
  const nodeEl = (event.target as HTMLElement).closest('.vue-flow__node') as HTMLElement;
  if (!nodeEl) return;
  const nodeId = nodeEl.getAttribute('data-id');
  const node = nodes.value.find(n => n.id === nodeId);
  if (node) showContextMenu(event, node);
};
</script>

<style scoped>
.scene-workflow-canvas {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
  position: relative;
}

.workflow-toolbar {
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #e5e6eb;
  display: flex;
  align-items: center;
  justify-content: flex-end;
}

.workflow-flow {
  flex: 1;
  min-height: 0;
}

.workflow-context-menu {
  position: fixed;
  z-index: 1000;
}

:deep(.vue-flow__node) {
  border: none;
  background: transparent;
  padding: 0;
}

:deep(.vue-flow__node.selected) {
  box-shadow: none;
}

:deep(.vue-flow__edge-path) {
  stroke-width: 2;
}

:deep(.vue-flow__handle) {
  opacity: 0;
  transition: opacity 0.2s;
}

:deep(.vue-flow__node:hover .vue-flow__handle) {
  opacity: 1;
}

:deep(.vue-flow__node.is-dragging) {
  opacity: 0.6;
}

:deep(.vue-flow__node.is-dragging .workflow-step-node),
:deep(.vue-flow__node.is-dragging .workflow-group-node) {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15);
}

:deep(.vue-flow__node.drop-sibling-highlight > *) {
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.35) !important;
  border-color: rgb(var(--arcoblue-6)) !important;
}

:deep(.vue-flow__node.drop-target-highlight > *) {
  box-shadow: 0 0 0 4px rgba(0, 180, 42, 0.35) !important;
  border-color: rgb(var(--green-6)) !important;
}

</style>
