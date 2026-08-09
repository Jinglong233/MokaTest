<template>
  <div class="project-config-page">
    <div class="page-inner">
        <!-- 页面头 -->
        <div class="page-header">
          <div>
            <div class="page-title">项目配置</div>
            <div class="page-subtitle">
              当前项目：<a-tag color="arcoblue">{{ projectStore.getProjectName || '未选择项目' }}</a-tag>
              配置仅作用于本项目，新项目默认继承平台默认行为
            </div>
          </div>
          <a-space>
            <a-button v-permission="'project:config:update'" @click="resetAll">恢复默认</a-button>
            <a-badge :dot="dirty">
              <a-button v-permission="'project:config:update'" type="primary" :loading="saving" @click="saveConfig">保存配置</a-button>
            </a-badge>
          </a-space>
        </div>

        <a-alert v-if="dirty" type="warning" class="demo-alert" closable>
          有未保存的修改，离开或切换项目后将丢失，请及时保存。
        </a-alert>

        <a-tabs v-model:active-key="activeTab" class="config-tabs">
          <!-- ============ 通知配置 ============ -->
          <a-tab-pane key="notify" title="通知配置">
            <div class="tab-desc">
              通知场景由平台预定义，项目可控制：是否启用、通知哪些角色、自定义通知文案模板（支持变量占位）。
              未修改的场景保持平台默认。
            </div>
            <a-tabs v-model:active-key="activeNotifyBiz" type="card-gutter" class="biz-tabs">
              <a-tab-pane v-for="biz in bizList" :key="biz.key" :title="biz.label">
                <a-table
                  :data="rulesOf(biz.key)"
                  :pagination="false"
                  :bordered="{ cell: true }"
                  row-key="eventType"
                >
                  <template #columns>
                    <a-table-column title="场景" :width="140">
                      <template #cell="{ record }">
                        <div class="scene-name">{{ record.eventName }}</div>
                      </template>
                    </a-table-column>
                    <a-table-column title="说明" data-index="description" />
                    <a-table-column title="启用" :width="80" align="center">
                      <template #cell="{ record }">
                        <a-switch v-model="record.enabled" />
                      </template>
                    </a-table-column>
                    <a-table-column title="通知角色" :width="260">
                      <template #cell="{ record }">
                        <a-checkbox-group v-model="record.receivers" :disabled="!record.enabled">
                          <a-checkbox v-for="r in record.availableRoles" :key="r.value" :value="r.value">
                            {{ r.label }}
                          </a-checkbox>
                        </a-checkbox-group>
                        <div v-if="record.enabled && record.receivers.length === 0" class="receiver-warning">
                          未选择通知角色，该场景不会产生通知
                        </div>
                      </template>
                    </a-table-column>
                    <a-table-column title="通知模板" :width="130" align="center">
                      <template #cell="{ record }">
                        <a-space :size="4">
                          <a-button size="mini" :disabled="!record.enabled" @click="openTemplateEditor(record)">
                            {{ record.customized ? '已自定义' : '默认模板' }}
                          </a-button>
                          <a-tooltip v-if="record.customized" content="恢复平台默认模板">
                            <a-button size="mini" status="warning" @click="revertTemplate(record)">
                              <template #icon><IconUndo /></template>
                            </a-button>
                          </a-tooltip>
                        </a-space>
                      </template>
                    </a-table-column>
                  </template>
                </a-table>
                <div class="summary">
                  {{ biz.label }}：已启用 {{ rulesOf(biz.key).filter((r) => r.enabled).length }} /
                  {{ rulesOf(biz.key).length }} 个场景，
                  自定义模板 {{ rulesOf(biz.key).filter((r) => r.customized).length }} 个
                </div>
              </a-tab-pane>
            </a-tabs>
          </a-tab-pane>

          <!-- ============ 字段配置 ============ -->
          <a-tab-pane key="field" title="字段配置">
            <div class="tab-desc">
              控制各业务对象在本项目中的字段显隐，隐藏后该字段在<b>列表、编辑弹窗、预览抽屉</b>中均不展示（数据保留，重新开启即恢复）。
              核心字段与必填字段不可隐藏。
            </div>
            <a-tabs v-model:active-key="activeFieldBiz" type="card-gutter" class="biz-tabs">
              <a-tab-pane v-for="biz in bizList" :key="biz.key" :title="biz.label">
                <div class="field-grid">
                  <div
                    v-for="field in fieldMeta[biz.key]"
                    :key="field.key"
                    class="field-item"
                    :class="{ locked: field.locked, hidden: !field.visible }"
                  >
                    <a-checkbox
                      :model-value="field.visible"
                      :disabled="field.locked"
                      @change="(v: boolean | (string | number | boolean)[]) => (field.visible = v === true)"
                    >
                      {{ field.label }}
                    </a-checkbox>
                    <a-tag v-if="field.locked" size="small" color="orangered">核心</a-tag>
                    <a-tag v-else-if="!field.visible" size="small" color="gray">已隐藏</a-tag>
                  </div>
                </div>
                <div class="summary">
                  {{ biz.label }}：显示 {{ visibleCount(biz.key) }} / {{ fieldMeta[biz.key].length }} 个字段，
                  隐藏 {{ fieldMeta[biz.key].length - visibleCount(biz.key) }} 个
                </div>
              </a-tab-pane>
            </a-tabs>
          </a-tab-pane>
        </a-tabs>

        <!-- ============ 模板编辑弹窗 ============ -->
        <a-modal
          :visible="templateEditorVisible"
          :title="`自定义通知模板 — ${editingRule?.eventName || ''}`"
          :width="1080"
          :body-style="{ height: '440px', overflow: 'hidden' }"
          ok-text="应用"
          @ok="handleEditorOk"
          @cancel="handleEditorCancel"
        >
          <template v-if="editingRule">
            <div class="editor-preview-layout">
              <!-- 左：消息脚本列表 -->
              <div class="var-pane">
                <div class="var-pane-title">消息脚本</div>
                <div class="var-pane-tip">点击插入到内容模板</div>
                <div class="var-list">
                  <a-tooltip
                    v-for="v in visibleVariables"
                    :key="v.key"
                    :content="varTooltip(v)"
                    position="right"
                  >
                    <div class="var-item" role="button" tabindex="0" @click="insertVariable(v.key)" @keydown.enter="insertVariable(v.key)">
                      {{ v.label }}
                    </div>
                  </a-tooltip>
                </div>
                <div v-if="hiddenVariableCount > 0" class="var-hidden-tip">
                  {{ hiddenVariableCount }} 个变量因对应字段被隐藏而不可用
                </div>
              </div>

              <!-- 中：编辑区 -->
              <div class="editor-pane">
                <a-form layout="vertical" :model="{}">
                  <a-form-item label="标题模板">
                    <a-input v-model="editTitle" placeholder="通知标题" />
                  </a-form-item>
                  <a-form-item label="内容模板">
                    <a-textarea v-model="editContent" :auto-size="{ minRows: 8, maxRows: 12 }" placeholder="通知内容" />
                  </a-form-item>
                </a-form>
              </div>

              <!-- 右：消息预览（模拟站内信真实呈现） -->
              <div class="preview-pane">
                <div class="preview-pane-title">
                  消息预览
                  <span class="preview-pane-tip">变量已替换为示例值</span>
                </div>
                <div class="msg-card">
                  <div class="msg-card-header">
                    <div class="msg-card-icon" :class="`biz-${editingRule.bizType}`">
                      <IconBug v-if="editingRule.bizType === 'bug'" />
                      <IconBookmark v-else-if="editingRule.bizType === 'requirement'" />
                      <IconFile v-else />
                    </div>
                    <div class="msg-card-heading">
                      <div class="msg-card-title">{{ renderPreview(editTitle, editingRule) || '（标题为空）' }}</div>
                      <div class="msg-card-meta">
                        <a-tag size="small" :color="bizColor(editingRule.bizType)">{{ bizLabel(editingRule.bizType) }}</a-tag>
                        <span class="msg-card-time">{{ previewTime }}</span>
                      </div>
                    </div>
                    <span class="msg-unread-dot" title="未读"></span>
                  </div>
                  <div class="msg-card-body">{{ renderPreview(editContent, editingRule) || '（内容为空）' }}</div>
                </div>
                <a-alert v-if="unknownVars.length" type="warning" class="unknown-var-alert">
                  存在未识别变量：{{ unknownVarsText }}。
                  仅支持上方列出的变量，未识别变量发送时将替换为空。
                </a-alert>
              </div>
            </div>
          </template>
        </a-modal>
    </div>
  </div>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'ProjectConfigIndex' };
</script>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import { IconBookmark, IconBug, IconFile, IconUndo } from '@arco-design/web-vue/es/icon';
import { useProjectStore } from '@/store';
import useProjectConfigStore from '@/store/modules/projectConfig';
import { getProjectConfigList, resetProjectConfig, saveProjectConfigAll } from '@/api/MyApi/projectConfig';

const projectStore = useProjectStore();
const projectConfigStore = useProjectConfigStore();

/* ---------------- 通知场景（代码预定义，模板默认内置） ---------------- */

interface RoleOption {
  value: string;
  label: string;
}

interface TemplateVariable {
  key: string;
  label: string;
  sample: string;
  /** 关联的字段显隐字段 key：该字段被隐藏时变量不可用、发送时置空 */
  field?: string;
}

interface NotifyRule {
  eventType: string;
  bizType: 'bug' | 'requirement' | 'testCase';
  eventName: string;
  description: string;
  enabled: boolean;
  receivers: string[];
  /** 默认勾选的角色（用于差量判断，不随用户修改变化） */
  defaultReceivers: string[];
  availableRoles: RoleOption[];
  variables: TemplateVariable[];
  defaultTitle: string;
  defaultContent: string;
  title: string;
  content: string;
  customized: boolean;
}

const BUG_ROLES: RoleOption[] = [
  { value: 'REPORTER', label: '报告人' },
  { value: 'ASSIGNEE', label: '指派人' },
];
const REQ_ROLES: RoleOption[] = [
  { value: 'OWNER', label: '负责人' },
  { value: 'PARTICIPANT', label: '参与人' },
  { value: 'CREATOR', label: '创建人' },
];

const BUG_VARS: TemplateVariable[] = [
  { key: 'bugCode', label: 'BUG编号', sample: 'BUG-1024' },
  { key: 'bugTitle', label: 'BUG标题', sample: '登录页验证码不刷新' },
  { key: 'operatorName', label: '操作人', sample: '张三' },
  { key: 'severity', label: '严重程度', sample: '严重', field: 'severity' },
  { key: 'priority', label: '优先级', sample: '高', field: 'priority' },
  { key: 'environment', label: '环境', sample: '测试', field: 'environment' },
  { key: 'deadline', label: '截止日期', sample: '2026-07-25', field: 'deadline' },
  { key: 'foundVersion', label: '发现版本', sample: 'v1.2.0', field: 'foundVersion' },
  { key: 'fixedVersion', label: '修复版本', sample: 'v1.2.1', field: 'fixedVersion' },
  { key: 'oldStatus', label: '原状态', sample: '新建' },
  { key: 'newStatus', label: '新状态', sample: '修复中' },
  { key: 'projectName', label: '项目名称', sample: '商城系统' },
  { key: 'moduleName', label: '所属模块', sample: '用户中心', field: 'moduleId' },
  { key: 'commentContent', label: '评论内容', sample: '这里空指针了，建议加个判空…' },
  { key: 'operateTime', label: '操作时间', sample: '2026-07-18 14:30:25' },
];
const REQ_VARS: TemplateVariable[] = [
  { key: 'reqCode', label: '需求编号', sample: 'REQ-2056' },
  { key: 'reqTitle', label: '需求标题', sample: '订单支持批量导出' },
  { key: 'operatorName', label: '操作人', sample: '李四' },
  { key: 'priority', label: '优先级', sample: 'P1', field: 'priority' },
  { key: 'oldStatus', label: '原状态', sample: '评审中' },
  { key: 'newStatus', label: '新状态', sample: '已确认' },
  { key: 'projectName', label: '项目名称', sample: '商城系统' },
  { key: 'moduleName', label: '所属模块', sample: '订单系统', field: 'moduleId' },
  { key: 'operateTime', label: '操作时间', sample: '2026-07-18 14:30:25' },
];
const CASE_VARS: TemplateVariable[] = [
  { key: 'caseCode', label: '用例编号', sample: 'CASE-3089' },
  { key: 'caseName', label: '用例名称', sample: '支付超时重试验证' },
  { key: 'operatorName', label: '操作人', sample: '王五' },
  { key: 'oldStatus', label: '原状态', sample: '草稿' },
  { key: 'newStatus', label: '新状态', sample: '已评审' },
  { key: 'projectName', label: '项目名称', sample: '商城系统' },
  { key: 'moduleName', label: '所属模块', sample: '支付系统', field: 'moduleId' },
];

function rule(
  partial: Omit<NotifyRule, 'title' | 'content' | 'customized' | 'defaultReceivers'>
): NotifyRule {
  return {
    ...partial,
    defaultReceivers: [...partial.receivers],
    title: partial.defaultTitle,
    content: partial.defaultContent,
    customized: false,
  };
}

function buildDefaultRules(): NotifyRule[] {
  return [
    rule({
      eventType: 'BUG_CREATED', bizType: 'bug', eventName: 'BUG 创建',
      description: '有新 BUG 创建时，通知指派人',
      enabled: true, receivers: ['ASSIGNEE'], availableRoles: BUG_ROLES,
      variables: BUG_VARS.filter((v) => ['bugCode', 'bugTitle', 'operatorName', 'severity', 'priority', 'projectName', 'moduleName', 'environment', 'deadline', 'foundVersion'].includes(v.key)),
      defaultTitle: '新BUG待处理',
      defaultContent: 'BUG ${bugCode}「${bugTitle}」已由 ${operatorName} 创建并指派给您，严重程度：${severity}',
    }),
    rule({
      eventType: 'BUG_ASSIGNED', bizType: 'bug', eventName: 'BUG 指派',
      description: 'BUG 被转派时，通知新指派人',
      enabled: true, receivers: ['ASSIGNEE'], availableRoles: BUG_ROLES,
      variables: BUG_VARS.filter((v) => ['bugCode', 'bugTitle', 'operatorName', 'severity', 'priority', 'projectName', 'moduleName', 'environment', 'deadline', 'foundVersion'].includes(v.key)),
      defaultTitle: 'BUG指派通知',
      defaultContent: 'BUG ${bugCode}「${bugTitle}」已被 ${operatorName} 指派给您',
    }),
    rule({
      eventType: 'BUG_STATUS_CHANGED', bizType: 'bug', eventName: 'BUG 状态变更',
      description: 'BUG 状态发生流转时，通知报告人和指派人',
      enabled: true, receivers: ['REPORTER', 'ASSIGNEE'], availableRoles: BUG_ROLES,
      variables: BUG_VARS.filter((v) => ['bugCode', 'bugTitle', 'operatorName', 'severity', 'oldStatus', 'newStatus', 'projectName', 'moduleName', 'environment', 'deadline', 'foundVersion', 'fixedVersion'].includes(v.key)),
      defaultTitle: 'BUG状态变更',
      defaultContent: 'BUG ${bugCode}「${bugTitle}」状态由 ${oldStatus} 变更为 ${newStatus}',
    }),
    rule({
      eventType: 'BUG_UPDATED', bizType: 'bug', eventName: 'BUG 更新',
      description: 'BUG 信息被编辑时，通知报告人和指派人',
      enabled: true, receivers: ['REPORTER', 'ASSIGNEE'], availableRoles: BUG_ROLES,
      variables: BUG_VARS.filter((v) => ['bugCode', 'bugTitle', 'operatorName', 'severity', 'projectName', 'moduleName', 'environment', 'deadline', 'foundVersion'].includes(v.key)),
      defaultTitle: 'BUG更新通知',
      defaultContent: 'BUG ${bugCode}「${bugTitle}」的信息已被 ${operatorName} 更新',
    }),
    rule({
      eventType: 'BUG_DELETED', bizType: 'bug', eventName: 'BUG 删除',
      description: 'BUG 被删除时，通知报告人和指派人',
      enabled: true, receivers: ['REPORTER', 'ASSIGNEE'], availableRoles: BUG_ROLES,
      variables: BUG_VARS.filter((v) => ['bugCode', 'bugTitle', 'operatorName', 'severity', 'projectName', 'moduleName', 'environment', 'deadline', 'foundVersion', 'operateTime'].includes(v.key)),
      defaultTitle: 'BUG删除通知',
      defaultContent: 'BUG ${bugCode}「${bugTitle}」已被 ${operatorName} 删除',
    }),
    rule({
      eventType: 'BUG_COMMENT_MENTION', bizType: 'bug', eventName: '评论 @ 我',
      description: '在 BUG 评论中被 @ 时通知',
      enabled: true, receivers: ['MENTION'],
      availableRoles: [{ value: 'MENTION', label: '被@人' }, ...BUG_ROLES],
      variables: BUG_VARS.filter((v) => ['bugCode', 'bugTitle', 'operatorName', 'commentContent', 'severity', 'projectName', 'moduleName', 'environment', 'deadline', 'foundVersion'].includes(v.key)),
      defaultTitle: 'BUG评论提及',
      defaultContent: '${operatorName} 在 BUG ${bugCode}「${bugTitle}」的评论中@了您',
    }),
    rule({
      eventType: 'REQ_CREATED', bizType: 'requirement', eventName: '需求创建',
      description: '有新需求创建时，通知负责人',
      enabled: true, receivers: ['OWNER'], availableRoles: REQ_ROLES,
      variables: REQ_VARS.filter((v) => ['reqCode', 'reqTitle', 'operatorName', 'priority', 'projectName', 'moduleName'].includes(v.key)),
      defaultTitle: '新需求待处理',
      defaultContent: '需求 ${reqCode}「${reqTitle}」已由 ${operatorName} 创建，优先级：${priority}，您是负责人',
    }),
    rule({
      eventType: 'REQ_ASSIGNED', bizType: 'requirement', eventName: '需求指派',
      description: '需求被转派时，通知新负责人',
      enabled: true, receivers: ['OWNER'], availableRoles: REQ_ROLES,
      variables: REQ_VARS.filter((v) => ['reqCode', 'reqTitle', 'operatorName', 'priority', 'projectName', 'moduleName'].includes(v.key)),
      defaultTitle: '需求指派通知',
      defaultContent: '需求 ${reqCode}「${reqTitle}」已被 ${operatorName} 指派给您',
    }),
    rule({
      eventType: 'REQ_STATUS_CHANGED', bizType: 'requirement', eventName: '需求状态变更',
      description: '需求状态发生流转时，通知负责人、参与人和创建人',
      enabled: true, receivers: ['OWNER', 'PARTICIPANT', 'CREATOR'], availableRoles: REQ_ROLES,
      variables: REQ_VARS.filter((v) => ['reqCode', 'reqTitle', 'operatorName', 'priority', 'oldStatus', 'newStatus', 'projectName', 'moduleName'].includes(v.key)),
      defaultTitle: '需求状态变更',
      defaultContent: '需求 ${reqCode}「${reqTitle}」状态由 ${oldStatus} 变更为 ${newStatus}',
    }),
    rule({
      eventType: 'REQ_UPDATED', bizType: 'requirement', eventName: '需求更新',
      description: '需求信息被编辑时，通知负责人和参与人',
      enabled: true, receivers: ['OWNER', 'PARTICIPANT'], availableRoles: REQ_ROLES,
      variables: REQ_VARS.filter((v) => ['reqCode', 'reqTitle', 'operatorName', 'priority', 'projectName', 'moduleName'].includes(v.key)),
      defaultTitle: '需求更新通知',
      defaultContent: '需求 ${reqCode}「${reqTitle}」的信息已被 ${operatorName} 更新',
    }),
    rule({
      eventType: 'REQ_DELETED', bizType: 'requirement', eventName: '需求删除',
      description: '需求被删除时，通知负责人和参与人',
      enabled: true, receivers: ['OWNER', 'PARTICIPANT'], availableRoles: REQ_ROLES,
      variables: REQ_VARS.filter((v) => ['reqCode', 'reqTitle', 'operatorName', 'projectName', 'moduleName', 'operateTime'].includes(v.key)),
      defaultTitle: '需求删除通知',
      defaultContent: '需求 ${reqCode}「${reqTitle}」已被 ${operatorName} 删除',
    }),
    rule({
      eventType: 'CASE_CREATED', bizType: 'testCase', eventName: '用例创建',
      description: '有用例创建时，通知创建人（预留事件）',
      enabled: true, receivers: ['CREATOR'], availableRoles: [{ value: 'CREATOR', label: '创建人' }],
      variables: CASE_VARS.filter((v) => ['caseCode', 'caseName', 'operatorName', 'projectName', 'moduleName'].includes(v.key)),
      defaultTitle: '用例创建通知',
      defaultContent: '用例 ${caseCode}「${caseName}」已由 ${operatorName} 创建',
    }),
    rule({
      eventType: 'CASE_STATUS_CHANGED', bizType: 'testCase', eventName: '用例状态变更',
      description: '用例状态发生流转时，通知创建人（预留事件）',
      enabled: true, receivers: ['CREATOR'], availableRoles: [{ value: 'CREATOR', label: '创建人' }],
      variables: CASE_VARS,
      defaultTitle: '用例状态变更',
      defaultContent: '用例 ${caseCode}「${caseName}」状态由 ${oldStatus} 变更为 ${newStatus}',
    }),
  ];
}

/* ---------------- 字段元数据 ---------------- */

interface FieldMeta {
  key: string;
  label: string;
  locked: boolean;
  visible: boolean;
}

function f(key: string, label: string, locked = false): FieldMeta {
  return { key, label, locked, visible: true };
}

function buildDefaultFields(): Record<string, FieldMeta[]> {
  return {
    bug: [
      f('bugCode', '编号', true),
      f('title', '标题', true),
      f('status', '状态', true),
      f('severity', '严重程度', true),
      f('priority', '优先级'),
      f('moduleId', '所属模块'),
      f('environment', '环境'),
      f('deadline', '截止日期'),
      f('foundVersion', '发现版本'),
      f('fixedVersion', '修复版本'),
      f('reproduceRate', '重现概率'),
      f('closeReason', '关闭原因'),
      f('tags', '标签'),
      f('requirementId', '关联需求'),
      f('testCaseId', '关联用例'),
      f('assigneeId', '指派人'),
      f('reproduceSteps', '重现步骤'),
    ],
    requirement: [
      f('reqCode', '编号', true),
      f('title', '标题', true),
      f('status', '状态', true),
      f('priority', '优先级', true),
      f('moduleId', '所属模块'),
      f('parentId', '父需求'),
      f('reqType', '需求类型'),
      f('source', '来源'),
      f('participants', '参与人'),
      f('expectReleaseTime', '期望上线时间'),
      f('tags', '标签'),
      f('version', '版本'),
      f('ownerId', '负责人'),
    ],
    testCase: [
      f('caseCode', '编号', true),
      f('caseName', '用例名称', true),
      f('status', '状态', true),
      f('priority', '优先级'),
      f('caseType', '用例类型'),
      f('moduleId', '所属模块'),
      f('preCondition', '前置条件'),
      f('testSteps', '测试步骤', true),
      f('lastResult', '最近执行结果'),
      f('lastExecuteTime', '最近执行时间'),
      f('expectDuration', '预期执行时长'),
      f('tags', '标签'),
      f('requirementId', '关联需求'),
    ],
  };
}

/* ---------------- 状态与持久化（project_config 差量存储，无记录 = 平台默认） ---------------- */

const activeTab = ref('notify');
const activeNotifyBiz = ref('bug');
const activeFieldBiz = ref('bug');
const bizList = [
  { key: 'bug', label: 'BUG' },
  { key: 'requirement', label: '需求' },
  { key: 'testCase', label: '用例' },
];

/** 前端业务对象 key → 后端 FIELD_VISIBLE config_key */
const BIZ_CONFIG_KEY: Record<string, string> = {
  bug: 'bug',
  requirement: 'requirement',
  testCase: 'test_case',
};

const notifyRules = reactive<NotifyRule[]>(buildDefaultRules());
const fieldMeta = reactive<Record<string, FieldMeta[]>>(buildDefaultFields());

function rulesOf(biz: string) {
  return notifyRules.filter((r) => r.bizType === biz);
}

/** 未保存修改标记（loadConfig 期间的变更不计入） */
const dirty = ref(false);
const saving = ref(false);
let configLoading = false;

/** 把后端差量记录应用到页面状态 */
function applyConfigRows(rows: any[]) {
  (rows || []).forEach((row) => {
    if (!row?.configValue) return;
    let value: any;
    try {
      value = JSON.parse(row.configValue);
    } catch {
      return;
    }
    if (row.configType === 'NOTIFY_RULE') {
      const r = notifyRules.find((x) => x.eventType === row.configKey);
      if (!r) return;
      r.enabled = value?.enabled !== false;
      // 规则中的 receivers 为 {ROLE: bool} 映射，显式配置优先，未配置的角色按场景默认勾选
      const receiverMap = value?.receivers || {};
      r.receivers = r.availableRoles
        .map((role) => role.value)
        .filter((roleValue) =>
          roleValue in receiverMap
            ? receiverMap[roleValue] === true
            : r.defaultReceivers.includes(roleValue)
        );
      if (value?.titleTemplate != null || value?.contentTemplate != null) {
        r.title = value.titleTemplate ?? r.defaultTitle;
        r.content = value.contentTemplate ?? r.defaultContent;
        r.customized = true;
      }
    } else if (row.configType === 'FIELD_VISIBLE') {
      const biz = Object.keys(BIZ_CONFIG_KEY).find((k) => BIZ_CONFIG_KEY[k] === row.configKey);
      const hidden: string[] = Array.isArray(value?.hiddenFields) ? value.hiddenFields : [];
      hidden.forEach((key) => {
        const field = biz ? fieldMeta[biz]?.find((fd) => fd.key === key) : null;
        if (field) field.visible = false;
      });
    }
  });
}

async function loadConfig() {
  configLoading = true;
  Object.assign(notifyRules, buildDefaultRules());
  const freshFields = buildDefaultFields();
  Object.keys(freshFields).forEach((k) => {
    fieldMeta[k] = freshFields[k];
  });
  try {
    const projectId = projectStore.getProjectId;
    if (projectId) {
      const res: any = await getProjectConfigList(projectId);
      applyConfigRows((res?.data as any[]) || []);
    }
  } catch {
    // 拉取失败按默认行为展示，但明确提示用户：此时保存会覆盖线上配置
    Message.warning('项目配置加载失败，当前展示为平台默认值；为避免误覆盖，请刷新重试后再修改');
  } finally {
    nextTick(() => {
      configLoading = false;
      dirty.value = false;
    });
  }
}

watch(
  [notifyRules, fieldMeta],
  () => {
    if (!configLoading) dirty.value = true;
  },
  { deep: true }
);

/** 构造后端差量记录：仅与默认有差异的项才生成记录 */
function buildConfigRows() {
  const rows: any[] = [];
  const sameSet = (a: string[], b: string[]) =>
    a.length === b.length && a.every((x) => b.includes(x));
  notifyRules
    .filter((r) => !r.enabled || !sameSet(r.receivers, r.defaultReceivers) || r.customized)
    .forEach((r) => {
      const receivers: Record<string, boolean> = {};
      r.availableRoles.forEach((role) => {
        receivers[role.value] = r.receivers.includes(role.value);
      });
      rows.push({
        configType: 'NOTIFY_RULE',
        configKey: r.eventType,
        configValue: JSON.stringify({
          enabled: r.enabled,
          receivers,
          ...(r.customized ? { titleTemplate: r.title, contentTemplate: r.content } : {}),
        }),
      });
    });
  Object.entries(fieldMeta).forEach(([biz, fields]) => {
    const hidden = fields.filter((fd) => !fd.visible).map((fd) => fd.key);
    if (hidden.length > 0) {
      rows.push({
        configType: 'FIELD_VISIBLE',
        configKey: BIZ_CONFIG_KEY[biz],
        configValue: JSON.stringify({ hiddenFields: hidden }),
      });
    }
  });
  return rows;
}

/** 保存到指定项目并刷新 store 缓存 */
async function persistTo(projectId: number | null) {
  if (!projectId) return;
  await saveProjectConfigAll(projectId, buildConfigRows());
  await projectConfigStore.load(projectId, true);
}

async function saveConfig() {
  if (!projectStore.getProjectId) {
    Message.warning('请先选择项目');
    return;
  }
  saving.value = true;
  try {
    await persistTo(projectStore.getProjectId);
    dirty.value = false;
    Message.success('配置已保存');
  } catch {
    // 拦截器已提示
  } finally {
    saving.value = false;
  }
}

function resetAll() {
  Modal.confirm({
    title: '恢复默认配置',
    content: '将清空本项目的全部自定义通知规则和字段显隐配置，恢复为平台默认行为。确定继续吗？',
    okButtonProps: { status: 'warning' },
    onOk: async () => {
      if (!projectStore.getProjectId) return;
      try {
        await resetProjectConfig(projectStore.getProjectId);
        await projectConfigStore.load(projectStore.getProjectId, true);
        await loadConfig();
        Message.success('已恢复平台默认配置');
      } catch {
        // 拦截器已提示
      }
    },
  });
}

watch(
  () => projectStore.getProjectId,
  (newId, oldId) => {
    // 有未保存修改时切换项目：提示保存到原项目或丢弃
    if (dirty.value && oldId && newId !== oldId) {
      Modal.confirm({
        title: '未保存的修改',
        content: '当前项目的配置有未保存的修改，切换项目后将丢失。是否保存到原项目后再切换？',
        okText: '保存并切换',
        cancelText: '丢弃修改',
        onOk: async () => {
          try {
            await persistTo(oldId);
          } catch {
            // 拦截器已提示
          }
          loadConfig();
        },
        onCancel: () => {
          loadConfig();
        },
      });
      return;
    }
    loadConfig();
  },
  { immediate: true }
);

/* ---------------- 模板编辑 ---------------- */

const templateEditorVisible = ref(false);
const editingRule = ref<NotifyRule | null>(null);
const editTitle = ref('');
const editContent = ref('');

function openTemplateEditor(r: NotifyRule) {
  editingRule.value = r;
  editTitle.value = r.title;
  editContent.value = r.content;
  templateEditorVisible.value = true;
}

/** 变量悬浮提示：中文名 + 脚本编码 + 示例值（预计算，避免模板里嵌套模板字符串） */
function varTooltip(v: TemplateVariable) {
  return v.label + '　${' + v.key + '}（示例：' + v.sample + '）';
}

function insertVariable(key: string) {
  editContent.value += `\${${key}}`;
}

/** 变量是否因对应字段被隐藏而不可用（与后端发送置空逻辑一致） */
function isVariableHidden(biz: string, v: TemplateVariable) {
  if (!v.field) return false;
  const field = fieldMeta[biz]?.find((fd) => fd.key === v.field);
  return field ? !field.visible : false;
}

/** 当前编辑场景可用的变量（过滤掉字段被隐藏的） */
const visibleVariables = computed(() => {
  if (!editingRule.value) return [];
  return editingRule.value.variables.filter((v) => !isVariableHidden(editingRule.value!.bizType, v));
});

/** 被字段显隐隐藏掉的变量数量（用于列表底部提示） */
const hiddenVariableCount = computed(() => {
  if (!editingRule.value) return 0;
  return editingRule.value.variables.length - visibleVariables.value.length;
});

function renderPreview(tpl: string, r: NotifyRule) {
  let out = tpl;
  r.variables.forEach((v) => {
    // 字段被隐藏的变量发送时置空，预览同步呈现为空
    out = out.split(`\${${v.key}}`).join(isVariableHidden(r.bizType, v) ? '' : v.sample);
  });
  return out;
}

const previewTime = computed(() =>
  templateEditorVisible.value
    ? new Date().toLocaleString('zh-CN', { hour12: false })
    : ''
);

/** 模板中未在变量白名单内的 ${xxx} 占位符 */
const unknownVars = computed(() => {
  if (!editingRule.value) return [];
  const known = new Set(editingRule.value.variables.map((v) => v.key));
  const found = new Set<string>();
  [editTitle.value, editContent.value].forEach((tpl) => {
    (tpl.match(/\$\{([^}]+)\}/g) || []).forEach((m) => {
      const key = m.slice(2, -1);
      if (!known.has(key)) found.add(key);
    });
  });
  return [...found];
});

const unknownVarsText = computed(() =>
  unknownVars.value.map((k) => '${' + k + '}').join('、')
);

function bizLabel(biz: string) {
  return bizList.find((b) => b.key === biz)?.label || biz;
}

function bizColor(biz: string) {
  return { bug: 'red', requirement: 'arcoblue', testCase: 'green' }[biz] || 'gray';
}

function applyTemplate() {
  if (!editingRule.value) return;
  editingRule.value.title = editTitle.value;
  editingRule.value.content = editContent.value;
  editingRule.value.customized =
    editTitle.value !== editingRule.value.defaultTitle ||
    editContent.value !== editingRule.value.defaultContent;
  Message.success(editingRule.value.customized ? '已应用自定义模板（保存后生效）' : '与默认模板一致，按默认处理');
}

function handleEditorOk() {
  applyTemplate();
  templateEditorVisible.value = false;
}

/** 模板弹窗取消拦截：内容已修改但未应用时二次确认 */
function handleEditorCancel() {
  const r = editingRule.value;
  if (!r) {
    templateEditorVisible.value = false;
    return;
  }
  const modified = editTitle.value !== r.title || editContent.value !== r.content;
  if (!modified) {
    templateEditorVisible.value = false;
    return;
  }
  Modal.confirm({
    title: '关闭模板编辑',
    content: '模板内容已修改但尚未应用，关闭后将丢失本次修改。确定关闭吗？',
    okText: '丢弃并关闭',
    okButtonProps: { status: 'warning' },
    onOk: () => {
      templateEditorVisible.value = false;
    },
  });
}

function revertTemplate(r: NotifyRule) {
  r.title = r.defaultTitle;
  r.content = r.defaultContent;
  r.customized = false;
  Message.info(`「${r.eventName}」已恢复默认模板`);
}

/* ---------------- 字段统计 ---------------- */

function visibleCount(biz: string) {
  return fieldMeta[biz].filter((fd) => fd.visible).length;
}
</script>

<style scoped>
.project-config-page {
  background: var(--color-fill-2);
  height: var(--page-container-height, calc(100vh - 60px));
}

.page-inner {
  height: 100%;
  box-sizing: border-box;
  padding: 20px 24px 16px;
  display: flex;
  flex-direction: column;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-1);
}

.page-subtitle {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-text-3);
}

.demo-alert {
  margin-bottom: 16px;
}

.config-tabs {
  flex: 1;
  min-height: 0;
  background: var(--color-bg-2);
  padding: 16px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
}

.config-tabs :deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
  padding-top: 8px;
}

.config-tabs :deep(.arco-tabs-content-list),
.config-tabs :deep(.arco-tabs-pane) {
  height: 100%;
}

.config-tabs :deep(.arco-tabs-pane) {
  overflow-y: auto;
}

.tab-desc {
  font-size: 13px;
  color: var(--color-text-3);
  margin-bottom: 16px;
  line-height: 1.6;
}

.biz-tabs {
  margin-top: 4px;
}

.scene-name {
  font-weight: 500;
  color: var(--color-text-1);
}

.summary {
  position: sticky;
  bottom: 0;
  margin-top: 12px;
  padding: 10px 0 2px;
  font-size: 13px;
  color: var(--color-text-3);
  background: var(--color-bg-2);
  border-top: 1px dashed var(--color-border-2);
}

.receiver-warning {
  margin-top: 4px;
  font-size: 12px;
  color: rgb(var(--orange-6));
}

.var-pane {
  width: 150px;
  flex-shrink: 0;
  border: 1px solid var(--color-border-2);
  background: var(--color-fill-1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.var-pane-title {
  padding: 10px 12px 2px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.var-pane-tip {
  padding: 0 12px 8px;
  font-size: 12px;
  color: var(--color-text-4);
}

.var-list {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}

.var-list::-webkit-scrollbar {
  width: 6px;
}

.var-list::-webkit-scrollbar-thumb {
  background: var(--color-fill-4);
}

.var-list::-webkit-scrollbar-track {
  background: transparent;
}

.var-item {
  padding: 7px 10px;
  margin-bottom: 6px;
  border: 1px solid var(--color-border-2);
  background: var(--color-bg-2);
  font-size: 13px;
  color: var(--color-text-1);
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}

.var-item:hover {
  border-color: rgb(var(--arcoblue-6));
  color: rgb(var(--arcoblue-6));
}

.var-item:focus-visible {
  outline: 2px solid rgb(var(--primary-6));
  outline-offset: -2px;
}

.var-hidden-tip {
  padding: 6px 12px 8px;
  font-size: 12px;
  color: rgb(var(--orange-6));
  border-top: 1px dashed var(--color-border-2);
}

.unknown-var-alert {
  margin-top: 10px;
}

.editor-preview-layout {
  display: flex;
  gap: 20px;
  height: 100%;
}

.editor-pane {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.preview-pane {
  width: 380px;
  flex-shrink: 0;
  overflow-y: auto;
}

.preview-pane-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  margin-bottom: 10px;
}

.preview-pane-tip {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-4);
}

.msg-card {
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  overflow: hidden;
}

.msg-card-header {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 14px 10px;
}

.msg-card-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.msg-card-icon.biz-bug {
  color: rgb(var(--red-6));
  background: rgb(var(--red-1));
}

.msg-card-icon.biz-requirement {
  color: rgb(var(--arcoblue-6));
  background: rgb(var(--arcoblue-1));
}

.msg-card-icon.biz-testCase {
  color: rgb(var(--green-6));
  background: rgb(var(--green-1));
}

.msg-card-heading {
  flex: 1;
  min-width: 0;
}

.msg-card-title {
  font-weight: 600;
  color: var(--color-text-1);
  line-height: 1.4;
  word-break: break-all;
}

.msg-card-meta {
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 8px;
}

.msg-card-time {
  font-size: 12px;
  color: var(--color-text-4);
}

.msg-unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgb(var(--red-6));
  flex-shrink: 0;
  margin-top: 4px;
}

.msg-card-body {
  padding: 0 14px 14px 60px;
  color: var(--color-text-2);
  line-height: 1.6;
  word-break: break-all;
  white-space: pre-wrap;
}

.field-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
}

.field-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.field-item.locked {
  background: var(--color-fill-1);
}

.field-item :deep(.arco-tag) {
  margin-left: auto;
}

.field-item.hidden {
  opacity: 0.65;
  border-style: dashed;
}
</style>
