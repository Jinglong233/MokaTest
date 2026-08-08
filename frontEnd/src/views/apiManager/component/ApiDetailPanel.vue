<template>
  <div class="api-debug-panel">
    <!-- 外层 Tab：调试 | 用例列表（SQL 接口无用例列表，仅剩一个 Tab，隐藏 Tab 条直接展示内容） -->
    <a-tabs v-model:active-key="outerActiveTab" class="outer-tabs" :class="{ 'sql-single-tab': isSqlMode }">
      <a-tab-pane key="debug" title="调试">
        <ApiDebugForm
            ref="apiDebugFormRef"
            v-model="localFormData"
            mode="interface"
            :disabled="!hasApiUpdatePermission"
            @update:model-value="(val) => emit('update:modelValue', val)"
            @change="handleFormChange"
            @save="emit('save')"
            @save-and-debug="(data) => emit('saveAndDebug', data)"
            @save-as-case="(data) => emit('saveAsCase', data)"
        />
      </a-tab-pane>
      <a-tab-pane v-if="!isSqlMode" key="caseList" title="用例列表">
        <div style="display: flex; justify-content: flex-end; margin-bottom: 8px">
          <a-button
              v-if="hasApiCreatePermission && localFormData.id"
              type="outline"
              size="small"
              @click="aiModalRef?.open()"
          >
            <template #icon><icon-robot/></template>
            AI 生成用例
          </a-button>
        </div>
        <ApiCaseList
            ref="apiCaseListRef"
            :source-id="localFormData.id"
            @edit-case="handleEditCase"
        />
      </a-tab-pane>
    </a-tabs>

    <!-- AI 生成用例弹窗（聊天式） -->
    <AiGenerateChatModal
        ref="aiModalRef"
        scene="apiCase"
        :entity-id="localFormData.id || 0"
        @adopted="apiCaseListRef?.refreshCases?.()"
    />

    <!-- 用例编辑抽屉 -->
    <a-drawer
        v-model:visible="caseDrawerVisible"
        :width="1100"
        :footer="false"
        title="编辑用例"
        unmount-on-close
    >
      <a-spin
          class="case-drawer-spin"
          style="height: 100%"
          :loading="caseDebugLoading"
          tip="正在调试..."
      >
        <div class="case-drawer-content">
          <a-split direction="vertical" :min="0.4" :max="0.8" style="height: 100%">
            <template #first>
              <ApiDebugForm
                  ref="caseDebugFormRef"
                  v-model="editingCaseData"
                  mode="case"
                  :disabled="!hasApiUpdatePermission"
                  @change="caseHasChanges = $event"
                  @save="handleCaseSave"
                  @send="handleCaseSend"
              />
            </template>
            <template #second>
              <div class="case-debug-result-wrapper">
                <ApiDebugResult v-if="caseDebugResult" :debug-result="caseDebugResult"/>
                <a-empty v-else description="点击发送后查看调试结果"/>
              </div>
            </template>
          </a-split>
        </div>
      </a-spin>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, ref, watch} from 'vue';
import ApiDebugForm from './ApiDebugForm.vue';
import ApiCaseList from './ApiCaseList.vue';
import ApiDebugResult from './ApiDebugResult.vue';
import AiGenerateChatModal from '@/components/ai-generate/AiGenerateChatModal.vue';
import {ApiRequest} from '@/types/domain/api/ApiRequest';
import {AddApiInterfaceDTO} from '@/types/domain/api/dto/AddApiInterfaceDTO';
import {ApiNodeType} from '@/types/domain/api/apiEnum/ApiNodeType';
import {ApiType} from '@/types/domain/api/apiEnum/ApiType';
import {Message} from "@arco-design/web-vue";
import {saveApi, debug} from '@/api/MyApi/apiInterface';
import {useProjectStore} from '@/store';
import useTeamStore from '@/store/modules/team';
import usePermission from '@/hooks/permission';

const permission = usePermission();
const hasApiUpdatePermission = computed(() => permission.hasPermission('auto:api:update'));
const hasApiCreatePermission = computed(() => permission.hasPermission('auto:api:create'));
const aiModalRef = ref<any>(null);
const isSqlMode = computed(() => localFormData.value.apiType === ApiType.SQL);

const props = defineProps<{
  modelValue?: ApiRequest;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: ApiRequest): void;
  (e: 'change', hasChanges: boolean): void;
  (e: 'save'): void;
  (e: 'saveAndDebug', value: AddApiInterfaceDTO): void;
  (e: 'saveAsCase', value: AddApiInterfaceDTO): void;
}>();

const localFormData = ref<AddApiInterfaceDTO>(
    new AddApiInterfaceDTO(ApiNodeType.INTERFACE)
);

// 用例编辑抽屉
const caseDrawerVisible = ref(false);
const editingCaseData = ref<AddApiInterfaceDTO>(new AddApiInterfaceDTO(ApiNodeType.INTERFACE));
const caseHasChanges = ref(false);
const caseDebugResult = ref<any>(null);
const caseDebugLoading = ref(false);
const apiDebugFormRef = ref();
const caseDebugFormRef = ref();
const apiCaseListRef = ref();

const outerActiveTab = ref('debug');

const teamStore = useTeamStore();

// 处理 ApiDebugForm 数据变化：同步最新数据到 localFormData 和父组件
const handleFormChange = (hasChange: boolean) => {
  const currentData = apiDebugFormRef.value?.getCurrentData?.();
  if (currentData) {
    localFormData.value = JSON.parse(JSON.stringify(currentData));
    emit('update:modelValue', currentData);
  }
  emit('change', hasChange);
};

// 初始化数据
const initData = (data: ApiRequest) => {
  if (data) {
    localFormData.value = JSON.parse(JSON.stringify(data));
  }
};

watch(
    () => props.modelValue,
    (newVal) => {
      if (newVal) {
        initData(newVal);
      }
    },
    { immediate: true }
);

// 切换到用例列表时自动刷新
watch(
    () => outerActiveTab.value,
    (tab) => {
      if (tab === 'caseList') {
        nextTick(() => {
          apiCaseListRef.value?.refreshCases();
        });
      }
    }
);

// SQL 接口没有用例列表 Tab：切到 SQL 接口时强制回「调试」页签，避免停留在已隐藏的 Tab 上导致内容空白
watch(isSqlMode, (isSql) => {
  if (isSql && outerActiveTab.value !== 'debug') {
    outerActiveTab.value = 'debug';
  }
});

// 打开用例编辑抽屉
const handleEditCase = (caseData: ApiRequest) => {
  editingCaseData.value = JSON.parse(JSON.stringify(caseData));
  caseHasChanges.value = false;
  caseDebugResult.value = null;
  caseDrawerVisible.value = true;
};

// 保存用例
const handleCaseSave = async () => {
  const saveData = caseDebugFormRef.value?.getCurrentData();
  if (!saveData) return;

  saveData.projectId = useProjectStore().getProjectId ?? undefined;
  saveData.teamId = teamStore.getTeamId ?? undefined;

  const res = await saveApi(saveData);
  if (res.code === 200) {
    Message.success({ content: '保存用例成功', duration: 2000 });
    // 刷新用例列表
    apiCaseListRef.value?.refreshCases();
    // 关闭抽屉
    caseDrawerVisible.value = false;
  } else {
    Message.error({ content: res.msg || '保存用例失败', duration: 2000 });
  }
};

// 发送用例（调试用例，不保存）
const handleCaseSend = async (data: AddApiInterfaceDTO) => {
  if (!data.id) {
    Message.error({ content: '用例未保存，无法调试', duration: 2000 });
    return;
  }
  caseDebugLoading.value = true;
  try {
    const res = await debug(data.id);
    if (res.code === 200) {
      Message.success({ content: '调试成功', duration: 2000 });
      caseDebugResult.value = res.data;
    } else {
      Message.error({ content: res.msg || '调试失败', duration: 2000 });
    }
  } catch (e) {
    Message.error({ content: '调试异常', duration: 2000 });
  } finally {
    caseDebugLoading.value = false;
  }
};

defineExpose({
  getCurrentData: () => apiDebugFormRef.value?.getCurrentData(),
  resetChanges: () => apiDebugFormRef.value?.resetChanges(),
  saveData: () => apiDebugFormRef.value?.saveData(),
  hasUnsavedChanges: () => apiDebugFormRef.value?.hasUnsavedChanges?.() ?? false,
});
</script>

<style scoped>
.api-debug-panel {
  padding: 12px;
  background: #fff;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 外层 Tab 占满剩余空间 */
.outer-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.outer-tabs :deep(.arco-tabs) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.outer-tabs :deep(.arco-tabs-nav) {
  flex-shrink: 0;
  margin-bottom: 8px;
}

/* SQL 接口只有一个 Tab（用例列表不适用），隐藏外层 Tab 条，内容区自动占满。
   注意必须用 > 只匹配外层直接子级的 nav：内层 ApiDebugForm 里还有一排 Tab（数据库/关联提取/断言），
   用后代选择器会把它们一起隐藏 */
.outer-tabs.sql-single-tab :deep(> .arco-tabs-nav) {
  display: none;
}

.outer-tabs :deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 0;
}

.outer-tabs :deep(.arco-tabs-pane) {
  height: 100%;
  padding: 0;
  overflow: hidden;
}

:deep(.arco-tabs-content-list) {
  height: 100%;
}

/* 用例编辑抽屉内容 */
.case-drawer-spin {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.case-drawer-spin :deep(.arco-spin) {
  height: 100%;
}

.case-drawer-spin :deep(.arco-spin-mask) {
  position: absolute;
}

.case-drawer-content {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 12px;
}

.case-debug-result-wrapper {
  height: 100%;
  overflow: auto;
  background: #fff;
  border-top: 1px solid #e5e5e5;
}
</style>
