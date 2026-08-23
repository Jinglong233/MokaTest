<template>
  <a-drawer
    :visible="visible"
    width="820px"
    :mask-closable="true"
    :footer="tabKey === 'comments'"
    class="bug-detail-drawer"
    @cancel="close"
  >
    <template #title>
      <div class="bdd-header">
        <span class="bdd-code">{{ bugData.bugCode }}</span>
        <a-tag :color="bugStatusColor(bugData.status)" size="small">{{ bugStatusText(bugData.status) }}</a-tag>
        <span class="bdd-title-text">{{ bugData.title }}</span>
      </div>
    </template>

    <a-skeleton v-if="loading" :animation="true">
      <a-skeleton-line :rows="6" />
    </a-skeleton>

    <a-tabs v-else v-model:active-key="tabKey" type="line" class="bdd-tabs" @change="handleTabChange">
      <!-- ========== 基本信息 ========== -->
      <a-tab-pane key="info" title="基本信息">
        <div class="bdd-info-list">
          <!-- 处理人 -->
          <div class="bdd-field-row">
            <span class="bdd-field-label">处理人</span>
            <a-select
              v-permission="'qa:bug:update'"
              v-model="bugData.assigneeId"
              allow-clear allow-search :filter-option="false"
              placeholder="搜索用户"
              style="width: 220px;"
              @search="handleSearchUser"
              @dropdown-visible-change="handleUserDropdownVisibleChange"
              @change="(val) => saveOnChange('assigneeId', val)"
            >
              <a-option v-for="u in userOptions" :key="u.id" :value="u.id">{{ u.nickname || u.username }}</a-option>
            </a-select>
          </div>

          <!-- 状态 -->
          <div class="bdd-field-row">
            <span class="bdd-field-label">状态</span>
            <a-select
              v-permission="'qa:bug:transition'"
              v-model="bugData.status"
              placeholder="请选择状态"
              style="width: 220px;"
              @change="(val) => saveOnChange('status', val)"
            >
              <a-option v-for="s in allBugStatuses" :key="s.value" :value="s.value">{{ s.label }}</a-option>
            </a-select>
          </div>

          <!-- 严重程度 -->
          <div class="bdd-field-row">
            <span class="bdd-field-label">严重程度</span>
            <a-select
              v-permission="'qa:bug:update'"
              v-model="bugData.severity"
              placeholder="请选择"
              style="width: 220px;"
              @change="(val) => saveOnChange('severity', val)"
            >
                <a-option value="FATAL">致命</a-option>
                <a-option value="SERIOUS">严重</a-option>
                <a-option value="NORMAL">一般</a-option>
                <a-option value="TIPS">提示</a-option>
            </a-select>
          </div>

          <!-- 标签 -->
          <div class="bdd-field-row">
            <span class="bdd-field-label">标签</span>
            <a-input-tag
              v-permission="'qa:bug:update'"
              v-model="bugTags"
              placeholder="输入标签后按回车"
              allow-clear
              style="width: 300px;"
              @blur="saveTagsOnChange"
            />
          </div>
        </div>
      </a-tab-pane>

      <!-- ========== 详情 ========== -->
      <a-tab-pane key="detail" title="详情">
        <div class="bdd-detail-toolbar">
          <template v-if="!detailEditing">
            <a-button v-permission="'qa:bug:update'" type="outline" size="small" @click="startDetailEdit">
              <template #icon><IconEdit /></template>编辑
            </a-button>
          </template>
          <template v-else>
            <span class="bdd-editing-tip">编辑中</span>
            <a-space>
              <a-button size="small" @click="cancelDetailEdit">取消</a-button>
              <a-button type="primary" size="small" :loading="detailSaving" @click="saveDetailEdit">保存</a-button>
            </a-space>
          </template>
        </div>
        <div class="bdd-detail">
          <div class="bdd-section">
            <div class="bdd-section-title">BUG 描述</div>
            <RichEditor v-if="!detailEditing" :model-value="bugData.description" readonly :auto-height="true" />
            <RichEditor v-else v-model="detailForm.description" :auto-height="true" />
          </div>
          <div class="bdd-section">
            <div class="bdd-section-title">复现步骤</div>
            <RichEditor v-if="!detailEditing" :model-value="bugData.reproduceSteps" readonly :auto-height="true" />
            <RichEditor v-else v-model="detailForm.reproduceSteps" :auto-height="true" />
          </div>
        </div>
      </a-tab-pane>

      <!-- ========== 用例 ========== -->
      <a-tab-pane key="case">
        <template #title>用例<span v-if="linkedCaseList.length" class="tab-count">{{ linkedCaseList.length }}</span></template>
        <div class="bdd-case">
          <div class="bdd-case-toolbar">
            <span class="bdd-case-tip">BUG 关联的测试用例</span>
            <a-button v-permission="'qa:bug:update'" type="outline" size="small" @click="openCaseSelectDrawer">
              <template #icon><IconLink /></template>关联用例
            </a-button>
          </div>
          <a-table
            v-if="linkedCaseList.length"
            :data="linkedCaseList"
            :loading="caseLoading"
            :pagination="false"
            size="small"
            row-key="id"
          >
            <template #columns>
              <a-table-column title="用例编号" data-index="caseCode" :width="120" />
              <a-table-column title="用例名称" data-index="caseName" :ellipsis="true" :tooltip="true" />
              <a-table-column title="优先级" data-index="priority" :width="80" />
              <a-table-column title="操作" :width="70" align="center">
                <template #cell="{ record }">
                  <a-popconfirm content="确认解除关联？" @ok="handleUnbindCase(record.id)">
                    <a-button v-permission="'qa:bug:update'" type="text" size="small" status="danger">解绑</a-button>
                  </a-popconfirm>
                </template>
              </a-table-column>
            </template>
          </a-table>
          <a-empty v-else description="暂未关联用例" />
        </div>
      </a-tab-pane>

      <!-- ========== 评论 ========== -->
      <a-tab-pane key="comments">
        <template #title>评论<span v-if="commentTree.length" class="tab-count">{{ commentTree.length }}</span></template>
        <div class="bdd-comments">
          <!-- 评论列表 -->
          <div v-if="commentTree.length" class="bdd-comment-list">
            <div v-for="cmt in commentTree" :key="cmt.id" :id="'bdd-comment-' + cmt.id" class="bdd-comment-item" :class="{ 'bdd-comment-flash': flashCommentId === cmt.id }">
              <a-avatar class="bdd-comment-avatar" :size="32" :image-url="cmt.createUserAvatar || undefined" :style="cmt.createUserAvatar ? {} : { backgroundColor: avatarColor(commentName(cmt)) }">{{ cmt.createUserAvatar ? '' : avatarText(commentName(cmt)) }}</a-avatar>
              <div class="bdd-comment-main">
                <div class="bdd-comment-head">
                  <span class="bdd-comment-user">{{ commentName(cmt) }}</span>
                  <span class="bdd-comment-time">{{ cmt.createTime }}</span>
                  <div v-permission="'qa:bug:comment:create'" class="bdd-comment-actions">
                    <a-link @click="startReply(cmt, null)">回复</a-link>
                    <template v-if="cmt.createUserId === currentUserId">
                      <a-link @click="startEditComment(cmt)">编辑</a-link>
                      <a-popconfirm content="确认删除这条评论？其下的回复会一并删除" @ok="handleDeleteComment(cmt)">
                        <a-link status="danger">删除</a-link>
                      </a-popconfirm>
                    </template>
                  </div>
                </div>
                <div v-if="editingCommentId === cmt.id" class="bdd-comment-edit-input">
                  <RichEditor v-model="editingContent" auto-height mention :mention-list="mentionOptions" placeholder="编辑评论..." @mention-trigger="handleMentionSearch('')" @mention="onMentionSelect" />
                  <div class="bdd-edit-actions">
                    <a-button type="primary" size="small" :loading="commentSaving" @click="saveEditComment(cmt)">保存</a-button>
                    <a-link @click="editingCommentId = null">取消</a-link>
                  </div>
                </div>
                <RichEditor v-else :model-value="cmt.content" readonly />

                <!-- 回复列表 -->
                <template v-if="cmt.replies && cmt.replies.length">
                  <div v-if="expandedReplyIds.has(cmt.id)" class="bdd-reply-list">
                    <div v-for="reply in cmt.replies" :key="reply.id" :id="'bdd-comment-' + reply.id" class="bdd-reply-item" :class="{ 'bdd-comment-flash': flashCommentId === reply.id }">
                      <a-avatar class="bdd-comment-avatar" :size="24" :image-url="reply.createUserAvatar || undefined" :style="reply.createUserAvatar ? {} : { backgroundColor: avatarColor(commentName(reply)) }">{{ reply.createUserAvatar ? '' : avatarText(commentName(reply)) }}</a-avatar>
                      <div class="bdd-comment-main">
                        <div class="bdd-comment-head">
                          <span class="bdd-comment-user">{{ commentName(reply) }} 回复 {{ commentName(cmt) }}</span>
                          <div v-permission="'qa:bug:comment:create'" class="bdd-comment-actions">
                            <a-link @click="startReply(cmt, reply)">回复</a-link>
                            <template v-if="reply.createUserId === currentUserId">
                              <a-link @click="startEditComment(reply)">编辑</a-link>
                              <a-popconfirm content="确认删除这条回复？" @ok="handleDeleteComment(reply)">
                                <a-link status="danger">删除</a-link>
                              </a-popconfirm>
                            </template>
                          </div>
                        </div>
                        <div v-if="editingCommentId === reply.id" class="bdd-comment-edit-input">
                          <RichEditor v-model="editingContent" auto-height mention :mention-list="mentionOptions" placeholder="编辑评论..." @mention-trigger="handleMentionSearch('')" @mention="onMentionSelect" />
                          <div class="bdd-edit-actions">
                            <a-button type="primary" size="small" :loading="commentSaving" @click="saveEditComment(reply)">保存</a-button>
                            <a-link @click="editingCommentId = null">取消</a-link>
                          </div>
                        </div>
                        <RichEditor v-else :model-value="reply.content" readonly />
                        <span class="bdd-comment-time">{{ reply.createTime }}</span>
                      </div>
                    </div>
                  </div>
                  <div class="bdd-reply-toggle" @click="toggleReplies(cmt.id)">
                    <template v-if="expandedReplyIds.has(cmt.id)">收起回复 <IconUp /></template>
                    <template v-else>共 {{ cmt.replies.length }} 条回复，点击展开 <IconDown /></template>
                  </div>
                </template>

                <!-- 行内回复输入框 -->
                <div v-if="replyParentId === cmt.id" class="bdd-reply-input">
                  <RichEditor v-model="replyContent" auto-height mention :mention-list="mentionOptions" :placeholder="`回复 ${replyTargetName}...`" @mention-trigger="handleMentionSearch('')" @mention="onMentionSelect" />
                  <div class="bdd-reply-actions">
                    <a-button type="primary" size="small" :loading="commentSaving" @click="handleSaveReply(cmt)">回复</a-button>
                    <a-link @click="cancelReply">取消</a-link>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <a-empty v-else description="暂无评论" />

        </div>
      </a-tab-pane>

      <!-- ========== 变更历史 ========== -->
      <a-tab-pane key="history" title="变更历史">
        <div class="bdd-history">
          <a-timeline v-if="historyList.length > 0">
            <a-timeline-item v-for="item in historyList" :key="item.id">
              <div class="bdd-history-head">
                <span class="bdd-history-time">{{ item.operateTime }}</span>
              </div>
              <div class="bdd-history-content">
                <span class="bdd-history-field">{{ fieldNameText(item.fieldName) }}</span>：
                <span class="bdd-history-old">{{ displayValue(item.fieldName, item.oldValue) }}</span>
                →
                <span class="bdd-history-new">{{ displayValue(item.fieldName, item.newValue) }}</span>
              </div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="暂无变更记录" />
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- 关联用例抽屉（二级） -->
    <a-drawer
      :visible="caseSelectVisible"
      :width="1000"
      :footer="true"
      :mask-closable="true"
      @cancel="caseSelectVisible = false"
    >
      <template #title>关联用例</template>
      <div class="case-select-body">
        <!-- 左侧模块树 -->
        <div class="case-select-side">
          <a-input-search v-model="moduleKeyword" placeholder="搜索模块" style="margin-bottom: 8px;" size="small" />
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
              </template>
            </a-tree>
          </a-scrollbar>
        </div>
        <!-- 右侧用例列表 -->
        <div class="case-select-main">
          <div class="case-select-toolbar">
            <a-input-search v-model="caseKeyword" placeholder="搜索用例编号/名称" allow-clear style="width: 240px" @search="searchCases" />
            <span class="selected-count">已选 {{ tempSelectedCaseIds.length }} 条</span>
          </div>
          <a-table
            :data="caseOptions"
            :loading="caseSearching"
            :pagination="casePagination"
            size="small"
            row-key="id"
            :scroll="{ y: 400 }"
            :selected-keys="tempSelectedCaseIds"
            :row-selection="{ type: 'checkbox', showCheckedAll: true, onlyCurrent: true }"
            @selection-change="onCaseSelectionChange"
            @page-change="onCasePageChange"
          >
            <template #columns>
              <a-table-column title="用例编号" data-index="caseCode" :width="120" />
              <a-table-column title="用例名称" data-index="caseName" :ellipsis="true" :tooltip="true" />
              <a-table-column title="优先级" data-index="priority" :width="80" />
            </template>
          </a-table>
        </div>
      </div>
      <template #footer>
        <a-space>
          <a-button @click="caseSelectVisible = false">取消</a-button>
          <a-button type="primary" :loading="caseBinding" @click="handleBindCases">确认关联</a-button>
        </a-space>
      </template>
    </a-drawer>

    <template #footer>
      <div v-permission="'qa:bug:comment:create'" class="bdd-comment-input-bar">
        <a-input v-if="!commentEditorActive" v-model="newComment" placeholder="输入评论..." allow-clear @focus="commentEditorActive = true" />
        <template v-else>
          <RichEditor v-model="newComment" auto-height mention :mention-list="mentionOptions" placeholder="输入评论，@ 可提及项目成员，Ctrl+Enter 发表" @keydown="handleCommentKeydown" @mention-trigger="handleMentionSearch('')" @mention="onMentionSelect" />
          <div class="bdd-comment-input-actions">
            <a-button type="primary" size="small" :loading="commentSaving" @click="handleSaveComment">发表</a-button>
            <a-button size="small" @click="commentEditorActive = false; newComment = ''">取消</a-button>
          </div>
        </template>
      </div>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import { Message } from '@arco-design/web-vue';
import { IconLink, IconEdit, IconDown, IconUp } from '@arco-design/web-vue/es/icon';
import RichEditor from '@/components/rich-editor/index.vue';
import {
  getBugDetail,
  getBugCommentList,
  saveBugComment,
  deleteBugComment,
  getBugOperationLogList,
  bindBugCases,
  unbindBugCase,
  getBugLinkedCases,
  getTestCaseList,
  getQaModuleTree,
  updateBug,
  transitionBugStatus,
} from '@/api/MyApi/qa';
import { getUserListByPage } from '@/api/MyApi/user';
import { useProjectStore, useUserStore } from '@/store';

const props = defineProps<{
  visible: boolean;
  bugId: number | null;
  activeTab?: string;
  highlightCommentId?: number;
}>();

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void;
  (e: 'refresh'): void;
}>();

const projectStore = useProjectStore();
const userStore = useUserStore();
const loading = ref(false);
const tabKey = ref('info');
const bugData = ref<any>({});

// 基本信息 - 始终可编辑，变更即保存
const userOptions = ref<any[]>([]);
const userPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

const allBugStatuses = [
  { value: 'NEW', label: '新建' },
  { value: 'CONFIRMED', label: '已确认' },
  { value: 'FIXING', label: '修复中' },
  { value: 'FIXED', label: '已修复' },
  { value: 'VERIFIED', label: '已验证' },
  { value: 'CLOSED', label: '已关闭' },
  { value: 'REJECTED', label: '已驳回' },
];

// 标签 computed（bugData.tags 是逗号字符串，前端用数组）
const bugTags = computed({
  get: () => parseTags(bugData.value.tags),
  set: (val: string[]) => { bugData.value.tags = val.join(','); },
});

const saveOnChange = async (field: string, val: any) => {
  if (val === undefined || val === null) return;
  try {
    if (field === 'status') {
      const res: any = await transitionBugStatus(bugData.value.id, val);
      if (res.code === 200) { Message.success('状态已更新'); emit('refresh'); }
    } else {
      const payload = { ...bugData.value, [field]: val || null };
      const res: any = await updateBug(payload);
      if (res.code === 200) { Message.success('更新成功'); emit('refresh'); }
    }
  } catch (e) {
    console.error(e);
  }
};

const saveTagsOnChange = async () => {
  const tagsStr = bugTags.value.join(',');
  try {
    const payload = { ...bugData.value, tags: tagsStr };
    const res: any = await updateBug(payload);
    if (res.code === 200) { Message.success('标签已更新'); emit('refresh'); }
  } catch (e) {
    console.error(e);
  }
};

// 详情 tab editing
const detailEditing = ref(false);
const detailSaving = ref(false);
const detailForm = ref<any>({});

const startDetailEdit = () => {
  detailForm.value = {
    description: bugData.value.description || '',
    reproduceSteps: bugData.value.reproduceSteps || '',
  };
  detailEditing.value = true;
};

const cancelDetailEdit = () => {
  detailEditing.value = false;
  detailForm.value = {};
};

const saveDetailEdit = async () => {
  detailSaving.value = true;
  try {
    const payload = { ...bugData.value, description: detailForm.value.description, reproduceSteps: detailForm.value.reproduceSteps };
    const res: any = await updateBug(payload);
    if (res.code === 200) {
      Message.success('保存成功');
      bugData.value.description = detailForm.value.description;
      bugData.value.reproduceSteps = detailForm.value.reproduceSteps;
      detailEditing.value = false;
      emit('refresh');
    }
  } catch (e) {
    console.error(e);
  } finally {
    detailSaving.value = false;
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
      pageSize: userPage.value.pageSize,
    });
    const records = res.data?.records || res.data || [];
    userOptions.value = isLoadMore ? [...userOptions.value, ...records] : records;
    userPage.value.hasMore = records.length >= userPage.value.pageSize;
  } catch (e) {
    console.error(e);
  }
};

const handleUserDropdownVisibleChange = (visible: boolean) => {
  if (visible) handleSearchUser('');
};

// 评论
const commentList = ref<any[]>([]);
const commentTree = computed(() => commentList.value);
const newComment = ref('');
const commentSaving = ref(false);
const commentEditorActive = ref(false);
const editingCommentId = ref<number | null>(null);
const editingContent = ref('');
const replyParentId = ref<number | null>(null);
const replyContent = ref('');
const replyTarget = ref<any>(null);
const replyTargetName = computed(() =>
  replyTarget.value
    ? replyTarget.value.createUserName || ('用户' + replyTarget.value.createUserId)
    : ''
);
const expandedReplyIds = ref<Set<number>>(new Set());
const flashCommentId = ref<number | null>(null);
const currentUserId = computed(() => userStore.userInfo?.id);

// 提及下拉选项 + 昵称→用户ID映射
const mentionOptions = ref<{ label: string; value: string }[]>([]);
const mentionNameIdMap = ref<Record<string, number>>({});

const commentName = (cmt: any) => cmt.createUserName || ('用户' + cmt.createUserId);

// Ctrl+Enter 发表（Enter 留给换行和提及下拉的选项确认）
const handleCommentKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); handleSaveComment(); }
};

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
      .map((u: any) => ({ label: u.nickname || u.username, value: u.nickname || u.username }))
      .filter((o: any) => o.value);
  } catch (e) { console.error(e); }
};

// 富文本空内容检测（wangEditor 空内容返回 <p><br></p>）
const isEmptyRichContent = (html: string) => {
  if (!html || !html.trim()) return true;
  const text = html.replace(/<[^>]+>/g, '').replace(/&nbsp;/g, ' ').trim();
  return !text;
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

const startReply = (cmt: any, reply: any | null) => {
  replyParentId.value = cmt.id;
  replyTarget.value = reply || cmt;
  replyContent.value = reply ? `<p>@${replyTargetName.value} </p>` : '';
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

const handleSaveReply = async (cmt: any) => {
  if (isEmptyRichContent(replyContent.value)) {
    Message.warning('回复内容不能为空');
    return;
  }
  commentSaving.value = true;
  try {
    const content = replyContent.value.trim();
    const mentionIds = extractMentionIds(content);
    const res: any = await saveBugComment({
      bugId: bugData.value.id,
      parentId: cmt.id,
      content,
      mentionUserIds: mentionIds.length ? JSON.stringify(mentionIds) : undefined,
    });
    if (res.code === 200) {
      Message.success('回复成功');
      cancelReply();
      await loadComments(bugData.value.id);
      if (!expandedReplyIds.value.has(cmt.id)) {
        expandedReplyIds.value = new Set([...expandedReplyIds.value, cmt.id]);
      }
    }
  } catch (e) { console.error(e); }
  finally { commentSaving.value = false; }
};

const startEditComment = (cmt: any) => {
  editingCommentId.value = cmt.id;
  editingContent.value = cmt.content;
};

const saveEditComment = async (cmt: any) => {
  if (isEmptyRichContent(editingContent.value)) return;
  commentSaving.value = true;
  try {
    const res: any = await saveBugComment({
      ...cmt,
      content: editingContent.value.trim(),
    });
    if (res.code === 200) {
      Message.success('修改成功');
      editingCommentId.value = null;
      await loadComments(bugData.value.id);
    }
  } catch (e) { console.error(e); }
  finally { commentSaving.value = false; }
};

const handleDeleteComment = async (cmt: any) => {
  try {
    const res: any = await deleteBugComment(cmt.id);
    if (res.code === 200) {
      Message.success('删除成功');
      await loadComments(bugData.value.id);
    }
  } catch (e) { console.error(e); }
};

const toggleReplies = (cmtId: number) => {
  const next = new Set(expandedReplyIds.value);
  if (next.has(cmtId)) {
    next.delete(cmtId);
  } else {
    next.add(cmtId);
  }
  expandedReplyIds.value = next;
};

// 变更历史
const historyList = ref<any[]>([]);

// 关联用例
const linkedCaseList = ref<any[]>([]);
const caseLoading = ref(false);
const caseSelectVisible = ref(false);
const caseKeyword = ref('');
const caseOptions = ref<any[]>([]);
const caseSearching = ref(false);
const casePagination = ref({ current: 1, pageSize: 10, total: 0 });
const tempSelectedCaseIds = ref<number[]>([]);
const caseBinding = ref(false);
const moduleKeyword = ref('');
const moduleTree = ref<any[]>([]);
const selectedModuleKeys = ref<(string | number)[]>([]);
const selectedModuleId = ref<number | undefined>(undefined);

const filteredModuleTree = computed(() => {
  if (!moduleKeyword.value) return moduleTree.value;
  const filter = (nodes: any[]): any[] => {
    return nodes.reduce((acc, node) => {
      const children = node.children ? filter(node.children) : [];
      if (node.moduleName.includes(moduleKeyword.value) || children.length) {
        acc.push({ ...node, children });
      }
      return acc;
    }, []);
  };
  return filter(moduleTree.value);
});

const openCaseSelectDrawer = async () => {
  caseSelectVisible.value = true;
  tempSelectedCaseIds.value = [];
  caseKeyword.value = '';
  casePagination.value.current = 1;
  selectedModuleId.value = undefined;
  selectedModuleKeys.value = [];
  await loadModuleTree();
  await searchCases();
  await loadLinkedCases();
  // 预选已关联的用例
  tempSelectedCaseIds.value = linkedCaseList.value.map((tc: any) => tc.id);
};

const loadModuleTree = async () => {
  const pid = projectStore.getProjectId as number;
  if (!pid) return;
  try {
    const res: any = await getQaModuleTree(pid, 'CASE');
    moduleTree.value = res.data || [];
  } catch (e) { console.error(e); }
};

const handleModuleSelect = (keys: (string | number)[], data: any) => {
  selectedModuleKeys.value = keys;
  selectedModuleId.value = data?.id !== undefined ? Number(data.id) : undefined;
  casePagination.value.current = 1;
  searchCases();
};

const close = () => {
  emit('update:visible', false);
};

const handleTabChange = (key: string | number) => {
  const k = String(key);
  if (k === 'comments' && props.bugId) loadComments(props.bugId);
  if (k === 'history' && props.bugId) loadHistory(props.bugId);
  if (k === 'case' && props.bugId) loadLinkedCases();
};

const loadDetail = async (id: number) => {
  loading.value = true;
  try {
    const res: any = await getBugDetail(id);
    if (res.code === 200 && res.data) {
      bugData.value = res.data;
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const loadComments = async (bugId: number) => {
  try {
    const res: any = await getBugCommentList(bugId);
    commentList.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const loadHistory = async (bugId: number) => {
  try {
    const res: any = await getBugOperationLogList(bugId);
    historyList.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const loadLinkedCases = async () => {
  if (!props.bugId) return;
  caseLoading.value = true;
  try {
    const res: any = await getBugLinkedCases(props.bugId);
    linkedCaseList.value = res.data || [];
  } catch (e) {
    console.error(e);
  } finally {
    caseLoading.value = false;
  }
};

const handleSaveComment = async () => {
  if (isEmptyRichContent(newComment.value)) {
    Message.warning('请输入评论内容');
    return;
  }
  commentSaving.value = true;
  try {
    const content = newComment.value.trim();
    const mentionIds = extractMentionIds(content);
    const res: any = await saveBugComment({
      bugId: bugData.value.id,
      parentId: 0,
      content,
      mentionUserIds: mentionIds.length ? JSON.stringify(mentionIds) : undefined,
    });
    if (res.code === 200) {
      Message.success('评论发表成功');
      newComment.value = '';
      commentEditorActive.value = false;
      await loadComments(bugData.value.id);
    }
  } catch (e) {
    console.error(e);
  } finally {
    commentSaving.value = false;
  }
};

// 关联用例搜索
const searchCases = async () => {
  caseSearching.value = true;
  try {
    const res: any = await getTestCaseList(
      projectStore.getProjectId as number,
      selectedModuleId.value,
      undefined, undefined,
      caseKeyword.value || undefined, undefined,
      casePagination.value.current, casePagination.value.pageSize,
      undefined, undefined
    );
    caseOptions.value = res.data?.records || [];
    casePagination.value.total = res.data?.total || 0;
  } catch (e) {
    console.error(e);
  } finally {
    caseSearching.value = false;
  }
};

const onCaseSelectionChange = (keys: (string | number)[]) => {
  tempSelectedCaseIds.value = keys as number[];
};

const onCasePageChange = (page: number) => {
  casePagination.value.current = page;
  searchCases();
};

const handleBindCases = async () => {
  if (tempSelectedCaseIds.value.length === 0) {
    Message.warning('请选择要关联的用例');
    return;
  }
  caseBinding.value = true;
  try {
    const res: any = await bindBugCases(bugData.value.id, tempSelectedCaseIds.value);
    if (res.code === 200) {
      Message.success('关联成功');
      caseSelectVisible.value = false;
      await loadLinkedCases();
      emit('refresh');
    }
  } catch (e) {
    console.error(e);
  } finally {
    caseBinding.value = false;
  }
};

const handleUnbindCase = async (testCaseId: number) => {
  try {
    const res: any = await unbindBugCase(bugData.value.id, testCaseId);
    if (res.code === 200) {
      Message.success('已解除关联');
      await loadLinkedCases();
      emit('refresh');
    }
  } catch (e) {
    console.error(e);
  }
};

// 辅助函数
const parseTags = (tags: any): string[] => {
  if (Array.isArray(tags)) return tags;
  if (typeof tags === 'string') return tags.split(',').filter((t: string) => t.trim());
  return [];
};

const formatTime = (time: string | undefined) => {
  if (!time) return '-';
  return time.replace('T', ' ').substring(0, 19);
};

const severityText = (severity: string) => {
  const map: Record<string, string> = { FATAL: '致命', SERIOUS: '严重', NORMAL: '一般', TIPS: '提示' };
  return map[severity] || severity;
};

const bugStatusColor = (status: string) => {
  const map: Record<string, string> = {
    NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold', FIXED: 'blue', VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray',
  };
  return map[status] || 'gray';
};

const bugStatusText = (status: string) => {
  const map: Record<string, string> = {
    NEW: '新建', CONFIRMED: '已确认', FIXING: '修复中', FIXED: '已修复', VERIFIED: '已验证', CLOSED: '已关闭', REJECTED: '已驳回',
  };
  return map[status] || status;
};

const fieldNameText = (fieldName: string) => {
  const map: Record<string, string> = {
    title: '标题', severity: '严重程度', priority: '优先级', status: '状态',
    assigneeId: '处理人', moduleId: '所属模块', environment: '环境',
    deadline: '截止日期', fixedVersion: '修复版本', closeReason: '关闭原因',
    testCaseId: '关联用例', requirementId: '关联需求',
  };
  return map[fieldName] || fieldName;
};

const displayValue = (fieldName: string, value: string | undefined) => {
  if (!value) return '-';
  if (fieldName === 'status') return bugStatusText(value);
  if (fieldName === 'severity') return severityText(value);
  return value;
};

const AVATAR_COLORS = [
  '#165dff', '#00b42a', '#ff7d00', '#f53f3f', '#722ed1', '#0fc6c2', '#eb2f96', '#86909c',
];
const avatarColor = (name: string) => {
  const s = name || '?';
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) >>> 0;
  return AVATAR_COLORS[h % AVATAR_COLORS.length];
};

const avatarText = (name: string) =>
  (name || '?').trim().charAt(0).toUpperCase();

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

watch(
  () => props.visible,
  (val) => {
    if (val && props.bugId) {
      tabKey.value = props.activeTab || 'info';
      commentList.value = [];
      historyList.value = [];
      linkedCaseList.value = [];
      newComment.value = '';
      commentEditorActive.value = false;
      commentSaving.value = false;
      editingCommentId.value = null;
      replyParentId.value = null;
      replyTarget.value = null;
      replyContent.value = '';
      expandedReplyIds.value = new Set();
      flashCommentId.value = null;
      tempSelectedCaseIds.value = [];
      caseKeyword.value = '';
      caseOptions.value = [];
      casePagination.value.current = 1;
      loadDetail(props.bugId);
      loadComments(props.bugId);
      loadHistory(props.bugId);
      loadLinkedCases();
    }
  },
  { immediate: true },
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
          const el = document.getElementById('bdd-comment-' + id);
          if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        });
      }, 500);
    });
  },
  { immediate: true }
);


</script>

<style scoped lang="less">
.bdd-header {
  display: flex;
  align-items: center;
  gap: 8px;
  .bdd-code {
    font-size: 13px;
    color: var(--color-text-3);
  }
  .bdd-title-text {
    font-size: 15px;
    font-weight: 600;
  }
}

.bdd-tabs {
  :deep(.arco-tabs-content) {
    padding-top: 8px;
  }
}

.tab-count {
  display: inline-block;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  margin-left: 4px;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
  color: #fff;
  background: rgb(var(--arcoblue-6));
  border-radius: 8px;
}

/* 基本信息 - 单列 inline editing */
.bdd-info-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.bdd-field-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-fill-2);
}
.bdd-field-label {
  font-size: 13px;
  color: var(--color-text-3);
  white-space: nowrap;
  width: 70px;
  flex-shrink: 0;
}
.bdd-field-control {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 4px;
}
.bdd-field-value {
  font-size: 13px;
  color: var(--color-text-1);
}
.bdd-field-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

/* 详情 tab 工具栏 */
.bdd-detail-toolbar {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  min-height: 32px;
}
.bdd-editing-tip {
  font-size: 13px;
  color: rgb(var(--arcoblue-6));
  margin-right: auto;
}

/* 详情 */
.bdd-detail {
  .bdd-section {
    margin-bottom: 20px;
  }
  .bdd-section-title {
    font-size: 14px;
    font-weight: 600;
    margin-bottom: 8px;
    padding-left: 8px;
    border-left: 3px solid rgb(var(--arcoblue-6));
    line-height: 1.4;
  }
}

/* 用例 */
.bdd-case {
  .bdd-case-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
  }
  .bdd-case-tip {
    font-size: 13px;
    color: var(--color-text-3);
  }
}

/* 评论 */
.bdd-comments {
  .bdd-comment-list {
    margin-bottom: 16px;
  }
  .bdd-comment-item {
    display: flex;
    gap: 10px;
    margin-bottom: 12px;
  }
  .bdd-comment-flash {
    animation: bdd-comment-flash 0.8s ease-in-out 3;
    :deep(.w-e-text-container) {
      background-color: transparent !important;
    }
  }
  @keyframes bdd-comment-flash {
    0%, 100% { background: transparent; }
    50% { background: var(--color-primary-light-1); }
  }
  .bdd-reply-item {
    display: flex;
    gap: 8px;
    margin-bottom: 8px;
  }
  .bdd-comment-main {
    flex: 1;
    min-width: 0;
  }
  .bdd-comment-head {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 4px;
  }
  .bdd-comment-actions {
    display: flex;
    gap: 4px;
    margin-left: auto;
  }
  .bdd-comment-user {
    font-size: 13px;
    font-weight: 500;
  }
  .bdd-comment-time {
    font-size: 12px;
    color: var(--color-text-3);
  }
  .bdd-comment-text {
    font-size: 13px;
    line-height: 1.5;
    word-break: break-all;
  }
  .bdd-comment-edit-input {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
  }
  .bdd-reply-list {
    margin-top: 8px;
    padding-left: 8px;
    border-left: 2px solid var(--color-fill-3);
  }
  .bdd-reply-toggle {
    margin-top: 4px;
    font-size: 12px;
    color: rgb(var(--arcoblue-6));
    cursor: pointer;
    user-select: none;
  }
  .bdd-reply-input {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 8px;
    margin-top: 8px;
  }
  .bdd-reply-cancel {
    margin-left: 4px;
  }
  .bdd-comment-input-bar {
    position: sticky;
    bottom: 0;
    z-index: 10;
    display: flex;
    flex-direction: column;
    gap: 8px;
    align-items: stretch;
    margin-top: 12px;
    padding: 8px 0;
    background-color: var(--color-bg-2);
    border-top: 1px solid var(--color-fill-2);
  }
  .bdd-edit-actions,
  .bdd-reply-actions,
  .bdd-comment-input-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-top: 4px;
  }
}

/* 变更历史 */
.bdd-history {
  .bdd-history-head {
    margin-bottom: 4px;
  }
  .bdd-history-time {
    font-size: 12px;
    color: var(--color-text-3);
  }
  .bdd-history-content {
    font-size: 13px;
    line-height: 1.6;
  }
  .bdd-history-field {
    font-weight: 600;
  }
  .bdd-history-old {
    color: var(--color-text-3);
  }
  .bdd-history-new {
    color: rgb(var(--arcoblue-6));
  }
}

.severity-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
  font-weight: 500;
  &.fatal { background: #fee; color: #e11d48; }
  &.serious { background: #fff3e0; color: #f77; }
  &.normal { background: #e8f5e9; color: #4caf50; }
  &.tips { background: #f0f0f0; color: #999; }
}

/* 关联用例抽屉布局 */
.case-select-body {
  display: flex;
  gap: 12px;
  height: calc(100% - 52px);
  min-height: 0;
}

.case-select-side {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--color-border-2);
  padding-right: 8px;
}

.case-select-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.case-select-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;

  .selected-count {
    margin-left: auto;
    color: var(--color-text-2);
    font-size: 13px;
  }
}
</style>
