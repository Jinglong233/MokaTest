<template>
  <div class="workbench-page">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testCase', 'menu.qa.reviewWorkbench']" />
    <a-card class="wb-card">
    <!-- 顶部信息栏 -->
    <div class="wb-header">
      <a-button size="small" @click="handleBack">
        <template #icon><icon-left /></template>
        {{ $t('qa.review.back') }}
      </a-button>
      <div class="wb-title">
        <span class="wb-code">{{ detail?.reviewCode || '' }}</span>
        <span class="wb-name">{{ detail?.title || $t('menu.qa.reviewWorkbench') }}</span>
        <a-tag v-if="detail" :color="statusColor(detail.status)" size="small">{{ statusText(detail.status) }}</a-tag>
      </div>
      <div class="wb-meta" v-if="detail">
        <span class="wb-meta-item">{{ $t('qa.review.passRuleLabel') }}{{ detail.passRule === 'MAJORITY' ? $t('qa.review.passRuleMajority') : $t('qa.review.passRuleAll') }}</span>
        <span class="wb-meta-item" v-if="detail.deadline">{{ $t('qa.review.deadlineLabel') }}{{ detail.deadline }}</span>
      </div>
    </div>

    <!-- 已完成我的评审横幅 -->
    <a-alert v-if="myAllDone && caseList.length" type="success" class="wb-banner">
      {{ $t('qa.review.myAllDone') }}
    </a-alert>

    <div class="wb-body" v-loading="loading">
      <!-- 左侧用例卡片列表 -->
      <div class="wb-left">
        <a-card size="small" class="wb-left-card">
          <div class="wb-left-filter">
            <a-input-search
              v-model="caseKeyword"
              :placeholder="$t('qa.review.searchCaseName')"
              allow-clear
              size="small"
            />
            <a-radio-group v-model="caseFilter" type="button" size="small" style="margin-top: 8px">
              <a-radio value="ALL">{{ $t('qa.common.all') }}</a-radio>
              <a-radio value="TODO">{{ $t('qa.review.todoMine') }}</a-radio>
            </a-radio-group>
          </div>
          <div class="wb-left-scroll">
            <template v-if="pagedCases.length">
              <div
                v-for="c in pagedCases"
                :key="c.testCaseId"
                class="case-card-item"
                :class="{ 'case-card-active': currentCase?.testCaseId === c.testCaseId }"
                @click="selectCase(c)"
              >
                <div class="case-card-text">
                  <div class="case-card-code">{{ c.caseCode }}</div>
                  <div class="case-card-name" :title="c.caseName">{{ c.caseName }}</div>
                </div>
                <span class="case-status">
                  <icon-check-circle-fill v-if="c.finalResult === 'PASS'" :size="16" class="status-icon-pass" />
                  <icon-close-circle-fill v-else-if="c.finalResult === 'REJECT'" :size="16" class="status-icon-reject" />
                  <icon-exclamation-circle-fill v-else :size="16" class="status-icon-pending" />
                  {{ caseReviewStatus(c).text }}
                </span>
              </div>
            </template>
            <a-empty v-else :description="$t('qa.review.noCases')" style="margin-top: 40px" />
          </div>
          <div class="wb-left-pager">
            <a-pagination
              :total="filteredCases.length"
              v-model:current="currentPage"
              :page-size="pageSize"
              size="mini"
              show-total
              show-jumper
              :show-page-size="false"
              @change="onPageChange"
            >
              <template #total>{{ $t('qa.review.totalPages', { count: totalPages }) }}</template>
            </a-pagination>
          </div>
        </a-card>
      </div>

      <!-- 右侧用例详情 + Tab -->
      <div class="wb-right">
        <div class="wb-right-scroll">
          <template v-if="currentCase">
            <!-- 用例信息头：第一行名称+编辑按钮，第二行模块/等级/评审结果 -->
            <div class="case-head">
              <div class="case-head-row1">
                <div class="case-head-name">{{ caseDetail?.caseName || currentCase.caseName }}</div>
                <a-button v-permission="'qa:testcase:update'" type="outline" size="small" @click="openEditDrawer">
                  <template #icon><icon-edit /></template>{{ $t('qa.review.op.edit') }}
                </a-button>
              </div>
              <div class="case-head-meta">
                <span class="meta-item">
                  <icon-info-circle-fill :size="14" class="meta-icon-info" />
                  <span class="meta-label">{{ $t('qa.review.moduleLabel') }}</span>
                  {{ caseDetail?.moduleName || '-' }}
                </span>
                <a-divider direction="vertical" />
                <span class="meta-item">
                  <icon-star-fill :size="14" class="meta-icon-priority" />
                  <span class="meta-label">{{ $t('qa.review.priorityLabel') }}</span>
                  {{ caseDetail?.priority || '-' }}
                </span>
                <a-divider direction="vertical" />
                <span class="meta-item">
                  <icon-check-circle-fill v-if="currentCase?.finalResult === 'PASS'" :size="14" class="meta-icon-pass" />
                  <icon-close-circle-fill v-else-if="currentCase?.finalResult === 'REJECT'" :size="14" class="meta-icon-reject" />
                  <icon-exclamation-circle-fill v-else :size="14" class="meta-icon-pending" />
                  <span class="meta-label">{{ $t('qa.review.reviewLabel') }}</span>
                  {{ caseReviewStatus(currentCase).text }}
                </span>
              </div>
            </div>

            <a-tabs v-model:active-key="activeTab" type="line" class="wb-tabs">
              <!-- ========== 基本信息 ========== -->
              <a-tab-pane key="basic" :title="$t('qa.review.tabBasic')">
                <a-descriptions :column="1" size="small">
                  <a-descriptions-item :label="$t('qa.review.module')">{{ caseDetail?.moduleName || '-' }}</a-descriptions-item>
                  <a-descriptions-item :label="$t('qa.testCase.type')">{{ caseTypeText(caseDetail?.caseType) }}</a-descriptions-item>
                  <a-descriptions-item :label="$t('qa.review.caseLevel')">
                    <a-tag v-if="caseDetail?.priority" :color="priorityColor(caseDetail.priority)" size="small">{{ caseDetail.priority }}</a-tag>
                    <span v-else>-</span>
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('qa.review.tags')">
                    <template v-if="caseDetail?.tags">
                      <a-tag v-for="tag in String(caseDetail.tags).split(',').filter(Boolean)" :key="tag" size="small">{{ tag }}</a-tag>
                    </template>
                    <span v-else>-</span>
                  </a-descriptions-item>
                  <a-descriptions-item :label="$t('qa.review.creator')">{{ caseDetail?.createUserName || '-' }}</a-descriptions-item>
                  <a-descriptions-item :label="$t('qa.review.createTime')">{{ caseDetail?.createTime || '-' }}</a-descriptions-item>
                </a-descriptions>
              </a-tab-pane>

              <!-- ========== 详情 ========== -->
              <a-tab-pane key="info" :title="$t('qa.review.tabDetail')">
                <div class="dd-section-title">{{ $t('qa.testCase.preCondition') }}</div>
                <div v-if="caseDetail?.preCondition" class="pre-condition-view" v-html="caseDetail.preCondition"></div>
                <div v-else class="pre-condition-empty">{{ $t('qa.review.none') }}</div>

                <div class="dd-section-title">{{ $t('qa.testCase.steps') }}</div>
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
                    <a-table-column :title="$t('qa.testCase.step')">
                      <template #cell="{ record }">
                        <div v-html="record.step || '-'"></div>
                      </template>
                    </a-table-column>
                    <a-table-column :title="$t('qa.testCase.expected')">
                      <template #cell="{ record }">
                        <div v-html="record.expected || '-'"></div>
                      </template>
                    </a-table-column>
                  </template>
                </a-table>
                <a-empty v-else :description="$t('qa.review.noSteps')" />
              </a-tab-pane>

              <!-- ========== 关联需求 ========== -->
              <a-tab-pane key="requirement">
                <template #title>{{ $t('qa.review.tabRequirement') }} <span v-if="requirementList.length" class="tab-count">{{ requirementList.length }}</span></template>
                <a-table v-if="requirementList.length" :data="requirementList" :pagination="false" size="small" row-key="id">
                  <template #columns>
                    <a-table-column :title="$t('qa.review.reqCode')" :width="120">
                      <template #cell="{ record }">{{ record.reqCode }}</template>
                    </a-table-column>
                    <a-table-column :title="$t('qa.review.reqName')">
                      <template #cell="{ record }">{{ record.title }}</template>
                    </a-table-column>
                    <a-table-column :title="$t('qa.testCase.status')" :width="100">
                      <template #cell="{ record }">
                        <a-tag size="small" color="arcoblue">{{ record.status || '-' }}</a-tag>
                      </template>
                    </a-table-column>
                  </template>
                </a-table>
                <a-empty v-else :description="$t('qa.review.noRequirement')" />
              </a-tab-pane>

              <!-- ========== 评审历史（投票事件流，逐票留痕） ========== -->
              <a-tab-pane key="history" :title="$t('qa.review.tabHistory')">
                <template v-if="reviewHistory.length">
                  <div v-for="(h, idx) in reviewHistory" :key="idx" class="vote-item">
                    <a-avatar
                      :size="32"
                      :image-url="h.reviewerAvatar || undefined"
                      class="vote-avatar"
                    >
                      {{ (h.reviewerName || '#').slice(0, 1) }}
                    </a-avatar>
                    <div class="vote-main">
                      <div class="vote-name-line">
                        <span class="vote-name">{{ h.reviewerName || `#${h.reviewerId}` }}</span>
                        <span v-if="h.reviewCode" class="vote-review">{{ h.reviewCode }} · {{ h.reviewTitle }}</span>
                      </div>
                      <div v-if="h.comment" class="vote-comment">{{ h.comment }}</div>
                      <div class="vote-time">{{ h.reviewTime || '' }}</div>
                    </div>
                    <a-tag class="vote-result" size="small" :color="conclusionColor(h.conclusion)">
                      {{ conclusionText(h.conclusion) }}
                    </a-tag>
                  </div>
                </template>
                <a-empty v-else :description="$t('qa.review.noHistory')" style="padding: 40px 0" />
              </a-tab-pane>
            </a-tabs>
          </template>
          <a-empty v-else :description="$t('qa.review.selectCaseHint')" style="margin-top: 80px" />
        </div>

        <!-- 我的评审面板（固定底部）：当前用例的在册评审人可见，评审中/已完结均可提交（完结单重提自动重开） -->
        <div class="my-review-panel" v-if="currentCase && detail?.status !== 'CANCELLED' && myExistingItem">
          <div class="my-review-title">
            {{ $t('qa.review.myReview') }}
            <a-tag v-if="myExistingItem?.conclusion && myExistingItem.conclusion !== 'PENDING'" size="small" :color="conclusionColor(myExistingItem.conclusion)">
              {{ $t('qa.review.reviewedTag', { conclusion: conclusionText(myExistingItem.conclusion) }) }}
            </a-tag>
          </div>
          <div class="my-review-body">
            <a-radio-group v-model="myForm.conclusion">
              <a-radio value="PASS">
                <icon-check-circle-fill :size="18" class="radio-icon-pass" />{{ $t('qa.review.resultPass') }}
              </a-radio>
              <a-radio value="REJECT">
                <icon-close-circle-fill :size="18" class="radio-icon-reject" />{{ $t('qa.review.resultReject') }}
              </a-radio>
            </a-radio-group>
            <!-- 通过 / 不通过 的意见分开填写，互不串扰 -->
            <a-textarea
              v-if="myForm.conclusion === 'PASS'"
              v-model="myForm.passComment"
              :auto-size="{ minRows: 2, maxRows: 4 }"
              :placeholder="$t('qa.review.commentOptional')"
              style="flex: 1"
            />
            <a-textarea
              v-else
              v-model="myForm.rejectComment"
              :auto-size="{ minRows: 2, maxRows: 4 }"
              :placeholder="$t('qa.review.commentRequired')"
              style="flex: 1"
            />
            <a-space direction="vertical">
              <a-button type="primary" :loading="submitting" @click="handleSubmit(true)">{{ $t('qa.review.submitNext') }}</a-button>
              <a-button :loading="submitting" @click="handleSubmit(false)">{{ $t('qa.review.submit') }}</a-button>
            </a-space>
          </div>
        </div>
      </div>
    </div>
    </a-card>
    <CaseDetailDrawer
      v-model:visible="editDrawerVisible"
      :case-id="currentCase?.testCaseId || null"
      :drawer-title="$t('qa.review.updateCase')"
      :confirm-text="$t('qa.review.update')"
      auto-edit
      @refresh="onEditDrawerRefresh"
    />
  </div>
</template>

<script lang="ts">
  export default { name: 'qaReviewWorkbench' };
</script>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import {
  IconLeft,
  IconEdit,
} from '@arco-design/web-vue/es/icon';
import CaseDetailDrawer from '@/views/qa/testCase/components/CaseDetailDrawer.vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import { useUserStore } from '@/store';
import useReviewTodoStore from '@/store/modules/review-todo';
import {
  getTestCaseReviewDetail,
  submitTestCaseReview,
  getTestCaseDetail,
  getRequirementDetail,
  getCaseReviewHistory,
} from '@/api/MyApi/qa';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const reviewTodoStore = useReviewTodoStore();
const { t } = useI18n();

const editDrawerVisible = ref(false);
const openEditDrawer = () => {
  if (!currentCase.value) return;
  editDrawerVisible.value = true;
};
const onEditDrawerRefresh = async () => {
  await loadDetail(true);
  // 重新加载当前所选用例的详情内容（右侧信息区）
  if (currentCase.value) {
    await selectCase(currentCase.value);
  }
};

const reviewId = Number(route.query.id);
const initialCaseId = route.query.caseId ? Number(route.query.caseId) : null;
const myId = computed(() => Number(userStore.id || (userStore.userInfo as any)?.id));

const loading = ref(false);
const detail = ref<any>(null);
const currentCase = ref<any>(null);
const caseDetail = ref<any>(null);
const requirementList = ref<any[]>([]);
const reviewHistory = ref<any[]>([]);
const activeTab = ref('info');
const caseFilter = ref<'ALL' | 'TODO'>('ALL');
const caseKeyword = ref('');
const currentPage = ref(1);
const pageSize = 10;
const submitting = ref(false);
const myForm = ref<{ conclusion: string; passComment: string; rejectComment: string }>({
  conclusion: 'PASS',
  passComment: '',
  rejectComment: '',
});

const caseList = computed<any[]>(() => detail.value?.cases || []);

const myItemOf = (c: any) => (c.items || []).find((i: any) => Number(i.reviewerId) === myId.value);
// PENDING 为占位结论，不算已评
const myConclusion = (c: any) => {
  const v = myItemOf(c)?.conclusion;
  return v && v !== 'PENDING' ? v : '';
};

const filteredCases = computed(() => {
  let list = caseList.value;
  if (caseFilter.value === 'TODO') {
    // 待我评 = 我是该用例评审人 且 我的明细仍为 PENDING
    list = list.filter((c: any) => myItemOf(c) && !myConclusion(c));
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

const onPageChange = (page: number) => {
  currentPage.value = page;
};
const totalPages = computed(() => Math.ceil(filteredCases.value.length / pageSize) || 1);

// 筛选条件变化时重置页码
watch([caseFilter, caseKeyword], () => {
  currentPage.value = 1;
});

const myAllDone = computed(() => {
  const myCases = caseList.value.filter((c: any) => myItemOf(c));
  return myCases.length > 0 && myCases.every((c: any) => !!myConclusion(c));
});

// 用例评审状态：未评审 / 评审中 / 已通过 / 不通过 / 重新提审
const caseReviewStatus = (c: any) => {
  if (c.finalResult === 'PASS') return { text: t('qa.review.casePassed'), color: 'green' };
  if (c.finalResult === 'REJECT') return { text: t('qa.review.resultReject'), color: 'red' };
  const items = c.items || [];
  const hasSubmitted = items.some((i: any) => i.conclusion && i.conclusion !== 'PENDING');
  if (!hasSubmitted) return { text: t('qa.review.notReviewed'), color: 'gray' };
  return { text: t('qa.review.status.reviewing'), color: 'gold' };
};

const reviewerProgress = computed(() => {
  const cases = caseList.value;
  const reviewers: any[] = detail.value?.reviewers || [];
  return reviewers.map((r: any) => {
    const rid = r.reviewerId ?? r.id;
    const name = r.reviewerName ?? r.nickname ?? r.name ?? `#${rid}`;
    let done = 0;
    cases.forEach((c: any) => {
      const item = (c.items || []).find((i: any) => Number(i.reviewerId) === Number(rid));
      if (item && item.conclusion) done++;
    });
    return { reviewerId: rid, name, done, total: cases.length };
  });
});

const myExistingItem = computed(() => (currentCase.value ? myItemOf(currentCase.value) : null));

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

const loadDetail = async (keepSelection = false) => {
  if (!reviewId) return;
  loading.value = true;
  try {
    const res: any = await getTestCaseReviewDetail(reviewId);
    if (res.code === 200) {
      detail.value = res.data;
      if (!keepSelection || !currentCase.value) {
        if (initialCaseId) {
          const target = caseList.value.find((c: any) => Number(c.testCaseId) === Number(initialCaseId));
          if (target) {
            selectCase(target);
          } else {
            const firstTodo = caseList.value.find((c: any) => myItemOf(c) && !myConclusion(c));
            selectCase(firstTodo || caseList.value[0] || null);
          }
        } else {
          const firstTodo = caseList.value.find((c: any) => myItemOf(c) && !myConclusion(c));
          selectCase(firstTodo || caseList.value[0] || null);
        }
      } else {
        const fresh = caseList.value.find((c: any) => Number(c.testCaseId) === Number(currentCase.value.testCaseId));
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
  // 自动跳转到该用例所在的分页页码
  const idx = filteredCases.value.findIndex((item: any) => Number(item.testCaseId) === Number(c.testCaseId));
  if (idx >= 0) {
    currentPage.value = Math.floor(idx / pageSize) + 1;
  }
  currentCase.value = c;
  activeTab.value = 'basic';
  const mine = myItemOf(c);
  const mineConclusion = mine?.conclusion && mine.conclusion !== 'PENDING' ? mine.conclusion : 'PASS';
  // 已有结论回填到对应的意见框
  myForm.value = {
    conclusion: mineConclusion,
    passComment: mineConclusion === 'PASS' ? (mine?.comment || '') : '',
    rejectComment: mineConclusion === 'REJECT' ? (mine?.comment || '') : '',
  };
  requirementList.value = [];
  reviewHistory.value = [];
  caseDetail.value = null;
  try {
    const res: any = await getTestCaseDetail(Number(c.testCaseId));
    if (res.code === 200) {
      caseDetail.value = res.data;
      // 加载关联需求（支持多需求）
      const reqIdsStr = res.data?.requirementIds || (res.data?.requirementId ? String(res.data.requirementId) : '');
      const reqIds = reqIdsStr.split(',').map((s: string) => Number(s.trim())).filter((n: number) => n > 0);
      if (reqIds.length) {
        const results = await Promise.all(reqIds.map((id: number) => getRequirementDetail(id)));
        requirementList.value = results.map((r: any) => r.code === 200 ? r.data : null).filter(Boolean);
      }
    }
    // 加载评审历史
    const histRes: any = await getCaseReviewHistory(Number(c.testCaseId));
    if (histRes.code === 200) {
      reviewHistory.value = histRes.data || [];
    }
  } catch (e) {
    console.error(e);
  }
};

// 单独刷新当前用例的评审历史（提交结论后调用，避免整页 selectCase 重置表单）
const reloadReviewHistory = async () => {
  if (!currentCase.value) return;
  try {
    const histRes: any = await getCaseReviewHistory(Number(currentCase.value.testCaseId));
    if (histRes.code === 200) {
      reviewHistory.value = histRes.data || [];
    }
  } catch (e) {
    console.error(e);
  }
};

const handleSubmit = async (next: boolean) => {
  if (!currentCase.value) return;
  if (!myForm.value.conclusion) {
    Message.warning(t('qa.review.conclusionRequired'));
    return;
  }
  const submitComment = myForm.value.conclusion === 'REJECT'
    ? myForm.value.rejectComment
    : myForm.value.passComment;
  if (myForm.value.conclusion === 'REJECT' && !submitComment?.trim()) {
    Message.warning(t('qa.review.rejectCommentRequired'));
    return;
  }
  submitting.value = true;
  try {
    const res: any = await submitTestCaseReview({
      reviewId,
      testCaseId: currentCase.value.testCaseId,
      conclusion: myForm.value.conclusion,
      comment: submitComment,
    });
    if (res.code === 200) {
      Message.success(t('qa.review.submitSuccess'));
      reviewTodoStore.refresh(true);
      const currentId = currentCase.value.testCaseId;
      await loadDetail(true);
      await reloadReviewHistory();
      if (next) {
        const idx = caseList.value.findIndex((c: any) => Number(c.testCaseId) === Number(currentId));
        const isMyTodo = (c: any) => myItemOf(c) && !myConclusion(c);
        const after = caseList.value.slice(idx + 1).find(isMyTodo);
        const before = caseList.value.slice(0, idx + 1).find(isMyTodo);
        const target = after || before;
        if (target) {
          selectCase(target);
        } else {
          Message.success(t('qa.review.allDone'));
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
  router.push({ name: 'qaReviewDetail', params: { id: reviewId } });
};

const statusColor = (s: string) => ({ REVIEWING: 'gold', COMPLETED: 'green', CANCELLED: 'gray' } as Record<string, string>)[s] || 'gray';
const statusText = (s: string) => ({ REVIEWING: t('qa.review.status.reviewing'), COMPLETED: t('qa.review.status.completed'), CANCELLED: t('qa.review.status.cancelled') } as Record<string, string>)[s] || s;
const caseTypeText = (type: string) => ({ FUNCTIONAL: t('qa.review.caseType.functional'), NON_FUNCTIONAL: t('qa.review.caseType.nonFunctional'), REGRESSION: t('qa.review.caseType.regression') } as Record<string, string>)[type] || type || '-';
const priorityColor = (p: string) => ({ P0: 'red', P1: 'orange', P2: 'blue' } as Record<string, string>)[p] || 'gray';
const conclusionColor = (c: string) => (c === 'PASS' ? 'green' : c === 'REJECT' ? 'red' : 'gray');
const conclusionText = (c: string) => (c === 'PASS' ? t('qa.review.resultPass') : c === 'REJECT' ? t('qa.review.resultReject') : t('qa.review.resultPending'));

onMounted(() => {
  loadDetail();
});
</script>

<style scoped lang="less">
.workbench-page {
  padding: 12px 20px 16px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.wb-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.wb-card :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.wb-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
  border-bottom: 1px solid var(--color-border-2);
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.wb-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.wb-code { color: var(--color-text-2); font-size: 13px; }
.wb-name { font-weight: 600; font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 320px; }
.wb-meta { display: flex; gap: 16px; color: var(--color-text-2); font-size: 13px; }
.wb-banner { margin-bottom: 8px; }

.wb-body {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.wb-left {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.wb-left :deep(.arco-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.wb-left :deep(.arco-card-body) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.wb-left-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}
.wb-pager {
  flex-shrink: 0;
  margin-top: 6px;
  justify-content: center;
}
.wb-left-pager {
  flex-shrink: 0;
  padding-top: 6px;
  border-top: 1px solid var(--color-border-2);
}
.wb-left-pager :deep(.arco-pagination) {
  justify-content: center;
}

.wb-left-filter {
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

.wb-right {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.wb-right-scroll {
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
.meta-icon-pass { color: #00b42a; }
.meta-icon-reject { color: #f53f3f; }
.meta-icon-pending { color: #86909c; }

/* 参照 CaseDetailDrawer 的详情 tab 样式 */
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
.tab-count { color: var(--color-text-3); font-size: 12px; }
.section-label { font-weight: 600; margin-bottom: 6px; color: var(--color-text-2); }
.section-content { white-space: pre-wrap; }

.vote-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border-2);
}
.vote-item:last-child { border-bottom: none; }
.vote-avatar { flex-shrink: 0; }
.vote-main { flex: 1; min-width: 0; }
.vote-name-line {
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.vote-name { font-size: 13px; font-weight: 600; }
.vote-review { font-size: 12px; color: var(--color-text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vote-comment { font-size: 13px; color: var(--color-text-2); white-space: pre-wrap; margin-top: 2px; }
.vote-time { font-size: 12px; color: var(--color-text-3); margin-top: 4px; }
.vote-result { flex-shrink: 0; }

.my-review-panel {
  background: var(--color-fill-1);
  border-radius: 8px;
  padding: 12px;
  flex-shrink: 0;
}
.my-review-title {
  font-weight: 600;
  margin-bottom: 10px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.my-review-body {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
/* 评审选项文字保持黑色，不随选中变色 */
.my-review-body :deep(.arco-radio) {
  color: var(--color-text-1);
}
.my-review-body :deep(.arco-radio:hover) {
  color: var(--color-text-1);
}
.my-review-body :deep(.arco-radio-checked) {
  color: var(--color-text-1);
}
.tag-icon {
  margin-right: 2px;
  vertical-align: -1px;
}
.case-status {
  font-size: 14px;
  color: var(--color-text-1);
  display: inline-flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}
.status-icon-pass {
  color: #00b42a;
}
.status-icon-reject {
  color: #f53f3f;
}
.status-icon-pending {
  color: #86909c;
}
.radio-icon-pass {
  color: #00b42a;
  margin-right: 4px;
  vertical-align: -2px;
}
.radio-icon-reject {
  color: #f53f3f;
  margin-right: 4px;
  vertical-align: -2px;
}
</style>
