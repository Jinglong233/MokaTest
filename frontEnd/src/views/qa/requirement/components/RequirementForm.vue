<template>
  <div class="requirement-form">
    <a-form :model="formData" layout="vertical">
      <a-row :gutter="20" class="form-row">
        <!-- 左侧主栏：基本信息 -->
        <a-col :xs="24" :lg="16" class="main-col">
          <a-card class="main-card">
            <div class="section-header">
              <div class="section-icon" style="background: #e8f3ff; color: #165dff;">
                <IconEdit />
              </div>
              <span class="section-title">基本信息</span>
            </div>

            <a-form-item
              field="title"
              label="需求标题"
              :rules="[{ required: true, message: '请输入需求标题' }]"
              :validate-trigger="['change', 'blur']"
              class="title-form-item"
            >
              <a-input
                v-model="formData.title"
                placeholder="请输入需求标题"
                size="large"
                class="title-input"
                allow-clear
              />
            </a-form-item>

            <a-form-item field="description" label="需求描述" class="description-item">
              <RichEditor
                v-model="formData.description"
                placeholder="请输入需求描述，支持图文混排"
                :height="editorHeight"
                @uploaded="handleUploadedFile"
              />
            </a-form-item>
          </a-card>
        </a-col>

        <!-- 右侧副栏：属性面板 -->
        <a-col :xs="24" :lg="8" class="side-col">
          <a-scrollbar class="side-scrollbar">
            <!-- 卡片 A：分类属性 -->
            <a-card class="side-card">
              <template #title>
                <div class="card-title">
                  <div class="card-title-dot" style="background: #165dff;" />
                  分类属性
                </div>
              </template>

              <a-form-item v-if="fieldVis('moduleId')" field="moduleId" label="所属模块">
                <a-select
                  v-model="formData.moduleId"
                  placeholder="请选择所属模块"
                  allow-clear
                >
                  <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('parentId')" field="parentId" label="父需求">
                <a-select
                  v-model="formData.parentId"
                  allow-clear
                  allow-search
                  :filter-option="false"
                  placeholder="请输入关键词搜索需求"
                  @search="handleSearchParent"
                  @dropdown-visible-change="handleParentDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreParent"
                >
                  <a-option v-for="req in parentOptions" :key="req.id" :value="req.id">{{ req.reqCode }} - {{ req.title }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="parentPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        滚动加载更多...
                      </div>
                      <div v-else-if="parentOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        没有更多了
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('reqType')" field="reqType" label="需求类型">
                    <a-select v-model="formData.reqType" placeholder="请选择需求类型">
                      <a-option value="FEATURE">功能需求</a-option>
                      <a-option value="BUGFIX">缺陷修复</a-option>
                      <a-option value="OPTIMIZE">优化</a-option>
                      <a-option value="TECH_DEBT">技术债务</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('source')" field="source" label="来源">
                    <a-select v-model="formData.source" placeholder="请选择来源">
                      <a-option value="CLIENT">客户反馈</a-option>
                      <a-option value="INTERNAL">内部规划</a-option>
                      <a-option value="COMPETITOR">竞品分析</a-option>
                      <a-option value="ONLINE">线上问题</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item field="priority" label="优先级">
                    <a-select v-model="formData.priority" placeholder="请选择优先级">
                      <a-option value="P0">
                        <span class="priority-dot" style="background: #f53f3f;" />P0
                      </a-option>
                      <a-option value="P1">
                        <span class="priority-dot" style="background: #ff7d00;" />P1
                      </a-option>
                      <a-option value="P2">
                        <span class="priority-dot" style="background: #f7ba1e;" />P2
                      </a-option>
                      <a-option value="P3">
                        <span class="priority-dot" style="background: #165dff;" />P3
                      </a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('expectReleaseTime')" field="expectReleaseTime" label="期望上线时间">
                    <a-date-picker
                      v-model="formData.expectReleaseTime"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择"
                      style="width: 100%;"
                    />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-card>

            <!-- 卡片 B：状态与协作 -->
            <a-card class="side-card">
              <template #title>
                <div class="card-title">
                  <div class="card-title-dot" style="background: #00b42a;" />
                  状态与协作
                </div>
              </template>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item field="status" label="当前状态">
                    <a-select
                      v-model="formData.status"
                      placeholder="草稿"
                      :disabled="!isEdit"
                      class="status-select"
                    >
                      <a-option v-for="s in allStatuses" :key="s.value" :value="s.value">{{ s.label }}</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('version')" field="version" label="版本">
                    <a-input v-model="formData.version" placeholder="如 v1.0.0" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item v-if="fieldVis('ownerId')" field="ownerId" label="负责人">
                <a-select
                  v-model="formData.ownerId"
                  allow-clear
                  allow-search
                  :filter-option="false"
                  placeholder="请输入关键词搜索用户"
                  @search="handleSearchUser"
                  @dropdown-visible-change="handleUserDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreUser"
                >
                  <a-option v-for="user in userOptions" :key="user.id" :value="user.id">{{ user.nickname || user.username }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="userPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        滚动加载更多...
                      </div>
                      <div v-else-if="userOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        没有更多了
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('participants')" field="participants" label="参与人">
                <a-select
                  v-model="participantIds"
                  multiple
                  allow-clear
                  allow-search
                  :filter-option="false"
                  placeholder="请输入关键词搜索用户"
                  @search="handleSearchUser"
                  @dropdown-visible-change="handleUserDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreUser"
                >
                  <a-option v-for="user in userOptions" :key="user.id" :value="user.id">{{ user.nickname || user.username }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="userPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        滚动加载更多...
                      </div>
                      <div v-else-if="userOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        没有更多了
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('tags')" field="tags" label="标签">
                <a-input-tag v-model="formData.tags" placeholder="输入标签后按回车" allow-clear />
              </a-form-item>
            </a-card>
          </a-scrollbar>
        </a-col>
      </a-row>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, computed } from 'vue';
import { Message } from '@arco-design/web-vue';
import { IconEdit } from '@arco-design/web-vue/es/icon';
import RichEditor from '@/components/rich-editor/index.vue';
import {useProjectStore} from '@/store';
import useProjectConfigStore from '@/store/modules/projectConfig';
import {deleteRichTextImages} from '@/api/MyApi/fileUpload';
import {
  getRequirementList,
  getRequirementDetail,
  saveRequirement,
  updateRequirement,
  getQaModuleList,
} from '@/api/MyApi/qa';
import {getUserListByPage} from '@/api/MyApi/user';

const props = defineProps<{
  id?: number | string;
  initialData?: any;
}>();

const emit = defineEmits<{
  (e: 'saved'): void;
}>();

const projectStore = useProjectStore();
const projectConfigStore = useProjectConfigStore();
const fieldVis = (key: string) => projectConfigStore.isFieldVisible('requirement', key);

// 编辑器高度跟随浏览器自适应：
// - 宽屏（a-col lg ≥1200px，左右双栏）：100%，由 flex 布局填满弹窗剩余空间
// - 窄屏（单栏堆叠）：42vh，随窗口高度变化，避免 flex 链断裂后高度坍缩
const windowWidth = ref(window.innerWidth);
const handleWindowResize = () => {
  windowWidth.value = window.innerWidth;
};
const editorHeight = computed(() => (windowWidth.value >= 1200 ? '100%' : '42vh'));
onMounted(() => window.addEventListener('resize', handleWindowResize));
onBeforeUnmount(() => window.removeEventListener('resize', handleWindowResize));

const VNodeRenderer = {
  props: ['vnodes'],
  render(this: { vnodes: any }) {
    return this.vnodes;
  }
};

const formData = ref<any>({});
const participantIds = ref<number[]>([]);
const isEdit = ref(false);
const initialFileIds = ref<Set<string>>(new Set());
const uploadedFileIds = ref<Set<string>>(new Set());
const initialSnapshot = ref('');

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

const handleUploadedFile = (fileId: string) => {
  uploadedFileIds.value.add(fileId);
};

const buildComparable = () => {
  return {
    ...formData.value,
    participants: participantIds.value,
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

const moduleOptions = ref<any[]>([]);

const parentOptions = ref<any[]>([]);
const parentPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

const userOptions = ref<any[]>([]);
const userPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

const allStatuses = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'REVIEWING', label: '评审中' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'DEVELOPING', label: '开发中' },
  { value: 'TESTING', label: '测试中' },
  { value: 'RELEASED', label: '已上线' },
  { value: 'CLOSED', label: '已关闭' }
];

const loadModuleOptions = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getQaModuleList(projectStore.getProjectId);
    moduleOptions.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const resetForm = () => {
  formData.value = {
    projectId: projectStore.getProjectId,
    priority: 'P2',
    status: 'DRAFT',
    tags: []
  };
  participantIds.value = [];
  parentOptions.value = [];
  userOptions.value = [];
  initialFileIds.value = new Set();
  uploadedFileIds.value = new Set();
  updateSnapshot();
};

const parseParticipants = (record: any) => {
  if (record.participants) {
    try {
      const arr = JSON.parse(record.participants);
      participantIds.value = Array.isArray(arr) ? arr.map(Number) : [];
    } catch {
      participantIds.value = [];
    }
  } else {
    participantIds.value = [];
  }
};

const loadDetail = async (id: number | string) => {
  try {
    const res: any = await getRequirementDetail(Number(id));
    if (res.data) {
      formData.value = {...res.data};
      if (formData.value.tags && typeof formData.value.tags === 'string') {
        formData.value.tags = formData.value.tags.split(',').filter((t: string) => t.trim());
      } else if (!formData.value.tags) {
        formData.value.tags = [];
      }
      parseParticipants(res.data);
      initialFileIds.value = extractFileIds(formData.value.description);
      uploadedFileIds.value = new Set();
      updateSnapshot();
      await handleSearchParent('');
      if (formData.value.parentId && !parentOptions.value.find((r: any) => r.id === formData.value.parentId)) {
        try {
          const parentRes: any = await getRequirementDetail(formData.value.parentId);
          if (parentRes.data) {
            parentOptions.value = [parentRes.data, ...parentOptions.value];
          }
        } catch (e) {
          console.error(e);
        }
      }
      await handleSearchUser('');
      if (formData.value.ownerId && !userOptions.value.find((u: any) => u.id === formData.value.ownerId)) {
        ensureUserInOptions(formData.value.ownerId, formData.value.ownerName);
      }
      if (participantIds.value.length > 0) {
        participantIds.value.forEach((pid: number) => {
          if (!userOptions.value.find((u: any) => u.id === pid)) {
            ensureUserInOptions(pid, '');
          }
        });
      }
    }
  } catch (e) {
    console.error(e);
  }
};

const ensureUserInOptions = (id: number, name?: string) => {
  const exists = userOptions.value.some((u: any) => u.id === id);
  if (exists) return;
  userOptions.value.unshift({id, username: name || '', nickname: name || ''});
};

const handleSearchParent = async (keyword?: string, isLoadMore = false) => {
  if (!projectStore.getProjectId) return;
  if (!isLoadMore) {
    parentPage.value.current = 1;
    parentPage.value.keyword = keyword || '';
    parentOptions.value = [];
  }
  try {
    const res: any = await getRequirementList(
        projectStore.getProjectId,
        parentPage.value.keyword || undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        parentPage.value.current,
        parentPage.value.pageSize
    );
    const records = res.data?.records || [];
    const currentId = formData.value.id;
    const filtered = records.filter((r: any) => r.id !== currentId);
    if (isLoadMore) {
      parentOptions.value.push(...filtered);
    } else {
      parentOptions.value = filtered;
    }
    parentPage.value.hasMore = records.length >= parentPage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};

const loadMoreParent = async () => {
  if (!parentPage.value.hasMore || parentPage.value.loading) return;
  parentPage.value.loading = true;
  try {
    parentPage.value.current++;
    await handleSearchParent(undefined, true);
  } finally {
    parentPage.value.loading = false;
  }
};

const handleParentDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    handleSearchParent('');
  }
};

const handleSearchUser = async (keyword?: string, isLoadMore = false) => {
  if (!isLoadMore) {
    userPage.value.current = 1;
    userPage.value.keyword = keyword || '';
    userOptions.value = [];
  }
  try {
    const res: any = await getUserListByPage({
      projectId: projectStore.getProjectId ?? undefined,
      username: userPage.value.keyword || undefined,
      pageNum: userPage.value.current,
      pageSize: userPage.value.pageSize
    });
    const records = res.data?.records || res.data || [];
    if (isLoadMore) {
      userOptions.value.push(...records);
    } else {
      userOptions.value = records;
    }
    userPage.value.hasMore = records.length >= userPage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};

const loadMoreUser = async () => {
  if (!userPage.value.hasMore || userPage.value.loading) return;
  userPage.value.loading = true;
  try {
    userPage.value.current++;
    await handleSearchUser(undefined, true);
  } finally {
    userPage.value.loading = false;
  }
};

const handleUserDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    handleSearchUser('');
  }
};

const save = async () => {
  if (!formData.value.title) {
    Message.warning('请输入需求标题');
    return false;
  }
  const payload = {...formData.value};
  if (payload.tags && Array.isArray(payload.tags)) {
    payload.tags = payload.tags.join(',');
  }
  payload.participants = participantIds.value.length > 0 ? JSON.stringify(participantIds.value) : null;
  try {
    const api = isEdit.value ? updateRequirement : saveRequirement;
    const res: any = await api(payload);
    if (res.code === 200) {
      Message.success('保存成功');
      // 计算并删除被移除的富文本图片
      const currentFileIds = extractFileIds(formData.value.description);
      const allSessionFileIds = new Set<string>([...initialFileIds.value, ...uploadedFileIds.value]);
      const deletedFileIds = Array.from(allSessionFileIds).filter(id => !currentFileIds.has(id));
      uploadedFileIds.value.clear();
      updateSnapshot();
      if (deletedFileIds.length > 0) {
        deleteRichTextImages(deletedFileIds).catch(() => {
          // 图片删除失败不影响业务保存
        });
      }
      emit('saved');
      return true;
    }
  } catch (e) {
    console.error(e);
  }
  return false;
};

const initForm = async () => {
  if (props.initialData) {
    formData.value = {...props.initialData};
    if (formData.value.tags && typeof formData.value.tags === 'string') {
      formData.value.tags = formData.value.tags.split(',').filter((t: string) => t.trim());
    } else if (!formData.value.tags) {
      formData.value.tags = [];
    }
    parseParticipants(props.initialData);
    isEdit.value = !!props.initialData.id;
    initialFileIds.value = extractFileIds(formData.value.description);
    uploadedFileIds.value = new Set();
    updateSnapshot();
    await handleSearchParent('');
    await handleSearchUser('');
    if (formData.value.parentId && !parentOptions.value.find((r: any) => r.id === formData.value.parentId)) {
      try {
        const parentRes: any = await getRequirementDetail(formData.value.parentId);
        if (parentRes.data) {
          parentOptions.value = [parentRes.data, ...parentOptions.value];
        }
      } catch (e) {
        console.error(e);
      }
    }
    if (formData.value.ownerId && !userOptions.value.find((u: any) => u.id === formData.value.ownerId)) {
      ensureUserInOptions(formData.value.ownerId, formData.value.ownerName);
    }
    if (participantIds.value.length > 0) {
      participantIds.value.forEach((pid: number) => {
        if (!userOptions.value.find((u: any) => u.id === pid)) {
          ensureUserInOptions(pid, '');
        }
      });
    }
  } else if (props.id) {
    isEdit.value = true;
    await loadDetail(props.id);
  } else {
    isEdit.value = false;
    resetForm();
    await handleSearchParent('');
    await handleSearchUser('');
  }
};

defineExpose({
  save,
  isDirty: () => isDirty.value,
});

watch(
    () => projectStore.getProjectId,
    (newId) => {
      if (newId) {
        loadModuleOptions();
      }
    },
    {immediate: true}
);

watch(
    () => [props.id, props.initialData],
    () => {
      initForm();
    },
    {deep: true}
);

onMounted(() => {
  initForm();
});
</script>

<style scoped lang="less">
.requirement-form {
  height: 100%;

  > :deep(.arco-form) {
    height: 100%;
  }
}

.requirement-form :deep(.arco-form-item-label) {
  font-weight: 500;
  color: var(--color-text-2);
}

.main-card,
.side-card {
  height: auto;
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
  }
}

.form-row {
  height: 100%;
}

.main-col,
.side-col {
  height: 100%;
}

.description-item {
  margin-bottom: 0;
  flex: 1 1 0;
  min-height: 0;
  display: flex;
  flex-direction: column;

  // 让 form-item 内部各层包装节点全部参与 flex 布局，
  // 使富文本编辑器始终填满剩余空间而不是按视口高度硬算
  :deep(.arco-form-item-label-col) {
    flex-shrink: 0;
  }

  :deep(.arco-form-item-wrapper-col),
  :deep(.arco-form-item-content-wrapper),
  :deep(.arco-form-item-content),
  :deep(.arco-form-item-control),
  :deep(.arco-form-item-control-children) {
    flex: 1 1 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
}

.section-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}

.section-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
}

.title-form-item {
  flex-shrink: 0;
  margin-bottom: 16px;
}

.title-input {
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.title-input :deep(.arco-input) {
  font-size: 18px;
  font-weight: 600;
}

.title-input:focus-within {
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.1);
  border-radius: 4px;
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

.priority-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 8px;
}

.status-select :deep(.arco-select-view) {
  background: var(--color-fill-2);
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

// 窄屏单栏堆叠（与 a-col 的 lg 断点 1200px 对齐）：
// 取消 100% 高度接力，改由内容自然撑开，弹窗内容区整体滚动
@media (max-width: 1199px) {
  .requirement-form,
  .requirement-form > :deep(.arco-form),
  .form-row,
  .main-col,
  .side-col,
  .main-card {
    height: auto;
  }
}
</style>
