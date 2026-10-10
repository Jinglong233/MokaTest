<template>
  <div class="bug-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.bug']" />
    <a-card class="bug-card general-card" :bordered="false">
      <!-- 筛选区 -->
      <a-row :wrap="true" :gutter="[16, 12]" align="center" class="filter-grid" style="margin: 8px 0 16px;">
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">{{ $t('qa.bug.bugName') }}</label>
            <a-input v-model="searchKeyword" :placeholder="$t('qa.bug.name')" allow-clear />
          </div>
        </a-col>
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">{{ $t('qa.bug.severity') }}</label>
            <a-select v-model="searchSeverity" :placeholder="$t('qa.bug.pleaseSelect')" allow-clear>
              <a-option value="FATAL">{{ $t('qa.bug.severity.fatal') }}</a-option>
              <a-option value="SERIOUS">{{ $t('qa.bug.severity.serious') }}</a-option>
              <a-option value="NORMAL">{{ $t('qa.bug.severity.normal') }}</a-option>
              <a-option value="TIPS">{{ $t('qa.bug.severity.tips') }}</a-option>
            </a-select>
          </div>
        </a-col>
        <a-col flex="auto">
          <div class="filter-actions">
            <a-button type="primary" :loading="searchLoading" @click="handleSearchWithLoading">{{ $t('qa.common.search') }}</a-button>
            <a-button @click="handleReset">{{ $t('qa.common.reset') }}</a-button>
          </div>
        </a-col>
      </a-row>
      <!-- 操作行 -->
      <a-row :wrap="true" :gutter="[16, 12]" align="center" style="margin-bottom: 16px;">
        <a-col flex="auto">
          <div class="filter-actions">
            <a-button
              v-permission="QA_BUG_CREATE"
              type="primary"
              class="add-btn"
              @click="handleAdd"
            >
              <template #icon><IconPlus /></template>
              {{ $t('qa.bug.add') }}
            </a-button>
            <a-dropdown-button
              v-permission="QA_BUG_DELETE"
              type="outline"
              :disabled="!selectedKeys.length"
            >
              {{ $t('qa.bug.batchOperation') }}
              <template #icon><IconMore /></template>
              <template #content>
                <a-doption
                  :disabled="!selectedKeys.length"
                  @click="handleBatchDelete"
                >
                  <template #icon><IconDelete /></template>
                  <span style="color: #f53f3f">{{ $t('qa.bug.batchDelete') }}</span>
                </a-doption>
              </template>
            </a-dropdown-button>
          </div>
        </a-col>
      </a-row>

      <a-table
        :data="bugList"
        :columns="columns"
        :loading="loading"
        :pagination="pagination"
        :scroll="{
          x: '125%',
          y: '100%',
        }"
        row-key="id"
        :sticky-header="bugList.length > 50"
        :selected-keys="selectedKeys"
        :row-selection="{
          type: 'checkbox',
          showCheckedAll: true,
          onlyCurrent: true,
        }"
        @selection-change="handleSelectChange"
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #bugCode="{ record }">
          <span class="bug-code-text">{{ record.bugCode }}</span>
        </template>
        <template #title="{ record }">
          <span class="bug-title-link" @click="handlePreview(record)">{{
            record.title
          }}</span>
        </template>
        <template #status="{ record }">
          <a-tag
            :color="bugStatusColor(record.status)"
            size="small"
            class="status-pill"
          >
            {{ bugStatusText(record.status) }}
          </a-tag>
        </template>
        <template #assigneeName="{ record }">
          {{ record.assigneeName || '-' }}
        </template>
        <template #tags="{ record }">
          <span v-if="record.tags">{{
            typeof record.tags === 'string'
              ? record.tags
              : record.tags.join(', ')
          }}</span>
          <span v-else style="color: #86909c">-</span>
        </template>
        <template #createUserName="{ record }">
          {{ record.createUserName || '-' }}
        </template>
        <template #createTime="{ record }">
          {{ formatTime(record.createTime) }}
        </template>
        <template #updateUserName="{ record }">
          {{ record.updateUserName || '-' }}
        </template>
        <template #updateTime="{ record }">
          {{ formatTime(record.updateTime) }}
        </template>
        <template #severity="{ record }">
          <span
            class="severity-badge"
            :class="record.severity?.toLowerCase()"
          >
            {{ severityText(record.severity) }}
          </span>
        </template>
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
              <a-button type="text" size="small">
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
        <template #empty>
          <a-empty :description="$t('qa.bug.empty')" />
        </template>
      </a-table>
    </a-card>

    <!-- 状态流转弹窗 -->
    <a-modal
      v-model:visible="transitionModalVisible"
      :title="$t('qa.bug.transition')"
      width="400px"
      @ok="handleConfirmTransition"
      @cancel="transitionModalVisible = false"
      :mask-closable="false"
    >
      <a-form :model="transitionForm" layout="vertical">
        <a-form-item :label="$t('qa.bug.currentStatus')">
          <a-tag :color="bugStatusColor(transitionForm.currentStatus)">{{
            bugStatusText(transitionForm.currentStatus)
          }}</a-tag>
        </a-form-item>
        <a-form-item :label="$t('qa.bug.targetStatus')" required>
          <a-select
            v-model="transitionForm.targetStatus"
            :placeholder="$t('qa.bug.selectTargetStatus')"
          >
            <a-option value="NEW">{{ $t('qa.bug.status.new') }}</a-option>
            <a-option value="CONFIRMED">{{ $t('qa.bug.status.confirmed') }}</a-option>
            <a-option value="FIXING">{{ $t('qa.bug.status.fixing') }}</a-option>
            <a-option value="FIXED">{{ $t('qa.bug.status.fixed') }}</a-option>
            <a-option value="VERIFIED">{{ $t('qa.bug.status.verified') }}</a-option>
            <a-option value="CLOSED">{{ $t('qa.bug.status.closed') }}</a-option>
            <a-option value="REJECTED">{{ $t('qa.bug.status.rejected') }}</a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新增/编辑弹窗（仅预览抽屉中使用） -->
    <a-modal
      class="qa-edit-modal"
      v-model:visible="modalVisible"
      :title="modalTitle"
      :width="modalWidth"
      @ok="handleSave"
      @cancel="handleModalCancel"
      :mask-closable="false"
    >
      <div class="modal-scroll-body">
        <BugForm ref="bugFormRef" :initial-data="formData" />
      </div>
    </a-modal>

    <!-- BUG 详情抽屉 -->
    <BugDetailDrawer
      v-model:visible="detailDrawerVisible"
      :bug-id="detailBugId"
      :active-tab="activeDetailTab"
      :highlight-comment-id="highlightCommentId"
      @refresh="loadData"
    />

    <!-- 需求预览抽屉（二级） -->
    <a-drawer
      v-model:visible="requirementPreviewVisible"
      :title="requirementPreviewData.title"
      width="520px"
      :mask-closable="true"
      :footer="false"
    >
      <a-descriptions
        :data="[
          { label: $t('qa.requirement.code'), value: requirementPreviewData.reqCode },
          { label: $t('qa.bug.reqType'), value: requirementPreviewData.reqType },
          { label: $t('qa.requirement.priority'), value: requirementPreviewData.priority },
          { label: $t('qa.requirement.status'), value: requirementPreviewData.status },
          {
            label: $t('qa.bug.belongModule'),
            value: requirementPreviewData.moduleName || '-',
          },
          { label: $t('qa.bug.source'), value: requirementPreviewData.source },
          { label: $t('qa.requirement.owner'), value: requirementPreviewData.ownerName || '-' },
          {
            label: $t('qa.bug.createUser'),
            value: requirementPreviewData.createUserName || '-',
          },
          { label: $t('qa.bug.createTime'), value: requirementPreviewData.createTime },
        ]"
        layout="inline-vertical"
        :column="2"
        :label-style="{ fontWeight: 'bold' }"
      />
      <a-divider />
      <h4 style="margin-bottom: 8px">{{ $t('qa.requirement.description') }}</h4>
      <div class="preview-desc-box">
        <div
          class="rich-text-preview"
          v-html="renderHtml(requirementPreviewData.description, $t('qa.bug.noDescription'))"
        />
      </div>
    </a-drawer>

    <!-- 用例预览抽屉 -->
    <a-drawer
      v-model:visible="casePreviewVisible"
      :title="casePreviewData.caseName"
      width="600px"
      :mask-closable="true"
      :footer="false"
    >
      <a-descriptions
        :data="[
          { label: $t('qa.testCase.code'), value: casePreviewData.caseCode },
          { label: $t('qa.testCase.type'), value: casePreviewData.caseType },
          { label: $t('qa.testCase.priority'), value: casePreviewData.priority },
          { label: $t('qa.testCase.status'), value: casePreviewData.status },
          { label: $t('qa.bug.createUser'), value: casePreviewData.createUserName || '-' },
          { label: $t('qa.bug.createTime'), value: casePreviewData.createTime },
        ]"
        layout="inline-vertical"
        :column="2"
        :label-style="{ fontWeight: 'bold' }"
      />
      <a-divider />
      <h4 style="margin-bottom: 8px">{{ $t('qa.testCase.preCondition') }}</h4>
      <div class="preview-desc-box">
        <div
          class="rich-text-preview"
          v-html="renderHtml(casePreviewData.preCondition, $t('qa.bug.none'))"
        />
      </div>
      <a-divider />
      <h4 style="margin-bottom: 8px">{{ $t('qa.testCase.steps') }}</h4>
      <div v-if="casePreviewData.testSteps && casePreviewData.testSteps.length">
        <div
          v-for="(step, index) in casePreviewData.testSteps"
          :key="index"
          style="margin-bottom: 12px"
        >
          <div style="font-weight: bold; color: #86909c; margin-bottom: 4px"
            >{{ $t('qa.testCase.step') }} {{ index + 1 }}</div
          >
          <div class="preview-desc-box">
            <div
              class="rich-text-preview"
              v-html="renderHtml(step.step, '-')"
            />
          </div>
          <div style="color: #86909c; margin-top: 4px">{{ $t('qa.bug.expectedResultLabel') }}</div>
          <div class="preview-desc-box">
            <div
              class="rich-text-preview"
              v-html="renderHtml(step.expected, '-')"
            />
          </div>
        </div>
      </div>
      <a-empty v-else :description="$t('qa.bug.noSteps')" />
    </a-drawer>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'Bug' };
</script>

<script setup lang="ts">
  import {
    ref,
    computed,
    onMounted,
    onBeforeUnmount,
    watch,
    nextTick,
  } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { useRouter } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';
  import {
    IconSearch,
    IconPlus,
    IconDelete,
    IconSwap,
    IconEdit,
    IconCopy,
    IconBug,
    IconExclamationCircle,
    IconCloseCircle,
    IconClockCircle,
    IconClose,
    IconCalendar,
    IconOrderedList,
    IconFile,
    IconLink,
    IconMore,
    IconDown,
  } from '@arco-design/web-vue/es/icon';
  import Breadcrumb from '@/components/breadcrumb/index.vue';
  import RichEditor from '@/components/rich-editor/index.vue';
  import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
  import BugForm from './components/BugForm.vue';
  import BugDetailDrawer from './components/BugDetailDrawer.vue';
  import { useProjectStore } from '@/store';
  import useProjectConfigStore from '@/store/modules/projectConfig';
  import { useRoute } from 'vue-router';
  import {
    QA_BUG_CREATE,
    QA_BUG_UPDATE,
    QA_BUG_DELETE,
    QA_BUG_TRANSITION,
    QA_BUG_COMMENT_CREATE,
  } from '@/constants/permissions';
  import {
    getBugList,
    getBugStats,
    deleteBug,
    batchDeleteBug,
    transitionBugStatus,
    getTestCaseList,
    getRequirementList,
    getRequirementDetail,
    getTestCaseDetail,
    getQaModuleList,
    getBugCommentList,
    saveBugComment,
    getBugOperationLogList,
    getBugDetail,
    updateBug,
    saveBug,
  } from '@/api/MyApi/qa';
  import { useDirtyGuard, confirmIfDirty } from '@/hooks/useDirtyGuard';

  const { t } = useI18n();
  const projectStore = useProjectStore();
  const projectConfigStore = useProjectConfigStore();
  const fieldVis = (key: string) =>
    projectConfigStore.isFieldVisible('bug', key);
  const router = useRouter();
  const route = useRoute();

  // 用于渲染 dropdownRender 插槽中的 VNode
  const VNodeRenderer = {
    props: ['vnodes'],
    render(this: { vnodes: any }) {
      return this.vnodes;
    },
  };

  const loading = ref(false);
  const bugList = ref<any[]>([]);
  const searchKeyword = ref('');
  const searchStatus = ref<string | undefined>(undefined);
  const searchSeverity = ref<string | undefined>(undefined);
  const searchPriority = ref<string | undefined>(undefined);
  const searchRequirementId = ref<number | undefined>(undefined);
  const searchCaseId = ref<number | undefined>(undefined);
  const searchModuleId = ref<string | undefined>(undefined);
  const searchEnvironment = ref<string | undefined>(undefined);
  const moduleOptions = ref<any[]>([]);
  const searchReproduceRate = ref<string | undefined>(undefined);
  const requirementOptions = ref<any[]>([]);
  const filterCaseOptions = ref<any[]>([]);
  const pagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: true,
    showPageSize: true,
    pageSizeOptions: [10, 20, 50, 100],
  });

  const modalVisible = ref(false);

  // 编辑弹窗宽度跟随浏览器窗口自适应：最宽 1200px，窄屏留 3vw 边距
  const windowWidth = ref(window.innerWidth);
  const handleWindowResize = () => {
    windowWidth.value = window.innerWidth;
  };
  const modalWidth = computed(() =>
    Math.min(1200, Math.max(360, windowWidth.value * 0.94))
  );
  onMounted(() => window.addEventListener('resize', handleWindowResize));
  onBeforeUnmount(() =>
    window.removeEventListener('resize', handleWindowResize)
  );
  const modalTitle = ref('');
  const formData = ref<any>({});
  const bugFormRef = ref<any>(null);
  const selectedKeys = ref<number[]>([]);
  const detailDrawerVisible = ref(false);
  const detailBugId = ref<number | null>(null);
  const activeDetailTab = ref<string>('');
  const highlightCommentId = ref<number | undefined>(undefined);
  const casePreviewVisible = ref(false);
  const casePreviewData = ref<any>({});

  // 预览抽屉状态
  const requirementPreviewVisible = ref(false);
  const requirementPreviewData = ref<any>({});
  const filterRequirementOptions = ref<any[]>([]);
  const filterRequirementPage = ref({
    current: 1,
    pageSize: 20,
    hasMore: true,
    keyword: '',
    loading: false,
  });

  const filterCasePage = ref({
    current: 1,
    pageSize: 20,
    hasMore: true,
    keyword: '',
    loading: false,
  });

  const transitionModalVisible = ref(false);
  const transitionForm = ref<any>({});

  const bugStats = ref({ total: 0, open: 0, serious: 0, fatal: 0 });

  // 新增：搜索按钮 loading、行内标题编辑
  const searchLoading = ref(false);
  const editingTitleId = ref<number | null>(null);
  const editingTitleValue = ref('');
  const titleInputRef = ref<any>(null);

  const loadData = async () => {
    if (!projectStore.getProjectId) return;
    loading.value = true;
    try {
      const res: any = await getBugList(
        projectStore.getProjectId,
        searchKeyword.value || undefined,
        searchStatus.value,
        searchSeverity.value,
        searchPriority.value,
        searchRequirementId.value,
        searchCaseId.value,
        searchModuleId.value ? Number(searchModuleId.value) : undefined,
        searchEnvironment.value,
        searchReproduceRate.value,
        undefined, // closeReason
        pagination.value.current,
        pagination.value.pageSize
      );
      bugList.value = res.data.records || [];
      pagination.value.total = res.data.total || 0;
      loadBugStats();
      // 消息通知跳转：高亮并打开指定 BUG 预览
      await checkHighlightBug();
      // 报告等页面跳转：?id= 打开对应缺陷详情抽屉，已删除则提示
      await checkQueryBug();
    } catch (e) {
      console.error(e);
    } finally {
      loading.value = false;
    }
  };

  const handlePageChange = (current: number) => {
    pagination.value.current = current;
    loadData();
  };

  const handlePageSizeChange = (pageSize: number) => {
    pagination.value.pageSize = pageSize;
    pagination.value.current = 1;
    loadData();
  };

  const handleSearchWithLoading = async () => {
    searchLoading.value = true;
    pagination.value.current = 1;
    await loadData();
    searchLoading.value = false;
  };

  const handleSearch = () => {
    pagination.value.current = 1;
    loadData();
  };

  const handleReset = () => {
    searchKeyword.value = '';
    searchStatus.value = undefined;
    searchSeverity.value = undefined;
    searchPriority.value = undefined;
    searchRequirementId.value = undefined;
    searchCaseId.value = undefined;
    searchModuleId.value = undefined;
    searchEnvironment.value = undefined;
    searchReproduceRate.value = undefined;
    filterRequirementOptions.value = [];
    filterCaseOptions.value = [];
    pagination.value.current = 1;
    loadData();
  };

  const handleFilterByOpen = () => {
    searchStatus.value = undefined;
    searchSeverity.value = undefined;
    searchPriority.value = undefined;
    handleSearchWithLoading();
  };

  const handleFilterBySerious = () => {
    searchSeverity.value = 'SERIOUS';
    handleSearchWithLoading();
  };

  const handleFilterByFatal = () => {
    searchSeverity.value = 'FATAL';
    handleSearchWithLoading();
  };

  const copyToClipboard = async (text: string) => {
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      Message.success(t('qa.bug.copySuccess'));
    } catch (e) {
      // 降级方案
      const textarea = document.createElement('textarea');
      textarea.value = text;
      textarea.style.position = 'fixed';
      textarea.style.opacity = '0';
      document.body.appendChild(textarea);
      textarea.select();
      try {
        document.execCommand('copy');
        Message.success(t('qa.bug.copySuccess'));
      } catch (err) {
        Message.error(t('qa.bug.copyFail'));
      }
      document.body.removeChild(textarea);
    }
  };

  const startEditTitle = (record: any) => {
    editingTitleId.value = record.id;
    editingTitleValue.value = record.title || '';
    nextTick(() => {
      titleInputRef.value?.focus?.();
    });
  };

  const confirmTitleRename = async (record: any) => {
    const newTitle = editingTitleValue.value.trim();
    if (!newTitle) {
      Message.warning(t('qa.bug.titleEmpty'));
      return;
    }
    if (newTitle === record.title) {
      editingTitleId.value = null;
      return;
    }
    try {
      // 先取完整对象再更新，避免后端覆盖其他字段
      const detailRes: any = await getBugDetail(record.id);
      if (detailRes.code !== 200 || !detailRes.data) {
        Message.error(t('qa.bug.getDetailFail'));
        return;
      }
      const fullData = detailRes.data;
      fullData.title = newTitle;
      const res: any = await updateBug(fullData);
      if (res.code === 200) {
        Message.success(t('qa.bug.modifySuccess'));
        editingTitleId.value = null;
        await loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const cancelTitleRename = () => {
    editingTitleId.value = null;
    editingTitleValue.value = '';
  };

  const handleInlineTransition = async (record: any, targetStatus: string) => {
    if (record.status === targetStatus) return;
    try {
      const res: any = await transitionBugStatus(record.id, targetStatus);
      if (res.code === 200) {
        Message.success(t('qa.bug.statusUpdated'));
        await loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const loadModuleOptions = async () => {
    if (!projectStore.getProjectId) return;
    try {
      const res: any = await getQaModuleList(projectStore.getProjectId);
      moduleOptions.value = res.data || [];
    } catch (e) {
      console.error(e);
    }
  };

  const handlePreview = (record: any, tab?: string, commentId?: number) => {
    detailBugId.value = record.id;
    activeDetailTab.value = tab || '';
    highlightCommentId.value = commentId;
    detailDrawerVisible.value = true;
  };

  /**
   * 报告等外部页面跳转：?id= 打开缺陷详情抽屉（源数据已删除则提示）
   */
  const checkQueryBug = async () => {
    const bugId = Number(route.query.id);
    if (!bugId || isNaN(bugId)) return;
    try {
      const res: any = await getBugDetail(bugId);
      if (res.code === 200 && res.data) {
        detailBugId.value = bugId;
        activeDetailTab.value = '';
        detailDrawerVisible.value = true;
      } else {
        Message.warning(t('qa.bug.notFound'));
      }
    } catch (e) {
      Message.warning(t('qa.bug.notFound'));
    }
  };

  /**
   * 检查是否有消息通知跳转过来的高亮 BUG
   */
  const checkHighlightBug = async () => {
    const highlightId = route.query.highlight;
    if (!highlightId) return;
    const bugId = Number(highlightId);
    if (isNaN(bugId)) return;

    const tab = route.query.detailTab as string | undefined;
    const commentId = route.query.commentId
      ? Number(route.query.commentId)
      : undefined;
    const record = bugList.value.find((item) => item.id === bugId);
    if (record) {
      handlePreview(record, tab, commentId);
    } else {
      // 当前页没有，尝试查详情打开预览
      try {
        const res: any = await getBugDetail(bugId);
        if (res.code === 200 && res.data) {
          handlePreview(res.data, tab, commentId);
        }
      } catch (e) {
        console.error('加载高亮BUG失败', e);
      }
    }
  };

  // 路由 query 变化时重新检查高亮（同页面消息跳转）
  watch(() => route.query.highlight, (val) => {
    if (val) checkHighlightBug();
  });

  const handlePreviewCase = async (caseId: number) => {
    if (!caseId) return;
    try {
      const res: any = await getTestCaseDetail(caseId);
      if (res.code === 200 && res.data) {
        casePreviewData.value = res.data;
        casePreviewVisible.value = true;
      }
    } catch (e) {
      console.error(e);
    }
  };

  const openRequirementDrawer = async (reqId: number) => {
    if (!reqId) return;
    try {
      const res: any = await getRequirementDetail(reqId);
      if (res.code === 200 && res.data) {
        requirementPreviewData.value = res.data;
        requirementPreviewVisible.value = true;
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleAdd = () => {
    router.push({ name: 'BugEdit' });
  };

  const handleEdit = (record: any) => {
    router.push({ name: 'BugEdit', params: { id: record.id } });
  };

  const handleCopy = async (record: any) => {
    try {
      const res: any = await getBugDetail(record.id);
      if (res.code !== 200 || !res.data) {
        Message.error(t('qa.bug.getDetailFail'));
        return;
      }
      const src = res.data;
      const copy: any = {
        ...src,
        id: undefined,
        bugCode: undefined,
        title: t('qa.bug.copyTitle', { title: src.title }),
        status: 'NEW',
        closeReason: undefined,
        createTime: undefined,
        updateTime: undefined,
        createUserId: undefined,
        updateUserId: undefined,
        projectId: projectStore.getProjectId,
      };
      const saveRes: any = await saveBug(copy);
      if (saveRes.code === 200) {
        Message.success(t('qa.bug.copyBugSuccess'));
        loadData();
      } else {
        Message.error(saveRes.msg || t('qa.bug.copyFail'));
      }
    } catch (e) {
      console.error(e);
    }
  };

  // 预览抽屉里继续用弹窗编辑
  const handleEditInModal = (record: any) => {
    modalTitle.value = t('qa.bug.edit');
    formData.value = { ...record };
    if (formData.value.tags && typeof formData.value.tags === 'string') {
      formData.value.tags = formData.value.tags
        .split(',')
        .filter((t: string) => t.trim());
    }
    modalVisible.value = true;
  };

  // ========== 筛选栏下拉滚动加载 ==========

  const loadFilterRequirementOptions = async (
    keyword?: string,
    isLoadMore = false
  ) => {
    if (!projectStore.getProjectId) return;
    if (!isLoadMore) {
      filterRequirementPage.value.current = 1;
      filterRequirementPage.value.keyword = keyword || '';
      filterRequirementOptions.value = [];
    }
    try {
      const res: any = await getRequirementList(
        projectStore.getProjectId,
        filterRequirementPage.value.keyword || undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        filterRequirementPage.value.current,
        filterRequirementPage.value.pageSize
      );
      const records = res.data?.records || [];
      if (isLoadMore) {
        filterRequirementOptions.value.push(...records);
      } else {
        filterRequirementOptions.value = records;
      }
      filterRequirementPage.value.hasMore =
        records.length >= filterRequirementPage.value.pageSize;
    } catch (e) {
      console.error(e);
    }
  };

  const loadMoreFilterRequirement = async () => {
    if (
      !filterRequirementPage.value.hasMore ||
      filterRequirementPage.value.loading
    )
      return;
    filterRequirementPage.value.loading = true;
    try {
      filterRequirementPage.value.current++;
      await loadFilterRequirementOptions(undefined, true);
    } finally {
      filterRequirementPage.value.loading = false;
    }
  };

  const handleSearchFilterRequirement = (keyword: string) => {
    loadFilterRequirementOptions(keyword);
  };

  const handleFilterRequirementDropdownVisibleChange = (visible: boolean) => {
    if (visible) {
      loadFilterRequirementOptions('').then(() => {
        ensureFilterRequirementSelected();
      });
    }
  };

  const ensureFilterRequirementSelected = async () => {
    if (!searchRequirementId.value) return;
    const exists = filterRequirementOptions.value.some(
      (r: any) => r.id === searchRequirementId.value
    );
    if (exists) return;
    try {
      const res: any = await getRequirementDetail(searchRequirementId.value);
      if (res.data) {
        filterRequirementOptions.value.unshift(res.data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const loadFilterCaseOptions = async (
    keyword?: string,
    isLoadMore = false
  ) => {
    if (!projectStore.getProjectId) return;
    if (!isLoadMore) {
      filterCasePage.value.current = 1;
      filterCasePage.value.keyword = keyword || '';
      filterCaseOptions.value = [];
    }
    try {
      const res: any = await getTestCaseList(
        projectStore.getProjectId,
        undefined,
        undefined,
        filterCasePage.value.keyword || undefined,
        undefined,
        undefined,
        filterCasePage.value.current,
        filterCasePage.value.pageSize
      );
      const records = res.data?.records || [];
      if (isLoadMore) {
        filterCaseOptions.value.push(...records);
      } else {
        filterCaseOptions.value = records;
      }
      filterCasePage.value.hasMore =
        records.length >= filterCasePage.value.pageSize;
    } catch (e) {
      console.error(e);
    }
  };

  const loadMoreFilterCase = async () => {
    if (!filterCasePage.value.hasMore || filterCasePage.value.loading) return;
    filterCasePage.value.loading = true;
    try {
      filterCasePage.value.current++;
      await loadFilterCaseOptions(undefined, true);
    } finally {
      filterCasePage.value.loading = false;
    }
  };

  const handleSearchFilterCase = (keyword: string) => {
    loadFilterCaseOptions(keyword);
  };

  const handleFilterCaseDropdownVisibleChange = (visible: boolean) => {
    if (visible) {
      loadFilterCaseOptions('').then(() => {
        ensureFilterCaseSelected();
      });
    }
  };

  const ensureFilterCaseSelected = async () => {
    if (!searchCaseId.value) return;
    const exists = filterCaseOptions.value.some(
      (tc: any) => tc.id === searchCaseId.value
    );
    if (exists) return;
    try {
      const res: any = await getTestCaseDetail(searchCaseId.value);
      if (res.data) {
        filterCaseOptions.value.unshift(res.data);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleSave = async () => {
    const success = await bugFormRef.value?.save();
    if (success) {
      modalVisible.value = false;
      await loadData();
    }
  };

  const handleModalCancel = () => {
    confirmIfDirty(!!bugFormRef.value?.isDirty?.(), () => { modalVisible.value = false; });
  };

  useDirtyGuard(() => modalVisible.value && (bugFormRef.value?.isDirty?.() ?? false));

  const handleDelete = async (id: number) => {
    try {
      const res: any = await deleteBug(id);
      if (res.code === 200) {
        Message.success(t('qa.bug.deleteSuccess'));
        await loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  // 主表列定义
  const columns = [
    { title: t('qa.bug.code'), dataIndex: 'bugCode', width: 120, ellipsis: true, tooltip: true, slotName: 'bugCode' },
    { title: t('qa.bug.bugName'), dataIndex: 'title', width: 200, ellipsis: true, tooltip: true, slotName: 'title' },
    { title: t('qa.bug.bugStatus'), dataIndex: 'status', width: 90, slotName: 'status' },
    { title: t('qa.bug.handler'), dataIndex: 'assigneeName', width: 100, ellipsis: true, tooltip: true, slotName: 'assigneeName' },
    { title: t('qa.bug.tags'), dataIndex: 'tags', ellipsis: true, tooltip: true, slotName: 'tags' },
    { title: t('qa.bug.createUser'), dataIndex: 'createUserName', width: 100, ellipsis: true, tooltip: true, slotName: 'createUserName' },
    { title: t('qa.bug.createTime'), dataIndex: 'createTime', width: 180, ellipsis: true, tooltip: true, slotName: 'createTime' },
    { title: t('qa.bug.updateUser'), dataIndex: 'updateUserName', width: 100, ellipsis: true, tooltip: true, slotName: 'updateUserName' },
    { title: t('qa.bug.updateTime'), dataIndex: 'updateTime', width: 180, ellipsis: true, tooltip: true, slotName: 'updateTime' },
    { title: t('qa.bug.severity'), dataIndex: 'severity', width: 90, slotName: 'severity' },
    { title: t('qa.common.operation'), width: 200, fixed: 'right' as const, slotName: 'actions' },
  ];

  // ========== 操作列：统一文字按钮 + 更多下拉 ==========
  interface BugOp {
    key: string;
    label: string;
    danger?: boolean;
    permission: string;
    handler: (record: any) => void;
  }

  // 删除操作：二次确认后再调用 handleDelete
  const confirmDeleteBug = (record: any) => {
    Modal.confirm({
      title: t('qa.bug.deleteTitle'),
      content: t('qa.bug.deleteContent', { title: record.title }),
      okText: t('qa.bug.deleteConfirm'),
      cancelText: t('qa.common.cancel'),
      okButtonProps: { status: 'danger' },
      onOk: async () => {
        await handleDelete(record.id);
      },
    });
  };

  // 操作列定义：详情 / 编辑 / 复制 / 删除
  const opsFor = (record: any): BugOp[] => [
    { key: 'preview', label: t('qa.bug.actionDetail'), permission: '', handler: handlePreview },
    {
      key: 'edit',
      label: t('qa.bug.actionEdit'),
      permission: QA_BUG_UPDATE,
      handler: handleEdit,
    },
    {
      key: 'copy',
      label: t('qa.bug.actionCopy'),
      permission: QA_BUG_CREATE,
      handler: handleCopy,
    },
    {
      key: 'delete',
      label: t('qa.common.delete'),
      danger: true,
      permission: QA_BUG_DELETE,
      handler: confirmDeleteBug,
    },
  ];

  // 平铺规则：前 2 个常显，第 3 个起进「更多」下拉
  const visibleOps = (record: any) => opsFor(record).slice(0, 2);
  const overflowOps = (record: any) => opsFor(record).slice(2);

  const handleSelectChange = (keys: (string | number)[]) => {
    selectedKeys.value = keys as number[];
  };

  const handleBatchDelete = () => {
    if (selectedKeys.value.length === 0) return;
    Modal.confirm({
      title: t('qa.bug.batchDeleteTitle'),
      content: t('qa.bug.batchDeleteContent', { count: selectedKeys.value.length }),
      okText: t('qa.common.delete'),
      cancelText: t('qa.common.cancel'),
      okButtonProps: { status: 'danger' },
      onOk: async () => {
        const res: any = await batchDeleteBug(selectedKeys.value);
        if (res.code === 200) {
          Message.success(t('qa.bug.batchDeleteSuccess'));
          selectedKeys.value = [];
          await loadData();
        }
      },
    });
  };

  const openTransitionModal = (record: any) => {
    transitionForm.value = {
      bugId: record.id,
      currentStatus: record.status,
      targetStatus: '',
    };
    transitionModalVisible.value = true;
  };

  const handleConfirmTransition = async () => {
    if (!transitionForm.value.targetStatus) {
      Message.warning(t('qa.bug.selectTargetStatus'));
      return;
    }
    try {
      const res: any = await transitionBugStatus(
        transitionForm.value.bugId,
        transitionForm.value.targetStatus
      );
      if (res.code === 200) {
        Message.success(t('qa.bug.statusUpdated'));
        transitionModalVisible.value = false;
        await loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const severityColor = (severity: string) => {
    const map: Record<string, string> = {
      FATAL: 'red',
      SERIOUS: 'orange',
      NORMAL: 'blue',
      TIPS: 'gray',
    };
    return map[severity] || 'gray';
  };
  const formatTime = (time: string | undefined) => {
    if (!time) return '-';
    return time.replace('T', ' ').substring(0, 19);
  };

  const severityText = (severity: string) => {
    const map: Record<string, string> = {
      FATAL: t('qa.bug.severity.fatal'),
      SERIOUS: t('qa.bug.severity.serious'),
      NORMAL: t('qa.bug.severity.normal'),
      TIPS: t('qa.bug.severity.tips'),
    };
    return map[severity] || severity;
  };
  const priorityColor = (priority: string) => {
    const map: Record<string, string> = {
      URGENT: 'red',
      HIGH: 'orange',
      MEDIUM: 'blue',
      LOW: 'gray',
    };
    return map[priority] || 'gray';
  };
  const bugStatusColor = (status: string) => {
    const map: Record<string, string> = {
      NEW: 'red',
      CONFIRMED: 'orange',
      FIXING: 'gold',
      FIXED: 'blue',
      VERIFIED: 'cyan',
      CLOSED: 'gray',
      REJECTED: 'gray',
    };
    return map[status] || 'gray';
  };
  const bugStatusText = (status: string) => {
    const map: Record<string, string> = {
      NEW: t('qa.bug.status.new'),
      CONFIRMED: t('qa.bug.status.confirmed'),
      FIXING: t('qa.bug.status.fixing'),
      FIXED: t('qa.bug.status.fixed'),
      VERIFIED: t('qa.bug.status.verified'),
      CLOSED: t('qa.bug.status.closed'),
      REJECTED: t('qa.bug.status.rejected'),
    };
    return map[status] || status;
  };
  const renderHtml = (
    content: string | undefined,
    defaultText?: string
  ) => {
    if (!content || content.trim() === '') return defaultText ?? t('qa.bug.noContent');
    // 如果内容已经包含 HTML 标签，直接返回
    if (/<[a-z][\s\S]*?>/i.test(content)) {
      return content;
    }
    // 纯文本，把换行符转成 <br>
    return content.replace(/\n/g, '<br>');
  };

  const renderStepsHtml = (content: string | undefined) => {
    if (!content || content.trim() === '') return '';
    // 已有 HTML 时直接返回
    if (/<[a-z][\s\S]*?>/i.test(content)) {
      return content;
    }
    const lines = content.split('\n').filter((line) => line.trim() !== '');
    if (lines.length === 0) return '';
    return `<ol>${lines
      .map(
        (line) => `<li>${line.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</li>`
      )
      .join('')}</ol>`;
  };

  const stringToColor = (str: string) => {
    if (!str) return 'rgb(var(--arcoblue-6))';
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      hash = str.charCodeAt(i) + ((hash << 5) - hash);
    }
    const colors = [
      'rgb(var(--arcoblue-6))',
      'rgb(var(--green-6))',
      'rgb(var(--orange-6))',
      'rgb(var(--red-6))',
      'rgb(var(--purple-6))',
      'rgb(var(--magenta-6))',
    ];
    return colors[Math.abs(hash) % colors.length];
  };

  const avatarText = (name: string) => {
    if (!name) return '?';
    return name.charAt(0).toUpperCase();
  };

  const relativeTime = (timeStr: string) => {
    if (!timeStr) return '';
    const date = new Date(timeStr.replace(' ', 'T'));
    if (isNaN(date.getTime())) return timeStr;
    const now = new Date();
    const diff = Math.floor((now.getTime() - date.getTime()) / 1000);
    if (diff < 60) return t('qa.bug.timeJustNow');
    if (diff < 3600) return t('qa.bug.timeMinutesAgo', { n: Math.floor(diff / 60) });
    if (diff < 86400) return t('qa.bug.timeHoursAgo', { n: Math.floor(diff / 3600) });
    if (diff < 2592000) return t('qa.bug.timeDaysAgo', { n: Math.floor(diff / 86400) });
    return timeStr;
  };
  const allBugStatuses = computed(() => [
    { value: 'NEW', label: t('qa.bug.status.new') },
    { value: 'CONFIRMED', label: t('qa.bug.status.confirmed') },
    { value: 'FIXING', label: t('qa.bug.status.fixing') },
    { value: 'FIXED', label: t('qa.bug.status.fixed') },
    { value: 'VERIFIED', label: t('qa.bug.status.verified') },
    { value: 'CLOSED', label: t('qa.bug.status.closed') },
    { value: 'REJECTED', label: t('qa.bug.status.rejected') },
  ]);
  const environmentText = (env: string) => {
    const map: Record<string, string> = {
      TEST: t('qa.bug.env.test'),
      STAGING: t('qa.bug.env.staging'),
      PROD: t('qa.bug.env.prod'),
    };
    return map[env] || env || '-';
  };
  const reproduceRateText = (rate: string) => {
    const map: Record<string, string> = {
      ALWAYS: t('qa.bug.rate.always'),
      OFTEN: t('qa.bug.rate.often'),
      SOMETIMES: t('qa.bug.rate.sometimes'),
      RARE: t('qa.bug.rate.rare'),
    };
    return map[rate] || rate || '-';
  };
  const closeReasonText = (closeReason: string) => {
    const map: Record<string, string> = {
      FIXED: t('qa.bug.status.fixed'),
      DUPLICATE: t('qa.bug.closeReason.duplicate'),
      NOT_BUG: t('qa.bug.closeReason.notBug'),
      CANNOT_REPRODUCE: t('qa.bug.closeReason.cannotReproduce'),
      WONT_FIX: t('qa.bug.closeReason.wontFix'),
    };
    return map[closeReason] || closeReason || '-';
  };

  // 统计卡片：后端全项目口径聚合，不再只数当前页
  const loadBugStats = async () => {
    if (!projectStore.getProjectId) return;
    try {
      const res: any = await getBugStats(projectStore.getProjectId);
      if (res.code === 200 && res.data) {
        bugStats.value = {
          total: 0,
          open: 0,
          serious: 0,
          fatal: 0,
          ...res.data,
        };
      }
    } catch (e) {
      console.error(e);
    }
  };

  onMounted(() => {
    loadData();
    loadFilterRequirementOptions('');
    loadFilterCaseOptions('');
    loadModuleOptions();
  });

  watch(
    () => projectStore.getProjectId,
    (newId) => {
      if (newId) {
        loadData();
        filterRequirementOptions.value = [];
        filterCaseOptions.value = [];
        loadFilterRequirementOptions('');
        loadFilterCaseOptions('');
        loadModuleOptions();
      }
    },
    { immediate: true }
  );
</script>

<style scoped lang="less">
  .bug-page {
    padding: 0 20px 20px;
    display: flex;
    flex-direction: column;
    height: var(--page-container-height, calc(100vh - 60px));
  }

  .bug-card {
    flex: 1;
    height: 100%;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  .bug-card :deep(.arco-card-body) {
    flex: 1;
    height: 100%;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  /* 危机指标卡片 */
  .metric-card {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 16px 20px;
    border-radius: 8px;
    background: var(--color-bg-2);
    border: 1px solid var(--color-border-2);
    cursor: pointer;
    transition: all 0.2s ease;
    margin-bottom: 16px;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
    }

    .metric-content {
      display: flex;
      flex-direction: column;
      gap: 4px;
    }

    .metric-number {
      font-size: 28px;
      font-weight: 700;
      line-height: 1.2;
    }

    .metric-label {
      font-size: 13px;
    }

    .metric-icon {
      width: 44px;
      height: 44px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  }

  .metric-total {
    background: #f2f3f5;
    border-color: #e5e6eb;
    color: #4e5969;

    .metric-icon {
      background: #e5e6eb;
      color: #4e5969;
    }
  }

  .metric-open {
    background: #e8f3ff;
    border-color: #bedaff;
    color: rgb(var(--arcoblue-6));

    .metric-icon {
      background: #d4e8ff;
      color: rgb(var(--arcoblue-6));
    }
  }

  .metric-serious {
    background: #fff7e8;
    border-color: #ffe4ba;
    color: #ff7d00;

    .metric-icon {
      background: #ffecd4;
      color: #ff7d00;
    }
  }

  .metric-fatal {
    position: relative;
    background: #ffece8;
    border-color: #ffd6cf;
    border-left: 4px solid #a8071a;
    color: #f53f3f;

    .metric-icon {
      background: #ffd6cf;
      color: #a8071a;
    }
  }

  /* 主操作行 */
  .filter-toolbar {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;

    .filter-actions-left {
      display: flex;
      align-items: center;
      gap: 12px;
    }
  }

  .add-btn {
    background: #e11d48 !important;
    border-color: #e11d48 !important;
    font-weight: 600;

    &:hover {
      background: #be123c !important;
      border-color: #be123c !important;
    }
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

  /* 表格区 */
  .table-wrapper {
    flex: 1;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  /* 表格撑满容器，表体滚动，分页固定底部 */
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

  .bug-code-cell {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-family: monospace;
    font-size: 12px;
    color: var(--color-text-3);

    .bug-code-text {
      flex-shrink: 0;
    }

    .copy-icon {
      display: inline-flex;
      align-items: center;
      cursor: pointer;
      opacity: 0.6;
      transition: all 0.2s;

      &:hover {
        opacity: 1;
        color: rgb(var(--primary-6));
      }
    }
  }

  .bug-title-link {
    display: inline-block;
    max-width: 240px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: rgb(var(--primary-6));
    cursor: pointer;
    transition: all 0.2s;

    &:hover {
      text-decoration: underline;
    }
  }

  .bug-case-link {
    color: rgb(var(--primary-6));
    cursor: pointer;

    &:hover {
      color: rgb(var(--link-color-hover));
    }
  }

  .severity-badge {
    display: inline-flex;
    align-items: center;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 12px;
    font-weight: 500;

    &.fatal {
      background: #fff1f3;
      color: #be123c;
    }

    &.serious {
      background: #fff7e8;
      color: #b45d00;
    }

    &.normal {
      background: #f2f3f5;
      color: #4e5969;
    }

    &.tips {
      background: #f7f8fa;
      color: #86909c;
    }
  }

  .priority-dot {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 13px;
    text-transform: uppercase;

    .dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
    }

    &.urgent,
    &.high {
      color: rgb(var(--danger-6));

      .dot {
        background: rgb(var(--danger-6));
      }
    }

    &.medium {
      color: rgb(var(--orange-6));

      .dot {
        background: rgb(var(--orange-6));
      }
    }

    &.low {
      color: rgb(var(--gray-5));

      .dot {
        background: rgb(var(--gray-5));
      }
    }
  }

  :deep(.arco-tag.status-pill) {
    border-radius: 999px;
  }

  .action-btn {
    transition: all 0.2s;

    &:hover {
      color: rgb(var(--primary-6));
    }

    &.danger:hover {
      color: rgb(var(--danger-6));
    }
  }

  .rich-text-preview {
    line-height: 1.6;
    :deep(p) {
      margin: 8px 0;
    }
    :deep(ul),
    :deep(ol) {
      padding-left: 20px;
      margin: 8px 0;
    }
    :deep(li) {
      margin-bottom: 6px;
    }
    :deep(blockquote) {
      border-left: 4px solid #ccc;
      padding-left: 10px;
      color: #666;
      margin: 8px 0;
    }
    :deep(img) {
      max-width: 100%;
    }
    :deep(a) {
      color: rgb(var(--primary-6));
    }
    :deep(code) {
      background: #fff1f3;
      color: #be123c;
      padding: 1px 5px;
      border-radius: 4px;
      font-family: 'SFMono-Regular', Consolas, monospace;
      font-size: 12px;
    }
    :deep(pre) {
      background: #f7f8fa;
      padding: 10px;
      border-radius: 6px;
      overflow-x: auto;
      :deep(code) {
        background: transparent;
        padding: 0;
      }
    }
  }

  .qa-edit-modal {
    // 弹窗高度跟随浏览器窗口自适应（宽度由 modalWidth 计算属性控制）
    :deep(.arco-modal) {
      height: 86vh;
      display: flex;
      flex-direction: column;
    }

    :deep(.arco-modal-header) {
      flex-shrink: 0;
    }

    :deep(.arco-modal-body) {
      padding: 0;
      flex: 1 1 0;
      min-height: 0;
      overflow: hidden;
    }

    :deep(.arco-modal-footer) {
      flex-shrink: 0;
    }
  }

  .modal-scroll-body {
    height: 100%;
    overflow-y: auto;
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
  }

  :deep(.arco-table-row-selected) {
    background-color: rgb(var(--primary-1));
  }

  /* BUG 预览抽屉 */
  .bug-preview-drawer {
    :deep(.arco-drawer-body) {
      padding: 0;
      overflow: hidden;
      height: 100%;
    }
  }

  .preview-drawer-inner {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  .preview-header {
    flex-shrink: 0;
    padding: 16px 20px;
    border-bottom: 1px solid var(--color-border-2);
    display: flex;
    justify-content: space-between;
    gap: 16px;
    background: var(--color-bg-2);
  }

  .preview-header-main {
    flex: 1;
    min-width: 0;
  }

  .preview-code-row {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 8px;
  }

  .preview-code-tag {
    font-family: 'SFMono-Regular', Consolas, monospace;
    background: #fff1f3;
    color: #be123c;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 12px;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .preview-code-copy {
    cursor: pointer;
    opacity: 0.6;
    transition: opacity 0.2s;

    &:hover {
      opacity: 1;
    }
  }

  .preview-status-tag {
    font-size: 12px;
  }

  .preview-title {
    margin: 0;
    font-size: 18px;
    font-weight: 700;
    line-height: 1.4;
    word-break: break-word;
    color: var(--color-text-1);
  }

  .preview-header-actions {
    display: flex;
    gap: 4px;
    flex-shrink: 0;
  }

  .preview-body {
    flex: 1;
    min-height: 0;
    overflow-y: auto;
    padding: 16px 20px;
  }

  .preview-meta-card {
    background: var(--color-fill-2);
    border-radius: 8px;
    padding: 16px;
    margin-bottom: 16px;
  }

  .preview-meta-grid {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 16px;
  }

  .preview-meta-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }

  .preview-meta-label {
    font-size: 12px;
    color: var(--color-text-3);
  }

  .preview-meta-value {
    font-size: 13px;
    color: var(--color-text-1);
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .preview-collab-row {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 16px;
  }

  .preview-collab-item {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .preview-collab-label {
    font-size: 12px;
    color: var(--color-text-3);
  }

  .preview-collab-name {
    font-size: 13px;
    color: var(--color-text-1);
  }

  .preview-create-time {
    margin-left: auto;
    font-size: 12px;
    color: var(--color-text-3);
  }

  .preview-section {
    margin-bottom: 16px;
  }

  .preview-section-title {
    font-weight: 600;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 14px;
    color: var(--color-text-1);
  }

  .preview-steps-box {
    background: #f7f8fa;
    border-radius: 8px;
    padding: 12px;
    max-height: 160px;
    overflow-y: auto;
  }

  .preview-desc-box {
    background: var(--color-bg-2);
    border: 1px solid var(--color-border-2);
    border-radius: 8px;
    padding: 12px;
    max-height: 180px;
    overflow-y: auto;
  }

  .preview-relation-row {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
    margin-bottom: 16px;
  }

  .preview-relation-card {
    background: var(--color-bg-2);
    border: 1px solid var(--color-border-2);
    border-radius: 8px;
    padding: 12px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    cursor: pointer;
    transition: all 0.2s ease;

    &:hover:not(.disabled) {
      background: var(--color-fill-2);
      box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
    }

    &.disabled {
      cursor: default;
      opacity: 0.6;
    }
  }

  .preview-relation-type {
    font-size: 12px;
    color: var(--color-text-3);
    margin-bottom: 4px;
  }

  .preview-relation-name {
    font-size: 13px;
    color: var(--color-text-1);
    font-weight: 500;
  }

  .preview-tabs {
    :deep(.arco-tabs-content) {
      padding-top: 12px;
    }
  }

  .preview-comment-list {
    max-height: 180px;
    overflow-y: auto;
  }

  .preview-comment-item {
    display: flex;
    gap: 12px;
  }

  .preview-comment-main {
    flex: 1;
    min-width: 0;
  }

  .preview-comment-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }

  .preview-comment-user {
    font-weight: 600;
    font-size: 13px;
    color: var(--color-text-1);
  }

  .preview-comment-time {
    font-size: 12px;
    color: var(--color-text-3);
  }

  .preview-comment-bubble {
    background: var(--color-fill-2);
    padding: 10px 12px;
    border-radius: 8px;
    font-size: 13px;
    color: var(--color-text-1);
    word-break: break-word;
  }

  .preview-comment-input-area {
    margin-top: 12px;
  }

  .preview-comment-input {
    transition: all 0.2s ease;

    &:focus {
      border-color: rgb(var(--arcoblue-6));
      box-shadow: 0 0 0 2px rgba(var(--arcoblue-6), 0.1);
    }
  }

  .preview-comment-submit {
    margin-top: 8px;
    float: right;
    opacity: 0;
    transform: translateY(-4px);
    transition: all 0.2s ease;

    &.visible {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .preview-log-list {
    max-height: 220px;
    overflow-y: auto;
  }

  .preview-log-header {
    color: #86909c;
    font-size: 12px;
    margin-bottom: 4px;
  }

  .preview-log-content {
    font-size: 13px;
    color: var(--color-text-1);
  }

  .preview-footer {
    flex-shrink: 0;
    padding: 12px 20px;
    background: #f7f8fa;
    border-top: 1px solid var(--color-border-2);
    display: flex;
    justify-content: flex-end;
    gap: 12px;
  }
</style>
