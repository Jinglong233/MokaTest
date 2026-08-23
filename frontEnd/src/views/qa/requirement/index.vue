<template>
  <div class="requirement-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.requirement']"/>

    <div class="main-row">
      <!-- 左栏：模块树（复用 ModuleTreeManager，支持新增/编辑/删除/拖拽排序） -->
      <div class="col side-col" :style="sideColStyle">
        <a-card class="tree-card" size="small" :bordered="false">
          <div class="req-tree-panel">
            <ModuleTreeManager
              ref="moduleTreeRef"
              :show-search="true"
              :show-virtual-root="true"
              :selected-keys="[selectedModuleKey]"
              root-name="全部需求"
              default-expand-all
              @select="onModuleTreeSelect"
              @changed="handleModuleChanged"
            >
              <template #search-extra>
                <a-button v-permission="QA_REQUIREMENT_CREATE" type="primary" size="small" @click="handleAdd">
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
      <!-- 右侧：需求列表 -->
      <div class="col content-col">
    <a-card class="requirement-card general-card" :bordered="false">
      <!-- 主操作行 -->
      <div class="filter-toolbar">
        <div class="filter-left">
          <a-button
            v-permission="QA_REQUIREMENT_DELETE"
            v-if="selectedKeys.length > 0"
            status="danger"
            size="large"
            @click="handleBatchDelete"
          >
            <template #icon><IconDelete /></template>
            批量删除({{ selectedKeys.length }})
          </a-button>
        </div>
      </div>
      <!-- 筛选区 -->
      <a-row :wrap="true" :gutter="[16, 12]" align="center" class="filter-grid" style="margin: 8px 0 16px;">
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">需求标题</label>
            <a-input v-model="searchKeyword" placeholder="请输入需求标题" allow-clear />
          </div>
        </a-col>
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">状态</label>
            <a-select v-model="searchStatus" placeholder="请选择" allow-clear>
              <a-option value="DRAFT">草稿</a-option>
              <a-option value="REVIEWING">评审中</a-option>
              <a-option value="CONFIRMED">已确认</a-option>
              <a-option value="DEVELOPING">开发中</a-option>
              <a-option value="TESTING">测试中</a-option>
              <a-option value="RELEASED">已上线</a-option>
              <a-option value="CLOSED">已关闭</a-option>
            </a-select>
          </div>
        </a-col>
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">所属模块</label>
            <a-select v-model="searchModuleId" placeholder="请选择" allow-clear>
              <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
            </a-select>
          </div>
        </a-col>
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">需求类型</label>
            <a-select v-model="searchReqType" placeholder="请选择" allow-clear>
              <a-option value="FEATURE">功能需求</a-option>
              <a-option value="BUGFIX">缺陷修复</a-option>
              <a-option value="OPTIMIZE">优化</a-option>
            <a-option value="TECH_DEBT">技术债务</a-option>
          </a-select>
          </div>
        </a-col>
        <a-col flex="280px">
          <div class="filter-field">
            <label class="filter-label">来源</label>
            <a-select v-model="searchSource" placeholder="请选择" allow-clear>
              <a-option value="CLIENT">客户反馈</a-option>
              <a-option value="INTERNAL">内部规划</a-option>
              <a-option value="COMPETITOR">竞品分析</a-option>
              <a-option value="ONLINE">线上问题</a-option>
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
            :data="requirementList"
            :columns="columns"
            :loading="loading"
            :pagination="pagination"
            :bordered="{ wrapper: true, cell: false }"
            row-key="id"
            :sticky-header="true"
            :scroll="{ x: 1160 }"
            :row-selection="{ type: 'checkbox', onChange: handleSelectChange }"
            @page-change="handlePageChange"
            @page-size-change="handlePageSizeChange"
        >
          <template #reqCode="{ record }">
            <span class="req-title-link" @click="handlePreview(record)">{{ record.reqCode }}</span>
          </template>
          <template #title="{ record }">
            <a-tooltip :content="record.title">
              <span class="req-title-link" @click="handlePreview(record)">{{ record.title }}</span>
            </a-tooltip>
          </template>
          <template #reqType="{ record }">
            <a-tag size="small">{{ reqTypeText(record.reqType) }}</a-tag>
          </template>
          <template #priority="{ record }">
            <span class="priority-dot" :class="record.priority?.toLowerCase()">
              <span class="dot"></span>
              <span>{{ record.priority }}</span>
            </span>
          </template>
          <template #status="{ record }">
            <a-tag :color="statusColor(record.status)" size="small" class="status-pill">{{ statusText(record.status) }}</a-tag>
          </template>
          <template #tags="{ record }">
            <span
              v-if="
                !record.tags ||
                (Array.isArray(record.tags) && record.tags.length === 0) ||
                (typeof record.tags === 'string' && record.tags.trim() === '')
              "
              style="color: #86909c;"
            >-</span>
            <a-tooltip
              v-else
              :content="
                typeof record.tags === 'string' ? record.tags : record.tags.join(', ')
              "
            >
              <span>
                {{ typeof record.tags === 'string' ? record.tags : record.tags.join(', ') }}
              </span>
            </a-tooltip>
          </template>
          <template #caseCount="{ record }">
            <a-tag color="blue" size="small">{{ record.caseCount || 0 }}</a-tag>
          </template>
          <template #bugCount="{ record }">
            <a-tag :color="record.bugCount > 0 ? 'red' : 'green'" size="small">{{ record.bugCount || 0 }}</a-tag>
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
    </a-card>
      </div>
    </div>

    <!-- 关联已有需求弹窗（子需求 tab） -->
    <a-modal
        v-model:visible="linkChildVisible"
        title="关联已有需求"
        :width="520"
        :on-before-ok="handleLinkChildOk"
        @cancel="linkChildVisible = false"
        :mask-closable="false"
    >
      <a-select
          v-model="linkChildId"
          placeholder="选择要挂到当前需求下的需求"
          allow-search
          allow-clear
          style="width: 100%"
      >
        <a-option v-for="r in linkChildCandidates" :key="r.id" :value="r.id">
          {{ r.reqCode }} {{ r.title }}
        </a-option>
      </a-select>
      <p style="color: var(--color-text-3); font-size: 12px; margin-top: 8px;">已排除当前需求及其所有子孙需求，避免循环引用</p>
    </a-modal>

    <!-- 新增/编辑弹窗（仅预览抽屉中使用） -->
    <a-modal
        class="qa-edit-modal"
        v-model:visible="modalVisible"
        :title="modalTitle"
        :width="modalWidth"
        @ok="handleSave"
        @cancel="modalVisible = false"
        :mask-closable="false"
    >
      <div class="modal-scroll-body">
        <RequirementForm
            ref="requirementFormRef"
            :initial-data="formData"
        />
      </div>
    </a-modal>

    <!-- AI 生成用例弹窗（聊天式） -->
    <AiGenerateChatModal
        ref="aiGenerateModalRef"
        scene="case"
        :entity-id="aiTargetRequirement?.id || 0"
        @adopted="handleAiAdopted"
    />

    <!-- 预览抽屉（多 tab：基本信息 / 关联用例 / 关联BUG / 子需求） -->
    <a-drawer
        v-model:visible="previewVisible"
        width="860px"
        :closable="false"
        :mask-closable="true"
        :esc-to-close="true"
        :footer="true"
        class="preview-drawer"
        :body-style="{ padding: 0, overflow: 'hidden' }"
    >
      <template #title>
        <div class="preview-header">
          <div class="preview-req-code">{{ previewData.reqCode || '-' }}</div>
          <div class="preview-header-bottom">
            <span class="preview-title-text">{{ previewData.title }}</span>
            <a-tag v-if="previewData.status" :color="statusColor(previewData.status)" size="small" class="status-pill">
              {{ statusText(previewData.status) }}
            </a-tag>
          </div>
        </div>
      </template>

      <div class="preview-body">
        <a-tabs v-model:active-key="previewTabKey" type="line" class="preview-tabs" @change="handlePreviewTabChange">
          <!-- ========== 基本信息 ========== -->
          <a-tab-pane key="info" title="基本信息">
            <!-- 基础属性 -->
            <a-card class="preview-section" :bordered="false" size="small">
              <a-descriptions :bordered="true" :data="previewDescriptions" layout="horizontal" :column="2" :label-style="{ fontWeight: 'bold', width: '90px' }" :value-style="{ textAlign: 'right' }" />
            </a-card>

            <!-- 需求描述 -->
            <a-card class="preview-section" title="需求描述" :bordered="false" size="small">
              <div class="preview-description rich-text-preview" v-html="renderHtml(previewData.description, '暂无描述')"></div>
            </a-card>
          </a-tab-pane>

          <!-- ========== 关联用例 ========== -->
          <a-tab-pane key="cases">
            <template #title>关联用例 <span class="tab-count-badge">{{ previewData.caseCount || 0 }}</span></template>
            <a-row justify="space-between" style="margin-bottom: 12px;">
              <a-button v-permission="QA_TEST_CASE_CREATE" type="primary" size="small" @click="handleAddCase">
                <template #icon><icon-plus /></template>
                新增用例
              </a-button>
            </a-row>
            <a-table
                :data="drawerCaseList"
                :columns="drawerCaseColumns"
                :loading="drawerLoading"
                :pagination="drawerPagination"
                row-key="id"
                :scroll="{ x: 780 }"
                @page-change="handleDrawerPageChange"
                @page-size-change="handleDrawerPageSizeChange"
            >
              <template #caseType="{ record }">
                <a-tag>{{ caseTypeText(record.caseType) }}</a-tag>
              </template>
              <template #drawerCasePriority="{ record }">
                <a-tag :color="priorityColor(record.priority)">{{ record.priority }}</a-tag>
              </template>
              <template #drawerCaseBugCount="{ record }">
                <a-tag :color="(record.bugCount || 0) > 0 ? 'red' : 'green'">{{ record.bugCount || 0 }}</a-tag>
              </template>
              <template #drawerCaseActions="{ record }">
                <a-popconfirm
                    content="取消该用例与当前需求的关联？（用例本身保留）"
                    type="warning"
                    @ok="handleUnbindCase(record.id)"
                >
                  <a-button v-permission="QA_REQUIREMENT_UPDATE" type="text" size="small" status="warning">取消关联</a-button>
                </a-popconfirm>
              </template>
              <template #empty>
                <a-empty description="暂无关联用例"/>
              </template>
            </a-table>
          </a-tab-pane>

          <!-- ========== 关联BUG ========== -->
          <a-tab-pane key="bugs">
            <template #title>关联BUG <span class="tab-count-badge">{{ previewData.bugCount || 0 }}</span></template>
            <a-row justify="space-between" style="margin-bottom: 12px;">
              <a-button v-permission="QA_BUG_CREATE" type="primary" size="small" @click="handleAddBug">
                <template #icon><icon-plus /></template>
                新增BUG
              </a-button>
            </a-row>
            <a-table
                :data="drawerBugList"
                :columns="drawerBugColumns"
                :loading="bugDrawerLoading"
                :pagination="bugDrawerPagination"
                row-key="id"
                :scroll="{ x: 680 }"
                @page-change="handleBugDrawerPageChange"
                @page-size-change="handleBugDrawerPageSizeChange"
            >
              <template #bugCode="{ record }">
                <a-tooltip :content="record.bugCode" mini>
                  <span class="text-ellipsis-inline">{{ record.bugCode }}</span>
                </a-tooltip>
              </template>
              <template #bugTitle="{ record }">
                <a-tooltip :content="record.title" mini>
                  <span class="text-ellipsis-inline">{{ record.title }}</span>
                </a-tooltip>
              </template>
              <template #severity="{ record }">
                <a-tag :color="severityColor(record.severity)" size="small">{{ severityText(record.severity) }}</a-tag>
              </template>
              <template #bugStatus="{ record }">
                <a-tag :color="bugStatusColor(record.status)" size="small">{{ bugStatusText(record.status) }}</a-tag>
              </template>
              <template #planName="{ record }">
                <a-tooltip :content="record.planName || '-'" mini>
                  <span v-if="record.planName" class="text-ellipsis-inline">
                    <a-tag color="arcoblue" size="small">{{ record.planName }}</a-tag>
                  </span>
                  <span v-else style="color: #86909c;">-</span>
                </a-tooltip>
              </template>
              <template #bugCaseName="{ record }">
                <a-tooltip :content="record.caseName || '-'" mini>
                  <span v-if="record.caseName" class="text-ellipsis-inline" style="color: rgb(var(--primary-6)); cursor: pointer;" @click="handlePreviewCaseFromBug(record.testCaseId)">{{ record.caseName }}</span>
                  <span v-else style="color: #86909c;">-</span>
                </a-tooltip>
              </template>
              <template #drawerBugActions="{ record }">
                <a-popconfirm
                    content="取消该BUG与当前需求的关联？（BUG本身保留）"
                    type="warning"
                    @ok="handleUnbindBug(record.id)"
                >
                  <a-button v-permission="QA_REQUIREMENT_UPDATE" type="text" size="small" status="warning">取消关联</a-button>
                </a-popconfirm>
              </template>
              <template #empty>
                <a-empty description="暂无关联BUG"/>
              </template>
            </a-table>
          </a-tab-pane>

          <!-- ========== 子需求 ========== -->
          <a-tab-pane key="children">
            <template #title>子需求 <span class="tab-count-badge">{{ childRequirements.length }}</span></template>
            <a-row justify="space-between" style="margin-bottom: 12px;">
              <a-space>
                <a-button v-permission="QA_REQUIREMENT_CREATE" type="primary" size="small" @click="handleAddChildReq">
                  <template #icon><icon-plus /></template>
                  新增子需求
                </a-button>
                <a-button v-permission="QA_REQUIREMENT_UPDATE" size="small" @click="openLinkChild">关联已有需求</a-button>
              </a-space>
            </a-row>
            <a-table :data="childRequirements" :columns="subReqColumns" row-key="id" :pagination="false">
              <template #subReqCode="{ record }">
                <span class="req-title-link" @click="handlePreview(record)">{{ record.reqCode }}</span>
              </template>
              <template #subReqTitle="{ record }">
                <a-tooltip :content="record.title" mini>
                  <span class="req-title-link" @click="handlePreview(record)">{{ record.title }}</span>
                </a-tooltip>
              </template>
              <template #subReqPriority="{ record }">
                <span class="priority-dot" :class="record.priority?.toLowerCase()">
                  <span class="dot"></span>
                  <span>{{ record.priority }}</span>
                </span>
              </template>
              <template #subReqStatus="{ record }">
                <a-tag :color="statusColor(record.status)" size="small" class="status-pill">{{ statusText(record.status) }}</a-tag>
              </template>
              <template #subReqOwner="{ record }">{{ record.ownerName || '-' }}</template>
              <template #empty>
                <a-empty description="暂无子需求"/>
              </template>
            </a-table>
          </a-tab-pane>
        </a-tabs>

        <!-- 评论与操作日志：后端接口未落地（二期），暂时隐藏，避免"发表成功但不落库"的假反馈 -->
        <a-card v-if="false" class="preview-section preview-tabs-section" :bordered="false" size="small">
          <a-tabs type="line">
            <a-tab-pane key="comments" title="评论">
              <a-empty description="暂无评论" />
            </a-tab-pane>
            <a-tab-pane key="logs" title="操作日志">
              <a-empty description="暂无操作日志" />
            </a-tab-pane>
          </a-tabs>
        </a-card>
      </div>

      <template #footer>
        <div class="preview-footer">
          <a-space>
            <a-button @click="previewVisible = false">关闭</a-button>
            <a-button v-permission="QA_TEST_CASE_CREATE" @click="handleAiGenerateFromPreview"><icon-robot /> AI 生成用例</a-button>
            <a-button v-permission="QA_REQUIREMENT_UPDATE" type="primary" @click="handleEditFromPreview">编辑需求</a-button>
          </a-space>
        </div>
      </template>
    </a-drawer>
    <!-- 用例新增/编辑抽屉（复用共享 TestCaseForm，同评审详情页） -->
    <a-drawer
        v-model:visible="caseModalVisible"
        :title="caseModalTitle"
        :width="1200"
        :mask-closable="true"
        @cancel="caseModalVisible = false"
    >
      <div class="form-drawer-body">
        <TestCaseForm ref="caseFormRef" :initial-data="caseFormData" />
      </div>
      <template #footer>
        <a-space>
          <a-button @click="caseModalVisible = false">取消</a-button>
          <a-button type="primary" :loading="caseSaving" @click="handleSaveCase">保存</a-button>
        </a-space>
      </template>
    </a-drawer>

    <!-- BUG 新增/编辑抽屉（复用共享 BugForm，同计划脑图） -->
    <a-drawer
        v-model:visible="bugModalVisible"
        :title="bugModalTitle"
        :width="1000"
        :mask-closable="true"
        @cancel="bugModalVisible = false"
    >
      <div class="form-drawer-body">
        <BugForm ref="bugFormRef" :initial-data="bugFormData" />
      </div>
      <template #footer>
        <a-space>
          <a-button @click="bugModalVisible = false">取消</a-button>
          <a-button type="primary" :loading="bugSaving" @click="handleSaveBug">保存</a-button>
        </a-space>
      </template>
    </a-drawer>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'Requirement' };
</script>

<script setup lang="ts">
import {ref, computed, onMounted, onBeforeUnmount, watch, nextTick} from 'vue';
import {useI18n} from 'vue-i18n';
import {useRouter} from 'vue-router';
import {Message, Modal} from '@arco-design/web-vue';
import {IconSearch, IconPlus, IconDelete, IconRight, IconDown} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import RequirementForm from './components/RequirementForm.vue';
import TestCaseForm from '../testCase/components/TestCaseForm.vue';
import BugForm from '../bug/components/BugForm.vue';
import AiGenerateChatModal from '@/components/ai-generate/AiGenerateChatModal.vue';
import ModuleTreeManager from '@/views/qa/components/ModuleTreeManager.vue';
import {useProjectStore} from '@/store';
import useProjectConfigStore from '@/store/modules/projectConfig';
import {useRoute} from 'vue-router';
import {
  QA_REQUIREMENT_CREATE,
  QA_REQUIREMENT_UPDATE,
  QA_REQUIREMENT_DELETE,
  QA_REQUIREMENT_TRANSITION,
  QA_TEST_CASE_CREATE,
  QA_TEST_CASE_UPDATE,
  QA_TEST_CASE_DELETE,
  QA_BUG_CREATE,
  QA_BUG_UPDATE,
  QA_BUG_DELETE,
} from '@/constants/permissions';
import {getRequirementList, getRequirementStats, getRequirementDetail, updateRequirement, deleteRequirement, batchDeleteRequirement, getTestCaseList, getTestCaseDetail, getQaModuleList, getBugList, unbindRequirementCase, unbindRequirementBug} from '@/api/MyApi/qa';
import {getUserListByPage} from '@/api/MyApi/user';

const {t} = useI18n();
const projectStore = useProjectStore();
const projectConfigStore = useProjectConfigStore();
const fieldVis = (key: string) => projectConfigStore.isFieldVisible('requirement', key);
const route = useRoute();
const router = useRouter();

// 用于渲染 dropdownRender 插槽中的 VNode
const VNodeRenderer = {
  props: ['vnodes'],
  render(this: { vnodes: any }) {
    return this.vnodes;
  }
};

const loading = ref(false);
const requirementList = ref<any[]>([]);
const searchKeyword = ref('');
const searchStatus = ref('');
const userOptions = ref<any[]>([]);
const userPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });
const searchModuleId = ref('');
const searchReqType = ref('');
const moduleOptions = ref<any[]>([]);
const searchSource = ref('');
const pagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });
const statsData = ref({ total: 0, developing: 0, testing: 0, closed: 0 });

const modalVisible = ref(false);
const modalTitle = ref('');

// 编辑弹窗宽度跟随浏览器窗口自适应：最宽 1200px，窄屏留 3vw 边距
const windowWidth = ref(window.innerWidth);
const handleWindowResize = () => {
  windowWidth.value = window.innerWidth;
};
const modalWidth = computed(() => Math.min(1400, Math.max(360, windowWidth.value * 0.96)));
onMounted(() => window.addEventListener('resize', handleWindowResize));
onBeforeUnmount(() => window.removeEventListener('resize', handleWindowResize));

// ===== 左侧模块树：可拖拽伸缩 + 拖拽隐藏（与用例列表/计划详情一致） =====
const SIDEBAR_DEFAULT_WIDTH = 240;
const SIDEBAR_MIN_WIDTH = 200;
const SIDEBAR_MAX_WIDTH = 480;
const SIDEBAR_COLLAPSE_THRESHOLD = 20;

const sidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);
// 窄屏（<992px）默认折叠侧栏，把空间让给列表
const isSidebarCollapsed = ref(window.innerWidth < 992);
const lastSidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);

// 侧栏最大宽度跟随浏览器窗口：不超过窗口的 40%
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

// 窗口尺寸变化：收窄侧栏不超过动态上限；窄屏自动折叠
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

// ===== 模块树（复用 ModuleTreeManager 组件，支持新增/编辑/删除/拖拽排序） =====
const moduleTreeRef = ref();
const selectedModuleKey = ref<number>(0);

// 树选中 → 同步模块筛选下拉并查询；选中 0（全部）清除模块筛选
function onModuleTreeSelect(keys: any[]) {
  const k = keys && keys.length ? Number(keys[0]) : 0;
  selectedModuleKey.value = k;
  searchModuleId.value = k ? String(k) : '';
  handleSearch();
}

// 下拉筛选/重置 → 同步树选中
watch(searchModuleId, (v) => {
  selectedModuleKey.value = v ? Number(v) : 0;
});

// 模块增删改后 → 刷新下拉模块选项
function handleModuleChanged() {
  loadModuleOptions();
}

// 全量需求缓存：用于预览抽屉「子需求」tab
// ponytail: 每页 9999 条全量拉取，需求量大时改后端按模块聚合接口
const loadAllRequirements = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getRequirementList(projectStore.getProjectId, undefined, undefined, undefined, undefined, undefined, 1, 9999);
    allRequirements.value = res.data?.records || [];
  } catch (e) {
    console.error(e);
  }
};

const allRequirements = ref<any[]>([]);
const childRequirements = computed(() =>
  allRequirements.value.filter((r: any) => r.parentId === previewData.value?.id)
);

// ===== 子需求 tab：新增 / 关联已有 =====
// 新增子需求：打开需求编辑弹窗，父需求/模块自动带出当前需求
const handleAddChildReq = () => {
  modalTitle.value = '新增子需求';
  formData.value = {
    projectId: projectStore.getProjectId,
    parentId: previewData.value?.id,
    moduleId: previewData.value?.moduleId ?? undefined,
  };
  modalVisible.value = true;
};

// 关联已有需求：候选 = 全量需求中排除自身/子孙/已是子级的记录（防循环引用）
const linkChildVisible = ref(false);
const linkChildId = ref<number | null>(null);

const descendantIds = computed(() => {
  const rootId = previewData.value?.id;
  const set = new Set<number>(rootId ? [rootId] : []);
  let grew = true;
  while (grew) {
    grew = false;
    for (const r of allRequirements.value) {
      if (r.parentId && set.has(r.parentId) && !set.has(r.id)) {
        set.add(r.id);
        grew = true;
      }
    }
  }
  return set;
});

const linkChildCandidates = computed(() =>
  allRequirements.value.filter((r: any) =>
    !descendantIds.value.has(r.id) && r.parentId !== previewData.value?.id
  )
);

const openLinkChild = () => {
  linkChildId.value = null;
  linkChildVisible.value = true;
};

const handleLinkChildOk = async () => {
  if (!linkChildId.value) {
    Message.warning('请选择要关联的需求');
    return false;
  }
  try {
    const res: any = await updateRequirement({
      id: linkChildId.value,
      parentId: previewData.value.id,
      projectId: projectStore.getProjectId,
    });
    if (res.code === 200) {
      Message.success('关联成功');
      await loadData(); // loadAllRequirements 随之刷新 allRequirements，子需求列表自动更新
      return true;
    }
    Message.error(res.msg || '关联失败');
  } catch (e) {
    console.error(e);
    Message.error('关联失败');
  }
  return false;
};
const formData = ref<any>({});
const requirementFormRef = ref<any>(null);
const selectedKeys = ref<number[]>([]);

const drawerCaseList = ref<any[]>([]);
const drawerLoading = ref(false);
const drawerPagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });
const currentRequirementId = ref<number | null>(null);
const caseModalVisible = ref(false);
const caseModalTitle = ref('');
const caseFormData = ref<any>({});
const caseFormRef = ref<any>(null);
const caseSaving = ref(false);

// BUG tab
const drawerBugList = ref<any[]>([]);
const bugDrawerLoading = ref(false);
const bugDrawerPagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });
const currentRequirementIdForBug = ref<number | null>(null);
const currentRequirementRecord = ref<any>({});

// BUG 弹窗
const bugModalVisible = ref(false);
const bugModalTitle = ref('');
const bugFormData = ref<any>({});
const bugFormRef = ref<any>(null);
const bugSaving = ref(false);

// 富文本图片 fileId 提取



const previewVisible = ref(false);
const previewData = ref<any>({});
// AI 生成用例
const aiGenerateModalRef = ref<any>(null);
const aiTargetRequirement = ref<any>(null);

function handleAiGenerate(record: any) {
  aiTargetRequirement.value = record;
  nextTick(() => aiGenerateModalRef.value?.open());
}

function handleAiGenerateFromPreview() {
  aiTargetRequirement.value = previewData.value;
  nextTick(() => aiGenerateModalRef.value?.open());
}

function handleAiAdopted() {
  // 入库成功后刷新需求列表（关联用例计数变化）
  loadData();
}
const previewTabKey = ref('comments');
const previewComments = ref<any[]>([]);
const previewCommentInput = ref('');
const previewLogs = ref<any[]>([]);
const commentInputFocused = ref(false);
// 预览抽屉基础属性（按项目字段显隐配置过滤）
const previewDescriptions = computed(() => {
  const items = [
    { key: 'moduleId', label: '所属模块', value: previewData.value.moduleName || '-' },
    { key: 'reqType', label: '需求类型', value: reqTypeText(previewData.value.reqType) },
    { key: 'source', label: '来源', value: sourceText(previewData.value.source) },
    { key: 'priority', label: '优先级', value: previewData.value.priority },
    { key: 'version', label: '目标版本', value: previewData.value.version || '-' },
    { key: 'expectReleaseTime', label: '期望上线时间', value: previewData.value.expectReleaseTime || '-' },
    { key: 'ownerId', label: '负责人', value: previewData.value.ownerName || '-' },
    { key: 'createUser', label: '创建人', value: previewData.value.createUserName || '-' }
  ];
  const locked = ['priority', 'createUser'];
  return items
    .filter((item) => locked.includes(item.key) || fieldVis(item.key))
    .map(({ label, value }) => ({ label, value }));
});

const loadData = async () => {
  if (!projectStore.getProjectId) return;
  loading.value = true;
  try {
    const res: any = await getRequirementList(
        projectStore.getProjectId,
        searchKeyword.value || undefined,
        searchStatus.value || undefined,
        searchModuleId.value ? Number(searchModuleId.value) : undefined,
        searchReqType.value || undefined,
        searchSource.value || undefined,
        pagination.value.current,
        pagination.value.pageSize
    );
    requirementList.value = res.data.records || [];
    pagination.value.total = res.data.total || 0;
    loadStats();
    loadAllRequirements(); // 刷新全量需求缓存（子需求 tab 复用）
    // 消息通知跳转：高亮并打开指定需求预览
    await checkHighlightRequirement();
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

// 统计卡片：后端全项目口径聚合，不再只数当前页
const loadStats = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getRequirementStats(projectStore.getProjectId);
    if (res.code === 200 && res.data) {
      statsData.value = { total: 0, developing: 0, testing: 0, closed: 0, ...res.data };
    }
  } catch (e) {
    console.error(e);
  }
};

const handleSearch = () => {
  pagination.value.current = 1;
  loadData();
};
const handleReset = () => {
  searchKeyword.value = '';
  searchStatus.value = '';
  searchModuleId.value = '';
  searchReqType.value = '';
  searchSource.value = '';
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

const handlePageChange = (page: number) => {
  pagination.value.current = page;
  loadData();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.value.current = 1;
  pagination.value.pageSize = pageSize;
  loadData();
};

const handleAdd = () => {
  router.push({name: 'RequirementEdit'});
};

const handleEdit = (record: any) => {
  router.push({name: 'RequirementEdit', params: {id: record.id}});
};

// 预览抽屉里继续用弹窗编辑
const handleEditInModal = (record: any) => {
  modalTitle.value = t('qa.requirement.edit');
  formData.value = {...record};
  if (formData.value.tags && typeof formData.value.tags === 'string') {
    formData.value.tags = formData.value.tags.split(',').filter((t: string) => t.trim());
  }
  modalVisible.value = true;
};

const handlePreview = (record: any) => {
  previewData.value = {...record};
  previewTabKey.value = 'info';
  previewCommentInput.value = '';
  // 加载评论和操作日志（当前为模拟数据，后续可对接后端接口）
  loadPreviewComments(record.id);
  loadPreviewLogs(record.id);
  previewVisible.value = true;
};

// 预览抽屉 tab 切换：按需加载关联数据
const handlePreviewTabChange = (key: any) => {
  const id = previewData.value?.id;
  if (!id) return;
  if (key === 'cases') {
    currentRequirementId.value = id;
    currentRequirementRecord.value = previewData.value;
    drawerPagination.value.current = 1;
    loadDrawerCases();
  } else if (key === 'bugs') {
    currentRequirementIdForBug.value = id;
    currentRequirementRecord.value = previewData.value;
    bugDrawerPagination.value.current = 1;
    loadDrawerBugs();
  }
};

/**
 * 检查是否有消息通知跳转过来的高亮需求
 */
const checkHighlightRequirement = async () => {
  const highlightId = route.query.highlight;
  if (!highlightId) return;
  const requirementId = Number(highlightId);
  if (isNaN(requirementId)) return;

  const record = requirementList.value.find((item) => item.id === requirementId);
  if (record) {
    handlePreview(record);
  } else {
    // 当前页没有，尝试查详情打开预览
    try {
      const res: any = await getRequirementDetail(requirementId);
      if (res.code === 200 && res.data) {
        handlePreview(res.data);
      }
    } catch (e) {
      console.error('加载高亮需求失败', e);
    }
  }
};

const loadPreviewComments = async (requirementId: number) => {
  // TODO: 对接后端 /api/qa/requirement/comment/list?requirementId=xxx
  previewComments.value = [];
};

const loadPreviewLogs = async (requirementId: number) => {
  // TODO: 对接后端 /api/qa/requirement/operationLog/list?requirementId=xxx
  previewLogs.value = [];
};

const handleSavePreviewComment = async () => {
  if (!previewCommentInput.value.trim()) {
    Message.warning('请输入评论内容');
    return;
  }
  // TODO: 对接后端 /api/qa/requirement/comment/save
  previewComments.value.push({
    id: Date.now(),
    userName: '当前用户',
    content: previewCommentInput.value.trim(),
    createTime: new Date().toLocaleString()
  });
  previewCommentInput.value = '';
  Message.success('评论发表成功');
};

const handleEditFromPreview = () => {
  previewVisible.value = false;
  nextTick(() => {
    handleEditInModal(previewData.value);
  });
};

const loadDrawerCases = async () => {
  if (!currentRequirementId.value || !projectStore.getProjectId) return;
  drawerLoading.value = true;
  try {
    const res: any = await getTestCaseList(
        projectStore.getProjectId,
        undefined,                     // moduleId
        undefined,                     // setId
        currentRequirementId.value,    // requirementId
        undefined,                     // keyword
        undefined,                     // lastResult
        drawerPagination.value.current,
        drawerPagination.value.pageSize
    );
    if (res.data && res.data.records) {
      drawerCaseList.value = res.data.records;
      drawerPagination.value.total = res.data.total;
    } else {
      drawerCaseList.value = res.data || [];
    }
  } catch (e) {
    console.error(e);
  } finally {
    drawerLoading.value = false;
  }
};

const handleDrawerPageChange = (current: number) => {
  drawerPagination.value.current = current;
  loadDrawerCases();
};

const handleDrawerPageSizeChange = (pageSize: number) => {
  drawerPagination.value.pageSize = pageSize;
  drawerPagination.value.current = 1;
  loadDrawerCases();
};

const loadDrawerBugs = async () => {
  if (!currentRequirementIdForBug.value || !projectStore.getProjectId) return;
  bugDrawerLoading.value = true;
  try {
    const res: any = await getBugList(
        projectStore.getProjectId,
        undefined,                         // keyword
        undefined,                         // status
        undefined,                         // severity
        undefined,                         // priority
        undefined,                         // requirementId（精确筛选，这里不用）
        undefined,                         // caseId
        undefined,                         // moduleId
        undefined,                         // environment
        undefined,                         // reproduceRate
        undefined,                         // closeReason
        bugDrawerPagination.value.current,
        bugDrawerPagination.value.pageSize,
        currentRequirementIdForBug.value   // requirementScopeId：合并口径（直接关联 ∪ 需求下用例的BUG）
    );
    if (res.data && res.data.records) {
      drawerBugList.value = res.data.records;
      bugDrawerPagination.value.total = res.data.total;
    } else {
      drawerBugList.value = res.data || [];
    }
  } catch (e) {
    console.error(e);
  } finally {
    bugDrawerLoading.value = false;
  }
};

const handleBugDrawerPageChange = (current: number) => {
  bugDrawerPagination.value.current = current;
  loadDrawerBugs();
};

const handleBugDrawerPageSizeChange = (pageSize: number) => {
  bugDrawerPagination.value.pageSize = pageSize;
  bugDrawerPagination.value.current = 1;
  loadDrawerBugs();
};


const handleAddBug = () => {
  if (!currentRequirementIdForBug.value) {
    Message.warning('当前需求信息缺失，请重新打开预览');
    return;
  }
  bugModalTitle.value = '新增BUG';
  // 共享 BugForm：关联需求自动带出，下拉选项组件内部加载
  bugFormData.value = {
    projectId: projectStore.getProjectId,
    requirementId: currentRequirementIdForBug.value,
    moduleId: currentRequirementRecord.value?.moduleId ?? undefined,
  };
  bugModalVisible.value = true;
};


const handleSaveBug = async () => {
  bugSaving.value = true;
  try {
    const success = await bugFormRef.value?.save();
    if (success) {
      bugModalVisible.value = false;
      bugDrawerPagination.value.current = 1;
      await loadDrawerBugs();
      await loadData();
    }
  } finally {
    bugSaving.value = false;
  }
};

// 取消 BUG 与当前需求的关联（直接关联清字段 + 解除该需求下用例的绑定，BUG 本身保留）
const handleUnbindBug = async (id: number) => {
  try {
    const res: any = await unbindRequirementBug(currentRequirementIdForBug.value!, id);
    if (res.code === 200) {
      Message.success('已取消关联');
      await loadDrawerBugs();
      await loadData();
    } else {
      Message.error(res.msg || '取消关联失败');
    }
  } catch (e) {
    console.error(e);
    Message.error('取消关联失败');
  }
};
const severityColor = (severity: string) => {
  const map: Record<string, string> = {FATAL: 'red', SERIOUS: 'orange', NORMAL: 'blue', TIPS: 'gray'};
  return map[severity] || 'gray';
};

const severityText = (severity: string) => {
  const map: Record<string, string> = {FATAL: '致命', SERIOUS: '严重', NORMAL: '一般', TIPS: '提示'};
  return map[severity] || severity;
};

const bugStatusColor = (status: string) => {
  const map: Record<string, string> = {
    NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold',
    FIXED: 'blue', VERIFIED: 'cyan', CLOSED: 'green', REJECTED: 'gray'
  };
  return map[status] || 'gray';
};

const bugStatusText = (status: string) => {
  const map: Record<string, string> = {
    NEW: '新建', CONFIRMED: '已确认', FIXING: '修复中',
    FIXED: '已修复', VERIFIED: '已验证', CLOSED: '已关闭', REJECTED: '已驳回'
  };
  return map[status] || status;
};

const handlePreviewCaseFromBug = async (caseId: number) => {
  if (!caseId) return;
  try {
    const res: any = await getTestCaseDetail(caseId);
    if (res.data) {
      // 直接在新窗口或弹窗中预览用例信息
      Message.info(`用例: ${res.data.caseName || res.data.caseCode}`);
    }
  } catch (e) {
    console.error(e);
  }
};

const handleSave = async () => {
  const success = await requirementFormRef.value?.save();
  if (success) {
    modalVisible.value = false;
    await loadData();
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

const handleDelete = async (id: number) => {
  try {
    const res: any = await deleteRequirement(id);
    if (res.code === 200) {
      Message.success('删除成功');
      await loadData();
    }
  } catch (e) {
    console.error(e);
  }
};

// 删除需求（Modal 二次确认）
const confirmDelete = (record: any) => {
  Modal.confirm({
    title: '删除需求',
    content: '确认删除该需求吗？',
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { status: 'danger' },
    onOk: () => handleDelete(record.id),
  });
};

// 主表列定义
const columns = [
  { title: '需求编号', dataIndex: 'reqCode', width: 120, ellipsis: true, tooltip: true, slotName: 'reqCode' },
  { title: '需求名称', dataIndex: 'title', width: 200, ellipsis: true, slotName: 'title' },
  { title: '需求类型', dataIndex: 'reqType', width: 90, slotName: 'reqType' },
  { title: '优先级', dataIndex: 'priority', width: 80, slotName: 'priority' },
  { title: '状态', dataIndex: 'status', width: 90, slotName: 'status' },
  { title: '标签', dataIndex: 'tags', ellipsis: true, slotName: 'tags' },
  { title: '关联用例', dataIndex: 'caseCount', width: 90, slotName: 'caseCount' },
  { title: '关联BUG', dataIndex: 'bugCount', width: 90, slotName: 'bugCount' },
  { title: '操作', width: 200, fixed: 'right' as const, slotName: 'actions' },
];

// 抽屉-关联用例表格列
const drawerCaseColumns = [
  { title: '用例编号', dataIndex: 'caseCode', width: 120, ellipsis: true, tooltip: true },
  { title: '用例名称', dataIndex: 'caseName', width: 200, ellipsis: true, tooltip: true },
  { title: '类型', dataIndex: 'caseType', width: 80, slotName: 'caseType' },
  { title: '用例等级', dataIndex: 'priority', width: 80, slotName: 'drawerCasePriority' },
  { title: '关联BUG', dataIndex: 'bugCount', width: 90, slotName: 'drawerCaseBugCount' },
  { title: '操作', width: 200, fixed: 'right' as const, slotName: 'drawerCaseActions' },
];

// 抽屉-关联BUG表格列
const drawerBugColumns = [
  { title: 'BUG编号', dataIndex: 'bugCode', width: 120, slotName: 'bugCode' },
  { title: 'BUG标题', dataIndex: 'title', width: 140, slotName: 'bugTitle' },
  { title: '严重程度', dataIndex: 'severity', width: 90, slotName: 'severity' },
  { title: '状态', dataIndex: 'status', width: 90, slotName: 'bugStatus' },
  { title: '发现计划', dataIndex: 'planName', width: 110, slotName: 'planName' },
  { title: '关联用例', dataIndex: 'caseName', width: 100, slotName: 'bugCaseName' },
  { title: '操作', width: 200, slotName: 'drawerBugActions' },
];

// 抽屉-子需求表格列
const subReqColumns = [
  { title: '需求编号', dataIndex: 'reqCode', width: 120, ellipsis: true, tooltip: true, slotName: 'subReqCode' },
  { title: '需求名称', dataIndex: 'title', width: 200, ellipsis: true, slotName: 'subReqTitle' },
  { title: '优先级', dataIndex: 'priority', width: 80, slotName: 'subReqPriority' },
  { title: '状态', dataIndex: 'status', width: 90, slotName: 'subReqStatus' },
  { title: '负责人', dataIndex: 'ownerName', width: 100, ellipsis: true, tooltip: true, slotName: 'subReqOwner' },
];

// 操作列：统一文字按钮，前 2 个常显，第 3 个起收入「更多」下拉
interface RequirementOp {
  key: string;
  label: string;
  danger?: boolean;
  permission?: string;
  handler: (record: any) => void;
}
const opsFor = (record: any): RequirementOp[] => [
  { key: 'edit', label: '编辑', permission: QA_REQUIREMENT_UPDATE, handler: handleEdit },
  { key: 'delete', label: '删除', danger: true, permission: QA_REQUIREMENT_DELETE, handler: confirmDelete },
  { key: 'aiGenerate', label: 'AI 生成用例', permission: QA_TEST_CASE_CREATE, handler: handleAiGenerate },
];
const visibleOps = (record: any) => opsFor(record).slice(0, 2);
const overflowOps = (record: any) => opsFor(record).slice(2);

const handleSelectChange = (keys: any[]) => {
  selectedKeys.value = keys;
};

const handleBatchDelete = () => {
  if (selectedKeys.value.length === 0) return;
  Modal.confirm({
    title: '批量删除需求',
    content: `确定删除选中的 ${selectedKeys.value.length} 个需求吗？其关联用例与 BUG 将解除关联（数据保留），子需求将变为独立需求。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: {status: 'danger'},
    onOk: async () => {
      const res: any = await batchDeleteRequirement(selectedKeys.value);
      if (res.code === 200) {
        Message.success('批量删除成功');
        selectedKeys.value = [];
        await loadData();
      }
    },
  });
};

const priorityColor = (priority: string) => {
  const map: Record<string, string> = {P0: 'red', P1: 'orange', P2: 'blue', P3: 'gray'};
  return map[priority] || 'gray';
};

const statusColor = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: 'gray', REVIEWING: 'cyan', CONFIRMED: 'blue',
    DEVELOPING: 'gold', TESTING: 'purple', RELEASED: 'green', CLOSED: 'gray'
  };
  return map[status] || 'gray';
};

const statusText = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: '草稿', REVIEWING: '评审中', CONFIRMED: '已确认',
    DEVELOPING: '开发中', TESTING: '测试中', RELEASED: '已上线', CLOSED: '已关闭'
  };
  return map[status] || status;
};

const reqTypeText = (reqType: string) => {
  const map: Record<string, string> = {
    FEATURE: '功能需求',
    BUGFIX: '缺陷修复',
    OPTIMIZE: '优化',
    TECH_DEBT: '技术债务'
  };
  return map[reqType] || reqType || '-';
};

const sourceText = (source: string) => {
  const map: Record<string, string> = {
    CLIENT: '客户反馈',
    INTERNAL: '内部规划',
    COMPETITOR: '竞品分析',
    ONLINE: '线上问题'
  };
  return map[source] || source || '-';
};

const getParticipantNames = (participants: string | undefined) => {
  if (!participants) return '-';
  try {
    const arr = JSON.parse(participants);
    if (!Array.isArray(arr) || arr.length === 0) return '-';
    return arr.map((id: number) => {
      const user = userOptions.value.find((u: any) => u.id === id);
      return user ? (user.nickname || user.username) : id;
    }).join(', ');
  } catch {
    return participants;
  }
};

// 所有可选状态列表（用于编辑弹窗和流转弹窗）
const allStatuses = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'REVIEWING', label: '评审中' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'DEVELOPING', label: '开发中' },
  { value: 'TESTING', label: '测试中' },
  { value: 'RELEASED', label: '已上线' },
  { value: 'CLOSED', label: '已关闭' }
];

const renderHtml = (content: string | undefined, defaultText: string = '暂无') => {
  if (!content || content.trim() === '') return defaultText;
  // 如果内容已经包含 HTML 标签，直接返回
  if (/<[a-z][\s\S]*?>/i.test(content)) {
    return content;
  }
  // 纯文本，把换行符转成 <br>
  return content.replace(/\n/g, '<br>');
};

const caseTypeText = (type: string) => {
  const map: Record<string, string> = {
    FUNCTION: '功能', API: '接口', PERFORMANCE: '性能', COMPATIBILITY: '兼容', SMOKE: '冒烟'
  };
  return map[type] || type;
};


const handleAddCase = () => {
  if (!currentRequirementId.value) {
    Message.warning('当前需求信息缺失，请重新打开预览');
    return;
  }
  caseModalTitle.value = '新增用例';
  // 共享 TestCaseForm：关联需求/所属模块自动从当前需求带出，下拉选项组件内部加载
  caseFormData.value = {
    projectId: projectStore.getProjectId,
    requirementId: currentRequirementId.value,
    moduleId: currentRequirementRecord.value?.moduleId ?? undefined,
  };
  caseModalVisible.value = true;
};


const handleSaveCase = async () => {
  caseSaving.value = true;
  try {
    const saved = await caseFormRef.value?.save();
    if (saved) {
      caseModalVisible.value = false;
      drawerPagination.value.current = 1;
      await loadDrawerCases();
    }
  } finally {
    caseSaving.value = false;
  }
};





// 取消用例与当前需求的关联（清 requirement_id + 多需求串移除，用例本身保留）
const handleUnbindCase = async (id: number) => {
  try {
    const res: any = await unbindRequirementCase(currentRequirementId.value!, id);
    if (res.code === 200) {
      Message.success('已取消关联');
      await loadDrawerCases();
      await loadData();
    } else {
      Message.error(res.msg || '取消关联失败');
    }
  } catch (e) {
    console.error(e);
    Message.error('取消关联失败');
  }
};

onMounted(() => {
  loadData();
  loadModuleOptions();
});

watch(
    () => projectStore.getProjectId,
    (newId) => {
      if (newId) {
        loadData();
        loadModuleOptions();
      }
    },
    {immediate: true}
);
</script>

<style scoped lang="less">
.requirement-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

/* tab 标题计数徽标（灰色，与计划详情页一致） */
.tab-count-badge {
  display: inline-block;
  min-width: 20px;
  padding: 0 6px;
  margin-left: 4px;
  font-size: 12px;
  line-height: 18px;
  text-align: center;
  color: #4e5969;
  background: #e5e6eb;
  border-radius: 9px;
}

/* 用例/BUG 表单抽屉内容区：纵向滚动、禁掉横向滚动 */
.form-drawer-body {
  padding: 4px 0;
  height: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  box-sizing: border-box;
}

/* 抽屉里 a-row 负 margin 会撑出横向滚动条，兜底裁掉 */
:deep(.arco-drawer-body) {
  overflow-x: hidden;
}

.requirement-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.requirement-card :deep(.arco-card-body) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

/* ===== 左右分栏 + 伸缩分隔条（与用例列表/计划详情一致） ===== */
.main-row {
  display: flex;
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  margin-top: 16px;
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
.req-tree-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.req-tree-panel :deep(.module-tree-manager) {
  flex: 1;
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

.table-wrapper :deep(.arco-table-th) {
  background: var(--color-fill-1);
}

.drawer-table-wrapper {
  height: calc(100vh - 180px);
  overflow: auto;
}

.req-title-link {
  color: rgb(var(--primary-6));
  cursor: pointer;
  transition: all 0.2s;

  &:hover {
    color: rgb(var(--link-color-hover));
    text-decoration: underline;
  }
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
.stat-developing {
  background: #fff7e8;
  color: #ff7d00;
}
.stat-developing:hover {
  border-color: #ff7d00;
}
.stat-testing {
  background: #f5e8ff;
  color: rgb(var(--purple-6));
}
.stat-testing:hover {
  border-color: rgb(var(--purple-6));
}
.stat-closed {
  background: #f2f3f5;
  color: #86909c;
}
.stat-closed:hover {
  border-color: #86909c;
}
.rich-text-preview {
  line-height: 1.6;
  :deep(p) { margin: 8px 0; }
  :deep(ul), :deep(ol) { padding-left: 20px; margin: 8px 0; }
  :deep(blockquote) { border-left: 4px solid #ccc; padding-left: 10px; color: #666; margin: 8px 0; }
  :deep(img) { max-width: 100%; }
  :deep(a) { color: rgb(var(--primary-6)); }
}
.text-ellipsis-inline {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

// 顶部指标看板
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
    color: var(--color-text-1);
  }

  .metric-label {
    font-size: 13px;
    color: var(--color-text-2);
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
  background: #e8f3ff;
  border-color: #bedaff;
}

.metric-developing {
  background: #fff7e8;
  border-color: #ffe4ba;
}

.metric-testing {
  background: #f5e8ff;
  border-color: #e6cfff;
}

.metric-closed {
  background: #e8ffea;
  border-color: #c8f0c8;
}

// 主操作行
.filter-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;

  .filter-left {
    display: flex;
    align-items: center;
    gap: 12px;

    .add-btn {
      font-weight: 600;
    }
  }
}

// 筛选区（a-row + a-col 栅格，gutter 控制间距）
.filter-grid {
  margin: 8px 0 16px;
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

// 优先级圆点
.priority-dot {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--color-text-1);

  .dot {
    width: 6px;
    height: 6px;
    border-radius: 50%;
  }

  &.p0 .dot { background: rgb(var(--danger-6)); }
  &.p1 .dot { background: rgb(var(--orange-6)); }
  &.p2 .dot { background: rgb(var(--warning-6)); }
  &.p3 .dot { background: rgb(var(--primary-6)); }
}

:deep(.arco-tag.status-pill) {
  border-radius: 999px;
}

:deep(.arco-table-row-selected) {
  background-color: rgb(var(--primary-1));
}

// 需求预览抽屉
.preview-drawer {
  :deep(.arco-drawer-mask) {
    background-color: rgba(0, 0, 0, 0.3);
    backdrop-filter: blur(4px);
  }

  :deep(.arco-drawer) {
    height: 100vh;
    max-height: 100vh;
    display: flex;
    flex-direction: column;
  }

  :deep(.arco-drawer-header) {
    height: auto;
    min-height: 48px;
    padding: 16px 20px;
    border-bottom: 1px solid var(--color-border-2);
    flex-shrink: 0;
  }

  :deep(.arco-drawer-body) {
    display: flex;
    flex-direction: column;
    flex: 1;
    padding: 0;
    overflow: hidden;
  }

  :deep(.arco-drawer-footer) {
    padding: 0;
    border-top: 1px solid var(--color-border-2);
    flex-shrink: 0;
  }
}

.preview-header {
  width: 100%;

  .preview-header-top {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
  }

  .preview-req-code {
    font-size: 13px;
    color: var(--color-text-3);
    font-family: monospace;
  }

  .preview-header-bottom {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .preview-title-text {
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text-1);
  }
}

.preview-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

/* 多 tab 结构：tab 内容区留白与原卡片一致 */
.preview-tabs {
  :deep(.arco-tabs-content) {
    padding-top: 8px;
  }
}

.preview-section {
  margin-bottom: 16px;

  :deep(.arco-card-header) {
    padding: 12px 0;
    border-bottom: none;
  }

  :deep(.arco-card-body) {
    padding: 0;
  }
}

.preview-description {
  max-height: 350px;
  overflow-y: auto;
  padding-right: 4px;
}

.preview-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.preview-stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  .stat-icon {
    width: 40px;
    height: 40px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: rgba(255, 255, 255, 0.6);
  }

  .stat-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }

  .stat-value {
    font-size: 22px;
    font-weight: 700;
    line-height: 1.2;
  }

  .stat-name {
    font-size: 12px;
  }

  &.stat-case {
    background: #e8f3ff;
    border-color: #bedaff;

    &:hover { background: #d4e8ff; }
    .stat-value, .stat-name { color: rgb(var(--primary-6)); }
  }

  &.stat-bug {
    background: #fff0ed;
    border-color: #ffd6cf;

    &:hover { background: #ffe0db; }
    .stat-value, .stat-name { color: rgb(var(--danger-6)); }
  }

  &.stat-open-bug {
    background: #fff7e8;
    border-color: #ffe4ba;

    &:hover { background: #ffecd4; }
    .stat-value, .stat-name { color: rgb(var(--orange-6)); }
  }
}

.preview-tabs-section {
  margin-bottom: 0;

  :deep(.arco-tabs-content) {
    padding-top: 12px;
  }
}

.preview-comment-list {
  max-height: 240px;
  overflow-y: auto;
  padding-right: 4px;
}

.preview-comment-input {
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 8px 12px;
  margin-top: 16px;
  transition: all 0.2s ease;
  background: var(--color-bg-1);

  &.focused {
    border-color: rgb(var(--primary-6));
    box-shadow: 0 0 0 2px rgba(var(--primary-6), 0.1);
  }

  :deep(.arco-textarea) {
    border: none;
    background: transparent;
    resize: none;
    padding: 0;

    &:focus {
      box-shadow: none;
    }
  }
}

.preview-comment-actions {
  margin-top: 12px;
  text-align: right;
}

.preview-log-list {
  max-height: 320px;
  overflow-y: auto;
  padding-right: 4px;
}

.preview-footer {
  padding: 12px 20px;
  text-align: right;
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
.step-number {
  color: rgb(var(--primary-6));
  font-size: 13px;
  font-weight: 600;
}
</style>
