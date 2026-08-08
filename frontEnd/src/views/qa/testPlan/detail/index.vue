<template>
  <div class="test-plan-detail-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testPlan', planData.planName || '计划详情']"/>

    <!-- 计划基本信息 -->
    <a-card class="plan-info-card" size="small">
      <!-- 基础信息行 -->
      <div class="plan-meta-row">
        <span class="plan-meta-item">
          <span class="plan-meta-label">计划</span>
          <span class="plan-meta-value" style="font-weight: 600;">{{ planData.planName || '-' }}</span>
        </span>
        <span class="plan-meta-divider">|</span>
        <span class="plan-meta-item">
          <span class="plan-meta-label">状态</span>
          <a-tag size="small">{{ statusText(planData.status) }}</a-tag>
        </span>
        <span class="plan-meta-divider">|</span>
        <span class="plan-meta-item">
          <span class="plan-meta-label">时间</span>
          <span class="plan-meta-value">{{ planData.startTime || '-' }} ~ {{ planData.endTime || '-' }}</span>
        </span>
        <span class="plan-meta-divider">|</span>
        <span class="plan-meta-item">
          <span class="plan-meta-label">描述</span>
          <span class="plan-meta-value">{{ planData.description || '-' }}</span>
        </span>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-row">
        <div class="stat-card stat-total">
          <div class="stat-left-bar"></div>
          <div class="stat-body">
            <div class="stat-number">{{ resultStats.total }}</div>
            <div class="stat-label">总用例</div>
          </div>
          <div class="stat-progress-wrap">
            <div class="stat-progress-bg">
              <div class="stat-progress-fill" style="width: 100%"></div>
            </div>
          </div>
        </div>
        <div class="stat-card stat-pass">
          <div class="stat-left-bar"></div>
          <div class="stat-body">
            <div class="stat-number">{{ resultStats.pass }}</div>
            <div class="stat-label">通过 {{ resultStats.total > 0 ? Math.round(resultStats.pass / resultStats.total * 100) : 0 }}%</div>
          </div>
          <div class="stat-progress-wrap">
            <div class="stat-progress-bg">
              <div class="stat-progress-fill" :style="{ width: resultStats.total > 0 ? (resultStats.pass / resultStats.total * 100) + '%' : '0%' }"></div>
            </div>
          </div>
        </div>
        <div class="stat-card stat-fail">
          <div class="stat-left-bar"></div>
          <div class="stat-body">
            <div class="stat-number">{{ resultStats.fail }}</div>
            <div class="stat-label">失败 {{ resultStats.total > 0 ? Math.round(resultStats.fail / resultStats.total * 100) : 0 }}%</div>
          </div>
          <div class="stat-progress-wrap">
            <div class="stat-progress-bg">
              <div class="stat-progress-fill" :style="{ width: resultStats.total > 0 ? (resultStats.fail / resultStats.total * 100) + '%' : '0%' }"></div>
            </div>
          </div>
        </div>
        <div class="stat-card stat-block">
          <div class="stat-left-bar"></div>
          <div class="stat-body">
            <div class="stat-number">{{ resultStats.block }}</div>
            <div class="stat-label">阻塞 {{ resultStats.total > 0 ? Math.round(resultStats.block / resultStats.total * 100) : 0 }}%</div>
          </div>
          <div class="stat-progress-wrap">
            <div class="stat-progress-bg">
              <div class="stat-progress-fill" :style="{ width: resultStats.total > 0 ? (resultStats.block / resultStats.total * 100) + '%' : '0%' }"></div>
            </div>
          </div>
        </div>
        <div class="stat-card stat-unexec">
          <div class="stat-left-bar"></div>
          <div class="stat-body">
            <div class="stat-number">{{ resultStats.unexec }}</div>
            <div class="stat-label">未执行 {{ resultStats.total > 0 ? Math.round(resultStats.unexec / resultStats.total * 100) : 0 }}%</div>
          </div>
          <div class="stat-progress-wrap">
            <div class="stat-progress-bg">
              <div class="stat-progress-fill" :style="{ width: resultStats.total > 0 ? (resultStats.unexec / resultStats.total * 100) + '%' : '0%' }"></div>
            </div>
          </div>
        </div>
      </div>

      <a-divider style="margin: 12px 0;"/>
      <a-space>
        <a-button v-permission="'qa:testplan:update'" type="primary" @click="showAddCaseModal">
          <template #icon><icon-plus /></template>
          添加用例
        </a-button>
        <a-button v-permission="'qa:testplan:execute'" @click="handleGenerateReport">
          <template #icon><icon-file /></template>
          生成报告
        </a-button>
      </a-space>
    </a-card>

    <!-- 用例执行列表 -->
    <a-card class="case-list-card" :bordered="false">
      <a-table
          :data="caseList"
          :loading="loading"
          row-key="id"
          :sticky-header="true" :scroll="{ x: 'max-content' }"
      >
        <template #columns>
          <a-table-column title="用例编号" data-index="caseCode" :width="120"/>
          <a-table-column title="用例名称" data-index="caseName">
            <template #cell="{ record }">
              <a-link @click="handlePreviewCase(record)">{{ record.caseName }}</a-link>
            </template>
          </a-table-column>
          <a-table-column title="优先级" data-index="priority" :width="70">
            <template #cell="{ record }">
              <a-tag :color="priorityColor(record.priority)" size="small">{{ record.priority }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="执行结果" data-index="executeResult" :width="110">
            <template #cell="{ record }">
              <a-popover position="top" content-class="result-popover">
                <a-tag :color="resultColor(record.executeResult)" size="small" style="cursor: help;">
                  {{ resultText(record.executeResult) }}
                </a-tag>
                <template #content>
                  <div class="result-tooltip">
                    <div class="result-tooltip-header">
                      <a-tag :color="resultColor(record.executeResult)" size="small">{{ resultText(record.executeResult) }}</a-tag>
                      <span v-if="record.executeTime" class="result-tooltip-time">{{ record.executeTime }}</span>
                    </div>
                    <a-divider style="margin: 8px 0;" />
                    <div class="result-tooltip-body">
                      <div v-if="record.executeUserName || record.executeUserId" class="result-tooltip-item">
                        <span class="result-tooltip-label">执行人</span>
                        <span class="result-tooltip-value">{{ record.executeUserName || '用户' + record.executeUserId }}</span>
                      </div>
                      <div v-if="record.executeRemark" class="result-tooltip-item">
                        <span class="result-tooltip-label">备注</span>
                        <span class="result-tooltip-value">{{ record.executeRemark }}</span>
                      </div>
                      <div v-if="record.bugList && record.bugList.length" class="result-tooltip-item">
                        <span class="result-tooltip-label">关联BUG</span>
                        <span class="result-tooltip-value">
                          <a-tag v-for="bug in record.bugList.slice(0, 3)" :key="bug.id" color="red" size="small" style="margin-right: 4px;">{{ bug.bugCode }}</a-tag>
                          <span v-if="record.bugList.length > 3" style="color: #86909c;">+{{ record.bugList.length - 3 }}</span>
                        </span>
                      </div>
                      <div v-if="!record.executeTime && !record.executeRemark && !record.executeUserId" class="result-tooltip-empty">
                        <icon-exclamation-circle style="color: #86909c; margin-right: 4px;" />暂无执行记录
                      </div>
                    </div>
                  </div>
                </template>
              </a-popover>
            </template>
          </a-table-column>
          <a-table-column title="关联BUG" :width="120">
            <template #cell="{ record }">
              <a-tag
                v-if="record.bugList && record.bugList.length"
                color="red"
                size="small"
                style="cursor: pointer;"
                @click="handleOpenBugListDrawer(record)"
              >
                BUG ×{{ record.bugList.length }}
              </a-tag>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="200" fixed="right">
            <template #cell="{ record }">
              <a-space class="op-icons">
                <a-tooltip content="通过">
                  <a-button v-permission="'qa:testplan:execute'" type="text" size="small" @click="handleExecute(record, 'PASS')">
                    <template #icon><icon-check style="color: rgb(var(--success-6));" /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="失败">
                  <a-button v-permission="'qa:testplan:execute'" type="text" size="small" status="danger" @click="handleExecute(record, 'FAIL')">
                    <template #icon><icon-close /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="阻塞">
                  <a-button v-permission="'qa:testplan:execute'" type="text" size="small" @click="handleExecute(record, 'BLOCK')">
                    <template #icon><icon-exclamation-circle style="color: rgb(var(--warning-6));" /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="执行历史">
                  <a-button type="text" size="small" @click="handleShowHistory(record)">
                    <template #icon><icon-list style="color: rgb(var(--primary-6));" /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip v-if="record.executeResult === 'FAIL'" content="提BUG">
                  <a-button v-permission="'qa:bug:create'" type="text" size="small" status="warning" @click="handleGenerateBug(record)">
                    <template #icon><icon-bug /></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="移除">
                  <a-popconfirm content="确认从计划中移除该用例？" type="warning" @ok="handleRemoveCase(record.id)">
                    <a-button v-permission="'qa:testplan:update'" type="text" size="small" status="danger">
                      <template #icon><icon-delete /></template>
                    </a-button>
                  </a-popconfirm>
                </a-tooltip>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 添加用例弹窗 -->
    <a-modal
        v-model:visible="addCaseModalVisible"
        title="添加用例到计划"
        width="800px"
        @ok="handleConfirmAddCases"
        @cancel="addCaseModalVisible = false"
        :mask-closable="false"
    >
      <a-row class="add-case-toolbar" justify="space-between">
        <a-space>
          <a-input v-model="caseSearchKeyword" placeholder="搜索用例名称" allow-clear style="width: 180px;"/>
          <a-select v-model="caseSearchModuleId" placeholder="所属模块" allow-clear style="width: 160px;">
            <a-option v-for="mod in moduleOptions" :key="mod.id" :value="mod.id">{{ mod.moduleName }}</a-option>
          </a-select>
          <a-select v-model="caseSearchSetId" placeholder="所属测试集" allow-clear style="width: 160px;">
            <a-option v-for="set in setOptions" :key="set.id" :value="set.id">{{ set.setName }}</a-option>
          </a-select>
          <a-button type="primary" @click="handleSearchCases">
            <template #icon><icon-search /></template>
            搜索
          </a-button>
        </a-space>
        <span style="color: #86909c;">已选 {{ selectedAddCaseKeys.length }} 条</span>
      </a-row>
      <div class="modal-table-wrapper">
      <a-table
          :data="availableCaseList"
          :loading="caseLoading"
          :pagination="casePagination"
          row-key="id"
          :row-selection="{ type: 'checkbox' }"
          v-model:selectedKeys="selectedAddCaseKeys"
          :sticky-header="true" :scroll="{ x: 'max-content' }"
          @page-change="handleCasePageChange"
          @page-size-change="handleCasePageSizeChange"
      >
        <template #columns>
          <a-table-column title="用例编号" data-index="caseCode" :width="140"/>
          <a-table-column title="用例名称" data-index="caseName"/>
          <a-table-column title="类型" data-index="caseType" :width="100">
            <template #cell="{ record }">
              <a-tag>{{ caseTypeText(record.caseType) }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="优先级" data-index="priority" :width="100">
            <template #cell="{ record }">
              <a-tag :color="priorityColor(record.priority)">{{ record.priority }}</a-tag>
            </template>
          </a-table-column>
        </template>
      </a-table>
      </div>
    </a-modal>

    <!-- 执行备注弹窗 -->
    <a-modal
        v-model:visible="remarkModalVisible"
        title="执行备注"
        width="480px"
        @ok="handleConfirmExecute"
        @cancel="remarkModalVisible = false"
        :mask-closable="false"
    >
      <a-form :model="executeForm" layout="vertical">
        <a-form-item label="执行结果">
          <a-tag :color="resultColor(executeForm.result)" size="large">{{ resultText(executeForm.result) }}</a-tag>
        </a-form-item>
        <a-form-item label="备注">
          <a-textarea v-model="executeForm.remark" :auto-size="{ minRows: 3 }" placeholder="请输入执行备注（可选）"/>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 执行历史抽屉 -->
    <a-drawer
        v-model:visible="historyVisible"
        title="执行历史"
        width="560px"
        :mask-closable="true"
        :footer="false"
    >
      <a-timeline v-if="executionHistoryList.length > 0">
        <a-timeline-item v-for="item in executionHistoryList" :key="item.id">
          <div style="color: #86909c; font-size: 12px; margin-bottom: 4px;">{{ item.executeTime }}</div>
          <div>
            <a-tag :color="resultColor(item.result)">{{ resultText(item.result) }}</a-tag>
            <span v-if="item.planName" style="margin-left: 8px; color: #86909c;">计划：{{ item.planName }}</span>
            <span v-if="item.executeUserName || item.executeUserId" style="margin-left: 8px; color: #86909c;">执行人：{{ item.executeUserName || '用户' + item.executeUserId }}</span>
          </div>
          <div v-if="item.remark" style="margin-top: 4px; color: #666;">{{ item.remark }}</div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-else description="暂无执行记录" />
    </a-drawer>

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

    <!-- BUG预览抽屉 -->
    <a-drawer
        v-model:visible="bugPreviewVisible"
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

    <!-- 测试报告弹窗 -->
    <a-modal
        v-model:visible="reportVisible"
        :title="`【${reportData.planName || ''}】测试报告`"
        width="960px"
        :footer="false"
        :mask-closable="true"
    >
      <div v-if="reportLoading" class="report-loading">
        <a-spin size="large" />
      </div>
      <div v-else id="plan-report-content" class="plan-report-body">
        <!-- 头部信息 -->
        <div class="report-header">
          <div class="report-title">【{{ reportData.planName }}】测试报告</div>
          <div class="report-meta">
            <span v-if="reportData.planStatus">状态：{{ statusText(reportData.planStatus) }}</span>
            <span v-if="reportData.startTime">时间：{{ reportData.startTime }} ~ {{ reportData.endTime }}</span>
            <span v-if="reportData.generateTime">生成：{{ reportData.generateTime }}</span>
          </div>
          <div v-if="reportData.description" class="report-desc">{{ reportData.description }}</div>
        </div>

        <!-- 执行概况 -->
        <div class="report-section">
          <div class="report-section-title">执行概况</div>
          <div class="report-stat-grid">
            <div class="report-stat-item">
              <div class="report-stat-num">{{ reportData.total || 0 }}</div>
              <div class="report-stat-label">总用例</div>
            </div>
            <div class="report-stat-item pass">
              <div class="report-stat-num">{{ reportData.pass || 0 }}</div>
              <div class="report-stat-label">通过</div>
            </div>
            <div class="report-stat-item fail">
              <div class="report-stat-num">{{ reportData.fail || 0 }}</div>
              <div class="report-stat-label">失败</div>
            </div>
            <div class="report-stat-item block">
              <div class="report-stat-num">{{ reportData.block || 0 }}</div>
              <div class="report-stat-label">阻塞</div>
            </div>
            <div class="report-stat-item unexec">
              <div class="report-stat-num">{{ reportData.unexec || 0 }}</div>
              <div class="report-stat-label">未执行</div>
            </div>
          </div>
          <div class="report-rate-row">
            <span>执行率：<strong>{{ reportData.executeRate || 0 }}%</strong></span>
            <a-divider direction="vertical" />
            <span>通过率：<strong>{{ reportData.passRate || 0 }}%</strong></span>
            <a-divider direction="vertical" />
            <span>BUG：<strong style="color: #f53f3f;">{{ reportData.totalBug || 0 }}</strong> 个（未关闭 <strong style="color: #f53f3f;">{{ reportData.openBug || 0 }}</strong>）</span>
          </div>
        </div>

        <!-- 图表区 -->
        <div class="report-chart-row">
          <div class="report-chart-box">
            <div class="report-chart-title">执行结果分布</div>
            <Chart :options="resultPieOption" :height="220" />
          </div>
          <div class="report-chart-box">
            <div class="report-chart-title">执行趋势</div>
            <Chart :options="trendLineOption" :height="220" />
          </div>
        </div>

        <!-- BUG 统计 -->
        <div class="report-section" v-if="reportData.totalBug > 0">
          <div class="report-section-title">BUG 统计</div>
          <div class="report-bug-stats">
            <div class="report-bug-group" v-if="reportData.bugBySeverity && Object.keys(reportData.bugBySeverity).length">
              <div class="report-bug-group-title">按严重程度</div>
              <div class="report-bug-tags">
                <span
                    v-for="(count, severity) in reportData.bugBySeverity"
                    :key="severity"
                    class="report-bug-tag"
                    :class="severityTagClass(severity)"
                >
                  {{ severityLabel(severity) }} {{ count }}
                </span>
              </div>
            </div>
            <div class="report-bug-group" v-if="reportData.bugByStatus && Object.keys(reportData.bugByStatus).length">
              <div class="report-bug-group-title">按状态</div>
              <div class="report-bug-tags">
                <a-tag
                    v-for="(count, status) in reportData.bugByStatus"
                    :key="status"
                    :color="bugStatusColor(status)"
                    size="small"
                >
                  {{ bugStatusText(status) }} {{ count }}
                </a-tag>
              </div>
            </div>
          </div>
        </div>

        <!-- 失败/阻塞用例明细 -->
        <div class="report-section" v-if="reportData.failCases && reportData.failCases.length">
          <div class="report-section-title">失败/阻塞用例明细（{{ reportData.failCases.length }} 条）</div>
          <a-table :data="reportData.failCases" size="small" :pagination="false">
            <template #columns>
              <a-table-column title="用例编号" data-index="caseCode" :width="120" />
              <a-table-column title="用例名称" data-index="caseName">
                <template #cell="{ record }">
                  <div class="text-ellipsis-cell">{{ record.caseName }}</div>
                </template>
              </a-table-column>
              <a-table-column title="优先级" data-index="priority" :width="70">
                <template #cell="{ record }">
                  <a-tag :color="priorityColor(record.priority)" size="small">{{ record.priority }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column title="结果" data-index="executeResult" :width=80>
                <template #cell="{ record }">
                  <a-tag :color="resultColor(record.executeResult)" size="small">{{ resultText(record.executeResult) }}</a-tag>
                </template>
              </a-table-column>
              <a-table-column title="关联BUG" :width="140">
                <template #cell="{ record }">
                  <div v-if="record.bugs && record.bugs.length">
                    <a-tag
                        v-for="b in record.bugs"
                        :key="b.bugCode"
                        color="red"
                        size="small"
                        style="margin-right: 4px; margin-bottom: 2px;"
                    >
                      {{ b.bugCode }}
                    </a-tag>
                  </div>
                  <span v-else style="color: #86909c;">-</span>
                </template>
              </a-table-column>
              <a-table-column title="备注" data-index="executeRemark">
                <template #cell="{ record }">
                  <span v-if="record.executeRemark" class="text-ellipsis-cell">{{ record.executeRemark }}</span>
                  <span v-else style="color: #86909c;">-</span>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </div>
        <a-empty v-else-if="reportData.total > 0" description="暂无失败/阻塞用例，质量良好 👍" />

        <!-- 模块统计 -->
        <div class="report-section" v-if="reportData.moduleStats && reportData.moduleStats.length">
          <div class="report-section-title">模块执行统计</div>
          <a-table :data="reportData.moduleStats" size="small" :pagination="false">
            <template #columns>
              <a-table-column title="模块" data-index="moduleName" :width=140>
                <template #cell="{ record }">
                  <span :class="{ 'module-bad': modulePassRate(record) < 60 && record.total > 0 }">{{ record.moduleName }}</span>
                </template>
              </a-table-column>
              <a-table-column title="用例数" data-index="total" :width=80 />
              <a-table-column title="通过" data-index="pass" :width=70>
                <template #cell="{ record }">
                  <span style="color: #00b42a;">{{ record.pass }}</span>
                </template>
              </a-table-column>
              <a-table-column title="失败" data-index="fail" :width=70>
                <template #cell="{ record }">
                  <span style="color: #f53f3f;">{{ record.fail }}</span>
                </template>
              </a-table-column>
              <a-table-column title="阻塞" data-index="block" :width="70">
                <template #cell="{ record }">
                  <span style="color: #ff7d00;">{{ record.block }}</span>
                </template>
              </a-table-column>
              <a-table-column title="BUG" data-index="bugCount" :width="70">
                <template #cell="{ record }">
                  <span :style="{ color: record.bugCount > 0 ? '#f53f3f' : '#86909c' }">{{ record.bugCount || 0 }}</span>
                </template>
              </a-table-column>
              <a-table-column title="通过率" :width="160">
                <template #cell="{ record }">
                  <div class="module-progress-wrap">
                    <div
                        class="module-progress-bar"
                        :class="{ 'module-progress-bad': modulePassRate(record) < 60, 'module-progress-good': modulePassRate(record) >= 80 }"
                        :style="{ width: modulePassRate(record) + '%' }"
                    />
                    <span class="module-progress-text">{{ modulePassRate(record) }}%</span>
                  </div>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </div>

        <!-- 需求覆盖 -->
        <div class="report-section" v-if="reportData.requirements && reportData.requirements.length">
          <div class="report-section-title">需求覆盖（{{ reportData.requirements.length }} 个）</div>
          <a-table :data="reportData.requirements" size="small" :pagination="false">
            <template #columns>
              <a-table-column title="需求编号" data-index="reqCode" :width="140" />
              <a-table-column title="需求标题" data-index="title" />
            </template>
          </a-table>
        </div>

        <!-- 执行人员 -->
        <div class="report-section" v-if="reportData.executors && reportData.executors.length">
          <div class="report-section-title">执行人员</div>
          <div class="report-executor-list">
            <div
                v-for="exec in reportData.executors"
                :key="exec.userName"
                class="report-executor-item"
            >
              <span class="report-executor-name">{{ exec.userName }}</span>
              <span class="report-executor-count">执行了 {{ exec.count }} 条用例</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!reportLoading" class="report-actions">
        <a-space>
          <a-button type="primary" @click="handlePrintReport">
            <template #icon><icon-printer /></template>
            打印 / 导出 PDF
          </a-button>
          <a-button @click="reportVisible = false">关闭</a-button>
        </a-space>
      </div>
    </a-modal>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script setup lang="ts">
import {ref, onMounted, computed} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import {
  IconPlus, IconSearch, IconCheck, IconClose,
  IconExclamationCircle, IconBug, IconDelete, IconList, IconFile
} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import RichEditor from '@/components/rich-editor/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import Chart from '@/components/chart/index.vue';
import {getUserListByPage} from '@/api/MyApi/user';
import {deleteRichTextImages} from '@/api/MyApi/fileUpload';
import {useProjectStore} from '@/store';
import {
  getTestPlanDetail, addCasesToPlan, removeCaseFromPlan,
  executePlanCase, generateBugFromPlanCase,
  getTestCaseList, getQaModuleList, getTestCaseSetOptions, getTestCaseExecutionHistory,
  getBugDetail, updateBug,
  getRequirementList, getRequirementDetail, getTestCaseDetail,
  getTestPlanReport
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

// 添加用例弹窗
const addCaseModalVisible = ref(false);
const availableCaseList = ref<any[]>([]);
const caseLoading = ref(false);
const caseSearchKeyword = ref('');
const caseSearchModuleId = ref<number | undefined>(undefined);
const caseSearchSetId = ref<number | undefined>(undefined);
const casePagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] });
const selectedAddCaseKeys = ref<any[]>([]);
const moduleOptions = ref<any[]>([]);
const setOptions = ref<any[]>([]);

// 执行备注弹窗
const remarkModalVisible = ref(false);
const executeForm = ref<any>({ planCaseId: null, result: '', remark: '' });

// 执行历史
const historyVisible = ref(false);
const executionHistoryList = ref<any[]>([]);

// 预览
const previewVisible = ref(false);
const previewData = ref<any>({});

// BUG预览
const bugPreviewVisible = ref(false);
const bugPreviewData = ref<any>({});

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
const reportVisible = ref(false);
const reportData = ref<any>({});
const reportLoading = ref(false);

const handleGenerateReport = async () => {
  reportLoading.value = true;
  try {
    const res: any = await getTestPlanReport(planId.value);
    if (res.code === 200 && res.data) {
      reportData.value = res.data;
      reportVisible.value = true;
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

const modulePassRate = (record: any) => {
  if (!record.total) return 0;
  return Math.round((record.pass || 0) * 100 / record.total);
};

const resultPieOption = computed(() => {
  const d = reportData.value;
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    color: ['#00b42a', '#f53f3f', '#ff7d00', '#86909c', '#c9cdd4'],
    series: [{
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{c}', fontSize: 12 },
      data: [
        { name: '通过', value: d.pass || 0 },
        { name: '失败', value: d.fail || 0 },
        { name: '阻塞', value: d.block || 0 },
        { name: '未执行', value: d.unexec || 0 },
        { name: '不适用', value: d.na || 0 },
      ].filter(item => item.value > 0),
    }],
  };
});

const trendLineOption = computed(() => {
  const trend = reportData.value.executionTrend || [];
  if (!trend.length) {
    return { title: { text: '暂无执行记录', left: 'center', top: 'center', textStyle: { color: '#86909c', fontSize: 14 } } };
  }
  const dates = trend.map((t: any) => t.date);
  const passData = trend.map((t: any) => t.pass || 0);
  const failData = trend.map((t: any) => t.fail || 0);
  const blockData = trend.map((t: any) => t.block || 0);
  return {
    tooltip: { trigger: 'axis' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    grid: { left: '3%', right: '4%', bottom: '15%', top: '10%', containLabel: true },
    xAxis: { type: 'category', boundaryGap: false, data: dates, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { fontSize: 11 } },
    series: [
      { name: '通过', type: 'line', smooth: true, data: passData, itemStyle: { color: '#00b42a' }, areaStyle: { color: 'rgba(0,180,42,0.1)' } },
      { name: '失败', type: 'line', smooth: true, data: failData, itemStyle: { color: '#f53f3f' }, areaStyle: { color: 'rgba(245,63,63,0.1)' } },
      { name: '阻塞', type: 'line', smooth: true, data: blockData, itemStyle: { color: '#ff7d00' }, areaStyle: { color: 'rgba(255,125,0,0.1)' } },
    ],
  };
});

const handlePrintReport = () => {
  const printWindow = window.open('', '_blank');
  if (!printWindow) return;
  const reportHtml = document.getElementById('plan-report-content')?.innerHTML || '';
  printWindow.document.write(`
    <html><head><title>测试报告</title>
    <style>
      body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 40px; color: #1d2129; }
      .report-header { text-align: center; margin-bottom: 32px; }
      .report-title { font-size: 22px; font-weight: 700; margin-bottom: 8px; }
      .report-meta { color: #86909c; font-size: 13px; }
      .report-section { margin-bottom: 24px; }
      .report-section-title { font-size: 15px; font-weight: 600; margin-bottom: 12px; padding-left: 8px; border-left: 3px solid rgb(var(--primary-6)); }
      .stat-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; margin-bottom: 16px; }
      .stat-item { text-align: center; padding: 12px; background: #f7f8fa; border-radius: 6px; }
      .stat-num { font-size: 20px; font-weight: 700; }
      .stat-label { font-size: 12px; color: #86909c; margin-top: 4px; }
      table { width: 100%; border-collapse: collapse; font-size: 13px; }
      th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #e5e6eb; }
      th { background: #f7f8fa; font-weight: 600; }
      .tag { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 12px; }
      .tag-red { background: #ffece8; color: #f53f3f; }
      .tag-green { background: #e8ffea; color: #00b42a; }
      .tag-orange { background: #fff7e8; color: #ff7d00; }
      .tag-gray { background: #f2f3f5; color: #86909c; }
    </style></head><body>${reportHtml}</body></html>
  `);
  printWindow.document.close();
  setTimeout(() => {
    printWindow.print();
  }, 300);
};

const severityTagClass = (severity: string) => {
  const map: Record<string, string> = { FATAL: 'tag-red', SERIOUS: 'tag-orange', NORMAL: 'tag-gray', TIPS: 'tag-gray' };
  return map[severity] || 'tag-gray';
};
const severityLabel = (severity: string) => {
  const map: Record<string, string> = { FATAL: '致命', SERIOUS: '严重', NORMAL: '一般', TIPS: '提示' };
  return map[severity] || severity;
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
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

// 添加用例
const showAddCaseModal = async () => {
  addCaseModalVisible.value = true;
  selectedAddCaseKeys.value = [];
  caseSearchKeyword.value = '';
  caseSearchModuleId.value = undefined;
  caseSearchSetId.value = undefined;
  casePagination.value = { current: 1, pageSize: 10, total: 0, showTotal: true, showPageSize: true, pageSizeOptions: [10, 20, 50, 100] };
  await loadModuleOptions();
  await loadSetOptions();
  await loadAvailableCases();
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

const loadSetOptions = async () => {
  if (!projectStore.getProjectId) return;
  try {
    const res: any = await getTestCaseSetOptions(projectStore.getProjectId);
    setOptions.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const loadAvailableCases = async () => {
  if (!projectStore.getProjectId) return;
  caseLoading.value = true;
  try {
    const res: any = await getTestCaseList(
        projectStore.getProjectId,
        caseSearchModuleId.value || undefined,
        caseSearchSetId.value || undefined,
        undefined,
        caseSearchKeyword.value || undefined,
        undefined,
        casePagination.value.current,
        casePagination.value.pageSize,
        planId.value
    );
    availableCaseList.value = res.data?.records || [];
    casePagination.value.total = res.data?.total || 0;
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || '搜索用例失败');
  } finally {
    caseLoading.value = false;
  }
};

const handleSearchCases = () => {
  casePagination.value.current = 1;
  loadAvailableCases();
};

const handleCasePageChange = (page: number) => {
  casePagination.value.current = page;
  loadAvailableCases();
};

const handleCasePageSizeChange = (pageSize: number) => {
  casePagination.value.pageSize = pageSize;
  casePagination.value.current = 1;
  loadAvailableCases();
};

const handleConfirmAddCases = async () => {
  if (selectedAddCaseKeys.value.length === 0) {
    Message.warning('请至少选择一条用例');
    return;
  }
  try {
    const caseIds = selectedAddCaseKeys.value.map((k) => Number(k));
    const res: any = await addCasesToPlan(planId.value, caseIds);
    if (res.code === 200) {
      Message.success(`成功添加 ${res.data || selectedAddCaseKeys.value.length} 条用例`);
      addCaseModalVisible.value = false;
      await loadPlanDetail();
    } else {
      Message.error(res.msg || '添加用例失败');
    }
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || '添加用例失败，请检查网络');
  }
};

// 移除用例
const handleRemoveCase = async (planCaseId: number) => {
  try {
    const res: any = await removeCaseFromPlan(planCaseId);
    if (res.code === 200) {
      Message.success('移除成功');
      await loadPlanDetail();
    }
  } catch (e) {
    console.error(e);
  }
};

// 执行用例
const handleExecute = (record: any, result: string) => {
  executeForm.value = { planCaseId: record.id, result, remark: '' };
  remarkModalVisible.value = true;
};

const handleConfirmExecute = async () => {
  try {
    const res: any = await executePlanCase(
        executeForm.value.planCaseId,
        executeForm.value.result,
        executeForm.value.remark || undefined
    );
    if (res.code === 200) {
      Message.success('执行结果已记录');
      remarkModalVisible.value = false;
      await loadPlanDetail();
    }
  } catch (e) {
    console.error(e);
  }
};

// 查看执行历史
const handleShowHistory = async (record: any) => {
  if (!record.testCaseId) return;
  try {
    const res: any = await getTestCaseExecutionHistory(record.testCaseId);
    if (res.code === 200) {
      executionHistoryList.value = res.data || [];
      historyVisible.value = true;
    }
  } catch (e) {
    console.error(e);
  }
};

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
  const map: Record<string, string> = { PASS: '通过', FAIL: '失败', BLOCK: '阻塞', NA: '不适用', UNEXECUTED: '未执行' };
  return map[result] || result;
};

const statusText = (status: string) => {
  const map: Record<string, string> = { DRAFT: '草稿', READY: '就绪', RUNNING: '执行中', COMPLETED: '已完成' };
  return map[status] || status;
};

onMounted(() => {
  loadPlanDetail();
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

.case-list-card {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.case-list-card :deep(.arco-card-body) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: auto;
}

.add-case-toolbar {
  margin-bottom: 16px;
}

.modal-table-wrapper {
  height: 420px;
  overflow: auto;
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

/* 执行结果 tooltip */
.result-tooltip {
  min-width: 200px;
}
.result-tooltip-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.result-tooltip-time {
  font-size: 12px;
  color: #86909c;
  margin-left: auto;
}
.result-tooltip-body {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.result-tooltip-item {
  display: flex;
  gap: 8px;
  font-size: 12px;
  line-height: 1.6;
}
.result-tooltip-label {
  color: #86909c;
  white-space: nowrap;
  flex-shrink: 0;
  width: 56px;
}
.result-tooltip-value {
  color: #1d2129;
  word-break: break-all;
}
.result-tooltip-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 8px 0;
  font-size: 12px;
  color: #86909c;
}

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
</style>
