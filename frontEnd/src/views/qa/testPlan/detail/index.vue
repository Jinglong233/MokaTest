<template>
  <div class="test-plan-detail-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testPlan', planData.planName || '计划详情']"/>

    <!-- 计划基本信息 -->
    <a-card class="plan-info-card" size="small">
      <div class="plan-header-row">
        <span class="plan-title">{{ planData.planName || '-' }}</span>
        <a-space>
          <a-button v-permission="'qa:testplan:update'" @click="showEditModal">
            <template #icon><icon-edit /></template>
            编辑
          </a-button>
          <a-button v-permission="'qa:testplan:create'" @click="handleCopyPlan">
            <template #icon><icon-copy /></template>
            复制
          </a-button>
          <a-popconfirm content="确认删除该测试计划吗？" type="warning" @ok="handleDeletePlan">
            <a-button v-permission="'qa:testplan:delete'" status="danger">
              <template #icon><icon-delete /></template>
              删除
            </a-button>
          </a-popconfirm>
          <a-button v-permission="'qa:testplan:execute'" @click="handleGenerateReport">
            <template #icon><icon-file /></template>
            生成报告
          </a-button>
        </a-space>
      </div>
      <div class="plan-progress-row">
        <span class="plan-progress-text">已执行 {{ resultStats.total - resultStats.unexec }}/{{ resultStats.total }}，通过率 {{ resultStats.total > 0 ? Math.round(resultStats.pass / resultStats.total * 100) : 0 }}%</span>
      </div>
      <!-- 执行结果占比条：成功绿/失败红/阻塞橙/未执行灰 -->
      <div class="plan-progress-bar-bg">
        <a-tooltip v-if="resultStats.pass" :content="`成功 ${resultStats.pass}`">
          <div class="seg seg-pass" :style="{ width: (resultStats.pass / resultStats.total * 100) + '%' }"></div>
        </a-tooltip>
        <a-tooltip v-if="resultStats.fail" :content="`失败 ${resultStats.fail}`">
          <div class="seg seg-fail" :style="{ width: (resultStats.fail / resultStats.total * 100) + '%' }"></div>
        </a-tooltip>
        <a-tooltip v-if="resultStats.block" :content="`阻塞 ${resultStats.block}`">
          <div class="seg seg-block" :style="{ width: (resultStats.block / resultStats.total * 100) + '%' }"></div>
        </a-tooltip>
        <a-tooltip v-if="resultStats.unexec" :content="`未执行 ${resultStats.unexec}`">
          <div class="seg seg-unexec" :style="{ width: (resultStats.unexec / resultStats.total * 100) + '%' }"></div>
        </a-tooltip>
      </div>
    </a-card>

    <!-- 用例执行列表 -->
    <a-tabs :active-key="activeTab" type="line" size="small" class="plan-main-tabs" @change="handleTabChange">
        <!-- Tab: 测试规划（脑图） -->
        <a-tab-pane key="mindmap" title="测试规划">
          <a-card class="list-card" :bordered="false">
          <MindMapExec
              v-if="activeTab === 'mindmap'"
              ref="mindMapRef"
              :plan-id="planId"
              :case-list="caseList"
              :plan-name="planData.planName || ''"
              @open-drawer="handleOpenCaseDrawer"
          />
          </a-card>
        </a-tab-pane>

        <!-- Tab: 功能用例 -->
        <a-tab-pane key="cases">
          <template #title>
            功能用例
            <span class="tab-count-badge">{{ caseList.length }}</span>
          </template>
          <div class="case-view-wrap">
            <!-- 列表/脑图 视图切换 -->
            <div class="case-view-toggle">
              <a-radio-group v-model="caseViewMode" type="button" size="small">
                <a-radio value="list"><icon-list /> 列表</a-radio>
                <a-radio value="mindmap"><icon-mind-mapping /> 脑图</a-radio>
              </a-radio-group>
            </div>
          <div class="main-row">
            <!-- 左栏：树筛选（列表/脑图两种视图共用） -->
            <div class="col side-col" :style="sideColStyle">
              <a-card class="tree-card" size="small" :bordered="false">
                <div class="case-tree-panel">
              <a-radio-group v-model="treeMode" type="button" size="small" @change="onTreeModeChange" class="tree-mode-switch">
                <a-radio value="testpoint">测试点</a-radio>
                <a-radio value="module">模块</a-radio>
              </a-radio-group>
              <a-input-search v-model="treeKeyword" placeholder="搜索名称" allow-clear size="small" class="tree-search" />
              <div class="case-tree-scroll">
                <a-tree
                  v-model:expanded-keys="treeExpandedKeys"
                  :data="filteredTreeData"
                  :field-names="treeFieldNames"
                  :animation="false"
                  :selected-keys="selectedTreeKeys"
                  :block-node="true"
                  @select="onTreeSelect"
                >
                  <template #title="node">
                    <span class="tree-node-title-row">
                      <template v-if="treeMode === 'testpoint'">
                        <icon-apps v-if="node.key === 0" style="font-size: 12px; color: var(--color-text-3); margin-right: 4px; flex-shrink: 0" />
                        <icon-bookmark v-else style="font-size: 12px; color: var(--color-text-3); margin-right: 4px; flex-shrink: 0" />
                      </template>
                      <template v-else>
                        <icon-apps v-if="node.id === 0" style="font-size: 12px; color: var(--color-text-3); margin-right: 4px; flex-shrink: 0" />
                        <icon-folder v-else style="font-size: 12px; color: var(--color-text-3); margin-right: 4px; flex-shrink: 0" />
                      </template>
                      <span class="tree-node-label" :title="treeMode === 'testpoint' ? node.label : node.moduleName">
                        {{ treeMode === 'testpoint' ? node.label : node.moduleName }}
                      </span>
                      <span class="tree-node-count">{{ treeMode === 'testpoint' ? (node.count ?? 0) : (node.caseCount ?? 0) }}</span>
                    </span>
                  </template>
                </a-tree>
              </div>
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
            <!-- 右侧：用例列表 / 脑图 -->
            <div class="col content-col">
              <a-card class="list-card" :bordered="false" v-show="caseViewMode === 'list'">
                <div class="case-list-panel">
      <!-- 用例筛选工具栏 -->
      <div class="case-filter-toolbar">
        <a-space>
          <a-input
              v-model="caseFilterKeyword"
              placeholder="搜索用例名称/编号"
              allow-clear
              style="width: 200px;"
              @change="selectedCaseKeys = []"
          />
          <a-select
              v-model="caseFilterResult"
              placeholder="执行结果"
              allow-clear
              style="width: 130px;"
              @change="selectedCaseKeys = []"
          >
            <a-option value="PASS">成功</a-option>
            <a-option value="FAIL">失败</a-option>
            <a-option value="BLOCK">阻塞</a-option>
            <a-option value="UNEXECUTED">未执行</a-option>
          </a-select>
          <a-select
              v-model="caseFilterPriority"
              placeholder="优先级"
              allow-clear
              style="width: 110px;"
              @change="selectedCaseKeys = []"
          >
            <a-option value="P0">P0</a-option>
            <a-option value="P1">P1</a-option>
            <a-option value="P2">P2</a-option>
          </a-select>
          <a-button @click="handleResetCaseFilter">
            <template #icon><icon-refresh /></template>
            重置
          </a-button>
        </a-space>
      </div>

      <!-- 批量操作工具栏 -->
      <div v-if="selectedCaseKeys.length > 0" class="batch-toolbar">
        <span class="batch-count">已选 {{ selectedCaseKeys.length }} 条</span>
        <a-divider direction="vertical" />
        <a-space>
          <a-button v-permission="'qa:testplan:execute'" type="text" size="small" @click="handleBatchExecute('PASS')">
            <template #icon><icon-check style="color: rgb(var(--success-6));" /></template>
            批量成功
          </a-button>
          <a-button v-permission="'qa:testplan:execute'" type="text" size="small" status="danger" @click="handleBatchExecute('FAIL')">
            <template #icon><icon-close /></template>
            批量失败
          </a-button>
          <a-button v-permission="'qa:testplan:execute'" type="text" size="small" @click="handleBatchExecute('BLOCK')">
            <template #icon><icon-exclamation-circle style="color: rgb(var(--warning-6));" /></template>
            批量阻塞
          </a-button>
          <a-popconfirm :content="`确认移除选中的 ${selectedCaseKeys.length} 条用例？`" type="warning" @ok="handleBatchRemove">
            <a-button v-permission="'qa:testplan:update'" type="text" size="small" status="danger">
              <template #icon><icon-delete /></template>
              批量移除
            </a-button>
          </a-popconfirm>
          <a-divider direction="vertical" />
          <a-button type="text" size="small" @click="handleClearSelection">取消选择</a-button>
        </a-space>
      </div>

      <a-table
          :data="filteredCaseList"
          :columns="columns"
          :loading="loading"
          row-key="id"
          :sticky-header="true" :scroll="{ x: '125%' }"
          :row-selection="{ type: 'checkbox', showCheckedAll: true }"
          v-model:selectedKeys="selectedCaseKeys"
      >
        <template #caseName="{ record }">
          <a-tooltip :content="record.caseName">
            <a-link @click="handlePreviewCase(record)">{{ record.caseName }}</a-link>
          </a-tooltip>
        </template>
        <template #priority="{ record }">
          <a-tag :color="priorityColor(record.priority)" size="small">
            <template #icon><icon-flag /></template>
            {{ record.priority }}
          </a-tag>
        </template>
        <template #executeResult="{ record }">
          <!-- 行内编辑执行结果，选择即保存 -->
          <a-select
            :model-value="record.executeResult || 'UNEXECUTED'"
            size="small"
            :trigger-props="{ autoFitPopupMinWidth: true }"
            @change="(val: any) => handleInlineExecute(record, val)"
          >
            <a-option value="UNEXECUTED">
              <icon-clock-circle :style="{ color: resultIconColor('UNEXECUTED'), marginRight: '4px' }" />未执行
            </a-option>
            <a-option value="PASS">
              <icon-check-circle-fill :style="{ color: resultIconColor('PASS'), marginRight: '4px' }" />成功
            </a-option>
            <a-option value="FAIL">
              <icon-close-circle-fill :style="{ color: resultIconColor('FAIL'), marginRight: '4px' }" />失败
            </a-option>
            <a-option value="BLOCK">
              <icon-exclamation-circle-fill :style="{ color: resultIconColor('BLOCK'), marginRight: '4px' }" />阻塞
            </a-option>
          </a-select>
        </template>
        <template #bugCount="{ record }">
          <a-tag
            v-if="record.bugList && record.bugList.length"
            color="red"
            size="small"
            style="cursor: pointer;"
            @click="handleOpenBugListDrawer(record)"
          >
            {{ record.bugList.length }}
          </a-tag>
          <span v-else style="color: #86909c;">0</span>
        </template>
        <template #actions="{ record }">
          <a-space class="op-icons" :size="0">
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
            <a-dropdown v-if="overflowOps(record).length" trigger="click">
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
                  <span :style="op.danger ? 'color: rgb(var(--danger-6))' : ''">{{ op.label }}</span>
                </a-doption>
              </template>
            </a-dropdown>
          </a-space>
        </template>
      </a-table>
                </div>
              </a-card>
              <!-- 脑图视图（跟随左栏树选中联动过滤，模块模式根节点显示选中模块名） -->
              <CaseMindMap v-if="caseViewMode === 'mindmap'" :plan-id="planId" :filter-case-ids="mindmapFilterIds" :root-label="mindmapRootLabel" :flat-cases="mindmapFlat" :group-by-module="mindmapGroupByModule" :module-list="flatModuleList" class="case-mindmap-fill" @refresh="loadPlanDetail" />
            </div>
          </div>
          </div>
        </a-tab-pane>

        <!-- Tab: 缺陷列表 -->
        <a-tab-pane key="bugs">
          <template #title>
            缺陷列表
            <span class="tab-count-badge">{{ planBugList.length }}</span>
          </template>
          <a-card class="list-card" :bordered="false">
          <a-table :data="planBugList" :columns="bugListColumns" row-key="id" :sticky-header="true">
            <template #bugTitle="{ record }">
              <a-tooltip :content="record.title">
                <a-link @click="handleOpenBugDetail(record)">{{ record.title }}</a-link>
              </a-tooltip>
            </template>
            <template #status="{ record }">
              <a-tag :color="bugStatusColor(record.status)" size="small">{{ bugStatusText(record.status) }}</a-tag>
            </template>
            <template #caseCount="{ record }">{{ record.caseCount ?? 0 }}</template>
            <template #assigneeName="{ record }">{{ record.assigneeName || '-' }}</template>
            <template #createTime="{ record }">{{ record.createTime || '-' }}</template>
          </a-table>
          <a-empty v-if="planBugList.length === 0" description="暂无缺陷" style="padding: 40px 0;" />
          </a-card>
        </a-tab-pane>

        <!-- Tab: 执行历史 -->
        <a-tab-pane key="history" title="执行历史">
          <a-card class="list-card" :bordered="false">
          <a-table :data="planHistoryList" :columns="historyColumns" row-key="id" :sticky-header="true" :scroll="{ x: '125%' }">
            <template #historyResult="{ record }">
              <a-tag :color="resultColor(record.executeResult)" size="small">
                <template #icon><component :is="resultIcon(record.executeResult)" /></template>
                {{ resultText(record.executeResult) }}
              </a-tag>
            </template>
          </a-table>
          <a-empty v-if="planHistoryList.length === 0" description="暂无执行记录" style="padding: 40px 0;" />
          </a-card>
        </a-tab-pane>
      </a-tabs>

    <!-- 编辑计划弹窗 -->
    <a-modal
        v-model:visible="editModalVisible"
        title="编辑测试计划"
        width="560px"
        @ok="handleSaveEdit"
        @cancel="editModalVisible = false"
        :mask-closable="false"
    >
      <a-form :model="editForm" layout="vertical">
        <a-form-item label="计划名称" required>
          <a-input v-model="editForm.planName" placeholder="请输入计划名称"/>
        </a-form-item>
        <a-form-item label="所属模块">
          <a-tree-select
              v-model="editForm.moduleId"
              :data="moduleTreeData"
              :field-names="{ key: 'id', title: 'moduleName', children: 'children' }"
              allow-clear
              placeholder="选择所属模块"
              style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="计划描述">
          <a-textarea v-model="editForm.description" :auto-size="{ minRows: 3 }" placeholder="请输入计划描述"/>
        </a-form-item>
        <a-form-item label="标签">
          <a-input-tag v-model="editForm.tags" placeholder="输入后回车添加标签" allow-clear style="width: 100%"/>
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model="editForm.status">
            <a-option value="DRAFT">草稿</a-option>
            <a-option value="READY">就绪</a-option>
            <a-option value="RUNNING">执行中</a-option>
            <a-option value="COMPLETED">已完成</a-option>
          </a-select>
        </a-form-item>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="开始时间">
              <a-date-picker v-model="editForm.startTime" show-time style="width: 100%;"/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="结束时间">
              <a-date-picker v-model="editForm.endTime" show-time style="width: 100%;"/>
            </a-form-item>
          </a-col>
        </a-row>
      </a-form>
    </a-modal>

    <!-- 用例预览抽屉 -->
    <a-drawer
        v-model:visible="previewVisible"
        :title="previewData.caseName"
        width="600px"
        :mask-closable="true"
        :footer="false"
    >
      <a-descriptions :data="[
        { label: '用例编号', value: previewData.caseCode },
        { label: '用例类型', value: caseTypeText(previewData.caseType) },
        { label: '优先级', value: previewData.priority },
        { label: '执行结果', value: resultText(previewData.executeResult) },
      ]" layout="inline-vertical" :column="2" :label-style="{ fontWeight: 'bold' }"/>
      <a-divider/>
      <h4 style="margin-bottom: 8px;">前置条件</h4>
      <div class="rich-text-preview" v-html="renderHtml(previewData.preCondition, '无')"/>
      <a-divider/>
      <h4 style="margin-bottom: 8px;">测试步骤</h4>
      <div v-if="previewData.testSteps && previewData.testSteps.length">
        <div v-for="(step, index) in previewData.testSteps" :key="index" style="margin-bottom: 12px;">
          <div style="font-weight: bold; color: #86909c; margin-bottom: 4px;">步骤 {{ index + 1 }}</div>
          <div class="rich-text-preview" v-html="renderHtml(step.step, '-')"/>
          <div style="color: #86909c; margin-top: 4px;">预期结果：</div>
          <div class="rich-text-preview" v-html="renderHtml(step.expected, '-')"/>
        </div>
      </div>
      <div v-else style="color: #86909c;">暂无测试步骤</div>
    </a-drawer>

    <!-- BUG详情抽屉（复用BUG池同款，支持编辑/评论/操作日志） -->
    <BugDetailDrawer
        v-model:visible="bugDetailVisible"
        :bug-id="currentBugId"
        @refresh="loadPlanDetail"
    />

    <!-- BUG预览抽屉 -->
    <a-drawer        v-model:visible="bugPreviewVisible"
        title="BUG详情"
        width="560px"
        :mask-closable="true"
        :footer="false"
    >
      <div class="bug-preview-body">
        <!-- 标题区 -->
        <div class="bug-preview-title">{{ bugPreviewData.title }}</div>
        <div class="bug-preview-tags">
          <a-tag :color="bugStatusColor(bugPreviewData.status)" size="large">{{ bugStatusText(bugPreviewData.status) }}</a-tag>
          <a-tag :color="severityColor(bugPreviewData.severity)" size="large">{{ severityText(bugPreviewData.severity) }}</a-tag>
          <a-tag :color="priorityColor(bugPreviewData.priority)" size="large">{{ bugPreviewData.priority }}</a-tag>
        </div>

        <!-- 元信息区 -->
        <div class="bug-preview-meta">
          <div class="meta-item">
            <span class="meta-label">编号</span>
            <span class="meta-value">{{ bugPreviewData.bugCode || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">环境</span>
            <span class="meta-value">{{ environmentText(bugPreviewData.environment) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">发现版本</span>
            <span class="meta-value">{{ bugPreviewData.foundVersion || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">修复版本</span>
            <span class="meta-value">{{ bugPreviewData.fixedVersion || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">截止日期</span>
            <span class="meta-value">{{ bugPreviewData.deadline || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">重现概率</span>
            <span class="meta-value">{{ reproduceRateText(bugPreviewData.reproduceRate) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">报告人</span>
            <span class="meta-value">{{ bugPreviewData.reporterName || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">指派人</span>
            <span class="meta-value">{{ bugPreviewData.assigneeName || '-' }}</span>
          </div>
          <div class="meta-item" style="grid-column: span 2;">
            <span class="meta-label">关联需求</span>
            <span class="meta-value">{{ bugPreviewData.requirementTitle || '-' }}</span>
          </div>
          <div class="meta-item" style="grid-column: span 2;">
            <span class="meta-label">关联用例</span>
            <span class="meta-value">{{ bugPreviewData.caseName || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">创建时间</span>
            <span class="meta-value">{{ bugPreviewData.createTime || '-' }}</span>
          </div>
          <div class="meta-item" v-if="bugPreviewData.closeReason">
            <span class="meta-label">关闭原因</span>
            <span class="meta-value">{{ closeReasonText(bugPreviewData.closeReason) }}</span>
          </div>
        </div>

        <!-- 描述区 -->
        <div class="bug-preview-section">
          <div class="section-title">BUG描述</div>
          <div class="rich-text-preview" v-html="renderHtml(bugPreviewData.description, '暂无描述')"/>
        </div>

        <!-- 复现步骤区 -->
        <div class="bug-preview-section" v-if="bugPreviewData.reproduceSteps">
          <div class="section-title">复现步骤</div>
          <div class="rich-text-preview" v-html="renderHtml(bugPreviewData.reproduceSteps, '暂无')"/>
        </div>
      </div>

      <div class="bug-preview-footer">
        <a-space>
          <a-button @click="bugPreviewVisible = false">关闭</a-button>
          <a-button v-permission="'qa:bug:update'" type="primary" @click="handleEditBugFromPreview">编辑</a-button>
        </a-space>
      </div>
    </a-drawer>

    <!-- BUG列表抽屉（某用例下的全部BUG） -->
    <a-drawer
        v-model:visible="bugListDrawerVisible"
        :title="bugListDrawerTitle"
        width="480px"
        :mask-closable="true"
        :footer="false"
    >
      <div v-if="bugListDrawerCaseId">
        <div
          v-for="record in caseList.filter(c => c.id === bugListDrawerCaseId)"
          :key="record.id"
        >
          <div v-if="record.bugList && record.bugList.length">
            <div
              v-for="bug in record.bugList"
              :key="bug.id"
              class="bug-drawer-item"
              @click="handlePreviewBug(bug.id)"
            >
              <div class="bug-drawer-header">
                <a-tag :color="bugStatusColor(bug.status)" size="small">{{ bugStatusText(bug.status) }}</a-tag>
                <span class="bug-drawer-code">{{ bug.bugCode }}</span>
                <span class="bug-drawer-severity" :style="{ color: severityColor(bug.severity) }">{{ severityText(bug.severity) }}</span>
              </div>
              <div class="bug-drawer-title" :title="bug.title">{{ bug.title }}</div>
            </div>
          </div>
          <a-empty v-else description="暂无关联BUG" />
        </div>
      </div>
    </a-drawer>

    <!-- 测试点关联用例抽屉（复用 CaseSelectDrawer：模块树筛选 + 多选 + 确认，剔除已加入计划的用例） -->
    <CaseSelectDrawer
        v-model:visible="caseSelectVisible"
        :project-id="projectStore.getProjectId"
        :exclude-plan-id="planId"
        @confirm="handleCaseSelectConfirm"
    />

    <!-- 提BUG弹窗 -->
    <a-modal
        class="qa-edit-modal"
        v-model:visible="bugModalVisible"
        :title="bugFormIsEdit ? '编辑BUG' : '提交BUG'"
        width="960px"
        @ok="handleConfirmBug"
        @cancel="bugModalVisible = false"
        :mask-closable="false"
    >
      <div class="modal-scroll-body">
        <a-form :model="bugForm" layout="vertical">
          <!-- 关联信息（只读） -->
          <a-divider orientation="left" style="margin-top: 0;">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">关联信息</span>
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="12">
              <a-form-item label="关联用例">
                <a-input :model-value="bugFormCaseName" disabled/>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="关联需求">
                <a-input :model-value="bugFormRequirementTitle || '-'" disabled/>
              </a-form-item>
            </a-col>
          </a-row>

          <!-- 基本信息 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">基本信息</span>
          </a-divider>
          <a-form-item label="BUG标题" required>
            <a-input v-model="bugForm.title" placeholder="请输入BUG标题" size="large"/>
          </a-form-item>

          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="严重程度">
                <a-select v-model="bugForm.severity">
                  <a-option value="FATAL">致命</a-option>
                  <a-option value="SERIOUS">严重</a-option>
                  <a-option value="NORMAL">一般</a-option>
                  <a-option value="TIPS">提示</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="优先级">
                <a-select v-model="bugForm.priority">
                  <a-option value="URGENT">紧急</a-option>
                  <a-option value="HIGH">高</a-option>
                  <a-option value="MEDIUM">中</a-option>
                  <a-option value="LOW">低</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="状态">
                <a-select v-model="bugForm.status">
                  <a-option value="NEW">新建</a-option>
                  <a-option value="CONFIRMED">已确认</a-option>
                  <a-option value="FIXING">修复中</a-option>
                  <a-option value="FIXED">已修复</a-option>
                  <a-option value="VERIFIED">已验证</a-option>
                  <a-option value="CLOSED">已关闭</a-option>
                  <a-option value="REJECTED">已驳回</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>

          <!-- 环境信息 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">环境信息</span>
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="所属模块">
                <a-select v-model="bugForm.moduleId" allow-clear placeholder="请选择所属模块">
                  <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="环境">
                <a-select v-model="bugForm.environment" placeholder="请选择环境" allow-clear>
                  <a-option value="TEST">测试环境</a-option>
                  <a-option value="STAGING">预发环境</a-option>
                  <a-option value="PROD">生产环境</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="截止日期">
                <a-date-picker v-model="bugForm.deadline" value-format="YYYY-MM-DD" placeholder="请选择截止日期"
                               style="width: 100%"/>
              </a-form-item>
            </a-col>
          </a-row>

          <!-- 版本与复现 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">版本与复现</span>
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="发现版本">
                <a-input v-model="bugForm.foundVersion" placeholder="如 v1.0.0"/>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="修复版本">
                <a-input v-model="bugForm.fixedVersion" placeholder="如 v1.0.1"/>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="重现概率">
                <a-select v-model="bugForm.reproduceRate" placeholder="请选择重现概率" allow-clear>
                  <a-option value="ALWAYS">必现</a-option>
                  <a-option value="OFTEN">高概率</a-option>
                  <a-option value="SOMETIMES">偶现</a-option>
                  <a-option value="RARE">难现</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>

          <!-- 详细描述 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">详细描述</span>
          </a-divider>
          <a-form-item label="BUG描述">
            <RichEditor v-model="bugForm.description" placeholder="请输入BUG描述" @uploaded="handleUploadedBugFile"/>
          </a-form-item>
          <a-form-item label="复现步骤">
            <RichEditor v-model="bugForm.reproduceSteps" placeholder="请输入复现步骤" @uploaded="handleUploadedBugFile"/>
          </a-form-item>

          <!-- 其他 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">其他</span>
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="12">
              <a-form-item label="标签">
                <a-input-tag v-model="bugForm.tags" placeholder="输入标签后按回车" allow-clear/>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item v-if="bugForm.status === 'CLOSED'" label="关闭原因">
                <a-select v-model="bugForm.closeReason" placeholder="请选择关闭原因" allow-clear>
                  <a-option value="FIXED">已修复</a-option>
                  <a-option value="DUPLICATE">重复</a-option>
                  <a-option value="NOT_BUG">不是BUG</a-option>
                  <a-option value="CANNOT_REPRODUCE">无法复现</a-option>
                  <a-option value="WONT_FIX">暂不处理</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>
          <a-form-item label="指派人">
            <a-select
                v-model="bugForm.assigneeId"
                allow-clear
                allow-search
                :filter-option="false"
                placeholder="请输入关键词搜索用户"
                @search="handleSearchUser"
                @dropdown-visible-change="handleUserDropdownVisibleChange"
            >
              <a-option v-for="u in userOptions" :key="u.id" :value="u.id">{{ u.nickname || u.username }}</a-option>
            </a-select>
          </a-form-item>
        </a-form>
      </div>
    </a-modal>

  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'TestPlanDetail' };
</script>

<script setup lang="ts">
import {ref, onMounted, onBeforeUnmount, computed, watch, nextTick} from 'vue';
import {useRoute, useRouter, onBeforeRouteLeave} from 'vue-router';
import {Message, Modal} from '@arco-design/web-vue';
import {
  IconCheck, IconClose,
  IconExclamationCircle, IconDelete, IconFile, IconRefresh,
  IconEdit, IconCopy
} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import MindMapExec from './MindMapExec.vue';
import CaseMindMap from './CaseMindMap.vue';
import CaseSelectDrawer from '@/views/qa/review/components/CaseSelectDrawer.vue';
import BugDetailDrawer from '@/views/qa/bug/components/BugDetailDrawer.vue';
import RichEditor from '@/components/rich-editor/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import {getUserListByPage} from '@/api/MyApi/user';
import {deleteRichTextImages} from '@/api/MyApi/fileUpload';
import {useProjectStore} from '@/store';
import {
  getTestPlanDetail, addCasesToPlan, removeCaseFromPlan, updateCaseTestPoint,
  executePlanCase, batchExecutePlanCases, generateBugFromPlanCase, generatePlanReport,
  updateTestPlan, saveTestPlan, deleteTestPlan,
  getQaModuleList,
  getBugDetail, updateBug,
  getRequirementList, getRequirementDetail, getTestCaseDetail,
  getTestPoints
} from '@/api/MyApi/qa';

const route = useRoute();
const router = useRouter();
const projectStore = useProjectStore();

// 用于渲染 dropdownRender 插槽中的 VNode
const VNodeRenderer = {
  props: ['vnodes'],
  render() {
    return this.vnodes;
  }
};

const planId = computed(() => Number(route.params.planId));

const loading = ref(false);
const planData = ref<any>({});
const caseList = ref<any[]>([]);

// 用例列表筛选
const caseFilterKeyword = ref('');
const caseFilterResult = ref('');
const caseFilterPriority = ref('');
const activeTab = ref('mindmap');
// 功能用例：列表/脑图 视图切换
const caseViewMode = ref<'list' | 'mindmap'>('list');
const mindMapRef = ref();

// ===== 测试规划脑图未保存拦截 =====
const isMindmapDirty = () => activeTab.value === 'mindmap' && !!mindMapRef.value?.isDirty?.();

// 1. tab 切换（脑图组件 v-if 销毁，未保存内容会丢，必须先确认）
const handleTabChange = (key: string | number) => {
  const k = String(key);
  if (k !== 'mindmap' && isMindmapDirty()) {
    Modal.confirm({
      title: '有未保存的修改',
      content: '测试规划脑图有未保存的修改，切换后将丢失，确定离开吗？',
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: () => { activeTab.value = k; },
    });
    return;
  }
  activeTab.value = k;
};

// 2. 路由离开
onBeforeRouteLeave((_to, _from, next) => {
  if (isMindmapDirty()) {
    Modal.confirm({
      title: '有未保存的修改',
      content: '测试规划脑图有未保存的修改，离开后将丢失，确定离开吗？',
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: () => next(),
      onCancel: () => next(false),
    });
    return;
  }
  next();
});

// 3. 浏览器刷新/关闭的兜底拦截（此场景只能用原生确认框，自定义弹窗无效）
const handleBeforeUnload = (e: BeforeUnloadEvent) => {
  if (isMindmapDirty()) {
    e.preventDefault();
    e.returnValue = '';
  }
};
onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload));
onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload));

// 左栏树：测试点/模块切换 + 联动筛选
const treeMode = ref<'testpoint' | 'module'>('testpoint');

// ===== 左侧树：可拖拽伸缩 + 拖拽隐藏（与用例列表一致） =====
const SIDEBAR_DEFAULT_WIDTH = 240;
const SIDEBAR_MIN_WIDTH = 200;
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
const treeKeyword = ref('');
const testPointTreeData = ref<any[]>([]); // [{key,label,count}]
const selectedTreeKey = ref<number | string>(0); // 选中节点 id（0=全部）
// 受控展开：default-expand-all 只在挂载时计算一次，数据异步到达后不生效，改为数据到位后手动全展开
const treeExpandedKeys = ref<any[]>([]);

function expandAllTreeNodes() {
  const keys: any[] = [];
  const walk = (nodes: any[]) => nodes?.forEach(n => {
    const k = treeMode.value === 'testpoint' ? n.key : n.id;
    if (n.children?.length) {
      keys.push(k);
      walk(n.children);
    }
  });
  walk(filteredTreeData.value);
  treeExpandedKeys.value = keys;
}

// 树数据字段映射：测试点 {key,label,count} / 模块 {id,moduleName,caseCount,children}
const treeFieldNames = computed(() => treeMode.value === 'testpoint'
  ? { key: 'key', title: 'label', children: 'children' }
  : { key: 'id', title: 'moduleName', children: 'children' });

// 模块树：重算 caseCount 为计划内用例数（含子模块），getQaModuleList 返回的是项目用例数
// ponytail: O(n²) 每节点递归 collectSubModuleIds，模块数有限可接受，模块量大时改预计算
function recountModuleTree(nodes: any[]): any[] {
  return nodes.map(n => {
    const ids = collectSubModuleIds(moduleTreeData.value, n.id);
    return {
      ...n,
      caseCount: caseList.value.filter(c => c.moduleId != null && ids.has(c.moduleId)).length,
      children: n.children ? recountModuleTree(n.children) : [],
    };
  });
}

const filteredTreeData = computed(() => {
  const total = caseList.value.length;
  const kw = treeKeyword.value.trim().toLowerCase();
  if (treeMode.value === 'testpoint') {
    // 测试点挂在「全部测试点」根节点下，整棵树可折叠
    let children = testPointTreeData.value;
    if (kw) children = children.filter(n => (n.label || '').toLowerCase().includes(kw));
    return [{ key: 0, label: '全部测试点', count: total, children }];
  }
  // 模块树
  const moduleNodes = recountModuleTree(moduleTreeData.value);
  const root: any = { id: 0, moduleName: '全部模块', caseCount: total, children: moduleNodes };
  if (!kw) return [root];
  const filterNodes = (nodes: any[]): any[] => {
    const result: any[] = [];
    for (const n of nodes) {
      const c = n.children ? filterNodes(n.children) : [];
      if ((n.moduleName || '').toLowerCase().includes(kw) || c.length) result.push({ ...n, children: c });
    }
    return result;
  };
  return [{ ...root, children: filterNodes(moduleNodes) }];
});

const selectedTreeKeys = computed(() => [selectedTreeKey.value]);

// 加载测试点树（扁平，每个测试点一个节点，count=关联用例数）
async function loadTestPointTree() {
  if (!planId.value) return;
  const res: any = await getTestPoints(planId.value);
  if (res.code === 200) {
    testPointTreeData.value = (res.data?.testPoints || []).map((tp: any) => ({
      key: tp.id,
      label: tp.name || '默认测试点',
      count: tp.cases?.length || 0,
    }));
  }
}

function onTreeModeChange() {
  selectedTreeKey.value = 0;
  treeKeyword.value = '';
  selectedCaseKeys.value = [];
  nextTick(expandAllTreeNodes);
}

function onTreeSelect(keys: any[]) {
  selectedTreeKey.value = keys && keys.length ? keys[0] : 0;
  selectedCaseKeys.value = [];
}

// 批量选择
const selectedCaseKeys = ref<any[]>([]);

const filteredCaseList = computed(() => {
  let list = caseList.value;
  // 左栏树联动筛选：0=全部不筛选；测试点按 testPointId，模块按 moduleId（含子树）
  if (selectedTreeKey.value !== 0) {
    if (treeMode.value === 'testpoint') {
      list = list.filter(c => c.testPointId === selectedTreeKey.value);
    } else {
      const modIds = collectSubModuleIds(moduleTreeData.value, selectedTreeKey.value);
      list = list.filter(c => c.moduleId != null && modIds.has(c.moduleId));
    }
  }
  const kw = caseFilterKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter(c => (c.caseName || '').toLowerCase().includes(kw) || (c.caseCode || '').toLowerCase().includes(kw));
  }
  if (caseFilterResult.value) {
    const r = caseFilterResult.value;
    list = list.filter(c => (r === 'UNEXECUTED' ? (!c.executeResult || c.executeResult === 'UNEXECUTED') : c.executeResult === r));
  }
  if (caseFilterPriority.value) {
    list = list.filter(c => c.priority === caseFilterPriority.value);
  }
  return list;
});

// 收集模块及其所有子模块 id（模块树联动需含子模块）
function collectSubModuleIds(nodes: any[], targetId: any): Set<number> {
  const ids = new Set<number>();
  const walk = (list: any[]) => {
    for (const n of list) {
      if (n.id === targetId) { collectAll(n, ids); return true; }
      if (n.children && walk(n.children)) return true;
    }
    return false;
  };
  const collectAll = (node: any, set: Set<number>) => {
    set.add(node.id);
    (node.children || []).forEach((c: any) => collectAll(c, set));
  };
  walk(nodes);
  return ids;
}

// 脑图联动：左栏树选中的用例 id 集合（仅树联动，不含关键字/结果/优先级筛选；0=全部不过滤）
const mindmapFilterIds = computed<Set<number> | null>(() => {
  if (selectedTreeKey.value === 0) return null;
  let list = caseList.value;
  if (treeMode.value === 'testpoint') {
    list = list.filter(c => c.testPointId === selectedTreeKey.value);
  } else {
    const modIds = collectSubModuleIds(moduleTreeData.value, selectedTreeKey.value);
    list = list.filter(c => c.moduleId != null && modIds.has(c.moduleId));
  }
  return new Set(list.map(c => c.testCaseId));
});

// 脑图根节点文案：模块模式选中模块时显示该模块名，否则固定「功能用例」
const findModuleName = (nodes: any[], id: any): string => {
  for (const n of nodes) {
    if (n.id === id) return n.moduleName;
    if (n.children) {
      const hit = findModuleName(n.children, id);
      if (hit) return hit;
    }
  }
  return '';
};
const mindmapRootLabel = computed(() => {
  if (treeMode.value === 'module' && selectedTreeKey.value !== 0) {
    return findModuleName(moduleTreeData.value, selectedTreeKey.value) || '功能用例';
  }
  if (treeMode.value === 'testpoint' && selectedTreeKey.value !== 0) {
    return testPointTreeData.value.find(n => n.key === selectedTreeKey.value)?.label || '功能用例';
  }
  return '功能用例';
});
// 脑图平铺：选中具体测试点/模块时该节点作为根、用例平铺；模块模式选全部模块时按模块分组
const mindmapFlat = computed(() => selectedTreeKey.value !== 0);
const mindmapGroupByModule = computed(() => treeMode.value === 'module' && selectedTreeKey.value === 0);
// 扁平化模块树给脑图分组用（空模块也要出节点）
const flatModuleList = computed(() => {
  const out: { id: number; moduleName: string }[] = [];
  const walk = (nodes: any[]) => nodes.forEach(n => {
    out.push({ id: n.id, moduleName: n.moduleName });
    if (n.children) walk(n.children);
  });
  walk(moduleOptions.value);
  return out;
});

const handleResetCaseFilter = () => {
  caseFilterKeyword.value = '';
  caseFilterResult.value = '';
  caseFilterPriority.value = '';
  selectedCaseKeys.value = [];
};

const planBugList = computed(() => {
  const bugs: any[] = [];
  const seen = new Set();
  caseList.value.forEach((c: any) => {
    if (c.bugList) {
      c.bugList.forEach((b: any) => {
        if (!seen.has(b.id)) {
          seen.add(b.id);
          bugs.push(b);
        }
      });
    }
  });
  return bugs;
});

const planHistoryList = computed(() => {
  return caseList.value
    .filter((c: any) => c.executeResult && c.executeResult !== 'UNEXECUTED')
    .sort((a: any, b: any) => {
      const ta = a.executeTime ? new Date(a.executeTime).getTime() : 0;
      const tb = b.executeTime ? new Date(b.executeTime).getTime() : 0;
      return tb - ta;
    });
});

const handleBatchExecute = async (result: string) => {
  if (selectedCaseKeys.value.length === 0) return;
  try {
    const res: any = await batchExecutePlanCases(planId.value, selectedCaseKeys.value.map(k => Number(k)), result);
    if (res.code === 200) {
      Message.success(`批量执行 ${selectedCaseKeys.value.length} 条用例完成`);
      selectedCaseKeys.value = [];
      await loadPlanDetail();
    } else {
      Message.error(res.msg || '批量执行失败');
    }
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || '批量执行失败');
  }
};

const handleBatchRemove = async () => {
  if (selectedCaseKeys.value.length === 0) return;
  const ids = selectedCaseKeys.value.map(k => Number(k));
  try {
    for (const id of ids) {
      await removeCaseFromPlan(id);
    }
    Message.success(`批量移除 ${ids.length} 条用例完成`);
    selectedCaseKeys.value = [];
    await loadPlanDetail();
  } catch (e: any) {
    console.error(e);
    Message.error('批量移除失败');
    await loadPlanDetail();
  }
};

const handleClearSelection = () => {
  selectedCaseKeys.value = [];
};

// 脑图：双击用例集合节点 → 打开关联用例抽屉（复用 CaseSelectDrawer）
const caseSelectVisible = ref(false);
const currentTpId = ref<string>('');

const handleOpenCaseDrawer = (tpId: string) => {
  if (tpId === 'unassigned') {
    Message.info('未分配用例无需关联测试点，请在功能用例中管理');
    return;
  }
  currentTpId.value = tpId;
  caseSelectVisible.value = true;
};

// 关联用例确认：列表已剔除计划内用例，新选的加入计划，与已关联的合并后替换测试点
const handleCaseSelectConfirm = async (cases: any[]) => {
  const newIds = cases.map(c => c.id);
  if (newIds.length === 0) return;
  // 已关联用例不在抽屉列表，从缓存取回合并，避免 replaceTestPointCases 替换时清掉
  const existing = mindMapRef.value?.getTestPointCases?.(currentTpId.value) || [];
  const allIds = [...new Set([...existing.map((c: any) => c.testCaseId), ...newIds])];
  try {
    const res: any = await addCasesToPlan(planId.value, newIds);
    if (res.code !== 200) {
      Message.error(res.msg || '加入计划失败');
      return;
    }
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || '加入计划失败');
    return;
  }
  // 替换测试点全部关联（已有+新选）→ saveTestPoints → loadTestPoints 刷新脑图节点数，handleSave 提示“保存成功”
  await mindMapRef.value?.replaceTestPointCases?.(currentTpId.value, allIds);
  await loadPlanDetail();
};

const moduleOptions = ref<any[]>([]);

// 模块树：list 接口返回扁平数据，本地按 parentId 组装层级（否则二级及以下模块渲染不出来）
const moduleTreeData = computed(() => {
  const build = (pid: number): any[] =>
    moduleOptions.value
      .filter((m: any) => (m.parentId || 0) === pid)
      .map((m: any) => ({ ...m, children: build(m.id) }));
  return build(0);
});

// 数据到位后默认全展开（测试点树/模块树各自加载完成后）；注意必须在 moduleOptions 声明之后，否则 setup 期 TDZ
watch([testPointTreeData, moduleOptions], () => nextTick(expandAllTreeNodes));

// 预览
const previewVisible = ref(false);
const previewData = ref<any>({});

// BUG预览
const bugPreviewVisible = ref(false);
const bugPreviewData = ref<any>({});

// BUG详情抽屉（复用BUG池同款组件）
const bugDetailVisible = ref(false);
const currentBugId = ref<number | null>(null);
const handleOpenBugDetail = (record: any) => {
  currentBugId.value = record.id;
  bugDetailVisible.value = true;
};

// BUG列表抽屉（某用例下的全部BUG）
const bugListDrawerVisible = ref(false);
const bugListDrawerTitle = ref('');
const bugListDrawerCaseId = ref<number | null>(null);

// 提BUG弹窗
const bugModalVisible = ref(false);
const bugForm = ref<any>({});
const bugFormIsEdit = ref(false);
const bugFormPlanCaseId = ref<number | null>(null);
const bugFormCaseName = ref('');
const bugFormRequirementTitle = ref('');
const initialBugFileIds = ref<Set<string>>(new Set());
const uploadedBugFileIds = ref<Set<string>>(new Set());
const requirementOptions = ref<any[]>([]);
const testCaseOptions = ref<any[]>([]);
const userOptions = ref<any[]>([]);
const userPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });
const requirementPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });
const testCasePage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

// 富文本图片 fileId 提取
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

const collectBugFileIds = (): Set<string> => {
  const set = new Set<string>();
  extractFileIds(bugForm.value.description).forEach(id => set.add(id));
  extractFileIds(bugForm.value.reproduceSteps).forEach(id => set.add(id));
  return set;
};

const handleUploadedBugFile = (fileId: string) => {
  uploadedBugFileIds.value.add(fileId);
};


// 生成报告：落库快照并跳转报告详情页
const reportLoading = ref(false);
const handleGenerateReport = async () => {
  reportLoading.value = true;
  try {
    const res: any = await generatePlanReport(planId.value);
    if (res.code === 200 && res.data?.id) {
      router.push({ name: 'TestPlanReportDetail', params: { id: res.data.id } });
    } else {
      Message.error(res.msg || '生成报告失败');
    }
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || '生成报告失败');
  } finally {
    reportLoading.value = false;
  }
};

const resultStats = computed(() => {
  const stats = { pass: 0, fail: 0, block: 0, na: 0, unexec: 0, total: caseList.value.length };
  caseList.value.forEach((c: any) => {
    if (c.executeResult === 'PASS') stats.pass++;
    else if (c.executeResult === 'FAIL') stats.fail++;
    else if (c.executeResult === 'BLOCK') stats.block++;
    else if (c.executeResult === 'NA') stats.na++;
    else stats.unexec++;
  });
  return stats;
});

const loadPlanDetail = async () => {
  if (!planId.value) return;
  loading.value = true;
  try {
    const res: any = await getTestPlanDetail(planId.value);
    if (res.code === 200 && res.data) {
      planData.value = res.data.plan || {};
      caseList.value = res.data.cases || [];
      loadTestPointTree(); // 异步刷新左栏测试点树
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
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

// 编辑/复制/删除计划
const editModalVisible = ref(false);
const editForm = ref<any>({});

const showEditModal = () => {
  editForm.value = {...planData.value, tags: planData.value.tags ? String(planData.value.tags).split(',').filter(Boolean) : []};
  editModalVisible.value = true;
};

const handleSaveEdit = async () => {
  if (!editForm.value.planName || editForm.value.planName.trim() === '') {
    Message.warning('请输入计划名称');
    return;
  }
  try {
    const payload = {...editForm.value};
    if (Array.isArray(payload.tags)) payload.tags = payload.tags.join(',');
    const res: any = await updateTestPlan(payload);
    if (res.code === 200) {
      Message.success('保存成功');
      editModalVisible.value = false;
      await loadPlanDetail();
    }
  } catch (e) {
    console.error(e);
  }
};

// 复制计划：新建副本并复制关联用例，成功后跳转副本详情（同列表页逻辑）
const handleCopyPlan = async () => {
  try {
    const newPlan = {
      projectId: planData.value.projectId,
      planName: `${planData.value.planName}（副本）`,
      description: planData.value.description,
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
    if (caseList.value.length > 0) {
      await addCasesToPlan(newPlanId, caseList.value.map((c: any) => c.testCaseId));
    }
    Message.success('复制成功');
    router.push({ name: 'TestPlanDetail', params: { planId: newPlanId } });
  } catch (e) {
    console.error(e);
    Message.error('复制计划失败');
  }
};

const handleDeletePlan = async () => {
  try {
    const res: any = await deleteTestPlan(planId.value);
    if (res.code === 200) {
      Message.success('删除成功');
      router.push({ name: 'TestPlan' });
    }
  } catch (e) {
    console.error(e);
  }
};

// 移除用例
const handleUnrelateTestPoint = async (record: any) => {
  try {
    const res: any = await updateCaseTestPoint(planId.value, record.id, null);
    if (res.code === 200) { Message.success('已取消关联'); await loadPlanDetail(); }
  } catch (e) { console.error(e); }
};

// 行内编辑执行结果：选择即保存（executeCase 后端不做结果值校验，直接落库并写执行历史）
const handleInlineExecute = async (record: any, result: string) => {
  try {
    const res: any = await executePlanCase(record.id, result);
    if (res.code === 200) {
      Message.success('执行结果已记录');
      await loadPlanDetail();
    }
  } catch (e) {
    console.error(e);
    Message.error('保存失败');
  }
};

// 执行用例：跳转用例执行页
const goExecute = (record: any) => {
  router.push({ name: 'TestPlanExecute', params: { planId: planId.value }, query: { planCaseId: record.id } });
};

// 操作列：统一文字按钮模式，前2个常显，第3个起进「更多」下拉
interface PlanCaseOp {
  key: string;
  label: string;
  danger?: boolean;
  disabled?: boolean;
  permission?: string;
  handler: (record: any) => void;
}
// 取消关联用 Modal.confirm 替代 popconfirm，条件渲染在 opsFor 内按 testPointId 判断
const confirmUnrelateTestPoint = (record: any) => {
  Modal.warning({
    title: '取消关联',
    content: '确认取消关联该测试点吗？',
    okText: '确认取消关联',
    okButtonProps: { status: 'warning' },
    cancelText: '再想想',
    hideCancel: false,
    onOk: async () => {
      await handleUnrelateTestPoint(record);
    },
  });
};
const opsFor = (record: any): PlanCaseOp[] => {
  const ops: PlanCaseOp[] = [];
  if (record.testPointId) {
    ops.push({ key: 'unrelate', label: '取消关联', danger: true, handler: confirmUnrelateTestPoint });
  }
  ops.push({ key: 'execute', label: '执行', permission: 'qa:testplan:execute', handler: goExecute });
  return ops;
};
// 平铺规则：前2个固定平铺，其余进「更多」
const visibleOps = (record: any) => opsFor(record).slice(0, 2);
const overflowOps = (record: any) => opsFor(record).slice(2);
const guardAndRun = (op: PlanCaseOp, record: any) => {
  if (op.disabled) return;
  op.handler(record);
};

// 主表列定义（用例执行列表）
const columns = [
  { title: '用例编号', dataIndex: 'caseCode', width: 120, ellipsis: true, tooltip: true },
  { title: '用例名称', dataIndex: 'caseName', width: 200, ellipsis: true, tooltip: true, slotName: 'caseName' },
  { title: '测试点', dataIndex: 'testPointName', width: 140, ellipsis: true, tooltip: true },
  { title: '用例等级', dataIndex: 'priority', width: 80, slotName: 'priority' },
  { title: '标签', dataIndex: 'tags', width: 120, ellipsis: true, tooltip: true },
  { title: '创建时间', dataIndex: 'createTime', width: 180, ellipsis: true, tooltip: true },
  { title: '更新时间', dataIndex: 'updateTime', width: 180, ellipsis: true, tooltip: true },
  { title: '执行结果', dataIndex: 'executeResult', width: 90, ellipsis: true, tooltip: true, slotName: 'executeResult' },
  { title: '所属模块', dataIndex: 'moduleName', ellipsis: true, tooltip: true },
  { title: '缺陷数', width: 80, slotName: 'bugCount' },
  { title: '创建人', dataIndex: 'createUserName', width: 100, ellipsis: true, tooltip: true },
  { title: '执行人', dataIndex: 'executeUserName', width: 100, ellipsis: true, tooltip: true },
  { title: '操作', width: 200, fixed: 'right' as const, slotName: 'actions' },
];

// 缺陷列表子表列定义
const bugListColumns = [
  { title: 'BUG编号', dataIndex: 'bugCode', width: 120, ellipsis: true, tooltip: true },
  { title: 'BUG名称', dataIndex: 'title', width: 200, ellipsis: true, tooltip: true, slotName: 'bugTitle' },
  { title: '缺陷状态', dataIndex: 'status', width: 90, align: 'center', headerAlign: 'center', slotName: 'status' },
  { title: '关联用例数', dataIndex: 'caseCount', width: 110, align: 'center', headerAlign: 'center', ellipsis: true, tooltip: true, slotName: 'caseCount' },
  { title: '处理人', dataIndex: 'assigneeName', width: 100, align: 'center', headerAlign: 'center', ellipsis: true, tooltip: true, slotName: 'assigneeName' },
  { title: '创建时间', dataIndex: 'createTime', width: 180, align: 'center', headerAlign: 'center', ellipsis: true, tooltip: true, slotName: 'createTime' },
];

// 执行历史子表列定义
const historyColumns = [
  { title: '用例编号', dataIndex: 'caseCode', width: 120, ellipsis: true, tooltip: true },
  { title: '用例名称', dataIndex: 'caseName', width: 200, ellipsis: true, tooltip: true },
  { title: '执行结果', dataIndex: 'executeResult', width: 90, slotName: 'historyResult' },
  { title: '执行人', dataIndex: 'executeUserName', width: 100, ellipsis: true, tooltip: true },
  { title: '执行时间', dataIndex: 'executeTime', width: 180, ellipsis: true, tooltip: true },
  { title: '备注', dataIndex: 'executeRemark', width: 200, ellipsis: true, tooltip: true },
];

// 打开提BUG弹窗（新增）
const handleGenerateBug = async (record: any) => {
  bugFormIsEdit.value = false;
  bugFormPlanCaseId.value = record.id;
  bugFormCaseName.value = record.caseCode + ' - ' + record.caseName;
  // 从用例带出模块和需求
  bugForm.value = {
    projectId: projectStore.getProjectId,
    title: '',
    severity: 'NORMAL',
    priority: 'MEDIUM',
    status: 'NEW',
    environment: 'TEST',
    moduleId: undefined,
    tags: [],
    description: '',
    reproduceSteps: '',
    requirementId: undefined,
    testCaseId: record.testCaseId,
    assigneeId: undefined,
  };
  initialBugFileIds.value = new Set();
  uploadedBugFileIds.value = new Set();
  // 尝试从用例详情补全模块和需求
  if (record.testCaseId) {
    try {
      const res: any = await getTestCaseDetail(record.testCaseId);
      if (res.code === 200 && res.data) {
        const tc = res.data;
        bugForm.value.moduleId = tc.moduleId;
        bugForm.value.requirementId = tc.requirementId;
        // 自动填充复现步骤：前置条件 + 测试步骤
        const steps: string[] = [];
        if (tc.preCondition) {
          steps.push('【前置条件】\n' + tc.preCondition);
        }
        if (tc.testSteps && tc.testSteps.length) {
          tc.testSteps.forEach((s: any, i: number) => {
            steps.push(`步骤 ${i + 1}：${s.step}\n预期结果：${s.expected}`);
          });
        }
        bugForm.value.reproduceSteps = steps.join('\n\n');
        // 补全需求标题
        if (tc.requirementId) {
          const reqRes: any = await getRequirementDetail(tc.requirementId);
          if (reqRes.code === 200 && reqRes.data) {
            bugFormRequirementTitle.value = reqRes.data.title || '';
          }
        }
      }
    } catch (e) {
      console.error(e);
    }
  }
  // 预加载模块选项和用户选项
  await loadModuleOptions();
  handleSearchUser('');
  bugModalVisible.value = true;
};

// 提交BUG
const handleConfirmBug = async () => {
  if (!bugForm.value.title || bugForm.value.title.trim() === '') {
    Message.warning('请输入BUG标题');
    return;
  }
  try {
    const payload = { ...bugForm.value };
    if (payload.tags && Array.isArray(payload.tags)) {
      payload.tags = payload.tags.join(',');
    }
    let res: any;
    if (bugFormIsEdit.value) {
      res = await updateBug(payload);
    } else {
      if (!bugFormPlanCaseId.value) {
        Message.error('缺少计划用例ID');
        return;
      }
      res = await generateBugFromPlanCase(bugFormPlanCaseId.value, payload);
    }
    if (res.code === 200) {
      Message.success(bugFormIsEdit.value ? 'Bug 更新成功' : 'Bug 创建成功');
      // 计算并删除被移除的富文本图片
      const currentFileIds = collectBugFileIds();
      const allSessionFileIds = new Set<string>([...initialBugFileIds.value, ...uploadedBugFileIds.value]);
      const deletedFileIds = Array.from(allSessionFileIds).filter(id => !currentFileIds.has(id));
      uploadedBugFileIds.value.clear();
      if (deletedFileIds.length > 0) {
        deleteRichTextImages(deletedFileIds).catch(() => {
          // 图片删除失败不影响业务保存
        });
      }
      bugModalVisible.value = false;
      await loadPlanDetail();
    } else {
      Message.error(res.msg || '保存失败');
    }
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || '保存失败');
  }
};

// 指派人搜索
const handleSearchUser = async (keyword?: string) => {
  userPage.value.current = 1;
  userPage.value.keyword = keyword || '';
  userOptions.value = [];
  try {
    const res: any = await getUserListByPage({
      projectId: projectStore.getProjectId ?? undefined,
      username: userPage.value.keyword || undefined,
      pageNum: userPage.value.current,
      pageSize: userPage.value.pageSize
    });
    const records = res.data?.records || res.data || [];
    userOptions.value = records;
    userPage.value.hasMore = records.length >= userPage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};

const handleUserDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    handleSearchUser('');
  }
};

const severityText = (severity: string) => {
  const map: Record<string, string> = { FATAL: '致命', SERIOUS: '严重', NORMAL: '一般', TIPS: '提示' };
  return map[severity] || severity;
};
const severityColor = (severity: string) => {
  const map: Record<string, string> = { FATAL: 'red', SERIOUS: 'orange', NORMAL: 'blue', TIPS: 'gray' };
  return map[severity] || 'gray';
};
const bugStatusText = (status: string) => {
  const map: Record<string, string> = {
    NEW: '新建', CONFIRMED: '已确认', FIXING: '修复中', FIXED: '已修复',
    VERIFIED: '已验证', CLOSED: '已关闭', REJECTED: '已驳回'
  };
  return map[status] || status;
};
const bugStatusColor = (status: string) => {
  const map: Record<string, string> = {
    NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold', FIXED: 'blue',
    VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray'
  };
  return map[status] || 'gray';
};
const environmentText = (env: string) => {
  const map: Record<string, string> = { TEST: '测试环境', STAGING: '预发环境', PROD: '生产环境' };
  return map[env] || env || '-';
};
const closeReasonText = (reason: string) => {
  const map: Record<string, string> = {
    FIXED: '已修复', DUPLICATE: '重复', NOT_BUG: '不是BUG',
    CANNOT_REPRODUCE: '无法复现', WONT_FIX: '暂不处理'
  };
  return map[reason] || reason || '-';
};
const reproduceRateText = (rate: string) => {
  const map: Record<string, string> = { ALWAYS: '必现', OFTEN: '高概率', SOMETIMES: '偶现', RARE: '难现' };
  return map[rate] || rate || '-';
};

const handleOpenBugListDrawer = (record: any) => {
  bugListDrawerCaseId.value = record.id;
  bugListDrawerTitle.value = `${record.caseCode} 的关联BUG（${record.bugList?.length || 0}个）`;
  bugListDrawerVisible.value = true;
};

const handlePreviewBug = async (bugId: number) => {
  if (!bugId) return;
  try {
    const res: any = await getBugDetail(bugId);
    if (res.code === 200 && res.data) {
      bugPreviewData.value = res.data;
      bugPreviewVisible.value = true;
    }
  } catch (e) {
    console.error(e);
  }
};

// 从预览抽屉打开编辑BUG弹窗
const handleEditBugFromPreview = async () => {
  const bug = bugPreviewData.value;
  if (!bug || !bug.id) return;
  bugFormIsEdit.value = true;
  bugFormPlanCaseId.value = bug.planCaseId || null;
  bugFormCaseName.value = bug.caseName ? (bug.caseCode + ' - ' + bug.caseName) : '-';
  bugFormRequirementTitle.value = bug.requirementTitle || '-';
  bugForm.value = { ...bug };
  if (bugForm.value.tags && typeof bugForm.value.tags === 'string') {
    bugForm.value.tags = bugForm.value.tags.split(',').filter((t: string) => t.trim());
  } else {
    bugForm.value.tags = [];
  }
  initialBugFileIds.value = collectBugFileIds();
  uploadedBugFileIds.value = new Set();
  // 预加载模块选项和用户选项
  await loadModuleOptions();
  handleSearchUser('');
  bugPreviewVisible.value = false;
  bugModalVisible.value = true;
};

// 预览用例
const handlePreviewCase = (record: any) => {
  previewData.value = {...record};
  previewVisible.value = true;
};

const renderHtml = (content: string | undefined, defaultText: string = '暂无') => {
  if (!content || content.trim() === '') return defaultText;
  if (/<[a-z][\s\S]*?>/i.test(content)) return content;
  return content.replace(/\n/g, '<br>');
};

const caseTypeText = (type: string) => {
  const map: Record<string, string> = { FUNCTION: '功能', API: '接口', PERFORMANCE: '性能', COMPATIBILITY: '兼容', SMOKE: '冒烟' };
  return map[type] || type;
};

const priorityColor = (priority: string) => {
  const map: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue' };
  return map[priority] || 'gray';
};

const resultColor = (result: string) => {
  const map: Record<string, string> = { PASS: 'green', FAIL: 'red', BLOCK: 'orange', NA: 'gray', UNEXECUTED: 'gray' };
  return map[result] || 'gray';
};

const resultText = (result: string) => {
  const map: Record<string, string> = { PASS: '成功', FAIL: '失败', BLOCK: '阻塞', NA: '不适用', UNEXECUTED: '未执行' };
  return map[result] || result;
};

// 执行结果小图标（与 resultColor 颜色体系一致）
const resultIcon = (result: string) => {
  const map: Record<string, string> = {
    PASS: 'icon-check-circle-fill',
    FAIL: 'icon-close-circle-fill',
    BLOCK: 'icon-exclamation-circle-fill',
    NA: 'icon-minus-circle-fill',
    UNEXECUTED: 'icon-clock-circle',
  };
  return map[result] || 'icon-clock-circle';
};
// 执行结果图标颜色（用于下拉选项等非 tag 场景）
const resultIconColor = (result: string) => {
  const map: Record<string, string> = { PASS: '#00b42a', FAIL: '#f53f3f', BLOCK: '#ff7d00', NA: '#86909c', UNEXECUTED: '#86909c' };
  return map[result] || '#86909c';
};

const statusText = (status: string) => {
  const map: Record<string, string> = { DRAFT: '草稿', READY: '就绪', RUNNING: '执行中', COMPLETED: '已完成' };
  return map[status] || status;
};

onMounted(() => {
  loadPlanDetail();
  loadModuleOptions(); // 左栏模块树
});
</script>

<style scoped lang="less">
.test-plan-detail-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
  gap: 16px;
}

.plan-info-card {
  flex: 0 0 auto;
}

.plan-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.plan-title {
  font-size: 18px;
  font-weight: 700;
  color: #1d2129;
}
.plan-progress-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
}
.plan-progress-text {
  font-size: 13px;
  color: #4e5969;
  white-space: nowrap;
}
.plan-progress-bar-bg {
  display: flex;
  width: 25%;
  height: 8px;
  background: #e5e6eb;
  border-radius: 4px;
  overflow: hidden;
  margin-top: 8px;
}
/* 占比条分段 */
.plan-progress-bar-bg .seg {
  height: 100%;
  transition: width 0.4s ease;
}
.seg-pass { background: #00b42a; }
.seg-fail { background: #f53f3f; }
.seg-block { background: #ff7d00; }
.seg-unexec { background: #c9cdd4; }

/* 主 tab 填满剩余高度（参考 testCase 标准模式），让测试规划脑图撞满屏 */
.plan-main-tabs {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.plan-main-tabs :deep(.arco-tabs-content-wrapper) {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.plan-main-tabs :deep(.arco-tabs-content) {
  height: 100%;
  min-height: 0;
  overflow: hidden;
}
.plan-main-tabs :deep(.arco-tabs-content-list),
.plan-main-tabs :deep(.arco-tabs-pane) {
  height: 100%;
}
.plan-main-tabs :deep(.arco-tabs-pane) {
  overflow: auto;
}

/* tab 标题计数徽标（灰色） */
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

.text-ellipsis {
  display: inline-block;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 顶部基础信息行 */
.plan-meta-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  font-size: 13px;
  color: #86909c;
}
.plan-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.plan-meta-label {
  color: #86909c;
}
.plan-meta-value {
  color: #1d2129;
}
.plan-meta-divider {
  color: #e5e6eb;
}

/* 统计卡片 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 4px;
}
.stat-card {
  padding: 12px 16px;
  border-radius: 8px;
  background: #f7f8fa;
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 12px;
}
.stat-left-bar {
  width: 4px;
  height: 36px;
  border-radius: 2px;
  flex-shrink: 0;
}
.stat-body {
  flex: 1;
  min-width: 0;
}
.stat-number {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
}
.stat-label {
  font-size: 12px;
  color: #86909c;
  margin-top: 2px;
}
.stat-progress-wrap {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 0 12px 6px;
}
.stat-progress-bg {
  height: 4px;
  background: rgba(0,0,0,0.06);
  border-radius: 2px;
  overflow: hidden;
}
.stat-progress-fill {
  height: 100%;
  border-radius: 2px;
  transition: width 0.4s ease;
}
.stat-total .stat-number { color: rgb(var(--primary-6)); }
.stat-total .stat-left-bar { background: rgb(var(--primary-6)); }
.stat-total .stat-progress-fill { background: rgb(var(--primary-6)); }
.stat-pass .stat-number { color: rgb(var(--success-6)); }
.stat-pass .stat-left-bar { background: rgb(var(--success-6)); }
.stat-pass .stat-progress-fill { background: rgb(var(--success-6)); }
.stat-fail .stat-number { color: rgb(var(--danger-6)); }
.stat-fail .stat-left-bar { background: rgb(var(--danger-6)); }
.stat-fail .stat-progress-fill { background: rgb(var(--danger-6)); }
.stat-block .stat-number { color: rgb(var(--warning-6)); }
.stat-block .stat-left-bar { background: rgb(var(--warning-6)); }
.stat-block .stat-progress-fill { background: rgb(var(--warning-6)); }
.stat-unexec .stat-number { color: #86909c; }
.stat-unexec .stat-left-bar { background: #c9cdd4; }
.stat-unexec .stat-progress-fill { background: #c9cdd4; }

/* 操作列图标 */
.op-icons {
  :deep(.arco-btn) {
    padding: 0 4px;
    font-size: 15px;
  }
  :deep(.arco-btn .arco-icon) {
    font-size: 15px;
  }
}

/* BUG列表抽屉 */
.bug-drawer-item {
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border-2);
  cursor: pointer;
  transition: background 0.2s;
}
.bug-drawer-item:hover {
  background: var(--color-fill-2);
}
.bug-drawer-item:last-child {
  border-bottom: none;
}
.bug-drawer-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}
.bug-drawer-code {
  font-size: 13px;
  color: rgb(var(--primary-6));
  font-weight: 600;
}
.bug-drawer-severity {
  font-size: 12px;
  margin-left: auto;
}
.bug-drawer-title {
  font-size: 14px;
  color: #1d2129;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* BUG预览抽屉 */
.bug-preview-body {
  padding-bottom: 64px;
}
.bug-preview-title {
  font-size: 18px;
  font-weight: 600;
  color: #1d2129;
  line-height: 1.4;
  margin-bottom: 12px;
  word-break: break-all;
}
.bug-preview-tags {
  display: flex;
  gap: 8px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}
.bug-preview-meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px 16px;
  padding: 16px;
  background: #f7f8fa;
  border-radius: 8px;
  margin-bottom: 20px;
}
.meta-item {
  display: flex;
  gap: 8px;
  font-size: 13px;
  line-height: 1.6;
}
.meta-label {
  color: #86909c;
  white-space: nowrap;
  flex-shrink: 0;
}
.meta-value {
  color: #1d2129;
  word-break: break-all;
}
.bug-preview-section {
  margin-bottom: 20px;
}
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: rgb(var(--primary-6));
  margin-bottom: 10px;
  padding-left: 8px;
  border-left: 3px solid rgb(var(--primary-6));
}
.bug-preview-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 16px;
  background: var(--color-bg-2);
  border-top: 1px solid var(--color-border-2);
  text-align: right;
}

.rich-text-preview {
  line-height: 1.6;
  :deep(p) { margin: 8px 0; }
  :deep(ul), :deep(ol) { padding-left: 20px; margin: 8px 0; }
  :deep(blockquote) { border-left: 4px solid #ccc; padding-left: 10px; color: #666; margin: 8px 0; }
  :deep(img) { max-width: 100%; }
  :deep(a) { color: rgb(var(--primary-6)); }
}
.qa-edit-modal {
  :deep(.arco-modal-body) {
    padding: 0;
  }
}
.modal-scroll-body {
  max-height: calc(80vh - 120px);
  overflow-y: auto;
  padding: 16px 20px;
}

/* 测试报告 */
.report-loading {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 80px 0;
}
.plan-report-body {
  max-height: 600px;
  overflow-y: auto;
  padding: 0 8px;
}
.report-header {
  text-align: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e5e6eb;
}
.report-title {
  font-size: 20px;
  font-weight: 700;
  color: #1d2129;
  margin-bottom: 8px;
}
.report-meta {
  color: #86909c;
  font-size: 13px;
  display: flex;
  justify-content: center;
  gap: 16px;
  flex-wrap: wrap;
}
.report-desc {
  color: #4e5969;
  font-size: 13px;
  margin-top: 8px;
}
.report-section {
  margin-bottom: 24px;
}
.report-section-title {
  font-size: 15px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 12px;
  padding-left: 10px;
  border-left: 3px solid rgb(var(--primary-6));
}
.report-stat-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 12px;
}
.report-stat-item {
  text-align: center;
  padding: 14px 8px;
  background: #f7f8fa;
  border-radius: 8px;
}
.report-stat-item.pass { background: #e8ffea; }
.report-stat-item.pass .report-stat-num { color: #00b42a; }
.report-stat-item.fail { background: #ffece8; }
.report-stat-item.fail .report-stat-num { color: #f53f3f; }
.report-stat-item.block { background: #fff7e8; }
.report-stat-item.block .report-stat-num { color: #ff7d00; }
.report-stat-item.unexec { background: #f2f3f5; }
.report-stat-item.unexec .report-stat-num { color: #86909c; }
.report-stat-num {
  font-size: 22px;
  font-weight: 700;
  color: #1d2129;
  line-height: 1.2;
}
.report-stat-label {
  font-size: 12px;
  color: #86909c;
  margin-top: 4px;
}
.report-rate-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 13px;
  color: #4e5969;
  padding: 8px 0;
}
.report-bug-stats {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
}
.report-bug-group-title {
  font-size: 12px;
  color: #86909c;
  margin-bottom: 6px;
}
.report-bug-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
.report-bug-tag {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 12px;
}
.report-bug-tag.tag-red { background: #ffece8; color: #f53f3f; }
.report-bug-tag.tag-orange { background: #fff7e8; color: #ff7d00; }
.report-bug-tag.tag-gray { background: #f2f3f5; color: #86909c; }
.text-ellipsis-cell {
  display: block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.report-executor-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.report-executor-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: #f7f8fa;
  border-radius: 6px;
  font-size: 13px;
}
.report-executor-name {
  font-weight: 600;
  color: #1d2129;
}
.report-executor-count {
  color: #86909c;
}
.report-actions {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
  border-top: 1px solid #e5e6eb;
  margin-top: 8px;
}

/* 图表 */
.report-chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 24px;
}
.report-chart-box {
  background: #f7f8fa;
  border-radius: 8px;
  padding: 12px;
}
.report-chart-title {
  font-size: 13px;
  font-weight: 600;
  color: #1d2129;
  margin-bottom: 8px;
  text-align: center;
}

/* 模块进度条 */
.module-progress-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  position: relative;
  height: 18px;
  background: #e5e6eb;
  border-radius: 9px;
  overflow: hidden;
  padding-right: 8px;
}
.module-progress-bar {
  height: 100%;
  background: rgb(var(--primary-6));
  border-radius: 9px;
  transition: width 0.4s ease;
}
.module-progress-bar.module-progress-good {
  background: #00b42a;
}
.module-progress-bar.module-progress-bad {
  background: #f53f3f;
}
.module-progress-text {
  font-size: 11px;
  color: #4e5969;
  font-weight: 600;
  white-space: nowrap;
  z-index: 1;
}
.module-bad {
  color: #f53f3f;
  font-weight: 600;
}

/* 用例筛选工具栏 */
/* 功能用例：列表/脑图 视图切换容器 */
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
/* ===== 左右分栏 + 伸缩分隔条（与用例列表一致） ===== */
.main-row {
  display: flex;
  flex: 1;
  min-height: 0;
  height: 100%;
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
.case-tree-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
}
.tree-mode-switch {
  width: 100%;
  margin-bottom: 4px;
  padding: 0 4px;
  display: flex;
}
.tree-mode-switch :deep(.arco-radio-button) {
  flex: 1;
  text-align: center;
}
.tree-search {
  margin-bottom: 4px;
  padding: 0 4px;
}
.case-tree-scroll {
  flex: 1;
  overflow: auto;
  min-height: 0;
  padding: 0 4px;
}
.case-tree-scroll :deep(.arco-tree-node-title) {
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}
/* 计数钉在整行最右侧：节点容器全宽 + 绝对定位，而不是跟在名称后面 */
.case-tree-scroll :deep(.arco-tree-node) {
  position: relative;
}
.tree-node-title-row {
  display: flex;
  align-items: center;
}
.tree-node-label {
  display: inline-block;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.tree-node-count {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  color: #86909c;
  font-size: 12px;
}
.case-list-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.case-list-panel :deep(.arco-table) {
  flex: 1;
}

.case-filter-toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

/* 批量操作工具栏 */
.batch-toolbar {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #e8f3ff;
  border: 1px solid #bedaff;
  border-radius: 6px;
}
.batch-count {
  font-weight: 600;
  color: rgb(var(--primary-6));
  white-space: nowrap;
}

</style>
