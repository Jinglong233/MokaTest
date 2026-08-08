<template>
  <div class="operation-log-page">
    <Breadcrumb :items="['menu.system', 'menu.system.operationLog']" />
    <a-card class="general-card" title="操作日志">
      <!-- 筛选栏 -->
      <a-row class="toolbar" justify="space-between" style="margin-bottom: 16px;">
        <a-space wrap>
          <a-select
            v-model="searchModule"
            placeholder="所属模块"
            allow-clear
            style="width: 140px"
          >
            <a-option v-for="opt in moduleOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-option>
          </a-select>
          <a-select
            v-model="searchType"
            placeholder="操作类型"
            allow-clear
            style="width: 140px"
          >
            <a-option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-option>
          </a-select>
          <a-select
            v-model="searchTargetType"
            placeholder="对象类型"
            allow-clear
            style="width: 140px"
          >
            <a-option v-for="opt in targetTypeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-option>
          </a-select>
          <a-select
            v-model="searchOperatorId"
            placeholder="操作人"
            allow-clear
            allow-search
            :filter-option="false"
            :loading="userLoading"
            style="width: 160px"
            @search="handleSearchUser"
            @dropdown-visible-change="handleUserDropdownOpen"
          >
            <a-option v-for="opt in userOptions" :key="opt.value" :value="opt.value">{{ opt.label }}</a-option>
          </a-select>
          <a-input
            v-model="searchKeyword"
            placeholder="搜索描述或对象名称"
            allow-clear
            style="width: 220px"
          />
          <a-range-picker
            v-model="searchTimeRange"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
          />
          <a-button type="primary" @click="handleSearch">
            <template #icon><icon-search /></template>
            查询
          </a-button>
          <a-button @click="handleReset">重置</a-button>
        </a-space>
      </a-row>

      <!-- 表格 -->
      <LoadError v-if="loadError" @retry="loadList" />
      <a-table
        v-else
        :data="logList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        stripe
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="操作时间" data-index="operateTime" :width="165" />
          <a-table-column title="操作人" data-index="operatorName" :width="100">
            <template #cell="{ record }">
              <span>{{ record.operatorName || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" data-index="operateType" :width="120">
            <template #cell="{ record }">
              <a-space>
                <a-tag size="small">{{ moduleText(record.module) }}</a-tag>
                <a-tag :color="typeColor(record.operateType)" size="small">{{ typeText(record.operateType, record.targetType) }}</a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="对象" data-index="targetName" :width="180">
            <template #cell="{ record }">
              <a-space direction="vertical" size="mini" fill>
                <span v-if="record.targetName" class="text-ellipsis" :title="record.targetName">{{ record.targetName }}</span>
                <span v-else style="color: #86909c;">-</span>
                <a-tag v-if="record.targetType" size="mini" style="width: fit-content;">
                  {{ targetTypeText(record.targetType) }}
                  <span v-if="record.targetId && !isMemberType(record.targetType)">#{{ record.targetId }}</span>
                </a-tag>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="变更摘要" data-index="description">
            <template #cell="{ record }">
              <ChangeSummary :description="record.description" :operate-type="record.operateType" />
            </template>
          </a-table-column>
          <a-table-column title="结果" data-index="responseCode" :width="110">
            <template #cell="{ record }">
              <a-space>
                <a-tag v-if="record.responseCode === 200" color="green" size="small">成功</a-tag>
                <a-tag v-else-if="record.responseCode" color="red" size="small">失败</a-tag>
                <span v-else style="color: #86909c;">-</span>
                <span v-if="record.durationMs" style="color: #86909c; font-size: 12px;">{{ record.durationMs }}ms</span>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="90" fixed="right">
            <template #cell="{ record }">
              <a-button type="text" size="small" @click="openDetail(record)">
                <template #icon><icon-eye /></template>
                详情
              </a-button>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>

    <!-- 详情抽屉 -->
    <a-drawer
      v-model:visible="detailVisible"
      :width="960"
      :footer="false"
      :title="null"
      unmount-on-close
    >
      <template #header>
        <div style="display: flex; align-items: center; justify-content: space-between; width: 100%;">
          <span style="font-size: 16px; font-weight: 600;">操作详情</span>
          <a-tag v-if="detailData.responseCode" :color="detailData.responseCode === 200 ? 'green' : 'red'" size="small">
            {{ detailData.responseCode === 200 ? '成功' : '失败' }}
          </a-tag>
        </div>
      </template>

      <div v-if="detailData.id" class="detail-body">
        <!-- 头部信息 -->
        <div class="detail-header">
          <div class="detail-meta-row">
            <span class="detail-meta-label">操作时间</span>
            <span class="detail-meta-value">{{ detailData.operateTime }}</span>
          </div>
          <div class="detail-meta-row">
            <span class="detail-meta-label">操作人</span>
            <span class="detail-meta-value">{{ detailData.operatorName || '-' }}</span>
          </div>
          <div class="detail-meta-row">
            <span class="detail-meta-label">操作</span>
            <span class="detail-meta-value">
              <a-space>
                <a-tag size="small">{{ moduleText(detailData.module) }}</a-tag>
                <a-tag :color="typeColor(detailData.operateType)" size="small">{{ typeText(detailData.operateType, detailData.targetType) }}</a-tag>
              </a-space>
            </span>
          </div>
          <div class="detail-meta-row">
            <span class="detail-meta-label">对象</span>
            <span class="detail-meta-value">
              <span v-if="detailData.targetName">{{ detailData.targetName }}</span>
              <span v-else style="color: #86909c;">-</span>
              <a-tag v-if="detailData.targetType" size="mini" style="margin-left: 8px;">
                {{ targetTypeText(detailData.targetType) }}<span v-if="!isMemberType(detailData.targetType)">#{{ detailData.targetId || '-' }}</span>
              </a-tag>
            </span>
          </div>
          <div class="detail-meta-row">
            <span class="detail-meta-label">耗时</span>
            <span class="detail-meta-value">{{ detailData.durationMs ? detailData.durationMs + 'ms' : '-' }}</span>
          </div>
          <div class="detail-meta-row">
            <span class="detail-meta-label">IP</span>
            <span class="detail-meta-value" style="font-family: monospace;">{{ detailData.ip || '-' }}</span>
          </div>
        </div>

        <a-divider style="margin: 16px 0;" />

        <!-- 创建内容 / 删除前数据 —— 弹窗式表单布局 -->
        <div v-if="isFieldList(detailData.description)" class="detail-section">
          <div class="detail-section-title">
            <icon-plus v-if="detailData.operateType === 'CREATE'" />
            <icon-delete v-else-if="detailData.operateType === 'DELETE'" />
            <icon-file v-else />
            <span>{{ detailData.operateType === 'DELETE' ? '删除前数据' : '创建内容' }}</span>
          </div>
          <div class="log-form-panel">
            <div v-for="group in groupedFieldList" :key="group.name">
              <a-divider orientation="left" class="log-divider">
                <span class="log-group-title">{{ group.name }}</span>
              </a-divider>
              <div v-for="item in group.items" :key="item.field" class="log-form-item">
                <div class="log-form-label">{{ item.label || item.field }}</div>
                <div class="log-form-value">
                  <!-- 测试步骤 -->
                  <div v-if="item.field === 'testSteps'" class="steps-preview">
                    <div v-for="(step, idx) in parseSteps(item.value)" :key="idx" class="step-card">
                      <div class="step-card-header">步骤 {{ idx + 1 }}</div>
                      <div class="step-card-body">
                        <div class="step-row">
                          <span class="step-row-label">步骤描述</span>
                          <div class="step-row-content" v-html="sanitizeHtml(step.step)"></div>
                        </div>
                        <div class="step-row">
                          <span class="step-row-label">预期结果</span>
                          <div class="step-row-content" v-html="sanitizeHtml(step.expected)"></div>
                        </div>
                      </div>
                    </div>
                  </div>
                  <!-- 富文本 -->
                  <div v-else-if="isRichTextField(item.field)" v-html="sanitizeHtml(item.value)" class="rich-text-preview"></div>
                  <!-- 普通字段 -->
                  <span v-else-if="item.displayValue">{{ item.displayValue }}</span>
                  <span v-else-if="item.value !== undefined && item.value !== null && item.value !== ''">{{ formatFieldValue(item.field, item.value) }}</span>
                  <span v-else class="field-empty">（空）</span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 字段变更对比 —— 按字段配对行，左右天然对齐 -->
        <div v-else-if="hasFieldChanges(detailData.description)" class="detail-section">
          <div class="detail-section-title">
            <icon-swap />
            <span>字段变更</span>
          </div>

          <div class="log-compare-panel">
            <!-- 表头：修改前 / 修改后 -->
            <div class="log-compare-head">
              <div class="log-panel-header log-panel-header-old">修改前</div>
              <div class="log-panel-header log-panel-header-new">修改后</div>
            </div>
            <div class="log-panel-body">
              <template v-for="group in groupedChanges" :key="group.name">
                <a-divider orientation="left" class="log-divider">
                  <span class="log-group-title">{{ group.name }}</span>
                </a-divider>
                <div v-for="item in group.items" :key="item.field" class="log-compare-row">
                  <div class="log-form-label">{{ item.label || item.field }}</div>
                  <!-- 修改前 -->
                  <div class="log-form-value">
                    <!-- 测试步骤 -->
                    <div v-if="item.field === 'testSteps'" class="steps-preview">
                      <div v-for="(step, idx) in parseSteps(item.old)" :key="idx" class="step-card">
                        <div class="step-card-header">步骤 {{ idx + 1 }}</div>
                        <div class="step-card-body">
                          <div class="step-row">
                            <span class="step-row-label">步骤描述</span>
                            <div class="step-row-content" v-html="sanitizeHtml(step.step)"></div>
                          </div>
                          <div class="step-row">
                            <span class="step-row-label">预期结果</span>
                            <div class="step-row-content" v-html="sanitizeHtml(step.expected)"></div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <!-- 富文本 -->
                    <div v-else-if="isRichTextField(item.field)" v-html="sanitizeHtml(item.old)" class="rich-text-preview compare-old-text"></div>
                    <!-- 普通字段 -->
                    <span v-else-if="item.oldDisplayValue" class="compare-old-text">{{ item.oldDisplayValue }}</span>
                    <span v-else-if="item.old !== undefined && item.old !== null && item.old !== ''" class="compare-old-text">{{ formatFieldValue(item.field, item.old) }}</span>
                    <span v-else class="field-empty">（空）</span>
                  </div>
                  <!-- 修改后 -->
                  <div class="log-form-value">
                    <!-- 测试步骤 -->
                    <div v-if="item.field === 'testSteps'" class="steps-preview">
                      <div v-for="(step, idx) in parseSteps(item.ne)" :key="idx" class="step-card">
                        <div class="step-card-header">步骤 {{ idx + 1 }}</div>
                        <div class="step-card-body">
                          <div class="step-row">
                            <span class="step-row-label">步骤描述</span>
                            <div class="step-row-content" v-html="sanitizeHtml(step.step)"></div>
                          </div>
                          <div class="step-row">
                            <span class="step-row-label">预期结果</span>
                            <div class="step-row-content" v-html="sanitizeHtml(step.expected)"></div>
                          </div>
                        </div>
                      </div>
                    </div>
                    <!-- 富文本 -->
                    <div v-else-if="isRichTextField(item.field)" v-html="sanitizeHtml(item.ne)" class="rich-text-preview compare-new-text"></div>
                    <!-- 普通字段 -->
                    <span v-else-if="item.neDisplayValue" class="compare-new-text">{{ item.neDisplayValue }}</span>
                    <span v-else-if="item.ne !== undefined && item.ne !== null && item.ne !== ''" class="compare-new-text">{{ formatFieldValue(item.field, item.ne) }}</span>
                    <span v-else class="field-empty">（空）</span>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>

        <!-- 纯文本描述 -->
        <div v-else-if="detailData.description" class="detail-section">
          <div class="detail-section-title">
            <icon-info-circle />
            <span>描述</span>
          </div>
          <div class="detail-text-block">{{ detailData.description }}</div>
        </div>

        <!-- 请求参数 -->
        <div v-if="detailData.requestParams" class="detail-section">
          <div class="detail-section-title">
            <icon-code />
            <span>请求参数</span>
          </div>
          <pre class="code-block">{{ formatJson(detailData.requestParams) }}</pre>
        </div>

        <!-- 响应信息 -->
        <div v-if="detailData.responseCode || detailData.responseMsg" class="detail-section">
          <div class="detail-section-title">
            <icon-file />
            <span>响应信息</span>
          </div>
          <div class="detail-text-block">
            <a-space>
              <span>Code: <a-tag :color="detailData.responseCode === 200 ? 'green' : 'red'" size="small">{{ detailData.responseCode }}</a-tag></span>
              <span v-if="detailData.responseMsg">Msg: {{ detailData.responseMsg }}</span>
            </a-space>
          </div>
        </div>

        <!-- UserAgent -->
        <div v-if="detailData.userAgent" class="detail-section">
          <div class="detail-section-title">
            <icon-desktop />
            <span>User Agent</span>
          </div>
          <div class="detail-text-block" style="font-size: 12px; color: #86909c; word-break: break-all;">
            {{ detailData.userAgent }}
          </div>
        </div>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive, computed } from 'vue';
import {
  getOperationLogList,
  getOperationLogDetail,
  getOperationLogModuleOptions,
  getOperationLogTypeOptions,
  getOperationLogTargetTypeOptions,
} from '@/api/MyApi/operationLog';
import { getUserListByPage } from '@/api/MyApi/user';
import { sanitizeHtml } from '@/utils/sanitize';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';

const { loading, loadError, track } = useLoadState();
const userLoading = ref(false);
const logList = ref<any[]>([]);
const moduleOptions = ref<any[]>([]);
const typeOptions = ref<any[]>([]);
const targetTypeOptions = ref<any[]>([]);
const userOptions = ref<any[]>([]);
const userIdMap = ref<Record<number, string>>({});

const searchModule = ref(undefined);
const searchType = ref(undefined);
const searchTargetType = ref(undefined);
const searchOperatorId = ref(undefined);
const searchKeyword = ref('');
const searchTimeRange = ref<string[]>([]);

const pagination = reactive({
  total: 0,
  current: 1,
  pageSize: 20,
  showTotal: true,
  showJumper: true,
  showPageSize: true,
});

// 详情抽屉
const detailVisible = ref(false);
const detailData = ref<any>({});

const openDetail = async (record: any) => {
  detailData.value = { ...record };
  detailVisible.value = true;
  // 列表不含 requestParams/userAgent 大字段，打开详情时拉取完整数据
  try {
    const res: any = await getOperationLogDetail(record.id);
    if (res.code === 200 && res.data) {
      detailData.value = { ...detailData.value, ...res.data };
    }
  } catch (e) {
    console.error(e);
  }
};

const moduleText = (module: string) => {
  const map: Record<string, string> = {
    qa: '质量管理',
    automation: '自动化测试',
    team: '团队',
    project: '项目',
    system: '系统',
  };
  return map[module] || module;
};

// (操作类型, 对象类型) 组合文案，比单纯类型更具体；未命中回退到通用映射
const COMBO_TYPE_MAP: Record<string, string> = {
  'BIND:projectMember': '分配角色',
  'UNBIND:projectMember': '移除成员',
  'CREATE:teamMember': '添加成员',
  'DELETE:teamMember': '移除成员',
  'UPDATE:teamMember': '调整成员',
  'BIND:teamMember': '添加成员',
  'UNBIND:teamMember': '移除成员',
  'BIND:testCase': '绑定自动化',
  'UNBIND:testCase': '解绑自动化',
  'BIND:testPlan': '添加用例',
  'UNBIND:testPlan': '移除用例',
  'EXECUTE:testPlan': '执行计划',
};

const typeText = (type: string, targetType?: string) => {
  const combo = COMBO_TYPE_MAP[`${type}:${targetType}`];
  if (combo) return combo;
  const map: Record<string, string> = {
    CREATE: '创建',
    UPDATE: '更新',
    DELETE: '删除',
    EXECUTE: '执行',
    TRANSITION: '流转',
    LOGIN: '登录',
    LOGOUT: '登出',
    IMPORT: '导入',
    EXPORT: '导出',
    BIND: '绑定',
    UNBIND: '解绑',
    SORT: '排序',
    BATCH_DELETE: '批量删除',
  };
  return map[type] || type;
};

const targetTypeText = (type: string) => {
  const map: Record<string, string> = {
    requirement: '需求',
    bug: 'BUG',
    testCase: '用例',
    testCaseSet: '测试集',
    qaModule: '模块',
    testPlan: '测试计划',
    bugComment: 'BUG评论',
    scene: '场景',
    plan: '自动化任务',
    task: '任务',
    user: '用户',
    team: '团队',
    project: '项目',
    teamMember: '团队成员',
    projectMember: '项目成员',
  };
  return map[type] || type;
};

// 成员类对象的 targetId 是用户ID，拼 #id 会误导成成员主键，不展示
const isMemberType = (targetType?: string) => ['teamMember', 'projectMember'].includes(targetType || '');

const typeColor = (type: string) => {
  const map: Record<string, string> = {
    CREATE: 'green',
    UPDATE: 'blue',
    DELETE: 'red',
    EXECUTE: 'orange',
    TRANSITION: 'purple',
    LOGIN: 'gray',
    LOGOUT: 'gray',
    IMPORT: 'cyan',
    EXPORT: 'cyan',
    BIND: 'arcoblue',
    UNBIND: 'arcoblue',
    SORT: 'pink',
    BATCH_DELETE: 'red',
  };
  return map[type] || 'default';
};

const hasFieldChanges = (desc: string): boolean => {
  if (!desc) return false;
  try {
    const parsed = JSON.parse(desc);
    return Array.isArray(parsed) && parsed.length > 0 && parsed[0].field !== undefined && parsed[0].ne !== undefined;
  } catch (e) {
    return false;
  }
};

// CREATE/DELETE 共用：字段列表格式 [{field, label, value}]
const isFieldList = (desc: string): boolean => {
  if (!desc) return false;
  try {
    const parsed = JSON.parse(desc);
    return Array.isArray(parsed) && parsed.length > 0 && parsed[0].field !== undefined && parsed[0].value !== undefined;
  } catch (e) {
    return false;
  }
};

const parseChanges = (desc: string): any[] => {
  try {
    return JSON.parse(desc);
  } catch (e) {
    return [];
  }
};

const parseFieldList = (desc: string): any[] => {
  try {
    return JSON.parse(desc);
  } catch (e) {
    return [];
  }
};

const formatJson = (json: string): string => {
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch (e) {
    return json;
  }
};


const loadOptions = async () => {
  try {
    const [modRes, typeRes, targetRes]: any = await Promise.all([
      getOperationLogModuleOptions(),
      getOperationLogTypeOptions(),
      getOperationLogTargetTypeOptions(),
    ]);
    if (modRes.code === 200) moduleOptions.value = modRes.data || [];
    if (typeRes.code === 200) typeOptions.value = (typeRes.data || []).map((t: any) => ({
      label: typeText(t.value),
      value: t.value,
    }));
    if (targetRes.code === 200) targetTypeOptions.value = (targetRes.data || []).map((t: any) => ({
      label: targetTypeText(t.value),
      value: t.value,
    }));
  } catch (e) {
    console.error(e);
  }
};

// 操作人远程搜索
const handleSearchUser = async (keyword: string) => {
  userLoading.value = true;
  try {
    const res: any = await getUserListByPage({
      username: keyword || undefined,
      pageNum: 1,
      pageSize: 20,
    });
    if (res.code === 200) {
      const records = res.data?.records || res.data || [];
      userOptions.value = records.map((u: any) => ({
        label: u.nickname || u.username || String(u.id),
        value: u.id,
      }));
    }
  } catch (e) {
    console.error(e);
  } finally {
    userLoading.value = false;
  }
};

const handleUserDropdownOpen = async (visible: boolean) => {
  if (visible && userOptions.value.length === 0) {
    await handleSearchUser('');
  }
};

// 加载用户映射（用于ID转名称）
const loadAllUsers = async () => {
  try {
    const res: any = await getUserListByPage({ pageNum: 1, pageSize: 100 });
    if (res.code === 200) {
      const records = res.data?.records || res.data || [];
      const map: Record<number, string> = {};
      records.forEach((u: any) => {
        map[u.id] = u.nickname || u.username || String(u.id);
      });
      userIdMap.value = map;
    }
  } catch (e) {
    console.error(e);
  }
};

// 格式化字段值：枚举翻译、ID转名称
const formatFieldValue = (field: string, value: any): string => {
  if (value === null || value === undefined || value === '') return '（空）';
  const strValue = String(value);

  // 用户ID映射
  if (['ownerId', 'reporterId', 'assigneeId', 'createUserId', 'updateUserId', 'executeUserId'].includes(field)) {
    const name = userIdMap.value[value];
    return name ? `${name} (${value})` : `用户(${value})`;
  }

  // 状态映射（需求/BUG/用例通用）
  if (field === 'status') {
    const map: Record<string, string> = {
      DRAFT: '草稿', REVIEWING: '评审中', CONFIRMED: '已确认', DEVELOPING: '开发中',
      TESTING: '测试中', RELEASED: '已上线', CLOSED: '已关闭', NEW: '新建',
      FIXING: '修复中', FIXED: '已修复', VERIFIED: '已验证', REJECTED: '已驳回',
      REVIEWED: '已评审', DEPRECATED: '已废弃', ACTIVE: '活跃', INACTIVE: '非活跃',
    };
    return map[strValue] || strValue;
  }

  // 严重程度
  if (field === 'severity') {
    const map: Record<string, string> = { FATAL: '致命', SERIOUS: '严重', NORMAL: '一般', TIPS: '提示' };
    return map[strValue] || strValue;
  }

  // BUG优先级
  if (field === 'priority') {
    const map: Record<string, string> = { URGENT: '紧急', HIGH: '高', MEDIUM: '中', LOW: '低' };
    return map[strValue] || strValue;
  }

  // 用例类型
  if (field === 'caseType') {
    const map: Record<string, string> = { FUNCTION: '功能', API: '接口', PERFORMANCE: '性能', COMPATIBILITY: '兼容', SMOKE: '冒烟' };
    return map[strValue] || strValue;
  }

  // 环境
  if (field === 'environment') {
    const map: Record<string, string> = { TEST: '测试', STAGING: '预发', PROD: '生产' };
    return map[strValue] || strValue;
  }

  // 重现概率
  if (field === 'reproduceRate') {
    const map: Record<string, string> = { ALWAYS: '必现', OFTEN: '经常', SOMETIMES: '偶尔', RARE: '很难复现' };
    return map[strValue] || strValue;
  }

  // 关闭原因
  if (field === 'closeReason') {
    const map: Record<string, string> = { FIXED: '已修复', DUPLICATE: '重复', NOT_BUG: '非BUG', CANNOT_REPRODUCE: '无法复现', WONT_FIX: '暂不修复' };
    return map[strValue] || strValue;
  }

  // 需求类型
  if (field === 'reqType') {
    const map: Record<string, string> = { FEATURE: '功能', BUGFIX: '修复', OPTIMIZE: '优化', TECH_DEBT: '技术债' };
    return map[strValue] || strValue;
  }

  // 来源
  if (field === 'source') {
    const map: Record<string, string> = { CLIENT: '客户', INTERNAL: '内部', COMPETITOR: '竞品', ONLINE: '线上' };
    return map[strValue] || strValue;
  }

  // 参与人（JSON数组：[1,2,3]）
  if (field === 'participants') {
    try {
      const ids = JSON.parse(strValue);
      if (Array.isArray(ids)) {
        const names = ids.map((id: number) => userIdMap.value[id] || `用户(${id})`);
        return names.join(', ');
      }
    } catch (e) {
      return strValue;
    }
  }

  // 特殊ID处理
  if (field === 'parentId' && (value === 0 || value === '0')) return '无';
  if (field === 'moduleId') return `模块(${value})`;
  if (field === 'requirementId') return `需求(${value})`;
  if (field === 'testCaseId') return `用例(${value})`;
  if (field === 'projectId') return `项目(${value})`;
  if (field === 'folderId') return `文件夹(${value})`;
  if (field === 'planCaseId') return `计划用例(${value})`;

  return strValue;
};

// 富文本字段
const RICH_TEXT_FIELDS = ['description', 'reproduceSteps', 'preCondition'];
const isRichTextField = (field: string): boolean => RICH_TEXT_FIELDS.includes(field);

// 解析测试步骤 JSON [{step, expected}, ...]
const parseSteps = (value: any): Array<{ step: string; expected: string }> => {
  if (!value) return [];
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value;
    if (Array.isArray(parsed)) return parsed;
  } catch (e) {
    // 非 JSON 格式（历史数据），返回空数组
  }
  return [];
};

// 技术字段黑名单（弹窗里不展示、自动生成的编码）
const TECHNICAL_FIELDS = new Set([
  'id', 'createTime', 'updateTime', 'createUserId', 'updateUserId',
  'projectId', 'deleted', 'isDeleted', 'deletedAt', 'serialVersionUID',
  'reqCode', 'bugCode', 'caseCode', 'planCode',
]);

const isTechnicalField = (field: string): boolean => {
  return TECHNICAL_FIELDS.has(field);
};

// 字段分组定义（与编辑弹窗分组保持一致）
const FIELD_GROUPS: Record<string, Record<string, string[]>> = {
  requirement: {
    基本信息: ['title', 'description'],
    扩展信息: ['moduleId', 'parentId', 'reqType', 'source', 'priority', 'expectReleaseTime'],
    状态管理: ['status', 'version'],
    协作信息: ['ownerId', 'participants', 'tags'],
  },
  bug: {
    基本信息: ['title', 'description', 'reproduceSteps'],
    扩展信息: ['moduleId', 'environment', 'deadline', 'severity', 'priority'],
    状态管理: ['status', 'foundVersion', 'fixedVersion', 'reproduceRate', 'closeReason'],
    协作信息: ['assigneeId', 'requirementId', 'testCaseId', 'tags'],
  },
  testCase: {
    基本信息: ['caseName', 'caseType', 'preCondition', 'testSteps'],
    扩展信息: ['moduleId', 'setIds', 'priority', 'expectDuration'],
    状态管理: ['status', 'lastResult', 'lastExecuteTime'],
    协作信息: ['requirementId', 'tags'],
  },
  testCaseSet: {
    基本信息: ['setName'],
    扩展信息: ['projectId', 'description'],
    状态管理: [],
    协作信息: [],
  },
  testPlan: {
    基本信息: ['planName', 'description'],
    扩展信息: ['projectId', 'startTime', 'endTime'],
    状态管理: ['status'],
    协作信息: ['tags'],
  },
  qaModule: {
    基本信息: ['moduleName'],
    扩展信息: ['parentId', 'projectId', 'sort'],
    状态管理: [],
    协作信息: [],
  },
};

const groupOrder = ['基本信息', '扩展信息', '状态管理', '协作信息', '其他信息'];

// 获取字段所在分组
const getFieldGroup = (targetType: string, field: string): string => {
  const groups = FIELD_GROUPS[targetType];
  if (!groups) return '其他信息';
  for (const [groupName, fields] of Object.entries(groups)) {
    if (fields.includes(field)) return groupName;
  }
  return '其他信息';
};

// 字段变更 - 按分组
const groupedChanges = computed(() => {
  if (!detailData.value.description) return [];
  const changes = parseChanges(detailData.value.description).filter(
    (item: any) => !isTechnicalField(item.field)
  );

  const targetType = detailData.value.targetType;
  const groupMap = new Map<string, any[]>();

  changes.forEach((item: any) => {
    const groupName = getFieldGroup(targetType, item.field);
    if (!groupMap.has(groupName)) {
      groupMap.set(groupName, []);
    }
    groupMap.get(groupName)!.push(item);
  });

  return Array.from(groupMap.entries())
    .map(([name, items]) => ({ name, items }))
    .sort((a, b) => {
      const idxA = groupOrder.indexOf(a.name);
      const idxB = groupOrder.indexOf(b.name);
      return (idxA === -1 ? 999 : idxA) - (idxB === -1 ? 999 : idxB);
    });
});

// 字段列表（CREATE/DELETE）- 按分组
const groupedFieldList = computed(() => {
  if (!detailData.value.description) return [];
  const list = parseFieldList(detailData.value.description).filter(
    (item: any) => !isTechnicalField(item.field)
  );

  const targetType = detailData.value.targetType;
  const groupMap = new Map<string, any[]>();

  list.forEach((item: any) => {
    const groupName = getFieldGroup(targetType, item.field);
    if (!groupMap.has(groupName)) {
      groupMap.set(groupName, []);
    }
    groupMap.get(groupName)!.push(item);
  });

  return Array.from(groupMap.entries())
    .map(([name, items]) => ({ name, items }))
    .sort((a, b) => {
      const idxA = groupOrder.indexOf(a.name);
      const idxB = groupOrder.indexOf(b.name);
      return (idxA === -1 ? 999 : idxA) - (idxB === -1 ? 999 : idxB);
    });
});

const loadList = async () => {
  const params: any = {
    pageNum: pagination.current,
    pageSize: pagination.pageSize,
    module: searchModule.value,
    operateType: searchType.value,
    targetType: searchTargetType.value,
    operatorId: searchOperatorId.value,
    keyword: searchKeyword.value || undefined,
  };
  if (searchTimeRange.value && searchTimeRange.value.length === 2) {
    params.startTime = searchTimeRange.value[0];
    params.endTime = searchTimeRange.value[1];
  }
  const res: any = await track(getOperationLogList(params));
  if (res?.code === 200) {
    logList.value = res.data.list || [];
    pagination.total = res.data.total || 0;
  } else {
    loadError.value = true;
  }
};

const handleSearch = () => {
  pagination.current = 1;
  loadList();
};

const handleReset = () => {
  searchModule.value = undefined;
  searchType.value = undefined;
  searchTargetType.value = undefined;
  searchOperatorId.value = undefined;
  searchKeyword.value = '';
  searchTimeRange.value = [];
  userOptions.value = [];
  pagination.current = 1;
  loadList();
};

const handlePageChange = (current: number) => {
  pagination.current = current;
  loadList();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadList();
};

onMounted(() => {
  loadOptions();
  loadAllUsers();
  loadList();
});
</script>

<script lang="ts">
// 变更摘要组件（行内展示用）
const TECH_FIELDS = ['id','createTime','updateTime','createUserId','updateUserId','projectId','deleted','isDeleted','deletedAt','serialVersionUID','reqCode','bugCode','caseCode','planCode'];

export default {
  components: {
    ChangeSummary: {
      props: ['description', 'operateType'],
      setup(props: any) {
        const parseItems = (desc: string): any[] => {
          if (!desc) return [];
          try {
            const parsed = JSON.parse(desc);
            return Array.isArray(parsed) ? parsed.filter((c: any) => !TECH_FIELDS.includes(c.field)) : [];
          } catch (e) {
            return [];
          }
        };
        const items = parseItems(props.description);
        const isChange = items.length > 0 && items[0].ne !== undefined;
        const isList = !isChange && items.length > 0 && items[0].value !== undefined;
        const shown = items.slice(0, 3);
        const extra = items.length - shown.length;
        return {
          items, shown, extra, isChange, isList,
          description: props.description,
          operateType: props.operateType,
        };
      },
      template: `
        <div v-if="isChange || isList">
          <a-space wrap>
            <span style="color: #86909c; font-size: 12px;">
              {{ isChange ? '修改了' : (operateType === 'DELETE' ? '删除前包含' : '包含') }} {{ items.length }} 个字段
            </span>
            <a-tag
              v-for="(item, idx) in shown"
              :key="idx"
              size="mini"
              :color="isChange ? 'blue' : (operateType === 'DELETE' ? 'red' : 'green')"
            >
              {{ item.label || item.field }}
            </a-tag>
            <span v-if="extra > 0" style="color: #86909c; font-size: 12px;">+{{ extra }}</span>
          </a-space>
        </div>
        <span v-else-if="description" class="text-ellipsis" :title="description">{{ description }}</span>
        <span v-else style="color: #86909c;">-</span>
      `,
    },
  },
};
</script>

<style scoped>
.operation-log-page {
  padding: 16px;
}

.text-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 详情抽屉样式 */
.detail-body {
  padding: 0 4px;
}

.detail-header {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-meta-row {
  display: flex;
  align-items: center;
}

.detail-meta-label {
  width: 70px;
  color: #86909c;
  font-size: 13px;
  flex-shrink: 0;
}

.detail-meta-value {
  flex: 1;
  font-size: 13px;
  word-break: break-all;
}

.detail-section {
  margin-bottom: 20px;
}

.detail-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  color: rgb(var(--primary-6));
}

.detail-text-block {
  padding: 10px 12px;
  background: var(--color-fill-2);
  border-radius: 4px;
  font-size: 13px;
  line-height: 1.6;
}

.code-block {
  padding: 12px;
  background: #1e1e1e;
  color: #d4d4d4;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.6;
  overflow: auto;
  max-height: 300px;
  margin: 0;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
}

.html-cell {
  display: block;
  max-height: 120px;
  overflow: auto;
  font-size: 13px;
  line-height: 1.5;
}

.html-cell :deep(p) {
  margin: 0 0 4px 0;
}

.html-cell :deep(p:last-child) {
  margin-bottom: 0;
}

/* 弹窗式表单布局 —— 操作日志详情 */

/* 字段变更对比容器（按字段配对行，左右对齐） */
.log-compare-panel {
  border: 1px solid var(--color-neutral-3);
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-bg-2);
}

/* 创建内容/删除前数据 单面板 */
.log-form-panel {
  border: 1px solid var(--color-neutral-3);
  border-radius: 8px;
  overflow: hidden;
  background: var(--color-bg-2);
}

/* 表头两栏 */
.log-compare-head {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-neutral-3);
}

.log-compare-head .log-panel-header {
  border-bottom: none;
  padding: 10px 16px;
}

/* 字段配对行：label 通栏，下方左旧右新 */
.log-compare-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
  margin-bottom: 20px;
}

.log-compare-row .log-form-label {
  grid-column: 1 / -1;
}

.log-compare-row:last-child {
  margin-bottom: 0;
}

/* 面板头部 */
.log-panel-header {
  padding: 10px 16px;
  font-size: 14px;
  font-weight: 600;
  text-align: center;
  border-bottom: 1px solid var(--color-neutral-3);
}

.log-panel-header-old {
  background: #fff2f0;
  color: #f53f3f;
}

.log-panel-header-new {
  background: #e8ffea;
  color: #00b42a;
}

/* 面板内容区（模拟 modal-scroll-body） */
.log-panel-body {
  padding: 16px 20px;
  max-height: 600px;
  overflow-y: auto;
}

/* 分组标题 divider */
.log-divider {
  margin-top: 16px !important;
  margin-bottom: 16px !important;
}

.log-divider:first-child {
  margin-top: 0 !important;
}

/* 分组标题文字 */
.log-group-title {
  font-size: 14px;
  font-weight: 600;
  color: rgb(var(--primary-6));
}

/* 表单项（模拟 a-form-item layout=vertical） */
.log-form-item {
  margin-bottom: 20px;
}

.log-form-item:last-child {
  margin-bottom: 0;
}

/* 表单标签（模拟 a-form-item-label） */
.log-form-label {
  font-size: 13px;
  color: var(--color-text-2);
  margin-bottom: 8px;
  line-height: 1.5;
}

/* 表单值展示区 */
.log-form-value {
  font-size: 14px;
  line-height: 1.6;
  word-break: break-all;
  padding: 8px 12px;
  background: var(--color-fill-2);
  border-radius: 4px;
  min-height: 32px;
}

/* 空值 */
.field-empty {
  color: #86909c;
  font-style: italic;
}

/* 修改前/后文字样式 */
.compare-old-text {
  color: #f53f3f;
  text-decoration: line-through;
}

.compare-new-text {
  color: #00b42a;
  font-weight: 500;
}

/* 测试步骤卡片 */
.steps-preview {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-card {
  border: 1px solid var(--color-neutral-3);
  border-radius: 6px;
  overflow: hidden;
  background: var(--color-bg-2);
}

.step-card-header {
  padding: 6px 10px;
  background: var(--color-fill-2);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
  border-bottom: 1px solid var(--color-neutral-3);
}

.step-card-body {
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.step-row {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.step-row-label {
  font-size: 12px;
  color: #86909c;
  font-weight: 500;
}

.step-row-content {
  font-size: 13px;
  line-height: 1.5;
  word-break: break-all;
  padding: 6px 8px;
  background: var(--color-fill-2);
  border-radius: 4px;
}

.step-row-content :deep(p) {
  margin: 0 0 4px 0;
}

.step-row-content :deep(p:last-child) {
  margin-bottom: 0;
}

/* 富文本预览 */
.rich-text-preview {
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

.rich-text-preview :deep(p) {
  margin: 0 0 6px 0;
}

.rich-text-preview :deep(p:last-child) {
  margin-bottom: 0;
}

.rich-text-preview :deep(ul),
.rich-text-preview :deep(ol) {
  margin: 4px 0;
  padding-left: 20px;
}

.rich-text-preview :deep(li) {
  margin-bottom: 2px;
}
</style>
