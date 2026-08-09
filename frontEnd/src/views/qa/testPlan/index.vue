<template>
  <div class="test-plan-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testPlan']"/>
    <a-card class="test-plan-card" :title="$t('qa.testPlan.title')">
      <template #extra>
        <a-button v-permission="'qa:testplan:create'" type="primary" @click="handleAdd">
          <template #icon><icon-plus /></template>
          {{ $t('qa.testPlan.add') }}
        </a-button>
      </template>

      <a-row class="list-toolbar" justify="space-between" align="center" style="margin-bottom: 12px">
        <div class="search-items">
          <div class="search-item">
            <span class="search-label">计划名称</span>
            <a-input
                v-model="searchKeyword"
                :placeholder="$t('qa.testPlan.name')"
                allow-clear
                class="search-control"
            />
          </div>
          <div class="search-item">
            <span class="search-label">计划状态</span>
            <a-select
                v-model="searchStatus"
                :placeholder="$t('qa.testPlan.status')"
                allow-clear
                class="search-control"
            >
              <a-option value="DRAFT">草稿</a-option>
              <a-option value="READY">就绪</a-option>
              <a-option value="RUNNING">执行中</a-option>
              <a-option value="COMPLETED">已完成</a-option>
            </a-select>
          </div>
          <a-button type="primary" @click="handleSearch">
            <template #icon><icon-search /></template>
            {{ $t('qa.common.search') }}
          </a-button>
          <a-button @click="handleReset">{{ $t('qa.common.reset') }}</a-button>
        </div>
      </a-row>

      <!-- 统计卡片 -->
      <a-row class="stats-row" :gutter="12" style="margin-bottom: 12px">
        <a-col :span="6">
          <div class="stat-card stat-total" @click="handleReset">
            <div class="stat-number">{{ statsData.total }}</div>
            <div class="stat-label">计划总数</div>
          </div>
        </a-col>
        <a-col :span="6">
          <div
            class="stat-card stat-running"
            @click="
              searchStatus = 'RUNNING';
              handleSearch();
            "
          >
            <div class="stat-number">{{ statsData.running }}</div>
            <div class="stat-label">执行中</div>
          </div>
        </a-col>
        <a-col :span="6">
          <div
            class="stat-card stat-completed"
            @click="
              searchStatus = 'COMPLETED';
              handleSearch();
            "
          >
            <div class="stat-number">{{ statsData.completed }}</div>
            <div class="stat-label">已完成</div>
          </div>
        </a-col>
        <a-col :span="6">
          <div
            class="stat-card stat-draft"
            @click="
              searchStatus = 'DRAFT';
              handleSearch();
            "
          >
            <div class="stat-number">{{ statsData.draft }}</div>
            <div class="stat-label">草稿</div>
          </div>
        </a-col>
      </a-row>

      <div class="table-wrapper">
      <a-table
          :data="planList"
          :loading="loading"
          :pagination="pagination"
          :bordered="{ cell: true }"
          row-key="id"
          :sticky-header="true" :scroll="{ x: 'max-content' }"
          @page-change="handlePageChange"
          @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="计划名称" data-index="planName" :width="220">
            <template #cell="{ record }">
              <a-tooltip :content="record.planName">
                <span class="plan-name-link" @click="handleViewDetail(record)">{{ record.planName }}</span>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="状态" data-index="status" :width="100">
            <template #cell="{ record }">
              <a-tag :color="statusColor(record.status)" size="small">{{ statusText(record.status) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="描述" data-index="description" :width="240" :ellipsis="true">
            <template #cell="{ record }">
              <a-tooltip v-if="record.description" :content="record.description">
                <span>{{ record.description }}</span>
              </a-tooltip>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>
          <a-table-column title="开始时间" data-index="startTime" :width="160">
            <template #cell="{ record }">
              <span v-if="record.startTime">{{ record.startTime }}</span>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>
          <a-table-column title="结束时间" data-index="endTime" :width="160">
            <template #cell="{ record }">
              <span v-if="record.endTime">{{ record.endTime }}</span>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="120" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-tooltip content="详情">
                  <a-button type="text" size="small" @click="handleViewDetail(record)">
                    <template #icon><icon-eye /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="编辑">
                  <a-button v-permission="'qa:testplan:update'" type="text" size="small" @click="handleEdit(record)">
                    <template #icon><icon-edit /></template>
                  </a-button>
                </a-tooltip>
                <a-popconfirm
                    content="确认删除该测试计划吗？"
                    type="warning"
                    @ok="handleDelete(record.id)"
                >
                  <a-tooltip content="删除">
                    <a-button v-permission="'qa:testplan:delete'" type="text" size="small" status="danger">
                      <template #icon><icon-delete /></template>
                    </a-button>
                  </a-tooltip>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      </div>
    </a-card>

    <!-- 新增/编辑弹窗 -->
    <a-modal
        v-model:visible="modalVisible"
        :title="modalTitle"
        width="560px"
        @ok="handleSave"
        @cancel="modalVisible = false"
        :mask-closable="false"
    >
      <a-form :model="formData" layout="vertical">
        <a-form-item label="计划名称" required>
          <a-input v-model="formData.planName" placeholder="请输入计划名称"/>
        </a-form-item>
        <a-form-item label="计划描述">
          <a-textarea v-model="formData.description" :auto-size="{ minRows: 3 }" placeholder="请输入计划描述"/>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model="formData.status">
            <a-option value="DRAFT">草稿</a-option>
            <a-option value="READY">就绪</a-option>
            <a-option value="RUNNING">执行中</a-option>
            <a-option value="COMPLETED">已完成</a-option>
          </a-select>
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="开始时间">
              <a-date-picker v-model="formData.startTime" show-time style="width: 100%;"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="结束时间">
              <a-date-picker v-model="formData.endTime" show-time style="width: 100%;"/>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'TestPlan' };
</script>

<script setup lang="ts">
import {ref, onMounted, watch} from 'vue';
import {useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import {IconPlus, IconSearch, IconEye, IconEdit, IconDelete} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import {useProjectStore} from '@/store';
import {getTestPlanList, getTestPlanStats, saveTestPlan, updateTestPlan, deleteTestPlan} from '@/api/MyApi/qa';

const router = useRouter();
const projectStore = useProjectStore();

const loading = ref(false);
const planList = ref<any[]>([]);
const searchKeyword = ref('');
const searchStatus = ref('');
const pagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });

const statsData = ref({ total: 0, running: 0, completed: 0, draft: 0 });

const modalVisible = ref(false);
const modalTitle = ref('');
const formData = ref<any>({});
const isEdit = ref(false);

// 统计卡片：后端全项目口径聚合，不再只数当前页
const loadStats = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getTestPlanStats(projectStore.getProjectId);
    if (res.code === 200 && res.data) {
      statsData.value = { total: 0, running: 0, completed: 0, draft: 0, ...res.data };
    }
  } catch (e) {
    console.error(e);
  }
};

const loadData = async () => {
  if (!projectStore.getProjectId) return;
  loading.value = true;
  try {
    const res: any = await getTestPlanList(
        projectStore.getProjectId,
        searchKeyword.value || undefined,
        searchStatus.value || undefined,
        pagination.value.current,
        pagination.value.pageSize
    );
    planList.value = res.data?.records || [];
    pagination.value.total = res.data?.total || 0;
    loadStats();
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pagination.value.current = 1;
  loadData();
};

const handleReset = () => {
  searchKeyword.value = '';
  searchStatus.value = '';
  pagination.value.current = 1;
  loadData();
};

const handlePageChange = (page: number) => {
  pagination.value.current = page;
  loadData();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.value.pageSize = pageSize;
  pagination.value.current = 1;
  loadData();
};

const handleAdd = () => {
  isEdit.value = false;
  modalTitle.value = '新建测试计划';
  formData.value = {
    projectId: projectStore.getProjectId,
    status: 'DRAFT',
  };
  modalVisible.value = true;
};

const handleEdit = (record: any) => {
  isEdit.value = true;
  modalTitle.value = '编辑测试计划';
  formData.value = {...record};
  modalVisible.value = true;
};

const handleSave = async () => {
  if (!formData.value.planName || formData.value.planName.trim() === '') {
    Message.warning('请输入计划名称');
    return;
  }
  try {
    const api = isEdit.value ? updateTestPlan : saveTestPlan;
    const res: any = await api(formData.value);
    if (res.code === 200) {
      Message.success('保存成功');
      modalVisible.value = false;
      await loadData();
    }
  } catch (e) {
    console.error(e);
  }
};

const handleDelete = async (id: number) => {
  try {
    const res: any = await deleteTestPlan(id);
    if (res.code === 200) {
      Message.success('删除成功');
      await loadData();
    }
  } catch (e) {
    console.error(e);
  }
};

const handleViewDetail = (record: any) => {
  router.push({ name: 'TestPlanDetail', params: { planId: record.id } });
};

const statusColor = (status: string) => {
  const map: Record<string, string> = { DRAFT: 'gray', READY: 'blue', RUNNING: 'gold', COMPLETED: 'green' };
  return map[status] || 'gray';
};

const statusText = (status: string) => {
  const map: Record<string, string> = { DRAFT: '草稿', READY: '就绪', RUNNING: '执行中', COMPLETED: '已完成' };
  return map[status] || status;
};

onMounted(() => {
  loadData();
});

watch(
    () => projectStore.getProjectId,
    (newId) => {
      if (newId) loadData();
    },
    {immediate: true}
);
</script>

<style scoped lang="less">
.test-plan-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.test-plan-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.test-plan-card :deep(.arco-card-body) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.list-toolbar {
  flex-wrap: wrap;
}

.search-items {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.search-item {
  display: grid;
  grid-template-columns: 88px 180px;
  align-items: center;
  gap: 8px;
  width: 276px;
  flex-shrink: 0;
}

.search-label {
  width: 88px;
  color: var(--color-text-2);
  font-size: 14px;
  white-space: nowrap;
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
}

.search-control {
  width: 180px;
  min-width: 180px;
  max-width: 180px;
  flex-shrink: 0;
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.text-ellipsis {
  display: inline-block;
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.plan-name-link {
  color: rgb(var(--primary-6));
  cursor: pointer;
}
.plan-name-link:hover {
  color: rgb(var(--link-color-hover));
}
.stat-card {
  padding: 10px 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}
.stat-number {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  margin-top: 2px;
  opacity: 0.8;
}
.stat-total {
  background: #e8f4ff;
  color: rgb(var(--primary-6));
}
.stat-total:hover {
  border-color: rgb(var(--primary-6));
}
.stat-running {
  background: #fff7e8;
  color: #ff7d00;
}
.stat-running:hover {
  border-color: #ff7d00;
}
.stat-completed {
  background: #e8ffea;
  color: #00b42a;
}
.stat-completed:hover {
  border-color: #00b42a;
}
.stat-draft {
  background: #f2f3f5;
  color: #86909c;
}
.stat-draft:hover {
  border-color: #86909c;
}
</style>
