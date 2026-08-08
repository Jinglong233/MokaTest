<template>
  <div class="test-case-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testCase']" />
    <div class="main-row">
      <!-- 左侧模块树 -->
      <div class="col side-col" :style="sideColStyle">
        <a-card class="tree-card" size="small" title="业务模块">
          <div class="tree-scroll-wrapper">
            <ModuleTreeManager
              ref="moduleTreeRef"
              :show-search="false"
              :show-virtual-root="true"
              :selected-keys="[searchModuleId ?? 0]"
              default-expand-all
              @select="handleTreeSelect"
              @changed="handleModuleChanged"
            />
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
        <a-card class="list-card" :title="$t('qa.testCase.title')">
          <!-- 工具栏 -->
          <a-row
            class="list-toolbar"
            justify="space-between"
            align="center"
            style="margin-bottom: 12px"
          >
            <div class="search-items">
              <div class="search-item">
                <span class="search-label">用例名称</span>
                <a-input-search
                  v-model="searchKeyword"
                  :placeholder="$t('qa.testCase.name')"
                  allow-clear
                  class="search-control"
                  @search="handleSearch"
                  @clear="handleSearch"
                />
              </div>
              <div class="search-item">
                <span class="search-label">所属测试集</span>
                <a-select
                  v-model="searchSetId"
                  placeholder="请选择"
                  allow-clear
                  class="search-control"
                  @change="handleSearch"
                >
                  <a-option
                    v-for="set in setOptions"
                    :key="set.id"
                    :value="set.id"
                    >{{ set.setName }}</a-option
                  >
                </a-select>
              </div>
              <div class="search-item">
                <span class="search-label">最近结果</span>
                <a-select
                  v-model="searchLastResult"
                  placeholder="请选择"
                  allow-clear
                  class="search-control"
                  @change="handleSearch"
                >
                  <a-option value="PASS">通过</a-option>
                  <a-option value="FAIL">失败</a-option>
                  <a-option value="BLOCK">阻塞</a-option>
                  <a-option value="NA">不适用</a-option>
                </a-select>
              </div>
            </div>
            <a-space>
              <a-tooltip content="新建用例">
                <a-button
                  v-permission="'qa:testcase:create'"
                  type="primary"
                  @click="handleAdd"
                >
                  <template #icon><icon-plus /></template>
                </a-button>
              </a-tooltip>
              <a-tooltip content="导出">
                <a-button @click="handleExport">
                  <template #icon><icon-download /></template>
                </a-button>
              </a-tooltip>
              <a-tooltip content="测试集管理">
                <a-button @click="handleOpenSetModal">
                  <template #icon><icon-list /></template>
                </a-button>
              </a-tooltip>
            </a-space>
          </a-row>

          <!-- 统计卡片 -->
          <a-row class="stats-row" :gutter="[12, 12]" style="margin-bottom: 12px">
            <a-col :xs="12" :md="6">
              <div class="stat-card stat-total" @click="handleReset">
                <div class="stat-number">{{ stats.total }}</div>
                <div class="stat-label">总用例</div>
              </div>
            </a-col>
            <a-col :xs="12" :md="6">
              <div
                class="stat-card stat-pass"
                @click="
                  searchLastResult = 'PASS';
                  handleSearch();
                "
              >
                <div class="stat-number">{{ stats.pass }}</div>
                <div class="stat-label">通过</div>
              </div>
            </a-col>
            <a-col :xs="12" :md="6">
              <div
                class="stat-card stat-fail"
                @click="
                  searchLastResult = 'FAIL';
                  handleSearch();
                "
              >
                <div class="stat-number">{{ stats.fail }}</div>
                <div class="stat-label">失败</div>
              </div>
            </a-col>
            <a-col :xs="12" :md="6">
              <div class="stat-card stat-unexec">
                <div class="stat-number">{{ stats.unexec }}</div>
                <div class="stat-label">未执行</div>
              </div>
            </a-col>
          </a-row>

          <div class="table-wrapper">
            <a-skeleton v-if="loading" :animation="true" class="table-skeleton">
              <a-skeleton-line :rows="10" :widths="['100%']" />
            </a-skeleton>
            <a-table
              v-show="!loading"
              :data="testCaseList"
              :pagination="pagination"
              :bordered="{ cell: true }"
              row-key="id"
              :sticky-header="true"
              :scroll="{ x: 1160 }"
              @page-change="handlePageChange"
              @page-size-change="handlePageSizeChange"
            >
              <template #columns>
                <a-table-column
                  title="用例编号"
                  data-index="caseCode"
                  :width="120"
                />
                <a-table-column
                  title="用例名称"
                  data-index="caseName"
                  :width="280"
                  :ellipsis="true"
                >
                  <template #cell="{ record }">
                    <a-tooltip :content="record.caseName" position="top">
                      <span class="case-name-link" @click="handlePreview(record)">
                        {{ record.caseName }}
                      </span>
                    </a-tooltip>
                  </template>
                </a-table-column>
                <a-table-column
                  v-if="fieldVis('priority')"
                  title="优先级"
                  data-index="priority"
                  :width="70"
                >
                  <template #cell="{ record }">
                    <a-tag
                      :color="priorityColor(record.priority)"
                      size="small"
                      >{{ record.priority }}</a-tag
                    >
                  </template>
                </a-table-column>
                <a-table-column
                  v-if="fieldVis('lastResult')"
                  title="最近结果"
                  data-index="lastResult"
                  :width="100"
                >
                  <template #cell="{ record }">
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
                </a-table-column>
                <a-table-column
                  v-if="fieldVis('tags')"
                  title="标签"
                  data-index="tags"
                  :width="120"
                  :ellipsis="true"
                >
                  <template #cell="{ record }">
                    <a-tooltip
                      v-if="record.tags"
                      :content="record.tags"
                      position="top"
                    >
                      <span>{{ record.tags }}</span>
                    </a-tooltip>
                    <span v-else style="color: #86909c">-</span>
                  </template>
                </a-table-column>
                <a-table-column
                  v-if="fieldVis('requirementId')"
                  title="关联需求"
                  data-index="requirementTitle"
                  :width="180"
                  :ellipsis="true"
                >
                  <template #cell="{ record }">
                    <a-tooltip
                      v-if="record.requirementTitle"
                      :content="record.requirementTitle"
                      position="top"
                    >
                      <span>{{ record.requirementTitle }}</span>
                    </a-tooltip>
                    <span v-else style="color: #86909c">-</span>
                  </template>
                </a-table-column>
                <a-table-column
                  title="关联BUG"
                  data-index="bugCount"
                  :width="90"
                >
                  <template #cell="{ record }">
                    <a-tag
                      v-if="record.bugCount > 0"
                      color="red"
                      size="small"
                      >{{ record.bugCount }}</a-tag
                    >
                    <span v-else style="color: #86909c">-</span>
                  </template>
                </a-table-column>
                <a-table-column title="操作" :width="120" fixed="right">
                  <template #cell="{ record }">
                    <a-space>
                      <a-tooltip content="编辑">
                        <a-button
                          v-permission="'qa:testcase:update'"
                          type="text"
                          size="small"
                          @click="handleEdit(record)"
                        >
                          <icon-edit />
                        </a-button>
                      </a-tooltip>
                      <a-tooltip content="删除">
                        <a-popconfirm
                          content="确认删除该用例吗？"
                          type="warning"
                          @ok="handleDelete(record.id)"
                        >
                          <a-button
                            v-permission="'qa:testcase:delete'"
                            type="text"
                            size="small"
                            status="danger"
                            @click.stop
                          >
                            <icon-delete />
                          </a-button>
                        </a-popconfirm>
                      </a-tooltip>
                      <a-dropdown position="bottom">
                        <a-button type="text" size="small" @click.stop
                          ><icon-more /></a-button
                        >
                        <template #content>
                          <a-doption
                            v-permission="'qa:testcase:update'"
                            @click="openTransitionModal(record)"
                          >
                            <icon-swap /> 状态流转
                          </a-doption>
                          <a-doption @click="handleShowHistory(record)"
                            ><icon-history /> 执行历史</a-doption
                          >
                          <a-doption
                            v-permission="'qa:testcase:update'"
                            @click="handleBindAuto(record)"
                          >
                            <icon-link /> 关联自动化
                          </a-doption>
                          <a-doption @click="handleViewBugs(record)"
                            ><icon-bug /> 关联BUG</a-doption
                          >
                        </template>
                      </a-dropdown>
                    </a-space>
                  </template>
                </a-table-column>
              </template>
            </a-table>
          </div>
        </a-card>
      </div>
    </div>

    <!-- 新增/编辑弹窗（仅抽屉/预览中使用） -->
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
        <TestCaseForm ref="testCaseFormRef" :initial-data="formData" />
      </div>
    </a-modal>

    <!-- 关联自动化弹窗 -->
    <a-modal
      v-model:visible="bindModalVisible"
      title="关联自动化"
      @ok="handleBindSave"
      @cancel="bindModalVisible = false"
      :mask-closable="false"
    >
      <a-form :model="bindForm" layout="vertical">
        <a-form-item label="自动化类型" required>
          <a-select v-model="bindForm.autoType" @change="handleAutoTypeChange">
            <a-option value="UI_SCENE">UI场景</a-option>
            <a-option value="API_CASE">API用例</a-option>
          </a-select>
        </a-form-item>
        <a-form-item label="选择自动化用例" required>
          <a-select v-model="bindForm.autoId" :loading="autoOptionsLoading">
            <a-option
              v-for="opt in autoOptions"
              :key="opt.id"
              :value="opt.id"
              >{{ opt.name }}</a-option
            >
          </a-select>
        </a-form-item>
        <a-form-item label="绑定说明">
          <a-textarea
            v-model="bindForm.bindRemark"
            :auto-size="{ minRows: 2 }"
            placeholder="可选：填写绑定说明"
          />
        </a-form-item>
      </a-form>

      <!-- 已绑定列表 -->
      <a-divider>已绑定自动化</a-divider>
      <a-table :data="bindList" :pagination="false" size="small">
        <template #columns>
          <a-table-column title="类型" data-index="autoType" :width="100">
            <template #cell="{ record }">
              <a-tag>{{
                record.autoType === 'UI_SCENE' ? 'UI场景' : 'API用例'
              }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="名称" data-index="autoName" />
          <a-table-column title="操作" :width="80">
            <template #cell="{ record }">
              <a-button
                v-permission="'qa:testcase:update'"
                type="text"
                size="small"
                status="danger"
                @click="handleUnbind(record.id)"
                >解绑</a-button
              >
            </template>
          </a-table-column>
        </template>
      </a-table>
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
          <div style="color: #86909c; font-size: 12px; margin-bottom: 4px">{{
            item.executeTime
          }}</div>
          <div>
            <a-tag :color="lastResultColor(item.result)">{{
              lastResultText(item.result)
            }}</a-tag>
            <span v-if="item.planName" style="margin-left: 8px; color: #86909c"
              >计划：{{ item.planName }}</span
            >
            <span
              v-if="item.executeUserName || item.executeUserId"
              style="margin-left: 8px; color: #86909c"
              >执行人：{{
                item.executeUserName || '用户' + item.executeUserId
              }}</span
            >
          </div>
          <div v-if="item.remark" style="margin-top: 4px; color: #666">{{
            item.remark
          }}</div>
        </a-timeline-item>
      </a-timeline>
      <a-empty v-else description="暂无执行记录" />
    </a-drawer>

    <!-- 关联BUG抽屉 -->
    <a-drawer
      v-model:visible="bugDrawerVisible"
      :title="bugDrawerTitle"
      width="900px"
      :mask-closable="true"
      :footer="false"
    >
      <a-row justify="space-between" style="margin-bottom: 16px">
        <a-button
          v-permission="'qa:bug:create'"
          type="primary"
          size="small"
          @click="handleAddBug"
        >
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
          :scroll="{ x: 'max-content' }"
          @page-change="handleBugDrawerPageChange"
          @page-size-change="handleBugDrawerPageSizeChange"
        >
          <template #columns>
            <a-table-column title="BUG编号" data-index="bugCode" :width="100">
              <template #cell="{ record }">
                <a-tooltip :content="record.bugCode" mini>
                  <span class="text-ellipsis-inline">{{ record.bugCode }}</span>
                </a-tooltip>
              </template>
            </a-table-column>
            <a-table-column title="BUG标题" data-index="title">
              <template #cell="{ record }">
                <a-tooltip :content="record.title" mini>
                  <span class="text-ellipsis-inline">{{ record.title }}</span>
                </a-tooltip>
              </template>
            </a-table-column>
            <a-table-column title="严重程度" data-index="severity" :width="80">
              <template #cell="{ record }">
                <a-tag :color="severityColor(record.severity)" size="small">{{
                  severityText(record.severity)
                }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column title="状态" data-index="status" :width="80">
              <template #cell="{ record }">
                <a-tag :color="bugStatusColor(record.status)" size="small">{{
                  bugStatusText(record.status)
                }}</a-tag>
              </template>
            </a-table-column>
            <a-table-column
              title="关联需求"
              data-index="requirementTitle"
              :width="120"
            >
              <template #cell="{ record }">
                <span
                  v-if="record.requirementTitle"
                  class="text-ellipsis-inline"
                  :title="record.requirementTitle"
                  >{{ record.requirementTitle }}</span
                >
                <span v-else style="color: #86909c">-</span>
              </template>
            </a-table-column>
            <a-table-column title="操作" :width="90">
              <template #cell="{ record }">
                <a-space size="mini">
                  <a-button
                    v-permission="'qa:bug:update'"
                    type="text"
                    size="small"
                    @click="handleEditBug(record)"
                    >编辑</a-button
                  >
                  <a-popconfirm
                    content="确认删除该BUG吗？"
                    type="warning"
                    @ok="handleDeleteBug(record.id)"
                  >
                    <a-button
                      v-permission="'qa:bug:delete'"
                      type="text"
                      size="small"
                      status="danger"
                      >删除</a-button
                    >
                  </a-popconfirm>
                </a-space>
              </template>
            </a-table-column>
          </template>
          <template #empty>
            <a-empty description="暂无关联BUG" />
          </template>
        </a-table>
      </div>
    </a-drawer>

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
          <a-divider orientation="left" style="margin-top: 0">
            <span
              style="
                font-size: 14px;
                font-weight: 600;
                color: rgb(var(--primary-6));
              "
              >基本信息</span
            >
          </a-divider>
          <a-form-item label="BUG标题" required>
            <a-input
              v-model="bugFormData.title"
              placeholder="请输入BUG标题"
              size="large"
            />
          </a-form-item>

          <a-divider orientation="left">
            <span
              style="
                font-size: 14px;
                font-weight: 600;
                color: rgb(var(--primary-6));
              "
              >严重程度</span
            >
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

          <a-divider orientation="left">
            <span
              style="
                font-size: 14px;
                font-weight: 600;
                color: rgb(var(--primary-6));
              "
              >环境信息</span
            >
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="所属模块">
                <a-select
                  v-model="bugFormData.moduleId"
                  allow-clear
                  placeholder="请选择所属模块"
                >
                  <a-option
                    v-for="mod in moduleOptions"
                    :key="mod.id"
                    :value="mod.id"
                    >{{ mod.moduleName }}</a-option
                  >
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="环境">
                <a-select
                  v-model="bugFormData.environment"
                  placeholder="请选择环境"
                  allow-clear
                >
                  <a-option value="TEST">测试环境</a-option>
                  <a-option value="STAGING">预发环境</a-option>
                  <a-option value="PROD">生产环境</a-option>
                </a-select>
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="截止日期">
                <a-date-picker
                  v-model="bugFormData.deadline"
                  value-format="YYYY-MM-DD"
                  placeholder="请选择截止日期"
                  style="width: 100%"
                />
              </a-form-item>
            </a-col>
          </a-row>

          <a-divider orientation="left">
            <span
              style="
                font-size: 14px;
                font-weight: 600;
                color: rgb(var(--primary-6));
              "
              >版本与复现</span
            >
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="8">
              <a-form-item label="发现版本">
                <a-input
                  v-model="bugFormData.foundVersion"
                  placeholder="如 v1.0.0"
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="修复版本">
                <a-input
                  v-model="bugFormData.fixedVersion"
                  placeholder="如 v1.0.1"
                />
              </a-form-item>
            </a-col>
            <a-col :span="8">
              <a-form-item label="重现概率">
                <a-select
                  v-model="bugFormData.reproduceRate"
                  placeholder="请选择重现概率"
                  allow-clear
                >
                  <a-option value="ALWAYS">必现</a-option>
                  <a-option value="OFTEN">高概率</a-option>
                  <a-option value="SOMETIMES">偶现</a-option>
                  <a-option value="RARE">难现</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>

          <a-divider orientation="left">
            <span
              style="
                font-size: 14px;
                font-weight: 600;
                color: rgb(var(--primary-6));
              "
              >详细描述</span
            >
          </a-divider>
          <a-form-item label="BUG描述">
            <RichEditor
              v-model="bugFormData.description"
              placeholder="请输入BUG描述"
              @uploaded="handleUploadedBugFile"
            />
          </a-form-item>
          <a-form-item label="复现步骤">
            <RichEditor
              v-model="bugFormData.reproduceSteps"
              placeholder="请输入复现步骤"
              @uploaded="handleUploadedBugFile"
            />
          </a-form-item>

          <a-divider orientation="left">
            <span
              style="
                font-size: 14px;
                font-weight: 600;
                color: rgb(var(--primary-6));
              "
              >关联关系</span
            >
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
                  @dropdown-visible-change="
                    handleBugRequirementDropdownVisibleChange
                  "
                  @dropdown-reach-bottom="loadMoreBugRequirement"
                >
                  <a-option
                    v-for="req in bugRequirementOptions"
                    :key="req.id"
                    :value="req.id"
                    >{{ req.reqCode }} - {{ req.title }}</a-option
                  >
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div
                        v-if="bugRequirementPage.hasMore"
                        style="padding: 8px; text-align: center; color: #86909c"
                      >
                        滚动加载更多...
                      </div>
                      <div
                        v-else-if="bugRequirementOptions.length > 0"
                        style="padding: 8px; text-align: center; color: #86909c"
                      >
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
                  @dropdown-visible-change="
                    handleBugTestCaseDropdownVisibleChange
                  "
                  @dropdown-reach-bottom="loadMoreBugTestCase"
                >
                  <a-option
                    v-for="tc in bugTestCaseOptions"
                    :key="tc.id"
                    :value="tc.id"
                    >{{ tc.caseCode }} - {{ tc.caseName }}</a-option
                  >
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div
                        v-if="bugTestCasePage.hasMore"
                        style="padding: 8px; text-align: center; color: #86909c"
                      >
                        滚动加载更多...
                      </div>
                      <div
                        v-else-if="bugTestCaseOptions.length > 0"
                        style="padding: 8px; text-align: center; color: #86909c"
                      >
                        没有更多了
                      </div>
                    </div>
                  </template>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>

          <a-divider orientation="left">
            <span
              style="
                font-size: 14px;
                font-weight: 600;
                color: rgb(var(--primary-6));
              "
              >其他</span
            >
          </a-divider>
          <a-row :gutter="24">
            <a-col :span="12">
              <a-form-item label="标签">
                <a-input-tag
                  v-model="bugFormData.tags"
                  placeholder="输入标签后按回车"
                  allow-clear
                />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item
                v-if="bugFormData.status === 'CLOSED'"
                label="关闭原因"
              >
                <a-select
                  v-model="bugFormData.closeReason"
                  placeholder="请选择关闭原因"
                  allow-clear
                >
                  <a-option value="FIXED">已修复</a-option>
                  <a-option value="DUPLICATE">重复</a-option>
                  <a-option value="NOT_BUG">不是BUG</a-option>
                  <a-option value="CANNOT_REPRODUCE">无法复现</a-option>
                  <a-option value="WONT_FIX">暂不处理</a-option>
                </a-select>
              </a-form-item>
            </a-col>
          </a-row>
          <a-row :gutter="24">
            <a-col :span="12">
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
                  <a-option
                    v-for="u in userOptions"
                    :key="u.id"
                    :value="u.id"
                    >{{ u.nickname || u.username }}</a-option
                  >
                  <template #dropdownRender="{ menuNode: menu }">
                    <div>
                      <VNodeRenderer :vnodes="menu" />
                      <div
                        v-if="userPage.hasMore"
                        style="padding: 8px; text-align: center; color: #86909c"
                      >
                        滚动加载更多...
                      </div>
                      <div
                        v-else-if="userOptions.length > 0"
                        style="padding: 8px; text-align: center; color: #86909c"
                      >
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
          <a-tag :color="statusColor(transitionForm.currentStatus)">{{
            statusText(transitionForm.currentStatus)
          }}</a-tag>
        </a-form-item>
        <a-form-item label="目标状态" required>
          <a-select
            v-model="transitionForm.targetStatus"
            placeholder="请选择目标状态"
          >
            <a-option value="DRAFT">草稿</a-option>
            <a-option value="REVIEWING">评审中</a-option>
            <a-option value="REVIEWED">已评审</a-option>
            <a-option value="DEPRECATED">已废弃</a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 预览抽屉 -->
    <a-drawer
      v-model:visible="previewVisible"
      :title="null"
      width="640px"
      :mask-closable="true"
      class="case-preview-drawer"
    >
      <template #default>
        <div class="drawer-body">
          <!-- 头部：标题与状态胶囊 -->
          <div class="preview-header">
            <h3 class="preview-case-name">{{ previewData.caseName }}</h3>
            <div class="preview-badges">
              <a-tag v-if="fieldVis('priority')" :color="priorityColor(previewData.priority)" size="small">{{ previewData.priority }}</a-tag>
              <a-tag :color="statusColor(previewData.status)" size="small">{{ statusText(previewData.status) }}</a-tag>
              <a-tag v-if="fieldVis('caseType')" color="arcoblue" size="small">{{ caseTypeText(previewData.caseType) }}</a-tag>
            </div>
          </div>

          <!-- 基础信息卡片 -->
          <a-card class="preview-card meta-card" size="small">
            <a-descriptions
              :data="[
                { label: '用例编号', value: previewData.caseCode },
                { label: '创建人', value: previewData.createUserName || '-' },
                ...(fieldVis('moduleId')
                  ? [{ label: '所属模块', value: previewData.moduleName || '-' }]
                  : []),
                { label: '创建时间', value: previewData.createTime },
              ]"
              layout="inline-vertical"
              :column="2"
            />
          </a-card>

          <!-- 前置条件卡片 -->
          <a-card v-if="fieldVis('preCondition')" class="preview-card precondition-card" size="small" title="前置条件">
            <div class="rich-text-preview" v-html="renderHtml(previewData.preCondition, '无')"></div>
          </a-card>

          <!-- 测试步骤 -->
          <div class="preview-section">
            <div class="preview-section-title">测试步骤</div>
            <div v-if="previewData.testSteps && previewData.testSteps.length" class="steps-timeline">
              <div
                v-for="(step, index) in previewData.testSteps"
                :key="index"
                class="step-timeline-item"
              >
                <div class="step-timeline-marker">
                  <div class="step-number-badge">{{ String(index + 1).padStart(2, '0') }}</div>
                  <div v-if="index < previewData.testSteps.length - 1" class="step-timeline-line"></div>
                </div>
                <a-card class="preview-card step-content-card" size="small">
                  <a-row :gutter="16">
                    <a-col :span="24" :md="12">
                      <div class="step-subtitle">步骤</div>
                      <div class="rich-text-preview" v-html="renderHtml(step.step, '-')"></div>
                    </a-col>
                    <a-col :span="24" :md="12">
                      <div class="step-subtitle expected">预期结果</div>
                      <div class="rich-text-preview" v-html="renderHtml(step.expected, '-')"></div>
                    </a-col>
                  </a-row>
                </a-card>
              </div>
            </div>
            <a-empty v-else description="暂无测试步骤" />
          </div>
        </div>
      </template>

      <template #footer>
        <div class="drawer-footer">
          <a-space>
            <a-button @click="previewVisible = false">关闭</a-button>
            <a-button
              v-permission="'qa:testcase:update'"
              type="primary"
              @click="handleEditFromPreview"
            >编辑</a-button>
          </a-space>
        </div>
      </template>
    </a-drawer>
    <!-- 测试集管理弹窗 -->
    <a-modal
      v-model:visible="setModalVisible"
      title="测试集管理"
      width="600px"
      :footer="false"
      :mask-closable="false"
    >
      <a-space style="margin-bottom: 12px"
        >
        <a-button
          v-permission="'qa:testcase:create'"
          type="primary"
          @click="handleAddSet"
        >
          <template #icon><icon-plus /></template>
          新建测试集
        </a-button>
      </a-space>
      <a-table
        :data="setList"
        :loading="setModalLoading"
        :pagination="false"
        row-key="id"
        size="small"
      >
        <template #columns>
          <a-table-column title="测试集名称" data-index="setName" />
          <a-table-column title="描述" data-index="description">
            <template #cell="{ record }">
              <span
                v-if="record.description"
                class="text-ellipsis"
                :title="record.description"
                >{{ record.description }}</span
              >
              <span v-else style="color: #86909c">-</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="120">
            <template #cell="{ record }">
              <a-space>
                <a-button
                  v-permission="'qa:testcase:update'"
                  type="text"
                  size="small"
                  @click="handleEditSet(record)"
                >
                  <template #icon><icon-edit /></template>
                </a-button>
                <a-popconfirm
                  content="确认删除该测试集吗？"
                  type="warning"
                  @ok="handleDeleteSet(record.id)"
                >
                  <a-button
                    v-permission="'qa:testcase:delete'"
                    type="text"
                    size="small"
                    status="danger"
                    @click.stop
                  >
                    <template #icon><icon-delete /></template>
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>

      <!-- 测试集新建/编辑弹窗 -->
      <a-modal
        v-model:visible="setFormVisible"
        :title="setFormTitle"
        width="400px"
        @ok="handleSaveSet"
        @cancel="setFormVisible = false"
        :mask-closable="false"
      >
        <a-form :model="setForm" layout="vertical">
          <a-form-item label="测试集名称" required>
            <a-input
              v-model="setForm.setName"
              placeholder="请输入测试集名称"
            />
          </a-form-item>
          <a-form-item label="描述">
            <a-textarea
              v-model="setForm.description"
              :auto-size="{ minRows: 2 }"
              placeholder="请输入描述"
            />
          </a-form-item>
          <a-form-item label="排序">
            <a-input-number
              v-model="setForm.sort"
              :min="0"
              placeholder="排序"
              style="width: 100%"
            />
          </a-form-item>
        </a-form>
      </a-modal>
    </a-modal>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script setup lang="ts">
  import { ref, onMounted, onBeforeUnmount, watch, nextTick, computed } from 'vue';
  import { useI18n } from 'vue-i18n';
  import { useRouter } from 'vue-router';
  import { Message } from '@arco-design/web-vue';
  import {
    IconPlus,
    IconEdit,
    IconDelete,
    IconDownload,
    IconHistory,
    IconLink,
    IconBug,
    IconSwap,
    IconRight,
    IconList,
    IconMore,
  } from '@arco-design/web-vue/es/icon';
  import Breadcrumb from '@/components/breadcrumb/index.vue';
  import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
  import RichEditor from '@/components/rich-editor/index.vue';
  import TestCaseForm from './components/TestCaseForm.vue';
  import ModuleTreeManager from '@/views/qa/components/ModuleTreeManager.vue';
  import { useProjectStore } from '@/store';
  import useProjectConfigStore from '@/store/modules/projectConfig';
  import {
    getTestCaseList,
    getTestCaseStats,
    deleteTestCase,
    bindAuto,
    unbindAuto,
    getBindAutoList,
    getAutoOptions,
    getTestCaseSetOptions,
    getRequirementList,
    getRequirementDetail,
    exportTestCase,
    getQaModuleList,
    getTestCaseExecutionHistory,
    getBugList,
    saveBug,
    updateBug,
    deleteBug,
    transitionTestCaseStatus,
    getTestCaseDetail,
    getTestCaseSetList,
    saveTestCaseSet,
    updateTestCaseSet,
    deleteTestCaseSet,
  } from '@/api/MyApi/qa';
  import { getUserListByPage } from '@/api/MyApi/user';
  import { deleteRichTextImages } from '@/api/MyApi/fileUpload';

  const { t } = useI18n();
  const projectStore = useProjectStore();
  const router = useRouter();
  const projectConfigStore = useProjectConfigStore();
  const fieldVis = (key: string) =>
    projectConfigStore.isFieldVisible('testCase', key);

  // 浏览器窗口宽度（弹窗宽度、侧栏最大宽度等都跟随它自适应）
  const windowWidth = ref(window.innerWidth);
  const handleWindowResize = () => {
    windowWidth.value = window.innerWidth;
  };
  onMounted(() => window.addEventListener('resize', handleWindowResize));
  onBeforeUnmount(() => window.removeEventListener('resize', handleWindowResize));

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

  // 用于渲染 dropdownRender 插槽中的 VNode
  const VNodeRenderer = {
    props: ['vnodes'],
    render(this: { vnodes: any }) {
      return this.vnodes;
    },
  };

  const loading = ref(false);
  const testCaseList = ref<any[]>([]);
  const searchKeyword = ref('');
  const searchModuleId = ref<number | undefined>(undefined);
  const searchSetId = ref<number | undefined>(undefined);
  const searchLastResult = ref<string | undefined>(undefined);
  const setOptions = ref<any[]>([]);
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
  const modalWidth = computed(() => Math.min(1200, Math.max(360, windowWidth.value * 0.94)));
  const modalTitle = ref('');
  const formData = ref<any>({});
  const testCaseFormRef = ref<any>(null);
  const moduleTreeRef = ref<any>(null);

  const bindModalVisible = ref(false);
  const bindForm = ref<any>({
    autoType: 'UI_SCENE',
    autoId: undefined,
    bindRemark: '',
  });
  const autoOptions = ref<any[]>([]);
  const autoOptionsLoading = ref(false);
  const bindList = ref<any[]>([]);
  const currentCaseId = ref<number | null>(null);

  const requirementOptions = ref<any[]>([]);
  const requirementPage = ref({
    current: 1,
    pageSize: 20,
    hasMore: true,
    keyword: '',
    loading: false,
  });

  // 统计卡片：后端全项目口径聚合，不再只数当前页
  const stats = ref({ total: 0, pass: 0, fail: 0, unexec: 0 });
  const loadStats = async () => {
    if (!projectStore.getProjectId) return;
    try {
      const res: any = await getTestCaseStats(projectStore.getProjectId);
      if (res.code === 200 && res.data) {
        stats.value = { total: 0, pass: 0, fail: 0, unexec: 0, ...res.data };
      }
    } catch (e) {
      console.error(e);
    }
  };
  const moduleOptions = ref<any[]>([]);

  // 执行历史
  const historyVisible = ref(false);
  const executionHistoryList = ref<any[]>([]);

  const previewVisible = ref(false);
  const previewData = ref<any>({});

  const transitionModalVisible = ref(false);
  const transitionForm = ref<any>({});

  // BUG 抽屉
  const bugDrawerVisible = ref(false);
  const bugDrawerTitle = ref('');
  const drawerBugList = ref<any[]>([]);
  const bugDrawerLoading = ref(false);
  const bugDrawerPagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: true,
    showPageSize: true,
    pageSizeOptions: [10, 20, 50, 100],
  });
  const currentCaseIdForBug = ref<number | null>(null);
  const currentCaseRecord = ref<any>({});

  // BUG 弹窗
  const bugModalVisible = ref(false);
  const bugModalTitle = ref('');
  const bugFormData = ref<any>({});
  const isEditBug = ref(false);
  const initialBugFileIds = ref<Set<string>>(new Set());
  const uploadedBugFileIds = ref<Set<string>>(new Set());
  const bugRequirementOptions = ref<any[]>([]);
  const bugRequirementPage = ref({
    current: 1,
    pageSize: 20,
    hasMore: true,
    keyword: '',
    loading: false,
  });
  const bugTestCaseOptions = ref<any[]>([]);
  const bugTestCasePage = ref({
    current: 1,
    pageSize: 20,
    hasMore: true,
    keyword: '',
    loading: false,
  });

  // 测试集管理弹窗
  const setModalVisible = ref(false);
  const setModalLoading = ref(false);
  const setList = ref<any[]>([]);
  const setFormVisible = ref(false);
  const setFormTitle = ref('');
  const setForm = ref<any>({ setName: '', description: '', sort: 0 });
  const isEditSet = ref(false);

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
    extractFileIds(bugFormData.value.description).forEach((id) => set.add(id));
    extractFileIds(bugFormData.value.reproduceSteps).forEach((id) =>
      set.add(id)
    );
    return set;
  };

  const handleUploadedBugFile = (fileId: string) => {
    uploadedBugFileIds.value.add(fileId);
  };
  const userOptions = ref<any[]>([]);
  const userPage = ref({
    current: 1,
    pageSize: 20,
    hasMore: true,
    keyword: '',
    loading: false,
  });

  const loadData = async () => {
    if (!projectStore.getProjectId) return;
    loading.value = true;
    try {
      const res: any = await getTestCaseList(
        projectStore.getProjectId,
        searchModuleId.value,
        searchSetId.value,
        undefined,
        searchKeyword.value || undefined,
        searchLastResult.value,
        pagination.value.current,
        pagination.value.pageSize
      );
      if (res.data && res.data.records) {
        testCaseList.value = res.data.records;
        pagination.value.total = res.data.total;
      } else {
        testCaseList.value = res.data || [];
      }
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
    searchModuleId.value = undefined;
    searchSetId.value = undefined;
    searchLastResult.value = undefined;
    pagination.value.current = 1;
    loadData();
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

  const handleTreeSelect = (selectedKeys: any) => {
    searchModuleId.value =
      selectedKeys[0] === 0 ? undefined : selectedKeys[0];
    pagination.value.current = 1;
    loadData();
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

  // 抽屉/预览里继续用弹窗编辑
  const handleEditInModal = (record: any) => {
    modalTitle.value = t('qa.testCase.edit');
    formData.value = { ...record };
    if (formData.value.tags && typeof formData.value.tags === 'string') {
      formData.value.tags = formData.value.tags
        .split(',')
        .filter((t: string) => t.trim());
    } else if (!formData.value.tags) {
      formData.value.tags = [];
    }
    modalVisible.value = true;
  };

  const handleEditFromPreview = () => {
    previewVisible.value = false;
    nextTick(() => {
      handleEditInModal(previewData.value);
    });
  };

  const handlePreview = (record: any) => {
    previewData.value = { ...record };
    previewVisible.value = true;
  };

  const openTransitionModal = (record: any) => {
    transitionForm.value = {
      testCaseId: record.id,
      currentStatus: record.status,
      targetStatus: '',
    };
    transitionModalVisible.value = true;
  };

  const handleConfirmTransition = async () => {
    if (!transitionForm.value.targetStatus) {
      Message.warning('请选择目标状态');
      return;
    }
    try {
      const res: any = await transitionTestCaseStatus(
        transitionForm.value.testCaseId,
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

  const handleShowHistory = async (record: any) => {
    try {
      const res: any = await getTestCaseExecutionHistory(record.id);
      if (res.code === 200) {
        executionHistoryList.value = res.data || [];
        historyVisible.value = true;
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleSave = async () => {
    const success = await testCaseFormRef.value?.save();
    if (success) {
      modalVisible.value = false;
      await loadData();
      await moduleTreeRef.value?.loadData();
    }
  };

  // 测试集管理
  const handleOpenSetModal = async () => {
    setModalVisible.value = true;
    await loadSetList();
  };

  const loadSetList = async () => {
    if (!projectStore.getProjectId) return;
    setModalLoading.value = true;
    try {
      const res: any = await getTestCaseSetList(projectStore.getProjectId);
      setList.value = res.data || [];
    } catch (e) {
      console.error(e);
    } finally {
      setModalLoading.value = false;
    }
  };

  const handleAddSet = () => {
    isEditSet.value = false;
    setFormTitle.value = '新建测试集';
    setForm.value = {
      projectId: projectStore.getProjectId,
      setName: '',
      description: '',
      sort: 0,
    };
    setFormVisible.value = true;
  };

  const handleEditSet = (record: any) => {
    isEditSet.value = true;
    setFormTitle.value = '编辑测试集';
    setForm.value = { ...record };
    setFormVisible.value = true;
  };

  const handleSaveSet = async () => {
    if (!setForm.value.setName || setForm.value.setName.trim() === '') {
      Message.warning('请输入测试集名称');
      return;
    }
    try {
      const api = isEditSet.value ? updateTestCaseSet : saveTestCaseSet;
      const res: any = await api({
        ...setForm.value,
        projectId: projectStore.getProjectId,
      });
      if (res.code === 200) {
        Message.success('保存成功');
        setFormVisible.value = false;
        await loadSetList();
        await loadSetOptions();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleDeleteSet = async (id: number) => {
    try {
      const res: any = await deleteTestCaseSet(id);
      if (res.code === 200) {
        Message.success('删除成功');
        await loadSetList();
        await loadSetOptions();
        if (searchSetId.value === id) {
          searchSetId.value = undefined;
          await loadData();
        }
      }
    } catch (e) {
      console.error(e);
    }
  };

  // 模块管理
  const handleModuleChanged = async (type: string, payload?: any) => {
    // 若当前筛选的模块被删除，重置为全部
    if (type === 'delete' && payload?.id && searchModuleId.value === payload.id) {
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
        await loadData();
        await moduleTreeRef.value?.loadData();
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleExport = async () => {
    if (!projectStore.getProjectId) {
      Message.warning('请先选择项目');
      return;
    }
    try {
      const res: any = await exportTestCase(
        projectStore.getProjectId,
        searchModuleId.value,
        searchSetId.value
      );
      const blob =
        res instanceof Blob
          ? res
          : new Blob([res], {
              type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            });
      const link = document.createElement('a');
      link.href = URL.createObjectURL(blob);
      link.download = `test_case_export_${new Date().getTime()}.xlsx`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(link.href);
      Message.success('导出成功');
    } catch (e) {
      console.error(e);
      Message.error('导出失败');
    }
  };

  // 关联自动化
  const handleBindAuto = async (record: any) => {
    currentCaseId.value = record.id;
    bindForm.value = {
      autoType: 'UI_SCENE',
      autoId: undefined,
      bindRemark: '',
    };
    bindModalVisible.value = true;
    await loadBindList(record.id);
    await loadAutoOptions('UI_SCENE');
  };

  const loadBindList = async (caseId: number) => {
    try {
      const res: any = await getBindAutoList(caseId);
      bindList.value = res.data || [];
    } catch (e) {
      console.error(e);
    }
  };

  const loadAutoOptions = async (autoType: string) => {
    autoOptionsLoading.value = true;
    try {
      const res: any = await getAutoOptions(
        autoType,
        projectStore.getProjectId
      );
      autoOptions.value = res.data || [];
    } catch (e) {
      console.error(e);
    } finally {
      autoOptionsLoading.value = false;
    }
  };

  const handleAutoTypeChange = (val: string) => {
    bindForm.value.autoId = undefined;
    loadAutoOptions(val);
  };

  const handleBindSave = async () => {
    if (!currentCaseId.value || !bindForm.value.autoId) {
      Message.warning('请选择自动化用例');
      return;
    }
    try {
      const res: any = await bindAuto(
        currentCaseId.value,
        bindForm.value.autoType,
        bindForm.value.autoId,
        bindForm.value.bindRemark
      );
      if (res.code === 200) {
        Message.success('绑定成功');
        bindForm.value.autoId = undefined;
        await loadBindList(currentCaseId.value);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleUnbind = async (bindId: number) => {
    try {
      const res: any = await unbindAuto(bindId);
      if (res.code === 200) {
        Message.success('解绑成功');
        if (currentCaseId.value) await loadBindList(currentCaseId.value);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // ==================== BUG 抽屉相关方法 ====================
  const handleViewBugs = (record: any) => {
    currentCaseIdForBug.value = record.id;
    currentCaseRecord.value = record;
    bugDrawerTitle.value = `「${record.caseName}」关联BUG`;
    bugDrawerPagination.value = {
      current: 1,
      pageSize: 10,
      total: 0,
      showTotal: true,
      showPageSize: true,
      pageSizeOptions: [10, 20, 50, 100],
    };
    bugDrawerVisible.value = true;
    loadDrawerBugs();
  };

  const loadDrawerBugs = async () => {
    if (!currentCaseIdForBug.value || !projectStore.getProjectId) return;
    bugDrawerLoading.value = true;
    try {
      const res: any = await getBugList(
        projectStore.getProjectId,
        undefined, // keyword
        undefined, // status
        undefined, // severity
        undefined, // priority
        undefined, // requirementId
        currentCaseIdForBug.value, // testCaseId
        undefined, // moduleId
        undefined, // environment
        undefined, // reproduceRate
        undefined, // closeReason
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

  const handleAddBug = async () => {
    if (!currentCaseIdForBug.value) {
      Message.warning('当前用例信息缺失，请重新打开关联BUG抽屉');
      return;
    }
    isEditBug.value = false;
    bugModalTitle.value = '新增BUG';
    bugFormData.value = {
      severity: 'NORMAL',
      priority: 'MEDIUM',
      status: 'NEW',
      projectId: projectStore.getProjectId,
      testCaseId: currentCaseIdForBug.value,
      moduleId: currentCaseRecord.value?.moduleId || undefined,
      tags: [],
    };
    bugRequirementOptions.value = [];
    bugTestCaseOptions.value = [];
    initialBugFileIds.value = new Set();
    uploadedBugFileIds.value = new Set();
    await loadBugRequirementOptions();
    await loadBugTestCaseOptions();
    // 把当前用例插入关联用例选项首位，避免显示空白
    const currentCase = currentCaseRecord.value;
    if (
      currentCase &&
      !bugTestCaseOptions.value.find(
        (tc: any) => tc.id === currentCaseIdForBug.value
      )
    ) {
      bugTestCaseOptions.value.unshift({
        id: currentCaseIdForBug.value,
        caseCode: currentCase.caseCode,
        caseName: currentCase.caseName,
      });
    }
    bugModalVisible.value = true;
  };

  const handleEditBug = async (record: any) => {
    isEditBug.value = true;
    bugModalTitle.value = '编辑BUG';
    bugFormData.value = { ...record };
    if (bugFormData.value.tags && typeof bugFormData.value.tags === 'string') {
      bugFormData.value.tags = bugFormData.value.tags
        .split(',')
        .filter((t: string) => t.trim());
    }
    initialBugFileIds.value = collectBugFileIds();
    uploadedBugFileIds.value = new Set();
    bugRequirementOptions.value = [];
    bugTestCaseOptions.value = [];
    await loadBugRequirementOptions();
    if (
      bugFormData.value.requirementId &&
      !bugRequirementOptions.value.find(
        (r: any) => r.id === bugFormData.value.requirementId
      )
    ) {
      try {
        const res: any = await getRequirementDetail(
          bugFormData.value.requirementId
        );
        if (res.data)
          bugRequirementOptions.value = [
            res.data,
            ...bugRequirementOptions.value,
          ];
      } catch (e) {
        console.error(e);
      }
    }
    await loadBugTestCaseOptions();
    if (
      bugFormData.value.testCaseId &&
      !bugTestCaseOptions.value.find(
        (tc: any) => tc.id === bugFormData.value.testCaseId
      )
    ) {
      try {
        const res: any = await getTestCaseDetail(bugFormData.value.testCaseId);
        if (res.data)
          bugTestCaseOptions.value = [res.data, ...bugTestCaseOptions.value];
      } catch (e) {
        console.error(e);
      }
    }
    bugModalVisible.value = true;
  };

  const handleSaveBug = async () => {
    if (!bugFormData.value.title) {
      Message.warning('请输入BUG标题');
      return;
    }
    try {
      const payload = { ...bugFormData.value };
      if (payload.tags && Array.isArray(payload.tags)) {
        payload.tags = payload.tags.join(',');
      }
      const api = isEditBug.value ? updateBug : saveBug;
      const res: any = await api(payload);
      if (res.code === 200) {
        Message.success('保存成功');
        // 计算并删除被移除的富文本图片
        const currentFileIds = collectBugFileIds();
        const allSessionFileIds = new Set<string>([
          ...initialBugFileIds.value,
          ...uploadedBugFileIds.value,
        ]);
        const deletedFileIds = Array.from(allSessionFileIds).filter(
          (id) => !currentFileIds.has(id)
        );
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

  // BUG弹窗关联需求搜索
  const loadBugRequirementOptions = async (
    keyword?: string,
    isLoadMore = false
  ) => {
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
        undefined,
        undefined,
        undefined,
        undefined,
        bugRequirementPage.value.current,
        bugRequirementPage.value.pageSize
      );
      const records = res.data?.records || [];
      if (isLoadMore) {
        bugRequirementOptions.value.push(...records);
      } else {
        bugRequirementOptions.value = records;
      }
      bugRequirementPage.value.hasMore =
        records.length >= bugRequirementPage.value.pageSize;
    } catch (e) {
      console.error(e);
    }
  };
  const loadMoreBugRequirement = async () => {
    if (!bugRequirementPage.value.hasMore || bugRequirementPage.value.loading)
      return;
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
  const loadBugTestCaseOptions = async (
    keyword?: string,
    isLoadMore = false
  ) => {
    if (!projectStore.getProjectId) return;
    if (!isLoadMore) {
      bugTestCasePage.value.current = 1;
      bugTestCasePage.value.keyword = keyword || '';
      bugTestCaseOptions.value = [];
    }
    try {
      const res: any = await getTestCaseList(
        projectStore.getProjectId,
        undefined,
        undefined,
        bugTestCasePage.value.keyword || undefined,
        undefined,
        undefined,
        bugTestCasePage.value.current,
        bugTestCasePage.value.pageSize
      );
      const records = res.data?.records || [];
      if (isLoadMore) {
        bugTestCaseOptions.value.push(...records);
      } else {
        bugTestCaseOptions.value = records;
      }
      bugTestCasePage.value.hasMore =
        records.length >= bugTestCasePage.value.pageSize;
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

  // 指派人搜索
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
        pageSize: userPage.value.pageSize,
      });
      const records = res.data?.records || [];
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

  const priorityColor = (priority: string) => {
    const map: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue' };
    return map[priority] || 'gray';
  };
  const caseTypeText = (type: string) => {
    const map: Record<string, string> = {
      FUNCTION: '功能',
      API: '接口',
      PERFORMANCE: '性能',
      COMPATIBILITY: '兼容',
      SMOKE: '冒烟',
    };
    return map[type] || type;
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
      PASS: '通过',
      FAIL: '失败',
      BLOCK: '阻塞',
      NA: '不适用',
    };
    return map[result] || result;
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
  const severityText = (severity: string) => {
    const map: Record<string, string> = {
      FATAL: '致命',
      SERIOUS: '严重',
      NORMAL: '一般',
      TIPS: '提示',
    };
    return map[severity] || severity;
  };
  const bugStatusColor = (status: string) => {
    const map: Record<string, string> = {
      NEW: 'red',
      CONFIRMED: 'orange',
      FIXING: 'gold',
      FIXED: 'blue',
      VERIFIED: 'cyan',
      CLOSED: 'green',
      REJECTED: 'gray',
    };
    return map[status] || 'gray';
  };
  const bugStatusText = (status: string) => {
    const map: Record<string, string> = {
      NEW: '新建',
      CONFIRMED: '已确认',
      FIXING: '修复中',
      FIXED: '已修复',
      VERIFIED: '已验证',
      CLOSED: '已关闭',
      REJECTED: '已驳回',
    };
    return map[status] || status;
  };

  onMounted(() => {
    loadData();
    loadSetOptions();
    loadModuleOptions();
  });

  watch(
    () => projectStore.getProjectId,
    (newId) => {
      if (newId) {
        loadData();
        loadSetOptions();
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
    grid-template-columns: 76px minmax(0, 1fr);
    align-items: center;
    gap: 8px;
    flex: 1 1 240px;
    min-width: 220px;
    max-width: 320px;
  }

  .search-label {
    color: var(--color-text-2);
    font-size: 14px;
    white-space: nowrap;
    text-align: right;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .search-control {
    width: 100%;
    min-width: 0;
  }

  .list-card .stats-row {
    margin-top: 0;
  }

  .table-wrapper {
    flex: 1;
    min-height: 0;
    overflow: auto;
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
</style>
