<template>
  <div class="point-drafts">
    <div class="pd-header">
      <span class="pd-title">{{ tpTitle || $t('qa.ai.caseGen.selectPoint') }}</span>
      <span class="pd-summary">{{ $t('qa.ai.caseGen.pendingCount', { count: drafts.length }) }}</span>
    </div>
    <div v-if="streamingDrafts.length" class="pd-streaming">
      <CaseDraftCard v-for="(d, i) in streamingDrafts" :key="'s'+i" :draft="d" mode="streaming" />
    </div>
    <a-scrollbar outer-style="flex: 1; min-height: 0; overflow: hidden" style="height: 100%; overflow: auto;">
      <div v-if="drafts.length" class="pd-list">
        <div v-for="(d, i) in drafts" :key="d.draftId || i" class="pd-item">
          <span v-if="d._round" class="round-badge">R{{ d._round }}</span>
          <CaseDraftCard
            :draft="d"
            mode="adopt"
            @adopt-single="$emit('adopt-single', d)"
            @delete="$emit('delete', d)"
            @edited="$emit('edited', d)"
          />
        </div>
      </div>
      <div v-else-if="!streamingDrafts.length && !adoptedDrafts.length" class="pd-empty">
        {{ tpTitle ? $t('qa.ai.caseGen.draftsHere') : $t('qa.ai.caseGen.selectPointLeft') }}
      </div>
    </a-scrollbar>
    <div v-if="adoptedDrafts.length" class="pd-adopted">
      <div class="pd-adopted-header" @click="adoptedCollapsed = !adoptedCollapsed">
        <IconCheckCircle style="color: rgb(var(--success-6)); font-size: 12px" />
        <span class="pd-adopted-title">{{ $t('qa.ai.common.adopted') }}</span>
        <span class="pd-adopted-count">{{ $t('qa.ai.common.items', { count: adoptedDrafts.length }) }}</span>
        <span class="pd-adopted-toggle">{{ adoptedCollapsed ? $t('qa.ai.common.expand') : $t('qa.ai.common.collapse') }}</span>
      </div>
      <div v-show="!adoptedCollapsed" class="pd-adopted-list">
        <div v-for="(d, i) in adoptedDrafts" :key="'a'+i" class="pd-adopted-item">
          <IconCheckCircle style="color: rgb(var(--success-6)); font-size: 10px; flex-shrink: 0" />
          <span class="pd-adopted-name">{{ d.caseName }}</span>
          <span v-if="d._caseCode" class="pd-adopted-code">{{ d._caseCode }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'PointDrafts' };
</script>

<script setup lang="ts">
import { ref } from 'vue';
import { IconCheckCircle } from '@arco-design/web-vue/es/icon';
import CaseDraftCard from '../cards/CaseDraftCard.vue';

const props = withDefaults(defineProps<{
  drafts: any[];
  adoptedDrafts?: any[];
  streamingDrafts: any[];
  tpTitle?: string;
  adopting?: boolean;
}>(), {
  adoptedDrafts: () => [] as any[],
});
defineEmits(['adopt-single', 'delete', 'edited']);

const adoptedCollapsed = ref(true);
</script>

<style scoped>
.point-drafts { display: flex; flex-direction: column; height: 100%; min-height: 0; }
.pd-header { display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-bottom: 1px solid var(--color-border-2); flex-shrink: 0; }
.pd-title { font-size: 13px; font-weight: 600; }
.pd-summary { font-size: 11px; color: var(--color-text-3); }
.pd-streaming { padding: 4px 12px; flex-shrink: 0; max-height: 30%; overflow-y: auto; }
.pd-list { padding: 4px 12px; display: flex; flex-direction: column; gap: 8px; }
.pd-item { position: relative; }
.round-badge { position: absolute; top: -2px; right: -2px; z-index: 1; font-size: 10px; font-weight: 600; color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); border: 1px solid rgb(var(--primary-3)); border-radius: 8px; padding: 0 6px; line-height: 16px; }
.pd-empty { text-align: center; color: var(--color-text-4); padding: 40px 0; font-size: 13px; }
.pd-adopted { border-top: 1px solid var(--color-fill-2); padding: 6px 12px; flex-shrink: 0; }
.pd-adopted-header { display: flex; align-items: center; gap: 4px; cursor: pointer; font-size: 11px; color: var(--color-text-3); }
.pd-adopted-title { font-weight: 500; }
.pd-adopted-count { color: var(--color-text-4); }
.pd-adopted-toggle { margin-left: auto; font-size: 10px; }
.pd-adopted-list { display: flex; flex-direction: column; gap: 2px; margin-top: 4px; }
.pd-adopted-item { display: flex; align-items: center; gap: 4px; padding: 2px 0; font-size: 11px; }
.pd-adopted-name { color: var(--color-text-2); flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pd-adopted-code { font-size: 10px; color: rgb(var(--success-6)); flex-shrink: 0; }
</style>
