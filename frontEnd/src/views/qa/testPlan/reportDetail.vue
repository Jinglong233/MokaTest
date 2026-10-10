<template>
  <div class="report-detail-page" v-loading="loading">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testPlan', report.reportName || $t('qa.testPlan.report.detail')]"/>
    <template v-if="content">
      <div class="rd-body">
      <!-- 报告名称 + 计划执行时间 + 导出 -->
      <a-card class="rd-card rd-header-card" size="small">
        <div class="rd-header">
          <div class="rd-title">{{ report.reportName }}</div>
          <div class="rd-header-right">
            <span class="rd-exec-time" v-if="content.startTime || content.endTime">
              {{ $t('qa.testPlan.report.execTime', { start: content.startTime || '-', end: content.endTime || '-' }) }}
            </span>
            <a-button type="primary" @click="handlePrint">
              <template #icon><icon-printer /></template>
              {{ $t('qa.testPlan.report.exportPdf') }}
            </a-button>
          </div>
        </div>
      </a-card>

      <div id="report-print-area">
        <!-- 三个统计卡片 -->
        <div class="rd-stat-row">
          <!-- 报告分析 -->
          <a-card class="rd-card rd-stat-card" size="small">
              <div class="rd-card-title">{{ $t('qa.testPlan.report.analysis') }}</div>
              <div class="rd-analysis-list">
                <div class="rd-analysis-item">
                  <span class="rd-analysis-label">{{ $t('qa.testPlan.report.threshold') }}</span>
                  <span class="rd-analysis-value">-</span>
                </div>
                <div class="rd-analysis-item">
                  <span class="rd-analysis-label">{{ $t('qa.testPlan.passRate') }}</span>
                  <span class="rd-analysis-value" style="color:#00b42a">{{ pct(content.passRate) }}</span>
                </div>
                <div class="rd-analysis-item">
                  <span class="rd-analysis-label">{{ $t('qa.testPlan.report.execRate') }}</span>
                  <span class="rd-analysis-value">{{ pct(content.executeRate) }}</span>
                </div>
                <div class="rd-analysis-item">
                  <span class="rd-analysis-label">{{ $t('qa.testPlan.report.totalBugs') }}</span>
                  <span class="rd-analysis-value" style="color:#f53f3f">{{ content.totalBug || 0 }}</span>
                </div>
              </div>
            </a-card>

          <!-- 执行分析（环形图，误报为预留展示位） -->
          <a-card class="rd-card rd-stat-card" size="small">
              <div class="rd-card-title">{{ $t('qa.testPlan.report.execAnalysis') }}</div>
              <Chart :options="execPieOption" height="200px" />
            </a-card>

          <!-- 功能用例分析 -->
          <a-card class="rd-card rd-stat-card" size="small">
              <div class="rd-card-title">{{ $t('qa.testPlan.report.caseAnalysis') }}</div>
              <Chart :options="casePieOption" height="130px" />
              <div class="rd-case-bars">
                <div v-for="item in caseBarItems" :key="item.name" class="rd-case-bar-row">
                  <span class="rd-case-bar-label">{{ item.name }}</span>
                  <div class="rd-case-bar-bg">
                    <div class="rd-case-bar-fill" :style="{ width: item.rate + '%', background: item.color }"></div>
                  </div>
                  <span class="rd-case-bar-val">{{ item.value }}（{{ item.rate }}%）</span>
                </div>
              </div>
            </a-card>
          </div>

        <!-- 报告总结 -->
        <a-card class="rd-card rd-section-card" size="small">
            <div class="rd-card-title rd-summary-head">
              {{ $t('qa.testPlan.report.summary') }}
              <a-button size="mini" type="primary" :loading="summarySaving" @click="handleSaveSummary">{{ $t('qa.common.save') }}</a-button>
            </div>
            <RichEditor v-model="summary" height="180px" />
          </a-card>

        <!-- 缺陷列表 -->
        <a-card class="rd-card rd-section-card" size="small">
            <div class="rd-card-title">{{ $t('qa.testPlan.report.bugList', { count: bugList.length }) }}</div>
            <a-table :data="bugList" :pagination="false" size="small" row-key="bugId" :scroll="{ y: 360 }" class="rd-table">
              <template #columns>
                <a-table-column :title="$t('qa.testPlan.exec.bugCode')" :width="130">
                  <template #cell="{ record }">
                    <a-link @click="goBug(record)">{{ record.bugCode }}</a-link>
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.testPlan.report.bugName')" data-index="title" :ellipsis="true"/>
                <a-table-column :title="$t('qa.bug.status')" :width="100" align="center" header-align="center">
                  <template #cell="{ record }">
                    <a-tag size="small" :color="bugStatusColor(record.status)">{{ bugStatusText(record.status) }}</a-tag>
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.testPlan.assignee')" data-index="assigneeName" :width="110" align="center" header-align="center">
                  <template #cell="{ record }">{{ record.assigneeName || '-' }}</template>
                </a-table-column>
                <a-table-column :title="$t('qa.testPlan.caseCount')" data-index="caseCount" :width="90" align="center" header-align="center"/>
              </template>
              <template #empty><a-empty :description="$t('qa.testPlan.report.noBugs')" /></template>
            </a-table>
          </a-card>

        <!-- 用例明细 -->
        <a-card class="rd-card rd-section-card" size="small">
            <div class="rd-card-title">{{ $t('qa.testPlan.report.caseDetails', { count: caseDetails.length }) }}</div>
            <a-table :data="caseDetails" :pagination="false" size="small" row-key="planCaseId" :scroll="{ y: 360 }" class="rd-table">
              <template #columns>
                <a-table-column :title="$t('qa.testPlan.exec.bugCode')" :width="130">
                  <template #cell="{ record }">
                    <a-link @click="goCase(record)">{{ record.caseCode }}</a-link>
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.testCase.name')" data-index="caseName" :ellipsis="true"/>
                <a-table-column :title="$t('qa.testPlan.execResult')" :width="100" align="center" header-align="center">
                  <template #cell="{ record }">
                    <a-tag size="small" :color="resultColor(record.executeResult)">
                      <template #icon><component :is="resultIcon(record.executeResult)" /></template>
                      {{ resultText(record.executeResult) }}
                    </a-tag>
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.testPlan.caseLevel')" :width="90" align="center" header-align="center">
                  <template #cell="{ record }">
                    <a-tag size="small" :color="priorityColor(record.priority)">
                      <template #icon><icon-flag /></template>
                      {{ record.priority || '-' }}
                    </a-tag>
                  </template>
                </a-table-column>
                <a-table-column :title="$t('qa.testPlan.module')" data-index="moduleName" :width="130">
                  <template #cell="{ record }">{{ record.moduleName || '-' }}</template>
                </a-table-column>
                <a-table-column :title="$t('qa.testPlan.executor')" data-index="executorName" :width="100" align="center" header-align="center">
                  <template #cell="{ record }">{{ record.executorName || '-' }}</template>
                </a-table-column>
                <a-table-column :title="$t('qa.testPlan.bugCount')" data-index="bugCount" :width="90" align="center" header-align="center">
                  <template #cell="{ record }">
                    <span :style="{ color: record.bugCount > 0 ? '#f53f3f' : '#86909c' }">{{ record.bugCount || 0 }}</span>
                  </template>
                </a-table-column>
              </template>
              <template #empty><a-empty :description="$t('qa.testPlan.report.noCases')" /></template>
            </a-table>
          </a-card>
      </div>
      </div>
    </template>
    <a-empty v-else-if="!loading" :description="$t('qa.testPlan.report.notFound')" style="margin-top: 80px" />
  </div>
</template>

<script lang="ts">
  export default { name: 'TestPlanReportDetail' };
</script>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import Chart from '@/components/chart/index.vue';
import RichEditor from '@/components/rich-editor/index.vue';
import { getPlanReportDetail, updatePlanReportSummary } from '@/api/MyApi/qa';

const route = useRoute();
const router = useRouter();
const { t } = useI18n();
const reportId = Number(route.params.id);

const loading = ref(false);
const report = ref<any>({});
// content 为生成时固化的快照 JSON
const content = ref<any>(null);
const summary = ref('');
const summarySaving = ref(false);

const bugList = computed<any[]>(() => content.value?.bugList || []);
const caseDetails = computed<any[]>(() => content.value?.caseDetails || []);

const pct = (v: number) => (v == null ? '0%' : `${v}%`);

// 执行分析环形图：未执行/成功/误报（预留恒0）/阻塞/失败
const execPieOption = computed(() => {
  const d = content.value || {};
  return {
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, itemWidth: 10, itemHeight: 10, textStyle: { fontSize: 12 } },
    color: ['#c9cdd4', '#00b42a', '#722ed1', '#ff7d00', '#f53f3f'],
    series: [{
      type: 'pie', radius: ['40%', '65%'], center: ['50%', '42%'],
      label: { show: true, formatter: '{b} {c}', fontSize: 11 },
      data: [
        { name: t('qa.testPlan.result.unexecuted'), value: d.unexec || 0 },
        { name: t('qa.testPlan.result.success'), value: d.pass || 0 },
        { name: t('qa.testPlan.report.falsePositive'), value: d.falsePositive || 0 },
        { name: t('qa.testPlan.result.block'), value: d.block || 0 },
        { name: t('qa.testPlan.result.fail'), value: d.fail || 0 },
      ],
    }],
  };
});

// 功能用例分析：环形图 + 分段占比
const CASE_SEGS = computed(() => [
  { key: 'unexec', name: t('qa.testPlan.result.unexecuted'), color: '#c9cdd4' },
  { key: 'pass', name: t('qa.testPlan.result.success'), color: '#00b42a' },
  { key: 'block', name: t('qa.testPlan.result.block'), color: '#ff7d00' },
  { key: 'fail', name: t('qa.testPlan.result.fail'), color: '#f53f3f' },
]);
const casePieOption = computed(() => {
  const d = content.value || {};
  return {
    tooltip: { trigger: 'item' },
    color: CASE_SEGS.value.map(s => s.color),
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '50%'],
      label: { show: false },
      data: CASE_SEGS.value.map(s => ({ name: s.name, value: d[s.key] || 0 })),
    }],
  };
});
const caseBarItems = computed(() => {
  const d = content.value || {};
  const total = d.total || 0;
  return CASE_SEGS.value.map(s => ({
    ...s,
    value: d[s.key] || 0,
    rate: total > 0 ? Math.round((d[s.key] || 0) * 100 / total) : 0,
  }));
});

const bugStatusText = (s: string) => ({
  NEW: t('qa.testPlan.bugStatus.new'), CONFIRMED: t('qa.testPlan.bugStatus.confirmed'), FIXING: t('qa.testPlan.bugStatus.fixing'), FIXED: t('qa.testPlan.bugStatus.fixed'), VERIFIED: t('qa.testPlan.bugStatus.verified'), CLOSED: t('qa.testPlan.bugStatus.closed'), REJECTED: t('qa.testPlan.bugStatus.rejected'),
} as Record<string, string>)[s] || s || '-';
const bugStatusColor = (s: string) => ({
  NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold', FIXED: 'green', VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray',
} as Record<string, string>)[s] || 'gray';
const resultText = (r: string) => ({ PASS: t('qa.testPlan.result.success'), FAIL: t('qa.testPlan.result.fail'), BLOCK: t('qa.testPlan.result.block'), NA: t('qa.testPlan.result.na') } as Record<string, string>)[r] || t('qa.testPlan.result.unexecuted');
const resultColor = (r: string) => ({ PASS: 'green', FAIL: 'red', BLOCK: 'orange' } as Record<string, string>)[r] || 'gray';
const resultIcon = (r: string) => ({
  PASS: 'icon-check-circle-fill',
  FAIL: 'icon-close-circle-fill',
  BLOCK: 'icon-exclamation-circle-fill',
  NA: 'icon-minus-circle-fill',
} as Record<string, string>)[r] || 'icon-clock-circle';
const priorityColor = (p: string) => ({ P0: 'red', P1: 'orange', P2: 'blue' } as Record<string, string>)[p] || 'gray';

const loadDetail = async () => {
  if (!reportId) return;
  loading.value = true;
  try {
    const res: any = await getPlanReportDetail(reportId);
    if (res.code === 200 && res.data) {
      report.value = res.data;
      summary.value = res.data.summary || '';
      try {
        content.value = res.data.content ? JSON.parse(res.data.content) : null;
      } catch {
        content.value = null;
        Message.error(t('qa.testPlan.report.parseFail'));
      }
      // 从列表「导出」进入时自动打印
      if (route.query.print === '1' && content.value) {
        setTimeout(handlePrint, 500);
      }
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleSaveSummary = async () => {
  summarySaving.value = true;
  try {
    const res: any = await updatePlanReportSummary(reportId, summary.value);
    if (res.code === 200) Message.success(t('qa.testPlan.saveSuccess'));
  } catch (e) {
    console.error(e);
  } finally {
    summarySaving.value = false;
  }
};

// 跳转缺陷列表/用例列表并打开对应详情抽屉（源数据已删除时目标页提示不存在）
const goBug = (record: any) => {
  router.push({ name: 'Bug', query: { id: record.bugId } });
};
const goCase = (record: any) => {
  router.push({ name: 'TestCase', query: { id: record.testCaseId } });
};

const handlePrint = () => {
  const printWindow = window.open('', '_blank');
  if (!printWindow) return;
  const html = document.getElementById('report-print-area')?.innerHTML || '';
  printWindow.document.write(`
    <html><head><title>${report.value.reportName || t('qa.testPlan.report.defaultTitle')}</title>
    <style>
      body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; padding: 40px; color: #1d2129; }
      table { width: 100%; border-collapse: collapse; font-size: 13px; }
      th, td { padding: 8px 12px; text-align: left; border-bottom: 1px solid #e5e6eb; }
      th { background: #f7f8fa; font-weight: 600; }
      .rd-card-title { font-size: 15px; font-weight: 600; margin: 16px 0 10px; }
      .rd-analysis-item { display: flex; justify-content: space-between; padding: 4px 0; }
    </style></head><body><h2>${report.value.reportName || t('qa.testPlan.report.defaultTitle')}</h2>${html}</body></html>
  `);
  printWindow.document.close();
  setTimeout(() => printWindow.print(), 300);
};

onMounted(loadDetail);
</script>

<style scoped lang="less">
.report-detail-page {
  padding: 12px 20px 16px;
  height: var(--page-container-height, calc(100vh - 60px));
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  overflow: hidden;
}

// 去掉本页所有卡片边框，卡片之间留出间隔
.rd-card.arco-card {
  border: none;
}

.rd-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;

  .rd-card + .rd-card,
  .rd-stat-row + .rd-card,
  .rd-card + .rd-stat-row,
  #report-print-area .rd-section-card {
    margin-top: 12px;
  }
}
.rd-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.rd-title { font-size: 18px; font-weight: 700; }
.rd-header-right { display: flex; align-items: center; gap: 16px; }
.rd-exec-time { font-size: 13px; color: var(--color-text-2); }

.rd-stat-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: 12px;
}
.rd-card-title { font-weight: 600; margin-bottom: 10px; }
.rd-analysis-list { display: flex; flex-direction: column; gap: 10px; padding: 8px 0; }
.rd-analysis-item { display: flex; justify-content: space-between; font-size: 13px; }
.rd-analysis-label { color: var(--color-text-3); }
.rd-analysis-value { font-weight: 600; }

.rd-case-bars { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
.rd-case-bar-row { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.rd-case-bar-label { width: 40px; color: var(--color-text-2); flex-shrink: 0; }
.rd-case-bar-bg { flex: 1; height: 8px; background: #f2f3f5; border-radius: 4px; overflow: hidden; }
.rd-case-bar-fill { height: 100%; border-radius: 4px; }
.rd-case-bar-val { width: 76px; text-align: right; color: var(--color-text-3); flex-shrink: 0; }

.rd-section-card { margin-top: 12px; }
.rd-summary-head { display: flex; align-items: center; justify-content: space-between; }
</style>
