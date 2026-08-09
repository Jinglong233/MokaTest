<template>
  <div class="bug-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.bug']"/>
    <a-card class="bug-card general-card" :title="$t('qa.bug.title')">
      <!-- 危机指标看板 -->
      <a-row class="stats-row" :gutter="16" style="margin-bottom: 16px">
        <a-col :xs="12" :sm="12" :md="6" :lg="6">
          <div class="metric-card metric-total" @click="handleReset">
            <div class="metric-content">
              <div class="metric-number">{{ bugStats.total }}</div>
              <div class="metric-label">BUG总数</div>
            </div>
            <div class="metric-icon">
              <IconBug :size="22" />
            </div>
          </div>
        </a-col>
        <a-col :xs="12" :sm="12" :md="6" :lg="6">
          <div class="metric-card metric-open" @click="handleFilterByOpen">
            <div class="metric-content">
              <div class="metric-number">{{ bugStats.open }}</div>
              <div class="metric-label">未关闭</div>
            </div>
            <div class="metric-icon">
              <IconClockCircle :size="22" />
            </div>
          </div>
        </a-col>
        <a-col :xs="12" :sm="12" :md="6" :lg="6">
          <div class="metric-card metric-serious" @click="handleFilterBySerious">
            <div class="metric-content">
              <div class="metric-number">{{ bugStats.serious }}</div>
              <div class="metric-label">严重BUG</div>
            </div>
            <div class="metric-icon">
              <IconExclamationCircle :size="22" />
            </div>
          </div>
        </a-col>
        <a-col :xs="12" :sm="12" :md="6" :lg="6">
          <div class="metric-card metric-fatal" @click="handleFilterByFatal">
            <div class="metric-content">
              <div class="metric-number">{{ bugStats.fatal }}</div>
              <div class="metric-label">致命BUG</div>
            </div>
            <div class="metric-icon">
              <IconCloseCircle :size="22" />
            </div>
          </div>
        </a-col>
      </a-row>

      <!-- 聚合式筛选区 -->
      <div class="filter-toolbar">
        <div class="filter-grid labeled-filter-grid">
          <div class="filter-field">
            <span class="filter-field-label">BUG标题</span>
            <a-input
              v-model="searchKeyword"
              :placeholder="$t('qa.bug.name')"
              allow-clear
              size="large"
              class="filter-input"
              @press-enter="handleSearchWithLoading"
            />
          </div>
          <div class="filter-field">
            <span class="filter-field-label">BUG状态</span>
            <a-select
              v-model="searchStatus"
              :placeholder="$t('qa.bug.status')"
              allow-clear
              size="large"
              class="filter-select"
            >
              <a-option value="NEW">新建</a-option>
              <a-option value="CONFIRMED">已确认</a-option>
              <a-option value="FIXING">修复中</a-option>
              <a-option value="FIXED">已修复</a-option>
              <a-option value="VERIFIED">已验证</a-option>
              <a-option value="CLOSED">已关闭</a-option>
              <a-option value="REJECTED">已驳回</a-option>
            </a-select>
          </div>
          <div class="filter-field">
            <span class="filter-field-label">严重程度</span>
            <a-select
              v-model="searchSeverity"
              placeholder="请选择"
              allow-clear
              size="large"
              class="filter-select"
            >
              <a-option value="FATAL">致命</a-option>
              <a-option value="SERIOUS">严重</a-option>
              <a-option value="NORMAL">一般</a-option>
              <a-option value="TIPS">提示</a-option>
            </a-select>
          </div>
          <div class="filter-field">
            <span class="filter-field-label">优先级</span>
            <a-select
              v-model="searchPriority"
              placeholder="请选择"
              allow-clear
              size="large"
              class="filter-select"
            >
              <a-option value="URGENT">紧急</a-option>
              <a-option value="HIGH">高</a-option>
              <a-option value="MEDIUM">中</a-option>
              <a-option value="LOW">低</a-option>
            </a-select>
          </div>
          <div class="filter-field">
            <span class="filter-field-label">关联需求</span>
            <a-select
              v-model="searchRequirementId"
              placeholder="请选择"
              allow-clear
              allow-search
              :filter-option="false"
              size="large"
              class="filter-select"
              @search="handleSearchFilterRequirement"
              @dropdown-visible-change="handleFilterRequirementDropdownVisibleChange"
            >
              <a-option v-for="req in filterRequirementOptions" :key="req.id" :value="req.id">{{ req.title }}</a-option>
              <template #dropdownRender="{ menuNode: menu }">
                <div ref="filterRequirementDropdownRef">
                  <VNodeRenderer :vnodes="menu" />
                  <div v-if="filterRequirementPage.hasMore" style="height: 1px;"></div>
                  <div v-else-if="filterRequirementOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                    没有更多了
                  </div>
                </div>
              </template>
            </a-select>
          </div>
          <div class="filter-field">
            <span class="filter-field-label">关联用例</span>
            <a-select
              v-model="searchCaseId"
              placeholder="请选择"
              allow-clear
              allow-search
              :filter-option="false"
              size="large"
              class="filter-select"
              @search="handleSearchFilterCase"
              @dropdown-visible-change="handleFilterCaseDropdownVisibleChange"
            >
              <a-option v-for="tc in filterCaseOptions" :key="tc.id" :value="tc.id">{{ tc.caseName }}</a-option>
              <template #dropdownRender="{ menuNode: menu }">
                <div ref="filterCaseDropdownRef">
                  <VNodeRenderer :vnodes="menu" />
                  <div v-if="filterCasePage.hasMore" style="height: 1px;"></div>
                  <div v-else-if="filterCaseOptions.length > 0" style="padding: 8px; text-align: center; color: #86909c;">
                    没有更多了
                  </div>
                </div>
              </template>
            </a-select>
          </div>
          <div class="filter-field">
            <span class="filter-field-label">所属模块</span>
            <a-select
              v-model="searchModuleId"
              placeholder="请选择"
              allow-clear
              size="large"
              class="filter-select"
            >
              <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
            </a-select>
          </div>
          <div class="filter-field">
            <span class="filter-field-label">影响环境</span>
            <a-select
              v-model="searchEnvironment"
              placeholder="请选择"
              allow-clear
              size="large"
              class="filter-select"
            >
              <a-option value="TEST">测试环境</a-option>
              <a-option value="STAGING">预发环境</a-option>
              <a-option value="PROD">生产环境</a-option>
            </a-select>
          </div>
          <div class="filter-field">
            <span class="filter-field-label">重现概率</span>
            <a-select
              v-model="searchReproduceRate"
              placeholder="请选择"
              allow-clear
              size="large"
              class="filter-select"
            >
              <a-option value="ALWAYS">必现</a-option>
              <a-option value="OFTEN">高概率</a-option>
              <a-option value="SOMETIMES">偶现</a-option>
              <a-option value="RARE">难现</a-option>
            </a-select>
          </div>
        </div>

        <div class="filter-actions-bar">
          <div class="filter-actions-left">
            <a-button v-permission="QA_BUG_CREATE" type="primary" class="add-btn" size="large" @click="handleAdd">
              <template #icon><IconPlus /></template>
              {{ $t('qa.bug.add') }}
            </a-button>
            <a-button
              v-permission="QA_BUG_DELETE"
              v-if="selectedKeys.length > 0"
              status="danger"
              size="large"
              @click="handleBatchDelete"
            >
              <template #icon><IconDelete /></template>
              批量删除({{ selectedKeys.length }})
            </a-button>
          </div>
          <div class="filter-actions-right">
            <a-button size="large" @click="handleReset">{{ $t('qa.common.reset') }}</a-button>
            <a-button type="primary" size="large" :loading="searchLoading" @click="handleSearchWithLoading">
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
          :data="bugList"
          :pagination="pagination"
          :bordered="{ cell: true }"
          row-key="id"
          :sticky-header="bugList.length > 50"
          :scroll="{ x: 1280 }"
          :row-selection="{ type: 'checkbox', onChange: handleSelectChange }"
          @page-change="handlePageChange"
          @page-size-change="handlePageSizeChange"
        >
          <template #columns>
            <a-table-column title="BUG编号" data-index="bugCode" :width="140">
              <template #cell="{ record }">
                <span class="bug-code-cell">
                  <span class="bug-code-text">{{ record.bugCode }}</span>
                  <a-tooltip content="复制编号">
                    <span class="copy-icon" @click.stop="copyToClipboard(record.bugCode)">
                      <IconCopy :size="12" />
                    </span>
                  </a-tooltip>
                </span>
              </template>
            </a-table-column>
            <a-table-column title="BUG标题" data-index="title" :width="260">
              <template #cell="{ record }">
                <a-input
                  v-if="editingTitleId === record.id"
                  ref="titleInputRef"
                  v-model="editingTitleValue"
                  size="mini"
                  @blur="confirmTitleRename(record)"
                  @keydown.enter="confirmTitleRename(record)"
                  @keydown.esc="cancelTitleRename"
                />
                <a-tooltip v-else :content="record.title">
                  <span
                    class="bug-title-link"
                    @click="handlePreview(record)"
                    @dblclick="startEditTitle(record)"
                  >{{ record.title }}</span>
                </a-tooltip>
              </template>
            </a-table-column>
            <a-table-column title="严重程度" data-index="severity" :width="90">
              <template #cell="{ record }">
                <span class="severity-badge" :class="record.severity?.toLowerCase()">
                  {{ severityText(record.severity) }}
                </span>
              </template>
            </a-table-column>
            <a-table-column v-if="fieldVis('priority')" title="优先级" data-index="priority" :width="100">
              <template #cell="{ record }">
                <span class="priority-dot" :class="record.priority?.toLowerCase()">
                  <span class="dot"></span>
                  <span>{{ record.priority }}</span>
                </span>
              </template>
            </a-table-column>
            <a-table-column title="状态" data-index="status" :width="100">
              <template #cell="{ record }">
                <a-tag :color="bugStatusColor(record.status)" size="small" class="status-pill">
                  {{ bugStatusText(record.status) }}
                </a-tag>
              </template>
            </a-table-column>
            <a-table-column v-if="fieldVis('tags')" title="标签" data-index="tags" :width="120" :ellipsis="true">
              <template #cell="{ record }">
                <a-tooltip v-if="record.tags && record.tags.length > 0" :content="typeof record.tags === 'string' ? record.tags : record.tags.join(', ')">
                  <span>{{ typeof record.tags === 'string' ? record.tags : record.tags.join(', ') }}</span>
                </a-tooltip>
                <span v-else style="color: #86909c;">-</span>
              </template>
            </a-table-column>
            <a-table-column v-if="fieldVis('requirementId')" title="关联需求" data-index="requirementTitle" :width="160" :ellipsis="true">
              <template #cell="{ record }">
                <a-tooltip v-if="record.requirementTitle" :content="record.requirementTitle">
                  <span>{{ record.requirementTitle }}</span>
                </a-tooltip>
                <span v-else style="color: #86909c;">-</span>
              </template>
            </a-table-column>
            <a-table-column v-if="fieldVis('testCaseId')" title="关联用例" data-index="caseName" :width="160" :ellipsis="true">
              <template #cell="{ record }">
                <a-tooltip v-if="record.caseName" :content="record.caseName">
                  <span class="bug-case-link" @click="handlePreviewCase(record.testCaseId)">{{ record.caseName }}</span>
                </a-tooltip>
                <span v-else style="color: #86909c;">-</span>
              </template>
            </a-table-column>
            <a-table-column title="操作" :width="130" fixed="right">
              <template #cell="{ record }">
                <a-space>
                  <a-dropdown position="bottom">
                    <a-button v-permission="QA_BUG_TRANSITION" type="text" size="small" title="状态流转" @click.stop>
                      <template #icon><IconSwap /></template>
                    </a-button>
                    <template #content>
                      <a-doption
                        v-for="s in allBugStatuses"
                        :key="s.value"
                        :disabled="s.value === record.status"
                        @click="handleInlineTransition(record, s.value)"
                      >
                        {{ s.label }}
                      </a-doption>
                    </template>
                  </a-dropdown>
                  <a-tooltip content="编辑">
                    <a-button v-permission="QA_BUG_UPDATE" type="text" size="small" class="action-btn" @click="handleEdit(record)">
                      <template #icon><IconEdit /></template>
                    </a-button>
                  </a-tooltip>
                  <a-tooltip content="删除">
                    <a-popconfirm
                      content="确认删除该BUG吗？"
                      type="warning"
                      @ok="handleDelete(record.id)"
                    >
                      <a-button v-permission="QA_BUG_DELETE" type="text" size="small" class="action-btn danger" @click.stop>
                        <template #icon><IconDelete /></template>
                      </a-button>
                    </a-popconfirm>
                  </a-tooltip>
                </a-space>
              </template>
            </a-table-column>
          </template>
        </a-table>
      </div>
    </a-card>

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
          <a-tag :color="bugStatusColor(transitionForm.currentStatus)">{{ bugStatusText(transitionForm.currentStatus) }}</a-tag>
        </a-form-item>
        <a-form-item label="目标状态" required>
          <a-select v-model="transitionForm.targetStatus" placeholder="请选择目标状态">
            <a-option value="NEW">新建</a-option>
            <a-option value="CONFIRMED">已确认</a-option>
            <a-option value="FIXING">修复中</a-option>
            <a-option value="FIXED">已修复</a-option>
            <a-option value="VERIFIED">已验证</a-option>
            <a-option value="CLOSED">已关闭</a-option>
            <a-option value="REJECTED">已驳回</a-option>
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
      @cancel="modalVisible = false"
      :mask-closable="false"
    >
      <div class="modal-scroll-body">
        <BugForm
          ref="bugFormRef"
          :initial-data="formData"
        />
      </div>
    </a-modal>

    <!-- 预览抽屉 -->
    <a-drawer
      v-model:visible="previewVisible"
      width="620px"
      :mask-closable="true"
      :footer="false"
      class="bug-preview-drawer"
    >
      <div class="preview-drawer-inner">
        <!-- 头部 -->
        <div class="preview-header">
          <div class="preview-header-main">
            <div class="preview-code-row">
              <span class="preview-code-tag">
                <IconBug :size="12" />
                {{ previewData.bugCode }}
                <span class="preview-code-copy" @click="copyToClipboard(previewData.bugCode)">
                  <IconCopy :size="12" />
                </span>
              </span>
              <a-tag :color="bugStatusColor(previewData.status)" size="small" class="status-pill preview-status-tag">
                {{ bugStatusText(previewData.status) }}
              </a-tag>
            </div>
            <h2 class="preview-title">{{ previewData.title }}</h2>
          </div>
          <div class="preview-header-actions">
            <a-dropdown position="bottom">
              <a-button v-permission="QA_BUG_TRANSITION" type="text" size="small" title="状态流转" @click.stop>
                <template #icon><IconSwap /></template>
              </a-button>
              <template #content>
                <a-doption
                  v-for="s in allBugStatuses"
                  :key="s.value"
                  :disabled="s.value === previewData.status"
                  @click="handleInlineTransition(previewData, s.value)"
                >
                  {{ s.label }}
                </a-doption>
              </template>
            </a-dropdown>
            <a-tooltip content="编辑缺陷">
              <a-button v-permission="QA_BUG_UPDATE" type="text" size="small" @click="handleEditFromPreview">
                <template #icon><IconEdit /></template>
              </a-button>
            </a-tooltip>
            <a-tooltip content="关闭抽屉">
              <a-button type="text" size="small" @click="previewVisible = false">
                <template #icon><IconClose /></template>
              </a-button>
            </a-tooltip>
          </div>
        </div>

        <!-- 内容滚动区 -->
        <div class="preview-body">
          <!-- 元数据卡片 -->
          <div class="preview-meta-card">
            <div class="preview-meta-grid">
              <div class="preview-meta-item" v-if="fieldVis('moduleId')">
                <span class="preview-meta-label">所属模块</span>
                <span class="preview-meta-value">{{ previewData.moduleName || '-' }}</span>
              </div>
              <div class="preview-meta-item">
                <span class="preview-meta-label">严重程度</span>
                <span class="preview-meta-value">
                  <span class="severity-badge" :class="previewData.severity?.toLowerCase()">
                    {{ severityText(previewData.severity) }}
                  </span>
                </span>
              </div>
              <div class="preview-meta-item" v-if="fieldVis('priority')">
                <span class="preview-meta-label">优先级</span>
                <span class="preview-meta-value">
                  <span class="priority-dot" :class="previewData.priority?.toLowerCase()">
                    <span class="dot"></span>
                    <span>{{ previewData.priority }}</span>
                  </span>
                </span>
              </div>
              <div class="preview-meta-item" v-if="fieldVis('environment')">
                <span class="preview-meta-label">影响环境</span>
                <span class="preview-meta-value">{{ environmentText(previewData.environment) }}</span>
              </div>
              <div class="preview-meta-item" v-if="fieldVis('foundVersion')">
                <span class="preview-meta-label">发现版本</span>
                <span class="preview-meta-value">{{ previewData.foundVersion || '-' }}</span>
              </div>
              <div class="preview-meta-item" v-if="fieldVis('fixedVersion')">
                <span class="preview-meta-label">修复版本</span>
                <span class="preview-meta-value">{{ previewData.fixedVersion || '-' }}</span>
              </div>
              <div class="preview-meta-item" v-if="fieldVis('deadline')">
                <span class="preview-meta-label">截止日期</span>
                <span class="preview-meta-value">
                  <IconCalendar :size="14" /> {{ previewData.deadline || '-' }}
                </span>
              </div>
              <div class="preview-meta-item" v-if="fieldVis('closeReason')">
                <span class="preview-meta-label">关闭原因</span>
                <span class="preview-meta-value">{{ closeReasonText(previewData.closeReason) }}</span>
              </div>
            </div>
            <a-divider style="margin: 16px 0;" />
            <div class="preview-collab-row">
              <div class="preview-collab-item" v-if="fieldVis('assigneeId')">
                <span class="preview-collab-label">指派给</span>
                <a-avatar :size="28" :style="{ backgroundColor: stringToColor(previewData.assigneeName) }">
                  {{ avatarText(previewData.assigneeName) }}
                </a-avatar>
                <span class="preview-collab-name">{{ previewData.assigneeName || '未指派' }}</span>
              </div>
              <div class="preview-collab-item">
                <span class="preview-collab-label">报告人</span>
                <a-avatar :size="28" :style="{ backgroundColor: stringToColor(previewData.reporterName) }">
                  {{ avatarText(previewData.reporterName) }}
                </a-avatar>
                <span class="preview-collab-name">{{ previewData.reporterName || '-' }}</span>
              </div>
              <div class="preview-create-time">创建于 {{ previewData.createTime }}</div>
            </div>
          </div>

          <!-- 复现步骤 -->
          <div class="preview-section" v-if="fieldVis('reproduceSteps')">
            <div class="preview-section-title">
              <IconOrderedList :size="16" /> 复现步骤
            </div>
            <div class="preview-steps-box">
              <div
                v-if="previewData.reproduceSteps"
                class="rich-text-preview"
                v-html="renderStepsHtml(previewData.reproduceSteps)"
              />
              <a-empty v-else description="暂无复现步骤" />
            </div>
          </div>

          <!-- 缺陷描述 -->
          <div class="preview-section">
            <div class="preview-section-title">
              <IconFile :size="16" /> 缺陷描述
            </div>
            <div class="preview-desc-box">
              <div
                v-if="previewData.description"
                class="rich-text-preview"
                v-html="renderHtml(previewData.description)"
              />
              <a-empty v-else description="暂无描述" />
            </div>
          </div>

          <!-- 关联链条 -->
          <div class="preview-relation-row">
            <div
              v-if="fieldVis('requirementId')"
              class="preview-relation-card"
              :class="{ disabled: !previewData.requirementId }"
              @click="previewData.requirementId && openRequirementDrawer(previewData.requirementId)"
            >
              <div class="preview-relation-left">
                <div class="preview-relation-type">关联需求</div>
                <div class="preview-relation-name">{{ previewData.requirementTitle || '未关联需求' }}</div>
              </div>
              <IconLink v-if="previewData.requirementId" />
            </div>
            <div
              v-if="fieldVis('testCaseId')"
              class="preview-relation-card"
              :class="{ disabled: !previewData.testCaseId }"
              @click="previewData.testCaseId && handlePreviewCase(previewData.testCaseId)"
            >
              <div class="preview-relation-left">
                <div class="preview-relation-type">关联用例</div>
                <div class="preview-relation-name">{{ previewData.caseName || '未关联用例' }}</div>
              </div>
              <IconLink v-if="previewData.testCaseId" />
            </div>
          </div>

          <!-- 动态与协作 -->
          <a-tabs class="preview-tabs" type="rounded">
            <a-tab-pane key="comments" title="评论动态">
              <div class="preview-comment-list">
                <a-timeline v-if="commentList.length > 0">
                  <a-timeline-item v-for="item in commentList" :key="item.id">
                    <div class="preview-comment-item">
                      <a-avatar :size="32" :style="{ backgroundColor: stringToColor(item.createUserName) }">
                        {{ avatarText(item.createUserName) }}
                      </a-avatar>
                      <div class="preview-comment-main">
                        <div class="preview-comment-header">
                          <span class="preview-comment-user">{{ item.createUserName || '用户' + item.createUserId }}</span>
                          <span class="preview-comment-time">{{ relativeTime(item.createTime) }}</span>
                        </div>
                        <div class="preview-comment-bubble">{{ item.content }}</div>
                      </div>
                    </div>
                  </a-timeline-item>
                </a-timeline>
                <a-empty v-else description="暂无评论" />
              </div>
              <div class="preview-comment-input-area">
                <a-textarea
                  v-model="commentContent"
                  :auto-size="{ minRows: 1, maxRows: 4 }"
                  placeholder="输入评论..."
                  class="preview-comment-input"
                  @focus="commentInputFocused = true"
                  @blur="commentInputFocused = false"
                />
                <a-button
                  v-permission="QA_BUG_COMMENT_CREATE"
                  type="primary"
                  class="preview-comment-submit"
                  :class="{ visible: commentInputFocused || commentContent }"
                  @click="handleSaveComment"
                >
                  发表评论
                </a-button>
              </div>
            </a-tab-pane>
            <a-tab-pane key="logs" title="操作日志">
              <div class="preview-log-list">
                <a-timeline v-if="operationLogList.length > 0">
                  <a-timeline-item v-for="item in operationLogList" :key="item.id">
                    <div class="preview-log-header">
                      <span class="preview-log-time">{{ item.operateTime }}</span>
                    </div>
                    <div class="preview-log-content">
                      <span style="font-weight: bold;">{{ fieldNameText(item.fieldName) }}</span>
                      ：<span style="color: #86909c;">{{ item.oldValue || '-' }}</span>
                      → <span style="color: rgb(var(--arcoblue-6));">{{ item.newValue || '-' }}</span>
                    </div>
                  </a-timeline-item>
                </a-timeline>
                <a-empty v-else description="暂无操作日志" />
              </div>
            </a-tab-pane>
          </a-tabs>
        </div>

        <!-- 底部固定操作栏 -->
        <div class="preview-footer">
          <a-button @click="previewVisible = false">关闭</a-button>
          <a-dropdown position="top">
            <a-button v-permission="QA_BUG_TRANSITION" type="primary">
              状态流转
            </a-button>
            <template #content>
              <a-doption
                v-for="s in allBugStatuses"
                :key="s.value"
                :disabled="s.value === previewData.status"
                @click="handleInlineTransition(previewData, s.value)"
              >
                {{ s.label }}
              </a-doption>
            </template>
          </a-dropdown>
        </div>
      </div>
    </a-drawer>

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
          { label: '需求编号', value: requirementPreviewData.reqCode },
          { label: '需求类型', value: requirementPreviewData.reqType },
          { label: '优先级', value: requirementPreviewData.priority },
          { label: '状态', value: requirementPreviewData.status },
          { label: '所属模块', value: requirementPreviewData.moduleName || '-' },
          { label: '来源', value: requirementPreviewData.source },
          { label: '负责人', value: requirementPreviewData.ownerName || '-' },
          { label: '创建人', value: requirementPreviewData.createUserName || '-' },
          { label: '创建时间', value: requirementPreviewData.createTime }
        ]"
        layout="inline-vertical"
        :column="2"
        :label-style="{ fontWeight: 'bold' }"
      />
      <a-divider />
      <h4 style="margin-bottom: 8px;">需求描述</h4>
      <div class="preview-desc-box">
        <div class="rich-text-preview" v-html="renderHtml(requirementPreviewData.description, '暂无描述')" />
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
      <a-descriptions :data="[
        { label: '用例编号', value: casePreviewData.caseCode },
        { label: '用例类型', value: casePreviewData.caseType },
        { label: '优先级', value: casePreviewData.priority },
        { label: '状态', value: casePreviewData.status },
        { label: '创建人', value: casePreviewData.createUserName || '-' },
        { label: '创建时间', value: casePreviewData.createTime }
      ]" layout="inline-vertical" :column="2" :label-style="{ fontWeight: 'bold' }" />
      <a-divider />
      <h4 style="margin-bottom: 8px;">前置条件</h4>
      <div class="preview-desc-box">
        <div class="rich-text-preview" v-html="renderHtml(casePreviewData.preCondition, '无')" />
      </div>
      <a-divider />
      <h4 style="margin-bottom: 8px;">测试步骤</h4>
      <div v-if="casePreviewData.testSteps && casePreviewData.testSteps.length">
        <div v-for="(step, index) in casePreviewData.testSteps" :key="index" style="margin-bottom: 12px;">
          <div style="font-weight: bold; color: #86909c; margin-bottom: 4px;">步骤 {{ index + 1 }}</div>
          <div class="preview-desc-box">
            <div class="rich-text-preview" v-html="renderHtml(step.step, '-')" />
          </div>
          <div style="color: #86909c; margin-top: 4px;">预期结果：</div>
          <div class="preview-desc-box">
            <div class="rich-text-preview" v-html="renderHtml(step.expected, '-')" />
          </div>
        </div>
      </div>
      <a-empty v-else description="暂无步骤" />
    </a-drawer>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'Bug' };
</script>

<script setup lang="ts">
import {ref, computed, onMounted, onBeforeUnmount, watch, nextTick} from 'vue';
import {useI18n} from 'vue-i18n';
import {useRouter} from 'vue-router';
import {Message, Modal} from '@arco-design/web-vue';
import {
  IconSearch, IconPlus, IconDelete, IconSwap, IconEdit,
  IconCopy, IconBug, IconExclamationCircle,
  IconCloseCircle, IconClockCircle, IconClose, IconCalendar,
  IconOrderedList, IconFile, IconLink
} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import RichEditor from '@/components/rich-editor/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import BugForm from './components/BugForm.vue';
import {useProjectStore} from '@/store';
import useProjectConfigStore from '@/store/modules/projectConfig';
import {useRoute} from 'vue-router';
import {
  QA_BUG_CREATE, QA_BUG_UPDATE, QA_BUG_DELETE, QA_BUG_TRANSITION,
  QA_BUG_COMMENT_CREATE
} from '@/constants/permissions';
import {
  getBugList, getBugStats, deleteBug, batchDeleteBug, transitionBugStatus,
  getTestCaseList, getRequirementList, getRequirementDetail,
  getTestCaseDetail, getQaModuleList, getBugCommentList,
  saveBugComment, getBugOperationLogList, getBugDetail, updateBug
} from '@/api/MyApi/qa';

const {t} = useI18n();
const projectStore = useProjectStore();
const projectConfigStore = useProjectConfigStore();
const fieldVis = (key: string) => projectConfigStore.isFieldVisible('bug', key);
const router = useRouter();
const route = useRoute();

// 用于渲染 dropdownRender 插槽中的 VNode
const VNodeRenderer = {
  props: ['vnodes'],
  render(this: { vnodes: any }) {
    return this.vnodes;
  }
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
const pagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });

const modalVisible = ref(false);

// 编辑弹窗宽度跟随浏览器窗口自适应：最宽 1200px，窄屏留 3vw 边距
const windowWidth = ref(window.innerWidth);
const handleWindowResize = () => {
  windowWidth.value = window.innerWidth;
};
const modalWidth = computed(() => Math.min(1200, Math.max(360, windowWidth.value * 0.94)));
onMounted(() => window.addEventListener('resize', handleWindowResize));
onBeforeUnmount(() => window.removeEventListener('resize', handleWindowResize));
const modalTitle = ref('');
const formData = ref<any>({});
const bugFormRef = ref<any>(null);
const selectedKeys = ref<number[]>([]);
const previewVisible = ref(false);
const previewData = ref<any>({});
const casePreviewVisible = ref(false);
const casePreviewData = ref<any>({});
const commentContent = ref('');
const commentList = ref<any[]>([]);
const operationLogList = ref<any[]>([]);

// 预览抽屉状态
const requirementPreviewVisible = ref(false);
const requirementPreviewData = ref<any>({});
const commentInputFocused = ref(false);

const filterRequirementOptions = ref<any[]>([]);
const filterRequirementPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

const filterCasePage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

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

const handleSearch = () => {
  pagination.value.current = 1;
  loadData();
};

const handleSearchWithLoading = () => {
  searchLoading.value = true;
  handleSearch();
  setTimeout(() => {
    searchLoading.value = false;
  }, 1500);
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
    Message.success('已复制');
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
      Message.success('已复制');
    } catch (err) {
      Message.error('复制失败');
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
    Message.warning('标题不能为空');
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
      Message.error('获取BUG详情失败');
      return;
    }
    const fullData = detailRes.data;
    fullData.title = newTitle;
    const res: any = await updateBug(fullData);
    if (res.code === 200) {
      Message.success('修改成功');
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
      Message.success('状态已更新');
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

const handlePreview = (record: any) => {
  previewData.value = {...record};
  previewVisible.value = true;
  commentContent.value = '';
  loadBugComments(record.id);
  loadBugOperationLogs(record.id);
};

/**
 * 检查是否有消息通知跳转过来的高亮 BUG
 */
const checkHighlightBug = async () => {
  const highlightId = route.query.highlight;
  if (!highlightId) return;
  const bugId = Number(highlightId);
  if (isNaN(bugId)) return;

  const record = bugList.value.find((item) => item.id === bugId);
  if (record) {
    handlePreview(record);
  } else {
    // 当前页没有，尝试查详情打开预览
    try {
      const res: any = await getBugDetail(bugId);
      if (res.code === 200 && res.data) {
        handlePreview(res.data);
      }
    } catch (e) {
      console.error('加载高亮BUG失败', e);
    }
  }
};

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

const loadBugComments = async (bugId: number) => {
  try {
    const res: any = await getBugCommentList(bugId);
    commentList.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const loadBugOperationLogs = async (bugId: number) => {
  try {
    const res: any = await getBugOperationLogList(bugId);
    operationLogList.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const handleSaveComment = async () => {
  if (!commentContent.value || commentContent.value.trim() === '') {
    Message.warning('请输入评论内容');
    return;
  }
  try {
    const res: any = await saveBugComment({
      bugId: previewData.value.id,
      content: commentContent.value.trim()
    });
    if (res.code === 200) {
      Message.success('评论发表成功');
      commentContent.value = '';
      await loadBugComments(previewData.value.id);
    }
  } catch (e) {
    console.error(e);
  }
};

const fieldNameText = (fieldName: string) => {
  const map: Record<string, string> = {
    title: '标题', severity: '严重程度', priority: '优先级', status: '状态',
    assigneeId: '指派人', moduleId: '所属模块', environment: '环境',
    deadline: '截止日期', fixedVersion: '修复版本', closeReason: '关闭原因'
  };
  return map[fieldName] || fieldName;
};

const handleEditFromPreview = () => {
  previewVisible.value = false;
  nextTick(() => {
    handleEditInModal(previewData.value);
  });
};

const handleAdd = () => {
  router.push({name: 'BugEdit'});
};

const handleEdit = (record: any) => {
  router.push({name: 'BugEdit', params: {id: record.id}});
};

// 预览抽屉里继续用弹窗编辑
const handleEditInModal = (record: any) => {
  modalTitle.value = t('qa.bug.edit');
  formData.value = {...record};
  if (formData.value.tags && typeof formData.value.tags === 'string') {
    formData.value.tags = formData.value.tags.split(',').filter((t: string) => t.trim());
  }
  modalVisible.value = true;
};

// ========== 筛选栏下拉滚动加载 ==========

const loadFilterRequirementOptions = async (keyword?: string, isLoadMore = false) => {
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
        undefined, undefined, undefined, undefined,
        filterRequirementPage.value.current,
        filterRequirementPage.value.pageSize
    );
    const records = res.data?.records || [];
    if (isLoadMore) {
      filterRequirementOptions.value.push(...records);
    } else {
      filterRequirementOptions.value = records;
    }
    filterRequirementPage.value.hasMore = records.length >= filterRequirementPage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};

const loadMoreFilterRequirement = async () => {
  if (!filterRequirementPage.value.hasMore || filterRequirementPage.value.loading) return;
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
  const exists = filterRequirementOptions.value.some((r: any) => r.id === searchRequirementId.value);
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

const loadFilterCaseOptions = async (keyword?: string, isLoadMore = false) => {
  if (!projectStore.getProjectId) return;
  if (!isLoadMore) {
    filterCasePage.value.current = 1;
    filterCasePage.value.keyword = keyword || '';
    filterCaseOptions.value = [];
  }
  try {
    const res: any = await getTestCaseList(
        projectStore.getProjectId,
        undefined, undefined,
        filterCasePage.value.keyword || undefined,
        undefined, undefined,
        filterCasePage.value.current,
        filterCasePage.value.pageSize
    );
    const records = res.data?.records || [];
    if (isLoadMore) {
      filterCaseOptions.value.push(...records);
    } else {
      filterCaseOptions.value = records;
    }
    filterCasePage.value.hasMore = records.length >= filterCasePage.value.pageSize;
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
  const exists = filterCaseOptions.value.some((tc: any) => tc.id === searchCaseId.value);
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

const handleDelete = async (id: number) => {
  try {
    const res: any = await deleteBug(id);
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
    title: '批量删除 BUG',
    content: `确定删除选中的 ${selectedKeys.value.length} 个 BUG 吗？其评论与操作日志将一并删除，此操作不可恢复。`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: {status: 'danger'},
    onOk: async () => {
      const res: any = await batchDeleteBug(selectedKeys.value);
      if (res.code === 200) {
        Message.success('批量删除成功');
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
    const res: any = await transitionBugStatus(
      transitionForm.value.bugId,
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

const severityColor = (severity: string) => {
  const map: Record<string, string> = {FATAL: 'red', SERIOUS: 'orange', NORMAL: 'blue', TIPS: 'gray'};
  return map[severity] || 'gray';
};
const severityText = (severity: string) => {
  const map: Record<string, string> = {FATAL: '致命', SERIOUS: '严重', NORMAL: '一般', TIPS: '提示'};
  return map[severity] || severity;
};
const priorityColor = (priority: string) => {
  const map: Record<string, string> = {URGENT: 'red', HIGH: 'orange', MEDIUM: 'blue', LOW: 'gray'};
  return map[priority] || 'gray';
};
const bugStatusColor = (status: string) => {
  const map: Record<string, string> = {
    NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold', FIXED: 'blue', VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray'
  };
  return map[status] || 'gray';
};
const bugStatusText = (status: string) => {
  const map: Record<string, string> = {
    NEW: '新建', CONFIRMED: '已确认', FIXING: '修复中', FIXED: '已修复', VERIFIED: '已验证', CLOSED: '已关闭', REJECTED: '已驳回'
  };
  return map[status] || status;
};
const renderHtml = (content: string | undefined, defaultText: string = '暂无') => {
  if (!content || content.trim() === '') return defaultText;
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
  const lines = content.split('\n').filter(line => line.trim() !== '');
  if (lines.length === 0) return '';
  return `<ol>${lines.map(line => `<li>${line.replace(/</g, '&lt;').replace(/>/g, '&gt;')}</li>`).join('')}</ol>`;
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
  if (diff < 60) return '刚刚';
  if (diff < 3600) return `${Math.floor(diff / 60)}分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)}小时前`;
  if (diff < 2592000) return `${Math.floor(diff / 86400)}天前`;
  return timeStr;
};
const allBugStatuses = [
  { value: 'NEW', label: '新建' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'FIXING', label: '修复中' },
  { value: 'FIXED', label: '已修复' },
  { value: 'VERIFIED', label: '已验证' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'REJECTED', label: '已驳回' }
];
const environmentText = (env: string) => {
  const map: Record<string, string> = {TEST: '测试环境', STAGING: '预发环境', PROD: '生产环境'};
  return map[env] || env || '-';
};
const reproduceRateText = (rate: string) => {
  const map: Record<string, string> = {ALWAYS: '必现', OFTEN: '高概率', SOMETIMES: '偶现', RARE: '难现'};
  return map[rate] || rate || '-';
};
const closeReasonText = (closeReason: string) => {
  const map: Record<string, string> = {
    FIXED: '已修复', DUPLICATE: '重复', NOT_BUG: '不是BUG', CANNOT_REPRODUCE: '无法复现', WONT_FIX: '暂不处理'
  };
  return map[closeReason] || closeReason || '-';
};

// 统计卡片：后端全项目口径聚合，不再只数当前页
const loadBugStats = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getBugStats(projectStore.getProjectId);
    if (res.code === 200 && res.data) {
      bugStats.value = { total: 0, open: 0, serious: 0, fatal: 0, ...res.data };
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
    {immediate: true}
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

/* 聚合式筛选区 */
.filter-toolbar {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 16px;

  .filter-grid {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
    gap: 16px;
    width: 100%;
  }

  .filter-field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    min-width: 0;
  }

  .filter-field-label {
    font-size: 13px;
    color: var(--color-text-2);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .filter-input,
  .filter-select {
    width: 100%;
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

.filter-actions-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;

  .filter-actions-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .filter-actions-right {
    display: flex;
    gap: 8px;
  }
}

/* 表格区 */
.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.table-skeleton {
  padding: 16px;
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
  :deep(p) { margin: 8px 0; }
  :deep(ul), :deep(ol) { padding-left: 20px; margin: 8px 0; }
  :deep(li) { margin-bottom: 6px; }
  :deep(blockquote) { border-left: 4px solid #ccc; padding-left: 10px; color: #666; margin: 8px 0; }
  :deep(img) { max-width: 100%; }
  :deep(a) { color: rgb(var(--primary-6)); }
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
