<template>
  <div class="review-detail-page">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testCase', 'menu.qa.reviewDetail']" />
    <!-- 顶部信息卡片 -->
    <a-card class="header-card">
      <div class="detail-header">
        <a-button size="small" @click="handleBack">
          <template #icon><icon-left /></template>
          {{ $t('qa.review.back') }}
        </a-button>
        <span class="detail-code">{{ detail?.reviewCode || '' }}</span>
        <span class="detail-title">{{ detail?.title || $t('menu.qa.reviewDetail') }}</span>
        <a-tag v-if="detail" :color="statusColor(detail.status)" size="small">{{ statusText(detail.status) }}</a-tag>
        <div class="detail-meta" v-if="detail">
          <span class="detail-meta-item">{{ $t('qa.review.creatorLine', { name: detail.creatorName || '-' }) }}</span>
        </div>
        <a-space class="detail-actions">
          <a-button v-if="detail?.status === 'REVIEWING'" size="small" @click="handleEdit">
            <template #icon><icon-edit /></template>{{ $t('qa.review.op.edit') }}
          </a-button>
          <a-button v-if="canManageCases" type="primary" size="small" @click="openCaseFormDrawer">
            <template #icon><icon-plus /></template>{{ $t('qa.testCase.add') }}
          </a-button>
          <a-button v-if="canManageCases" size="small" @click="caseDrawerVisible = true">
            <template #icon><icon-link /></template>{{ $t('qa.review.linkCases') }}
          </a-button>
        </a-space>
      </div>

      <!-- 评审进度条 -->
      <div v-if="detail && progressStats.total > 0" class="detail-progress">
        <div class="progress-info">
          <span class="progress-label">{{ $t('qa.review.progress') }}</span>
          <span class="progress-text">{{ $t('qa.review.progressText', { finished: progressStats.finished, total: progressStats.total }) }}</span>
          <span class="progress-stat">
            <i class="dot dot-pass" />{{ $t('qa.review.passCount', { count: progressStats.pass }) }}
          </span>
          <span class="progress-stat">
            <i class="dot dot-reject" />{{ $t('qa.review.rejectCount', { count: progressStats.reject }) }}
          </span>
          <span class="progress-stat">
            <i class="dot dot-pending" />{{ $t('qa.review.pendingCount', { count: progressStats.pending }) }}
          </span>
        </div>
        <div class="progress-bar">
          <div class="progress-seg seg-pass" :style="{ width: (progressStats.pass / progressStats.total * 100) + '%' }" />
          <div class="progress-seg seg-reject" :style="{ width: (progressStats.reject / progressStats.total * 100) + '%' }" />
          <div class="progress-seg seg-pending" :style="{ width: (progressStats.pending / progressStats.total * 100) + '%' }" />
        </div>
      </div>

    </a-card>

    <!-- 内容区：左侧模块树卡片 + 右侧用例卡片 -->
    <div class="detail-body" v-loading="loading">
      <div class="main-row">
        <!-- 左侧用例模块树 -->
        <div class="col side-col" :style="sideColStyle">
          <a-card class="pane-card" size="small" :bordered="true">
            <div class="side-tree">
              <a-input-search v-model="moduleKeyword" :placeholder="$t('qa.review.searchModule')" size="small" style="margin-bottom: 8px" />
              <a-scrollbar style="flex: 1; overflow: auto;">
                <a-tree
                  :data="filteredModuleTree"
                  :field-names="{ key: 'id', title: 'moduleName' }"
                  :default-expand-all="true"
                  :selected-keys="selectedModuleKeys"
                  @select="handleModuleSelect"
                >
                  <template #title="nodeData">
                    <span>{{ nodeData.moduleName }}</span>
                    <span v-if="moduleCaseCount(nodeData.id)" class="tree-count">{{ moduleCaseCount(nodeData.id) }}</span>
                  </template>
                </a-tree>
              </a-scrollbar>
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
        <!-- 右侧：工具栏 + 用例表格/脑图 -->
        <div class="col content-col">
          <a-card class="pane-card" :bordered="true">
            <div class="detail-main">
          <div class="main-toolbar">
            <a-input-search
              v-model="caseKeyword"
              :placeholder="$t('qa.review.searchCase')"
              size="small"
              style="width: 240px"
              allow-clear
            />
            <!-- 列表/脑图 视图切换 -->
            <a-radio-group v-model="viewMode" type="button" size="small">
              <a-radio value="list">{{ $t('qa.review.viewList') }}</a-radio>
              <a-radio value="mindmap">{{ $t('qa.review.viewMindmap') }}</a-radio>
            </a-radio-group>
            <span class="case-total">{{ $t('qa.review.totalCases', { count: filteredCases.length }) }}</span>
            <a-dropdown-button
              size="small"
              type="outline"
              :disabled="!reviewSelectedIds.length"
              style="margin-left: auto;"
            >
              {{ $t('qa.review.batchOperation') }}
              <template #icon><icon-more /></template>
              <template #content>
                <a-doption
                  :disabled="!reviewSelectedIds.length"
                  @click="handleBatchRemoveFromReview"
                >
                  <template #icon><icon-delete /></template>
                  <span style="color: #f53f3f;">{{ $t('qa.review.batchRemove') }}</span>
                </a-doption>
              </template>
            </a-dropdown-button>
          </div>
          <div class="main-table" v-if="viewMode === 'list'">
            <a-table
              :data="pagedCases"
              :loading="loading"
              :pagination="false"
              :bordered="{ cell: true }"
              row-key="testCaseId"
              :selected-keys="reviewSelectedIds"
              :row-selection="{ type: 'checkbox', showCheckedAll: true, onlyCurrent: true }"
              size="small"
              :scroll="{ x: 'max-content', y: '100%' }"
              @selection-change="onReviewSelectionChange"
            >
              <template #columns>
                <a-table-column :title="$t('qa.testCase.code')" data-index="caseCode" :width="140" :ellipsis="true" :tooltip="true" />
                <a-table-column :title="$t('qa.testCase.name')" data-index="caseName" :width="200" :ellipsis="true" :tooltip="true" />
                <a-table-column :title="$t('qa.testCase.priority')" data-index="priority" :width="80" align="center">
                  <template #cell="{ record }">
                    <a-tag :color="priorityColor(record.priority)" size="small">{{ record.priority }}</a-tag>
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.review.module')" data-index="moduleName" :width="140" :ellipsis="true" :tooltip="true">
                  <template #cell="{ record }">
                    {{ record.moduleName || '-' }}
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.review.creator')" :width="100" align="center">
                  <template #cell="{ record }">
                    {{ record.caseCreatorName || '-' }}
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.review.reviewers')" :width="220">
                  <template #cell="{ record }">
                    <!-- 行内编辑态：多选下拉，收起即自动保存 -->
                    <div v-if="editingCaseId === Number(record.testCaseId)" class="reviewer-cell">
                      <a-select
                        v-model="reviewerEditIds"
                        multiple
                        size="mini"
                        :placeholder="$t('qa.review.reviewerPlaceholder')"
                        :max-tag-count="2"
                        :loading="savingReviewers"
                        style="flex: 1; min-width: 0"
                        @popup-visible-change="(v) => !v && handleSaveCaseReviewers(record)"
                        @blur="handleSaveCaseReviewers(record)"
                      >
                        <a-option v-for="r in detail?.reviewers || []" :key="r.reviewerId" :value="r.reviewerId">
                          {{ r.reviewerName || `#${r.reviewerId}` }}
                        </a-option>
                      </a-select>
                    </div>
                    <!-- 展示态：评审人标签 + 编辑入口 -->
                    <div v-else class="reviewer-cell">
                      <a-tooltip
                        v-for="item in record.items"
                        :key="item.itemId"
                        :disabled="!item.comment"
                        :content="`${item.reviewerName || '#' + item.reviewerId}：${item.comment || ''}`"
                        position="top"
                      >
                        <a-tag
                          size="small"
                          :color="item.conclusion === 'PASS' ? 'green' : item.conclusion === 'REJECT' ? 'red' : 'gray'"
                        >
                          {{ item.reviewerName || `#${item.reviewerId}` }}
                        </a-tag>
                      </a-tooltip>
                      <a-tooltip v-if="detail?.status === 'REVIEWING' && isCreator" :content="$t('qa.review.adjustReviewers')">
                        <a-button type="text" size="mini" @click="handleStartEditReviewers(record)">
                          <template #icon><icon-edit /></template>
                        </a-button>
                      </a-tooltip>
                    </div>
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.review.result')" :width="100" align="center">
                  <template #cell="{ record }">
                    <a-tag :color="finalResultColor(record.finalResult)" size="small">{{ finalResultText(record.finalResult) }}</a-tag>
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.common.operation')" :width="150" align="center" fixed="right">
                  <template #cell="{ record }">
                    <!-- 所有行固定渲染同一组按钮（不适用则禁用），保证操作列内容宽度一致 -->
                    <a-space :size="4">
                      <a-button
                        type="text"
                        size="mini"
                        :status="detail?.status !== 'CANCELLED' && isCaseReviewer(record) ? 'success' : undefined"
                        @click="handleReview(record)"
                      >
                        {{ detail?.status !== 'CANCELLED' && isCaseReviewer(record) ? $t('qa.review.op.review') : $t('qa.review.op.view') }}
                      </a-button>
                      <a-button
                        type="text"
                        size="mini"
                        status="danger"
                        :disabled="!(['REVIEWING', 'COMPLETED'].includes(detail?.status) && isCreator)"
                        @click="confirmRemoveCase(record)"
                      >
                        {{ $t('qa.review.unlink') }}
                      </a-button>
                    </a-space>
                  </template>
                </a-table-column>
              </template>
            </a-table>
          </div>
          <a-pagination
            v-if="viewMode === 'list'"
            :total="filteredCases.length"
            v-model:current="currentPage"
            :page-size="pageSize"
            size="mini"
            show-total
            show-jumper
            :show-page-size="false"
            @change="(p: number) => currentPage = p"
            style="margin-top: 8px; flex-shrink: 0; justify-content: center;"
          >
            <template #total>{{ $t('qa.review.totalPages', { count: totalPages }) }}</template>
          </a-pagination>
          <!-- 脑图视图：根节点跟随左侧模块选中状态 -->
          <ReviewMindMap
            v-else
            class="mindmap-fill"
            :cases="filteredCases"
            :module-tree="moduleTree"
            :selected-module-id="selectedModuleId"
            :review-status="detail?.status || ''"
            :is-creator="isCreator"
            :review-id="reviewId"
            :reviewers="detail?.reviewers || []"
            :my-id="myId"
            @review="handleReview"
            @remove="confirmRemoveCase"
            @refresh="loadDetail"
          />
            </div>
          </a-card>
        </div>
      </div>
    </div>

    <!-- 关联用例抽屉 -->
    <CaseSelectDrawer
      v-model:visible="caseDrawerVisible"
      :project-id="detail?.projectId"
      :exclude-ids="existingCaseIds"
      @confirm="handleAddCases"
    />

    <!-- 新建用例抽屉 -->
    <a-drawer
      v-model:visible="caseFormDrawerVisible"
      :title="$t('qa.testCase.add')"
      :width="1000"
      :mask-closable="true"
      @cancel="handleCaseDrawerClose"
    >
      <div class="case-form-wrap">
        <TestCaseForm
          :key="caseFormKey"
          ref="caseFormRef"
        />
      </div>
      <template #footer>
        <a-space>
          <a-button @click="handleCaseDrawerClose">{{ $t('qa.common.cancel') }}</a-button>
          <a-button type="primary" :loading="savingCase" @click="handleCreateCase">{{ $t('qa.review.saveAndLink') }}</a-button>
        </a-space>
      </template>
    </a-drawer>
  </div>
</template>

<script lang="ts">
  export default { name: 'qaReviewDetail' };
</script>

<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { Message, Modal } from '@arco-design/web-vue';
import { IconLeft, IconLink, IconPlus, IconEdit, IconMore, IconDelete, IconRight } from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import { useUserStore } from '@/store';
import { confirmIfDirty } from '@/hooks/useDirtyGuard';
import CaseSelectDrawer from './components/CaseSelectDrawer.vue';
import ReviewMindMap from './ReviewMindMap.vue';
import TestCaseForm from '../testCase/components/TestCaseForm.vue';
import {
  getTestCaseReviewDetail,
  removeReviewCase,
  addReviewCases,
  updateReviewCaseReviewers,
  getQaModuleTree,
  batchMoveTestCase,
} from '@/api/MyApi/qa';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const { t } = useI18n();

const reviewId = Number(route.params.id);
const myId = computed(() => Number(userStore.id || (userStore.userInfo as any)?.id));

const loading = ref(false);
const detail = ref<any>(null);
// 列表/脑图 视图切换
const viewMode = ref<'list' | 'mindmap'>('list');

// ===== 左侧栏伸缩（与用例列表页同款 resizer） =====
const windowWidth = ref(window.innerWidth);
const SIDEBAR_DEFAULT_WIDTH = 240;
const SIDEBAR_MIN_WIDTH = 200;
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
const caseDrawerVisible = ref(false);
const caseFormDrawerVisible = ref(false);
const caseFormRef = ref();
const caseFormKey = ref(0);
const savingCase = ref(false);
const existingCaseIds = computed(() => (detail.value?.cases || []).map((c: any) => Number(c.testCaseId)));
const moduleTree = ref<any[]>([]);
const moduleKeyword = ref('');
const caseKeyword = ref('');
const currentPage = ref(1);
const pageSize = 10;
const selectedModuleId = ref<number | undefined>(undefined);

// 批量操作
const reviewSelectedIds = ref<number[]>([]);
const onReviewSelectionChange = (keys: (string | number)[]) => {
  reviewSelectedIds.value = keys as number[];
};

const handleBatchRemoveFromReview = () => {
  Modal.warning({
    title: t('qa.review.batchRemove'),
    content: t('qa.review.batchRemoveContent', { count: reviewSelectedIds.value.length }),
    hideCancel: false,
    okText: t('qa.review.remove'),
    onOk: async () => {
      try {
        for (const testCaseId of reviewSelectedIds.value) {
          await removeReviewCase(reviewId, testCaseId);
        }
        Message.success(t('qa.review.removedCount', { count: reviewSelectedIds.value.length }));
        reviewSelectedIds.value = [];
        await loadDetail();
      } catch (e) {
        console.error(e);
      }
    },
  });
};
const selectedModuleKeys = ref<(string | number)[]>([]);

const caseList = computed<any[]>(() => detail.value?.cases || []);

// 评审进度统计：按用例 finalResult 汇总
const progressStats = computed(() => {
  const list = caseList.value;
  const total = list.length;
  const pass = list.filter((c: any) => c.finalResult === 'PASS').length;
  const reject = list.filter((c: any) => c.finalResult === 'REJECT').length;
  const finished = pass + reject;
  return {
    total,
    finished,
    pass,
    reject,
    pending: total - finished,
  };
});

const isCreator = computed(() => detail.value && Number(detail.value.creatorId) === myId.value);
// 用例圈选管理（新建/关联）：评审中 + 已完结均可（已完结追加会自动重开评审单）
const canManageCases = computed(() =>
  !!detail.value && isCreator.value && ['REVIEWING', 'COMPLETED'].includes(detail.value.status)
);
// 用例级评审人判断：调整过单条用例评审人后，只有该用例在册评审人可进入评审
const isCaseReviewer = (record: any) =>
  (record.items || []).some((i: any) => Number(i.reviewerId) === myId.value);

// 递归收集某模块及其所有子模块的 ID
const collectModuleIds = (nodes: any[], targetId: number): number[] => {
  const result: number[] = [];
  const collectAll = (children: any[]) => {
    for (const n of children) {
      result.push(Number(n.id));
      if (n.children) collectAll(n.children);
    }
  };
  const find = (list: any[]): boolean => {
    for (const n of list) {
      if (Number(n.id) === targetId) {
        result.push(Number(n.id));
        if (n.children) collectAll(n.children);
        return true;
      }
      if (n.children && find(n.children)) return true;
    }
    return false;
  };
  find(nodes);
  return result;
};

const filteredCases = computed(() => {
  let list = caseList.value;
  if (selectedModuleId.value !== undefined) {
    const ids = collectModuleIds(moduleTree.value, selectedModuleId.value);
    list = list.filter((c: any) => ids.includes(Number(c.moduleId)));
  }
  if (caseKeyword.value) {
    const kw = caseKeyword.value.toLowerCase();
    list = list.filter((c: any) =>
      (c.caseCode || '').toLowerCase().includes(kw) ||
      (c.caseName || '').toLowerCase().includes(kw)
    );
  }
  return list;
});

const pagedCases = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredCases.value.slice(start, start + pageSize);
});

const totalPages = computed(() => Math.ceil(filteredCases.value.length / pageSize) || 1);

watch([caseKeyword, selectedModuleId], () => {
  currentPage.value = 1;
});

// 每个模块的评审用例总数（含子模块聚合），用于隐藏没有评审用例的模块分支
const moduleTotalCountMap = computed(() => {
  const map = new Map<number, number>();
  const walk = (nodes: any[]): number => {
    let total = 0;
    for (const n of nodes) {
      const own = caseList.value.filter((c: any) => Number(c.moduleId) === Number(n.id)).length;
      const sub = walk(n.children || []);
      map.set(Number(n.id), own + sub);
      total += own + sub;
    }
    return total;
  };
  walk(moduleTree.value);
  return map;
});

// 过滤模块树（隐藏无评审用例的模块 + 按关键词搜索）
const filteredModuleTree = computed(() => {
  const kw = moduleKeyword.value.toLowerCase();
  const filterNodes = (nodes: any[]): any[] => {
    const result: any[] = [];
    for (const n of nodes) {
      // 无评审用例（含子模块聚合为 0）的模块不显示
      if ((moduleTotalCountMap.value.get(Number(n.id)) || 0) <= 0) continue;
      const children = n.children ? filterNodes(n.children) : [];
      if (!kw || (n.moduleName || '').toLowerCase().includes(kw) || children.length > 0) {
        result.push({ ...n, children });
      }
    }
    return result;
  };
  return filterNodes(moduleTree.value);
});

// 计算每个模块下参与评审的用例数（含子模块聚合）
const moduleCaseCount = (moduleId: number) => {
  const count = moduleTotalCountMap.value.get(Number(moduleId)) || 0;
  return count || '';
};

const loadDetail = async () => {
  if (!reviewId) return;
  loading.value = true;
  try {
    const res: any = await getTestCaseReviewDetail(reviewId);
    if (res.code === 200) {
      detail.value = res.data;
      loadModuleTree();
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const loadModuleTree = async () => {
  const pid = detail.value?.projectId;
  if (!pid) return;
  try {
    const res: any = await getQaModuleTree(pid, 'CASE');
    moduleTree.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const handleModuleSelect = (selectedKeys: (string | number)[]) => {
  if (selectedKeys.length > 0) {
    selectedModuleId.value = Number(selectedKeys[0]);
    selectedModuleKeys.value = selectedKeys;
  } else {
    selectedModuleId.value = undefined;
    selectedModuleKeys.value = [];
  }
};

const handleReview = (record: any) => {
  router.push({
    name: 'qaReviewWorkbench',
    query: { id: reviewId, caseId: record.testCaseId },
  });
};

const confirmRemoveCase = (record: any) => {
  if (!(['REVIEWING', 'COMPLETED'].includes(detail.value?.status) && isCreator.value)) return;
  Modal.warning({
    title: t('qa.review.unlink'),
    content: t('qa.review.unlinkContent'),
    okText: t('qa.review.confirmRemove'),
    cancelText: t('qa.review.thinkAgain'),
    hideCancel: false,
    onOk: () => handleRemoveCase(record),
  });
};

const handleRemoveCase = async (record: any) => {
  try {
    const res: any = await removeReviewCase(reviewId, Number(record.testCaseId));
    if (res.code === 200) {
      Message.success(t('qa.review.removed'));
      await loadDetail();
    } else {
      Message.error(res.msg || t('qa.review.removeFailed'));
    }
  } catch (e) {
    console.error(e);
  }
};

const handleAddCases = async (cases: any[]) => {
  if (!cases.length) return;
  const caseIds = cases.map((c: any) => Number(c.id));
  try {
    const res: any = await addReviewCases(reviewId, caseIds);
    if (res.code === 200) {
      Message.success(t('qa.review.appended', { count: caseIds.length }));
      await loadDetail();
    } else {
      Message.error(res.msg || t('qa.review.appendFailed'));
    }
  } catch (e) {
    console.error(e);
  }
};

const openCaseFormDrawer = () => {
  caseFormKey.value++;
  caseFormDrawerVisible.value = true;
};

const handleCreateCase = async () => {
  savingCase.value = true;
  try {
    const caseId = await caseFormRef.value?.save();
    if (caseId) {
      const res: any = await addReviewCases(reviewId, [Number(caseId)]);
      if (res.code === 200) {
        Message.success(t('qa.review.linkedSuccess'));
        caseFormDrawerVisible.value = false;
        await loadDetail();
      } else {
        Message.error(res.msg || t('qa.review.linkFailed'));
      }
    }
  } catch (e) {
    console.error(e);
  } finally {
    savingCase.value = false;
  }
};

const handleCaseDrawerClose = () => {
  confirmIfDirty(!!caseFormRef.value?.isDirty?.(), () => { caseFormDrawerVisible.value = false; });
};

const handleBack = () => {
  router.push({ name: 'TestCase', query: { tab: 'review' } });
};

const handleEdit = () => {
  router.push({ name: 'qaReviewEdit', params: { id: reviewId } });
};

// 行内编辑单条用例的评审人（失焦/下拉收起自动保存）
const editingCaseId = ref<number | null>(null);
const reviewerEditIds = ref<number[]>([]);
const reviewerEditOrigin = ref<number[]>([]);
const savingReviewers = ref(false);

const handleStartEditReviewers = (record: any) => {
  editingCaseId.value = Number(record.testCaseId);
  reviewerEditIds.value = (record.items || []).map((i: any) => Number(i.reviewerId));
  reviewerEditOrigin.value = [...reviewerEditIds.value];
};

const handleSaveCaseReviewers = async (record: any) => {
  if (savingReviewers.value || editingCaseId.value !== Number(record.testCaseId)) return;
  // 未变更直接退出编辑态，不发请求
  const same = reviewerEditIds.value.length === reviewerEditOrigin.value.length
    && reviewerEditIds.value.every((id) => reviewerEditOrigin.value.includes(id));
  if (same) {
    editingCaseId.value = null;
    return;
  }
  // 允许清空：后端会自动回退为评审单的默认评审人
  savingReviewers.value = true;
  try {
    const res: any = await updateReviewCaseReviewers(reviewId, Number(record.testCaseId), reviewerEditIds.value);
    if (res.code === 200) {
      Message.success(t('qa.review.reviewersAdjusted'));
      editingCaseId.value = null;
      await loadDetail();
    } else {
      Message.error(res.msg || t('qa.review.adjustFailed'));
    }
  } catch (e) {
    console.error(e);
  } finally {
    savingReviewers.value = false;
  }
};

const statusColor = (s: string) => ({ REVIEWING: 'gold', COMPLETED: 'green', CANCELLED: 'gray' } as Record<string, string>)[s] || 'gray';
const statusText = (s: string) => ({ REVIEWING: t('qa.review.status.reviewing'), COMPLETED: t('qa.review.status.completed'), CANCELLED: t('qa.review.status.cancelled') } as Record<string, string>)[s] || s;
const priorityColor = (p: string) => ({ P0: 'red', P1: 'orange', P2: 'blue' } as Record<string, string>)[p] || 'gray';
const finalResultColor = (r: string) => ({ PASS: 'green', REJECT: 'red', PENDING: 'gray' } as Record<string, string>)[r] || 'gray';
const finalResultText = (r: string) => ({ PASS: t('qa.review.resultPass'), REJECT: t('qa.review.resultReject'), PENDING: t('qa.review.resultPending') } as Record<string, string>)[r] || t('qa.review.resultPending');

const onWindowResize = () => {
  windowWidth.value = window.innerWidth;
};

onMounted(() => {
  window.addEventListener('resize', onWindowResize);
  loadDetail();
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', onWindowResize);
});
</script>

<style scoped lang="less">
.review-detail-page {
  padding: 12px 20px 16px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

/* 顶部信息卡片 */
.header-card {
  flex-shrink: 0;
  margin-bottom: 12px;
}

.header-card :deep(.arco-card-body) {
  padding-top: 12px;
  padding-bottom: 12px;
}

/* 内容区（左右独立卡片） */
.detail-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
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

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.detail-code {
  color: var(--color-text-2);
  font-size: 13px;
}

.detail-title {
  font-weight: 600;
  font-size: 16px;
}

.detail-meta {
  display: flex;
  gap: 16px;
  color: var(--color-text-2);
  font-size: 13px;
  margin-left: auto;
}

.detail-actions {
  margin-left: 12px;
}

.detail-progress {
  margin-bottom: 8px;
  max-width: 560px;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
  font-size: 13px;
  flex-wrap: wrap;
}

.progress-label {
  font-weight: 600;
}

.progress-text {
  color: var(--color-text-2);
}

.progress-stat {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-2);
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.dot-pass { background: #00b42a; }
.dot-reject { background: #f53f3f; }
.dot-pending { background: #c9cdd4; }

.progress-bar {
  height: 8px;
  border-radius: 4px;
  background: var(--color-fill-3);
  display: flex;
  overflow: hidden;
}
.progress-seg {
  height: 100%;
  transition: width 0.3s ease;
}
.seg-pass { background: #00b42a; }
.seg-reject { background: #f53f3f; }
.seg-pending { background: #c9cdd4; }

.mindmap-fill {
  flex: 1;
  min-height: 0;
}

.side-tree {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.tree-count {
  margin-left: 4px;
  font-size: 12px;
  color: var(--color-text-3);
  background: var(--color-fill-3);
  border-radius: 8px;
  padding: 0 6px;
}

.detail-main {
  height: 100%;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  padding-left: 6px;
}

.main-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
  flex-shrink: 0;
}

.case-total {
  margin-left: auto;
  font-size: 13px;
  color: var(--color-text-2);
}

.reviewer-cell {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}

.main-table {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.case-form-wrap {
  height: calc(100vh - 130px);
  overflow: hidden;
}
</style>
