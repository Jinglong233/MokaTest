<template>
  <div class="exec-page">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testPlan', planData.planName || '用例执行']" />
    <a-card class="exec-card">
      <!-- 顶部信息栏 -->
      <div class="exec-header">
        <a-button size="small" @click="handleBack">
          <template #icon><icon-left /></template>
          返回
        </a-button>
        <div class="exec-title">
          <span class="exec-code">{{ planData.planCode || '' }}</span>
          <span class="exec-name">{{ planData.planName || '用例执行' }}</span>
        </div>
        <div class="exec-meta">
          <span class="exec-meta-item">已执行 {{ executedCount }}/{{ caseList.length }}</span>
        </div>
      </div>

      <div class="exec-body" v-loading="loading">
        <!-- 左侧用例卡片列表 -->
        <div class="exec-left">
          <a-card size="small" class="exec-left-card">
            <div class="exec-left-filter">
              <a-input-search
                v-model="caseKeyword"
                placeholder="搜索用例名称"
                allow-clear
                size="small"
              />
              <a-radio-group v-model="caseFilter" type="button" size="small" style="margin-top: 8px">
                <a-radio value="ALL">全部</a-radio>
                <a-radio value="TODO">未执行</a-radio>
              </a-radio-group>
            </div>
            <div class="exec-left-scroll">
              <template v-if="pagedCases.length">
                <div
                  v-for="c in pagedCases"
                  :key="c.id"
                  class="case-card-item"
                  :class="{ 'case-card-active': currentCase?.id === c.id }"
                  @click="selectCase(c)"
                >
                  <div class="case-card-text">
                    <div class="case-card-code">{{ c.caseCode }}</div>
                    <div class="case-card-name" :title="c.caseName">{{ c.caseName }}</div>
                  </div>
                  <span class="case-status">
                    <icon-check-circle-fill v-if="c.executeResult === 'PASS'" :size="16" class="status-icon-pass" />
                    <icon-close-circle-fill v-else-if="c.executeResult === 'FAIL'" :size="16" class="status-icon-fail" />
                    <icon-exclamation-circle-fill v-else-if="c.executeResult === 'BLOCK'" :size="16" class="status-icon-block" />
                    <icon-clock-circle v-else :size="16" class="status-icon-pending" />
                    {{ resultText(c.executeResult) }}
                  </span>
                </div>
              </template>
              <a-empty v-else description="暂无用例" style="margin-top: 40px" />
            </div>
            <div class="exec-left-pager">
              <a-pagination
                :total="filteredCases.length"
                v-model:current="currentPage"
                :page-size="pageSize"
                size="mini"
                show-total
                :show-page-size="false"
                @change="(p: number) => (currentPage = p)"
              />
            </div>
          </a-card>
        </div>

        <!-- 右侧用例详情 + Tab -->
        <div class="exec-right">
          <div class="exec-right-scroll">
            <template v-if="currentCase">
              <!-- 用例信息头 -->
              <div class="case-head">
                <div class="case-head-row1">
                  <div class="case-head-name">{{ caseDetail?.caseName || currentCase.caseName }}</div>
                  <a-button v-permission="'qa:testcase:update'" type="outline" size="small" @click="editDrawerVisible = true">
                    <template #icon><icon-edit /></template>编辑
                  </a-button>
                </div>
                <div class="case-head-meta">
                  <span class="meta-item">
                    <icon-info-circle-fill :size="14" class="meta-icon-info" />
                    <span class="meta-label">模块</span>
                    {{ currentCase.moduleName || '-' }}
                  </span>
                  <a-divider direction="vertical" />
                  <span class="meta-item">
                    <icon-star-fill :size="14" class="meta-icon-priority" />
                    <span class="meta-label">优先级</span>
                    {{ currentCase.priority || '-' }}
                  </span>
                  <a-divider direction="vertical" />
                  <span class="meta-item">
                    <icon-check-circle-fill v-if="currentCase.executeResult === 'PASS'" :size="14" class="status-icon-pass" />
                    <icon-close-circle-fill v-else-if="currentCase.executeResult === 'FAIL'" :size="14" class="status-icon-fail" />
                    <icon-exclamation-circle-fill v-else-if="currentCase.executeResult === 'BLOCK'" :size="14" class="status-icon-block" />
                    <icon-clock-circle v-else :size="14" class="status-icon-pending" />
                    <span class="meta-label">结果</span>
                    {{ resultText(currentCase.executeResult) }}
                  </span>
                </div>
              </div>

              <a-tabs v-model:active-key="activeTabKey" type="line" class="exec-tabs">
                <!-- 基本信息 -->
                <a-tab-pane key="basic" title="基本信息">
                  <a-descriptions :column="1" size="small">
                    <a-descriptions-item label="用例编号">{{ currentCase.caseCode }}</a-descriptions-item>
                    <a-descriptions-item label="所属模块">{{ currentCase.moduleName || '-' }}</a-descriptions-item>
                    <a-descriptions-item label="用例等级">
                      <a-tag v-if="currentCase.priority" :color="priorityColor(currentCase.priority)" size="small">{{ currentCase.priority }}</a-tag>
                      <span v-else>-</span>
                    </a-descriptions-item>
                    <a-descriptions-item label="创建人">{{ caseDetail?.createUserName || '-' }}</a-descriptions-item>
                    <a-descriptions-item label="创建时间">{{ caseDetail?.createTime || '-' }}</a-descriptions-item>
                  </a-descriptions>
                </a-tab-pane>

                <!-- 详情 -->
                <a-tab-pane key="info" title="详情">
                  <div class="dd-section-title">前置条件</div>
                  <div v-if="caseDetail?.preCondition" class="pre-condition-view" v-html="caseDetail.preCondition"></div>
                  <div v-else class="pre-condition-empty">无</div>

                  <div class="dd-section-title">测试步骤</div>
                  <a-table
                    v-if="caseSteps.length"
                    :data="caseSteps"
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
                      <a-table-column title="步骤">
                        <template #cell="{ record }">
                          <div v-html="record.step || '-'"></div>
                        </template>
                      </a-table-column>
                      <a-table-column title="预期结果">
                        <template #cell="{ record }">
                          <div v-html="record.expected || '-'"></div>
                        </template>
                      </a-table-column>
                      <a-table-column title="实际结果" :width="220">
                        <template #cell="{ rowIndex }">
                          <a-textarea
                            v-model="stepEdits[rowIndex].actualResult"
                            :auto-size="{ minRows: 1, maxRows: 4 }"
                            placeholder="填写实际结果"
                            allow-clear
                          />
                        </template>
                      </a-table-column>
                      <a-table-column title="步骤执行结果" :width="140">
                        <template #cell="{ rowIndex }">
                          <a-select v-model="stepEdits[rowIndex].stepResult" placeholder="未执行" allow-clear size="small">
                            <a-option value="PASS">成功</a-option>
                            <a-option value="FAIL">失败</a-option>
                            <a-option value="BLOCK">阻塞</a-option>
                          </a-select>
                        </template>
                      </a-table-column>
                    </template>
                  </a-table>
                  <a-empty v-else description="暂无测试步骤" />
                </a-tab-pane>

                <!-- 缺陷：关联当前计划用例的 BUG（plan_case_id 维度） -->
                <a-tab-pane key="bugs">
                  <template #title>缺陷 <span v-if="bugList.length" class="tab-count">{{ bugList.length }}</span></template>
                  <div style="margin-bottom: 8px; display: flex; gap: 8px">
                    <a-button size="small" @click="openBugBindDrawer">
                      <template #icon><icon-link /></template>关联已有BUG
                    </a-button>
                    <a-button size="small" type="primary" @click="openNewBugDrawer">
                      <template #icon><icon-plus /></template>新建BUG
                    </a-button>
                  </div>
                  <a-table v-if="bugList.length" :data="bugList" :pagination="false" size="small" row-key="id">
                    <template #columns>
                      <a-table-column title="编号" :width="120">
                        <template #cell="{ record }">{{ record.bugCode }}</template>
                      </a-table-column>
                      <a-table-column title="BUG名称" data-index="title" :ellipsis="true" />
                      <a-table-column title="状态" :width="90">
                        <template #cell="{ record }">
                          <a-tag size="small" :color="bugStatusColor(record.status)">{{ bugStatusText(record.status) }}</a-tag>
                        </template>
                      </a-table-column>
                      <a-table-column title="创建人" :width="100">
                        <template #cell="{ record }">{{ record.reporterName || '-' }}</template>
                      </a-table-column>
                      <a-table-column title="处理人" :width="100">
                        <template #cell="{ record }">{{ record.assigneeName || '-' }}</template>
                      </a-table-column>
                      <a-table-column title="操作" :width="110">
                        <template #cell="{ record }">
                          <a-popconfirm content="取消关联该BUG？" type="warning" @ok="handleUnbindBug(record)">
                            <a-button type="text" size="small" status="warning">取消关联</a-button>
                          </a-popconfirm>
                        </template>
                      </a-table-column>
                    </template>
                  </a-table>
                  <a-empty v-else description="未关联缺陷" />
                </a-tab-pane>

                <!-- 执行历史 -->
                <a-tab-pane key="history" title="执行历史">
                  <a-timeline v-if="execHistory.length">
                    <a-timeline-item v-for="item in execHistory" :key="item.id">
                      <div class="hist-time">{{ item.executeTime }}</div>
                      <div>
                        <a-tag :color="resultColor(item.result)" size="small">{{ resultText(item.result) }}</a-tag>
                        <span v-if="item.executeUserName || item.executeUserId" class="hist-user">
                          执行人：{{ item.executeUserName || '用户' + item.executeUserId }}
                        </span>
                      </div>
                      <div v-if="item.remark" class="hist-remark">{{ item.remark }}</div>
                    </a-timeline-item>
                  </a-timeline>
                  <a-empty v-else description="暂无执行记录" style="padding: 40px 0" />
                </a-tab-pane>
              </a-tabs>
            </template>
            <a-empty v-else description="请选择左侧用例" style="margin-top: 80px" />
          </div>

          <!-- 执行面板（固定底部）：结果单选 成功/失败/阻塞，初始未执行 -->
          <div class="exec-panel" v-if="currentCase">
            <div class="exec-panel-title">
              执行结果
              <a-tag v-if="currentCase.executeResult && currentCase.executeResult !== 'UNEXECUTED'" size="small" :color="resultColor(currentCase.executeResult)">
                当前：{{ resultText(currentCase.executeResult) }}
              </a-tag>
              <a-tag v-else size="small" color="gray">未执行</a-tag>
            </div>
            <div class="exec-panel-body">
              <a-radio-group v-model="execForm.result">
                <a-radio value="PASS">
                  <icon-check-circle-fill :size="18" class="status-icon-pass" />成功
                </a-radio>
                <a-radio value="FAIL">
                  <icon-close-circle-fill :size="18" class="status-icon-fail" />失败
                </a-radio>
                <a-radio value="BLOCK">
                  <icon-exclamation-circle-fill :size="18" class="status-icon-block" />阻塞
                </a-radio>
              </a-radio-group>
              <a-textarea
                v-model="execForm.remark"
                :auto-size="{ minRows: 2, maxRows: 4 }"
                placeholder="执行备注（选填）"
                style="flex: 1"
              />
              <a-space direction="vertical">
                <a-button type="primary" :loading="submitting" @click="handleSubmit(true)">提交并下一条</a-button>
                <a-button :loading="submitting" @click="handleSubmit(false)">提交</a-button>
              </a-space>
            </div>
          </div>
        </div>
      </div>
    </a-card>

    <!-- 关联已有BUG抽屉（共享组件，差量绑定 planCaseId） -->
    <BugBindDrawer
      v-model:visible="bugBindVisible"
      :plan-case-id="currentCase?.id || null"
      @saved="loadDetail(true)"
    />

    <!-- 新建BUG抽屉：复用 BUG 表单，planCaseId/testCaseId 通过 initialData 带入完成关联 -->
    <a-drawer
      v-model:visible="newBugVisible"
      title="新建BUG"
      :width="920"
      :footer="true"
      :body-style="{ padding: '0', height: '100%', overflow: 'hidden' }"
      @cancel="newBugVisible = false"
    >
      <div class="new-bug-body">
        <BugForm
          v-if="newBugVisible"
          ref="newBugFormRef"
          :initial-data="{
            projectId: projectStore.getProjectId,
            testCaseId: currentCase?.testCaseId,
            planCaseId: currentCase?.id,
            moduleId: currentCase?.moduleId,
            status: 'NEW',
            severity: 'NORMAL',
            priority: 'MEDIUM',
            environment: 'TEST',
            tags: [],
          }"
        />
      </div>
      <template #footer>
        <a-space>
          <a-button @click="newBugVisible = false">取消</a-button>
          <a-button type="primary" :loading="newBugSaving" @click="handleNewBugSave">保存</a-button>
        </a-space>
      </template>
    </a-drawer>
    <CaseDetailDrawer
      v-model:visible="editDrawerVisible"
      :case-id="currentCase?.testCaseId || null"
      drawer-title="更新用例"
      confirm-text="更新"
      auto-edit
      @refresh="onEditDrawerRefresh"
    />
  </div>
</template>

<script lang="ts">
  export default { name: 'qaTestPlanExecute' };
</script>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import BugForm from '@/views/qa/bug/components/BugForm.vue';
import BugBindDrawer from '@/views/qa/components/BugBindDrawer.vue';
import CaseDetailDrawer from '@/views/qa/testCase/components/CaseDetailDrawer.vue';
import { useProjectStore } from '@/store';
import {
  getTestPlanDetail,
  getTestCaseDetail,
  executePlanCaseWithSteps,
  getTestCaseExecutionHistory,
  bindBugPlanCase,
} from '@/api/MyApi/qa';

const route = useRoute();
const router = useRouter();
const projectStore = useProjectStore();

const planId = Number(route.params.planId);
const initialPlanCaseId = route.query.planCaseId ? Number(route.query.planCaseId) : null;

const loading = ref(false);
const planData = ref<any>({});
const caseList = ref<any[]>([]);
const currentCase = ref<any>(null);
const caseDetail = ref<any>(null);
const execHistory = ref<any[]>([]);
const activeTabKey = ref('info');
const caseFilter = ref<'ALL' | 'TODO'>('ALL');
const caseKeyword = ref('');
const currentPage = ref(1);
const pageSize = 10;
const submitting = ref(false);
const execForm = ref<{ result: string; remark: string }>({ result: '', remark: '' });

// 编辑用例抽屉（同评审工作台）
const editDrawerVisible = ref(false);
const onEditDrawerRefresh = async () => {
  await loadDetail(true);
  if (currentCase.value) await selectCase(currentCase.value);
};

const executedCount = computed(() => caseList.value.filter(c => c.executeResult && c.executeResult !== 'UNEXECUTED').length);

const filteredCases = computed(() => {
  let list = caseList.value;
  if (caseFilter.value === 'TODO') {
    list = list.filter((c: any) => !c.executeResult || c.executeResult === 'UNEXECUTED');
  }
  const kw = caseKeyword.value.trim().toLowerCase();
  if (kw) {
    list = list.filter((c: any) =>
      (c.caseName || '').toLowerCase().includes(kw) ||
      (c.caseCode || '').toLowerCase().includes(kw)
    );
  }
  return list;
});

const pagedCases = computed(() => {
  const start = (currentPage.value - 1) * pageSize;
  return filteredCases.value.slice(start, start + pageSize);
});

watch([caseFilter, caseKeyword], () => {
  currentPage.value = 1;
});

const caseSteps = computed(() => {
  const steps = caseDetail.value?.testSteps;
  if (Array.isArray(steps)) return steps;
  if (typeof steps === 'string' && steps) {
    try {
      const parsed = JSON.parse(steps);
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [];
    }
  }
  return [];
});

// 步骤执行明细（实际结果/步骤执行结果），与 caseSteps 下标对齐；提交时才保存
const stepEdits = ref<{ actualResult: string; stepResult: string }[]>([]);

// 切换用例 / 用例详情加载后重建编辑数组，已提交过的从 planCase.stepResults 回填
const resetStepEdits = (savedJson?: string) => {
  let saved: any[] = [];
  if (savedJson) {
    try {
      const parsed = JSON.parse(savedJson);
      if (Array.isArray(parsed)) saved = parsed;
    } catch { /* 忽略脏数据 */ }
  }
  stepEdits.value = caseSteps.value.map((_, i) => ({
    actualResult: saved[i]?.actualResult || '',
    stepResult: saved[i]?.stepResult || '',
  }));
};

watch(caseSteps, () => resetStepEdits(currentCase.value?.stepResults));

const resultText = (r: string) => ({
  PASS: '成功', FAIL: '失败', BLOCK: '阻塞', NA: '不适用',
} as Record<string, string>)[r] || '未执行';
const resultColor = (r: string) => ({
  PASS: 'green', FAIL: 'red', BLOCK: 'orange', NA: 'gray',
} as Record<string, string>)[r] || 'gray';
const priorityColor = (p: string) => ({ P0: 'red', P1: 'orange', P2: 'blue' } as Record<string, string>)[p] || 'gray';

// ===== 缺陷 tab =====
const bugList = computed<any[]>(() => currentCase.value?.bugList || []);
const bugStatusText = (s: string) => ({
  NEW: '新建', CONFIRMED: '已确认', FIXING: '修复中', FIXED: '已修复', VERIFIED: '已验证', CLOSED: '已关闭', REJECTED: '已驳回',
} as Record<string, string>)[s] || s || '-';
const bugStatusColor = (s: string) => ({
  NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold', FIXED: 'green', VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray',
} as Record<string, string>)[s] || 'gray';

// 关联已有BUG抽屉（共享组件）
const bugBindVisible = ref(false);

const openBugBindDrawer = () => {
  if (!currentCase.value) return;
  bugBindVisible.value = true;
};

// 取消关联：planCaseId 置 null（bindPlanCase 支持）
const handleUnbindBug = async (record: any) => {
  if (!currentCase.value) return;
  try {
    const res: any = await bindBugPlanCase(record.id, null);
    if (res.code === 200) {
      Message.success('已取消关联');
      await loadDetail(true);
    }
  } catch (e) {
    console.error(e);
    Message.error('操作失败');
  }
};

// 新建BUG抽屉
const newBugVisible = ref(false);
const newBugSaving = ref(false);
const newBugFormRef = ref();

const openNewBugDrawer = () => {
  if (!currentCase.value) return;
  newBugVisible.value = true;
};

const handleNewBugSave = async () => {
  if (!newBugFormRef.value) return;
  newBugSaving.value = true;
  try {
    const ok = await newBugFormRef.value.save();
    if (ok) {
      Message.success('BUG已创建并关联');
      newBugVisible.value = false;
      await loadDetail(true);
    }
  } catch (e) {
    console.error(e);
  } finally {
    newBugSaving.value = false;
  }
};

const loadDetail = async (keepSelection = false) => {
  if (!planId) return;
  loading.value = true;
  try {
    const res: any = await getTestPlanDetail(planId);
    if (res.code === 200 && res.data) {
      planData.value = res.data.plan || {};
      caseList.value = res.data.cases || [];
      if (!keepSelection || !currentCase.value) {
        const target = initialPlanCaseId
          ? caseList.value.find((c: any) => Number(c.id) === initialPlanCaseId)
          : null;
        selectCase(target || caseList.value[0] || null);
      } else {
        const fresh = caseList.value.find((c: any) => Number(c.id) === Number(currentCase.value.id));
        if (fresh) currentCase.value = fresh;
      }
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const selectCase = async (c: any) => {
  if (!c) return;
  // 自动跳到该用例所在分页
  const idx = filteredCases.value.findIndex((item: any) => Number(item.id) === Number(c.id));
  if (idx >= 0) currentPage.value = Math.floor(idx / pageSize) + 1;
  currentCase.value = c;
  activeTabKey.value = 'info';
  // 已执行过的回填当前结果；未执行的保持空（未执行）
  const r = c.executeResult;
  execForm.value = { result: ['PASS', 'FAIL', 'BLOCK'].includes(r) ? r : '', remark: '' };
  caseDetail.value = null;
  execHistory.value = [];
  try {
    if (c.testCaseId) {
      const res: any = await getTestCaseDetail(Number(c.testCaseId));
      if (res.code === 200) caseDetail.value = res.data;
      const histRes: any = await getTestCaseExecutionHistory(Number(c.testCaseId));
      if (histRes.code === 200) execHistory.value = histRes.data || [];
    }
  } catch (e) {
    console.error(e);
  }
};

const handleSubmit = async (next: boolean) => {
  if (!currentCase.value) return;
  if (!execForm.value.result) {
    Message.warning('请选择执行结果');
    return;
  }
  submitting.value = true;
  try {
    const res: any = await executePlanCaseWithSteps(
      currentCase.value.id,
      execForm.value.result,
      execForm.value.remark || undefined,
      undefined,
      stepEdits.value
    );
    if (res.code === 200) {
      Message.success('执行结果已记录');
      const currentId = currentCase.value.id;
      await loadDetail(true);
      // 刷新执行历史
      if (currentCase.value?.testCaseId) {
        const histRes: any = await getTestCaseExecutionHistory(Number(currentCase.value.testCaseId));
        if (histRes.code === 200) execHistory.value = histRes.data || [];
      }
      if (next) {
        const idx = caseList.value.findIndex((c: any) => Number(c.id) === Number(currentId));
        const isTodo = (c: any) => !c.executeResult || c.executeResult === 'UNEXECUTED';
        const target = caseList.value.slice(idx + 1).find(isTodo) || caseList.value.slice(0, idx + 1).find(isTodo);
        if (target) {
          selectCase(target);
        } else {
          Message.success('已全部执行完成');
        }
      }
    }
  } catch (e) {
    console.error(e);
  } finally {
    submitting.value = false;
  }
};

const handleBack = () => {
  router.push({ name: 'TestPlanDetail', params: { planId } });
};

onMounted(() => {
  loadDetail();
});
</script>

<style scoped lang="less">
.exec-page {
  padding: 12px 20px 16px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.exec-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.exec-card :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.exec-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border-2);
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.exec-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.exec-code { color: var(--color-text-2); font-size: 13px; }
.exec-name { font-weight: 600; font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 320px; }
.exec-meta { display: flex; gap: 16px; color: var(--color-text-2); font-size: 13px; }

.exec-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.exec-left {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.exec-left :deep(.arco-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.exec-left :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.exec-left-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.exec-left-pager {
  flex-shrink: 0;
  padding-top: 6px;
  border-top: 1px solid var(--color-border-2);
}
.exec-left-pager :deep(.arco-pagination) {
  justify-content: center;
}

.exec-left-filter {
  padding: 0 0 8px;
  border-bottom: 1px solid var(--color-border-2);
  margin-bottom: 4px;
}

.case-card-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 8px;
  cursor: pointer;
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  margin: 4px;
  background: var(--color-bg-2);
}
.case-card-item:hover { background: var(--color-fill-2); }
.case-card-active {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-1);
}
.case-card-text { min-width: 0; flex: 1; }
.case-card-code { font-size: 12px; color: var(--color-text-3); }
.case-card-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.case-status {
  font-size: 14px;
  color: var(--color-text-1);
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.exec-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.exec-right-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding-bottom: 8px;
}

.case-head {
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border-2);
  margin-bottom: 8px;
}
.case-head-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.case-head-name {
  font-weight: 600;
  font-size: 16px;
  line-height: 1.4;
  margin-bottom: 8px;
}
.case-head-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-2);
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.meta-label {
  color: var(--color-text-3);
}
.meta-icon-info { color: rgb(var(--primary-6)); }
.meta-icon-priority { color: #ff7d00; }

.dd-section-title {
  font-weight: 600;
  margin: 4px 0 10px;
  padding-left: 8px;
  border-left: 3px solid rgb(var(--primary-6));
  line-height: 1.2;
}
.pre-condition-view { margin: 0 0 16px; }
.pre-condition-empty { margin: 0 0 16px; color: var(--color-text-3); font-size: 13px; }
.step-no { color: rgb(var(--primary-6)); font-weight: 600; font-size: 12px; }

.hist-time { color: #86909c; font-size: 12px; margin-bottom: 4px; }
.hist-user { margin-left: 8px; color: #86909c; }
.hist-remark { margin-top: 4px; color: #666; white-space: pre-wrap; }

.tab-count { color: var(--color-text-3); font-size: 12px; }
.new-bug-body { height: 100%; overflow: hidden; }

.status-icon-pass { color: #00b42a; }
.status-icon-fail { color: #f53f3f; }
.status-icon-block { color: #ff7d00; }
.status-icon-pending { color: #86909c; }

.exec-panel {
  background: var(--color-fill-1);
  border-radius: 8px;
  padding: 12px;
  flex-shrink: 0;
}
.exec-panel-title {
  font-weight: 600;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.exec-panel-body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
/* 选项文字保持默认色，不随选中变色 */
.exec-panel-body :deep(.arco-radio) {
  color: var(--color-text-1);
}
.exec-panel-body :deep(.arco-radio:hover),
.exec-panel-body :deep(.arco-radio-checked) {
  color: var(--color-text-1);
}
.exec-panel-body :deep(.arco-radio) svg {
  margin-right: 4px;
  vertical-align: -2px;
}
</style>
