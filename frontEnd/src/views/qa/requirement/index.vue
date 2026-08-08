<template>
  <div class="requirement-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.requirement']"/>
    <a-card class="requirement-card general-card" :title="$t('qa.requirement.title')">
      <!-- 顶部指标看板 -->
      <a-row class="stats-row" :gutter="16" style="margin-bottom: 16px">
        <a-col :xs="12" :sm="12" :md="6" :lg="6">
          <div class="metric-card metric-total" @click="handleReset">
            <div class="metric-content">
              <div class="metric-number">{{ statsData.total }}</div>
              <div class="metric-label">需求总数</div>
            </div>
            <div class="metric-icon" style="background: #e8f3ff; color: #165dff;">
              <IconList :size="22" />
            </div>
          </div>
        </a-col>
        <a-col :xs="12" :sm="12" :md="6" :lg="6">
          <div
            class="metric-card metric-developing"
            @click="
              searchStatus = 'DEVELOPING';
              handleSearch();
            "
          >
            <div class="metric-content">
              <div class="metric-number">{{ statsData.developing }}</div>
              <div class="metric-label">开发中</div>
            </div>
            <div class="metric-icon" style="background: #fff7e8; color: #ff7d00;">
              <IconTool :size="22" />
            </div>
          </div>
        </a-col>
        <a-col :xs="12" :sm="12" :md="6" :lg="6">
          <div
            class="metric-card metric-testing"
            @click="
              searchStatus = 'TESTING';
              handleSearch();
            "
          >
            <div class="metric-content">
              <div class="metric-number">{{ statsData.testing }}</div>
              <div class="metric-label">测试中</div>
            </div>
            <div class="metric-icon" style="background: #f5e8ff; color: #722ed1;">
              <IconExperiment :size="22" />
            </div>
          </div>
        </a-col>
        <a-col :xs="12" :sm="12" :md="6" :lg="6">
          <div
            class="metric-card metric-closed"
            @click="
              searchStatus = 'CLOSED';
              handleSearch();
            "
          >
            <div class="metric-content">
              <div class="metric-number">{{ statsData.closed }}</div>
              <div class="metric-label">已关闭</div>
            </div>
            <div class="metric-icon" style="background: #e8ffea; color: #00b42a;">
              <IconCheckCircle :size="22" />
            </div>
          </div>
        </a-col>
      </a-row>

      <!-- 高效过滤搜索区 -->
      <div class="filter-toolbar">
        <div class="filter-left">
          <a-button v-permission="QA_REQUIREMENT_CREATE" type="primary" class="add-btn" size="large" @click="handleAdd">
            <template #icon><IconPlus /></template>
            {{ $t('qa.requirement.add') }}
          </a-button>
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
        <div class="filter-right">
          <div class="filter-grid">
            <a-input
              v-model="searchKeyword"
              :placeholder="$t('qa.requirement.name')"
              allow-clear
              class="filter-input"
              @keydown.enter="handleSearch"
            />
            <a-select
              v-model="searchStatus"
              :placeholder="$t('qa.requirement.status')"
              allow-clear
              class="filter-select"
            >
              <a-option value="DRAFT">草稿</a-option>
              <a-option value="REVIEWING">评审中</a-option>
              <a-option value="CONFIRMED">已确认</a-option>
              <a-option value="DEVELOPING">开发中</a-option>
              <a-option value="TESTING">测试中</a-option>
              <a-option value="RELEASED">已上线</a-option>
              <a-option value="CLOSED">已关闭</a-option>
            </a-select>
            <a-select
              v-model="searchModuleId"
              placeholder="所属模块"
              allow-clear
              class="filter-select"
            >
              <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
            </a-select>
            <a-select
              v-model="searchReqType"
              placeholder="需求类型"
              allow-clear
              class="filter-select"
            >
              <a-option value="FEATURE">功能需求</a-option>
              <a-option value="BUGFIX">缺陷修复</a-option>
              <a-option value="OPTIMIZE">优化</a-option>
              <a-option value="TECH_DEBT">技术债务</a-option>
            </a-select>
            <a-select
              v-model="searchSource"
              placeholder="来源"
              allow-clear
              class="filter-select"
            >
              <a-option value="CLIENT">客户反馈</a-option>
              <a-option value="INTERNAL">内部规划</a-option>
              <a-option value="COMPETITOR">竞品分析</a-option>
              <a-option value="ONLINE">线上问题</a-option>
            </a-select>
          </div>
          <div class="filter-actions">
            <a-button size="large" @click="handleReset">重置</a-button>
            <a-button type="primary" size="large" :loading="loading" @click="handleSearch">
              <template #icon><IconSearch /></template>
              {{ $t('qa.common.search') }}
            </a-button>
          </div>
        </div>
      </div>

      <div class="table-wrapper">
        <a-skeleton v-if="loading" :animation="true" class="table-skeleton">
          <a-skeleton-line :rows="10" :widths="['100%']" />
        </a-skeleton>
        <a-table
            v-show="!loading"
            :data="requirementList"
            :pagination="pagination"
            :bordered="{ cell: true }"
            row-key="id"
            :sticky-header="true"
            :scroll="{ x: 1160 }"
            :row-selection="{ type: 'checkbox', onChange: handleSelectChange }"
            @page-change="handlePageChange"
            @page-size-change="handlePageSizeChange"
        >
        <template #columns>
          <a-table-column title="需求编号" data-index="reqCode" :width="120">
            <template #cell="{ record }">
              <span class="req-title-link" @click="handlePreview(record)">{{ record.reqCode }}</span>
            </template>
          </a-table-column>
          <a-table-column title="需求标题" data-index="title" :width="280" :ellipsis="true">
            <template #cell="{ record }">
              <a-tooltip :content="record.title">
                <span class="req-title-link" @click="handlePreview(record)">{{ record.title }}</span>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column v-if="fieldVis('reqType')" title="需求类型" data-index="reqType" :width="90">
            <template #cell="{ record }">
              <a-tag size="small">{{ reqTypeText(record.reqType) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="优先级" data-index="priority" :width="80">
            <template #cell="{ record }">
              <span class="priority-dot" :class="record.priority?.toLowerCase()">
                <span class="dot"></span>
                <span>{{ record.priority }}</span>
              </span>
            </template>
          </a-table-column>
          <a-table-column title="状态" data-index="status" :width="90">
            <template #cell="{ record }">
              <a-tag :color="statusColor(record.status)" size="small" class="status-pill">{{ statusText(record.status) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column v-if="fieldVis('tags')" title="标签" data-index="tags" :width="120" :ellipsis="true">
            <template #cell="{ record }">
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
          </a-table-column>
          <a-table-column title="关联用例" data-index="caseCount" :width="90">
            <template #cell="{ record }">
              <a-tag color="blue" size="small">{{ record.caseCount || 0 }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="关联BUG" data-index="bugCount" :width="90">
            <template #cell="{ record }">
              <a-tag :color="record.bugCount > 0 ? 'red' : 'green'" size="small">{{ record.bugCount || 0 }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="120" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-tooltip content="编辑">
                  <a-button v-permission="QA_REQUIREMENT_UPDATE" type="text" size="small" @click="handleEdit(record)">
                    <template #icon><icon-edit /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="删除">
                  <a-popconfirm
                      content="确认删除该需求吗？"
                      type="warning"
                      @ok="handleDelete(record.id)"
                  >
                    <a-button v-permission="QA_REQUIREMENT_DELETE" type="text" size="small" status="danger" @click.stop
                    >
                      <template #icon><icon-delete /></template>
                    </a-button>
                  </a-popconfirm>
                </a-tooltip>
                <a-dropdown position="bottom">
                  <a-button type="text" size="small" @click.stop>
                    <icon-more />
                  </a-button>
                  <template #content>
                    <a-doption @click="handleViewCases(record)"><icon-list /> 查看用例</a-doption>
                    <a-doption @click="handleViewBugs(record)"><icon-bug /> 查看BUG</a-doption>
                    <a-doption @click="handleShowTraceability(record)"><icon-branch /> 血缘追踪</a-doption>
                    <a-doption v-permission="QA_TEST_CASE_CREATE" @click="handleAiGenerate(record)"><icon-robot /> AI 生成用例</a-doption>
                    <a-doption v-permission="QA_REQUIREMENT_TRANSITION" @click="openTransitionModal(record)"><icon-swap /> 状态流转</a-doption>
                  </template>
                </a-dropdown>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      </div>
    </a-card>

    <!-- 关联用例抽屉 -->
    <a-drawer
        v-model:visible="drawerVisible"
        :title="drawerTitle"
        width="700px"
        :mask-closable="true"
        :footer="false"
    >
      <a-row justify="space-between" style="margin-bottom: 16px;">
        <a-button v-permission="QA_TEST_CASE_CREATE" type="primary" size="small" @click="handleAddCase">
          <template #icon><icon-plus /></template>
          新增用例
        </a-button>
      </a-row>
      <div class="drawer-table-wrapper">
        <a-table
            :data="drawerCaseList"
            :loading="drawerLoading"
            :pagination="drawerPagination"
            row-key="id"
            :sticky-header="true"
            :scroll="{ x: 780 }"
            @page-change="handleDrawerPageChange"
            @page-size-change="handleDrawerPageSizeChange"
        >
        <template #columns>
          <a-table-column title="用例编号" data-index="caseCode" :width="140" />
          <a-table-column title="用例名称" data-index="caseName" :width="250"/>
          <a-table-column title="类型" data-index="caseType" :width=80>
            <template #cell="{ record }">
              <a-tag>{{ caseTypeText(record.caseType) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="优先级" data-index="priority" :width=80>
            <template #cell="{ record }">
              <a-tag :color="priorityColor(record.priority)">{{ record.priority }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="关联BUG" data-index="bugCount" :width=90>
            <template #cell="{ record }">
              <a-tag :color="(record.bugCount || 0) > 0 ? 'red' : 'green'">{{ record.bugCount || 0 }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width=120 fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button v-permission="QA_TEST_CASE_UPDATE" type="text" size="small" @click="handleEditCase(record)">编辑</a-button>
                <a-popconfirm
                    content="确认删除该用例吗？"
                    type="warning"
                    @ok="handleDeleteCase(record.id)"
                >
                  <a-button v-permission="QA_TEST_CASE_DELETE" type="text" size="small" status="danger">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
        <template #empty>
          <a-empty description="暂无关联用例"/>
        </template>
      </a-table>
      </div>
    </a-drawer>

    <!-- 关联BUG抽屉 -->
    <a-drawer
        v-model:visible="bugDrawerVisible"
        :title="bugDrawerTitle"
        width="1000px"
        :mask-closable="true"
        :footer="false"
    >
      <a-row justify="space-between" style="margin-bottom: 16px;">
        <a-button v-permission="QA_BUG_CREATE" type="primary" size="small" @click="handleAddBug">
          <template #icon><icon-plus /></template>
          新增BUG
        </a-button>
      </a-row>
      <div class="drawer-table-wrapper">
        <a-table
            :data="drawerBugList"
            :loading="bugDrawerLoading"
            :pagination="bugDrawerPagination"
            row-key="id"
            :sticky-header="true"
            :scroll="{ x: 680 }"
            @page-change="handleBugDrawerPageChange"
            @page-size-change="handleBugDrawerPageSizeChange"
        >
        <template #columns>
          <a-table-column title="BUG编号" data-index="bugCode" :width="95">
            <template #cell="{ record }">
              <a-tooltip :content="record.bugCode" mini>
                <span class="text-ellipsis-inline">{{ record.bugCode }}</span>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="BUG标题" data-index="title" :width="140">
            <template #cell="{ record }">
              <a-tooltip :content="record.title" mini>
                <span class="text-ellipsis-inline">{{ record.title }}</span>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="严重程度" data-index="severity" :width="65">
            <template #cell="{ record }">
              <a-tag :color="severityColor(record.severity)" size="small">{{ severityText(record.severity) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="状态" data-index="status" :width="65">
            <template #cell="{ record }">
              <a-tag :color="bugStatusColor(record.status)" size="small">{{ bugStatusText(record.status) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="发现计划" data-index="planName" :width="110">
            <template #cell="{ record }">
              <a-tooltip :content="record.planName || '-'" mini>
                <span v-if="record.planName" class="text-ellipsis-inline">
                  <a-tag color="arcoblue" size="small">{{ record.planName }}</a-tag>
                </span>
                <span v-else style="color: #86909c;">-</span>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="关联用例" data-index="caseName" :width="100">
            <template #cell="{ record }">
              <a-tooltip :content="record.caseName || '-'" mini>
                <span v-if="record.caseName" class="text-ellipsis-inline" style="color: rgb(var(--primary-6)); cursor: pointer;" @click="handlePreviewCaseFromBug(record.testCaseId)">{{ record.caseName }}</span>
                <span v-else style="color: #86909c;">-</span>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="85">
            <template #cell="{ record }">
              <a-space>
                <a-button v-permission="QA_BUG_UPDATE" type="text" size="small" @click="handleEditBug(record)">编辑</a-button>
                <a-popconfirm
                    content="确认删除该BUG吗？"
                    type="warning"
                    @ok="handleDeleteBug(record.id)"
                >
                  <a-button v-permission="QA_BUG_DELETE" type="text" size="small" status="danger">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
        <template #empty>
          <a-empty description="暂无关联BUG"/>
        </template>
      </a-table>
      </div>
    </a-drawer>

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

    <!-- 预览抽屉 -->
    <a-drawer
        v-model:visible="previewVisible"
        width="640px"
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
        <!-- 基础属性 -->
        <a-card class="preview-section" :bordered="false" size="small">
          <a-descriptions :bordered="true" :data="previewDescriptions" layout="horizontal" :column="2" :label-style="{ fontWeight: 'bold', width: '90px' }" :value-style="{ textAlign: 'right' }" />
        </a-card>

        <!-- 需求描述 -->
        <a-card class="preview-section" title="需求描述" :bordered="false" size="small">
          <div class="preview-description rich-text-preview" v-html="renderHtml(previewData.description, '暂无描述')"></div>
        </a-card>

        <!-- 关联数据卡片 -->
        <a-card class="preview-section" :bordered="false" size="small">
          <div class="preview-stats">
            <div class="preview-stat-card stat-case" @click="handlePreviewStatClick('case')">
              <div class="stat-icon"><icon-file :size="22" /></div>
              <div class="stat-info">
                <div class="stat-value">{{ previewData.caseCount || 0 }}</div>
                <div class="stat-name">关联用例</div>
              </div>
            </div>
            <div class="preview-stat-card stat-bug" @click="handlePreviewStatClick('bug')">
              <div class="stat-icon"><icon-bug :size="22" /></div>
              <div class="stat-info">
                <div class="stat-value">{{ previewData.bugCount || 0 }}</div>
                <div class="stat-name">关联BUG</div>
              </div>
            </div>
            <div class="preview-stat-card stat-open-bug" @click="handlePreviewStatClick('bug')">
              <div class="stat-icon"><icon-exclamation-circle :size="22" /></div>
              <div class="stat-info">
                <div class="stat-value">{{ previewData.openBugCount || 0 }}</div>
                <div class="stat-name">未关闭BUG</div>
              </div>
            </div>
          </div>
        </a-card>

        <!-- 评论与操作日志：后端接口未落地（见 loadPreviewComments TODO），暂时隐藏，避免"发表成功但不落库"的假反馈 -->
        <a-card v-if="false" class="preview-section preview-tabs-section" :bordered="false" size="small">
          <a-tabs v-model:active-key="previewTabKey" type="line" class="preview-tabs">
            <a-tab-pane key="comments" title="评论">
              <div class="preview-comment-list">
                <div v-if="previewComments.length === 0" style="padding: 16px 0;">
                  <a-empty description="暂无评论" />
                </div>
                <div v-else>
                  <a-comment
                    v-for="comment in previewComments"
                    :key="comment.id"
                    :author="comment.userName"
                    :content="comment.content"
                    :datetime="comment.createTime"
                  />
                </div>
              </div>
              <div class="preview-comment-input" :class="{ focused: commentInputFocused }">
                <a-textarea
                  v-model="previewCommentInput"
                  placeholder="输入评论内容..."
                  :auto-size="{ minRows: 2, maxRows: 4 }"
                  @focus="commentInputFocused = true"
                  @blur="commentInputFocused = !!previewCommentInput.trim()"
                />
                <div v-show="commentInputFocused || previewCommentInput.trim()" class="preview-comment-actions">
                  <a-button v-permission="QA_REQUIREMENT_UPDATE" type="primary" size="small" @mousedown.prevent @click="handleSavePreviewComment">发表评论</a-button>
                </div>
              </div>
            </a-tab-pane>
            <a-tab-pane key="logs" title="操作日志">
              <div class="preview-log-list">
                <div v-if="previewLogs.length === 0" style="padding: 16px 0;">
                  <a-empty description="暂无操作日志" />
                </div>
                <a-timeline v-else>
                  <a-timeline-item
                    v-for="log in previewLogs"
                    :key="log.id"
                    :label="log.createTime"
                  >
                    <span style="font-weight: 500;">{{ log.userName }}</span>
                    <span style="color: var(--color-text-2); margin-left: 4px;">{{ log.action }}</span>
                    <span v-if="log.detail" style="color: var(--color-text-3); margin-left: 4px;">({{ log.detail }})</span>
                  </a-timeline-item>
                </a-timeline>
              </div>
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

    <!-- 状态流转弹窗 -->
    <a-modal
        v-model:visible="transitionModalVisible"
        title="状态流转"
        width="400px"
        @ok="handleConfirmTransition"
        @cancel="transitionModalVisible = false"
        :mask-closable="false"
    >
      <a-form :model="transitionForm" layout="vertical">
        <a-form-item label="当前状态">
          <a-tag :color="statusColor(transitionForm.currentStatus)">{{ statusText(transitionForm.currentStatus) }}</a-tag>
        </a-form-item>
        <a-form-item label="目标状态" required>
          <a-select v-model="transitionForm.targetStatus" placeholder="请选择目标状态">
            <a-option value="DRAFT">草稿</a-option>
            <a-option value="REVIEWING">评审中</a-option>
            <a-option value="CONFIRMED">已确认</a-option>
            <a-option value="DEVELOPING">开发中</a-option>
            <a-option value="TESTING">测试中</a-option>
            <a-option value="RELEASED">已上线</a-option>
            <a-option value="CLOSED">已关闭</a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 用例新增/编辑弹窗 -->
    <a-modal
        class="qa-edit-modal"
        v-model:visible="caseModalVisible"
        :title="caseModalTitle"
        :width="modalWidth"
        @ok="handleSaveCase"
        @cancel="caseModalVisible = false"
        :mask-closable="false"
    >
      <div class="modal-scroll-body">
        <a-form :model="caseFormData" layout="vertical">
          <!-- 基本信息 -->
          <a-divider orientation="left" style="margin-top: 0;">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">基本信息</span>
          </a-divider>
          <a-form-item label="用例名称" required>
            <a-input v-model="caseFormData.caseName" placeholder="请输入用例名称" size="large"/>
          </a-form-item>

          <!-- 分类属性 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">分类属性</span>
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="用例类型">
                <a-select v-model="caseFormData.caseType">
                  <a-option value="FUNCTION">功能</a-option>
                  <a-option value="API">接口</a-option>
                  <a-option value="PERFORMANCE">性能</a-option>
                  <a-option value="COMPATIBILITY">兼容</a-option>
                  <a-option value="SMOKE">冒烟</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="优先级">
                <a-select v-model="caseFormData.priority">
                  <a-option value="P0">P0</a-option>
                  <a-option value="P1">P1</a-option>
                  <a-option value="P2">P2</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="状态">
                <a-select v-model="caseFormData.status">
                  <a-option value="DRAFT">草稿</a-option>
                  <a-option value="REVIEWING">评审中</a-option>
                  <a-option value="REVIEWED">已评审</a-option>
                  <a-option value="DEPRECATED">已废弃</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="所属模块">
                <a-tree-select
                    v-model="caseFormData.moduleId"
                    :data="caseModuleTree"
                    :fieldNames="{ key: 'id', title: 'moduleName' }"
                    placeholder="请选择所属模块"
                    allow-clear
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="所属测试集">
                <a-select
                    v-model="caseFormData.setIds"
                    placeholder="请选择测试集"
                    multiple
                    allow-clear
                    :max-tag-count="2"
                >
                  <a-option v-for="set in caseSetOptions" :key="set.id" :value="set.id">{{ set.setName }}</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="预期执行时长(分钟)">
                <a-input-number v-model="caseFormData.expectDuration" :min="1" placeholder="分钟" style="width: 100%;"/>
              </a-form-item>
            </a-col>
          </a-row>

          <!-- 关联信息 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">关联信息</span>
          </a-divider>
          <a-form-item label="关联需求">
            <a-select
                v-model="caseFormData.requirementId"
                allow-clear
                allow-search
                :filter-option="false"
                placeholder="请输入关键词搜索需求"
                @search="handleSearchCaseRequirement"
                @dropdown-visible-change="handleCaseRequirementDropdownVisibleChange"
                @dropdown-reach-bottom="loadMoreCaseRequirement"
            >
              <a-option v-for="req in caseRequirementOptions" :key="req.id" :value="req.id">{{ req.reqCode }} - {{ req.title }}</a-option>
              <template #dropdownRender="{ menuNode: menu }">
                <div>
                  <VNodeRenderer :vnodes="menu" />
                  <div v-if="caseRequirementPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                    滚动加载更多...
                  </div>
                  <div v-else-if="caseRequirementOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                    没有更多了
                  </div>
                </div>
              </template>
            </a-select>
          </a-form-item>

          <!-- 测试内容 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">测试内容</span>
          </a-divider>
          <a-form-item label="前置条件">
            <RichEditor v-model="caseFormData.preCondition" placeholder="请输入前置条件" @uploaded="handleUploadedCaseFile"/>
          </a-form-item>
          <a-form-item label="测试步骤">
            <a-space direction="vertical" style="width: 100%">
              <div
                  v-for="(step, index) in caseStepList"
                  :key="index"
                  class="step-card"
              >
                <div class="step-card-header">
                  <span class="step-number">步骤 {{ index + 1 }}</span>
                  <a-space>
                    <a-button v-permission="QA_TEST_CASE_UPDATE" type="text" size="mini" :disabled="index === 0" @click="moveCaseStep(index, -1)">↑</a-button>
                    <a-button v-permission="QA_TEST_CASE_UPDATE" type="text" size="mini" :disabled="index === caseStepList.length - 1" @click="moveCaseStep(index, 1)">↓</a-button>
                    <a-button v-permission="QA_TEST_CASE_UPDATE" type="text" status="danger" size="mini" @click="removeCaseStep(index)">删除</a-button>
                  </a-space>
                </div>
                <a-row :gutter="16">
                  <a-col :span="12">
                    <a-form-item label="步骤描述" style="margin-bottom: 0;">
                      <a-textarea v-model="step.step" :placeholder="`步骤${index + 1}`" :auto-size="{ minRows: 2, maxRows: 8 }"/>
                    </a-form-item>
                  </a-col>
                  <a-col :span="12">
                    <a-form-item label="预期结果" style="margin-bottom: 0;">
                      <a-textarea v-model="step.expected" placeholder="预期结果" :auto-size="{ minRows: 2, maxRows: 8 }"/>
                    </a-form-item>
                  </a-col>
                </a-row>
              </div>
              <a-button v-permission="QA_TEST_CASE_UPDATE" type="dashed" long @click="addCaseStep">
                <icon-plus/> 添加步骤
              </a-button>
            </a-space>
          </a-form-item>
          <a-form-item label="标签">
            <a-input-tag v-model="caseFormData.tags" placeholder="输入标签后按回车" allow-clear/>
          </a-form-item>
        </a-form>
      </div>
    </a-modal>

    <!-- BUG 新增/编辑弹窗 -->
    <a-modal
        class="qa-edit-modal"
        v-model:visible="bugModalVisible"
        :title="bugModalTitle"
        :width="modalWidth"
        @ok="handleSaveBug"
        @cancel="bugModalVisible = false"
        :mask-closable="false"
    >
      <div class="modal-scroll-body">
        <a-form :model="bugFormData" layout="vertical">
          <!-- 基本信息 -->
          <a-divider orientation="left" style="margin-top: 0;">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">基本信息</span>
          </a-divider>
          <a-form-item label="BUG标题" required>
            <a-input v-model="bugFormData.title" placeholder="请输入BUG标题" size="large"/>
          </a-form-item>

          <!-- 严重程度 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">严重程度</span>
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="严重程度">
                <a-select v-model="bugFormData.severity">
                  <a-option value="FATAL">致命</a-option>
                  <a-option value="SERIOUS">严重</a-option>
                  <a-option value="NORMAL">一般</a-option>
                  <a-option value="TIPS">提示</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="优先级">
                <a-select v-model="bugFormData.priority">
                  <a-option value="URGENT">紧急</a-option>
                  <a-option value="HIGH">高</a-option>
                  <a-option value="MEDIUM">中</a-option>
                  <a-option value="LOW">低</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="状态">
                <a-select v-model="bugFormData.status">
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
                <a-select v-model="bugFormData.moduleId" allow-clear placeholder="请选择所属模块">
                  <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="环境">
                <a-select v-model="bugFormData.environment" placeholder="请选择环境" allow-clear>
                  <a-option value="TEST">测试环境</a-option>
                  <a-option value="STAGING">预发环境</a-option>
                  <a-option value="PROD">生产环境</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="截止日期">
                <a-date-picker v-model="bugFormData.deadline" value-format="YYYY-MM-DD" placeholder="请选择截止日期" style="width: 100%;"/>
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
                <a-input v-model="bugFormData.foundVersion" placeholder="如 v1.0.0"/>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="修复版本">
                <a-input v-model="bugFormData.fixedVersion" placeholder="如 v1.0.1"/>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="重现概率">
                <a-select v-model="bugFormData.reproduceRate" placeholder="请选择重现概率" allow-clear>
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
            <RichEditor v-model="bugFormData.description" placeholder="请输入BUG描述" @uploaded="handleUploadedBugFile"/>
          </a-form-item>
          <a-form-item label="复现步骤">
            <RichEditor v-model="bugFormData.reproduceSteps" placeholder="请输入复现步骤" @uploaded="handleUploadedBugFile"/>
          </a-form-item>

          <!-- 关联关系 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">关联关系</span>
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="12">
              <a-form-item label="关联需求">
                <a-select
                    v-model="bugFormData.requirementId"
                    allow-clear
                    allow-search
                    :filter-option="false"
                    placeholder="请输入关键词搜索需求"
                    @search="handleSearchBugRequirement"
                    @dropdown-visible-change="handleBugRequirementDropdownVisibleChange"
                    @dropdown-reach-bottom="loadMoreBugRequirement"
                >
                  <a-option v-for="req in bugRequirementOptions" :key="req.id" :value="req.id">{{ req.reqCode }} - {{ req.title }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="bugRequirementPage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        滚动加载更多...
                      </div>
                      <div v-else-if="bugRequirementOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        没有更多了
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="关联用例">
                <a-select
                    v-model="bugFormData.testCaseId"
                    allow-clear
                    allow-search
                    :filter-option="false"
                    placeholder="请输入关键词搜索用例"
                    @search="handleSearchBugTestCase"
                    @dropdown-visible-change="handleBugTestCaseDropdownVisibleChange"
                    @dropdown-reach-bottom="loadMoreBugTestCase"
                >
                  <a-option v-for="tc in bugTestCaseOptions" :key="tc.id" :value="tc.id">{{ tc.caseCode }} - {{ tc.caseName }}</a-option>
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div v-if="bugTestCasePage.hasMore" style="padding: 8px; text-align: center; color: #86909c;">
                        滚动加载更多...
                      </div>
                      <div v-else-if="bugTestCaseOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                        没有更多了
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>

          <!-- 其他 -->
          <a-divider orientation="left">
            <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">其他</span>
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="标签">
                <a-input-tag v-model="bugFormData.tags" placeholder="输入标签后按回车" allow-clear/>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item v-if="bugFormData.status === 'CLOSED'" label="关闭原因">
                <a-select v-model="bugFormData.closeReason" placeholder="请选择关闭原因" allow-clear>
                  <a-option value="FIXED">已修复</a-option>
                  <a-option value="DUPLICATE">重复</a-option>
                  <a-option value="NOT_BUG">不是BUG</a-option>
                  <a-option value="CANNOT_REPRODUCE">无法复现</a-option>
                  <a-option value="WONT_FIX">暂不处理</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="指派人">
                <a-select
                    v-model="bugFormData.assigneeId"
                    allow-clear
                    allow-search
                    :filter-option="false"
                    placeholder="请输入关键词搜索用户"
                    @search="handleSearchUser"
                    @dropdown-visible-change="handleUserDropdownVisibleChange"
                    @dropdown-reach-bottom="loadMoreUser"
                >
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
            </a-col>
          </a-row>
        </a-form>
      </div>
    </a-modal>

    <!-- 血缘追踪弹窗 -->
    <a-modal
        v-model:visible="traceModalVisible"
        :title="traceModalTitle"
        width="85%"
        :footer="false"
        :mask-closable="true"
    >
      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :span="6">
          <a-statistic title="关联用例" :value="traceData.caseCount || 0">
            <template #prefix><icon-file style="color: rgb(var(--arcoblue-6));" /></template>
          </a-statistic>
        </a-col>
        <a-col :span="6">
          <a-statistic title="关联BUG总数" :value="traceData.bugCount || 0">
            <template #prefix><icon-bug style="color: rgb(var(--red-6));" /></template>
          </a-statistic>
        </a-col>
        <a-col :span="6">
          <a-statistic title="未关闭BUG" :value="traceData.openBugCount || 0">
            <template #prefix><icon-exclamation-circle style="color: rgb(var(--orange-6));" /></template>
          </a-statistic>
        </a-col>
        <a-col :span="6">
          <a-statistic title="用例通过率" :value="tracePassRate">
            <template #suffix>%</template>
          </a-statistic>
        </a-col>
      </a-row>

      <a-row justify="space-between" align="center" style="margin-bottom: 8px;">
        <a-space>
          <a-switch v-model="traceFilterBugOnly" size="small" @change="handleTraceFilterChange" />
          <span style="font-size: 13px; color: var(--color-text-2);">仅显示有BUG的用例</span>
        </a-space>
        <a-space size="mini">
          <span style="font-size: 12px; color: var(--color-text-3);">🖱️ 滚轮缩放 · 拖拽移动 · 点击节点折叠/展开</span>
        </a-space>
      </a-row>

      <a-divider style="margin: 12px 0;" />

      <div v-if="!traceData.testCases || traceData.testCases.length === 0">
        <a-empty description="该需求暂无关联用例">
          <template #extra>
            <a-button v-permission="QA_TEST_CASE_CREATE" type="primary" size="small" @click="handleAddCaseFromTrace">
              <template #icon><icon-plus /></template>
              创建用例
            </a-button>
          </template>
        </a-empty>
      </div>
      <div v-else style="height: 500px;">
        <Chart :options="traceChartOption" height="100%" />
      </div>
    </a-modal>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script setup lang="ts">
import {ref, computed, onMounted, onBeforeUnmount, watch, nextTick} from 'vue';
import {useI18n} from 'vue-i18n';
import {useRouter} from 'vue-router';
import {Message, Modal} from '@arco-design/web-vue';
import {IconSearch, IconPlus, IconDelete, IconMore, IconBug, IconList, IconSwap, IconEdit, IconBranch, IconFile, IconExclamationCircle, IconTool, IconExperiment, IconStop, IconCheckCircle} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import RichEditor from '@/components/rich-editor/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import Chart from '@/components/chart/index.vue';
import RequirementForm from './components/RequirementForm.vue';
import AiGenerateChatModal from '@/components/ai-generate/AiGenerateChatModal.vue';
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
import {getRequirementList, getRequirementStats, getRequirementDetail, deleteRequirement, batchDeleteRequirement, getTestCaseList, getTestCaseDetail, transitionRequirementStatus, saveTestCase, updateTestCase, deleteTestCase, getQaModuleTree, getQaModuleList, getTestCaseSetOptions, getBugList, saveBug, updateBug, deleteBug, getRequirementTraceability} from '@/api/MyApi/qa';
import {getUserListByPage} from '@/api/MyApi/user';
import {deleteRichTextImages} from '@/api/MyApi/fileUpload';

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
const modalWidth = computed(() => Math.min(1200, Math.max(360, windowWidth.value * 0.94)));
onMounted(() => window.addEventListener('resize', handleWindowResize));
onBeforeUnmount(() => window.removeEventListener('resize', handleWindowResize));
const formData = ref<any>({});
const requirementFormRef = ref<any>(null);
const selectedKeys = ref<number[]>([]);

const drawerVisible = ref(false);
const drawerTitle = ref('');
const drawerCaseList = ref<any[]>([]);
const drawerLoading = ref(false);
const drawerPagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });
const currentRequirementId = ref<number | null>(null);
const caseModalVisible = ref(false);
const caseModalTitle = ref('');
const caseFormData = ref<any>({});
const isEditCase = ref(false);
const initialCaseFileIds = ref<Set<string>>(new Set());
const uploadedCaseFileIds = ref<Set<string>>(new Set());
const caseModuleTree = ref<any[]>([{id: 0, moduleName: '全部用例', children: []}]);
const caseSetOptions = ref<any[]>([]);
const caseStepList = ref<any[]>([{step: '', expected: ''}]);
const caseRequirementOptions = ref<any[]>([]);
const caseRequirementPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

// BUG 抽屉
const bugDrawerVisible = ref(false);
const bugDrawerTitle = ref('');
const drawerBugList = ref<any[]>([]);
const bugDrawerLoading = ref(false);
const bugDrawerPagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });
const currentRequirementIdForBug = ref<number | null>(null);
const currentRequirementRecord = ref<any>({});

// BUG 弹窗
const bugModalVisible = ref(false);
const bugModalTitle = ref('');
const bugFormData = ref<any>({});
const isEditBug = ref(false);
const initialBugFileIds = ref<Set<string>>(new Set());
const uploadedBugFileIds = ref<Set<string>>(new Set());
const bugRequirementOptions = ref<any[]>([]);
const bugRequirementPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });
const bugTestCaseOptions = ref<any[]>([]);
const bugTestCasePage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

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

const handleUploadedCaseFile = (fileId: string) => {
  uploadedCaseFileIds.value.add(fileId);
};

const handleUploadedBugFile = (fileId: string) => {
  uploadedBugFileIds.value.add(fileId);
};

// 血缘追踪弹窗
const traceModalVisible = ref(false);
const traceModalTitle = ref('');
const traceData = ref<any>({});
const traceChartOption = ref<any>({});
const tracePassRate = ref(0);
const traceCurrentReqId = ref<number | null>(null);
const traceFilterBugOnly = ref(false);

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
const transitionModalVisible = ref(false);
const transitionForm = ref<any>({});

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
  previewTabKey.value = 'comments';
  previewCommentInput.value = '';
  // 加载评论和操作日志（当前为模拟数据，后续可对接后端接口）
  loadPreviewComments(record.id);
  loadPreviewLogs(record.id);
  previewVisible.value = true;
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

const handleViewCases = (record: any) => {
  currentRequirementId.value = record.id;
  currentRequirementRecord.value = record;
  drawerTitle.value = `「${record.title}」关联用例`;
  drawerPagination.value = { current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] };
  drawerVisible.value = true;
  loadDrawerCases();
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

// ==================== BUG 抽屉相关方法 ====================
const handleViewBugs = (record: any) => {
  currentRequirementIdForBug.value = record.id;
  currentRequirementRecord.value = record;
  bugDrawerTitle.value = `「${record.title}」关联BUG`;
  bugDrawerPagination.value = { current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] };
  bugDrawerVisible.value = true;
  loadDrawerBugs();
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
        currentRequirementIdForBug.value,  // requirementId
        undefined,                         // caseId
        undefined,                         // moduleId
        undefined,                         // environment
        undefined,                         // reproduceRate
        undefined,                         // closeReason
        bugDrawerPagination.value.current,
        bugDrawerPagination.value.pageSize
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

const collectBugFileIds = (): Set<string> => {
  const set = new Set<string>();
  extractFileIds(bugFormData.value.description).forEach(id => set.add(id));
  extractFileIds(bugFormData.value.reproduceSteps).forEach(id => set.add(id));
  return set;
};

const handleAddBug = async () => {
  if (!currentRequirementIdForBug.value) {
    Message.warning('当前需求信息缺失，请重新打开关联BUG抽屉');
    return;
  }
  isEditBug.value = false;
  bugModalTitle.value = '新增BUG';
  bugFormData.value = {
    severity: 'NORMAL',
    priority: 'MEDIUM',
    status: 'NEW',
    projectId: projectStore.getProjectId,
    requirementId: currentRequirementIdForBug.value,
    moduleId: currentRequirementRecord.value?.moduleId || undefined,
    tags: []
  };
  // 预加载关联选项，并把当前需求插入首位
  bugRequirementOptions.value = [];
  bugTestCaseOptions.value = [];
  initialBugFileIds.value = new Set();
  uploadedBugFileIds.value = new Set();
  await loadBugRequirementOptions();
  const currentReq = currentRequirementRecord.value;
  if (currentReq && !bugRequirementOptions.value.find((r: any) => r.id === currentRequirementIdForBug.value)) {
    bugRequirementOptions.value.unshift({
      id: currentRequirementIdForBug.value,
      reqCode: currentReq.reqCode,
      title: currentReq.title
    });
  }
  bugModalVisible.value = true;
};

const handleEditBug = async (record: any) => {
  isEditBug.value = true;
  bugModalTitle.value = '编辑BUG';
  bugFormData.value = {...record};
  if (bugFormData.value.tags && typeof bugFormData.value.tags === 'string') {
    bugFormData.value.tags = bugFormData.value.tags.split(',').filter((t: string) => t.trim());
  }
  initialBugFileIds.value = collectBugFileIds();
  uploadedBugFileIds.value = new Set();
  // 预加载关联选项
  bugRequirementOptions.value = [];
  bugTestCaseOptions.value = [];
  await loadBugRequirementOptions();
  if (bugFormData.value.requirementId && !bugRequirementOptions.value.find((r: any) => r.id === bugFormData.value.requirementId)) {
    try {
      const res: any = await getRequirementDetail(bugFormData.value.requirementId);
      if (res.data) bugRequirementOptions.value = [res.data, ...bugRequirementOptions.value];
    } catch (e) { console.error(e); }
  }
  await loadBugTestCaseOptions();
  if (bugFormData.value.testCaseId && !bugTestCaseOptions.value.find((tc: any) => tc.id === bugFormData.value.testCaseId)) {
    try {
      const res: any = await getTestCaseDetail(bugFormData.value.testCaseId);
      if (res.data) bugTestCaseOptions.value = [res.data, ...bugTestCaseOptions.value];
    } catch (e) { console.error(e); }
  }
  bugModalVisible.value = true;
};

const handleSaveBug = async () => {
  if (!bugFormData.value.title) {
    Message.warning('请输入BUG标题');
    return;
  }
  try {
    const payload = {...bugFormData.value};
    if (payload.tags && Array.isArray(payload.tags)) {
      payload.tags = payload.tags.join(',');
    }
    const api = isEditBug.value ? updateBug : saveBug;
    const res: any = await api(payload);
    if (res.code === 200) {
      Message.success('保存成功');
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
      bugDrawerPagination.value.current = 1;
      await loadDrawerBugs();
      await loadData();
    }
  } catch (e) {
    console.error(e);
  }
};

const handleDeleteBug = async (id: number) => {
  try {
    const res: any = await deleteBug(id);
    if (res.code === 200) {
      Message.success('删除成功');
      await loadDrawerBugs();
      await loadData();
    }
  } catch (e) {
    console.error(e);
  }
};

// ==================== 血缘追踪弹窗 ====================
const handleShowTraceability = async (record: any) => {
  traceCurrentReqId.value = record.id;
  traceModalTitle.value = `「${record.title}」血缘图谱`;
  traceModalVisible.value = true;
  traceData.value = {};
  traceChartOption.value = {};
  try {
    const res: any = await getRequirementTraceability(record.id);
    if (res.code === 200 && res.data) {
      traceData.value = res.data;
      buildTraceChartOption(res.data, traceFilterBugOnly.value);
      calcPassRate(res.data);
    }
  } catch (e) {
    console.error(e);
  }
};

const calcPassRate = (data: any) => {
  const cases = data.testCases || [];
  if (cases.length === 0) {
    tracePassRate.value = 0;
    return;
  }
  const passed = cases.filter((tc: any) => tc.lastResult === 'PASS').length;
  tracePassRate.value = Math.round((passed / cases.length) * 100);
};

const ellipsis = (str: string, len: number) => {
  if (!str) return '';
  return str.length > len ? str.substring(0, len) + '…' : str;
};

const buildTraceChartOption = (data: any, filterBugOnly: boolean = false) => {
  const req = data.requirement || {};
  const cases = data.testCases || [];
  const directBugs = data.directBugs || [];

  // 构建 echarts tree 数据
  const children: any[] = [];

  // 用例分支（支持过滤：仅显示有BUG的用例）
  cases.forEach((tc: any) => {
    const bugs = tc.bugs || [];
    if (filterBugOnly && bugs.length === 0) {
      return; // 跳过无BUG的用例
    }
    const bugCount = bugs.length;
    const caseNode: any = {
      name: `${tc.caseCode} ${ellipsis(tc.caseName, 8)}${bugCount > 0 ? ` (${bugCount}BUG)` : ' (无BUG)'}`,
      fullTitle: `${tc.caseCode} ${tc.caseName} (${bugCount > 0 ? `${bugCount}个BUG` : '无BUG'})`,
      value: '用例',
      itemStyle: { color: '#00B42A', borderColor: '#00B42A' },
      label: {
        backgroundColor: '#E8FFEA',
        borderColor: '#00B42A',
        borderWidth: 1,
        borderRadius: 4,
        padding: [4, 8],
        color: '#333',
        fontSize: 12
      },
      children: []
    };
    bugs.forEach((bug: any) => {
      caseNode.children.push({
        name: `${bug.bugCode} ${ellipsis(bug.title, 6)} ${bugStatusText(bug.status)}·${severityText(bug.severity)}`,
        fullTitle: `${bug.bugCode} ${bug.title} — ${bugStatusText(bug.status)} · ${severityText(bug.severity)}`,
        value: 'BUG',
        itemStyle: { color: '#F53F3F', borderColor: '#F53F3F' },
        label: {
          backgroundColor: '#FFECE8',
          borderColor: '#F53F3F',
          borderWidth: 1,
          borderRadius: 4,
          padding: [4, 8],
          color: '#333',
          fontSize: 12
        }
      });
    });
    children.push(caseNode);
  });

  // 直接关联BUG分支
  if (directBugs.length > 0) {
    const directBugNode: any = {
      name: `直接关联BUG (${directBugs.length})`,
      value: '直接BUG',
      itemStyle: { color: '#F77234', borderColor: '#F77234' },
      label: {
        backgroundColor: '#FFF3E8',
        borderColor: '#F77234',
        borderWidth: 1,
        borderRadius: 4,
        padding: [4, 8],
        color: '#333'
      },
      children: []
    };
    directBugs.forEach((bug: any) => {
      directBugNode.children.push({
        name: `${bug.bugCode} ${ellipsis(bug.title, 6)} ${bugStatusText(bug.status)}·${severityText(bug.severity)}`,
        fullTitle: `${bug.bugCode} ${bug.title} — ${bugStatusText(bug.status)} · ${severityText(bug.severity)}`,
        value: 'BUG',
        itemStyle: { color: '#F53F3F', borderColor: '#F53F3F' },
        label: {
          backgroundColor: '#FFECE8',
          borderColor: '#F53F3F',
          borderWidth: 1,
          borderRadius: 4,
          padding: [4, 8],
          color: '#333',
          fontSize: 12
        }
      });
    });
    children.push(directBugNode);
  }

  traceChartOption.value = {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        return params.data.fullTitle || params.data.name;
      }
    },
    series: [{
      type: 'tree',
      data: [{
        name: `${req.reqCode || ''} ${ellipsis(req.title, 10)} (${cases.length}用例·${data.bugCount || 0}BUG)`,
        fullTitle: `${req.reqCode || ''} ${req.title || ''} (${cases.length}用例·${data.bugCount || 0}BUG)`,
        value: '需求',
        itemStyle: { color: '#165DFF', borderColor: '#165DFF' },
        label: {
          backgroundColor: '#E8F3FF',
          borderColor: '#165DFF',
          borderWidth: 1,
          borderRadius: 4,
          padding: [6, 10],
          fontWeight: 'bold',
          color: '#333',
          fontSize: 13
        },
        children
      }],
      top: '5%',
      left: '3%',
      bottom: '5%',
      right: '3%',
      symbol: 'roundRect',
      symbolSize: [10, 10],
      orient: 'LR',
      expandAndCollapse: true,
      roam: true,
      initialTreeDepth: 2,
      layerPadding: 240,
      nodePadding: 18,
      label: {
        position: 'right',
        verticalAlign: 'middle',
        align: 'left',
        fontSize: 12,
        lineHeight: 16
      },
      leaves: {
        label: {
          position: 'right',
          verticalAlign: 'middle',
          align: 'left'
        }
      },
      emphasis: {
        focus: 'descendant'
      },
      animationDuration: 550,
      animationDurationUpdate: 750,
      lineStyle: {
        color: '#C9CDD4',
        width: 1.5,
        curveness: 0.5
      }
    }]
  };
};

const handleTraceFilterChange = (val: any) => {
  buildTraceChartOption(traceData.value, val);
};

const handleAddCaseFromTrace = () => {
  traceModalVisible.value = false;
  nextTick(() => {
    if (traceCurrentReqId.value) {
      const record = requirementList.value.find((r: any) => r.id === traceCurrentReqId.value);
      if (record) {
        handleViewCases(record);
      }
    }
  });
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

// 预览抽屉统计卡片点击
const handlePreviewStatClick = (type: string) => {
  previewVisible.value = false;
  nextTick(() => {
    if (type === 'case') {
      handleViewCases(previewData.value);
    } else if (type === 'bug') {
      handleViewBugs(previewData.value);
    }
  });
};

// 从BUG抽屉点击关联用例，预览用例
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

const loadCaseModuleTree = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getQaModuleTree(projectStore.getProjectId);
    caseModuleTree.value = res.data || [{id: 0, moduleName: '全部用例', children: []}];
  } catch (e) {
    console.error(e);
  }
};

const loadCaseSetOptions = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getTestCaseSetOptions(projectStore.getProjectId);
    caseSetOptions.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const collectCaseFileIds = (): Set<string> => {
  const set = new Set<string>();
  extractFileIds(caseFormData.value.preCondition).forEach(id => set.add(id));
  caseStepList.value.forEach((s: any) => {
    extractFileIds(s.step).forEach(id => set.add(id));
    extractFileIds(s.expected).forEach(id => set.add(id));
  });
  return set;
};

const handleAddCase = async () => {
  if (!currentRequirementId.value) {
    Message.warning('当前需求信息缺失，请重新打开关联用例抽屉');
    return;
  }
  isEditCase.value = false;
  caseModalTitle.value = '新增用例';
  caseFormData.value = {
    caseType: 'FUNCTION',
    priority: 'P1',
    status: 'DRAFT',
    projectId: projectStore.getProjectId,
    requirementId: currentRequirementId.value,
    moduleId: undefined,
    setIds: [],
    tags: []
  };
  caseStepList.value = [{step: '', expected: ''}];
  initialCaseFileIds.value = new Set();
  uploadedCaseFileIds.value = new Set();
  // 预加载关联需求下拉框，并把当前需求插入首位
  caseRequirementOptions.value = [];
  await loadCaseRequirementOptions();
  const currentReq = currentRequirementRecord.value;
  if (currentReq && !caseRequirementOptions.value.find((r: any) => r.id === currentRequirementId.value)) {
    caseRequirementOptions.value.unshift({
      id: currentRequirementId.value,
      reqCode: currentReq.reqCode,
      title: currentReq.title
    });
  }
  await loadCaseModuleTree();
  await loadCaseSetOptions();
  caseModalVisible.value = true;
};

const handleEditCase = async (record: any) => {
  isEditCase.value = true;
  caseModalTitle.value = '编辑用例';
  caseFormData.value = {...record};
  if (caseFormData.value.tags && typeof caseFormData.value.tags === 'string') {
    caseFormData.value.tags = caseFormData.value.tags.split(',').filter((t: string) => t.trim());
  }
  if (!caseFormData.value.setIds) {
    caseFormData.value.setIds = [];
  }
  caseStepList.value = record.testSteps || [{step: '', expected: ''}];
  initialCaseFileIds.value = collectCaseFileIds();
  uploadedCaseFileIds.value = new Set();
  caseRequirementOptions.value = [];
  await loadCaseRequirementOptions();
  if (caseFormData.value.requirementId && !caseRequirementOptions.value.find((r: any) => r.id === caseFormData.value.requirementId)) {
    try {
      const res: any = await getRequirementDetail(caseFormData.value.requirementId);
      if (res.data) caseRequirementOptions.value = [res.data, ...caseRequirementOptions.value];
    } catch (e) { console.error(e); }
  }
  await loadCaseModuleTree();
  await loadCaseSetOptions();
  caseModalVisible.value = true;
};

const handleSaveCase = async () => {
  if (!caseFormData.value.caseName) {
    Message.warning('请输入用例名称');
    return;
  }
  try {
    const payload = {...caseFormData.value};
    payload.testSteps = caseStepList.value.filter((s: any) => s.step || s.expected);
    if (payload.tags && Array.isArray(payload.tags)) {
      payload.tags = payload.tags.join(',');
    }
    const api = isEditCase.value ? updateTestCase : saveTestCase;
    const res: any = await api(payload);
    if (res.code === 200) {
      Message.success('保存成功');
      // 计算并删除被移除的富文本图片
      const currentFileIds = collectCaseFileIds();
      const allSessionFileIds = new Set<string>([...initialCaseFileIds.value, ...uploadedCaseFileIds.value]);
      const deletedFileIds = Array.from(allSessionFileIds).filter(id => !currentFileIds.has(id));
      uploadedCaseFileIds.value.clear();
      if (deletedFileIds.length > 0) {
        deleteRichTextImages(deletedFileIds).catch(() => {
          // 图片删除失败不影响业务保存
        });
      }
      caseModalVisible.value = false;
      drawerPagination.value.current = 1;
      await loadDrawerCases();
    }
  } catch (e) {
    console.error(e);
  }
};

// 用例弹窗测试步骤操作
const addCaseStep = () => {
  caseStepList.value.push({step: '', expected: ''});
};
const removeCaseStep = (index: number) => {
  if (caseStepList.value.length <= 1) return;
  caseStepList.value.splice(index, 1);
};
const moveCaseStep = (index: number, direction: number) => {
  const newIndex = index + direction;
  if (newIndex < 0 || newIndex >= caseStepList.value.length) return;
  const temp = caseStepList.value[index];
  caseStepList.value[index] = caseStepList.value[newIndex];
  caseStepList.value[newIndex] = temp;
};

// 用例弹窗关联需求搜索
const loadCaseRequirementOptions = async (keyword?: string, isLoadMore = false) => {
  if (!projectStore.getProjectId) return;
  if (!isLoadMore) {
    caseRequirementPage.value.current = 1;
    caseRequirementPage.value.keyword = keyword || '';
    caseRequirementOptions.value = [];
  }
  try {
    const res: any = await getRequirementList(
        projectStore.getProjectId,
        caseRequirementPage.value.keyword || undefined,
        undefined, undefined, undefined, undefined,
        caseRequirementPage.value.current,
        caseRequirementPage.value.pageSize
    );
    const records = res.data?.records || [];
    if (isLoadMore) {
      caseRequirementOptions.value.push(...records);
    } else {
      caseRequirementOptions.value = records;
    }
    caseRequirementPage.value.hasMore = records.length >= caseRequirementPage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};
const loadMoreCaseRequirement = async () => {
  if (!caseRequirementPage.value.hasMore || caseRequirementPage.value.loading) return;
  caseRequirementPage.value.loading = true;
  try {
    caseRequirementPage.value.current++;
    await loadCaseRequirementOptions(undefined, true);
  } finally {
    caseRequirementPage.value.loading = false;
  }
};
const handleSearchCaseRequirement = (keyword: string) => {
  loadCaseRequirementOptions(keyword);
};
const handleCaseRequirementDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    loadCaseRequirementOptions('');
  }
};

// BUG弹窗关联需求搜索
const loadBugRequirementOptions = async (keyword?: string, isLoadMore = false) => {
  if (!projectStore.getProjectId) return;
  if (!isLoadMore) {
    bugRequirementPage.value.current = 1;
    bugRequirementPage.value.keyword = keyword || '';
    bugRequirementOptions.value = [];
  }
  try {
    const res: any = await getRequirementList(
        projectStore.getProjectId,
        bugRequirementPage.value.keyword || undefined,
        undefined, undefined, undefined, undefined,
        bugRequirementPage.value.current,
        bugRequirementPage.value.pageSize
    );
    const records = res.data?.records || [];
    if (isLoadMore) {
      bugRequirementOptions.value.push(...records);
    } else {
      bugRequirementOptions.value = records;
    }
    bugRequirementPage.value.hasMore = records.length >= bugRequirementPage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};
const loadMoreBugRequirement = async () => {
  if (!bugRequirementPage.value.hasMore || bugRequirementPage.value.loading) return;
  bugRequirementPage.value.loading = true;
  try {
    bugRequirementPage.value.current++;
    await loadBugRequirementOptions(undefined, true);
  } finally {
    bugRequirementPage.value.loading = false;
  }
};
const handleSearchBugRequirement = (keyword: string) => {
  loadBugRequirementOptions(keyword);
};
const handleBugRequirementDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    loadBugRequirementOptions('');
  }
};

// BUG弹窗关联用例搜索
const loadBugTestCaseOptions = async (keyword?: string, isLoadMore = false) => {
  if (!projectStore.getProjectId) return;
  if (!isLoadMore) {
    bugTestCasePage.value.current = 1;
    bugTestCasePage.value.keyword = keyword || '';
    bugTestCaseOptions.value = [];
  }
  try {
    const res: any = await getTestCaseList(
        projectStore.getProjectId,
        undefined, undefined,
        bugTestCasePage.value.keyword || undefined,
        undefined, undefined,
        bugTestCasePage.value.current,
        bugTestCasePage.value.pageSize
    );
    const records = res.data?.records || [];
    if (isLoadMore) {
      bugTestCaseOptions.value.push(...records);
    } else {
      bugTestCaseOptions.value = records;
    }
    bugTestCasePage.value.hasMore = records.length >= bugTestCasePage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};
const loadMoreBugTestCase = async () => {
  if (!bugTestCasePage.value.hasMore || bugTestCasePage.value.loading) return;
  bugTestCasePage.value.loading = true;
  try {
    bugTestCasePage.value.current++;
    await loadBugTestCaseOptions(undefined, true);
  } finally {
    bugTestCasePage.value.loading = false;
  }
};
const handleSearchBugTestCase = (keyword: string) => {
  loadBugTestCaseOptions(keyword);
};
const handleBugTestCaseDropdownVisibleChange = (visible: boolean) => {
  if (visible) {
    loadBugTestCaseOptions('');
  }
};

const handleDeleteCase = async (id: number) => {
  try {
    const res: any = await deleteTestCase(id);
    if (res.code === 200) {
      Message.success('删除成功');
      await loadDrawerCases();
    }
  } catch (e) {
    console.error(e);
  }
};

const openTransitionModal = (record: any) => {
  transitionForm.value = {
    requirementId: record.id,
    currentStatus: record.status,
    targetStatus: ''
  };
  transitionModalVisible.value = true;
};

const handleConfirmTransition = async () => {
  if (!transitionForm.value.targetStatus) {
    Message.warning('请选择目标状态');
    return;
  }
  try {
    const res: any = await transitionRequirementStatus(
      transitionForm.value.requirementId,
      transitionForm.value.targetStatus
    );
    if (res.code === 200) {
      Message.success('状态已更新');
      transitionModalVisible.value = false;
      await loadData();
    }
  } catch (e) {
    console.error(e);
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

.table-skeleton {
  padding: 16px;
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

// 高效过滤搜索区
.filter-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;

  .filter-left {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-shrink: 0;

    .add-btn {
      font-weight: 600;
    }
  }

  .filter-right {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 12px;
    flex: 1;
    min-width: 0;
  }

  .filter-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
    gap: 12px;
    width: 100%;
  }

  .filter-input,
  .filter-select {
    width: 100%;
  }

  .filter-actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    width: 100%;
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
