<template>
  <a-drawer
    :visible="visible"
    :width="fullScreen ? '100%' : 860"
    :mask-closable="true"
    :footer="!!props.autoEdit || tabKey === 'comments'"
    :hide-cancel="true"
    class="case-detail-drawer"
    @cancel="close"
  >
    <template #title>
      <div class="dd-title-wrap">
        <!-- 支持自定义抽屉标题 -->
        <div v-if="props.drawerTitle" class="dd-custom-title">{{ props.drawerTitle }}</div>
        <!-- 默认抽屉标题栏：编号 + 名称（双击改）+ 等级（双击改）+ 评审状态（只读） -->
        <div v-else class="dd-header-line">
        <span class="dd-code">{{ detail.caseCode }}</span>

        <a-input
          v-if="headerEditField === 'caseName'"
          v-model="headerEditDraft"
          size="small"
          class="dd-name-input"
          autofocus
          :max-length="100"
          @press-enter="commitHeaderEdit('caseName')"
          @blur="commitHeaderEdit('caseName')"
          @keydown.esc.stop="headerEditField = null"
        />
        <a-tooltip
          v-else
          :content="detail.caseName"
          position="bottom"
          :disabled="!detail.caseName"
        >
          <span
            class="dd-name"
            :class="{ editable: canUpdateCase }"
            :title="canUpdateCase ? $t('qa.testCase.dblClickEdit') : ''"
            @dblclick="startHeaderEdit('caseName')"
            >{{ detail.caseName || '-' }}</span
          >
        </a-tooltip>

        <a-select
          v-if="headerEditField === 'priority'"
          v-model="headerEditDraft"
          size="small"
          style="width: 90px"
          default-popup-visible
          @change="commitHeaderEdit('priority')"
          @popup-visible-change="onHeaderPopupClose"
        >
          <a-option value="P0">P0</a-option>
          <a-option value="P1">P1</a-option>
          <a-option value="P2">P2</a-option>
        </a-select>
        <a-tag
          v-else-if="fieldVis('priority')"
          :color="priorityColor(detail.priority)"
          size="small"
          :class="{ editable: canUpdateCase }"
          :title="canUpdateCase ? $t('qa.testCase.dblClickEdit') : ''"
          @dblclick="startHeaderEdit('priority')"
          >{{ detail.priority }}</a-tag
        >

        <a-tag :color="statusColor(detail.status)" size="small">{{
          statusText(detail.status)
        }}</a-tag>
      </div>
        <!-- 全屏切换按钮 -->
        <a-button
          type="text"
          size="small"
          class="dd-fullscreen-btn"
          @click="fullScreen = !fullScreen"
        >
          <template #icon>
            <icon-fullscreen v-if="!fullScreen" />
            <icon-fullscreen-exit v-else />
          </template>
        </a-button>
      </div>
    </template>
    <template #default>
      <a-spin :loading="detailLoading" style="width: 100%; height: 100%">
        <!-- 纯编辑模式：无 tab，直接展示完整表单 -->
        <div v-if="props.autoEdit" class="dd-body dd-edit-only">
          <TestCaseForm
            ref="editFormRef"
            :initial-data="detail"
            @saved="handleSaved"
          />
        </div>
        <!-- 默认模式：完整 tab 布局 -->
        <div v-else class="dd-body">
          <a-tabs
            v-model:active-key="tabKey"
            type="line"
            class="dd-tabs"
            @change="handleTabChange"
          >
            <!-- ========== 详情 ========== -->
            <a-tab-pane key="info" :title="$t('qa.testCase.tab.info')">
              <!-- 查看态 -->
              <template v-if="!editing">
                <div class="info-toolbar">
                  <a-button
                    v-permission="'qa:testcase:update'"
                    type="outline"
                    size="small"
                    @click="startEdit"
                  >
                    <template #icon><icon-edit /></template>{{ $t('qa.testCase.actionEdit') }}
                  </a-button>
                </div>
                <template v-if="fieldVis('preCondition')">
                  <div class="dd-section-title">{{ $t('qa.testCase.preCondition') }}</div>
                  <RichEditor
                    v-if="hasPreCondition"
                    :model-value="detail.preCondition"
                    readonly
                    class="pre-condition-view"
                  />
                  <div v-else class="pre-condition-empty">{{ $t('qa.testCase.emptyText') }}</div>
                </template>
                <div class="dd-section-title">{{ $t('qa.testCase.steps') }}</div>
                <a-table
                  v-if="detailSteps.length"
                  :data="detailSteps"
                  :pagination="false"
                  size="small"
                  row-key="index"
                  :bordered="{ cell: true }"
                >
                  <template #columns>
                    <a-table-column title="#" :width="50" align="center">
                      <template #cell="{ rowIndex }">
                        <span class="step-no">{{ rowIndex + 1 }}</span>
                      </template>
                    </a-table-column>
                    <a-table-column :title="$t('qa.testCase.step')">
                      <template #cell="{ record }">
                        <div
                          class="rich-text-preview"
                          v-html="renderHtml(record.step, '-')"
                        ></div>
                      </template>
                    </a-table-column>
                    <a-table-column :title="$t('qa.testCase.expected')">
                      <template #cell="{ record }">
                        <div
                          class="rich-text-preview"
                          v-html="renderHtml(record.expected, '-')"
                        ></div>
                      </template>
                    </a-table-column>
                  </template>
                </a-table>
                <a-empty v-else :description="$t('qa.testCase.noSteps')" />

                <!-- 底部元信息：编号/创建人等非编辑项，淡灰色展示 -->
                <a-divider />
                <div class="meta-footer">
                  <div
                    v-for="item in infoItems"
                    :key="item.label"
                    class="meta-footer-item"
                  >
                    <span class="meta-footer-label">{{ item.label }}</span>
                    <span class="meta-footer-value">{{ item.value }}</span>
                  </div>
                </div>
              </template>

              <!-- 编辑态：内嵌精简用例表单（无关联需求/分类属性/状态协作，那些字段在列表行内改） -->
              <template v-else>
                <div class="info-toolbar">
                  <span class="editing-tip">{{ $t('qa.testCase.editing') }}</span>
                  <a-space>
                    <a-button size="small" @click="cancelEdit">{{ $t('qa.common.cancel') }}</a-button>
                    <a-button
                      type="primary"
                      size="small"
                      :loading="editSaving"
                      @click="saveEdit"
                      >{{ props.confirmText || $t('qa.common.save') }}</a-button
                    >
                  </a-space>
                </div>
                <TestCaseForm
                  ref="editFormRef"
                  :initial-data="detail"
                  slim
                  @saved="handleSaved"
                />
              </template>
            </a-tab-pane>

            <!-- ========== 关联需求 ========== -->
            <a-tab-pane key="req">
              <template #title
                >{{ $t('qa.testCase.tab.req') }}
                <span v-if="requirementList.length" class="tab-count">{{
                  requirementList.length
                }}</span></template
              >
              <div v-if="canUpdateCase" class="tab-toolbar req-bind-bar">
                <a-button type="outline" size="small" @click="openReqModal">
                  <template #icon><icon-plus /></template>
                  {{ $t('qa.testCase.selectRequirements') }}
                </a-button>
                <span class="req-bind-tip"
                  >{{ $t('qa.testCase.reqBindTip', { count: caseReqIds.length }) }}</span
                >
              </div>
              <a-table
                v-if="requirementList.length"
                :data="requirementList"
                :pagination="false"
                size="small"
                row-key="id"
              >
                <template #columns>
                  <a-table-column :title="$t('qa.testCase.colCode')" :width="120">
                    <template #cell="{ record }">{{ record.reqCode }}</template>
                  </a-table-column>
                  <a-table-column :title="$t('qa.testCase.reqName')">
                    <template #cell="{ record }">
                      <a-link @click="goRequirement(record)">{{
                        record.title
                      }}</a-link>
                    </template>
                  </a-table-column>
                  <a-table-column :title="$t('qa.testCase.status')" :width="100">
                    <template #cell="{ record }">
                      <a-tag size="small" color="arcoblue">{{
                        reqStatusText(record.status)
                      }}</a-tag>
                    </template>
                  </a-table-column>
                </template>
              </a-table>
              <a-empty v-else :description="$t('qa.testCase.noRequirement')" />
            </a-tab-pane>

            <!-- ========== 关联用例（自动化实现） ========== -->
            <a-tab-pane key="rel">
              <template #title
                >{{ $t('qa.testCase.tab.rel') }}
                <span v-if="bindList.length" class="tab-count">{{
                  bindList.length
                }}</span></template
              >
              <div class="tab-toolbar rel-bind-bar">
                <a-radio-group v-model="relType" type="button" size="small">
                  <a-radio value="ALL">{{ $t('qa.common.all') }}</a-radio>
                  <a-radio value="API_CASE">{{ $t('qa.testCase.apiCase') }}</a-radio>
                  <a-radio value="UI_SCENE">{{ $t('qa.testCase.uiScene') }}</a-radio>
                </a-radio-group>
                <a-button
                  v-permission="'qa:testcase:update'"
                  type="outline"
                  size="small"
                  @click="openAutoModal"
                >
                  <template #icon><icon-plus /></template>
                  {{ $t('qa.testCase.bindAuto') }}
                </a-button>
              </div>
              <a-table
                :data="filteredBindList"
                :loading="relLoading"
                :pagination="false"
                size="small"
                row-key="id"
              >
                <template #columns>
                  <a-table-column :title="$t('qa.testCase.colType')" :width="90">
                    <template #cell="{ record }">
                      <a-tag
                        size="small"
                        :color="
                          record.autoType === 'API_CASE' ? 'arcoblue' : 'purple'
                        "
                      >
                        {{
                          record.autoType === 'API_CASE'
                            ? $t('qa.testCase.apiCase')
                            : $t('qa.testCase.uiScene')
                        }}
                      </a-tag>
                    </template>
                  </a-table-column>
                  <a-table-column :title="$t('qa.testCase.colName')" data-index="autoName" />
                  <a-table-column :title="$t('qa.testCase.bindRemark')" data-index="bindRemark">
                    <template #cell="{ record }">{{
                      record.bindRemark || '-'
                    }}</template>
                  </a-table-column>
                  <a-table-column
                    :title="$t('qa.testCase.bindTime')"
                    data-index="createTime"
                    :width="150"
                  />
                </template>
                <template #empty>
                  <a-empty :description="$t('qa.testCase.noAuto')" />
                </template>
              </a-table>
            </a-tab-pane>

            <!-- ========== 关联缺陷 ========== -->
            <a-tab-pane key="bug">
              <template #title
                >{{ $t('qa.testCase.tab.bug') }}
                <span v-if="bugList.length + planBugList.length" class="tab-count">{{
                  bugList.length + planBugList.length
                }}</span></template
              >
              <a-radio-group v-model="bugTabMode" type="button" size="small" style="margin-bottom: 10px">
                <a-radio value="direct">{{ $t('qa.testCase.bugDirect') }}</a-radio>
                <a-radio value="plan">{{ $t('qa.testPlan.title') }}</a-radio>
              </a-radio-group>
              <!-- 直接关联：本 tab 内关联/新增；测试计划：执行中关联（只读），两区共用一张表 -->
              <div v-if="bugTabMode === 'direct'" class="tab-toolbar rel-bind-bar">
                <span class="req-bind-tip"
                  >{{ $t('qa.testCase.bugDirectTip') }}</span
                >
                <a-space>
                  <a-button
                    v-permission="'qa:bug:update'"
                    type="outline"
                    size="small"
                    @click="openBugBindModal"
                  >
                    <template #icon><icon-link /></template>
                    {{ $t('qa.testCase.bindExistingBug') }}
                  </a-button>
                  <a-button
                    v-permission="'qa:bug:create'"
                    type="primary"
                    size="small"
                    @click="openNewBugModal"
                  >
                    <template #icon><icon-plus /></template>
                    {{ $t('qa.testCase.newBug') }}
                  </a-button>
                </a-space>
              </div>
              <a-table
                :data="bugTabMode === 'direct' ? bugList : planBugList"
                :loading="bugLoading"
                :pagination="false"
                size="small"
                row-key="id"
              >
                <template #columns>
                  <a-table-column :title="$t('qa.testCase.colCode')" :width="110">
                    <template #cell="{ record }">{{ record.bugCode }}</template>
                  </a-table-column>
                  <a-table-column :title="$t('qa.bug.name')">
                    <template #cell="{ record }">
                      <a-link @click="goBug(record)">{{ record.title }}</a-link>
                    </template>
                  </a-table-column>
                  <a-table-column :title="$t('qa.bug.severity')" :width="90">
                    <template #cell="{ record }">
                      <a-tag
                        size="small"
                        :color="bugSeverityColor(record.severity)"
                        >{{ bugSeverityText(record.severity) }}</a-tag
                      >
                    </template>
                  </a-table-column>
                  <a-table-column :title="$t('qa.testCase.status')" :width="90">
                    <template #cell="{ record }">
                      <a-tag
                        size="small"
                        :color="bugStatusColor(record.status)"
                        >{{ bugStatusText(record.status) }}</a-tag
                      >
                    </template>
                  </a-table-column>
                  <a-table-column
                    v-if="bugTabMode === 'direct' && hasPerm('qa:bug:update')"
                    :title="$t('qa.common.operation')"
                    :width="70"
                    align="center"
                  >
                    <template #cell="{ record }">
                      <a-popconfirm
                        :content="$t('qa.testCase.unbindBugConfirm')"
                        type="warning"
                        @ok="handleUnbindBug(record)"
                      >
                        <a-button type="text" size="small" status="danger"
                          >{{ $t('qa.testCase.unbind') }}</a-button
                        >
                      </a-popconfirm>
                    </template>
                  </a-table-column>
                </template>
                <template #empty>
                  <a-empty :description="bugTabMode === 'direct' ? $t('qa.testCase.noBugDirect') : $t('qa.testCase.noBugPlan')" />
                </template>
              </a-table>
            </a-tab-pane>

            <!-- ========== 操作日志 ========== -->
            <a-tab-pane key="logs">
              <template #title
                >{{ $t('qa.testCase.tab.logs') }}
                <span v-if="groupedOperationLogs.length" class="tab-count">{{
                  groupedOperationLogs.length
                }}</span></template
              >
              <a-alert
                v-if="lastReviewRejected"
                type="warning"
                class="reject-alert"
                show-icon
              >
                {{ $t('qa.testCase.reviewRejectedTip') }}
                <template v-if="operationLogList[0]?.comment">
                  <div class="reject-comment"
                    >{{ $t('qa.testCase.rejectComment', { comment: operationLogList[0].comment }) }}</div
                  >
                </template>
              </a-alert>
              <a-timeline
                v-if="groupedOperationLogs.length > 0"
                class="oplog-timeline"
              >
                <a-timeline-item
                  v-for="group in groupedOperationLogs"
                  :key="group.id"
                >
                  <div class="oplog-head">
                    <span class="oplog-operator">{{
                      group.operatorName || $t('qa.testCase.userPrefix') + group.operatorId
                    }}</span>
                    <a-tag
                      :color="operationColor(group.operation)"
                      size="small"
                      >{{ operationText(group.operation) }}</a-tag
                    >
                    <span class="oplog-time">{{ group.operateTime }}</span>
                  </div>
                  <!-- 单条字段变更：直接平铺展示 -->
                  <template v-if="group.changes.length === 1">
                    <div v-if="group.changes[0].fieldName" class="oplog-change">
                      <span class="oplog-field">{{
                        fieldNameText(group.changes[0].fieldName)
                      }}</span
                      >：
                      <span class="oplog-old">{{
                        renderLogValue(
                          group.changes[0].fieldName,
                          group.changes[0].oldValue
                        )
                      }}</span>
                      <icon-arrow-right class="oplog-arrow" />
                      <span class="oplog-new">{{
                        renderLogValue(
                          group.changes[0].fieldName,
                          group.changes[0].newValue
                        )
                      }}</span>
                    </div>
                  </template>
                  <!-- 同一次操作的多处字段变更：聚合为一条，可展开 -->
                  <div v-else class="oplog-multi">
                    <a-link @click="toggleLogGroup(group.id)">
                      {{
                        expandedLogGroups.has(group.id)
                          ? $t('qa.testCase.collapse')
                          : $t('qa.testCase.expandChanges', { count: group.changes.length })
                      }}
                    </a-link>
                    <template v-if="expandedLogGroups.has(group.id)">
                      <div
                        v-for="log in group.changes"
                        :key="log.id"
                        class="oplog-change"
                      >
                        <span class="oplog-field">{{
                          fieldNameText(log.fieldName)
                        }}</span
                        >：
                        <span class="oplog-old">{{
                          renderLogValue(log.fieldName, log.oldValue)
                        }}</span>
                        <icon-arrow-right class="oplog-arrow" />
                        <span class="oplog-new">{{
                          renderLogValue(log.fieldName, log.newValue)
                        }}</span>
                      </div>
                    </template>
                  </div>
                  <div v-if="group.comment" class="oplog-comment">{{
                    group.comment
                  }}</div>
                </a-timeline-item>
              </a-timeline>
              <a-empty v-else :description="$t('qa.testCase.noLogs')" />
            </a-tab-pane>

            <!-- ========== 评审记录（投票事件流，逐票留痕） ========== -->
            <a-tab-pane key="review">
              <template #title
                >{{ $t('qa.testCase.tab.reviewHistory') }}
                <span v-if="reviewHistory.length" class="tab-count">{{
                  reviewHistory.length
                }}</span></template
              >
              <template v-if="reviewHistory.length">
                <div
                  v-for="(h, idx) in reviewHistory"
                  :key="idx"
                  class="vote-item"
                >
                  <a-avatar
                    :size="32"
                    :image-url="h.reviewerAvatar || undefined"
                    class="vote-avatar"
                  >
                    {{ (h.reviewerName || '#').slice(0, 1) }}
                  </a-avatar>
                  <div class="vote-main">
                    <div class="vote-name-line">
                      <span class="vote-name">{{
                        h.reviewerName || `#${h.reviewerId}`
                      }}</span>
                      <span v-if="h.reviewCode" class="vote-review"
                        >{{ h.reviewCode }} · {{ h.reviewTitle }}</span
                      >
                    </div>
                    <div v-if="h.comment" class="vote-comment">{{
                      h.comment
                    }}</div>
                    <div class="vote-time">{{ h.reviewTime || '' }}</div>
                  </div>
                  <a-tag
                    class="vote-result"
                    size="small"
                    :color="voteConclusionColor(h.conclusion)"
                  >
                    {{ voteConclusionText(h.conclusion) }}
                  </a-tag>
                </div>
              </template>
              <a-empty v-else :description="$t('qa.testCase.noReview')" />
            </a-tab-pane>

            <!-- ========== 评论（二级回复 + @提及） ========== -->
            <a-tab-pane key="comments" class="comment-pane">
              <template #title
                >{{ $t('qa.testCase.tab.comments') }}
                <span v-if="commentList.length" class="tab-count">{{
                  commentList.length
                }}</span></template
              >
              <div class="comment-tab-body">
                <!-- 评论列表 -->
                <div v-if="commentTree.length" class="comment-list">
                  <div
                    v-for="cmt in commentTree"
                    :key="cmt.id"
                    :id="'comment-' + cmt.id"
                    class="comment-item"
                    :class="{ 'comment-flash': flashCommentId === cmt.id }"
                  >
                    <a-avatar
                      class="comment-avatar"
                      :size="32"
                      :image-url="cmt.createUserAvatar || undefined"
                      :style="
                        cmt.createUserAvatar
                          ? {}
                          : { backgroundColor: avatarColor(commentName(cmt)) }
                      "
                      >{{
                        cmt.createUserAvatar ? '' : avatarText(commentName(cmt))
                      }}</a-avatar
                    >
                    <div class="comment-main">
                      <div class="comment-head">
                        <span class="comment-who">{{ commentName(cmt) }}</span>
                        <span class="comment-time">{{ cmt.createTime }}</span>
                        <div
                          v-if="hasPerm('qa:testcase:update')"
                          class="comment-actions"
                        >
                          <a-link @click="startReply(cmt, null)">{{ $t('qa.testCase.reply') }}</a-link>
                          <template v-if="cmt.createUserId === currentUserId">
                            <a-link @click="startEditComment(cmt)">{{ $t('qa.testCase.actionEdit') }}</a-link>
                            <a-popconfirm
                              :content="$t('qa.testCase.deleteCommentConfirm')"
                              @ok="handleDeleteComment(cmt)"
                            >
                              <a-link status="danger">{{ $t('qa.common.delete') }}</a-link>
                            </a-popconfirm>
                          </template>
                        </div>
                      </div>
                      <div
                        v-if="editingCommentId === cmt.id"
                        class="comment-edit-input"
                      >
                        <RichEditor v-model="editingContent" auto-height mention :mention-list="mentionOptions" :placeholder="$t('qa.testCase.editCommentPlaceholder')" @mention-trigger="handleMentionSearch('')" @mention="onMentionSelect" />
                        <div class="edit-actions">
                          <a-button type="primary" size="small" :loading="commentSaving" @click="saveEditComment(cmt)">{{ $t('qa.common.save') }}</a-button>
                          <a-link @click="editingCommentId = null">{{ $t('qa.common.cancel') }}</a-link>
                        </div>
                      </div>
                      <RichEditor v-else :model-value="cmt.content" readonly />

                      <!-- 回复列表（有回复即可折叠，默认折叠） -->
                      <template v-if="cmt.replies.length">
                        <div
                          v-if="expandedReplyIds.has(cmt.id)"
                          class="reply-list"
                        >
                          <div
                            v-for="reply in cmt.replies"
                            :key="reply.id"
                            :id="'comment-' + reply.id"
                            class="reply-item"
                            :class="{ 'comment-flash': flashCommentId === reply.id }"
                          >
                            <a-avatar
                              class="comment-avatar"
                              :size="24"
                              :image-url="reply.createUserAvatar || undefined"
                              :style="
                                reply.createUserAvatar
                                  ? {}
                                  : {
                                      backgroundColor: avatarColor(
                                        commentName(reply)
                                      ),
                                    }
                              "
                              >{{
                                reply.createUserAvatar
                                  ? ''
                                  : avatarText(commentName(reply))
                              }}</a-avatar
                            >
                            <div class="comment-main">
                              <div class="comment-head">
                                <span class="comment-who">{{
                                  commentName(reply)
                                }} {{ $t('qa.testCase.reply') }} {{ commentName(cmt) }}</span>
                                <div
                                  v-if="hasPerm('qa:testcase:update')"
                                  class="comment-actions"
                                >
                                  <a-link @click="startReply(cmt, reply)"
                                    >{{ $t('qa.testCase.reply') }}</a-link
                                  >
                                  <template
                                    v-if="reply.createUserId === currentUserId"
                                  >
                                    <a-link @click="startEditComment(reply)"
                                      >{{ $t('qa.testCase.actionEdit') }}</a-link
                                    >
                                    <a-popconfirm
                                      :content="$t('qa.testCase.deleteReplyConfirm')"
                                      @ok="handleDeleteComment(reply)"
                                    >
                                      <a-link status="danger">{{ $t('qa.common.delete') }}</a-link>
                                    </a-popconfirm>
                                  </template>
                                </div>
                              </div>
                              <div
                                v-if="editingCommentId === reply.id"
                                class="comment-edit-input"
                              >
                                <RichEditor v-model="editingContent" auto-height mention :mention-list="mentionOptions" :placeholder="$t('qa.testCase.editCommentPlaceholder')" @mention-trigger="handleMentionSearch('')" @mention="onMentionSelect" />
                                <div class="edit-actions">
                                  <a-button type="primary" size="small" :loading="commentSaving" @click="saveEditComment(reply)">{{ $t('qa.common.save') }}</a-button>
                                  <a-link @click="editingCommentId = null">{{ $t('qa.common.cancel') }}</a-link>
                                </div>
                              </div>
                              <RichEditor v-else :model-value="reply.content" readonly />
                              <span class="comment-time">{{
                                reply.createTime
                              }}</span>
                            </div>
                          </div>
                        </div>
                        <!-- 折叠/展开开关（有回复即显示） -->
                        <div
                          class="reply-toggle"
                          @click="toggleReplies(cmt.id)"
                        >
                          <template v-if="expandedReplyIds.has(cmt.id)">
                            {{ $t('qa.testCase.collapseReplies') }} <icon-up />
                          </template>
                          <template v-else>
                            {{ $t('qa.testCase.expandReplies', { count: cmt.replies.length }) }}
                            <icon-down />
                          </template>
                        </div>
                      </template>

                      <!-- 行内回复输入框 -->
                      <div v-if="replyParentId === cmt.id" class="reply-input">
                        <RichEditor v-model="replyContent" auto-height mention :mention-list="mentionOptions" :placeholder="$t('qa.testCase.replyTo', { name: replyTargetName })" @mention-trigger="handleMentionSearch('')" @mention="onMentionSelect" />
                        <div class="reply-actions">
                          <a-button type="primary" size="small" :loading="commentSaving" @click="handleSaveReply(cmt)">{{ $t('qa.testCase.reply') }}</a-button>
                          <a-link @click="cancelReply">{{ $t('qa.common.cancel') }}</a-link>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <a-empty v-else :description="$t('qa.testCase.noComments')" class="comment-empty" />
              </div>

            </a-tab-pane>
          </a-tabs>
        </div>
      </a-spin>
    </template>

    <template #footer>
      <div v-if="tabKey === 'comments'" v-permission="'qa:testcase:update'" class="comment-input-bar">
        <a-input v-if="!commentEditorActive" v-model="newComment" :placeholder="$t('qa.testCase.commentPlaceholder')" allow-clear @focus="commentEditorActive = true" />
        <template v-else>
          <RichEditor v-model="newComment" auto-height mention :mention-list="mentionOptions" :placeholder="$t('qa.testCase.commentPlaceholderFull')" @keydown="handleCommentKeydown" @mention-trigger="handleMentionSearch('')" @mention="onMentionSelect" />
          <div class="comment-input-actions">
            <a-button type="primary" size="small" :loading="commentSaving" @click="handleSaveComment">{{ $t('qa.testCase.publish') }}</a-button>
            <a-button size="small" @click="commentEditorActive = false; newComment = ''">{{ $t('qa.common.cancel') }}</a-button>
          </div>
        </template>
      </div>
      <div v-else-if="props.autoEdit" class="dd-footer">
        <a-button @click="cancelEdit">{{ $t('qa.common.cancel') }}</a-button>
        <a-button type="primary" :loading="editSaving" @click="saveEdit">{{ props.confirmText || $t('qa.common.save') }}</a-button>
      </div>
      <div v-else class="dd-footer">
        <a-button @click="close">{{ $t('qa.testCase.close') }}</a-button>
      </div>
    </template>
  </a-drawer>

  <!-- 关联需求选择弹窗：搜索 + 分页列表 + 复选 -->
  <a-modal
    v-model:visible="reqModalVisible"
    :title="$t('qa.testCase.selectRequirements')"
    width="680px"
    :ok-loading="reqModalSaving"
    :body-style="{ minHeight: '450px' }"
    @ok="handleReqModalOk"
  >
    <div class="req-modal-body">
      <a-input-search
        v-model="reqModalKeyword"
        :placeholder="$t('qa.testCase.searchReqPlaceholder')"
        allow-clear
        style="width: 280px; margin-bottom: 12px"
        @search="handleReqModalSearch"
        @clear="handleReqModalSearch"
      />
      <a-table
        :selected-keys="reqModalSelected"
        :data="reqModalList"
        :loading="reqModalLoading"
        :pagination="reqModalPagination"
        :row-selection="{ type: 'checkbox', showCheckedAll: true, onlyCurrent: true }"
        :scroll="{ y: 360 }"
        row-key="id"
        size="small"
        @page-change="handleReqModalPageChange"
        @selection-change="onReqModalSelectionChange"
      >
        <template #columns>
          <a-table-column :title="$t('qa.testCase.colCode')" :width="110">
            <template #cell="{ record }">{{ record.reqCode }}</template>
          </a-table-column>
          <a-table-column
            :title="$t('qa.testCase.reqName')"
            data-index="title"
            :ellipsis="true"
          />
          <a-table-column :title="$t('qa.testCase.status')" :width="90">
            <template #cell="{ record }">
              <a-tag size="small" color="arcoblue">{{
                reqStatusText(record.status)
              }}</a-tag>
            </template>
          </a-table-column>
        </template>
        <template #empty>
          <a-empty :description="$t('qa.testCase.noMatchedReq')" />
        </template>
      </a-table>
    </div>
  </a-modal>

  <!-- 关联自动化选择弹窗：类型切换 + 搜索 + 复选，确定后按差量绑定/解绑 -->
  <a-modal
    v-model:visible="autoModalVisible"
    :title="$t('qa.testCase.bindAuto')"
    width="640px"
    :ok-loading="autoModalSaving"
    :body-style="{ minHeight: '450px' }"
    @ok="handleAutoModalOk"
  >
    <div class="auto-modal-bar">
      <a-radio-group
        v-model="autoModalType"
        type="button"
        size="small"
        @change="handleAutoTypeChange"
      >
        <a-radio value="API_CASE">{{ $t('qa.testCase.apiCase') }}</a-radio>
        <a-radio value="UI_SCENE">{{ $t('qa.testCase.uiScene') }}</a-radio>
      </a-radio-group>
      <a-input-search
        v-model="autoModalKeyword"
        :placeholder="$t('qa.testCase.searchName')"
        allow-clear
        style="width: 240px"
      />
    </div>
    <a-table
      :selected-keys="autoModalSelected"
      :data="filteredAutoOptions"
      :loading="autoModalLoading"
      :pagination="{ pageSize: 10, showTotal: true }"
      :row-selection="{ type: 'checkbox', showCheckedAll: true, onlyCurrent: true }"
      :scroll="{ y: 360 }"
      row-key="id"
      size="small"
      @selection-change="onAutoSelectionChange"
    >
      <template #columns>
        <a-table-column :title="$t('qa.testCase.colName')" data-index="name" :ellipsis="true" />
      </template>
      <template #empty>
        <a-empty
          :description="
            autoModalType === 'API_CASE'
              ? $t('qa.testCase.noApiCase')
              : $t('qa.testCase.noUiScene')
          "
        />
      </template>
    </a-table>
  </a-modal>

  <!-- 关联已有缺陷弹窗：搜索 + 分页 + 复选，确定后差量绑定/解绑 -->
  <a-modal
    v-model:visible="bugBindModalVisible"
    :title="$t('qa.testCase.bindExistingBug')"
    width="680px"
    :ok-loading="bugBindSaving"
    :body-style="{ minHeight: '450px' }"
    @ok="handleBugBindOk"
  >
    <a-input-search
      v-model="bugBindKeyword"
      :placeholder="$t('qa.testCase.searchBugPlaceholder')"
      allow-clear
      style="width: 280px; margin-bottom: 12px"
      @search="handleBugBindSearch"
      @clear="handleBugBindSearch"
    />
    <a-table
      :selected-keys="bugBindSelected"
      :data="bugBindList"
      :loading="bugBindLoading"
      :pagination="bugBindPagination"
      :row-selection="{ type: 'checkbox', showCheckedAll: true, onlyCurrent: true }"
      :scroll="{ y: 360 }"
      row-key="id"
      size="small"
      @page-change="handleBugBindPageChange"
      @selection-change="onBugBindSelectionChange"
    >
      <template #columns>
        <a-table-column :title="$t('qa.testCase.colCode')" :width="110">
          <template #cell="{ record }">{{ record.bugCode }}</template>
        </a-table-column>
        <a-table-column :title="$t('qa.bug.name')" data-index="title" :ellipsis="true" />
        <a-table-column :title="$t('qa.testCase.bindStatus')" :width="110">
          <template #cell="{ record }">
            <a-tag
              v-if="record.testCaseId && record.testCaseId !== props.caseId"
              size="small"
              color="orange"
              >{{ $t('qa.testCase.bugLinkedOther') }}</a-tag
            >
            <a-tag v-else-if="record.testCaseId" size="small" color="green"
              >{{ $t('qa.testCase.bugLinkedThis') }}</a-tag
            >
            <span v-else style="color: #86909c">{{ $t('qa.testCase.bugNotLinked') }}</span>
          </template>
        </a-table-column>
      </template>
      <template #empty>
        <a-empty :description="$t('qa.testCase.noMatchedBug')" />
      </template>
    </a-table>
    <div class="bug-bind-tip"
      >{{ $t('qa.testCase.bugBindTip') }}</div
    >
  </a-modal>

  <!-- 新增缺陷抽屉（复用 BUG 表单，创建后自动关联当前用例） -->
  <a-drawer
    :visible="newBugVisible"
    :width="newBugFullScreen ? '100%' : 920"
    :footer="true"
    :mask-closable="true"
    :body-style="{ padding: '0', height: '100%', overflow: 'hidden' }"
    @cancel="newBugVisible = false"
  >
    <template #title>
      <div class="drawer-title-wrap">
        <span>{{ $t('qa.testCase.newBug') }}</span>
        <a-button type="text" size="small" class="dd-fullscreen-btn" @click="newBugFullScreen = !newBugFullScreen">
          <template #icon>
            <icon-fullscreen v-if="!newBugFullScreen" />
            <icon-fullscreen-exit v-else />
          </template>
        </a-button>
      </div>
    </template>
    <div class="new-bug-body">
      <BugForm
        v-if="newBugVisible"
        ref="newBugFormRef"
        :initial-data="{
          projectId: projectStore.getProjectId,
        }"
      />
    </div>
    <template #footer>
      <a-space>
        <a-button @click="newBugVisible = false">{{ $t('qa.common.cancel') }}</a-button>
        <a-button :loading="newBugSavingContinue" @click="handleNewBugSaveContinue">{{ $t('qa.testCase.saveAndContinue') }}</a-button>
        <a-button type="primary" :loading="newBugSaving" @click="handleNewBugSave">{{ $t('qa.common.save') }}</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
  import { computed, ref, watch, nextTick } from 'vue';
  import { useRouter } from 'vue-router';
  import { useI18n } from 'vue-i18n';
  import { Message } from '@arco-design/web-vue';
  import { useProjectStore, useUserStore } from '@/store';
  import { usePermissionStore } from '@/store';
  import useProjectConfigStore from '@/store/modules/projectConfig';
  import { sanitizeHtml } from '@/utils/sanitize';
  import { getUserListByPage } from '@/api/MyApi/user';
  import TestCaseForm from './TestCaseForm.vue';
  import RichEditor from '@/components/rich-editor/index.vue';
  import BugForm from '@/views/qa/bug/components/BugForm.vue';
  import { confirmIfDirty } from '@/hooks/useDirtyGuard';
  import {
    getTestCaseDetail,
    getRequirementDetail,
    getRequirementList,
    getBindAutoList,
    getAutoOptions,
    bindAuto,
    unbindAuto,
    getBugList,
    getBugListByPlanCase,
    bindBugCase,
    bindBugCases,
    unbindBugCase,
    getTestCaseOperationLogList,
    getCaseReviewHistory,
    getTestCaseCommentList,
    saveTestCaseComment,
    updateTestCaseComment,
    deleteTestCaseComment,
    updateTestCase,
  } from '@/api/MyApi/qa';

  const props = defineProps<{
    visible: boolean;
    caseId: number | null;
    drawerTitle?: string;
    confirmText?: string;
    autoEdit?: boolean;
    activeTab?: string;
    highlightCommentId?: number;
  }>();
  const emit = defineEmits(['update:visible', 'refresh']);

  const router = useRouter();
  const { t } = useI18n();
  const projectStore = useProjectStore();
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();
  const projectConfigStore = useProjectConfigStore();
  const hasPerm = (code: string) => permissionStore.hasPermission(code);
  const fieldVis = (key: string) =>
    projectConfigStore.isFieldVisible('testCase', key);
  const currentUserId = computed(() => userStore.userInfo?.id);

  // ===== 详情 =====
  const detail = ref<any>({});
  const detailLoading = ref(false);
  const tabKey = ref('info');

  const detailSteps = computed(() => {
    const steps = detail.value?.testSteps;
    if (Array.isArray(steps)) return steps;
    if (typeof steps === 'string') {
      try {
        const parsed = JSON.parse(steps);
        return Array.isArray(parsed) ? parsed : [];
      } catch {
        return [];
      }
    }
    return [];
  });

  // 底部元信息：编号/等级在抽屉头部已展示，这里只放头部没有的非编辑项
  // 前置条件是否有有效内容（纯空段落视为无）
  const hasPreCondition = computed(() => {
    const c = detail.value?.preCondition;
    if (!c) return false;
    const text = String(c)
      .replace(/<[^>]*>/g, '')
      .replace(/&nbsp;/g, '')
      .trim();
    return text.length > 0 || /<img/i.test(String(c));
  });

  const infoItems = computed(() => [
    ...(fieldVis('moduleId')
      ? [{ label: t('qa.testCase.module'), value: detail.value.moduleName || '-' }]
      : []),
    { label: t('qa.testCase.createUser'), value: detail.value.createUserName || '-' },
    { label: t('qa.testCase.updateUser'), value: detail.value.updateUserName || '-' },
    { label: t('qa.testCase.createTime'), value: detail.value.createTime || '-' },
    { label: t('qa.testCase.updateTime'), value: detail.value.updateTime || '-' },
  ]);

  // ===== 头部双击编辑（名称/等级/评审状态；编号不可改） =====
  const canUpdateCase = computed(() => hasPerm('qa:testcase:update'));
  const headerEditField = ref<string | null>(null);
  const headerEditDraft = ref<any>('');
  const headerSaving = ref(false);

  const startHeaderEdit = (field: string) => {
    if (!canUpdateCase.value || headerSaving.value || !detail.value?.id) return;
    headerEditDraft.value = detail.value[field];
    headerEditField.value = field;
  };

  // 下拉弹层关闭时延迟退出编辑态，避免 change 事件晚于关闭事件到达导致保存被跳过
  const onHeaderPopupClose = (visible: boolean) => {
    if (visible) return;
    setTimeout(() => {
      if (!headerSaving.value) headerEditField.value = null;
    }, 150);
  };

  const commitHeaderEdit = async (field: string) => {
    if (headerEditField.value !== field) return;
    let value = headerEditDraft.value;
    if (field === 'caseName') {
      value = (value || '').trim();
      if (!value || value === detail.value.caseName) {
        headerEditField.value = null;
        return;
      }
    } else if (value === detail.value[field]) {
      headerEditField.value = null;
      return;
    }
    headerEditField.value = null;
    headerSaving.value = true;
    try {
      const res: any = await updateTestCase({
        id: detail.value.id,
        projectId: projectStore.getProjectId,
        [field]: value,
      });
      if (res.code === 200) {
        detail.value[field] = value;
        Message.success(t('qa.testCase.saved'));
        operationLogLoaded.value = false;
        emit('refresh');
      } else {
        Message.error(res.msg || t('qa.testCase.saveFailed'));
      }
    } catch (e) {
      console.error(e);
    } finally {
      headerSaving.value = false;
    }
  };

  // ===== 详情 tab 内嵌编辑（slim 表单：只编辑名称/前置条件/步骤） =====
  const editing = ref(false);
  const editSaving = ref(false);
  const editFormRef = ref<any>(null);
  const fullScreen = ref(false);

  const startEdit = () => {
    editing.value = true;
  };

  const cancelEdit = () => {
    confirmIfDirty(!!editFormRef.value?.isDirty?.(), () => { editing.value = false; }, t('qa.testCase.unsavedCancelConfirm'));
  };

  const saveEdit = async () => {
    editSaving.value = true;
    try {
      const ok = await editFormRef.value?.save();
      if (ok) {
        editing.value = false;
        if (props.autoEdit) {
          // 纯编辑模式：更新成功后关闭抽屉，由父组件刷新列表和内容
          emit('refresh');
          close();
          return;
        }
        if (props.caseId) loadDetail(props.caseId);
        emit('refresh');
      }
    } finally {
      editSaving.value = false;
    }
  };

  const handleSaved = () => {
    // TestCaseForm 内部已提示保存成功，这里无需额外处理（saveEdit 中已刷新）
  };

  // ===== 各 tab 懒加载状态 =====
  const requirementList = ref<any[]>([]);
  const reqLoaded = ref(false);

  // 当前用例关联的需求ID数组（新字段 requirement_ids 优先，旧数据回退 requirement_id）
  const caseReqIds = computed<number[]>(() => {
    const str = detail.value?.requirementIds;
    if (str)
      return String(str)
        .split(',')
        .map((s: string) => Number(s.trim()))
        .filter((n: number) => n > 0);
    return detail.value?.requirementId ? [detail.value.requirementId] : [];
  });

  const bindList = ref<any[]>([]);
  const relLoading = ref(false);
  const relLoaded = ref(false);
  const relType = ref('ALL');
  const filteredBindList = computed(() =>
    relType.value === 'ALL'
      ? bindList.value
      : bindList.value.filter((b) => b.autoType === relType.value)
  );

  const bugList = ref<any[]>([]);
  const planBugList = ref<any[]>([]); // 测试计划来源缺陷（只读展示）
  const bugTabMode = ref<'direct' | 'plan'>('direct');
  const bugLoading = ref(false);
  const bugLoaded = ref(false);

  const operationLogList = ref<any[]>([]);
  const operationLogLoading = ref(false);
  const operationLogLoaded = ref(false);

  const reviewHistory = ref<any[]>([]);
  const reviewLoaded = ref(false);

  const commentList = ref<any[]>([]);
  const commentLoaded = ref(false);
  const newComment = ref('');
  const commentSaving = ref(false);
  const commentEditorActive = ref(false);

  // ===== 评论：二级回复 + 输入 @ 提及成员 =====
  // 提及下拉选项（label/value 均为昵称，选中后文本插入 @昵称）+ 昵称→用户ID映射（提交时解析）
  const mentionOptions = ref<{ label: string; value: string }[]>([]);
  const mentionNameIdMap = ref<Record<string, number>>({});
  const replyParentId = ref<number | null>(null);
  const replyContent = ref('');
  const replyTarget = ref<any>(null);
  // 正在编辑的评论（仅本人评论显示编辑入口）
  const editingCommentId = ref<number | null>(null);
  const editingContent = ref('');

  // 输入 @ 后按关键词拉取项目成员
  const handleMentionSearch = async (text: string) => {
    try {
      const res: any = await getUserListByPage({
        projectId: projectStore.getProjectId ?? undefined,
        username: text || undefined,
        pageNum: 1,
        pageSize: 20,
      });
      const users = res.data?.records || res.data || [];
      users.forEach((u: any) => {
        const name = u.nickname || u.username;
        if (name && u.id != null) mentionNameIdMap.value[name] = u.id;
      });
      mentionOptions.value = users
        .map((u: any) => ({
          label: u.nickname || u.username,
          value: u.nickname || u.username,
        }))
        .filter((o: any) => o.value);
    } catch (e) {
      console.error(e);
    }
  };

  // 提交时从评论文本解析 @昵称 → 用户ID（后端据此发站内信）
  const extractMentionIds = (content: string): number[] => {
    const ids: number[] = [];
    Object.entries(mentionNameIdMap.value).forEach(([name, id]) => {
      if (content.includes(`@${name}`) && !ids.includes(id)) ids.push(id);
    });
    return ids;
  };

  // @提及下拉选中回调（mentionNameIdMap 在 handleMentionSearch 时已填充）
  const onMentionSelect = (_value: string) => {};

  // 富文本空内容检测（wangEditor 空内容返回 <p><br></p>）
  const isEmptyRichContent = (html: string) => {
    if (!html || !html.trim()) return true;
    const text = html.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();
    return !text;
  };

  // Ctrl+Enter 发表（Enter 留给换行和提及下拉的选项确认）
  const handleCommentKeydown = (e: KeyboardEvent) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
      e.preventDefault();
      handleSaveComment();
    }
  };

  const startEditComment = (cmt: any) => {
    editingCommentId.value = cmt.id;
    editingContent.value = cmt.content;
  };

  const saveEditComment = async (cmt: any) => {
    if (isEmptyRichContent(editingContent.value)) {
      Message.warning(t('qa.testCase.commentEmpty'));
      return;
    }
    const content = editingContent.value.trim();
    if (content === cmt.content) {
      editingCommentId.value = null;
      return;
    }
    commentSaving.value = true;
    try {
      const res: any = await updateTestCaseComment({ id: cmt.id, content });
      if (res.code === 200) {
        Message.success(t('qa.testCase.updated'));
        editingCommentId.value = null;
        loadComments(props.caseId as number);
      } else {
        Message.error(res.msg || t('qa.testCase.updateFailed'));
      }
    } catch (e) {
      console.error(e);
    } finally {
      commentSaving.value = false;
    }
  };

  // ===== 评论头像：昵称首字符 + 按名字哈希固定配色 =====
  const AVATAR_COLORS = [
    '#165dff',
    '#00b42a',
    '#ff7d00',
    '#f53f3f',
    '#722ed1',
    '#0fc6c2',
    '#eb2f96',
    '#86909c',
  ];
  const commentName = (cmt: any) =>
    cmt.createUserName || t('qa.testCase.userPrefix') + cmt.createUserId;
  const avatarText = (name: string) =>
    (name || '?').trim().charAt(0).toUpperCase();
  const avatarColor = (name: string) => {
    const s = name || '?';
    let h = 0;
    for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
    return AVATAR_COLORS[h % AVATAR_COLORS.length];
  };

  // 平铺列表组装成二级树：顶层按时间倒序，回复按时间正序
  const commentTree = computed(() => {
    const tops = commentList.value.filter((c: any) => !c.parentId);
    return tops.map((c: any) => ({
      ...c,
      replies: commentList.value
        .filter((r: any) => r.parentId === c.id)
        .slice()
        .sort((a: any, b: any) =>
          String(a.createTime).localeCompare(String(b.createTime))
        ),
    }));
  });

  // 回复折叠：有回复即可折叠，默认折叠；展开状态按顶层评论 id 记录
  const expandedReplyIds = ref<Set<number>>(new Set());
  const flashCommentId = ref<number | null>(null);

  const toggleReplies = (commentId: number) => {
    const next = new Set(expandedReplyIds.value);
    if (next.has(commentId)) {
      next.delete(commentId);
    } else {
      next.add(commentId);
    }
    expandedReplyIds.value = next;
  };

  const replyTargetName = computed(() =>
    replyTarget.value
      ? replyTarget.value.createUserName ||
        t('qa.testCase.userPrefix') + replyTarget.value.createUserId
      : ''
  );

  // 回复：parent 始终传顶层评论 id（后端只允许两级）；回复某条回复时预填 @对方昵称
  const startReply = (top: any, reply: any | null) => {
    replyParentId.value = top.id;
    replyTarget.value = reply || top;
    replyContent.value = reply ? `<p>@${replyTargetName.value} </p>` : '';
    // 被回复人记入 昵称→ID 映射，提交时解析为提及（回复通知走提及通道）
    const targetUserId = replyTarget.value?.createUserId;
    if (targetUserId && replyTargetName.value) {
      mentionNameIdMap.value[replyTargetName.value] = targetUserId;
    }
  };

  const cancelReply = () => {
    replyParentId.value = null;
    replyTarget.value = null;
    replyContent.value = '';
  };

  const handleSaveReply = async (top: any) => {
    if (isEmptyRichContent(replyContent.value)) {
      Message.warning(t('qa.testCase.replyEmpty'));
      return;
    }
    const ok = await doSaveComment(replyContent.value.trim(), top.id);
    if (ok) {
      // 发表回复后自动展开该楼，保证新回复可见
      if (!expandedReplyIds.value.has(top.id)) {
        expandedReplyIds.value = new Set([...expandedReplyIds.value, top.id]);
      }
      cancelReply();
    }
  };

  const lastReviewRejected = computed(
    () =>
      detail.value?.status === 'DRAFT' &&
      operationLogList.value.length > 0 &&
      operationLogList.value[0]?.operation === 'REVIEW_REJECT'
  );

  // ===== 操作日志：按「一次操作」聚合 =====
  // 评审类记录（投票/定结）移出本 tab，统一在「评审记录」展示
  const NON_REVIEW_OPS = new Set([
    'CREATE',
    'UPDATE',
    'TRANSITION',
    'DELETE',
    'VERSION',
  ]);
  const expandedLogGroups = ref<Set<number>>(new Set());
  const groupedOperationLogs = computed(() => {
    const groups: any[] = [];
    for (const log of operationLogList.value) {
      if (!NON_REVIEW_OPS.has(log.operation)) continue;
      const prev = groups[groups.length - 1];
      const mergeable =
        prev &&
        prev.operation === 'UPDATE' &&
        log.operation === 'UPDATE' &&
        prev.operatorId === log.operatorId &&
        Math.abs(
          new Date(prev.operateTime).getTime() -
            new Date(log.operateTime).getTime()
        ) <= 60000;
      if (mergeable) {
        prev.changes.push(log);
      } else {
        groups.push({ ...log, changes: [log] });
      }
    }
    return groups;
  });
  const toggleLogGroup = (id: number) => {
    const next = new Set(expandedLogGroups.value);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    expandedLogGroups.value = next;
  };

  // ===== 打开/关闭 =====
  watch(
    () => props.visible,
    (v) => {
      if (v && props.caseId) {
        resetState();
        loadDetail(props.caseId);
        // 消息跳转指定 tab 时，主动加载对应数据
        if (props.activeTab === 'comments') {
          loadComments(props.caseId);
        }
      }
    }
  );

  // 评论高亮闪烁：消息跳转时定位并闪烁指定评论
  watch(
    () => props.highlightCommentId,
    (id) => {
      if (!id) return;
      tabKey.value = 'comments';
      // 等待评论列表加载完成
      nextTick(() => {
        setTimeout(() => {
          // 如果目标评论是回复，找到其父评论并展开回复列表
          const parent = commentTree.value.find(
            (c: any) => c.replies?.some((r: any) => r.id === id)
          );
          if (parent && !expandedReplyIds.value.has(parent.id)) {
            expandedReplyIds.value = new Set([
              ...expandedReplyIds.value,
              parent.id,
            ]);
          }
          // 触发闪烁
          flashCommentId.value = id;
          setTimeout(() => {
            flashCommentId.value = null;
          }, 3000);
          // 滚动到评论位置
          nextTick(() => {
            const el = document.getElementById('comment-' + id);
            if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
          });
        }, 500);
      });
    },
    { immediate: true }
  );

  const resetState = () => {
    tabKey.value = props.activeTab || 'info';
    editing.value = false;
    requirementList.value = [];
    reqLoaded.value = false;
    bindList.value = [];
    relLoaded.value = false;
    relType.value = 'ALL';
    bugList.value = [];
    planBugList.value = [];
    bugLoaded.value = false;
    operationLogList.value = [];
    operationLogLoaded.value = false;
    reviewHistory.value = [];
    reviewLoaded.value = false;
    commentList.value = [];
    commentLoaded.value = false;
    newComment.value = '';
    flashCommentId.value = null;
  };

  const close = () => emit('update:visible', false);

  const loadDetail = async (id: number) => {
    detailLoading.value = true;
    try {
      const res: any = await getTestCaseDetail(id);
      if (res.code === 200 && res.data) {
        detail.value = res.data;
        if (props.autoEdit) editing.value = true;
      }
    } catch (e) {
      console.error(e);
    } finally {
      detailLoading.value = false;
    }
    // 预加载各 tab 数据，使数量徽标立即可见（后台并行，不阻塞详情展示）
    loadRequirement();
    loadBindList(id);
    loadBugs(id);
    loadOperationLogs(id);
    loadReviewHistory(id);
    loadComments(id);
  };

  // ===== tab 懒加载 =====
  const handleTabChange = (key: string | number) => {
    // 编辑中切走 tab 时退出编辑态（有未保存修改时先确认）
    if (key !== 'info' && editing.value) {
      cancelEdit();
    }
    const id = props.caseId;
    if (!id) return;
    if (key === 'req' && !reqLoaded.value) loadRequirement();
    if (key === 'rel' && !relLoaded.value) loadBindList(id);
    if (key === 'bug' && !bugLoaded.value) loadBugs(id);
    if (key === 'logs' && !operationLogLoaded.value) loadOperationLogs(id);
    if (key === 'review' && !reviewLoaded.value) loadReviewHistory(id);
    if (key === 'comments' && !commentLoaded.value) loadComments(id);
  };

  // ===== 关联需求：弹窗列表选择（搜索 + 分页 + 复选，确定后保存） =====
  const reqModalVisible = ref(false);
  const reqModalSaving = ref(false);
  const reqModalLoading = ref(false);
  const reqModalKeyword = ref('');
  const reqModalList = ref<any[]>([]);
  const reqModalSelected = ref<number[]>([]);
    const onReqModalSelectionChange = (keys: (string | number)[]) => {
      reqModalSelected.value = keys as number[];
    };
  const reqModalPagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: true,
  });

  const openReqModal = () => {
    reqModalSelected.value = [...caseReqIds.value];
    reqModalKeyword.value = '';
    reqModalPagination.value.current = 1;
    reqModalVisible.value = true;
    loadReqModalList();
  };

  const loadReqModalList = async () => {
    if (!projectStore.getProjectId) return;
    reqModalLoading.value = true;
    try {
      const res: any = await getRequirementList(
        projectStore.getProjectId,
        reqModalKeyword.value || undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        reqModalPagination.value.current,
        reqModalPagination.value.pageSize
      );
      reqModalList.value = res.data?.records || [];
      reqModalPagination.value.total = res.data?.total || 0;
    } catch (e) {
      console.error(e);
    } finally {
      reqModalLoading.value = false;
    }
  };

  const handleReqModalSearch = () => {
    reqModalPagination.value.current = 1;
    loadReqModalList();
  };

  const handleReqModalPageChange = (current: number) => {
    reqModalPagination.value.current = current;
    loadReqModalList();
  };

  const handleReqModalOk = async () => {
    reqModalSaving.value = true;
    try {
      await handleBindRequirement(reqModalSelected.value);
    } finally {
      reqModalSaving.value = false;
    }
  };

  const handleBindRequirement = async (ids: number[]) => {
    const joined = ids.join(',');
    const current = caseReqIds.value.join(',');
    if (joined === current) return;
    try {
      const res: any = await updateTestCase({
        id: detail.value.id,
        projectId: projectStore.getProjectId,
        requirementIds: joined,
      });
      if (res.code === 200) {
        Message.success(t('qa.testCase.reqUpdated'));
        detail.value.requirementIds = joined;
        detail.value.requirementId = ids[0] ?? null;
        requirementList.value = [];
        if (ids.length) loadRequirement();
        emit('refresh');
      } else {
        Message.error(res.msg || t('qa.testCase.operationFailed'));
      }
    } catch (e) {
      console.error(e);
    }
  };

  // ===== 关联自动化：弹窗选择（类型切换 + 搜索 + 复选，确定后差量绑定/解绑） =====
  const autoModalVisible = ref(false);
  const autoModalSaving = ref(false);
  const autoModalLoading = ref(false);
  const autoModalType = ref<'API_CASE' | 'UI_SCENE'>('API_CASE');
  const autoModalKeyword = ref('');
  const autoModalOptions = ref<any[]>([]);
  const autoModalSelected = ref<number[]>([]);
    const onAutoSelectionChange = (keys: (string | number)[]) => {
      autoModalSelected.value = keys as number[];
    };

  const filteredAutoOptions = computed(() => {
    const kw = autoModalKeyword.value.trim().toLowerCase();
    if (!kw) return autoModalOptions.value;
    return autoModalOptions.value.filter((o: any) =>
      (o.name || '').toLowerCase().includes(kw)
    );
  });

  // 当前类型下已绑定的自动化ID
  const boundAutoIds = (type: string) =>
    bindList.value
      .filter((b: any) => b.autoType === type)
      .map((b: any) => b.autoId);

  const loadAutoModalOptions = async () => {
    autoModalLoading.value = true;
    try {
      const res: any = await getAutoOptions(
        autoModalType.value,
        projectStore.getProjectId
      );
      autoModalOptions.value = res.data || [];
      autoModalSelected.value = boundAutoIds(autoModalType.value);
    } catch (e) {
      console.error(e);
    } finally {
      autoModalLoading.value = false;
    }
  };

  const openAutoModal = () => {
    autoModalType.value =
      relType.value === 'UI_SCENE' ? 'UI_SCENE' : 'API_CASE';
    autoModalKeyword.value = '';
    autoModalVisible.value = true;
    loadAutoModalOptions();
  };

  const handleAutoTypeChange = () => {
    autoModalKeyword.value = '';
    loadAutoModalOptions();
  };

  const handleAutoModalOk = async () => {
    if (!props.caseId) return;
    autoModalSaving.value = true;
    try {
      const type = autoModalType.value;
      const before = bindList.value.filter((b: any) => b.autoType === type);
      const beforeIds = before.map((b: any) => b.autoId);
      const afterIds = autoModalSelected.value;
      const toBind = afterIds.filter((id) => !beforeIds.includes(id));
      const toUnbind = before.filter((b: any) => !afterIds.includes(b.autoId));
      const tasks: Promise<any>[] = [
        ...toBind.map((id) => bindAuto(props.caseId as number, type, id)),
        ...toUnbind.map((b: any) => unbindAuto(b.id)),
      ];
      if (tasks.length) {
        await Promise.all(tasks);
        Message.success(t('qa.testCase.autoUpdated'));
        relLoaded.value = false;
        await loadBindList(props.caseId);
        emit('refresh');
      }
    } catch (e) {
      console.error(e);
      Message.error(t('qa.testCase.saveRetryFailed'));
    } finally {
      autoModalSaving.value = false;
    }
  };

  // ===== 关联缺陷：关联已有 / 新增 / 解绑 =====
  const bugBindModalVisible = ref(false);
  const bugBindSaving = ref(false);
  const bugBindLoading = ref(false);
  const bugBindKeyword = ref('');
  const bugBindList = ref<any[]>([]);
  const bugBindSelected = ref<number[]>([]);
    const onBugBindSelectionChange = (keys: (string | number)[]) => {
      bugBindSelected.value = keys as number[];
    };
  const bugBindPagination = ref({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: true,
  });

  const openBugBindModal = () => {
    bugBindSelected.value = bugList.value.map((b: any) => b.id);
    bugBindKeyword.value = '';
    bugBindPagination.value.current = 1;
    bugBindModalVisible.value = true;
    loadBugBindList();
  };

  const loadBugBindList = async () => {
    if (!projectStore.getProjectId) return;
    bugBindLoading.value = true;
    try {
      const res: any = await getBugList(
        projectStore.getProjectId,
        bugBindKeyword.value || undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        bugBindPagination.value.current,
        bugBindPagination.value.pageSize
      );
      bugBindList.value = res.data?.records || [];
      bugBindPagination.value.total = res.data?.total || 0;
    } catch (e) {
      console.error(e);
    } finally {
      bugBindLoading.value = false;
    }
  };

  const handleBugBindSearch = () => {
    bugBindPagination.value.current = 1;
    loadBugBindList();
  };

  const handleBugBindPageChange = (current: number) => {
    bugBindPagination.value.current = current;
    loadBugBindList();
  };

  const handleBugBindOk = async () => {
    if (!props.caseId) return;
    bugBindSaving.value = true;
    try {
      const beforeIds = bugList.value.map((b: any) => b.id);
      const afterIds = bugBindSelected.value;
      const toBind = afterIds.filter((id) => !beforeIds.includes(id));
      const toUnbind = beforeIds.filter((id) => !afterIds.includes(id));
      const tasks: Promise<any>[] = [
        ...toBind.map((id) => bindBugCase(id, props.caseId)),
        ...toUnbind.map((id) => unbindBugCase(id, props.caseId as number)),
      ];
      if (tasks.length) {
        await Promise.all(tasks);
        Message.success(t('qa.testCase.bugUpdated'));
        bugLoaded.value = false;
        await loadBugs(props.caseId);
        emit('refresh');
      }
    } catch (e) {
      console.error(e);
      Message.error(t('qa.testCase.saveRetryFailed'));
    } finally {
      bugBindSaving.value = false;
    }
  };

  const handleUnbindBug = async (record: any) => {
    if (!props.caseId) return;
    try {
      const res: any = await unbindBugCase(record.id, props.caseId as number);
      if (res.code === 200) {
        Message.success(t('qa.testCase.unbound'));
        bugLoaded.value = false;
        if (props.caseId) await loadBugs(props.caseId);
        emit('refresh');
      } else {
        Message.error(res.msg || t('qa.testCase.unbindFailed'));
      }
    } catch (e) {
      console.error(e);
    }
  };

  // ===== 新增缺陷（复用 BugForm，自动带入当前用例） =====
  const newBugVisible = ref(false);
  const newBugFullScreen = ref(false);
  const newBugSaving = ref(false);
  const newBugSavingContinue = ref(false);
  const newBugFormRef = ref<any>(null);

  const openNewBugModal = () => {
    newBugVisible.value = true;
  };

  // 保存后公共逻辑：关联用例 + 刷新列表
  const afterBugSaved = async () => {
    if (props.caseId) {
      const formData = newBugFormRef.value?.formData;
      if (formData?.id) {
        await bindBugCases(formData.id, [props.caseId as number]);
      }
    }
    bugLoaded.value = false;
    if (props.caseId) await loadBugs(props.caseId);
    emit('refresh');
  };

  // 保存：保存后关闭抽屉
  const handleNewBugSave = async () => {
    newBugSaving.value = true;
    try {
      const ok = await newBugFormRef.value?.save();
      if (ok) {
        await afterBugSaved();
        newBugVisible.value = false;
      }
    } catch (e) {
      console.error(e);
    } finally {
      newBugSaving.value = false;
    }
  };

  // 保存并继续创建：保存后重置表单，不关闭抽屉
  const handleNewBugSaveContinue = async () => {
    newBugSavingContinue.value = true;
    try {
      const ok = await newBugFormRef.value?.save();
      if (ok) {
        await afterBugSaved();
        await newBugFormRef.value?.resetForm();
      }
    } catch (e) {
      console.error(e);
    } finally {
      newBugSavingContinue.value = false;
    }
  };

  const loadRequirement = async () => {
    reqLoaded.value = true;
    const ids = caseReqIds.value;
    if (!ids.length) return;
    try {
      const results = await Promise.all(
        ids.map((id) => getRequirementDetail(id))
      );
      requirementList.value = results
        .map((res: any) => (res.code === 200 ? res.data : null))
        .filter(Boolean);
    } catch (e) {
      console.error(e);
    }
  };

  const loadBindList = async (id: number) => {
    relLoading.value = true;
    try {
      const res: any = await getBindAutoList(id);
      if (res.code === 200) {
        bindList.value = res.data || [];
        relLoaded.value = true;
      }
    } catch (e) {
      console.error(e);
    } finally {
      relLoading.value = false;
    }
  };

  const loadBugs = async (id: number) => {
    if (!projectStore.getProjectId) return;
    bugLoading.value = true;
    try {
      const res: any = await getBugList(
        projectStore.getProjectId,
        undefined,
        undefined,
        undefined,
        undefined,
        undefined,
        id, // testCaseId
        undefined,
        undefined,
        undefined,
        undefined,
        1,
        100
      );
      bugList.value = res.data?.records || res.data || [];
      bugLoaded.value = true;
      // 测试计划来源的缺陷（plan_case_id 维度，与直接关联分开展示）
      const planRes: any = await getBugListByPlanCase(id);
      planBugList.value = planRes.data || [];
    } catch (e) {
      console.error(e);
    } finally {
      bugLoading.value = false;
    }
  };

  const loadOperationLogs = async (id: number) => {
    operationLogLoading.value = true;
    try {
      const res: any = await getTestCaseOperationLogList(id);
      if (res.code === 200) {
        operationLogList.value = res.data || [];
        operationLogLoaded.value = true;
      }
    } catch (e) {
      console.error(e);
    } finally {
      operationLogLoading.value = false;
    }
  };

  const loadComments = async (id: number) => {
    try {
      const res: any = await getTestCaseCommentList(id);
      if (res.code === 200) {
        commentList.value = res.data || [];
        commentLoaded.value = true;
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleSaveComment = async () => {
    if (isEmptyRichContent(newComment.value)) {
      Message.warning(t('qa.testCase.commentEmpty'));
      return;
    }
    const ok = await doSaveComment(newComment.value.trim(), 0);
    if (ok) {
      newComment.value = '';
      commentEditorActive.value = false;
    }
  };

  // 统一保存入口：parentId=0 顶层评论，>0 为对该顶层评论的回复；@昵称 解析为提及人发站内信
  const doSaveComment = async (content: string, parentId: number) => {
    commentSaving.value = true;
    try {
      const mentionIds = extractMentionIds(content);
      const res: any = await saveTestCaseComment({
        testCaseId: props.caseId,
        content,
        parentId,
        mentionUserIds: mentionIds.length
          ? JSON.stringify(mentionIds)
          : undefined,
      });
      if (res.code === 200) {
        Message.success(parentId ? t('qa.testCase.replySuccess') : t('qa.testCase.commentSuccess'));
        loadComments(props.caseId as number);
        return true;
      }
      Message.error(res.msg || t('qa.testCase.commentFailed'));
      return false;
    } catch (e) {
      console.error(e);
      return false;
    } finally {
      commentSaving.value = false;
    }
  };

  const handleDeleteComment = async (cmt: any) => {
    try {
      const res: any = await deleteTestCaseComment(cmt.id);
      if (res.code === 200) {
        Message.success(t('qa.testCase.deleted'));
        loadComments(props.caseId as number);
      }
    } catch (e) {
      console.error(e);
    }
  };

  // ===== 评审记录（投票事件流） =====
  const loadReviewHistory = async (id: number) => {
    try {
      const res: any = await getCaseReviewHistory(id);
      if (res.code === 200) {
        reviewHistory.value = res.data || [];
        reviewLoaded.value = true;
      }
    } catch (e) {
      console.error(e);
    }
  };
  const voteConclusionColor = (c: string) =>
    c === 'PASS' ? 'green' : c === 'REJECT' ? 'red' : 'gray';
  const voteConclusionText = (c: string) =>
    c === 'PASS'
      ? t('qa.testCase.vote.pass')
      : c === 'REJECT'
        ? t('qa.testCase.vote.reject')
        : t('qa.testCase.vote.pending');

  // ===== 跳转 =====
  const goRequirement = (record: any) => {
    router.push({ name: 'Requirement', query: { keyword: record.reqCode } });
  };
  const goBug = (record: any) => {
    router.push({ name: 'Bug', query: { keyword: record.bugCode } });
  };

  // ===== 文案/颜色映射 =====
  const renderHtml = (content: string | undefined, defaultText = t('qa.testCase.none')) => {
    if (!content || content.trim() === '') return defaultText;
    if (/<[a-z][\s\S]*?>/i.test(content)) return sanitizeHtml(content);
    return content.replace(/\n/g, '<br>');
  };
  const priorityColor = (p: string) =>
    ({ P0: 'red', P1: 'orange', P2: 'arcoblue' }[p] || 'gray');
  const statusColor = (s: string) =>
    ({ DRAFT: 'gray', REVIEWING: 'cyan', REVIEWED: 'green', DEPRECATED: 'red' }[
      s
    ] || 'gray');
  const statusText = (s: string) =>
    ({
      DRAFT: t('qa.testCase.status.draft'),
      REVIEWING: t('qa.testCase.status.reviewing'),
      REVIEWED: t('qa.testCase.status.reviewed'),
      DEPRECATED: t('qa.testCase.status.deprecated'),
    }[s] || s);
  const reqStatusText = (s: string) =>
    ({
      DRAFT: t('qa.testCase.status.draft'),
      REVIEWING: t('qa.testCase.status.reviewing'),
      CONFIRMED: t('qa.testCase.reqStatus.confirmed'),
      DEVELOPING: t('qa.testCase.reqStatus.developing'),
      TESTING: t('qa.testCase.reqStatus.testing'),
      RELEASED: t('qa.testCase.reqStatus.released'),
      CLOSED: t('qa.testCase.reqStatus.closed'),
    }[s] || s);
  const bugSeverityColor = (s: string) =>
    ({ FATAL: 'red', SERIOUS: 'orangered', NORMAL: 'orange', TIPS: 'gray' }[
      s
    ] || 'gray');
  const bugSeverityText = (s: string) =>
    ({
      FATAL: t('qa.testCase.bugSeverity.fatal'),
      SERIOUS: t('qa.testCase.bugSeverity.serious'),
      NORMAL: t('qa.testCase.bugSeverity.normal'),
      TIPS: t('qa.testCase.bugSeverity.tips'),
    }[s] || s);
  const bugStatusColor = (s: string) =>
    ({
      NEW: 'red',
      CONFIRMED: 'orangered',
      FIXING: 'orange',
      FIXED: 'arcoblue',
      VERIFIED: 'cyan',
      CLOSED: 'green',
      REJECTED: 'gray',
    }[s] || 'gray');
  const bugStatusText = (s: string) =>
    ({
      NEW: t('qa.testCase.bugStatus.new'),
      CONFIRMED: t('qa.testCase.bugStatus.confirmed'),
      FIXING: t('qa.testCase.bugStatus.fixing'),
      FIXED: t('qa.testCase.bugStatus.fixed'),
      VERIFIED: t('qa.testCase.bugStatus.verified'),
      CLOSED: t('qa.testCase.bugStatus.closed'),
      REJECTED: t('qa.testCase.bugStatus.rejected'),
    }[s] || s);
  const operationText = (op: string) =>
    ({
      CREATE: t('qa.testCase.operation.create'),
      UPDATE: t('qa.testCase.operation.update'),
      TRANSITION: t('qa.testCase.operation.transition'),
      DELETE: t('qa.testCase.operation.delete'),
      REVIEW_PASS: t('qa.testCase.operation.reviewPass'),
      REVIEW_REJECT: t('qa.testCase.operation.reviewReject'),
      VERSION: t('qa.testCase.operation.version'),
    }[op] || op);
  const operationColor = (op: string) =>
    ({
      CREATE: 'green',
      UPDATE: 'blue',
      TRANSITION: 'cyan',
      DELETE: 'red',
      REVIEW_PASS: 'green',
      REVIEW_REJECT: 'orange',
      VERSION: 'purple',
    }[op] || 'gray');
  const fieldNameText = (field: string) =>
    ({ status: t('qa.testCase.status') }[field] || field);
  const renderLogValue = (field: string, value: any) => {
    if (value === null || value === undefined || value === '') return '-';
    if (field === 'status') return statusText(String(value));
    return String(value);
  };
</script>

<style scoped>
  /* 抽屉整体布局：body 固定高度不滚，各 tab 内容区自己滚动（tab 导航始终固定） */
  .case-detail-drawer :deep(.arco-drawer-body) {
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
  .case-detail-drawer :deep(.arco-spin),
  .case-detail-drawer :deep(.arco-spin-children) {
    height: 100%;
  }
  .dd-body {
    height: 100%;
    padding: 4px 0 0;
    display: flex;
    flex-direction: column;
  }
  /* 纯编辑模式：表单区滚动，footer 固定底部 */
  .dd-edit-only {
    overflow-y: auto;
    overflow-x: hidden;
  }
  .dd-tabs {
    flex: 1 1 0;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .dd-tabs :deep(.arco-tabs-content) {
    flex: 1 1 0;
    min-height: 0;
  }
  .dd-tabs :deep(.arco-tabs-content-list) {
    height: 100%;
  }
  /* 覆盖 arco 默认的 height:0/height:auto 规则，每个 tab 页撑满并自己滚动 */
  .dd-tabs :deep(.arco-tabs-content-item),
  .dd-tabs :deep(.arco-tabs-content-item.arco-tabs-content-item-active) {
    height: 100%;
    overflow-y: auto;
  }
  /* 自定义抽屉标题 */
  .dd-custom-title {
    font-size: 16px;
    font-weight: 600;
  }
  .dd-title-wrap {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
  }
  .dd-fullscreen-btn {
    margin-left: auto;
    flex-shrink: 0;
    color: var(--color-text-2);
  }
  /* 抽屉标题栏一行：编号 + 名称 + 等级 + 评审状态 */
  .dd-header-line {
    display: flex;
    align-items: center;
    gap: 10px;
    min-height: 28px;
    width: 100%;
    max-width: 100%;
    overflow: hidden;
    padding-right: 32px; /* 给右上角关闭按钮让位 */
  }
  .dd-name {
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text-1);
    min-width: 0;
    /* 硬性上限，避免长名称把抽屉标题栏（乃至整个抽屉）撑宽 */
    max-width: 420px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .dd-name-input {
    flex: 1;
    min-width: 0;
    font-size: 15px;
    font-weight: 600;
  }
  /* 名称超长时优先压缩名称，等级/状态标签保持完整 */
  .dd-header-line :deep(.arco-tag) {
    flex-shrink: 0;
  }
  .dd-code {
    color: var(--color-text-3);
    font-size: 12px;
    flex-shrink: 0;
    padding: 2px 8px;
    background: var(--color-fill-2);
    border-radius: 4px;
  }
  .dd-header-line .editable {
    cursor: pointer;
  }
  .dd-header-line .editable:hover {
    background: var(--color-fill-2);
    border-radius: 3px;
  }
  .dd-tabs {
    margin-top: 8px;
  }
  .info-toolbar {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
  }
  .editing-tip {
    margin-right: auto;
    color: rgb(var(--orange-6));
    font-size: 12px;
  }
  .tab-count {
    color: var(--color-text-3);
    font-size: 12px;
  }
  /* 查看态：去掉灰底卡片，用分割线 + 左侧竖条小标题分区 */
  .dd-section-title {
    font-weight: 600;
    margin: 4px 0 10px;
    padding-left: 8px;
    border-left: 3px solid rgb(var(--primary-6));
    line-height: 1.2;
  }
  .pre-condition-view {
    margin: 0 0 16px;
  }
  .pre-condition-empty {
    margin: 0 0 16px;
    color: var(--color-text-3);
    font-size: 13px;
  }
  .step-no {
    color: rgb(var(--primary-6));
    font-weight: 600;
    font-size: 12px;
  }
  /* 底部元信息：单列平铺、淡灰色，弱化非编辑项存在感 */
  .meta-footer {
    display: flex;
    flex-direction: column;
    gap: 6px;
    margin-top: 4px;
  }
  .meta-footer-item {
    display: flex;
    align-items: baseline;
    gap: 8px;
    font-size: 12px;
    color: var(--color-text-3);
  }
  .meta-footer-label {
    flex-shrink: 0;
    width: 60px;
  }
  .meta-footer-value {
    color: var(--color-text-3);
  }
  .tab-toolbar {
    margin-bottom: 10px;
  }
  .req-bind-bar {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .req-bind-tip {
    color: var(--color-text-3);
    font-size: 12px;
  }
  .rel-bind-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .auto-modal-bar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }
  .bug-bind-tip {
    margin-top: 8px;
    color: var(--color-text-3);
    font-size: 12px;
  }
  .rich-text-preview {
    line-height: 1.7;
    word-break: break-word;
  }
  .new-bug-body {
    height: 100%;
    overflow-y: auto;
    overflow-x: hidden;
    padding: 16px 20px;
  }
  .rich-text-preview :deep(img) {
    max-width: 100%;
  }
  .rich-text-preview :deep(p) {
    margin: 6px 0;
  }
  .reject-alert {
    margin-bottom: 12px;
  }
  .reject-comment {
    margin-top: 4px;
    color: var(--color-text-2);
  }
  .oplog-head {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .oplog-operator {
    font-weight: 600;
  }
  .oplog-time {
    color: var(--color-text-3);
    font-size: 12px;
  }
  .oplog-change {
    margin-top: 4px;
    color: var(--color-text-2);
    display: flex;
    align-items: center;
    gap: 4px;
    flex-wrap: wrap;
  }
  .oplog-old {
    text-decoration: line-through;
    color: var(--color-text-3);
  }
  .oplog-comment {
    margin-top: 4px;
    background: var(--color-fill-2);
    border-left: 3px solid var(--color-border-3);
    padding: 6px 10px;
    border-radius: 0 4px 4px 0;
  }

  /* 评审记录投票流 */
  .vote-item {
    display: flex;
    align-items: flex-start;
    gap: 10px;
    padding: 10px 0;
    border-bottom: 1px dashed var(--color-border-2);
  }
  .vote-item:last-child {
    border-bottom: none;
  }
  .vote-avatar {
    flex-shrink: 0;
  }
  .vote-main {
    flex: 1;
    min-width: 0;
  }
  .vote-name-line {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }
  .vote-name {
    font-size: 13px;
    font-weight: 600;
  }
  .vote-review {
    font-size: 12px;
    color: var(--color-text-3);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .vote-comment {
    font-size: 13px;
    color: var(--color-text-2);
    white-space: pre-wrap;
    margin-top: 2px;
  }
  .vote-time {
    font-size: 12px;
    color: var(--color-text-3);
    margin-top: 4px;
  }
  .vote-result {
    flex-shrink: 0;
  }
  /* 评论 tab：列表区滚动，输入框固定底部（flex 布局，不依赖 sticky） */
  .comment-pane {
    overflow: hidden !important;
    display: flex;
    flex-direction: column;
  }
  .comment-tab-body {
    flex: 1 1 0;
    min-height: 0;
    overflow-y: auto;
    padding-right: 4px;
  }
  .comment-empty {
    margin-top: 60px;
  }
  /* 评论输入框：固定在评论 tab 底部 */
  .comment-input-bar {
    position: sticky;
    bottom: 0;
    z-index: 10;
    flex-shrink: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 10px 0 4px;
    margin-top: 10px;
    background: var(--color-bg-2);
    border-top: 1px solid var(--color-border-1);
  }

  /* 评论列表：头像 + 右侧内容区 */
  .comment-item {
    display: flex;
    gap: 10px;
    padding: 14px 0;
    border-bottom: 1px solid var(--color-border-1);
  }
  .comment-flash {
    animation: comment-flash 0.8s ease-in-out 3;
    :deep(.w-e-text-container) {
      background-color: transparent !important;
    }
  }
  @keyframes comment-flash {
    0%, 100% { background: transparent; }
    50% { background: var(--color-primary-light-1); }
  }
  .comment-item:last-child {
    border-bottom: none;
  }
  .comment-avatar {
    flex-shrink: 0;
    font-size: 14px;
    font-weight: 600;
  }
  .comment-main {
    flex: 1;
    min-width: 0;
  }
  .comment-head {
    display: flex;
    align-items: baseline;
    gap: 8px;
  }
  .comment-who {
    font-weight: 600;
    font-size: 13px;
    color: var(--color-text-1);
  }
  .comment-time {
    color: var(--color-text-3);
    font-size: 12px;
  }
  /* 操作按钮固定在评论行最右侧，默认弱化、悬浮行时显现 */
  .comment-actions {
    margin-left: auto;
    display: flex;
    gap: 12px;
    font-size: 12px;
    opacity: 0;
    transition: opacity 0.15s;
  }
  .comment-item:hover > .comment-main .comment-actions,
  .reply-item:hover .comment-actions {
    opacity: 1;
  }
  .comment-actions :deep(.arco-link) {
    font-size: 12px;
  }
  .comment-text {
    margin-top: 4px;
    color: var(--color-text-1);
    font-size: 13px;
    line-height: 1.6;
    word-break: break-word;
  }
  .comment-edit-input {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    margin-top: 4px;
  }

  /* 回复列表：灰底分区 + 小头像 */
  .reply-list {
    margin-top: 10px;
    padding: 4px 12px;
    background: var(--color-fill-1);
    border-radius: 6px;
  }
  .reply-item {
    display: flex;
    gap: 8px;
    padding: 10px 0;
  }
  .reply-item + .reply-item {
    border-top: 1px solid var(--color-border-1);
  }
  .reply-item .comment-avatar {
    font-size: 12px;
  }
  .reply-input {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    margin-top: 10px;
  }
  .edit-actions,
  .reply-actions,
  .comment-input-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
  }
  .reply-toggle {
    margin-top: 6px;
    font-size: 12px;
    color: rgb(var(--primary-6));
    cursor: pointer;
    user-select: none;
    display: inline-flex;
    align-items: center;
    gap: 2px;
  }
  .reply-toggle:hover {
    color: rgb(var(--primary-5));
  }
  .reply-cancel {
    font-size: 12px;
    flex-shrink: 0;
  }
  .dd-footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }
</style>
