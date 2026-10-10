<template>
  <div class="review-card" :class="{ 'review-card-embedded': embedded }">
    <div class="review-body">
      <div class="main-row">
        <!-- 左侧模块树卡片 -->
        <div class="col side-col" :style="sideColStyle">
          <a-card class="pane-card" size="small">
            <ModuleTreeManager
              ref="moduleTreeRef"
              module-type="REVIEW"
              :show-search="true"
              :show-virtual-root="true"
              :selected-keys="selectedModuleKeys"
              default-expand-all
              @select="handleModuleSelect"
            >
              <template #search-extra>
                <a-button type="primary" size="small" @click="handleAdd">
                  {{ $t('qa.review.add') }}
                </a-button>
              </template>
            </ModuleTreeManager>
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
        <!-- 右侧评审单列表卡片 -->
        <div class="col content-col">
          <a-card class="pane-card">
            <div class="review-main">
    <a-row class="list-toolbar" justify="space-between" align="center" style="margin-bottom: 12px">
      <div class="search-items">
        <div class="search-item">
          <span class="search-label">{{ $t('qa.review.keyword') }}</span>
          <a-input
              v-model="searchKeyword"
              :placeholder="$t('qa.review.searchPlaceholder')"
              allow-clear
              class="search-control"
              @press-enter="handleSearch"
          />
        </div>
        <div class="search-item">
          <span class="search-label">{{ $t('qa.testCase.status') }}</span>
          <a-select v-model="searchStatus" :placeholder="$t('qa.testCase.status')" allow-clear class="search-control">
            <a-option value="REVIEWING">{{ $t('qa.review.status.reviewing') }}</a-option>
            <a-option value="COMPLETED">{{ $t('qa.review.status.completed') }}</a-option>
            <a-option value="CANCELLED">{{ $t('qa.review.status.cancelled') }}</a-option>
          </a-select>
        </div>
        <a-button type="primary" @click="handleSearch">
          <template #icon><icon-search /></template>
          {{ $t('qa.common.search') }}
        </a-button>
        <a-button @click="handleReset">{{ $t('qa.common.reset') }}</a-button>
      </div>
    </a-row>

    <div class="table-wrapper">
      <!-- 批量操作工具栏 -->
      <div class="batch-bar">
        <a-dropdown-button
          size="small"
          type="outline"
          :disabled="!selectedIds.length"
        >
          {{ $t('qa.review.batchOperation') }}
          <template #icon><icon-more /></template>
          <template #content>
            <a-doption
              :disabled="!selectedIds.length"
              @click="moveModalVisible = true; loadMoveModuleTree()"
            >
              <template #icon><icon-to-right /></template>
              {{ $t('qa.review.moveToModule') }}
            </a-doption>
            <a-doption
              :disabled="!selectedIds.length"
              @click="handleBatchDelete"
            >
              <template #icon><icon-delete /></template>
              <span style="color: #f53f3f;">{{ $t('qa.review.batchDelete') }}</span>
            </a-doption>
          </template>
        </a-dropdown-button>
      </div>
      <a-table
          :data="reviewList"
          :columns="columns"
          :loading="loading"
          :pagination="pagination"
          :bordered="{ cell: true }"
          row-key="id"
          :selected-keys="selectedIds"
          :row-selection="{ type: 'checkbox', showCheckedAll: true, onlyCurrent: true }"
          :scroll="{ x: 'max-content' }"
          @page-change="handlePageChange"
          @page-size-change="handlePageSizeChange"
          @selection-change="onSelectionChange"
      >
        <template #title="{ record }">
          <a-tooltip :content="record.title">
            <span class="review-title-link" @click="handleView(record)">{{ record.title }}</span>
          </a-tooltip>
        </template>
        <template #moduleName="{ record }">
          <a-tooltip v-if="record.moduleName" :content="record.moduleName">
            <span>{{ record.moduleName }}</span>
          </a-tooltip>
          <span v-else style="color: #86909c">-</span>
        </template>
        <template #caseCount="{ record }">
          {{ record.totalCount ?? record.caseCount ?? (record.cases?.length ?? '-') }}
        </template>
        <template #passRate="{ record }">
          <div class="progress-cell">
            <a-progress
                :percent="passRate(record)"
                :stroke-width="8"
                size="small"
                :show-text="false"
                :color="passRateColor(record)"
                style="flex: 1"
            />
            <span class="progress-text">{{ passRateText(record) }}</span>
          </div>
        </template>
        <template #passRule="{ record }">
          <a-tag :color="record.passRule === 'MAJORITY' ? 'arcoblue' : 'purple'" size="small">
            {{ record.passRule === 'MAJORITY' ? $t('qa.review.passRuleMajority') : $t('qa.review.passRuleAll') }}
          </a-tag>
        </template>
        <template #deadline="{ record }">
          <span v-if="record.deadline">{{ record.deadline }}</span>
          <span v-else style="color: #86909c;">-</span>
        </template>
        <template #status="{ record }">
          <a-tag :color="statusColor(record.status)" size="small">{{ statusText(record.status) }}</a-tag>
        </template>
        <template #creator="{ record }">
          {{ record.creatorName || record.creatorId || '-' }}
        </template>
        <template #actions="{ record }">
          <a-space :size="0">
            <a-button
                v-for="op in visibleOps(record)"
                :key="op.key"
                type="text"
                size="small"
                :status="op.danger ? 'danger' : undefined"
                :disabled="op.disabled"
                @click="op.handler(record)"
            >
              {{ op.label }}
            </a-button>
            <a-dropdown v-if="overflowOps(record).length" trigger="click">
              <a-button type="text" size="small">
                ...
              </a-button>
              <template #content>
                <a-doption
                    v-for="op in overflowOps(record)"
                    :key="op.key"
                    :disabled="op.disabled"
                    @click="guardAndRun(op, record)"
                >
                  <span :style="op.danger ? 'color: rgb(var(--danger-6))' : ''">{{ op.label }}</span>
                </a-doption>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </a-table>
      </div>

      <!-- 批量移动模块弹窗 -->
      <a-modal
        v-model:visible="moveModalVisible"
        :title="$t('qa.review.moveToModule')"
        :width="480"
        :ok-loading="moveLoading"
        @ok="handleBatchMove"
      >
        <div style="padding: 4px 0;">
          <div style="margin-bottom: 4px;">{{ $t('qa.review.targetModule') }} <span style="color: #f53f3f;">*</span></div>
          <a-tree-select
            v-model="moveTargetModuleId"
            :data="moveModuleTree"
            :field-names="{ key: 'id', title: 'moduleName' }"
            :placeholder="$t('qa.review.targetModulePlaceholder')"
            allow-clear
            style="width: 100%; margin-bottom: 12px;"
          />
          <p style="color: var(--color-text-3); font-size: 13px;">
            {{ $t('qa.review.moveTip', { count: selectedIds.length }) }}
          </p>
        </div>
      </a-modal>
            </div>
          </a-card>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, onMounted, onBeforeUnmount, watch} from 'vue';
import {useRouter} from 'vue-router';
import {useI18n} from 'vue-i18n';
import {Message, Modal} from '@arco-design/web-vue';
import { IconPlus, IconSearch, IconDown, IconMore, IconToRight, IconDelete, IconRight } from '@arco-design/web-vue/es/icon';
import {useProjectStore} from '@/store';
import useReviewTodoStore from '@/store/modules/review-todo';
import ModuleTreeManager from '@/views/qa/components/ModuleTreeManager.vue';
import {
  getTestCaseReviewList,
  deleteTestCaseReview,
  batchDeleteTestCaseReview,
  batchMoveTestCaseReview,
  getQaModuleTree,
} from '@/api/MyApi/qa';

// embedded=true 时作为「用例管理 → 用例评审」tab 内嵌使用（无面包屑/独立页边距由父级控制）
withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

const router = useRouter();
const {t} = useI18n();

const projectStore = useProjectStore();
const reviewTodoStore = useReviewTodoStore();

const loading = ref(false);
const reviewList = ref<any[]>([]);

// ===== 左侧栏伸缩（与用例列表页同款 resizer） =====
const windowWidth = ref(window.innerWidth);
const SIDEBAR_DEFAULT_WIDTH = 280;
const SIDEBAR_MIN_WIDTH = 220;
const SIDEBAR_MAX_WIDTH = 480;
const SIDEBAR_COLLAPSE_THRESHOLD = 20;

const sidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);
// 窄屏（<992px）默认折叠侧栏，把空间让给列表
const isSidebarCollapsed = ref(window.innerWidth < 992);
const lastSidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);

// 侧栏最大宽度跟随浏览器窗口：不超过窗口的 40%，避免小屏下树把列表挤没
const sidebarMaxWidth = computed(() =>
  Math.max(SIDEBAR_MIN_WIDTH, Math.min(SIDEBAR_MAX_WIDTH, Math.round(windowWidth.value * 0.4)))
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

const onWindowResize = () => {
  windowWidth.value = window.innerWidth;
};

const searchKeyword = ref('');
const searchStatus = ref('');
const searchModuleId = ref<number | undefined>(undefined);
const selectedModuleKeys = ref<(string | number)[]>([]);
const moduleTreeRef = ref<any>(null);
const pagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });

// 主表列定义（:columns 数组 + slotName 方式）
const columns = computed(() => [
  { title: t('qa.review.code'), dataIndex: 'reviewCode', width: 120, ellipsis: true, tooltip: true },
  { title: t('qa.review.name'), dataIndex: 'title', width: 200, ellipsis: true, tooltip: true, slotName: 'title' },
  { title: t('qa.review.module'), dataIndex: 'moduleName', ellipsis: true, tooltip: true, slotName: 'moduleName' },
  { title: t('qa.review.caseCount'), dataIndex: 'caseCount', width: 90, align: 'center', slotName: 'caseCount' },
  { title: t('qa.review.passRate'), width: 160, slotName: 'passRate' },
  { title: t('qa.review.passRule'), dataIndex: 'passRule', width: 100, align: 'center', slotName: 'passRule' },
  { title: t('qa.review.deadline'), dataIndex: 'deadline', width: 180, ellipsis: true, tooltip: true, slotName: 'deadline' },
  { title: t('qa.testCase.status'), dataIndex: 'status', width: 90, align: 'center', slotName: 'status' },
  { title: t('qa.review.creator'), dataIndex: 'creatorName', width: 100, ellipsis: true, tooltip: true, slotName: 'creator' },
  { title: t('qa.common.operation'), width: 200, align: 'center', fixed: 'right' as const, slotName: 'actions' },
]);

// 批量操作
const selectedIds = ref<number[]>([]);
const onSelectionChange = (keys: (string | number)[]) => {
  selectedIds.value = keys as number[];
};
const moveModalVisible = ref(false);
const moveTargetModuleId = ref<number | undefined>(undefined);
const moveLoading = ref(false);
const moveModuleTree = ref<any[]>([]);

const loadMoveModuleTree = async () => {
  const pid = projectStore.getProjectId as number;
  if (!pid) return;
  try {
    const res: any = await getQaModuleTree(pid, 'REVIEW');
    moveModuleTree.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const handleBatchDelete = () => {
  Modal.warning({
    title: t('qa.review.batchDelete'),
    content: t('qa.review.batchDeleteContent', { count: selectedIds.value.length }),
    okText: t('qa.common.delete'),
    okButtonProps: { status: 'danger' },
    hideCancel: false,
    onOk: async () => {
      try {
        const res: any = await batchDeleteTestCaseReview(selectedIds.value);
        if (res.code === 200) {
          Message.success(res.msg || t('qa.review.batchDeleteSuccess'));
          selectedIds.value = [];
          reviewTodoStore.refresh(true);
          // 后台刷新，不阻塞 UI
          Promise.all([loadData(), moduleTreeRef.value?.loadData()]);
        }
      } catch (e) {
        console.error(e);
      }
    },
  });
};

const handleBatchMove = async () => {
  if (moveTargetModuleId.value === undefined) {
    Message.warning(t('qa.review.selectTargetModule'));
    return;
  }
  moveLoading.value = true;
  try {
    const res: any = await batchMoveTestCaseReview(selectedIds.value, moveTargetModuleId.value);
    if (res.code === 200) {
      Message.success(t('qa.review.moveSuccess', { count: selectedIds.value.length }));
      selectedIds.value = [];
      moveModalVisible.value = false;
      moveTargetModuleId.value = undefined;
      // 后台刷新，不阻塞 UI
      Promise.all([loadData(), moduleTreeRef.value?.loadData()]);
    }
  } catch (e) {
    console.error(e);
  } finally {
    moveLoading.value = false;
  }
};

// 通过率 = 通过用例数 / 总用例数（未出结论的记为未通过）
const passRate = (record: any) => {
  const total = record.caseCount ?? 0;
  return total > 0 ? (record.passCount ?? 0) / total : 0;
};
const passRateText = (record: any) => {
  const total = record.caseCount ?? 0;
  const pass = record.passCount ?? 0;
  return `${Math.round(passRate(record) * 100)}%（${pass}/${total}）`;
};
const passRateColor = (record: any) => {
  const r = passRate(record);
  if (r >= 1) return '#00b42a';
  if (r >= 0.5) return '#ff7d00';
  return '#f53f3f';
};

// 操作列：所有按钮恒可点；不适用场景由后端返回明确错误提示（如"仅创建人可删除"）
interface ReviewOp {
  key: string;
  label: string;
  danger?: boolean;
  disabled?: boolean;
  handler: (record: any) => void;
}
// 操作列：所有按钮恒可点；不适用场景由后端返回明确错误提示（如"仅创建人可删除"）
const opsFor = (record: any): ReviewOp[] => [
  { key: 'enter', label: t('qa.review.op.review'), handler: handleEnter },
  { key: 'edit', label: t('qa.review.op.edit'), handler: handleEdit },
  { key: 'view', label: t('qa.review.op.detail'), handler: handleView },
  { key: 'delete', label: t('qa.common.delete'), danger: true, handler: confirmDelete },
];
// 平铺规则：前2个常显，第3个起进「更多」
const visibleOps = (record: any) => opsFor(record).slice(0, 2);
const overflowOps = (record: any) => opsFor(record).slice(2);

const guardAndRun = (op: ReviewOp, record: any) => {
  if (op.disabled) return;
  op.handler(record);
};

const confirmDelete = (record: any) => {
  Modal.warning({
    title: t('qa.review.deleteTitle'),
    content: t('qa.review.deleteContent', { name: record.title }),
    okText: t('qa.review.confirmDelete'),
    okButtonProps: { status: 'danger' },
    cancelText: t('qa.review.thinkAgain'),
    hideCancel: false,
    onOk: async () => {
      const res: any = await deleteTestCaseReview(record.id);
      if (res.code === 200) {
        Message.success(t('qa.review.deleted'));
        reviewTodoStore.refresh(true);
        await loadData();
      } else {
        Message.error(res.msg || t('qa.review.deleteFailed'));
      }
    },
  });
};

const loadData = async () => {
  const pid = projectStore.getProjectId;
  if (!pid) return;
  loading.value = true;
  try {
    const res: any = await getTestCaseReviewList(
        pid,
        searchStatus.value || undefined,
        searchKeyword.value || undefined,
        pagination.value.current,
        pagination.value.pageSize,
        searchModuleId.value
    );
    reviewList.value = res.data?.records || [];
    pagination.value.total = res.data?.total || 0;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleModuleSelect = (selectedKeys: any[], nodeData: any) => {
  selectedModuleKeys.value = selectedKeys;
  const id = Number(nodeData?.id ?? 0);
  searchModuleId.value = id === 0 ? undefined : id;
  pagination.value.current = 1;
  loadData();
};

const handleSearch = () => {
  pagination.value.current = 1;
  loadData();
};

const handleReset = () => {
  searchKeyword.value = '';
  searchStatus.value = '';
  searchModuleId.value = undefined;
  selectedModuleKeys.value = [];
  pagination.value.current = 1;
  loadData();
};

const handlePageChange = (page: number) => {
  selectedIds.value = [];
  pagination.value.current = page;
  loadData();
};

const handlePageSizeChange = (pageSize: number) => {
  selectedIds.value = [];
  pagination.value.pageSize = pageSize;
  pagination.value.current = 1;
  loadData();
};

// ==================== 新建评审（跳转独立页面） ====================

const handleAdd = () => {
  router.push({ name: 'qaReviewCreate' });
};

const handleEdit = (record: any) => {
  router.push({ name: 'qaReviewEdit', params: { id: record.id } });
};

// ==================== 详情 / 评审 ====================

const handleView = (record: any) => {
  router.push({ name: 'qaReviewDetail', params: { id: record.id } });
};

const handleEnter = (record: any) => {
  router.push({ name: 'qaReviewWorkbench', query: { id: record.id } });
};

// ==================== 字典 ====================

const statusColor = (status: string) => {
  const map: Record<string, string> = { REVIEWING: 'gold', COMPLETED: 'green', CANCELLED: 'gray' };
  return map[status] || 'gray';
};
const statusText = (status: string) => {
  const map: Record<string, string> = { REVIEWING: t('qa.review.status.reviewing'), COMPLETED: t('qa.review.status.completed'), CANCELLED: t('qa.review.status.cancelled') };
  return map[status] || status;
};

onMounted(() => {
  window.addEventListener('resize', onWindowResize);
  loadData();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize);
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
.review-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.review-body {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

/* 左右分栏（与用例列表页同款） */
.main-row {
  display: flex;
  flex: 1;
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
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.pane-card {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.pane-card :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
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

.review-main {
  height: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
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

.batch-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 4px 0;
  margin-bottom: 8px;
}

.review-title-link {
  color: rgb(var(--primary-6));
  cursor: pointer;
}
.review-title-link:hover {
  color: rgb(var(--link-color-hover));
}

.progress-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-text {
  font-size: 12px;
  color: var(--color-text-2);
  white-space: nowrap;
}
</style>
