<template>
  <div class="review-page" v-if="projectStore.hasProjectSelected">
    <div class="rv-header">
      <a-space>
        <a-button size="small" @click="goBack">
          <template #icon><IconLeft /></template>
          {{ $t('qa.ai.common.back') }}
        </a-button>
        <span class="rv-req-code">{{ reqData?.reqCode }}</span>
        <span class="rv-req-title">{{ reqData?.title }}</span>
      </a-space>
      <a-space size="small">
        <a-button v-if="reqData?.description" size="small" @click="sourceVisible = !sourceVisible">
          <template #icon><IconFile /></template>
          {{ sourceVisible ? $t('qa.ai.review.hideSource') : $t('qa.ai.review.showSource') }}
        </a-button>
        <a-button v-if="!finalized && !latestReview" type="primary" size="small" @click="showDialog = true">{{ $t('qa.ai.review.start') }}</a-button>
        <a-button v-if="latestReview && !finalized" size="small" type="primary" status="success" @click="doFinalize">{{ $t('qa.ai.review.finalize') }}</a-button>
        <a-tag v-if="finalized" color="green" size="small">{{ $t('qa.ai.review.locked') }}</a-tag>
        <a-button v-if="latestReview" size="small" @click="exportReport">
          <template #icon><IconDownload /></template>
          {{ $t('qa.ai.review.export') }}
        </a-button>
        <a-popconfirm v-if="latestReview" :content="finalized ? $t('qa.ai.review.restartConfirmFinalized') : $t('qa.ai.review.restartConfirm')" :ok-text="$t('qa.ai.common.ok')" :cancel-text="$t('qa.common.cancel')" @ok="restartReview">
          <a-button :status="finalized ? 'warning' : 'normal'" size="small">{{ $t('qa.ai.review.restart') }}</a-button>
        </a-popconfirm>
      </a-space>
    </div>

    <div class="rv-body" :class="{ 'with-source': sourceVisible }">
      <div class="rv-main">
        <!-- 评审结果展示（只读） -->
        <div v-if="latestReview" class="rv-result">
          <ReviewResult :review="latestReview" :risks="sortRisks(latestReview.risks || [])" :reused="false" @locate="locateQuote" />
        </div>

        <!-- 空状态 -->
        <a-empty v-else-if="!loading" :description="$t('qa.ai.review.emptyHint')">
          <template #extra>
            <a-button type="primary" @click="showDialog = true">{{ $t('qa.ai.review.start') }}</a-button>
          </template>
        </a-empty>

        <!-- 加载中（进度面板：Vue 直接渲染 ReviewProgressNode） -->
        <div v-if="loading" class="rv-loading-panel">
          <a-progress v-if="progressItems.length > 0" :percent="progressPercent" size="small" style="margin-bottom: 12px" />
          <ReviewProgressNode v-for="item in progressItems" :key="item.dim"
            :dim="item.dim" :status="item.status" :score="item.score" />
          <div class="rv-loading-status">
            <a-spin :size="20" />
            <span style="margin-left: 8px">{{ statusText }}</span>
          </div>
        </div>
      </div>

      <transition name="slide">
        <div v-show="sourceVisible" class="rv-source-panel">
          <RequirementSource
            ref="sourceRef"
            :title="reqData?.title"
            :html="reqData?.description"
            :saving="sourceSaving"
            @save="handleSaveSource"
          />
        </div>
      </transition>
    </div>

    <!-- 评审指令弹窗 -->
    <a-modal v-model:visible="showDialog" :title="$t('qa.ai.review.dialogTitle')" @ok="startReview" :ok-loading="loading" :ok-text="$t('qa.ai.review.dialogOk')" :cancel-text="$t('qa.common.cancel')" :mask-closible="false">
      <a-textarea
        v-model="instructionText"
        :placeholder="$t('qa.ai.review.instructionPlaceholder')"
        :auto-size="{ minRows: 3, maxRows: 6 }"
        @keydown.enter.ctrl="startReview"
      />
      <p style="margin-top: 8px; color: var(--color-text-3); font-size: 12px;">{{ $t('qa.ai.review.ctrlEnterTip') }}</p>
    </a-modal>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
export default { name: 'AiReview' };
</script>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { IconLeft, IconDownload, IconFile } from '@arco-design/web-vue/es/icon';
import { checklistText, getDims, dimOfId, answerText, levelText } from './review/constants';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import { useProjectStore } from '@/store';
import { getRequirementDetail, updateRequirement } from '@/api/MyApi/qa';
import { streamReviewAgent, clearReviewMemory, finalizeReview, listReviews, getReviewById, getReviewProgressStatus, resumeReviewProgress } from '@/api/MyApi/ai';
import ReviewResult from './review/ReviewResult.vue';
import RequirementSource from './review/RequirementSource.vue';
import ReviewProgressNode from './review/ReviewProgressNode.vue';

const route = useRoute();
const router = useRouter();
const projectStore = useProjectStore();
const { t } = useI18n();

const reqId = computed(() => { const n = Number(route.query.reqId); return Number.isNaN(n) ? 0 : n; });
const reqData = ref<any>(null);
const latestReview = ref<any>(null);
const finalized = ref(false);
const loading = ref(false);
const statusText = ref('');
const showDialog = ref(false);
const instructionText = ref('');
let abortCtrl: AbortController | null = null;

// 评审进度面板（Vue 直接渲染，响应式更新组件 props）
const progressItems = ref<Array<{dim: string, status: string, score?: number}>>([]);
const progressPercent = computed(() => {
  if (progressItems.value.length === 0) return 0;
  const done = progressItems.value.filter(p => p.status === 'done').length;
  return Math.round((done / 6) * 100);
});

/** 收到进度事件时的统一处理：新维度 running → 之前 running 自动 done */
function handleProgress(data: any) {
  if (data.status === 'running') {
    progressItems.value.forEach(item => {
      if (item.status === 'running') item.status = 'done';
    });
  }
  const idx = progressItems.value.findIndex(p => p.dim === data.dim);
  if (idx >= 0) progressItems.value[idx] = { ...progressItems.value[idx], ...data };
  else progressItems.value.push(data);
}

const sourceRef = ref();
const sourceSaving = ref(false);
const sourceVisible = ref(false);

function goBack() {
  const from = route.query.from as string;
  router.push({ name: from || 'Requirement' });
}

function scoreColor(s?: number) {
  if (s == null) return '#86909c';
  return s >= 70 ? '#00b42a' : s >= 50 ? '#ff7d00' : '#f53f3f';
}

function sortRisks(list: any[]) {
  const order: Record<string, number> = { HIGH: 0, MEDIUM: 1, LOW: 2 };
  return [...list].sort((a, b) => (order[a.level] ?? 9) - (order[b.level] ?? 9));
}

function locateQuote(quote: string) {
  if (sourceRef.value && quote) sourceRef.value.locateQuote(quote);
}

async function handleSaveSource(html: string) {
  sourceSaving.value = true;
  try {
    await updateRequirement({ ...reqData.value, description: html });
    if (reqData.value) reqData.value.description = html;
    sourceRef.value?.exitEdit();
    Message.success(t('qa.ai.review.sourceSaved'));
  } catch { Message.error(t('qa.ai.review.saveFailed')); }
  finally { sourceSaving.value = false; }
}

// ==================== 评审 ====================

function startReview() {
  if (!reqId.value || loading.value) return;
  showDialog.value = false;
  loading.value = true;
  statusText.value = t('qa.ai.review.running');
  progressItems.value = [];
  abortCtrl = new AbortController();

  streamReviewAgent(
    { requirementId: reqId.value, message: instructionText.value || '请进行全面评审' },
    {
      onStatus: (s: string) => { statusText.value = s; },
      onProgress: (data: any) => handleProgress(data),
      onDone: (data: any) => {
        loading.value = false;
        progressItems.value = [];
        latestReview.value = data;
      },
      onError: (msg: string) => {
        loading.value = false;
        progressItems.value = [];
        Message.error(t('qa.ai.review.failed', { msg }));
      },
    },
    abortCtrl.signal,
  );
}

async function doFinalize() {
  try {
    await finalizeReview(reqId.value);
    finalized.value = true;
    exportReport();
    Message.success(t('qa.ai.review.finalizeSuccess'));
  } catch { Message.error(t('qa.ai.review.finalizeFailed')); }
}

async function restartReview() {
  loading.value = false;
  latestReview.value = null;
  finalized.value = false;
  instructionText.value = '';
  try { await clearReviewMemory(reqId.value); } catch { /* ignore */ }
  showDialog.value = true;
}

// ==================== 导出报告 ====================

function exportReport() {
  if (!latestReview.value) return;
  const r = latestReview.value, req = reqData.value;
  const dims = getDims();
  const dimLabel = (k: string) => dims.find(d => d.key === k)?.label ?? k;
  const esc = (s?: string) => (s == null ? '' : String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;'));
  const checklistByDim: Record<string, any[]> = {};
  for (const item of (r.checklist || [])) { const dim = dimOfId(item.id); if (!checklistByDim[dim]) checklistByDim[dim] = []; checklistByDim[dim].push(item); }
  const checklistHtml = dims.map(d => {
    const items = checklistByDim[d.key] || []; if (!items.length) return '';
    const rows = items.map((it: any) => {
      const ans = answerText(it.answer);
      const ansColor = it.answer === 'YES' ? '#00b42a' : it.answer === 'PARTIAL' ? '#ff7d00' : '#f53f3f';
      const quote = it.quote ? `<span class="quote">"${esc(it.quote)}"</span>` : '';
      return `<tr><td>${it.id}</td><td>${esc(checklistText(it.id))}</td><td style="color:${ansColor};font-weight:600">${ans}</td><td>${quote}</td></tr>`;
    }).join('');
    const score = r.scores?.[d.key];
    const scoreBadge = score != null ? `<span style="color:${scoreColor(score)};font-weight:700">${score}</span>` : '';
    return `<h3>${d.label} ${scoreBadge}</h3><table><thead><tr><th>ID</th><th>${t('qa.ai.review.report.thCheckItem')}</th><th>${t('qa.ai.review.report.thResult')}</th><th>${t('qa.ai.review.report.thQuote')}</th></tr></thead><tbody>${rows}</tbody></table>`;
  }).join('');
  const risksHtml = (r.risks || []).length ? r.risks.map((rk: any) => {
    const lv = levelText(rk.level);
    const lvColor = rk.level === 'HIGH' ? '#f53f3f' : rk.level === 'MEDIUM' ? '#ff7d00' : '#00b42a';
    return `<div class="risk-item"><span class="risk-level" style="background:${lvColor}">${lv}</span><span class="risk-dim">${esc(dimLabel(rk.dimension))}</span><span class="risk-desc">${esc(rk.desc || '')}</span>${rk.suggestion ? `<span class="risk-sug">${t('qa.ai.review.report.suggestionPrefix')}${esc(rk.suggestion)}</span>` : ''}</div>`;
  }).join('') : `<p>${t('qa.ai.review.report.noRisks')}</p>`;
  const now = new Date();
  const dateStr = `${now.getFullYear()}-${String(now.getMonth()+1).padStart(2,'0')}-${String(now.getDate()).padStart(2,'0')} ${String(now.getHours()).padStart(2,'0')}:${String(now.getMinutes()).padStart(2,'0')}`;
  const html = `<!DOCTYPE html><html lang="zh"><head><meta charset="utf-8"><title>${t('qa.ai.review.report.title')} - ${esc(req?.title)}</title>
<style>*{margin:0;padding:0;box-sizing:border-box}body{font-family:-apple-system,'PingFang SC','Microsoft YaHei',sans-serif;padding:32px;color:#1d2129;max-width:900px;margin:0 auto}h1{font-size:22px;margin-bottom:4px}.meta{font-size:12px;color:#86909c;margin-bottom:24px}.score-overview{display:flex;gap:24px;align-items:center;margin-bottom:24px;padding:20px;background:#f7f8fa;border-radius:8px}.score-total{text-align:center}.score-total .num{font-size:36px;font-weight:800}.score-total .label{font-size:12px;color:#86909c}.score-dims{display:grid;grid-template-columns:repeat(3,1fr);gap:8px 20px}.score-dim{display:flex;justify-content:space-between}.score-dim .label{font-size:13px;color:#4e5969}.score-dim .val{font-weight:700}h2{font-size:16px;margin:24px 0 12px;padding-bottom:6px;border-bottom:2px solid #e5e6eb}h3{font-size:14px;margin:16px 0 8px}table{width:100%;border-collapse:collapse;font-size:12px;margin-bottom:8px}th{background:#f7f8fa;text-align:left;padding:6px 8px;border-bottom:1px solid #e5e6eb;font-weight:600}td{padding:6px 8px;border-bottom:1px solid #f2f3f5;vertical-align:top}.quote{color:#86909c;font-style:italic}.risk-item{padding:8px 0;border-bottom:1px solid #f2f3f5;font-size:13px;display:flex;gap:8px;align-items:flex-start}.risk-level{color:#fff;font-size:11px;padding:1px 8px;border-radius:4px;flex-shrink:0}.risk-dim{color:#86909c;font-size:12px;flex-shrink:0;width:60px}.risk-desc{flex:1}.risk-sug{color:#4e5969;flex-basis:100%;padding-left:28px;font-size:12px}.summary{font-size:13px;line-height:1.7;white-space:pre-wrap;padding:16px;background:#f7f8fa;border-radius:8px}@media print{body{padding:16px}}</style></head><body>
<h1>${t('qa.ai.review.report.title')}</h1><div class="meta">${esc(req?.reqCode)} | ${esc(req?.title)} | ${t('qa.ai.review.report.reviewTime')}${dateStr}</div>
<div class="score-overview"><div class="score-total"><div class="num" style="color:${scoreColor(r.overall || 0)}">${r.overall ?? '—'}</div><div class="label">${t('qa.ai.review.report.overallPer100')}</div></div><div class="score-dims">${dims.map(d => { const s = r.scores?.[d.key]; return `<div class="score-dim"><span class="label">${d.label}</span><span class="val" style="color:${s!=null?scoreColor(s):'#86909c'}">${s ?? '—'}</span></div>`; }).join('')}</div></div>
<h2>${t('qa.ai.review.report.checklistDetail')}</h2>${checklistHtml}<h2>${t('qa.ai.review.report.riskList')}</h2>${risksHtml}<h2>${t('qa.ai.review.report.aiSummary')}</h2><div class="summary">${esc(r.summary) || t('qa.ai.review.report.noSummary')}</div></body></html>`;
  const w = window.open('', '_blank');
  if (!w) { Message.warning(t('qa.ai.review.allowPopup')); return; }
  w.document.write(html); w.document.close(); w.onload = () => { setTimeout(() => w.print(), 300); };
}

// ==================== 生命周期 ====================
async function init() {
  if (!reqId.value) return;
  try {
    const res: any = await getRequirementDetail(reqId.value);
    if (res.data) { reqData.value = res.data; finalized.value = res.data.aiReviewStatus === 'FINALIZED'; }
  } catch { Message.error(t('qa.ai.review.reqNotFound')); return; }

  // 加载已有评审结果
  try {
    const listRes: any = await listReviews(reqId.value, 1, 1);
    const records = listRes.data?.records || [];
    if (records.length > 0) {
      const latest = records[0];
      if (latest?.id) {
        const detailRes: any = await getReviewById(latest.id);
        if (detailRes.data) {
          const data = parseReviewDetail(detailRes.data);
          latestReview.value = data;
        }
      }
    }
  } catch { /* ignore */ }

  // 检查是否有进行中的评审（断线恢复）
  try {
    const statusRes: any = await getReviewProgressStatus(reqId.value);
    if (statusRes.data?.active && !latestReview.value) {
      // 有进行中的评审：恢复进度面板
      loading.value = true;
      statusText.value = t('qa.ai.review.running');
      progressItems.value = [];
      abortCtrl = new AbortController();
      resumeReviewProgress(reqId.value, {
        onRestore: (items: any[]) => { progressItems.value = items; },
        onProgress: (data: any) => handleProgress(data),
        onDone: (data: any) => {
          loading.value = false;
          progressItems.value = [];
          if (data && Object.keys(data).length > 0) latestReview.value = data;
        },
        onError: (msg: string) => {
          loading.value = false;
          progressItems.value = [];
          Message.error(t('qa.ai.review.resumeFailed', { msg }));
        },
      }, abortCtrl.signal);
    }
  } catch { /* ignore */ }
}

function parseReviewDetail(data: any) {
  const scores: any = {};
  if (data.scoresJson) {
    try { Object.assign(scores, JSON.parse(data.scoresJson)); } catch { /* ignore */ }
  }
  let risks: any[] = [];
  if (data.risksJson) {
    try { risks = JSON.parse(data.risksJson); } catch { /* ignore */ }
  }
  let checklist: any[] = [];
  if (data.checklistJson) {
    try { checklist = JSON.parse(data.checklistJson); } catch { /* ignore */ }
  }
  return {
    scores,
    overall: data.overallScore,
    risks,
    checklist,
    confidence: data.confidence,
    summary: data.summary,
  };
}

onMounted(() => { if (reqId.value) init(); });
</script>

<style scoped>
.review-page { position: relative; height: var(--page-container-height, calc(100vh - 60px)); display: flex; flex-direction: column; overflow: hidden; }
.rv-header { display: flex; justify-content: space-between; align-items: center; flex-shrink: 0; padding: 0 16px; height: 48px; background: var(--color-bg-1); border-bottom: 1px solid var(--color-border-2); }
.rv-req-code { font-size: 12px; color: var(--color-text-3); }
.rv-req-title { font-size: 16px; font-weight: 600; }
.rv-body { flex: 1; min-height: 0; display: flex; overflow: hidden; }
.rv-main { flex: 1; min-width: 0; overflow-y: auto; padding: 16px; }
.rv-source-panel { width: 340px; flex-shrink: 0; border-left: 1px solid var(--color-border-2); overflow-y: auto; background: var(--color-bg-1); }
.rv-result { max-width: 900px; margin: 0 auto; }
.rv-loading { display: flex; align-items: center; justify-content: center; height: 200px; color: var(--color-text-2); }
.rv-loading-panel { max-width: 900px; margin: 0 auto; padding: 20px 0; }
.rv-loading-status { display: flex; align-items: center; justify-content: center; padding: 12px 0; color: var(--color-text-2); }
</style>
