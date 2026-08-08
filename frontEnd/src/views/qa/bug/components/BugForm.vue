<template>
  <div class="bug-form">
    <a-form :model="formData" layout="vertical">
      <a-row :gutter="20" class="form-row">
        <!-- 左侧主栏：核心文本输入区 -->
        <a-col :xs="24" :lg="16" class="main-col">
          <a-card class="main-card">
            <div class="section-header">
              <div class="section-icon" style="background: #fff1f3; color: #e11d48;">
                <IconBug />
              </div>
              <span class="section-title">BUG 信息</span>
            </div>

            <a-form-item
              field="title"
              label="BUG标题"
              :rules="[{ required: true, message: '请输入BUG标题' }]"
              :validate-trigger="['change', 'blur']"
              class="title-form-item"
            >
              <a-input
                v-model="formData.title"
                placeholder="请输入BUG标题"
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
                <a-form-item v-if="fieldVis('reproduceSteps')" field="reproduceSteps" label="复现步骤" class="description-item">
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

        <!-- 右侧副栏：属性与关联管理 -->
        <a-col :xs="24" :lg="8" class="side-col">
          <a-scrollbar class="side-scrollbar">
            <!-- 卡片一：属性与状态 -->
            <a-card class="side-card">
              <template #title>
                <div class="card-title">
                  <div class="card-title-dot" style="background: #f53f3f;" />
                  属性与状态
                </div>
              </template>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item field="severity" label="严重程度">
                    <a-select v-model="formData.severity" placeholder="请选择严重程度">
                      <a-option value="FATAL" class="severity-option severity-fatal">致命</a-option>
                      <a-option value="SERIOUS" class="severity-option severity-serious">严重</a-option>
                      <a-option value="NORMAL">一般</a-option>
                      <a-option value="TIPS">提示</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('priority')" field="priority" label="优先级">
                    <a-select v-model="formData.priority" placeholder="请选择优先级">
                      <a-option value="URGENT">紧急</a-option>
                      <a-option value="HIGH">高</a-option>
                      <a-option value="MEDIUM">中</a-option>
                      <a-option value="LOW">低</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item field="status" label="BUG状态">
                <a-select
                  v-model="formData.status"
                  class="status-select"
                  :class="{ 'status-readonly': !isEdit }"
                  placeholder="新建"
                  :disabled="!isEdit"
                >
                  <a-option v-for="s in allBugStatuses" :key="s.value" :value="s.value">{{ s.label }}</a-option>
                </a-select>
              </a-form-item>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('environment')" field="environment" label="影响环境">
                    <a-select v-model="formData.environment" placeholder="请选择环境" allow-clear>
                      <a-option value="TEST">测试环境</a-option>
                      <a-option value="STAGING">预发环境</a-option>
                      <a-option value="PROD">生产环境</a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('deadline')" field="deadline" label="截止日期">
                    <a-date-picker
                      v-model="formData.deadline"
                      value-format="YYYY-MM-DD"
                      placeholder="请选择截止日期"
                      style="width: 100%;"
                    />
                  </a-form-item>
                </a-col>
              </a-row>
            </a-card>

            <!-- 卡片二：版本与关联 -->
            <a-card class="side-card">
              <template #title>
                <div class="card-title">
                  <div class="card-title-dot" style="background: #165dff;" />
                  版本与关联
                </div>
              </template>

              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('foundVersion')" field="foundVersion" label="发现版本">
                    <a-input v-model="formData.foundVersion" placeholder="如 v1.0.0" />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item v-if="fieldVis('fixedVersion')" field="fixedVersion" label="修复版本">
                    <a-input v-model="formData.fixedVersion" placeholder="如 v1.0.1" />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item v-if="fieldVis('requirementId')" field="requirementId" label="关联需求">
                <a-select
                  v-model="requirementIds"
                  multiple
                  allow-clear
                  allow-search
                  :max-tag-count="1"
                  :filter-option="false"
                  placeholder="请输入关键词搜索需求"
                  @search="handleSearchRequirement"
                  @dropdown-visible-change="handleRequirementDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreRequirement"
                >
                  <a-option v-for="req in requirementOptions" :key="req.id" :value="Number(req.id)">{{ req.reqCode }} - {{ req.title }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="requirementPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        滚动加载更多...
                      </div>
                      <div v-else-if="requirementOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        没有更多了
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('testCaseId')" field="testCaseId" label="关联用例">
                <a-select
                  v-model="testCaseIds"
                  multiple
                  allow-clear
                  allow-search
                  :max-tag-count="1"
                  :filter-option="false"
                  placeholder="请输入关键词搜索用例"
                  @search="handleSearchTestCase"
                  @dropdown-visible-change="handleTestCaseDropdownVisibleChange"
                  @dropdown-reach-bottom="loadMoreTestCase"
                >
                  <a-option v-for="tc in testCaseOptions" :key="tc.id" :value="Number(tc.id)">{{ tc.caseCode }} - {{ tc.caseName }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="testCasePage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        滚动加载更多...
                      </div>
                      <div v-else-if="testCaseOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        没有更多了
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>

              <a-form-item v-if="formData.status === 'CLOSED' && fieldVis('closeReason')" field="closeReason" label="关闭原因">
                <a-select v-model="formData.closeReason" placeholder="请选择关闭原因" allow-clear>
                  <a-option value="FIXED">已修复</a-option>
                  <a-option value="DUPLICATE">重复</a-option>
                  <a-option value="NOT_BUG">不是BUG</a-option>
                  <a-option value="CANNOT_REPRODUCE">无法复现</a-option>
                  <a-option value="WONT_FIX">暂不处理</a-option>
                </a-select>
              </a-form-item>
            </a-card>

            <!-- 卡片三：协作与其它 -->
            <a-card class="side-card">
              <template #title>
                <div class="card-title">
                  <div class="card-title-dot" style="background: #00b42a;" />
                  协作与其它
                </div>
              </template>

              <a-form-item v-if="fieldVis('assigneeId')" field="assigneeId" label="指派给">
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

              <a-form-item v-if="fieldVis('moduleId')" field="moduleId" label="所属模块">
                <a-select v-model="formData.moduleId" allow-clear placeholder="请选择所属模块">
                  <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('reproduceRate')" field="reproduceRate" label="重现概率">
                <a-select v-model="formData.reproduceRate" placeholder="请选择重现概率" allow-clear>
                  <a-option value="ALWAYS">必现</a-option>
                  <a-option value="OFTEN">高概率</a-option>
                  <a-option value="SOMETIMES">偶现</a-option>
                  <a-option value="RARE">难现</a-option>
                </a-select>
              </a-form-item>

              <a-form-item v-if="fieldVis('tags')" field="tags" label="标签">
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
  getBugList,
  saveBug,
  updateBug,
  getBugDetail,
  getTestCaseList,
  getRequirementList,
  getRequirementDetail,
  getTestCaseDetail,
  getQaModuleList,
} from '@/api/MyApi/qa';
import {getUserListByPage} from '@/api/MyApi/user';
import useProjectConfigStore from '@/store/modules/projectConfig';

const projectConfigStore = useProjectConfigStore();
const fieldVis = (key: string) => projectConfigStore.isFieldVisible('bug', key);

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
const editorHeight = computed(() => (windowWidth.value >= 1200 ? '100%' : '32vh'));
onMounted(() => window.addEventListener('resize', handleWindowResize));
onBeforeUnmount(() => window.removeEventListener('resize', handleWindowResize));

const VNodeRenderer = {
  props: ['vnodes'],
  render(this: { vnodes: any }) {
    return this.vnodes;
  }
};

const formData = ref<any>({});
const isEdit = ref(false);
const initialFileIds = ref<Set<string>>(new Set());
const uploadedFileIds = ref<Set<string>>(new Set());
const initialSnapshot = ref('');

// 关联需求/用例的多选标签绑定（后端为单值，前端以数组形式展示标签气泡）
const requirementIds = ref<number[]>([]);
const testCaseIds = ref<number[]>([]);

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

const moduleOptions = ref<any[]>([]);

const requirementOptions = ref<any[]>([]);
const requirementPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

const testCaseOptions = ref<any[]>([]);
const testCasePage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

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
    severity: 'NORMAL',
    priority: 'MEDIUM',
    status: 'NEW',
    tags: []
  };
  requirementIds.value = [];
  testCaseIds.value = [];
  requirementOptions.value = [];
  testCaseOptions.value = [];
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
      requirementIds.value = formData.value.requirementId ? [Number(formData.value.requirementId)] : [];
      testCaseIds.value = formData.value.testCaseId ? [Number(formData.value.testCaseId)] : [];
      await loadRequirementOptions();
      await loadTestCaseOptions();
      await handleSearchUser('');
      if (formData.value.requirementId) {
        await ensureRequirementInOptions(formData.value.requirementId);
      }
      if (formData.value.testCaseId) {
        await ensureTestCaseInOptions(formData.value.testCaseId);
      }
      if (formData.value.assigneeId) {
        ensureUserInOptions(formData.value.assigneeId, formData.value.assigneeName);
      }
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

const ensureUserInOptions = (id: number, name?: string) => {
  const exists = userOptions.value.some((u: any) => u.id === id);
  if (exists) return;
  userOptions.value.unshift({id, username: name || '', nickname: name || ''});
};

const ensureTestCaseInOptions = async (id: number) => {
  const exists = testCaseOptions.value.some((tc: any) => tc.id === id);
  if (exists) return;
  try {
    const res: any = await getTestCaseDetail(id);
    if (res.data) {
      testCaseOptions.value.unshift(res.data);
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

const loadTestCaseOptions = async (keyword?: string, isLoadMore = false) => {
  if (!projectStore.getProjectId) return;
  if (!isLoadMore) {
    testCasePage.value.current = 1;
    testCasePage.value.keyword = keyword || '';
    testCaseOptions.value = [];
  }
  try {
    const res: any = await getTestCaseList(
        projectStore.getProjectId,
        undefined,
        undefined,
        testCasePage.value.keyword || undefined,
        undefined,
        undefined,
        testCasePage.value.current,
        testCasePage.value.pageSize
    );
    const records = res.data?.records || [];
    if (isLoadMore) {
      testCaseOptions.value.push(...records);
    } else {
      testCaseOptions.value = records;
    }
    testCasePage.value.hasMore = records.length >= testCasePage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};

const loadMoreTestCase = async () => {
  if (!testCasePage.value.hasMore || testCasePage.value.loading) return;
  testCasePage.value.loading = true;
  try {
    testCasePage.value.current++;
    await loadTestCaseOptions(undefined, true);
  } finally {
    testCasePage.value.loading = false;
  }
};

const handleSearchTestCase = (keyword: string) => {
  loadTestCaseOptions(keyword);
};

const handleTestCaseDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    loadTestCaseOptions('');
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
    Message.warning('请输入BUG标题');
    return false;
  }
  const payload = {...formData.value};
  if (payload.tags && Array.isArray(payload.tags)) {
    payload.tags = payload.tags.join(',');
  }
  // 关联需求/用例以多选标签展示，提交时取首个值或置空
  payload.requirementId = requirementIds.value[0] || null;
  payload.testCaseId = testCaseIds.value[0] || null;
  try {
    const api = isEdit.value ? updateBug : saveBug;
    const res: any = await api(payload);
    if (res.code === 200) {
      Message.success('保存成功');
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
    requirementIds.value = formData.value.requirementId ? [Number(formData.value.requirementId)] : [];
    testCaseIds.value = formData.value.testCaseId ? [Number(formData.value.testCaseId)] : [];
    await loadRequirementOptions();
    await loadTestCaseOptions();
    await handleSearchUser('');
    if (formData.value.requirementId) {
      await ensureRequirementInOptions(formData.value.requirementId);
    }
    if (formData.value.testCaseId) {
      await ensureTestCaseInOptions(formData.value.testCaseId);
    }
    if (formData.value.assigneeId) {
      ensureUserInOptions(formData.value.assigneeId, formData.value.assigneeName);
    }
  } else if (props.id) {
    isEdit.value = true;
    await loadDetail(props.id);
  } else {
    isEdit.value = false;
    resetForm();
    await loadRequirementOptions();
    await loadTestCaseOptions();
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

// 关联需求/用例最多只允许选中一个，保持与后端单值字段一致
watch(
    () => requirementIds.value,
    (val) => {
      if (val.length > 1) {
        requirementIds.value = [val[val.length - 1]];
      }
    },
    {deep: true}
);

watch(
    () => testCaseIds.value,
    (val) => {
      if (val.length > 1) {
        testCaseIds.value = [val[val.length - 1]];
      }
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
