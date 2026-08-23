<template>
  <div class="bug-form">
    <a-form ref="formRef" :model="formData" layout="vertical">
      <a-row :gutter="20" class="form-row">
        <!-- 左侧主栏：核心文本输入区 -->
        <a-col :xs="24" :lg="16" class="main-col">
          <a-card class="main-card">
            <a-form-item
              field="title"
              label="BUG名称"
              :rules="[{ required: true, message: '请输入BUG名称' }]"
              :validate-trigger="['change', 'blur']"
              class="title-form-item"
            >
              <a-input
                v-model="formData.title"
                placeholder="请输入BUG名称"
                size="large"
                class="title-input"
                allow-clear
              />
            </a-form-item>

            <a-row :gutter="16" class="editors-row">
              <a-col :xs="24" :md="12" class="editor-col">
                <a-form-item field="description" label="BUG描述" class="description-item">
                  <RichEditor
                    v-model="formData.description"
                    placeholder="请输入BUG描述"
                    :height="editorHeight"
                    @uploaded="handleUploadedFile"
                  />
                </a-form-item>
              </a-col>
              <a-col :xs="24" :md="12" class="editor-col">
                <a-form-item field="reproduceSteps" label="复现步骤" class="description-item">
                  <RichEditor
                    v-model="formData.reproduceSteps"
                    placeholder="请输入复现步骤"
                    :height="editorHeight"
                    @uploaded="handleUploadedFile"
                  />
                </a-form-item>
              </a-col>
            </a-row>
          </a-card>
        </a-col>

        <!-- 右侧副栏：属性管理 -->
        <a-col :xs="24" :lg="8" class="side-col">
          <a-scrollbar class="side-scrollbar">
            <a-card class="side-card">
              <template #title>
                <div class="card-title">
                  <div class="card-title-dot" style="background: #f53f3f;" />
                  属性与状态
                </div>
              </template>

              <a-form-item field="severity" label="严重程度" :rules="[{ required: true, message: '请选择严重程度' }]" :validate-trigger="['change', 'blur']">
                <a-select v-model="formData.severity" placeholder="请选择严重程度">
                  <a-option value="FATAL" class="severity-option severity-fatal">致命</a-option>
                  <a-option value="SERIOUS" class="severity-option severity-serious">严重</a-option>
                  <a-option value="NORMAL">一般</a-option>
                  <a-option value="TIPS">提示</a-option>
                </a-select>
              </a-form-item>

              <a-form-item field="status" label="BUG状态" :rules="[{ required: true, message: '请选择BUG状态' }]" :validate-trigger="['change', 'blur']">
                <a-select
                  v-model="formData.status"
                  class="status-select"
                  :class="{ 'status-readonly': !isEdit }"
                  placeholder="新建"
                  :disabled="!isEdit"
                >
                  <a-option v-if="!isEdit" value="NEW">新建</a-option>
                  <template v-else>
                    <a-option v-for="s in allBugStatuses" :key="s.value" :value="s.value">{{ s.label }}</a-option>
                  </template>
                </a-select>
              </a-form-item>

              <a-form-item field="assigneeId" label="处理人" :rules="[{ required: true, message: '请选择处理人' }]" :validate-trigger="['change', 'blur']">
                <a-select
                  v-model="formData.assigneeId"
                  allow-clear
                  allow-search
                  :filter-option="false"
                  placeholder="请输入关键词搜索用户"
                  @search="handleSearchUser"
                  @dropdown-visible-change="handleUserDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreUser"
                >
                  <template #prefix>
                    <IconUser />
                  </template>
                  <a-option v-for="u in userOptions" :key="u.id" :value="u.id">{{ u.nickname || u.username }}</a-option>
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

              <a-form-item field="tags" label="标签">
                <a-input-tag v-model="formData.tags" placeholder="输入标签后按回车" allow-clear class="bug-tags-input" />
              </a-form-item>
            </a-card>
          </a-scrollbar>
        </a-col>
      </a-row>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import {ref, watch, onMounted, onBeforeUnmount, computed} from 'vue';
import {Message} from '@arco-design/web-vue';
import { IconBug, IconBulb, IconUser } from '@arco-design/web-vue/es/icon';
import RichEditor from '@/components/rich-editor/index.vue';
import {useProjectStore} from '@/store';
import {deleteRichTextImages} from '@/api/MyApi/fileUpload';
import {
  saveBug,
  updateBug,
  getBugDetail,
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

// 编辑器高度跟随浏览器自适应：
// - 宽屏（a-col lg ≥1200px，左右双栏）：100%，由 flex 布局填满弹窗剩余空间
// - 窄屏（单栏堆叠）：32vh，随窗口高度变化，避免 flex 链断裂后高度坍缩
const windowWidth = ref(window.innerWidth);
const handleWindowResize = () => {
  windowWidth.value = window.innerWidth;
};
const editorHeight = computed(() => (windowWidth.value >= 1200 ? '100%' : '40vh'));
onMounted(() => window.addEventListener('resize', handleWindowResize));
onBeforeUnmount(() => window.removeEventListener('resize', handleWindowResize));

const VNodeRenderer = {
  props: ['vnodes'],
  render(this: { vnodes: any }) {
    return this.vnodes;
  }
};

const formData = ref<any>({});
const formRef = ref();
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

const collectInitialFileIds = () => {
  const set = new Set<string>();
  extractFileIds(formData.value.description).forEach(id => set.add(id));
  extractFileIds(formData.value.reproduceSteps).forEach(id => set.add(id));
  initialFileIds.value = set;
  uploadedFileIds.value = new Set();
};

const handleUploadedFile = (fileId: string) => {
  uploadedFileIds.value.add(fileId);
};

const fillReproduceTemplate = () => {
  formData.value.reproduceSteps = '<ol><li>进入页面</li><li>点击按钮</li><li>出现报错</li></ol>';
};

const buildComparable = () => {
  return {
    ...formData.value,
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

const userOptions = ref<any[]>([]);
const userPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

const allBugStatuses = [
  { value: 'NEW', label: '新建' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'FIXING', label: '修复中' },
  { value: 'FIXED', label: '已修复' },
  { value: 'VERIFIED', label: '已验证' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'REJECTED', label: '已驳回' }
];

const resetForm = () => {
  formData.value = {
    projectId: projectStore.getProjectId,
    severity: 'NORMAL',
    status: 'NEW',
    tags: []
  };
  userOptions.value = [];
  initialFileIds.value = new Set();
  uploadedFileIds.value = new Set();
  updateSnapshot();
};

const loadDetail = async (id: number | string) => {
  try {
    const res: any = await getBugDetail(Number(id));
    if (res.data) {
      formData.value = {...res.data};
      if (formData.value.tags && typeof formData.value.tags === 'string') {
        formData.value.tags = formData.value.tags.split(',').filter((t: string) => t.trim());
      } else if (!formData.value.tags) {
        formData.value.tags = [];
      }
      collectInitialFileIds();
      updateSnapshot();
      await handleSearchUser('');
      if (formData.value.assigneeId) {
        ensureUserInOptions(formData.value.assigneeId, formData.value.assigneeName);
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
    Message.warning('请输入BUG名称');
    return false;
  }
  if (!formData.value.severity) {
    Message.warning('请选择严重程度');
    return false;
  }
  if (!formData.value.status) {
    Message.warning('请选择BUG状态');
    return false;
  }
  if (!formData.value.assigneeId) {
    Message.warning('请选择处理人');
    return false;
  }
  const payload = {...formData.value};
  if (payload.tags && Array.isArray(payload.tags)) {
    payload.tags = payload.tags.join(',');
  }
  try {
    const api = isEdit.value ? updateBug : saveBug;
    const res: any = await api(payload);
    if (res.code === 200) {
      Message.success('保存成功');
      // 存储后端返回的 BUG ID
      if (res.data) {
        formData.value.id = res.data;
      }
      // 计算并删除被移除的富文本图片
      const currentFileIds = new Set<string>();
      extractFileIds(formData.value.description).forEach(id => currentFileIds.add(id));
      extractFileIds(formData.value.reproduceSteps).forEach(id => currentFileIds.add(id));
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
    isEdit.value = !!props.initialData.id;
    collectInitialFileIds();
    updateSnapshot();
    await handleSearchUser('');
    if (formData.value.assigneeId) {
      ensureUserInOptions(formData.value.assigneeId, formData.value.assigneeName);
    }
  } else if (props.id) {
    isEdit.value = true;
    await loadDetail(props.id);
  } else {
    isEdit.value = false;
    resetForm();
    await handleSearchUser('');
  }
};

defineExpose({
  save,
  formData,
  isDirty: () => isDirty.value,
  resetForm,
});

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
.bug-form {
  height: 100%;

  > :deep(.arco-form) {
    height: 100%;
  }
}

.bug-form :deep(.arco-form-item-label) {
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
.side-col,
.editor-col {
  height: 100%;
}

.editors-row {
  flex: 1 1 0;
  min-height: 0;
}

.editor-col {
  display: flex;
  flex-direction: column;
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
  box-shadow: 0 0 0 3px rgba(225, 29, 72, 0.12);
  border-radius: 4px;
}

.title-input :deep(.arco-input-wrapper-focus),
.title-input :deep(.arco-input-focus) {
  border-color: #e11d48 !important;
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

.severity-option.severity-fatal,
.severity-option.severity-serious {
  color: #f53f3f;
  font-weight: 600;
}

.status-select.status-readonly :deep(.arco-select-view) {
  background: #fff1f3;
  color: #e11d48;
  font-weight: 600;
}

.bug-tags-input :deep(.arco-input-tag-tag) {
  background: #fff1f3;
  color: #e11d48;
  border-color: #ffd6dd;
}

.reproduce-hint {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  width: fit-content;
  margin-bottom: 8px;
  padding: 4px 10px;
  font-size: 12px;
  color: #86909c;
  background: #f7f8fa;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.reproduce-hint:hover {
  color: #e11d48;
  background: #fff1f3;
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
  .bug-form,
  .bug-form > :deep(.arco-form),
  .form-row,
  .main-col,
  .side-col,
  .editor-col,
  .main-card {
    height: auto;
  }

  .editors-row {
    flex: none;
  }
}
</style>
