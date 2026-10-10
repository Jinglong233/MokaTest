<template>
  <div class="test-case-form" :class="{ 'slim-mode': slim }">
    <a-form :model="formData" layout="vertical">
      <a-row :gutter="20" class="form-row">
        <!-- 左侧主栏：用例内容 -->
        <a-col :xs="24" :lg="slim ? 24 : 16" class="main-col">
          <a-card class="main-card" :bordered="false">
            <a-form-item v-if="!slim" :label="$t('qa.testCase.name')" required class="title-form-item">
              <a-input
                v-model="formData.caseName"
                :placeholder="$t('qa.testCase.namePlaceholder')"
                class="title-input"
                allow-clear
              />
            </a-form-item>

            <a-form-item v-if="fieldVis('preCondition')" :label="$t('qa.testCase.preCondition')" class="precondition-item">
              <RichEditor
                v-model="formData.preCondition"
                :placeholder="$t('qa.testCase.preConditionPlaceholder')"
                :height="slim ? undefined : '260px'"
                :auto-height="slim"
                @uploaded="handleUploadedFile"
              />
            </a-form-item>

            <div class="steps-area">
              <div class="steps-area-label">{{ $t('qa.testCase.steps') }}</div>
              <div class="steps-scroll">
                <div class="steps-table">
                  <div class="steps-table-header">
                    <span class="col-index">{{ $t('qa.testCase.stepIndex') }}</span>
                    <span>{{ $t('qa.testCase.stepDesc') }}</span>
                    <span>{{ $t('qa.testCase.expected') }}</span>
                    <span class="col-actions">{{ $t('qa.common.operation') }}</span>
                  </div>
                  <div
                    v-for="(step, index) in stepList"
                    :key="step.id"
                    class="steps-table-row"
                    :class="{ dragging: dragIndex === index }"
                    :draggable="dragFromHandle"
                    @dragstart="handleDragStart(index)"
                    @dragend="handleDragEnd"
                    @dragover.prevent="handleDragOver(index)"
                    @drop="handleDrop"
                  >
                    <div
                      class="col-index"
                      @mousedown="dragFromHandle = true"
                      @mouseup="dragFromHandle = false"
                    >
                      <icon-drag-dot-vertical class="drag-handle" />
                      <span class="step-number">{{ index + 1 }}</span>
                    </div>
                    <div class="col-step">
                      <a-textarea
                        v-model="step.step"
                        :placeholder="$t('qa.testCase.stepN', { index: index + 1 })"
                        :auto-size="{ minRows: 1, maxRows: 6 }"
                      />
                    </div>
                    <div class="col-expected">
                      <a-textarea
                        v-model="step.expected"
                        :placeholder="$t('qa.testCase.expected')"
                        :auto-size="{ minRows: 1, maxRows: 6 }"
                      />
                    </div>
                    <div class="col-actions">
                      <a-button
                        v-permission="'qa:testcase:update'"
                        type="text"
                        size="mini"
                        @click="copyStep(index)"
                      >
                        <icon-copy />
                      </a-button>
                      <a-button
                        v-permission="'qa:testcase:update'"
                        type="text"
                        status="danger"
                        size="mini"
                        :disabled="stepList.length <= 1"
                        @click="removeStep(index)"
                      >
                        <icon-delete />
                      </a-button>
                    </div>
                  </div>
                </div>
              </div>

              <a-button v-permission="'qa:testcase:update'" type="dashed" long class="add-step-btn" @click="addStep">
                <icon-plus /> {{ $t('qa.testCase.addStep') }}
              </a-button>
            </div>
          </a-card>
        </a-col>

        <!-- 右侧副栏：属性字段（平铺，不再按卡片分组；slim 模式隐藏，字段在列表行内修改） -->
        <a-col v-if="!slim" :xs="24" :lg="8" class="side-col">
          <a-scrollbar class="side-scrollbar">
            <a-form-item v-if="fieldVis('moduleId')" :label="$t('qa.testCase.module')" required>
              <a-tree-select
                v-model="formData.moduleId"
                :data="moduleTreeData"
                :fieldNames="{ key: 'id', title: 'moduleName' }"
                :placeholder="$t('qa.testCase.selectModule')"
              />
            </a-form-item>

            <a-form-item v-if="fieldVis('requirementId')" :label="$t('qa.testCase.relatedRequirement')">
              <a-select
                v-model="formData.requirementIds"
                multiple
                allow-search
                :filter-option="false"
                :placeholder="$t('qa.testCase.searchRequirementPlaceholder')"
                @search="handleSearchRequirement"
                @dropdown-visible-change="handleRequirementDropdownVisibleChange"
                @dropdown-reach-bottom="loadMoreRequirement"
              >
                <a-option v-for="req in requirementOptions" :key="req.id" :value="req.id">{{ req.reqCode }} - {{ req.title }}</a-option>
                <template #dropdownRender="{ menuNode: menu }">
                  <div>
                    <VNodeRenderer :vnodes="menu" />
                    <div v-if="requirementPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                      {{ $t('qa.testCase.scrollLoadMore') }}
                    </div>
                    <div v-else-if="requirementOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                      {{ $t('qa.testCase.noMore') }}
                    </div>
                  </div>
                </template>
              </a-select>
            </a-form-item>

            <a-form-item v-if="fieldVis('priority')" :label="$t('qa.testCase.priorityLevel')" required>
              <a-select v-model="formData.priority" :placeholder="$t('qa.testCase.pleaseSelect')">
                <a-option value="P0">
                  <span class="priority-dot" style="background: #f53f3f;" />P0
                </a-option>
                <a-option value="P1">
                  <span class="priority-dot" style="background: #ff7d00;" />P1
                </a-option>
                <a-option value="P2">
                  <span class="priority-dot" style="background: #165dff;" />P2
                </a-option>
              </a-select>
            </a-form-item>

            <a-form-item v-if="fieldVis('tags')" :label="$t('qa.testCase.tags')" style="margin-bottom: 0;">
              <a-input-tag v-model="formData.tags" :placeholder="$t('qa.testCase.tagsPlaceholder')" allow-clear />
            </a-form-item>
          </a-scrollbar>
        </a-col>
      </a-row>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import {ref, watch, onMounted, computed} from 'vue';
import {useI18n} from 'vue-i18n';
import {Message} from '@arco-design/web-vue';
import {
  IconPlus,
  IconDragDotVertical,
  IconDelete,
  IconCopy
} from '@arco-design/web-vue/es/icon';
import RichEditor from '@/components/rich-editor/index.vue';
import {useProjectStore} from '@/store';
import useProjectConfigStore from '@/store/modules/projectConfig';
import {deleteRichTextImages} from '@/api/MyApi/fileUpload';
import {
  getRequirementList,
  getRequirementDetail,
  saveTestCase,
  updateTestCase,
  getTestCaseDetail,
  getQaModuleTree,
} from '@/api/MyApi/qa';

const props = defineProps<{
  id?: number | string;
  initialData?: any;
  /** 精简模式：隐藏关联需求与右侧属性面板（用于详情抽屉内嵌编辑） */
  slim?: boolean;
}>();

const emit = defineEmits<{
  (e: 'saved'): void;
}>();

const {t} = useI18n();

const projectStore = useProjectStore();
const projectConfigStore = useProjectConfigStore();
const fieldVis = (key: string) => projectConfigStore.isFieldVisible('testCase', key);

const VNodeRenderer = {
  props: ['vnodes'],
  render(this: { vnodes: any }) {
    return this.vnodes;
  }
};

const formData = ref<any>({});
const stepList = ref<any[]>([]);
const isEdit = ref(false);
const initialFileIds = ref<Set<string>>(new Set());
const uploadedFileIds = ref<Set<string>>(new Set());
const initialSnapshot = ref('');

const dragIndex = ref<number | null>(null);
// 仅按住序号列时才允许拖拽排序，避免与文本框内的文字选择冲突
const dragFromHandle = ref(false);

const extractFileIds = (html?: string): Set<string> => {
  if (!html) return new Set();
  const regex = /\/api\/file\/download\?fileId=([^"'\s&)]+)/g;
  const result = new Set<string>();
  let match;
  while ((match = regex.exec(html)) !== null) {
    result.add(decodeURIComponent(match[1]));
  }
  return result;
};

const collectInitialFileIds = () => {
  const set = new Set<string>();
  extractFileIds(formData.value.preCondition).forEach(id => set.add(id));
  stepList.value.forEach((s: any) => {
    extractFileIds(s.step).forEach(id => set.add(id));
    extractFileIds(s.expected).forEach(id => set.add(id));
  });
  initialFileIds.value = set;
  uploadedFileIds.value = new Set();
};

const handleUploadedFile = (fileId: string) => {
  uploadedFileIds.value.add(fileId);
};

const buildComparable = () => {
  return {
    ...formData.value,
    testSteps: stepList.value,
    tags: Array.isArray(formData.value.tags)
      ? formData.value.tags.slice().sort()
      : formData.value.tags,
  };
};

const isDirty = computed(() => {
  return JSON.stringify(buildComparable()) !== initialSnapshot.value;
});

const updateSnapshot = () => {
  initialSnapshot.value = JSON.stringify(buildComparable());
};

const moduleTreeData = ref<any[]>([{id: 0, moduleName: t('qa.testCase.allCases'), children: []}]);

const requirementOptions = ref<any[]>([]);
const requirementPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

const loadModuleTree = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getQaModuleTree(projectStore.getProjectId);
    moduleTreeData.value = res.data || [{id: 0, moduleName: t('qa.testCase.allCases'), children: []}];
  } catch (e) {
    console.error(e);
  }
};


const resetForm = () => {
  formData.value = {
    projectId: projectStore.getProjectId,
    caseType: 'FUNCTION',
    priority: 'P1',
    status: 'DRAFT',
    moduleId: 0, // 默认归属根节点「全部用例」，提交时归一化为 NULL
    requirementIds: [],
    setIds: [], // 测试集字段保留（后端仍支持），UI 已隐藏
    expectDuration: undefined, // UI 已隐藏
    tags: []
  };
  stepList.value = [createEmptyStep()];
  requirementOptions.value = [];
  initialFileIds.value = new Set();
  uploadedFileIds.value = new Set();
  updateSnapshot();
};

const createEmptyStep = () => ({
  step: '',
  expected: '',
  id: `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
});

const normalizeSteps = (steps: any[]) => {
  if (!steps || steps.length === 0) return [createEmptyStep()];
  return steps.map((s: any) => ({
    ...s,
    id: s.id || `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
  }));
};

const handleDragStart = (index: number) => {
  dragIndex.value = index;
};

const handleDragOver = (index: number) => {
  if (dragIndex.value === null || dragIndex.value === index) return;
  const temp = stepList.value[dragIndex.value];
  stepList.value.splice(dragIndex.value, 1);
  stepList.value.splice(index, 0, temp);

  dragIndex.value = index;
};

const handleDrop = () => {
  dragIndex.value = null;
};

const handleDragEnd = () => {
  dragIndex.value = null;
  dragFromHandle.value = false;
};

const parseRequirementIds = (data: any): number[] => {
  if (data?.requirementIds) {
    return String(data.requirementIds)
      .split(',')
      .map((s: string) => Number(s.trim()))
      .filter((n: number) => n > 0);
  }
  return data?.requirementId ? [data.requirementId] : [];
};

// 把已关联但不在下拉选项里的需求逐个补详情，避免多选框显示空白
const ensureRequirementsInOptions = async (ids: number[]) => {
  for (const id of ids) {
    await ensureRequirementInOptions(id);
  }
};

const loadDetail = async (id: number | string) => {
  try {
    const res: any = await getTestCaseDetail(Number(id));
    if (res.data) {
      formData.value = {...res.data};
      // 根节点归属（NULL/0）统一显示为选中「全部用例」
      formData.value.moduleId = res.data.moduleId ?? 0;
      formData.value.requirementIds = parseRequirementIds(res.data);
      stepList.value = normalizeSteps(res.data.testSteps);
      if (formData.value.tags && typeof formData.value.tags === 'string') {
        formData.value.tags = formData.value.tags.split(',').filter((t: string) => t.trim());
      } else if (!formData.value.tags) {
        formData.value.tags = [];
      }
      collectInitialFileIds();
      updateSnapshot();
      await loadRequirementOptions();
      await ensureRequirementsInOptions(formData.value.requirementIds);
    }
  } catch (e) {
    console.error(e);
  }
};

const ensureRequirementInOptions = async (id: number) => {
  const exists = requirementOptions.value.some((r: any) => r.id === id);
  if (exists) return;
  try {
    const res: any = await getRequirementDetail(id);
    if (res.data) {
      requirementOptions.value.unshift(res.data);
    }
  } catch (e) {
    console.error(e);
  }
};

const loadRequirementOptions = async (keyword?: string, isLoadMore = false) => {
  if (!projectStore.getProjectId) return;
  if (!isLoadMore) {
    requirementPage.value.current = 1;
    requirementPage.value.keyword = keyword || '';
    requirementOptions.value = [];
  }
  try {
    const res: any = await getRequirementList(
      projectStore.getProjectId,
      requirementPage.value.keyword || undefined,
      undefined,
      undefined,
      undefined,
      undefined,
      requirementPage.value.current,
      requirementPage.value.pageSize
    );
    const records = res.data?.records || [];
    if (isLoadMore) {
      requirementOptions.value.push(...records);
    } else {
      requirementOptions.value = records;
    }
    requirementPage.value.hasMore = records.length >= requirementPage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};

const loadMoreRequirement = async () => {
  if (!requirementPage.value.hasMore || requirementPage.value.loading) return;
  requirementPage.value.loading = true;
  try {
    requirementPage.value.current++;
    await loadRequirementOptions(undefined, true);
  } finally {
    requirementPage.value.loading = false;
  }
};

const handleSearchRequirement = (keyword: string) => {
  loadRequirementOptions(keyword);
};

const handleRequirementDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    loadRequirementOptions('');
  }
};

const addStep = () => {
  stepList.value.push(createEmptyStep());
};

// 复制步骤：深拷贝内容并插入到当前行之后，生成新 id 避免 key 冲突
const copyStep = (index: number) => {
  const source = stepList.value[index];
  stepList.value.splice(index + 1, 0, {
    step: source.step,
    expected: source.expected,
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`
  });
};

const removeStep = (index: number) => {
  if (stepList.value.length <= 1) return;
  stepList.value.splice(index, 1);
};

const save = async () => {
  if (!formData.value.caseName) {
    Message.warning(t('qa.testCase.namePlaceholder'));
    return false;
  }
  if (formData.value.moduleId === undefined || formData.value.moduleId === null || formData.value.moduleId === '') {
    Message.warning(t('qa.testCase.selectModule'));
    return false;
  }
  if (!formData.value.priority) {
    Message.warning(t('qa.testCase.priorityRequired'));
    return false;
  }
  const payload = {...formData.value};
  // 多需求关联：数组转逗号分隔字符串；requirementId 旧字段由后端按列表首个自动同步
  payload.requirementIds = Array.isArray(formData.value.requirementIds)
    ? formData.value.requirementIds.join(',')
    : (formData.value.requirementIds || '');
  payload.testSteps = stepList.value
    .filter((s: any) => s.step || s.expected)
    .map((s: any) => ({ step: s.step, expected: s.expected }));
  if (payload.tags && Array.isArray(payload.tags)) {
    payload.tags = payload.tags.join(',');
  }
  try {
    const api = isEdit.value ? updateTestCase : saveTestCase;
    const res: any = await api(payload);
    if (res.code === 200) {
      Message.success(t('qa.testCase.saveSuccess'));
      // 计算并删除被移除的富文本图片
      const currentFileIds = new Set<string>();
      extractFileIds(formData.value.preCondition).forEach(id => currentFileIds.add(id));
      stepList.value.forEach((s: any) => {
        extractFileIds(s.step).forEach(id => currentFileIds.add(id));
        extractFileIds(s.expected).forEach(id => currentFileIds.add(id));
      });
      const allSessionFileIds = new Set<string>([...initialFileIds.value, ...uploadedFileIds.value]);
      const deletedFileIds = Array.from(allSessionFileIds).filter(id => !currentFileIds.has(id));
      uploadedFileIds.value.clear();
      updateSnapshot();
      if (deletedFileIds.length > 0) {
        deleteRichTextImages(deletedFileIds).catch(() => {
          // 图片删除失败不影响业务保存
        });
      }
      emit('saved', res.data);
      return res.data;
    }
  } catch (e) {
    console.error(e);
  }
  return false;
};

const initForm = async () => {
  if (props.initialData) {
    formData.value = {...props.initialData};
    // 根节点归属（NULL/undefined）统一显示为选中「全部用例」
    formData.value.moduleId = props.initialData.moduleId ?? 0;
    formData.value.requirementIds = parseRequirementIds(props.initialData);
    stepList.value = normalizeSteps(props.initialData.testSteps);
    if (formData.value.tags && typeof formData.value.tags === 'string') {
      formData.value.tags = formData.value.tags.split(',').filter((t: string) => t.trim());
    } else if (!formData.value.tags) {
      formData.value.tags = [];
    }
    isEdit.value = !!props.initialData.id;
    collectInitialFileIds();
    updateSnapshot();
    await loadRequirementOptions();
    await ensureRequirementsInOptions(formData.value.requirementIds);
  } else if (props.id) {
    isEdit.value = true;
    await loadDetail(props.id);
  } else {
    isEdit.value = false;
    resetForm();
    await loadRequirementOptions();
  }
};

defineExpose({
  save,
  isDirty: () => isDirty.value,
  initForm,
});

watch(
  () => [props.id, props.initialData],
  () => {
    initForm();
  },
  {deep: true}
);

watch(
  () => projectStore.getProjectId,
  async (newId) => {
    if (newId) {
      loadModuleTree();
      await loadRequirementOptions();
      if (Array.isArray(formData.value.requirementIds) && formData.value.requirementIds.length) {
        await ensureRequirementsInOptions(formData.value.requirementIds);
      }
    }
  },
  {immediate: true}
);

onMounted(() => {
  initForm();
});
</script>

<style scoped lang="less">
.test-case-form {
  height: 100%;

  > :deep(.arco-form) {
    height: 100%;
  }

  :deep(.arco-form-item-label) {
    font-weight: 500;
    color: var(--color-text-2);
    line-height: 24px;
    margin-bottom: 2px;
  }

  :deep(.arco-form-item) {
    margin-bottom: 12px;
  }
}

.form-row {
  height: 100%;
}

.main-col,
.side-col {
  height: 100%;
}

.main-card {
  height: 100%;
  display: flex;
  flex-direction: column;

  :deep(.arco-card-body) {
    flex: 1 1 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
    padding: 14px 16px;
  }

  // 左侧主栏 label 前置竖向蓝条
  :deep(.arco-form-item-label),
  .steps-area-label {
    position: relative;
    padding-left: 10px;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 50%;
      transform: translateY(-50%);
      width: 3px;
      height: 14px;
      border-radius: 2px;
      background: rgb(var(--primary-6));
    }
  }
}

.title-form-item {
  flex-shrink: 0;
  margin-bottom: 12px;
}

.title-input {
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.title-input:focus-within {
  box-shadow: 0 0 0 3px rgba(0, 180, 42, 0.1);
  border-radius: 4px;
}

.precondition-item {
  flex-shrink: 0;
  margin-bottom: 12px;
}

// 测试步骤区：占满左侧剩余空间，步骤多时在区域内滚动
.steps-area {
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.steps-area-label {
  flex-shrink: 0;
  margin-bottom: 8px;
  font-weight: 500;
  color: var(--color-text-2);
}

.steps-scroll {
  flex: 1 1 0;
  min-height: 0;
  overflow-y: auto;
  padding-right: 4px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.card-title-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.side-col {
  margin-top: 20px;

  // a-scrollbar 开启 inheritAttrs:false，外部 class/style 会落在内部滚动容器上，
  // 这里统一用 :deep 直接控制组件根节点，保证宽度稳定
  :deep(.arco-scrollbar) {
    width: 100%;
  }
}

.side-card + .side-card {
  margin-top: 16px;
}

.priority-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
}

.steps-table {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  /* 不能用 overflow:hidden 裁圆角，否则表头 sticky 会失效；改为首尾行各自圆角 */
}

.steps-table-header {
  border-radius: 6px 6px 0 0;
}

.steps-table-row:last-child {
  border-radius: 0 0 6px 6px;
}

.steps-table-header,
.steps-table-row {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr) minmax(0, 1fr) 80px;
  column-gap: 8px;
  align-items: start;
  padding: 6px 10px;
}

.steps-table-header {
  background: var(--color-fill-1);
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
  align-items: center;
  /* 步骤区域滚动时表头吸顶 */
  position: sticky;
  top: 0;
  z-index: 2;
}

/* 添加步骤按钮固定在滚动区外，不随步骤列表滚动 */
.add-step-btn {
  flex-shrink: 0;
  margin-top: 8px;
}

.steps-table-row {
  border-top: 1px solid var(--color-border-2);
  background: var(--color-bg-2);
  transition: box-shadow 0.2s ease, opacity 0.2s ease;

  &.dragging {
    opacity: 0.6;
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  }
}

.col-index {
  display: flex;
  align-items: center;
  gap: 4px;
  padding-top: 4px;
  cursor: grab;

  &:active {
    cursor: grabbing;
  }
}

.col-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
}

.steps-table-header .col-actions {
  justify-content: flex-end;
}

.drag-handle {
  color: var(--color-text-3);
  font-size: 16px;
  flex-shrink: 0;
}

.step-number {
  color: rgb(var(--primary-6));
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

// 宽屏双栏（与 a-col 的 lg 断点 1200px 对齐）：
// a-scrollbar 根节点撑满右侧列高，内部滚动容器 height:100% + overflow，
// 卡片顶对齐，内容超出栏高时在栏内滚动
@media (min-width: 1200px) {
  .side-col {
    margin-top: 0;

    :deep(.arco-scrollbar) {
      height: 100%;
    }

    :deep(.arco-scrollbar-container) {
      height: 100%;
      overflow-y: auto;
    }
  }
}

// slim 模式（详情抽屉内嵌编辑）：抽屉没有给表单有界高度，
// 100% 高度接力会塌缩成 0，flex:1 + min-height:0 会把内容压没；
// 改为高度由内容自然撑开，步骤区给个上限在区域内滚动
.slim-mode {
  &,
  > :deep(.arco-form),
  .form-row,
  .main-col,
  .main-card {
    height: auto;
  }

  .main-card :deep(.arco-card-body) {
    flex: none;
  }

  /* 抽屉内嵌编辑：去掉最外层卡片边框/阴影/内边距，内容直接平铺 */
  .main-card {
    border: none;
    box-shadow: none;

    :deep(.arco-card-body) {
      padding: 0;
    }
  }

  .steps-area {
    flex: none;
  }

  .steps-scroll {
    flex: none;
    max-height: 420px;
    overflow-y: auto;
    padding-right: 4px;
  }

  .section-header {
    margin-bottom: 16px;
  }
}

// 窄屏单栏堆叠（与 a-col 的 lg 断点 1200px 对齐）：
// 取消 100% 高度接力，改由内容自然撑开，弹窗内容区整体滚动
@media (max-width: 1199px) {
  .test-case-form,
  .test-case-form > :deep(.arco-form),
  .form-row,
  .main-col,
  .side-col,
  .main-card {
    height: auto;
  }

  .steps-scroll {
    flex: none;
    overflow-y: visible;
    padding-right: 0;
  }
}
</style>
