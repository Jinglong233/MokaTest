<template>
  <div class="dim-board">
    <!-- 需求级收敛横幅 -->
    <div v-if="reqStaleConverged" class="banner banner-stale">
      <IconExclamationCircle /> {{ $t('qa.ai.caseGen.staleAudit') }}
    </div>
    <div v-else-if="reqConverged" class="banner banner-ok">
      <IconCheckCircle /> {{ archivedAudit?.overallSummary ? $t('qa.ai.caseGen.auditPassedWithSummary', { summary: archivedAudit.overallSummary }) : $t('qa.ai.caseGen.auditPassed') }}
    </div>

    <!-- 头部：维度统计 + 归档 -->
    <div class="db-header">
      <span class="db-title">{{ $t('qa.ai.caseGen.dimTitle') }}</span>
      <span v-if="dimensions.length" class="db-summary">
        {{ $t('qa.ai.caseGen.covered', { covered: coveredCount, total: dimensions.length }) }}
        <template v-if="issueCount"> · <span class="warn-text">{{ $t('qa.ai.caseGen.issues', { count: issueCount }) }}</span></template>
      </span>
      <span v-else class="db-summary">{{ $t('qa.ai.caseGen.autoListShort') }}</span>
      <span v-if="generating && roundInfo" class="db-round">{{ $t('qa.ai.caseGen.completingRound', { round: roundInfo.round }) }}</span>
      <a-button v-if="allCovered && !generating && !pendingDraftCount" size="mini" type="outline" @click="$emit('archive')">{{ $t('qa.ai.caseGen.archive') }}</a-button>
    </div>

    <!-- 维度清单 -->
    <div v-if="dimensions.length" class="dim-list">
      <div v-for="dim in dimensions" :key="dim.id" class="dim-row">
        <span class="dim-icon">
          <IconCheckCircle v-if="dim.status === 'COVERED'" style="color: rgb(var(--success-6))" />
          <IconLoading v-else-if="dim.status === 'GENERATING'" style="color: rgb(var(--primary-6))" />
          <IconExclamationCircle v-else-if="dim.status === 'ISSUE'" style="color: rgb(var(--warning-6))" />
          <IconCloseCircle v-else-if="dim.status === 'FAILED'" style="color: rgb(var(--danger-6))" />
          <IconMinus v-else style="color: var(--color-text-4)" />
        </span>
        <div class="dim-main">
          <div class="dim-line1">
            <span class="dim-title">{{ dim.title }}</span>
            <span v-if="dim.category" class="dim-category">{{ categoryText(dim.category) }}</span>
            <span v-for="(cn, i) in (dim.caseNames || []).slice(0, 3)" :key="i" class="dim-case">{{ cn }}</span>
          </div>
          <div v-if="dim.issue" class="dim-issue">
            {{ dim.issue }}
            <a-button size="mini" type="text" :disabled="generating" @click="$emit('retry-dim', dim)">{{ $t('qa.ai.caseGen.retryDim') }}</a-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 全部覆盖横幅 -->
    <div v-if="allCovered && !generating" class="banner banner-ok" style="margin-top: 8px">
      <IconCheckCircle /> {{ $t('qa.ai.caseGen.allCoveredTip') }}
    </div>

    <!-- 审查详情（折叠，人话术语） -->
    <div v-if="audit" class="audit-detail">
      <div class="audit-toggle" @click="auditExpanded = !auditExpanded">
        {{ auditExpanded ? $t('qa.ai.caseGen.collapseReport') : $t('qa.ai.caseGen.viewReport') }}
        <span v-if="audit.overallSummary" class="audit-summary-inline">{{ audit.overallSummary }}</span>
      </div>
      <template v-if="auditExpanded">
        <div v-if="claimedOnlyList.length" class="audit-section">
          <div class="audit-sec-title warn-text">{{ $t('qa.ai.caseGen.claimedOnlyTitle', { count: claimedOnlyList.length }) }}</div>
          <div v-for="(v, i) in claimedOnlyList" :key="'c'+i" class="audit-item">
            {{ $t('qa.ai.caseGen.claimedOnlyItem', { name: v.claimedBy, tpId: v.tpId, reason: v.reason }) }}
          </div>
        </div>
        <div v-if="missingList.length" class="audit-section">
          <div class="audit-sec-title">{{ $t('qa.ai.caseGen.missingTitle', { count: missingList.length }) }}</div>
          <div v-for="(m, i) in missingList" :key="'m'+i" class="audit-item">
            {{ $t('qa.ai.caseGen.missingItem', { title: m.title, category: categoryText(m.category), reason: m.reason }) }}
          </div>
        </div>
        <div v-if="draftCoveredCount" class="audit-section">
          <div class="audit-sec-title">{{ $t('qa.ai.caseGen.draftCoveredTitle', { count: draftCoveredCount }) }}
            <a-button size="mini" type="text" @click="$emit('go-adopt')">{{ $t('qa.ai.caseGen.goAdopt') }}</a-button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'DimensionBoard' };
</script>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useI18n } from 'vue-i18n';
import { IconCheckCircle, IconExclamationCircle, IconCloseCircle, IconLoading, IconMinus } from '@arco-design/web-vue/es/icon';

const { t } = useI18n();

const props = defineProps<{
  dimensions: any[];
  audit: any;
  generating: boolean;
  roundInfo?: any;
  reqConverged?: boolean;
  reqStaleConverged?: boolean;
  archivedAudit?: any;
  pendingDraftCount?: number;
}>();
defineEmits(['retry-dim', 'archive', 'go-adopt']);

const auditExpanded = ref(false);

const coveredCount = computed(() => props.dimensions.filter((d: any) => d.status === 'COVERED').length);
const issueCount = computed(() => props.dimensions.filter((d: any) => d.status === 'ISSUE' || d.status === 'FAILED').length);
const allCovered = computed(() =>
  props.dimensions.length > 0 && coveredCount.value === props.dimensions.length
);

const claimedOnlyList = computed(() =>
  (props.audit?.verdicts || []).filter((v: any) => v.status === 'CLAIMED_ONLY'));
const missingList = computed(() => props.audit?.missingPoints || []);
const draftCoveredCount = computed(() =>
  (props.audit?.verdicts || []).filter((v: any) => v.status === 'COVERED_DRAFT').length);

const CATEGORY_KEY_MAP: Record<string, string> = {
  FUNCTION: 'qa.ai.caseGen.category.FUNCTION', BOUNDARY: 'qa.ai.caseGen.category.BOUNDARY', EXCEPTION: 'qa.ai.caseGen.category.EXCEPTION', PERMISSION: 'qa.ai.caseGen.category.PERMISSION', SECURITY: 'qa.ai.caseGen.category.SECURITY',
};
function categoryText(c: string) {
  return CATEGORY_KEY_MAP[c] ? t(CATEGORY_KEY_MAP[c]) : c || '';
}
</script>

<style scoped>
.dim-board { padding: 12px 16px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); margin-bottom: 12px; flex-shrink: 0; max-height: 45%; overflow-y: auto; }
.banner { display: flex; align-items: center; gap: 6px; padding: 6px 10px; border-radius: 6px; font-size: 12px; margin-bottom: 8px; }
.banner-ok { background: rgb(var(--success-1)); color: rgb(var(--success-6)); border: 1px solid rgb(var(--success-3)); }
.banner-stale { background: rgb(var(--warning-1)); color: rgb(var(--warning-6)); border: 1px solid rgb(var(--warning-3)); }
.db-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.db-title { font-size: 14px; font-weight: 600; }
.db-summary { font-size: 12px; color: var(--color-text-3); }
.db-round { font-size: 12px; color: rgb(var(--primary-6)); margin-left: auto; }
.db-header .a-button { margin-left: auto; }
.db-round + .a-button { margin-left: 0; }
.warn-text { color: rgb(var(--warning-6)); }
.dim-list { display: flex; flex-direction: column; }
.dim-row { display: flex; gap: 8px; padding: 5px 0; border-bottom: 1px dashed var(--color-fill-2); }
.dim-row:last-child { border-bottom: none; }
.dim-icon { flex-shrink: 0; line-height: 20px; }
.dim-main { flex: 1; min-width: 0; }
.dim-line1 { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.dim-title { font-size: 13px; font-weight: 500; }
.dim-category { font-size: 11px; color: var(--color-text-3); background: var(--color-fill-2); padding: 0 6px; border-radius: 3px; }
.dim-case { font-size: 11px; color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); padding: 0 6px; border-radius: 3px; }
.dim-issue { font-size: 12px; color: rgb(var(--warning-6)); margin-top: 2px; }
.audit-detail { margin-top: 8px; border-top: 1px solid var(--color-fill-2); padding-top: 6px; }
.audit-toggle { font-size: 12px; color: rgb(var(--primary-6)); cursor: pointer; user-select: none; }
.audit-summary-inline { color: var(--color-text-3); margin-left: 8px; font-weight: normal; }
.audit-section { margin-top: 6px; font-size: 12px; }
.audit-sec-title { font-weight: 500; margin-bottom: 4px; }
.audit-item { padding-left: 12px; color: var(--color-text-2); margin-bottom: 2px; line-height: 1.6; }
</style>
