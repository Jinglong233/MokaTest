<template>
  <div class="api-request-step-editor" :style="{ height: editorHeight }">
    <!-- 步骤名称 + API来源：紧凑单行（纵向两个 form-item 会占 ~160px，挤压参数区高度） -->
    <div class="step-header-row">
      <a-input
          v-model="localData.stepName"
          class="step-name-input"
          placeholder="请输入步骤名称（必填）"
          allow-clear
          :max-length="50"
          :disabled="disabled"
          @input="formDirty = true"
      />
      <div class="source-info">
        <a-tag v-if="localData.apiRequestId && sourceDeleted" color="gray" size="small">
          来源: {{ localData.apiName || '未命名' }}（已删除）
        </a-tag>
        <a-tag v-else-if="localData.apiRequestId" color="arcoblue" size="small">
          来源: {{ localData.apiName || '未命名' }}
        </a-tag>
        <a-tag v-else-if="localData.apiConfig" color="green" size="small">新建接口</a-tag>
        <a-tag v-else color="gray" size="small">未配置</a-tag>
        <!-- 仅绑定了来源的步骤才显示「更换来源」；新建的空白接口没有来源，不显示 -->
        <a-button v-if="localData.apiRequestId || !localData.apiConfig" :disabled="disabled" type="text" size="small" @click="showSelectDrawer = true">
          <template #icon>
            <icon-swap/>
          </template>
          更换来源
        </a-button>
        <a-button
            v-if="localData.apiRequestId"
            :disabled="disabled || sourceDeleted"
            :loading="syncLoading"
            type="text"
            size="small"
            @click="handleSyncSource"
        >
          <template #icon>
            <icon-refresh/>
          </template>
          同步来源
        </a-button>
      </div>
    </div>

    <!-- API配置编辑器 + 单步调试结果：结果区可折叠，空结果时收起把高度让给参数区 -->
    <div class="api-config-editor" v-if="localData.apiConfig">
      <a-divider style="margin: 8px 0"/>
      <div class="editor-pane">
        <ApiDebugForm
            ref="apiDebugFormRef"
            v-model="localData.apiConfig"
            mode="scene"
            :scene-environment-id="sceneEnvironmentId"
            :disabled="disabled"
            @change="handleApiConfigChange"
            @send="handleSend"
        />
      </div>
      <div class="result-pane" :class="{ collapsed: resultCollapsed }">
        <div class="result-header" @click="resultCollapsed = !resultCollapsed">
          <icon-right class="result-caret" :class="{ expanded: !resultCollapsed }"/>
          <span class="result-title">调试结果</span>
          <span v-if="!debugResult && !debugLoading" class="result-hint">（点击「发送」后查看）</span>
          <span v-else-if="resultCollapsed" class="result-hint">（已生成，点击展开）</span>
        </div>
        <div v-show="!resultCollapsed" class="result-body">
          <a-spin :loading="debugLoading" tip="正在调试..." class="step-debug-spin">
            <div class="step-debug-result-wrapper">
              <ApiDebugResult v-if="debugResult" :debug-result="debugResult"/>
              <a-empty v-else description="点击发送后查看调试结果"/>
            </div>
          </a-spin>
        </div>
      </div>
    </div>
    <a-empty v-else description="请点击上方「更换来源」选择已有用例"/>

    <!-- 引入已有用例选择器 -->
    <ApiSourcePickerModal
        v-model:visible="showSelectDrawer"
        api-type="HTTP"
        @select="handleChangeSource"
    />
  </div>
</template>

<script setup lang="ts">
import {reactive, ref, watch, onMounted} from 'vue';
import {Message, Modal} from '@arco-design/web-vue';
import ApiDebugForm from '@/views/apiManager/component/ApiDebugForm.vue';
import ApiDebugResult from '@/views/apiManager/component/ApiDebugResult.vue';
import ApiSourcePickerModal from '@/views/scene/component/ApiSourcePickerModal.vue';
import {getApiById, getInterfaceCaseTree, debugByConfig} from '@/api/MyApi/apiInterface';
import {buildImportedStepData} from '@/views/scene/component/apiStepTemplate';
import {useProjectStore} from '@/store';

interface ApiRequestStepData {
  stepName?: string;
  stepType?: string;
  apiRequestId?: number | null;
  apiName?: string;
  apiConfig?: any;
  [key: string]: any;
}

const props = withDefaults(defineProps<{
  modelValue: ApiRequestStepData;
  /**
   * 场景关联的环境ID
   * 传入后，接口配置中的环境选择会自动选中该环境
   */
  sceneEnvironmentId?: number;
  /**
   * 是否只读
   */
  disabled?: boolean;
  /**
   * 编辑器根高度（由抽屉传入其视口高度，默认 80vh）。
   * 抽屉内 a-scrollbar 的内容区是内容撑高的，没有确定高度，
   * 显式给根高度后，表单/结果两个半区才能按 50/50 分配。
   */
  editorHeight?: string;
}>(), {
  editorHeight: '80vh',
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: ApiRequestStepData): void;
}>();

const apiDebugFormRef = ref();

// 本地数据
const localData = reactive<ApiRequestStepData>({
  stepName: '',
  apiRequestId: null,
  apiName: '',
  apiConfig: null,
});

// 选择用例弹窗
const showSelectDrawer = ref(false);

// 表单是否有未保存修改（发送不会清除，用于抽屉关闭时的未保存确认）
const formDirty = ref(false);

// 初始化数据（保留原始数据中的所有字段）
const initData = (data: ApiRequestStepData) => {
  Object.assign(localData, {
    ...data,
    stepName: data.stepName || '',
    apiRequestId: data.apiRequestId || null,
    apiName: data.apiName || '',
    apiConfig: data.apiConfig ? JSON.parse(JSON.stringify(data.apiConfig)) : null,
  });
  formDirty.value = false;
};

// 监听外部数据变化
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    initData(newVal);
  }
}, {deep: true, immediate: true});

// API配置变更时同步（保留原始数据中的所有字段）
const handleApiConfigChange = (hasChanges?: boolean) => {
  formDirty.value = hasChanges ?? true;
  emit('update:modelValue', {...props.modelValue, ...localData});
};

// 单步调试：不保存，直接按当前表单配置发送（场景副本可能没有 id，走 debugByConfig）
const debugLoading = ref(false);
const debugResult = ref<any>(null);
// 调试结果区默认收起（把高度让给参数区），发送/有结果时自动展开
const resultCollapsed = ref(true);
watch([debugLoading, debugResult], () => {
  if (debugLoading.value || debugResult.value) resultCollapsed.value = false;
});
const handleSend = async (data: any) => {
  debugLoading.value = true;
  try {
    const res = await debugByConfig(data);
    if (res.data) {
      debugResult.value = res.data;
    } else {
      debugResult.value = null;
      Message.error({content: res.msg || '调试失败', duration: 2000});
    }
  } catch (e: any) {
    debugResult.value = null;
    Message.error({content: '调试异常：' + (e.message || '未知错误'), duration: 2000});
  } finally {
    debugLoading.value = false;
  }
};

// 更换来源：已有来源时先确认（当前配置将被新来源覆盖），再应用选中用例
const handleChangeSource = (data: any) => {
  if (props.disabled) return;
  if (localData.apiRequestId) {
    Modal.confirm({
      title: '更换来源',
      content: '当前步骤配置将被新来源的副本覆盖，步骤内已做的修改将丢失，是否继续？',
      okText: '更换',
      cancelText: '取消',
      onOk: () => applySelectedApi(data),
    });
    return;
  }
  applySelectedApi(data);
};

// 应用选中的用例：深拷贝为步骤副本（id 置空，apiRequestId 仅作来源标记）
const applySelectedApi = (data: any) => {
  if (props.disabled) return;
  const sourceData = buildImportedStepData(data);
  localData.apiRequestId = sourceData.apiRequestId;
  localData.apiName = sourceData.apiName;
  localData.apiConfig = sourceData.apiConfig;
  if (!localData.stepName) {
    localData.stepName = sourceData.apiName;
  }
  formDirty.value = true;
  emit('update:modelValue', {...props.modelValue, ...localData});
  Message.success({content: `已选择用例【${sourceData.apiName}】`, duration: 2000});
};

// 来源接口/用例是否已被删除（步骤副本不受影响，仅提示）
const sourceDeleted = ref(false);
const syncLoading = ref(false);
const projectStore = useProjectStore();

// 同步来源：拉取来源最新配置覆盖当前副本（会丢失步骤内的修改，需确认）
const handleSyncSource = () => {
  if (props.disabled || !localData.apiRequestId) return;
  Modal.confirm({
    title: '同步来源',
    content: '将用来源用例的最新配置覆盖当前步骤配置，步骤内已做的修改将丢失，是否继续？',
    okText: '同步',
    cancelText: '取消',
    onOk: async () => {
      syncLoading.value = true;
      try {
        const {data} = await getApiById(localData.apiRequestId!);
        applySelectedApi(data);
        Message.success({content: '已同步来源最新配置', duration: 2000});
      } catch (e: any) {
        if (e?.msg?.includes('不存在')) {
          // 来源已删除：全局拦截器已弹出「接口不存在」，这里只标灰，不再重复提示
          sourceDeleted.value = true;
        } else if (!e?.code) {
          // 业务错误码的全局拦截器已提示；仅网络级异常才补充提示
          Message.error({content: '同步失败：' + (e.message || '未知错误'), duration: 2000});
        }
      } finally {
        syncLoading.value = false;
      }
    },
  });
};

// 拍平树节点 id 列表
const flattenTreeIds = (nodes: any[]): number[] =>
    nodes.flatMap(n => [n.id, ...flattenTreeIds(n.children || [])]);

// 来源存在性检查：打开抽屉时校验一次，源已删除则标灰提示（不阻塞编辑，副本仍可执行）。
// 走树接口而非详情接口：详情接口对不存在的 id 返回错误码，会触发全局拦截器弹错误提示
const checkSourceExists = async () => {
  if (!localData.apiRequestId) {
    sourceDeleted.value = false;
    return;
  }
  try {
    const res = await getInterfaceCaseTree(projectStore.getProjectId as number);
    sourceDeleted.value = !flattenTreeIds(res.data || []).includes(localData.apiRequestId);
  } catch (e) {
    // 检查失败（如网络/权限）不影响编辑，保持原状
  }
};

watch(() => localData.apiRequestId, () => {
  checkSourceExists();
});

// 校验方法
const validate = async () => {
  // 校验步骤名称
  if (!localData.stepName || !localData.stepName.trim()) {
    return {valid: false, errors: {stepName: ['步骤名称不能为空']}};
  }
  // 校验是否有API配置
  if (!localData.apiConfig) {
    return {valid: false, errors: {_apiConfig: ['请点击「更换来源」选择已有用例']}};
  }

  // 校验ApiDebugForm内部的数据
  if (apiDebugFormRef.value) {
    // 先同步当前表单数据
    const currentData = apiDebugFormRef.value.getCurrentData?.();
    if (currentData) {
      localData.apiConfig = currentData;
    }

    if (apiDebugFormRef.value.validateApiName && !apiDebugFormRef.value.validateApiName()) {
      return {valid: false, errors: {apiName: ['接口名称不能为空']}};
    }
    if (apiDebugFormRef.value.validateRequestPath && !apiDebugFormRef.value.validateRequestPath()) {
      return {valid: false, errors: {requestPath: ['请求路径不能为空']}};
    }
    if (apiDebugFormRef.value.validateParameters && !apiDebugFormRef.value.validateParameters()) {
      return {valid: false, errors: {parameters: ['请求参数校验失败']}};
    }
    if (apiDebugFormRef.value.validateExtractions && !apiDebugFormRef.value.validateExtractions()) {
      return {valid: false, errors: {extraction: ['提取规则校验失败']}};
    }
    if (apiDebugFormRef.value.validateAssertions && !apiDebugFormRef.value.validateAssertions()) {
      return {valid: false, errors: {assertion: ['断言规则校验失败']}};
    }
  }

  return {valid: true, data: JSON.parse(JSON.stringify(localData))};
};

// 获取表单数据
const getFormData = () => {
  // 如果ApiDebugForm有数据变更，需要收集
  if (apiDebugFormRef.value) {
    const currentData = apiDebugFormRef.value.getCurrentData?.();
    if (currentData) {
      localData.apiConfig = currentData;
    }
  }
  return JSON.parse(JSON.stringify(localData));
};

// 重置表单
const resetForm = () => {
  initData(props.modelValue);
};

// 暴露方法
defineExpose({
  validate,
  getFormData,
  resetForm,
  // 是否有未保存修改（抽屉关闭确认用）
  isDirty: () => formDirty.value,
});

onMounted(() => {
  checkSourceExists();
});
</script>

<style scoped>
.api-request-step-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  /* 横向截断：宽响应内容不允许撑破抽屉宽度 */
  min-width: 0;
  overflow: hidden;
}

.api-config-editor {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* 步骤名称 + 来源：紧凑单行，窄抽屉下允许换行 */
.step-header-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.step-name-input {
  flex: 1 1 200px;
  min-width: 160px;
}
.source-info {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: nowrap;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
}

/* 打通尺寸链：a-spin 根元素 .arco-spin 是 inline-block（宽度由内容决定，
   会被宽表格无限撑宽导致超出屏幕、横向滚动条不出现），
   必须显式钉回 100% 宽度 + min-width:0，让 overflow 链重新生效 */
.step-debug-spin {
  display: block;
  width: 100%;
  min-width: 0;
  height: 100%;
}
.step-debug-spin :deep(.arco-spin-children) {
  width: 100%;
  min-width: 0;
  height: 100%;
}

.editor-pane {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

/* 调试结果区：展开时与表单各占一半；收起时只剩一条标题栏，高度全让给参数区 */
.result-pane {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  border-top: 1px solid var(--color-border-2);
  display: flex;
  flex-direction: column;
}
.result-pane.collapsed {
  flex: 0 0 auto;
}
.result-header {
  display: flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  flex-shrink: 0;
  cursor: pointer;
  user-select: none;
  color: var(--color-text-2);
}
.result-caret {
  font-size: 12px;
  transition: transform 0.15s;
}
.result-caret.expanded {
  transform: rotate(90deg);
}
.result-title {
  font-weight: 500;
}
.result-hint {
  font-size: 12px;
  font-weight: normal;
  color: var(--color-text-3);
}
.result-body {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}
.step-debug-result-wrapper {
  height: 100%;
  min-width: 0;
  overflow: hidden;
}
</style>
