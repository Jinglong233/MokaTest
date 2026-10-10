<template>
  <div class="draft-pool">
    <div class="dp-header">
      <span class="dp-title">{{ $t('qa.ai.caseGen.draftPool') }}</span>
      <span class="dp-summary">{{ $t('qa.ai.caseGen.poolSummary', { pending: drafts.length, adopted: adoptedDrafts.length }) }}</span>
    </div>
    <!-- 知识库引用 -->
    <div v-if="citations?.length" class="cite-banner">
      <div class="cite-title" @click="showCites = !showCites">
        <IconBook />
        <span>{{ $t('qa.ai.chat.basis') }} {{ citeTitles }}</span>
        <IconDown v-if="!showCites" />
        <IconUp v-else />
      </div>
      <ul v-if="showCites" class="cite-list">
        <li v-for="(c, i) in citations" :key="i">
          <span class="cite-doc">{{ $t('qa.ai.chat.docTitle', { title: c.title }) }}</span>{{ c.snippet }}
        </li>
      </ul>
    </div>
    <!-- 流式卡片区（生成中） -->
    <div v-if="streamingDrafts.length" class="streaming-area">
      <CaseDraftCard v-for="(d, i) in streamingDrafts" :key="'stream'+i" :draft="d" mode="streaming" />
    </div>
    <!-- 筛选栏 -->
    <div v-if="drafts.length" class="draft-filter-bar">
      <div class="filter-left">
        <span v-for="f in filterOpts" :key="f.value" class="filter-chip" :class="{ active: filterPri === f.value }" @click="filterPri = f.value">
          {{ f.label }} <span class="filter-count">{{ countBy(f.value) }}</span>
        </span>
      </div>
      <a-input v-model="searchKw" :placeholder="$t('qa.ai.caseGen.searchCaseName')" style="width: 180px" allow-clear size="small" />
    </div>
    <!-- 草稿列表（按维度分组） -->
    <div v-if="filteredList.length" class="case-card-list">
      <template v-for="g in groupedList" :key="g.title">
        <div class="dim-group-title">{{ g.title ? $t('qa.ai.caseGen.groupTitle', { title: g.title, count: g.list.length }) : $t('qa.ai.caseGen.groupCountOnly', { count: g.list.length }) }}</div>
        <div v-for="(d, i) in g.list" :key="d.draftId || i" class="draft-card-wrapper">
          <span v-if="d._round" class="round-badge">R{{ d._round }}</span>
          <CaseDraftCard
            :draft="d"
            mode="adopt"
            @adopt-single="$emit('adopt-single', d)"
            @delete="$emit('delete', d)"
            @edited="$emit('edited', d)"
          />
        </div>
      </template>
    </div>
    <div v-else-if="drafts.length" class="dp-empty">{{ $t('qa.ai.caseGen.noMatch') }}</div>
    <div v-else-if="!streamingDrafts.length && !adoptedDrafts.length" class="dp-empty-hint">{{ $t('qa.ai.caseGen.draftsHere') }}</div>
    <!-- 已采纳折叠区 -->
    <div v-if="adoptedDrafts.length" class="adopted-section">
      <div class="adopted-header" @click="adoptedCollapsed = !adoptedCollapsed">
        <IconCheckCircle style="color: rgb(var(--success-6)); font-size: 14px" />
        <span class="adopted-title">{{ $t('qa.ai.common.adopted') }}</span>
        <span class="adopted-count">{{ $t('qa.ai.common.items', { count: adoptedDrafts.length }) }}</span>
        <span class="adopted-toggle">{{ adoptedCollapsed ? $t('qa.ai.common.expand') : $t('qa.ai.common.collapse') }}</span>
      </div>
      <div v-show="!adoptedCollapsed" class="adopted-list">
        <div v-for="(d, i) in adoptedDrafts" :key="d.draftId || i" class="adopted-item">
          <IconCheckCircle style="color: rgb(var(--success-6)); font-size: 12px; flex-shrink: 0" />
          <span class="adopted-name">{{ d.caseName }}</span>
          <span v-if="d._caseCode" class="adopted-code">{{ d._caseCode }}</span>
          <span v-if="d._round" class="round-badge-sm">R{{ d._round }}</span>
        </div>
      </div>
    </div>
    <!-- 工具栏 -->
    <div v-if="drafts.length" class="dp-toolbar">
      <a-checkbox v-model="selectAll" @change="onSelectAll">{{ $t('qa.ai.common.selectAll') }}</a-checkbox>
      <a-button type="primary" size="small" :loading="adopting" :disabled="!selectedCount" @click="$emit('adopt-batch', false)">{{ $t('qa.ai.caseGen.adoptSelected') }}</a-button>
      <a-button size="small" :loading="adopting" @click="$emit('adopt-batch', true)">{{ $t('qa.ai.caseGen.adoptAll') }}</a-button>
    </div>
    <a-alert v-if="adoptResult" :type="adoptResult.failed ? 'warning' : 'success'" style="margin-top: 8px">
      {{ $t('qa.ai.caseGen.adoptResultSummary', { success: adoptResult.success, skipped: adoptResult.skipped, failed: adoptResult.failed }) }}
    </a-alert>
  </div>
</template>

<script lang="ts">
export default { name: 'DraftPool' };
</script>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { IconBook, IconDown, IconUp, IconCheckCircle } from '@arco-design/web-vue/es/icon';
import CaseDraftCard from '../cards/CaseDraftCard.vue';

const { t } = useI18n();

const props = withDefaults(defineProps<{
  drafts: any[];
  adoptedDrafts?: any[];
  streamingDrafts: any[];
  citations?: any[];
  /** 维度清单（草稿按维度分组展示用） */
  dimensions?: any[];
  adopting?: boolean;
  adoptResult?: any;
}>(), {
  adoptedDrafts: () => [] as any[],
  dimensions: () => [] as any[],
});
defineEmits(['adopt-single', 'adopt-batch', 'delete', 'edited']);

const filterPri = ref('all');
const searchKw = ref('');
const selectAll = ref(false);
const showCites = ref(false);
const adoptedCollapsed = ref(true);

const filterOpts = computed(() => [
  { label: t('qa.common.all'), value: 'all' }, { label: 'P0', value: 'P0' },
  { label: 'P1', value: 'P1' }, { label: 'P2', value: 'P2' }, { label: t('qa.ai.cards.pendingAdopt'), value: 'pending' },
]);

const isPending = (d: any) => (d._adoptStatus || 'pending') === 'pending';
const selectedCount = computed(() => props.drafts.filter((d: any) => d._selected && isPending(d)).length);
const citeTitles = computed(() => {
  const titles = [...new Set<string>((props.citations || []).map((c: any) => c.title))];
  return titles.map((title) => t('qa.ai.chat.docTitle', { title })).join('');
});

function countBy(val: string) {
  if (val === 'all') return props.drafts.length;
  if (val === 'pending') return props.drafts.filter(isPending).length;
  return props.drafts.filter((d: any) => d.priority === val).length;
}

const filteredList = computed(() => {
  let list = props.drafts;
  if (filterPri.value === 'pending') list = list.filter(isPending);
  else if (filterPri.value !== 'all') list = list.filter((d: any) => d.priority === filterPri.value);
  const kw = searchKw.value.trim();
  if (kw) list = list.filter((d: any) => (d.caseName || '').includes(kw));
  return list;
});

/** 按维度分组：草稿声明的第一个 coverPoint 决定分组；未声明归入「其他」 */
const groupedList = computed(() => {
  const list = filteredList.value;
  const dims = props.dimensions || [];
  if (!dims.length) return [{ title: '', list }];
  const byTp = new Map<string, any[]>();
  const others: any[] = [];
  for (const d of list) {
    const tpId = (d.coverPoints || [])[0];
    if (tpId && dims.some((dim: any) => dim.id === tpId)) {
      if (!byTp.has(tpId)) byTp.set(tpId, []);
      byTp.get(tpId)!.push(d);
    } else {
      others.push(d);
    }
  }
  const groups: { title: string; list: any[] }[] = [];
  for (const dim of dims) {
    const l = byTp.get(dim.id);
    if (l?.length) groups.push({ title: dim.title, list: l });
  }
  if (others.length) groups.push({ title: t('qa.ai.caseGen.otherGroup'), list: others });
  return groups;
});

function onSelectAll(v: any) {
  props.drafts.forEach((d: any) => { if (isPending(d)) d._selected = v; });
}
</script>

<style scoped>
.draft-pool { flex: 12 1 0; display: flex; flex-direction: column; min-height: 0; }
.dp-header { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; flex-shrink: 0; }
.dp-title { font-size: 14px; font-weight: 600; }
.dp-summary { font-size: 12px; color: var(--color-text-3); }
.cite-banner { margin-bottom: 8px; padding: 8px 12px; border: 1px solid rgb(var(--primary-3)); background: rgb(var(--primary-1)); border-radius: 6px; font-size: 13px; flex-shrink: 0; }
.cite-title { display: flex; align-items: center; gap: 6px; cursor: pointer; color: rgb(var(--primary-6)); font-weight: 500; }
.cite-list { margin: 8px 0 0 0; padding-left: 18px; color: var(--color-text-2); line-height: 1.7; }
.cite-doc { color: rgb(var(--primary-6)); font-weight: 500; }
.streaming-area { margin-bottom: 8px; flex-shrink: 0; }
.draft-filter-bar { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 8px 0; border-bottom: 1px solid var(--color-fill-2); margin-bottom: 8px; flex-shrink: 0; }
.filter-left { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.filter-chip { padding: 2px 10px; border: 1px solid var(--color-border-2); border-radius: 12px; font-size: 12px; color: var(--color-text-2); cursor: pointer; transition: .2s; background: var(--color-bg-1); user-select: none; }
.filter-chip:hover { border-color: rgb(var(--primary-5)); }
.filter-chip.active { border-color: rgb(var(--primary-6)); color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); }
.filter-count { font-size: 10px; color: var(--color-text-4); margin-left: 2px; }
.filter-chip.active .filter-count { color: rgb(var(--primary-6)); }
.case-card-list { flex: 1; display: flex; flex-direction: column; gap: 8px; overflow-y: auto; min-height: 0; margin-bottom: 8px; }
.dim-group-title { font-size: 12px; font-weight: 600; color: var(--color-text-3); margin-top: 4px; }
.dim-group-title:empty { display: none; }
.draft-card-wrapper { position: relative; }
.round-badge { position: absolute; top: -2px; right: -2px; z-index: 1; font-size: 10px; font-weight: 600; color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); border: 1px solid rgb(var(--primary-3)); border-radius: 8px; padding: 0 6px; line-height: 16px; }
.round-badge-sm { font-size: 10px; color: var(--color-text-4); background: var(--color-fill-2); border-radius: 3px; padding: 0 4px; line-height: 14px; flex-shrink: 0; }
.dp-empty { text-align: center; color: var(--color-text-4); padding: 40px 0; font-size: 13px; }
.dp-empty-hint { text-align: center; color: var(--color-text-4); padding: 60px 0; font-size: 13px; line-height: 2; }
.adopted-section { border-top: 1px solid var(--color-fill-2); margin-top: 8px; padding-top: 8px; flex-shrink: 0; }
.adopted-header { display: flex; align-items: center; gap: 6px; cursor: pointer; font-size: 12px; color: var(--color-text-3); user-select: none; }
.adopted-header:hover { color: var(--color-text-1); }
.adopted-title { font-weight: 500; }
.adopted-count { color: var(--color-text-4); }
.adopted-toggle { margin-left: auto; font-size: 11px; color: var(--color-text-4); }
.adopted-list { display: flex; flex-direction: column; gap: 4px; margin-top: 6px; }
.adopted-item { display: flex; align-items: center; gap: 4px; padding: 3px 0; font-size: 12px; }
.adopted-name { color: var(--color-text-2); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adopted-code { font-size: 11px; color: rgb(var(--success-6)); flex-shrink: 0; }
.dp-toolbar { display: flex; gap: 8px; align-items: center; padding: 8px 0; border-top: 1px solid var(--color-fill-2); margin-top: 4px; flex-shrink: 0; }
</style>
