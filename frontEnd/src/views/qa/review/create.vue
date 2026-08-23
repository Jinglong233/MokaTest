<template>
  <div class="review-create-page">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testCase', isEdit ? 'menu.qa.reviewEdit' : 'menu.qa.reviewCreate']" />
    <a-card class="create-card" :bordered="false" :title="isEdit ? '编辑评审' : '新建评审'">
      <template #extra>
        <a-space>
          <a-button @click="handleBack">取消</a-button>
          <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
        </a-space>
      </template>

      <a-form :model="formData" layout="vertical" class="create-form">
        <a-row :gutter="20" class="form-row">
          <!-- 左侧主栏：评审内容 -->
          <a-col :xs="24" :lg="16" class="main-col">
            <a-form-item label="评审名称" required>
              <a-input v-model="formData.title" placeholder="请输入评审名称" :max-length="100" show-word-limit />
            </a-form-item>

            <a-form-item label="评审说明">
              <a-textarea v-model="formData.description" :auto-size="{ minRows: 3, maxRows: 6 }" placeholder="请输入评审说明" />
            </a-form-item>

            <!-- 关联用例：仅新建时展示；编辑评审的用例圈选收口到详情页 -->
            <div v-if="!isEdit" class="case-section">
              <div class="case-section-header">
                <span class="case-section-title">关联用例</span>
                <a-space>
                  <span v-if="selectedCases.length" class="case-count">已关联 {{ selectedCases.length }} 条</span>
                  <a-button type="outline" size="small" @click="caseDrawerVisible = true">
                    <template #icon><icon-link /></template>
                    关联用例
                  </a-button>
                </a-space>
              </div>
              <div class="case-table-wrap">
                <a-table
                  v-if="selectedCases.length"
                  :data="selectedCases"
                  :pagination="false"
                  row-key="id"
                  size="small"
                  :bordered="{ cell: true }"
                  :scroll="{ x: 'max-content', y: '100%' }"
                >
                  <template #columns>
                    <a-table-column title="用例编号" data-index="caseCode" :width="140" />
                    <a-table-column title="用例名称" data-index="caseName" :width="260" :ellipsis="true" />
                    <a-table-column title="优先级" data-index="priority" :width="80" align="center">
                      <template #cell="{ record }">
                        <a-tag :color="priorityColor(record.priority)" size="small">{{ record.priority }}</a-tag>
                      </template>
                    </a-table-column>
                    <a-table-column title="状态" data-index="status" :width="90" align="center">
                      <template #cell="{ record }">
                        <a-tag :color="caseStatusColor(record.status)" size="small">{{ caseStatusText(record.status) }}</a-tag>
                      </template>
                    </a-table-column>
                    <a-table-column title="操作" :width="70" align="center">
                      <template #cell="{ record }">
                        <a-button type="text" size="mini" status="danger" @click="removeCase(record)">
                          移除
                        </a-button>
                      </template>
                    </a-table-column>
                  </template>
                </a-table>
                <a-empty v-else description="未关联用例" style="padding: 24px 0" />
              </div>
            </div>
          </a-col>

          <!-- 右侧副栏：属性字段平铺 -->
          <a-col :xs="24" :lg="8" class="side-col">
            <a-form-item label="所属模块">
              <a-tree-select
                v-model="formData.moduleId"
                :data="reviewModuleTreeData"
                :field-names="{ key: 'id', title: 'moduleName' }"
                placeholder="不选则为根模块"
                allow-clear
              />
            </a-form-item>

            <!-- 通过规则仅新建时可定，编辑时锁定（改规则会让已投结论的口径失效） -->
            <a-form-item v-if="!isEdit" label="通过规则" required>
              <a-radio-group v-model="formData.passRule">
                <a-radio value="ALL">全票通过</a-radio>
                <a-radio value="MAJORITY">多数通过</a-radio>
              </a-radio-group>
            </a-form-item>

            <a-form-item label="截止时间">
              <a-date-picker v-model="formData.deadline" show-time style="width: 100%" />
            </a-form-item>

            <a-form-item label="默认评审人" required extra="新圈入用例默认由这些评审人评审；单条用例评审人清空后自动回退为默认评审人">
              <a-select
                v-model="formData.reviewerIds"
                multiple
                allow-search
                :filter-option="false"
                placeholder="请输入关键词搜索用户"
                :max-tag-count="5"
                @search="handleSearchUser"
                @dropdown-visible-change="handleUserDropdownVisibleChange"
                @dropdown-reach-bottom="loadMoreUser"
              >
                <a-option v-for="u in userOptions" :key="u.id" :value="u.id">{{ u.nickname || u.username }}</a-option>
              </a-select>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-card>

    <!-- 用例选择器抽屉 -->
    <CaseSelectDrawer
      v-model:visible="caseDrawerVisible"
      :project-id="projectStore.getProjectId"
      :selected-ids="selectedCaseIds"
      @confirm="handleCaseConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { IconLink } from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import { useProjectStore } from '@/store';
import {
  saveTestCaseReview,
  updateTestCaseReview,
  getTestCaseReviewDetail,
  getQaModuleTree,
} from '@/api/MyApi/qa';
import { getUserListByPage } from '@/api/MyApi/user';
import CaseSelectDrawer from './components/CaseSelectDrawer.vue';

const route = useRoute();
const router = useRouter();
const projectStore = useProjectStore();

// 编辑模式：路由带 :id 参数则为编辑
const editId = computed(() => route.params.id ? Number(route.params.id) : 0);
const isEdit = computed(() => editId.value > 0);

const formData = ref<any>({
  projectId: projectStore.getProjectId,
  moduleId: undefined,
  title: '',
  description: '',
  passRule: 'ALL',
  deadline: undefined,
  caseIds: [],
  reviewerIds: [],
});

const saving = ref(false);
const reviewModuleTreeData = ref<any[]>([]);

// 已选用例
const selectedCases = ref<any[]>([]);
const selectedCaseIds = computed(() => selectedCases.value.map((c: any) => c.id));
const caseDrawerVisible = ref(false);

// 用户远程搜索 + 滚动加载
const userOptions = ref<any[]>([]);
const userPage = ref({ current: 1, pageSize: 20, keyword: '', hasMore: false, loading: false });

const loadReviewModuleTree = async () => {
  const pid = projectStore.getProjectId;
  if (!pid) return;
  try {
    const res: any = await getQaModuleTree(pid, 'REVIEW');
    reviewModuleTreeData.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const handleCaseConfirm = (cases: any[]) => {
  // 合并：保留已有的 + 新增的，去重
  const existingIds = new Set(selectedCases.value.map((c: any) => c.id));
  const newCases = cases.filter((c: any) => !existingIds.has(c.id));
  selectedCases.value = [...selectedCases.value, ...newCases];
};

const removeCase = (record: any) => {
  selectedCases.value = selectedCases.value.filter((c: any) => c.id !== record.id);
};

// 用户搜索
const loadUserOptions = async (keyword?: string, isLoadMore = false) => {
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
      pageSize: userPage.value.pageSize,
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

const handleSearchUser = (keyword?: string) => loadUserOptions(keyword);
const handleUserDropdownVisibleChange = (visible: boolean) => {
  if (visible) loadUserOptions('');
};
const loadMoreUser = async () => {
  if (!userPage.value.hasMore || userPage.value.loading) return;
  userPage.value.loading = true;
  try {
    userPage.value.current++;
    await loadUserOptions(undefined, true);
  } finally {
    userPage.value.loading = false;
  }
};

const handleSave = async () => {
  if (!formData.value.title || formData.value.title.trim() === '') {
    Message.warning('请输入评审名称');
    return;
  }
  if (!formData.value.reviewerIds || formData.value.reviewerIds.length === 0) {
    Message.warning('请选择默认评审人');
    return;
  }
  saving.value = true;
  try {
    formData.value.caseIds = selectedCaseIds.value;
    const res: any = isEdit.value
      ? await updateTestCaseReview(editId.value, formData.value)
      : await saveTestCaseReview(formData.value);
    if (res.code === 200) {
      Message.success(isEdit.value ? '更新成功' : '创建成功');
      if (isEdit.value) {
        router.push({ name: 'qaReviewDetail', params: { id: editId.value } });
      } else {
        router.push({ name: 'qaReview' });
      }
    } else {
      Message.error(res.msg || (isEdit.value ? '更新失败' : '创建失败'));
    }
  } catch (e) {
    console.error(e);
  } finally {
    saving.value = false;
  }
};

const handleBack = () => {
  router.push({ name: 'TestCase', query: { tab: 'review' } });
};

const loadDetail = async () => {
  if (!editId.value) return;
  try {
    const res: any = await getTestCaseReviewDetail(editId.value);
    if (res.code === 200 && res.data) {
      const d = res.data;
      formData.value = {
        projectId: d.projectId,
        moduleId: d.moduleId,
        title: d.title || '',
        description: d.description || '',
        passRule: d.passRule || 'ALL',
        deadline: d.deadline,
        caseIds: [],
        reviewerIds: [],
      };
      // 填充已选用例
      if (d.cases) {
        selectedCases.value = d.cases.map((c: any) => ({
          id: c.testCaseId,
          caseCode: c.caseCode,
          caseName: c.caseName,
          priority: c.priority,
          status: c.caseStatus,
        }));
      }
      // 填充评审人，同时补入 userOptions 以保证下拉可回显
      if (d.reviewers) {
        formData.value.reviewerIds = d.reviewers.map((r: any) => r.reviewerId);
        const existing = new Set(userOptions.value.map((u: any) => u.id));
        for (const r of d.reviewers) {
          if (!existing.has(r.reviewerId)) {
            userOptions.value.push({ id: r.reviewerId, nickname: r.reviewerName, username: r.reviewerName });
          }
        }
      }
    }
  } catch (e) {
    console.error(e);
  }
};

const priorityColor = (p: string) => ({ P0: 'red', P1: 'orange', P2: 'blue' } as Record<string, string>)[p] || 'gray';
const caseStatusColor = (s: string) => ({ DRAFT: 'gray', REVIEWING: 'gold', REVIEWED: 'green', DEPRECATED: 'red' } as Record<string, string>)[s] || 'gray';
const caseStatusText = (s: string) => ({ DRAFT: '草稿', REVIEWING: '评审中', REVIEWED: '已评审', DEPRECATED: '已废弃' } as Record<string, string>)[s] || s;

onMounted(() => {
  loadReviewModuleTree();
  loadUserOptions('');
  if (isEdit.value) loadDetail();
});
</script>

<style scoped lang="less">
.review-create-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.create-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.create-card :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.create-form {
  height: 100%;
}

.form-row {
  height: 100%;
}

.main-col,
.side-col {
  min-width: 0;
}

// 主栏 label 前置竖向蓝条（与用例编辑页保持一致）
.main-col :deep(.arco-form-item-label),
.case-section-title {
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

.case-section {
  margin-top: 4px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.case-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.case-section-title {
  font-weight: 500;
  color: var(--color-text-2);
  display: inline-block;
}

.required-mark {
  color: #f53f3f;
  margin-left: 2px;
}

.case-count {
  color: var(--color-text-2);
  font-size: 13px;
}

.case-table-wrap {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
</style>
