<template>
  <div class="test-plan-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testPlan']"/>
    <a-tabs v-model:active-key="activeMainTab" class="plan-main-tabs" :lazy-load="true">
        <!-- Tab: 测试计划列表 -->
        <a-tab-pane key="list" title="测试计划列表">
      <div class="main-row">
        <!-- 左侧模块树 -->
        <div class="col side-col" :style="sideColStyle">
          <a-card class="tree-card" size="small" :bordered="false">
            <div class="tree-scroll-wrapper">
              <ModuleTreeManager
                  ref="moduleTreeRef"
                  module-type="PLAN"
                  root-name="全部计划"
                  :show-search="true"
                  :show-virtual-root="true"
                  :selected-keys="selectedModuleKeys"
                  default-expand-all
                  @select="handleModuleSelect"
              >
                <template #search-extra>
                  <a-button v-permission="'qa:testplan:create'" type="primary" size="small" @click="handleAdd">新建</a-button>
                </template>
              </ModuleTreeManager>
            </div>
          </a-card>
        </div>
        <div
            class="main-resizer"
            :class="{ 'is-collapsed': isSidebarCollapsed }"
            @mousedown="startResizeSidebar"
        >
          <div v-if="isSidebarCollapsed" class="sidebar-expand-btn">
            <icon-right class="sidebar-expand-icon" />
          </div>
          <span v-else class="resizer-line" />
        </div>
        <!-- 右侧列表 -->
        <div class="col content-col">
          <a-card class="list-card" :bordered="false">
          <div class="plan-main">

      <!-- 筛选区 -->
      <a-row :wrap="true" :gutter="[16, 12]" align="center" class="filter-grid" style="margin: 8px 0 16px;">
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">计划名称</label>
            <a-input v-model="searchKeyword" :placeholder="$t('qa.testPlan.name')" allow-clear />
          </div>
        </a-col>
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">计划状态</label>
            <a-select v-model="searchStatus" :placeholder="$t('qa.testPlan.status')" allow-clear>
              <a-option value="DRAFT">草稿</a-option>
              <a-option value="READY">就绪</a-option>
              <a-option value="RUNNING">执行中</a-option>
              <a-option value="COMPLETED">已完成</a-option>
            </a-select>
          </div>
        </a-col>
        <a-col flex="auto">
          <div class="filter-actions">
            <a-button type="primary" :loading="loading" @click="handleSearch">搜索</a-button>
            <a-button @click="handleReset">重置</a-button>
          </div>
        </a-col>
      </a-row>

      <div class="table-wrapper">
      <a-table
          :data="planList"
          :columns="columns"
          :loading="loading"
          :pagination="pagination"
          :bordered="{ wrapper: true, cell: false }"
          row-key="id"
          :sticky-header="true" :scroll="{ x: '125%', y: '100%' }"
          @page-change="handlePageChange"
          @page-size-change="handlePageSizeChange"
      >
        <template #planName="{ record }">
          <a-tooltip :content="record.planName">
            <span class="plan-name-link" @click="handleViewDetail(record)">{{ record.planName }}</span>
          </a-tooltip>
        </template>
        <template #status="{ record }">
          <a-tag :color="statusColor(record.status)" size="small">{{ statusText(record.status) }}</a-tag>
        </template>
        <template #createUserName="{ record }">{{ record.createUserName || '-' }}</template>
        <template #passRate="{ record }">
          <a-progress
            v-if="record.caseCount > 0"
            :percent="record.passRate / 100"
            size="small"
            :style="{ width: '100%' }"
          />
          <span v-else style="color: #86909c;">-</span>
        </template>
        <template #execResult="{ record }">
          <a-tag v-if="record.execResult && record.execResult !== 'NONE'" :color="execResultColor(record.execResult)" size="small">
            <template #icon><component :is="execResultIcon(record.execResult)" /></template>
            {{ execResultText(record.execResult) }}
          </a-tag>
          <span v-else style="color: #86909c;">-</span>
        </template>
        <template #caseCount="{ record }">{{ record.caseCount ?? 0 }}</template>
        <template #tags="{ record }">
          <template v-if="record.tags">
            <a-tag v-for="tag in String(record.tags).split(',').filter(Boolean)" :key="tag" size="small" style="margin-right: 4px">{{ tag }}</a-tag>
          </template>
          <span v-else style="color: #86909c;">-</span>
        </template>
        <template #moduleName="{ record }">{{ record.moduleName || '-' }}</template>
        <template #actions="{ record }">
          <a-space :size="0">
            <a-button
                v-for="op in visibleOps(record)"
                :key="op.key"
                v-permission="op.permission"
                type="text"
                size="small"
                :status="op.danger ? 'danger' : undefined"
                @click="op.handler(record)"
            >
              {{ op.label }}
            </a-button>
            <a-dropdown v-if="overflowOps(record).length" trigger="click">
              <a-button type="text" size="small" @click.stop>
                ...
              </a-button>
              <template #content>
                <a-doption
                    v-for="op in overflowOps(record)"
                    :key="op.key"
                    v-permission="op.permission"
                    @click="op.handler(record)"
                >
                  <span :style="op.danger ? 'color: rgb(var(--danger-6))' : ''">{{ op.label }}</span>
                </a-doption>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </a-table>
      </div>
          </div>
          </a-card>
        </div>
      </div>
        </a-tab-pane>

        <!-- Tab: 测试计划报告 -->
        <a-tab-pane key="report" title="测试计划报告">
          <a-card class="list-card" :bordered="false">
          <a-row class="list-toolbar" justify="space-between" align="center" style="margin-bottom: 12px">
            <div class="search-items">
              <div class="search-item">
                <span class="search-label">报告名称</span>
                <a-input v-model="reportKeyword" placeholder="搜索报告名称" allow-clear class="search-control" @change="loadReportList" />
              </div>
            </div>
          </a-row>
          <a-table
              :data="reportList"
              :columns="reportColumns"
              :loading="reportLoading"
              :pagination="reportPagination"
              :bordered="{ cell: true }"
              row-key="id"
              :sticky-header="true"
              :scroll="{ x: '125%' }"
              @page-change="handleReportPageChange"
          >
            <template #reportName="{ record }">
              <a-tooltip :content="record.reportName">
                <span class="plan-name-link" @click="handleViewReport(record)">{{ record.reportName }}</span>
              </a-tooltip>
            </template>
            <template #reportType="{ record }">{{ record.reportType === 'PLAN' ? '计划报告' : record.reportType || '-' }}</template>
            <template #reportExecResult="{ record }">
              <a-tag v-if="record.execResult && record.execResult !== 'NONE'" :color="execResultColor(record.execResult)" size="small">
                <template #icon><component :is="execResultIcon(record.execResult)" /></template>
                {{ execResultText(record.execResult) }}
              </a-tag>
              <span v-else style="color: #86909c;">-</span>
            </template>
            <template #reportPassRate="{ record }">{{ record.passRate != null ? (record.passRate + '%') : '-' }}</template>
            <template #triggerType="{ record }">
              <a-tag size="small" :color="record.triggerType === 'AUTO' ? 'arcoblue' : 'gray'">{{ record.triggerType === 'AUTO' ? '自动' : '手动' }}</a-tag>
            </template>
            <template #reportCreateUserName="{ record }">{{ record.createUserName || (record.triggerType === 'AUTO' ? '系统' : '-') }}</template>
            <template #reportActions="{ record }">
              <a-space>
                <a-tooltip content="导出PDF">
                  <a-button v-permission="'qa:testplan:view'" type="text" size="small" @click="handleExportReport(record)">
                    <template #icon><icon-download /></template>
                  </a-button>
                </a-tooltip>
                <a-popconfirm content="确认删除该报告吗？" type="warning" @ok="handleDeleteReport(record.id)">
                  <a-button v-permission="'qa:testplan:delete'" type="text" size="small" status="danger">
                    <template #icon><icon-delete /></template>
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table>
          </a-card>
        </a-tab-pane>
      </a-tabs>

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
        <a-form-item label="所属模块">
          <a-tree-select
              v-model="formData.moduleId"
              :data="moduleTreeData"
              :field-names="{ key: 'id', title: 'moduleName', children: 'children' }"
              allow-clear
              placeholder="选择所属模块"
              style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="计划描述">
          <a-textarea v-model="formData.description" :auto-size="{ minRows: 3 }" placeholder="请输入计划描述"/>
        </a-form-item>
        <a-form-item label="标签">
          <a-input-tag v-model="formData.tags" placeholder="输入后回车添加标签" allow-clear style="width: 100%"/>
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
import {ref, computed, onMounted, onBeforeUnmount, watch} from 'vue';
import {useRouter} from 'vue-router';
import {Message, Modal} from '@arco-design/web-vue';
import {IconPlus, IconSearch, IconDelete, IconDownload, IconRight} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import ModuleTreeManager from '@/views/qa/components/ModuleTreeManager.vue';
import {useProjectStore} from '@/store';
import {getTestPlanList, getTestPlanStats, saveTestPlan, updateTestPlan, deleteTestPlan, getTestPlanDetail, addCasesToPlan, getQaModuleTree, getPlanReportList, deletePlanReport} from '@/api/MyApi/qa';

const router = useRouter();
const projectStore = useProjectStore();

const loading = ref(false);
const activeMainTab = ref('list');
const planList = ref<any[]>([]);
const searchKeyword = ref('');
const searchStatus = ref('');
const searchModuleId = ref<number | undefined>(undefined);
const selectedModuleKeys = ref<(string | number)[]>([]);
const moduleTreeRef = ref<any>(null);
const moduleOptions = ref<any[]>([]);
const pagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });

const statsData = ref({ total: 0, running: 0, completed: 0, draft: 0 });

// ===== 左侧目录树：可拖拽伸缩 + 拖拽隐藏（与用例列表一致） =====
const SIDEBAR_DEFAULT_WIDTH = 280;
const SIDEBAR_MIN_WIDTH = 220;
const SIDEBAR_MAX_WIDTH = 480;
const SIDEBAR_COLLAPSE_THRESHOLD = 20;

const windowWidth = ref(window.innerWidth);
const sidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);
// 窄屏（<992px）默认折叠侧栏，把空间让给列表
const isSidebarCollapsed = ref(window.innerWidth < 992);
const lastSidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);

// 侧栏最大宽度跟随浏览器窗口：不超过窗口的 40%，避免小屏下树把列表挤没
const sidebarMaxWidth = computed(() =>
    Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, Math.floor(windowWidth.value * 0.4)))
);

const sideColStyle = computed(() => ({
  width: isSidebarCollapsed.value ? '0px' : `${sidebarWidth.value}px`,
  overflow: 'hidden',
}));

const collapseSidebar = () => {
  if (!isSidebarCollapsed.value) {
    lastSidebarWidth.value = Math.max(sidebarWidth.value, SIDEBAR_MIN_WIDTH);
  }
  isSidebarCollapsed.value = true;
  sidebarWidth.value = 0;
};

const expandSidebar = () => {
  isSidebarCollapsed.value = false;
  const expandedWidth = Math.round((lastSidebarWidth.value || SIDEBAR_DEFAULT_WIDTH) * 1.25);
  sidebarWidth.value = Math.min(expandedWidth, sidebarMaxWidth.value);
};

const handleWindowResize = () => {
  windowWidth.value = window.innerWidth;
};
onMounted(() => window.addEventListener('resize', handleWindowResize));
onBeforeUnmount(() => window.removeEventListener('resize', handleWindowResize));

// 窗口尺寸变化时：收窄侧栏不超过动态上限；窄屏自动折叠侧栏
watch(windowWidth, (w) => {
  if (sidebarWidth.value > sidebarMaxWidth.value) {
    sidebarWidth.value = sidebarMaxWidth.value;
  }
  if (w < 992 && !isSidebarCollapsed.value) {
    collapseSidebar();
  }
});

const startResizeSidebar = (e: MouseEvent) => {
  const startX = e.clientX;
  const wasCollapsed = isSidebarCollapsed.value;
  const startWidth = wasCollapsed ? 0 : sidebarWidth.value;
  let moved = false;

  const onMove = (ev: MouseEvent) => {
    if (Math.abs(ev.clientX - startX) > 3) moved = true;
    if (wasCollapsed) {
      isSidebarCollapsed.value = false;
    }
    const newWidth = startWidth + ev.clientX - startX;
    sidebarWidth.value = Math.min(Math.max(newWidth, 0), sidebarMaxWidth.value);
  };

  const onUp = () => {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.body.style.userSelect = '';

    if (!moved && wasCollapsed) {
      expandSidebar();
      return;
    }

    if (sidebarWidth.value <= SIDEBAR_COLLAPSE_THRESHOLD) {
      collapseSidebar();
    } else if (sidebarWidth.value < SIDEBAR_MIN_WIDTH) {
      sidebarWidth.value = SIDEBAR_MIN_WIDTH;
    }
  };

  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
  document.body.style.userSelect = 'none';
};

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
        searchModuleId.value,
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
    tags: [],
  };
  loadModuleOptions();
  modalVisible.value = true;
};

const handleEdit = (record: any) => {
  isEdit.value = true;
  modalTitle.value = '编辑测试计划';
  formData.value = {...record, tags: record.tags ? String(record.tags).split(',').filter(Boolean) : []};
  loadModuleOptions();
  modalVisible.value = true;
};

const handleSave = async () => {
  if (!formData.value.planName || formData.value.planName.trim() === '') {
    Message.warning('请输入计划名称');
    return;
  }
  try {
    const payload = {...formData.value};
    if (Array.isArray(payload.tags)) payload.tags = payload.tags.join(',');
    const api = isEdit.value ? updateTestPlan : saveTestPlan;
    const res: any = await api(payload);
    if (res.code === 200) {
      Message.success('保存成功');
      modalVisible.value = false;
      if (!isEdit.value && res.data?.id) {
        // 新建计划后自动跳转详情页（对标 MeterSphere v3）
        router.push({ name: 'TestPlanDetail', params: { planId: res.data.id } });
      } else {
        await loadData();
      }
    }
  } catch (e) {
    console.error(e);
  }
};

const handleCopy = async (record: any) => {
  try {
    // 获取计划详情（含关联用例）
    const detailRes: any = await getTestPlanDetail(record.id);
    if (detailRes.code !== 200 || !detailRes.data?.plan) {
      Message.error('获取计划详情失败');
      return;
    }
    const sourcePlan = detailRes.data.plan;
    const sourceCases = detailRes.data.cases || [];
    // 构造新计划对象
    const newPlan = {
      projectId: sourcePlan.projectId,
      planName: `${sourcePlan.planName}（副本）`,
      description: sourcePlan.description,
      status: 'DRAFT',
      startTime: null,
      endTime: null,
    };
    const createRes: any = await saveTestPlan(newPlan);
    if (createRes.code !== 200 || !createRes.data?.id) {
      Message.error('复制计划失败');
      return;
    }
    const newPlanId = createRes.data.id;
    // 复制关联用例
    if (sourceCases.length > 0) {
      const caseIds = sourceCases.map((c: any) => c.testCaseId);
      await addCasesToPlan(newPlanId, caseIds);
    }
    Message.success('复制成功');
    await loadData();
  } catch (e) {
    console.error(e);
    Message.error('复制计划失败');
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

// 执行：进入用例执行页
const handleExecute = (record: any) => {
  router.push({ name: 'TestPlanExecute', params: { planId: record.id } });
};

// 删除（更多下拉内，用 Modal 确认）
const handleDeleteConfirm = (id: number) => {
  Modal.confirm({
    title: '删除测试计划',
    content: '确认删除该测试计划吗？',
    onOk: async () => { await handleDelete(id); },
  });
};

// 主表列定义
const columns = [
  { title: '计划编号', dataIndex: 'planCode', width: 100, align: 'center', headerAlign: 'center', ellipsis: true, tooltip: true },
  { title: '计划名称', dataIndex: 'planName', width: 260, ellipsis: true, headerAlign: 'left', slotName: 'planName' },
  { title: '计划状态', dataIndex: 'status', width: 90, align: 'center', headerAlign: 'center', slotName: 'status' },
  { title: '创建人', dataIndex: 'createUserName', width: 100, align: 'center', headerAlign: 'center', ellipsis: true, tooltip: true, slotName: 'createUserName' },
  { title: '通过率', dataIndex: 'passRate', width: 130, align: 'center', headerAlign: 'center', slotName: 'passRate' },
  { title: '执行结果', dataIndex: 'execResult', width: 100, align: 'center', headerAlign: 'center', slotName: 'execResult' },
  { title: '用例数', dataIndex: 'caseCount', width: 80, align: 'center', headerAlign: 'center', slotName: 'caseCount' },
  { title: '标签', dataIndex: 'tags', headerAlign: 'left', slotName: 'tags' },
  { title: '所属模块', dataIndex: 'moduleName', width: 120, headerAlign: 'left', ellipsis: true, tooltip: true, slotName: 'moduleName' },
  { title: '创建时间', dataIndex: 'createTime', width: 180, ellipsis: true, align: 'center', headerAlign: 'center' },
  { title: '操作', width: 200, fixed: 'right' as const, align: 'center', headerAlign: 'center', slotName: 'actions' },
];

// 操作列：统一文字按钮，前 2 个常显，第 3 个起收入「更多」下拉
interface TestPlanOp {
  key: string;
  label: string;
  danger?: boolean;
  permission?: string;
  handler: (record: any) => void;
}
const opsFor = (record: any): TestPlanOp[] => [
  { key: 'execute', label: '执行', permission: 'qa:testplan:execute', handler: handleExecute },
  { key: 'edit', label: '编辑', permission: 'qa:testplan:update', handler: handleEdit },
  { key: 'copy', label: '复制', permission: 'qa:testplan:create', handler: handleCopy },
  { key: 'delete', label: '删除', danger: true, permission: 'qa:testplan:delete', handler: (record) => handleDeleteConfirm(record.id) },
];
const visibleOps = (record: any) => opsFor(record).slice(0, 2);
const overflowOps = (record: any) => opsFor(record).slice(2);

// 执行结果汇总展示：NONE 显示 -
const execResultText = (r: string) => ({
  ALL_PASS: '通过', NOT_PASS: '未通过',
} as Record<string, string>)[r] || '-';
const execResultColor = (r: string) => ({
  ALL_PASS: 'green', NOT_PASS: 'red',
} as Record<string, string>)[r] || 'gray';
const execResultIcon = (r: string) => ({
  ALL_PASS: 'icon-check-circle-fill', NOT_PASS: 'icon-close-circle-fill',
} as Record<string, string>)[r] || 'icon-clock-circle';

// ===== 报告列表 tab =====
const reportList = ref<any[]>([]);
const reportLoading = ref(false);
const reportKeyword = ref('');
const reportPagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true });

const reportColumns = [
  { title: '报告名称', dataIndex: 'reportName', width: 260, ellipsis: true, slotName: 'reportName' },
  { title: '报告类型', dataIndex: 'reportType', width: 100, align: 'center', headerAlign: 'center', ellipsis: true, tooltip: true, slotName: 'reportType' },
  { title: '计划名称', dataIndex: 'planName', width: 200, ellipsis: true },
  { title: '执行结果', dataIndex: 'execResult', width: 100, align: 'center', headerAlign: 'center', slotName: 'reportExecResult' },
  { title: '通过率', dataIndex: 'passRate', width: 90, align: 'center', headerAlign: 'center', ellipsis: true, tooltip: true, slotName: 'reportPassRate' },
  { title: '触发方式', dataIndex: 'triggerType', width: 100, align: 'center', headerAlign: 'center', slotName: 'triggerType' },
  { title: '创建人', dataIndex: 'createUserName', width: 100, align: 'center', headerAlign: 'center', ellipsis: true, tooltip: true, slotName: 'reportCreateUserName' },
  { title: '创建时间', dataIndex: 'createTime', width: 160, ellipsis: true, align: 'center', headerAlign: 'center' },
  { title: '操作', width: 120, fixed: 'right' as const, align: 'center', headerAlign: 'center', slotName: 'reportActions' },
];

const loadReportList = async () => {
  if (!projectStore.getProjectId) return;
  reportLoading.value = true;
  try {
    const res: any = await getPlanReportList(projectStore.getProjectId, reportKeyword.value || undefined, reportPagination.value.current, reportPagination.value.pageSize);
    reportList.value = res.data?.records || [];
    reportPagination.value.total = res.data?.total || 0;
  } catch (e) {
    console.error(e);
  } finally {
    reportLoading.value = false;
  }
};

const handleReportPageChange = (page: number) => {
  reportPagination.value.current = page;
  loadReportList();
};

const handleViewReport = (record: any) => {
  router.push({ name: 'TestPlanReportDetail', params: { id: record.id } });
};

// 导出：跳详情页并自动触发打印
const handleExportReport = (record: any) => {
  router.push({ name: 'TestPlanReportDetail', params: { id: record.id }, query: { print: '1' } });
};

const handleDeleteReport = async (id: number) => {
  try {
    const res: any = await deletePlanReport(id);
    if (res.code === 200) {
      Message.success('删除成功');
      await loadReportList();
    }
  } catch (e) {
    console.error(e);
  }
};

// 切到报告 tab 时加载
watch(activeMainTab, (key) => {
  if (key === 'report') loadReportList();
});

const handleModuleSelect = (selectedKeys: any[], nodeData: any) => {
  selectedModuleKeys.value = selectedKeys;
  const id = Number(nodeData?.id ?? 0);
  searchModuleId.value = id === 0 ? undefined : id;
  pagination.value.current = 1;
  loadData();
};

const loadModuleOptions = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getQaModuleTree(projectStore.getProjectId, 'PLAN');
    moduleOptions.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const moduleTreeData = computed(() => moduleOptions.value);

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
      if (newId) {
        loadData();
        moduleTreeRef.value?.loadData?.();
      }
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

.list-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.list-card :deep(.arco-card-body) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

// tabs 链条向内容区传递高度（与用例列表一致）
.plan-main-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.plan-main-tabs :deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
  padding-top: 8px;
  overflow: hidden;
}

.plan-main-tabs :deep(.arco-tabs-content-list),
.plan-main-tabs :deep(.arco-tabs-pane) {
  height: 100%;
}

.plan-main-tabs :deep(.arco-tabs-pane) {
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}

// ===== 左右分栏 + 伸缩分隔条（与用例列表一致） =====
.main-row {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.col {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.side-col {
  flex-shrink: 0;
  transition: none;
}

.content-col {
  flex: 1;
  min-width: 0;
}

.tree-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tree-card :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tree-scroll-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.main-resizer {
  flex-shrink: 0;
  width: 6px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: col-resize;
  background: transparent;
  user-select: none;
}

.main-resizer.is-collapsed {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  border-right: 1px solid var(--color-border-2);
  position: relative;
}

.sidebar-expand-btn {
  position: absolute;
  left: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-left: none;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  z-index: 10;
}

.sidebar-expand-btn:hover {
  background: var(--color-fill-2);
}

.sidebar-expand-icon {
  color: rgb(var(--primary-6));
  font-size: 14px;
}

.main-resizer:hover .resizer-line,
.main-resizer:active .resizer-line {
  background: rgb(var(--primary-6));
}

.resizer-line {
  width: 2px;
  height: 24px;
  border-radius: 1px;
  background: var(--color-border-2);
}

.plan-main {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
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

.filter-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.filter-field {
  display: flex;
  align-items: center;
  gap: 8px;

  :deep(.arco-input-wrapper),
  :deep(.arco-select-view) {
    flex: 1;
    min-width: 0;
  }
}

.filter-label {
  width: 72px;
  flex-shrink: 0;
  font-size: 13px;
  color: var(--color-text-2);
  text-align: right;
  white-space: nowrap;
}

.filter-actions {
  display: flex;
  gap: 8px;
  align-items: center;

  :deep(.arco-btn) {
    min-width: 72px;
  }
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.table-wrapper :deep(.arco-table) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}
.table-wrapper :deep(.arco-table-body) {
  flex: 1;
  overflow-y: auto;
}
.table-wrapper :deep(.arco-table-pagination) {
  flex-shrink: 0;
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
