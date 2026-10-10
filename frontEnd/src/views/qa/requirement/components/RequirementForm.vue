<template>
  <div class="requirement-form">
    <a-form :model="formData" layout="vertical">
      <a-row :gutter="20" class="form-row">
        <!-- 左侧主栏：基本信息 -->
        <a-col :xs="24" :lg="16" class="main-col">
          <a-card class="main-card">
            <a-form-item
              field="title"
              :label="$t('qa.requirement.name')"
              :rules="[{ required: true, message: $t('qa.requirement.nameRequired') }]"
              :validate-trigger="['change', 'blur']"
              class="title-form-item"
            >
              <a-input
                v-model="formData.title"
                :placeholder="$t('qa.requirement.nameRequired')"
                size="large"
                class="title-input"
                allow-clear
              />
            </a-form-item>

            <a-form-item field="description" :label="$t('qa.requirement.description')" class="description-item">
              <RichEditor
                v-model="formData.description"
                :placeholder="$t('qa.requirement.descriptionPlaceholder')"
                height="100%"
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
                  {{ $t('qa.requirement.section.category') }}
                </div>
              </template>

              <a-form-item v-if="fieldVis('moduleId')" field="moduleId" :label="$t('qa.requirement.module')">
                <a-select
                  v-model="formData.moduleId"
                  :placeholder="$t('qa.requirement.modulePlaceholder')"
                  allow-clear
                >
                  <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('parentId')" field="parentId" :label="$t('qa.requirement.parent')">
                <a-select
                  v-model="formData.parentId"
                  allow-clear
                  allow-search
                  :filter-option="false"
                  :placeholder="$t('qa.requirement.parentPlaceholder')"
                  @search="handleSearchParent"
                  @dropdown-visible-change="handleParentDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreParent"
                >
                  <a-option v-for="req in parentOptions" :key="req.id" :value="req.id">{{ req.reqCode }} - {{ req.title }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="parentPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        {{ $t('qa.requirement.scrollMore') }}
                      </div>
                      <div v-else-if="parentOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        {{ $t('qa.requirement.noMore') }}
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('reqType')" field="reqType" :label="$t('qa.requirement.reqType')">
                    <a-select v-model="formData.reqType" :placeholder="$t('qa.requirement.reqTypePlaceholder')">
                      <a-option value="FEATURE">{{ $t('qa.requirement.type.feature') }}</a-option>
                      <a-option value="BUGFIX">{{ $t('qa.requirement.type.bugfix') }}</a-option>
                      <a-option value="OPTIMIZE">{{ $t('qa.requirement.type.optimize') }}</a-option>
                      <a-option value="TECH_DEBT">{{ $t('qa.requirement.type.techDebt') }}</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('source')" field="source" :label="$t('qa.requirement.source')">
                    <a-select v-model="formData.source" :placeholder="$t('qa.requirement.sourcePlaceholder')">
                      <a-option value="CLIENT">{{ $t('qa.requirement.source.client') }}</a-option>
                      <a-option value="INTERNAL">{{ $t('qa.requirement.source.internal') }}</a-option>
                      <a-option value="COMPETITOR">{{ $t('qa.requirement.source.competitor') }}</a-option>
                      <a-option value="ONLINE">{{ $t('qa.requirement.source.online') }}</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item field="priority" :label="$t('qa.requirement.priority')">
                    <a-select v-model="formData.priority" :placeholder="$t('qa.requirement.priorityPlaceholder')">
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
                  <a-form-item v-if="fieldVis('expectReleaseTime')" field="expectReleaseTime" :label="$t('qa.requirement.expectReleaseTime')">
                    <a-date-picker
                      v-model="formData.expectReleaseTime"
                      value-format="YYYY-MM-DD"
                      :placeholder="$t('qa.requirement.selectPlaceholder')"
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
                  {{ $t('qa.requirement.section.collab') }}
                </div>
              </template>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item field="status" :label="$t('qa.requirement.currentStatus')">
                    <a-select
                      v-model="formData.status"
                      :placeholder="$t('qa.requirement.status.draft')"
                      :disabled="!isEdit"
                      class="status-select"
                    >
                      <a-option v-for="s in allStatuses" :key="s.value" :value="s.value">{{ s.label }}</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('version')" field="version" :label="$t('qa.requirement.version')">
                    <a-input v-model="formData.version" :placeholder="$t('qa.requirement.versionPlaceholder')" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item v-if="fieldVis('ownerId')" field="ownerId" :label="$t('qa.requirement.owner')">
                <a-select
                  v-model="formData.ownerId"
                  allow-clear
                  allow-search
                  :filter-option="false"
                  :placeholder="$t('qa.requirement.userSearchPlaceholder')"
                  @search="handleSearchUser"
                  @dropdown-visible-change="handleUserDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreUser"
                >
                  <a-option v-for="user in userOptions" :key="user.id" :value="user.id">{{ user.nickname || user.username }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="userPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        {{ $t('qa.requirement.scrollMore') }}
                      </div>
                      <div v-else-if="userOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        {{ $t('qa.requirement.noMore') }}
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('participants')" field="participants" :label="$t('qa.requirement.participants')">
                <a-select
                  v-model="participantIds"
                  multiple
                  allow-clear
                  allow-search
                  :filter-option="false"
                  :placeholder="$t('qa.requirement.userSearchPlaceholder')"
                  @search="handleSearchUser"
                  @dropdown-visible-change="handleUserDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreUser"
                >
                  <a-option v-for="user in userOptions" :key="user.id" :value="user.id">{{ user.nickname || user.username }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="userPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        {{ $t('qa.requirement.scrollMore') }}
                      </div>
                      <div v-else-if="userOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        {{ $t('qa.requirement.noMore') }}
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('tags')" field="tags" :label="$t('qa.requirement.tags')">
                <a-input-tag v-model="formData.tags" :placeholder="$t('qa.requirement.tagsPlaceholder')" allow-clear />
              </a-form-item>
            </a-card>
          </a-scrollbar>
        </a-col>
      </a-row>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
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
const { t } = useI18n();
const fieldVis = (key: string) => projectConfigStore.isFieldVisible('requirement', key);



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

const allStatuses = computed(() => [
  { value: 'DRAFT', label: t('qa.requirement.status.draft') },
  { value: 'REVIEWING', label: t('qa.requirement.status.reviewing') },
  { value: 'CONFIRMED', label: t('qa.requirement.status.confirmed') },
  { value: 'DEVELOPING', label: t('qa.requirement.status.developing') },
  { value: 'TESTING', label: t('qa.requirement.status.testing') },
  { value: 'RELEASED', label: t('qa.requirement.status.released') },
  { value: 'CLOSED', label: t('qa.requirement.status.closed') }
]);

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
    Message.warning(t('qa.requirement.nameRequired'));
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
      Message.success(t('qa.requirement.saveSuccess'));
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
  align-items: stretch;

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
    align-items: stretch;
  }
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

// 窄屏单栏堆叠：主栏保持 100% 高度接力（编辑器充满），侧栏自然高度
@media (max-width: 1199px) {
  .side-col {
    height: auto;
  }
}
</style>
