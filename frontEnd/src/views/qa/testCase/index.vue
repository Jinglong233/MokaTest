<template>
  <div class="test-case-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testCase']" />
    <a-tabs
      v-model:active-key="activeMainTab"
      class="case-main-tabs"
      :lazy-load="true"
    >
      <a-tab-pane key="case" :title="$t('qa.testCase.tab.case')">
        <div class="case-view-wrap">
          <!-- 列表/脑图 视图切换 -->
          <div class="case-view-toggle">
            <a-radio-group :model-value="caseViewMode" type="button" size="small" @change="handleViewModeChange">
              <a-radio value="list"><icon-list /> 列表</a-radio>
              <a-radio value="mindmap"><icon-mind-mapping /> 脑图</a-radio>
            </a-radio-group>
          </div>
        <div class="main-row">
          <!-- 左侧模块树 -->
          <div class="col side-col" :style="sideColStyle">
            <a-card class="tree-card" size="small" :bordered="false">
              <div class="tree-scroll-wrapper">
                <ModuleTreeManager
                  ref="moduleTreeRef"
                  :show-search="true"
                  :show-virtual-root="true"
                  :selected-keys="[searchModuleId ?? 0]"
                  default-expand-all
                  @select="handleTreeSelect"
                  @changed="handleModuleChanged"
                >
                  <template #search-extra>
                    <a-button
                      v-permission="'qa:testcase:create'"
                      type="primary"
                      size="small"
                      @click="handleAdd"
                    >
                      新建
                    </a-button>
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
            <a-card v-show="caseViewMode === 'list'" class="list-card" :bordered="false">
              <!-- 工具栏 -->
              <a-row :wrap="true" :gutter="[16, 12]" align="center" class="filter-grid" style="margin: 8px 0 16px;">
                <a-col flex="280px">
                  <div class="filter-field">
                    <label class="filter-label">用例名称</label>
                    <a-input v-model="searchKeyword" :placeholder="$t('qa.testCase.name')" allow-clear />
                  </div>
                </a-col>
                <a-col flex="280px">
                  <div class="filter-field">
                    <label class="filter-label">状态</label>
                    <a-select v-model="searchStatus" placeholder="请选择" allow-clear>
                      <a-option value="DRAFT">草稿</a-option>
                      <a-option value="REVIEWING">评审中</a-option>
                      <a-option value="REVIEWED">已评审</a-option>
                      <a-option value="DEPRECATED">已废弃</a-option>
                    </a-select>
                  </div>
                </a-col>
                <a-col flex="280px">
                  <div class="filter-field">
                    <label class="filter-label">最近结果</label>
                    <a-select v-model="searchLastResult" placeholder="请选择" allow-clear>
                      <a-option value="PASS">成功</a-option>
                      <a-option value="FAIL">失败</a-option>
                      <a-option value="BLOCK">阻塞</a-option>
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
                <!-- 批量操作工具栏：常驻显示，无选中时置灰 -->
                <div class="batch-bar">
                  <span class="batch-count"
                    >已选 {{ selectedIds.length }} 条</span
                  >
                  <a-dropdown-button
                    size="small"
                    type="outline"
                    :disabled="!selectedIds.length"
                  >
                    批量操作
                    <template #icon><icon-more /></template>
                    <template #content>
                      <a-doption
                        v-permission="'qa:testcase:update'"
                        :disabled="!selectedIds.length"
                        @click="
                          moveModalVisible = true;
                          loadMoveModuleTree();
                        "
                      >
                        <template #icon><icon-to-right /></template>
                        移动到模块
                      </a-doption>
                      <a-doption
                        v-permission="'qa:testcase:delete'"
                        :disabled="!selectedIds.length"
                        @click="handleBatchDeleteClick"
                      >
                        <template #icon><icon-delete /></template>
                        <span style="color: #f53f3f">批量删除</span>
                      </a-doption>
                    </template>
                  </a-dropdown-button>
                </div>
                <a-table
                  :data="testCaseList"
                  :loading="loading"
                  :pagination="pagination"
                  row-key="id"
                  :selected-keys="selectedIds"
                  :row-selection="{
                    type: 'checkbox',
                    showCheckedAll: true,
                    onlyCurrent: true,
                  }"
                  :sticky-header="true"
                  :scroll="{
                    x: '125%',
                    y: '100%',
                  }"
                  @page-change="handlePageChange"
                  @page-size-change="handlePageSizeChange"
                  @selection-change="onSelectionChange"
                  :columns="columns"
                >
                  <template #caseCode="{ record }">
                    <span
                      class="case-name-link"
                      @click="handlePreview(record)"
                    >
                      {{ record.caseCode }}
                    </span>
                  </template>
                  <template #caseName="{ record }">
                    <a-input
                      v-if="isEditing(record, 'caseName')"
                      v-model="draftValue"
                      size="small"
                      autofocus
                      :max-length="100"
                      @press-enter="commitEdit(record, 'caseName')"
                      @blur="onNameBlur(record)"
                      @keydown.esc.stop="cancelEdit"
                    />
                    <a-tooltip
                      v-else
                      :content="record.caseName"
                      position="top"
                    >
                      <span
                        class="cell-display"
                        :class="{ 'inline-editable': canInlineEdit }"
                        @click="startEdit(record, 'caseName')"
                      >
                        {{ record.caseName }}
                      </span>
                    </a-tooltip>
                  </template>
                  <template #priority="{ record }">
                    <a-select
                      v-if="isEditing(record, 'priority')"
                      v-model="draftValue"
                      size="small"
                      style="width: 90px"
                      default-popup-visible
                      @change="commitEdit(record, 'priority')"
                      @popup-visible-change="onEditPopupClose"
                    >
                      <a-option value="P0">P0</a-option>
                      <a-option value="P1">P1</a-option>
                      <a-option value="P2">P2</a-option>
                    </a-select>
                    <a-tag
                      v-else
                      :color="priorityColor(record.priority)"
                      size="small"
                      :class="{ 'inline-editable': canInlineEdit }"
                      @click="startEdit(record, 'priority')"
                      >{{ record.priority }}</a-tag
                    >
                  </template>
                  <template #lastResult="{ record }">
                    <a-tooltip
                      v-if="record.lastResult"
                      :content="
                        record.lastExecuteTime
                          ? `执行时间：${record.lastExecuteTime}`
                          : '暂无执行时间'
                      "
                    >
                      <a-tag
                        :color="lastResultColor(record.lastResult)"
                        size="small"
                        >{{ lastResultText(record.lastResult) }}</a-tag
                      >
                    </a-tooltip>
                    <span v-else style="color: #86909c">-</span>
                  </template>
                  <template #status="{ record }">
                    <a-tag
                      :color="statusColor(record.status)"
                      size="small"
                      >{{ statusText(record.status) }}</a-tag
                    >
                  </template>
                  <template #moduleName="{ record }">
                    <a-tree-select
                      v-if="isEditing(record, 'moduleId')"
                      v-model="draftValue"
                      :data="inlineModuleTree"
                      :field-names="{ key: 'id', title: 'moduleName' }"
                      placeholder="请选择所属模块"
                      allow-clear
                      size="small"
                      style="width: 100%"
                      default-popup-visible
                      @change="commitEdit(record, 'moduleId')"
                      @popup-visible-change="onEditPopupClose"
                    />
                    <span
                      v-else
                      class="cell-display"
                      :class="{ 'inline-editable': canInlineEdit }"
                      @click="startEdit(record, 'moduleId')"
                    >
                      <span v-if="record.moduleName">{{
                        record.moduleName
                      }}</span>
                      <span v-else style="color: #86909c">-</span>
                    </span>
                  </template>
                  <template #tags="{ record }">
                    <a-input-tag
                      v-if="isEditing(record, 'tags')"
                      v-model="draftValue"
                      placeholder="输入标签后按回车"
                      allow-clear
                      size="small"
                      style="width: 100%"
                      @change="commitTags(record)"
                      @blur="cancelEdit"
                      @keydown.esc.stop="cancelEdit"
                    />
                    <span
                      v-else
                      class="cell-display"
                      :class="{ 'inline-editable': canInlineEdit }"
                      @click="startEdit(record, 'tags')"
                    >
                      <a-tooltip
                        v-if="record.tags"
                        :content="record.tags"
                        position="top"
                      >
                        <span>{{ record.tags }}</span>
                      </a-tooltip>
                      <span v-else style="color: #86909c">-</span>
                    </span>
                  </template>
                  <template #updateUserName="{ record }">
                    <span v-if="record.updateUserName">{{
                      record.updateUserName
                    }}</span>
                    <span v-else style="color: #86909c">-</span>
                  </template>
                  <template #createUserName="{ record }">
                    <span v-if="record.createUserName">{{
                      record.createUserName
                    }}</span>
                    <span v-else style="color: #86909c">-</span>
                  </template>
                  <template #operations="{ record }">
                    <a-space :size="0">
                      <a-button
                        v-for="op in visibleOps(record)"
                        :key="op.key"
                        v-permission="op.permission"
                        type="text"
                        size="small"
                        :status="op.danger ? 'danger' : undefined"
                        :disabled="op.disabled"
                        @click="op.handler(record)"
                      >
                        {{ op.label }}
                      </a-button>
                      <a-dropdown
                        v-if="overflowOps(record).length"
                        trigger="click"
                      >
                        <a-button type="text" size="small">
                          ...
                        </a-button>
                        <template #content>
                          <a-doption
                            v-for="op in overflowOps(record)"
                            :key="op.key"
                            v-permission="op.permission"
                            :disabled="op.disabled"
                            @click="guardAndRun(op, record)"
                          >
                            <span
                              :style="
                                op.danger
                                  ? 'color: rgb(var(--danger-6))'
                                  : ''
                              "
                              >{{ op.label }}</span
                            >
                          </a-doption>
                        </template>
                      </a-dropdown>
                    </a-space>
                  </template>
                </a-table>
              </div>
            </a-card>
            <!-- 脑图视图：根节点跟随左侧模块选中状态；双击节点编辑 -->
            <CaseLibMindMap
              v-if="caseViewMode === 'mindmap'"
              ref="caseMindmapRef"
              :project-id="projectStore.getProjectId"
              :selected-module-id="searchModuleId"
              class="case-mindmap-fill"
              @open-detail="handleMindmapOpenDetail"
            />
          </div>
        </div>
        </div>
      </a-tab-pane>
      <a-tab-pane key="review" :title="$t('qa.testCase.tab.review')">
        <div class="review-tab-body">
          <ReviewList embedded />
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- 用例详情抽屉（MS v3：详情/关联需求/关联用例/关联缺陷/执行历史/操作日志/变更历史/评论） -->
    <CaseDetailDrawer
      v-model:visible="previewVisible"
      :case-id="previewData.id || null"
      :active-tab="activeDetailTab"
      :highlight-comment-id="highlightCommentId"
      @refresh="loadData"
    />

    <!-- 批量移动模块弹窗 -->
    <a-modal
      v-model:visible="moveModalVisible"
      title="移动到模块"
      :width="480"
      :ok-loading="moveLoading"
      @ok="handleBatchMove"
    >
      <div style="padding: 4px 0">
        <div class="move-label"
          >目标模块 <span style="color: #f53f3f">*</span></div
        >
        <a-tree-select
          v-model="moveTargetModuleId"
          :data="moveModuleTree"
          :field-names="{ key: 'id', title: 'moduleName' }"
          placeholder="请选择目标模块"
          allow-clear
          style="width: 100%; margin-bottom: 12px"
        />
        <p style="color: var(--color-text-3); font-size: 13px">
          将把已选的 {{ selectedIds.length }} 条用例移动到目标模块下。
        </p>
      </div>
    </a-modal>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'TestCase' };
</script>

<script setup lang="ts">
  import { ref, onMounted, onBeforeUnmount, watch, computed, nextTick } from 'vue';
  import { useRouter, useRoute, onBeforeRouteLeave } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';
  import {
    IconPlus,
    IconDelete,
    IconDown,
    IconRight,
    IconToRight,
  } from '@arco-design/web-vue/es/icon';
  import Breadcrumb from '@/components/breadcrumb/index.vue';
  import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
  import CaseDetailDrawer from './components/CaseDetailDrawer.vue';
  import ReviewList from '@/views/qa/review/ReviewList.vue';
  import ModuleTreeManager from '@/views/qa/components/ModuleTreeManager.vue';
  import CaseLibMindMap from './CaseLibMindMap.vue';
  import { useProjectStore } from '@/store';
  import useProjectConfigStore from '@/store/modules/projectConfig';
  import {
    getTestCaseList,
    deleteTestCase,
    batchDeleteTestCase,
    batchMoveTestCase,
    updateTestCase,
    getQaModuleList,
    getQaModuleTree,
    getTestCaseDetail,
    saveTestCase,
  } from '@/api/MyApi/qa';
  import { usePermissionStore } from '@/store';

  const permissionStore = usePermissionStore();
  const hasPerm = (code: string) => permissionStore.hasPermission(code);

  const projectStore = useProjectStore();
  const router = useRouter();
  const route = useRoute();
  const projectConfigStore = useProjectConfigStore();
  const fieldVis = (key: string) =>
    projectConfigStore.isFieldVisible('testCase', key);

  // 主表列定义
  const columns = [
    { title: '用例编号', dataIndex: 'caseCode', width: 120, ellipsis: true, tooltip: true, slotName: 'caseCode' },
    { title: '用例名称', dataIndex: 'caseName', width: 200, ellipsis: true, tooltip: true, slotName: 'caseName' },
    { title: '用例等级', dataIndex: 'priority', width: 80, slotName: 'priority' },
    { title: '最近结果', dataIndex: 'lastResult', width: 100, slotName: 'lastResult' },
    { title: '状态', dataIndex: 'status', width: 90, slotName: 'status' },
    { title: '所属模块', dataIndex: 'moduleName', ellipsis: true, tooltip: true, slotName: 'moduleName' },
    { title: '标签', dataIndex: 'tags', width: 120, ellipsis: true, tooltip: true, slotName: 'tags' },
    { title: '更新人', dataIndex: 'updateUserName', width: 100, ellipsis: true, tooltip: true, slotName: 'updateUserName' },
    { title: '创建人', dataIndex: 'createUserName', width: 100, ellipsis: true, tooltip: true, slotName: 'createUserName' },
    { title: '更新时间', dataIndex: 'updateTime', width: 180, ellipsis: true, tooltip: true },
    { title: '操作', width: 200, fixed: 'right' as const, slotName: 'operations' },
  ];

  // 浏览器窗口宽度（弹窗宽度、侧栏最大宽度等都跟随它自适应）
  const windowWidth = ref(window.innerWidth);
  const handleWindowResize = () => {
    windowWidth.value = window.innerWidth;
  };
  onMounted(() => window.addEventListener('resize', handleWindowResize));
  onBeforeUnmount(() =>
    window.removeEventListener('resize', handleWindowResize)
  );

  // 浏览器刷新/关闭时的未保存拦截（beforeunload 只能触发原生确认框）
  // 注意：handler 在下方 onBeforeRouteLeave 旁定义，这里只负责挂载/卸载
  onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
  onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload));

  // ===== 左侧目录树：可拖拽伸缩 + 拖拽隐藏 =====
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
    Math.max(
      SIDEBAR_MIN_WIDTH,
      Math.min(SIDEBAR_MAX_WIDTH, Math.round(windowWidth.value * 0.4))
    )
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
    const expandedWidth = Math.round(
      (lastSidebarWidth.value || SIDEBAR_DEFAULT_WIDTH) * 1.25
    );
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
      sidebarWidth.value = Math.min(
        Math.max(newWidth, 0),
        sidebarMaxWidth.value
      );
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

  // 用于渲染 dropdownRender 插槽中的 VNode
  const VNodeRenderer = {
    props: ['vnodes'],
    render(this: { vnodes: any }) {
      return this.vnodes;
    },
  };

  const loading = ref(false);
  const activeMainTab = ref((route.query.tab as string) || 'case');
  // 用例列表：列表/脑图 视图切换
  const caseViewMode = ref<'list' | 'mindmap'>('list');
  const caseMindmapRef = ref<any>(null);
  // 未保存拦截：脑图有未保存修改时，切回列表需确认（脑图组件会被销毁，修改丢失）
  const handleViewModeChange = (mode: 'list' | 'mindmap') => {
    if (mode === 'list' && caseViewMode.value === 'mindmap' && caseMindmapRef.value?.isDirty?.()) {
      Modal.confirm({
        title: '有未保存的修改',
        content: '脑图中有未保存的修改，切换到列表将丢失这些修改，确认切换？',
        okText: '放弃并切换',
        cancelText: '继续编辑',
        onOk: () => {
          caseViewMode.value = 'list';
        },
      });
      return;
    }
    caseViewMode.value = mode;
  };
  // 脑图切回列表时刷新列表和模块树（脑图里的增删改/排序已落库，列表需同步）
  watch(caseViewMode, (mode) => {
    if (mode === 'list') {
      Promise.all([loadData(), moduleTreeRef.value?.loadData()]);
    }
  });
  // 离开页面前的全局拦截：脑图有未保存修改时需确认
  onBeforeRouteLeave((_to, _from, next) => {
    if (caseViewMode.value === 'mindmap' && caseMindmapRef.value?.isDirty?.()) {
      Modal.confirm({
        title: '有未保存的修改',
        content: '脑图中有未保存的修改，离开将丢失这些修改，确认离开？',
        okText: '放弃并离开',
        cancelText: '继续编辑',
        onOk: () => next(),
        onCancel: () => next(false),
      });
      return;
    }
    next();
  });
  // 浏览器刷新/关闭的兜底拦截（此场景只能用原生确认框，自定义弹窗无效）
  const handleBeforeUnload = (e: BeforeUnloadEvent) => {
    if (caseViewMode.value === 'mindmap' && caseMindmapRef.value?.isDirty?.()) {
      e.preventDefault();
      e.returnValue = '';
    }
  };
  const testCaseList = ref<any[]>([]);
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
      const res: any = await getQaModuleTree(pid, 'CASE');
      moveModuleTree.value = res.data || [];
    } catch (e) {
      console.error(e);
    }
  };

  const handleBatchDeleteClick = () => {
    Modal.warning({
      title: '批量删除',
      content: `确认删除选中的 ${selectedIds.value.length} 条用例？`,
      hideCancel: false,
      okText: '删除',
      onOk: handleBatchDelete,
    });
  };

  const handleBatchDelete = async () => {
    try {
      const res: any = await batchDeleteTestCase(selectedIds.value);
      if (res.code === 200) {
        Message.success(`已删除 ${selectedIds.value.length} 条用例`);
        selectedIds.value = [];
        await loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleBatchMove = async () => {
    if (moveTargetModuleId.value === undefined) {
      Message.warning('请选择目标模块');
      return;
    }
    moveLoading.value = true;
    try {
      const res: any = await batchMoveTestCase(
        selectedIds.value,
        moveTargetModuleId.value
      );
      if (res.code === 200) {
        Message.success(`已移动 ${selectedIds.value.length} 条用例`);
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
  const searchKeyword = ref('');
  const searchModuleId = ref<number | undefined>(undefined);
  const searchLastResult = ref<string | undefined>(undefined);
  const searchStatus = ref<string | undefined>(undefined);

  const pagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: true,
    showPageSize: true,
    pageSizeOptions: [10, 20, 50, 100],
  });

  const moduleTreeRef = ref<any>(null);

  const moduleOptions = ref<any[]>([]);

  const previewVisible = ref(false);
  const previewData = ref<any>({});
  const activeDetailTab = ref<string>('');
  const highlightCommentId = ref<number | undefined>(undefined);
  // 脑图节点「详情」按钮 → 打开用例详情抽屉
  const handleMindmapOpenDetail = (caseId: number) => {
    previewData.value = { id: caseId };
    activeDetailTab.value = '';
    previewVisible.value = true;
  };

  const loadData = async () => {
    if (!projectStore.getProjectId) return;
    loading.value = true;
    try {
      const res: any = await getTestCaseList(
        projectStore.getProjectId,
        searchModuleId.value,
        undefined,
        undefined,
        searchKeyword.value || undefined,
        searchLastResult.value,
        pagination.value.current,
        pagination.value.pageSize,
        undefined,
        searchStatus.value
      );
      if (res.data && res.data.records) {
        testCaseList.value = res.data.records;
        pagination.value.total = res.data.total;
      } else {
        testCaseList.value = res.data || [];
      }
      // 消息通知跳转：高亮并打开指定用例预览
      await checkHighlightCase();
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
    searchModuleId.value = undefined;
    searchLastResult.value = undefined;
    searchStatus.value = undefined;
    pagination.value.current = 1;
    loadData();
  };

  const handlePageChange = (current: number) => {
    selectedIds.value = [];
    pagination.value.current = current;
    loadData();
  };

  const handlePageSizeChange = (pageSize: number) => {
    selectedIds.value = [];
    pagination.value.pageSize = pageSize;
    pagination.value.current = 1;
    loadData();
  };

  const handleTreeSelect = (selectedKeys: any) => {
    searchModuleId.value = selectedKeys[0] === 0 ? undefined : selectedKeys[0];
    pagination.value.current = 1;
    loadData();
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

  const handleAdd = () => {
    router.push({ name: 'TestCaseEdit' });
  };

  const handleEdit = (record: any) => {
    router.push({ name: 'TestCaseEdit', params: { id: record.id } });
  };

  const handlePreview = (record: any, tab?: string, commentId?: number) => {
    previewData.value = { ...record };
    activeDetailTab.value = tab || '';
    highlightCommentId.value = commentId;
    previewVisible.value = true;
  };

  /**
   * 检查是否有消息通知跳转过来的高亮用例
   */
  const checkHighlightCase = async () => {
    const highlightId = route.query.highlight;
    if (!highlightId) return;
    const caseId = Number(highlightId);
    if (isNaN(caseId)) return;

    const tab = route.query.detailTab as string | undefined;
    const commentId = route.query.commentId
      ? Number(route.query.commentId)
      : undefined;
    const record = testCaseList.value.find((item: any) => item.id === caseId);
    if (record) {
      handlePreview(record, tab, commentId);
    } else {
      // 当前页没有，尝试查详情打开预览
      try {
        const res: any = await getTestCaseDetail(caseId);
        if (res.code === 200 && res.data) {
          handlePreview(res.data, tab, commentId);
        }
      } catch (e) {
        console.error('加载高亮用例失败', e);
      }
    }
  };

  // 路由 query 变化时重新检查高亮（同页面消息跳转）
  watch(() => route.query.highlight, (val) => {
    if (val) checkHighlightCase();
  });

  // ===== 列表行内编辑（名称/用例等级/所属模块/便签，点击进入编辑态，回车/失焦保存，Esc 取消） =====
  const canInlineEdit = computed(() => hasPerm('qa:testcase:update'));
  const inlineSaving = ref(false);
  const inlineModuleTree = ref<any[]>([]);
  // 当前正在编辑的单元格（行 id + 字段名）及编辑草稿值
  const editingCell = ref<{ id: number; field: string } | null>(null);
  const draftValue = ref<any>(null);

  const isEditing = (record: any, field: string) =>
    canInlineEdit.value &&
    editingCell.value?.id === record.id &&
    editingCell.value?.field === field;

  const startEdit = (record: any, field: string) => {
    if (!canInlineEdit.value || inlineSaving.value) return;
    if (field === 'tags') {
      draftValue.value = parseTags(record.tags);
    } else if (field === 'moduleId') {
      draftValue.value = record.moduleId || undefined;
    } else {
      draftValue.value = record[field];
    }
    editingCell.value = { id: record.id, field };
  };

  const cancelEdit = () => {
    if (inlineSaving.value) return;
    editingCell.value = null;
    draftValue.value = null;
  };

  // 提交当前编辑中的单元格；值未变化时直接退出编辑态，不调接口
  const commitEdit = async (record: any, field: string) => {
    if (!isEditing(record, field)) return;
    let value = draftValue.value;
    if (field === 'caseName') {
      value = (value || '').trim();
      if (!value) {
        Message.warning('用例名称不能为空');
        cancelEdit();
        return;
      }
      if (value === record.caseName) {
        cancelEdit();
        return;
      }
    } else if (field === 'priority') {
      if (value === record.priority) {
        cancelEdit();
        return;
      }
    } else if (field === 'moduleId') {
      const v = value ?? null;
      if (v === (record.moduleId ?? null)) {
        cancelEdit();
        return;
      }
      value = v;
    } else if (field === 'tags') {
      const joined = Array.isArray(value) ? value.join(',') : value;
      if ((joined || '') === (record.tags || '')) {
        cancelEdit();
        return;
      }
    }
    editingCell.value = null;
    // 等待 DOM 更新完成（v-if 移除输入框），确保自动失焦
    await nextTick();
    await saveInline(record, field, value);
    draftValue.value = null;
  };

  // 下拉弹层关闭时延迟退出编辑态：点击选项的瞬间弹层先关闭，若立即取消会把随后到达的
  // change 事件漏掉（编辑态已清 → commitEdit 直接 return，表现为"修改不生效"）
  const onEditPopupClose = (visible: boolean) => {
    if (visible) return;
    setTimeout(() => cancelEdit(), 150);
  };

  // 名称失焦：内容为空时静默取消（不弹提示），有内容才走保存
  const onNameBlur = (record: any) => {
    if (!isEditing(record, 'caseName')) return;
    if (!(draftValue.value || '').trim()) {
      cancelEdit();
      return;
    }
    commitEdit(record, 'caseName');
  };

  // 便签比较特殊：每加/删一个标签就保存一次，但保持编辑态，失焦/Esc 才关闭
  const commitTags = async (record: any) => {
    if (!isEditing(record, 'tags')) return;
    const joined = Array.isArray(draftValue.value)
      ? draftValue.value.join(',')
      : draftValue.value;
    if ((joined || '') === (record.tags || '')) return;
    await saveInline(record, 'tags', draftValue.value);
  };

  const parseTags = (tags: any): string[] => {
    if (Array.isArray(tags)) return tags;
    if (typeof tags === 'string' && tags.trim())
      return tags.split(',').filter((t: string) => t.trim());
    return [];
  };

  const loadInlineModuleTree = async () => {
    if (inlineModuleTree.value.length > 0 || !projectStore.getProjectId) return;
    try {
      const res: any = await getQaModuleTree(projectStore.getProjectId);
      inlineModuleTree.value = res.data || [];
    } catch (e) {
      console.error(e);
    }
  };

  onMounted(loadInlineModuleTree);

  const formatNow = () => {
    const d = new Date();
    const p = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(
      d.getHours()
    )}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
  };

  // 局部保存：只提交变更字段，成功后本地更新该行（模块同步名称，不整表刷新）
  const saveInline = async (record: any, field: string, value: any) => {
    if (field === 'moduleId' && !value) value = null;
    const payload: any = {
      id: record.id,
      projectId: projectStore.getProjectId,
    };
    payload[field] =
      field === 'tags'
        ? Array.isArray(value)
          ? value.join(',')
          : value
        : value;
    inlineSaving.value = true;
    try {
      const res: any = await updateTestCase(payload);
      if (res.code === 200) {
        Message.success('已保存');
        if (field === 'caseName') record.caseName = value;
        if (field === 'priority') record.priority = value;
        if (field === 'tags') record.tags = payload.tags;
        if (field === 'moduleId') {
          record.moduleId = value;
          record.moduleName = findModuleName(inlineModuleTree.value, value);
        }
        record.updateTime = formatNow();
      } else {
        Message.error(res.msg || '保存失败');
      }
    } catch (e) {
      console.error(e);
    } finally {
      inlineSaving.value = false;
    }
  };

  const findModuleName = (tree: any[], id: any): string => {
    if (!id) return '';
    for (const node of tree) {
      if (node.id === id) return node.moduleName;
      const found = findModuleName(node.children || [], id);
      if (found) return found;
    }
    return '';
  };

  // 模块管理
  const handleModuleChanged = async (type: string, payload?: any) => {
    // 若当前筛选的模块被删除，重置为全部
    if (
      type === 'delete' &&
      payload?.id &&
      searchModuleId.value === payload.id
    ) {
      searchModuleId.value = undefined;
    }
    // 刷新用例表单中的模块下拉
    await loadModuleOptions();
    pagination.value.current = 1;
    await loadData();
  };

  const handleDelete = async (id: number) => {
    try {
      const res: any = await deleteTestCase(id);
      if (res.code === 200) {
        Message.success('删除成功');
        // 后台刷新，不阻塞 UI
        Promise.all([loadData(), moduleTreeRef.value?.loadData()]);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // 复制用例：拉详情 → 去掉 id/编号 → 名称加「副本」→ 置草稿 → 走新建保存（编号由后端重新生成）
  const handleCopy = async (record: any) => {
    try {
      const res: any = await getTestCaseDetail(record.id);
      if (res.code !== 200 || !res.data) {
        Message.error('获取用例详情失败');
        return;
      }
      const src = res.data;
      const copy: any = {
        ...src,
        id: undefined,
        caseCode: undefined,
        caseName: `${src.caseName} 副本`,
        status: 'DRAFT',
        lastResult: undefined,
        lastExecuteTime: undefined,
        createTime: undefined,
        updateTime: undefined,
        createUserId: undefined,
        updateUserId: undefined,
        projectId: projectStore.getProjectId,
      };
      const saveRes: any = await saveTestCase(copy);
      if (saveRes.code === 200) {
        Message.success('复制成功，已生成草稿态副本');
        // 后台刷新，不阻塞 UI
        Promise.all([loadData(), moduleTreeRef.value?.loadData()]);
      } else {
        Message.error(saveRes.msg || '复制失败');
      }
    } catch (e) {
      console.error(e);
      Message.error('复制失败');
    }
  };

  // ===== 操作列：前2个常显（编辑、复制），第3个起放「更多」下拉 =====
  interface CaseOp {
    key: string;
    label: string;
    permission?: string;
    danger?: boolean;
    disabled?: boolean;
    handler: (record: any) => void;
  }
  const confirmDelete = (record: any) => {
    Modal.confirm({
      title: '删除用例',
      content: `确认删除用例「${record.caseName}」吗？该操作不可恢复。`,
      okText: '确认删除',
      okButtonProps: { status: 'danger' },
      cancelText: '再想想',
      hideCancel: false,
      onOk: async () => {
        await handleDelete(record.id);
      },
    });
  };
  const opsFor = (record: any): CaseOp[] => [
    {
      key: 'edit',
      label: '编辑',
      permission: 'qa:testcase:update',
      handler: handleEdit,
    },
    {
      key: 'copy',
      label: '复制',
      permission: 'qa:testcase:create',
      handler: handleCopy,
    },
    {
      key: 'delete',
      label: '删除',
      permission: 'qa:testcase:delete',
      danger: true,
      handler: confirmDelete,
    },
  ];
  // 平铺规则：前2个固定平铺，其余进「更多」
  const visibleOps = (record: any) => opsFor(record).slice(0, 2);
  const overflowOps = (record: any) => opsFor(record).slice(2);
  const guardAndRun = (op: CaseOp, record: any) => {
    if (op.disabled) return;
    op.handler(record);
  };

  const priorityColor = (priority: string) => {
    const map: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue' };
    return map[priority] || 'gray';
  };
  const renderHtml = (
    content: string | undefined,
    defaultText: string = '暂无'
  ) => {
    if (!content || content.trim() === '') return defaultText;
    // 如果内容已经包含 HTML 标签，直接返回
    if (/<[a-z][\s\S]*?>/i.test(content)) {
      return content;
    }
    // 纯文本，把换行符转成 <br>
    return content.replace(/\n/g, '<br>');
  };
  const statusColor = (status: string) => {
    const map: Record<string, string> = {
      DRAFT: 'gray',
      REVIEWING: 'cyan',
      REVIEWED: 'green',
      DEPRECATED: 'red',
    };
    return map[status] || 'gray';
  };
  const statusText = (status: string) => {
    const map: Record<string, string> = {
      DRAFT: '草稿',
      REVIEWING: '评审中',
      REVIEWED: '已评审',
      DEPRECATED: '已废弃',
    };
    return map[status] || status;
  };
  const lastResultColor = (result: string) => {
    const map: Record<string, string> = {
      PASS: 'green',
      FAIL: 'red',
      BLOCK: 'orange',
      NA: 'gray',
    };
    return map[result] || 'gray';
  };
  const lastResultText = (result: string) => {
    const map: Record<string, string> = {
      PASS: '成功',
      FAIL: '失败',
      BLOCK: '阻塞',
      NA: '不适用',
    };
    return map[result] || result;
  };

  onMounted(() => {
    loadData();

    loadModuleOptions();

    // 报告等外部页面跳转：?id= 打开用例详情抽屉（源数据已删除则提示）
    const caseId = Number(route.query.id);
    if (caseId && !isNaN(caseId)) {
      getTestCaseDetail(caseId).then((res: any) => {
        if (res.code === 200 && res.data) {
          previewData.value = { ...res.data };
          activeDetailTab.value = '';
          previewVisible.value = true;
        } else {
          Message.warning('该用例不存在或已删除');
        }
      }).catch(() => Message.warning('该用例不存在或已删除'));
    }
  });

  watch(
    () => projectStore.getProjectId,
    (newId) => {
      if (newId) {
        loadData();
        loadModuleOptions();
      }
    },
    { immediate: true }
  );
</script>

<style scoped lang="less">
  .test-case-page {
    padding: 0 16px 12px;
    display: flex;
    flex-direction: column;
    height: var(--page-container-height, calc(100vh - 60px));
  }

  .main-row {
    display: flex;
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  /* 用例列表：列表/脑图 视图切换容器 */
  .case-view-wrap {
    display: flex;
    flex-direction: column;
    flex: 1;
    min-height: 0;
    height: 100%;
    position: relative;
  }
  .case-view-toggle {
    position: absolute;
    top: 8px;
    right: 10px;
    z-index: 10;
  }
  .case-mindmap-fill {
    flex: 1;
    min-height: 0;
  }

  /* 顶部主 tab（用例 / 用例评审）填满剩余高度 */
  .case-main-tabs {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .case-main-tabs :deep(.arco-tabs-content) {
    flex: 1;
    min-height: 0;
    padding-top: 8px;
    overflow: hidden;
  }

  .case-main-tabs :deep(.arco-tabs-content-list),
  .case-main-tabs :deep(.arco-tabs-pane) {
    height: 100%;
  }

  .case-main-tabs :deep(.arco-tabs-pane) {
    display: flex;
    flex-direction: column;
    min-height: 0;
    overflow: hidden;
  }

  .review-tab-body {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
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
    height: 100%;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
    padding: 12px 0 12px 12px;
  }

  .tree-scroll-wrapper {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  .tree-scroll-wrapper :deep(.arco-scrollbar-container) {
    overflow-x: hidden !important;
    overflow-y: auto !important;
    padding-right: 12px;
  }

  .tree-scroll-wrapper :deep(.arco-tree-node) {
    border-radius: 4px;
  }

  .tree-scroll-wrapper :deep(.arco-tree-node-selected) {
    background-color: var(--color-primary-light-1);
  }

  .tree-scroll-wrapper :deep(.arco-tree-node-selected .arco-tree-node-title) {
    background-color: transparent;
  }

  .tree-scroll-wrapper
    :deep(.arco-tree-node:hover:not(.arco-tree-node-selected)) {
    background-color: var(--color-fill-1);
  }

  .tree-scroll-wrapper :deep(.arco-tree-node-title-text) {
    display: flex !important;
    align-items: center;
    flex: 1 !important;
    min-width: 0 !important;
  }

  /* 更多操作默认隐藏，悬浮时绝对定位到节点标题右侧 */
  .tree-scroll-wrapper :deep(.arco-tree-node) .node-extra-actions {
    position: absolute;
    right: 4px;
    top: 50%;
    transform: translateY(-50%);
    visibility: hidden;
    z-index: 1;
  }

  .tree-scroll-wrapper :deep(.arco-tree-node:hover) .node-extra-actions {
    visibility: visible;
  }

  /* 左侧伸缩分隔条 */
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
    padding-top: 16px;
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

  .list-card .stats-row {
    margin-top: 0;
  }

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

  .batch-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 8px 12px;
    margin-bottom: 8px;
    background: var(--color-fill-1);
    border-radius: 4px;
    border: 1px solid var(--color-border-2);
  }

  .batch-count {
    font-size: 13px;
    color: var(--color-text-2);
    font-weight: 500;
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
  }

  /* 用例详情预览抽屉 */
  .case-preview-drawer :deep(.arco-drawer-body) {
    padding: 0;
    overflow: hidden;
  }

  .case-preview-drawer :deep(.arco-drawer-footer) {
    padding: 12px 20px;
    border-top: 1px solid var(--color-border-2);
    text-align: right;
  }

  .drawer-body {
    height: 100%;
    overflow-y: auto;
    padding: 20px;
  }

  .preview-header {
    margin-bottom: 16px;
  }

  .preview-case-name {
    margin: 0 0 10px;
    font-size: 18px;
    font-weight: 600;
    color: var(--color-text-1);
    line-height: 1.4;
  }

  .preview-badges {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }

  .preview-card {
    margin-bottom: 16px;

    :deep(.arco-card-header) {
      padding: 12px 16px;
      border-bottom: 1px solid var(--color-border-2);
    }

    :deep(.arco-card-body) {
      padding: 16px;
    }
  }

  .meta-card :deep(.arco-descriptions-item) {
    padding-bottom: 8px;
  }

  .meta-card :deep(.arco-descriptions-item-label) {
    color: var(--color-text-3);
    font-size: 12px;
  }

  .meta-card :deep(.arco-descriptions-item-value) {
    color: var(--color-text-1);
    font-size: 13px;
    font-weight: 500;
  }

  .precondition-card :deep(.arco-card-body) {
    background: #f7faff;
  }

  .preview-section {
    margin-bottom: 8px;
  }

  .preview-section-title {
    font-size: 14px;
    font-weight: 600;
    color: var(--color-text-1);
    margin-bottom: 12px;
    padding-left: 8px;
    border-left: 3px solid rgb(var(--primary-6));
  }

  .steps-timeline {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }

  .step-timeline-item {
    display: flex;
    gap: 12px;
  }

  .step-timeline-marker {
    display: flex;
    flex-direction: column;
    align-items: center;
    flex-shrink: 0;
    width: 28px;
    padding-top: 4px;
  }

  .step-number-badge {
    width: 28px;
    height: 28px;
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgb(var(--primary-6));
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    flex-shrink: 0;
  }

  .step-timeline-line {
    flex: 1;
    width: 2px;
    min-height: 24px;
    background: var(--color-border-2);
    margin-top: 8px;
  }

  .step-content-card {
    flex: 1;
    margin-bottom: 0;
  }

  .step-subtitle {
    font-size: 12px;
    font-weight: 600;
    color: var(--color-text-2);
    margin-bottom: 8px;
    padding-left: 8px;
    border-left: 2px solid rgb(var(--primary-6));
  }

  .step-subtitle.expected {
    border-left-color: #00b42a;
  }

  @media (max-width: 767px) {
    .step-timeline-item {
      flex-direction: column;
    }

    .step-timeline-marker {
      flex-direction: row;
      align-items: center;
      width: 100%;
      gap: 8px;
    }

    .step-timeline-line {
      display: none;
    }
  }

  .table-skeleton {
    padding: 16px;
  }

  .text-ellipsis {
    display: inline-block;
    max-width: 100%;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }

  .case-name-link {
    color: rgb(var(--primary-6));
    cursor: pointer;
  }

  .case-name-link:hover {
    color: rgb(var(--link-color-hover));
  }

  .adv-toggle {
    align-self: center;
    white-space: nowrap;
    font-size: 13px;
  }

  .inline-editable {
    cursor: pointer;
    border-radius: 3px;
    transition: background 0.15s ease, box-shadow 0.15s ease;
    padding: 2px 4px;
    margin: -2px -4px;
  }

  /* 行 hover 时，所有可编辑字段同时显示背景提示 */
  :deep(.arco-table-tr):hover .inline-editable {
    background: var(--color-fill-2);
  }

  /* 字段自身 hover 时，蓝色高亮 */
  .inline-editable:hover {
    background: rgb(var(--arcoblue-1));
    box-shadow: 0 0 0 1px rgb(var(--arcoblue-3));
  }

  /* 行内编辑单元格：撑满宽度，hover 提示可编辑 */
  .cell-display {
    display: block;
    width: 100%;
    min-height: 22px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .adv-panel {
    background: var(--color-fill-1);
    border-radius: 4px;
    padding: 10px 12px 2px;
    margin-bottom: 12px;
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
  .stat-pass {
    background: #e8ffea;
    color: #00b42a;
  }
  .stat-pass:hover {
    border-color: #00b42a;
  }
  .stat-fail {
    background: #ffece8;
    color: #f53f3f;
  }
  .stat-fail:hover {
    border-color: #f53f3f;
  }
  .stat-unexec {
    background: #f2f3f5;
    color: #86909c;
  }
  .stat-unexec:hover {
    border-color: #86909c;
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
  .step-card {
    border: 1px solid var(--color-border-2);
    border-radius: 8px;
    padding: 12px 16px;
    background: var(--color-fill-1);
  }
  .step-card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }

  .drawer-table-wrapper {
    height: calc(100vh - 180px);
    overflow: auto;
  }

  .step-number {
    color: rgb(var(--primary-6));
    font-size: 13px;
    font-weight: 600;
  }

  /* 预览抽屉 tabs */
  .preview-tabs :deep(.arco-tabs-content) {
    padding-top: 4px;
  }

  .reject-alert {
    margin-bottom: 16px;
  }

  .reject-comment {
    margin-top: 4px;
    color: var(--color-text-2);
  }

  .oplog-timeline {
    margin-top: 8px;
    padding-left: 4px;
  }

  .oplog-head {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-wrap: wrap;
  }

  .oplog-operator {
    font-weight: 600;
    color: var(--color-text-1);
  }

  .oplog-time {
    color: var(--color-text-3);
    font-size: 12px;
  }

  .oplog-change {
    margin-top: 4px;
    color: var(--color-text-2);
    font-size: 13px;
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 4px;
  }

  .oplog-field {
    font-weight: 500;
  }

  .oplog-old {
    color: var(--color-text-3);
    text-decoration: line-through;
  }

  .oplog-new {
    color: rgb(var(--primary-6));
    font-weight: 500;
  }

  .oplog-arrow {
    color: var(--color-text-3);
    font-size: 12px;
  }

  .oplog-comment {
    margin-top: 6px;
    padding: 8px 12px;
    background: var(--color-fill-2);
    border-left: 3px solid var(--color-border-3);
    border-radius: 4px;
    color: var(--color-text-2);
    font-size: 13px;
    white-space: pre-wrap;
  }
</style>
