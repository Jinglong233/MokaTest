<template>
  <div class="tp-sidebar">
    <div class="tp-header">
      <span class="tp-title">{{ $t('qa.ai.caseGen.testPoints') }}</span>
      <span v-if="dimensions.length" class="tp-count">{{ coveredCount }}/{{ dimensions.length }}</span>
    </div>
    <a-scrollbar outer-style="flex: 1; min-height: 0; overflow: hidden" style="height: 100%; overflow: auto;">
      <div class="tp-list">
        <div
          v-for="dim in dimensions"
          :key="dim.id"
          class="tp-item"
          :class="{ active: dim.id === activeTpId }"
          @click="$emit('select', dim)"
        >
          <span class="tp-icon">
            <IconCheckCircle v-if="dim.status === 'COVERED'" style="color: rgb(var(--success-6))" />
            <IconLoading v-else-if="dim.status === 'GENERATING'" style="color: rgb(var(--primary-6))" />
            <IconExclamationCircle v-else-if="dim.status === 'ISSUE'" style="color: rgb(var(--warning-6))" />
            <IconCloseCircle v-else-if="dim.status === 'FAILED'" style="color: rgb(var(--danger-6))" />
            <IconMinus v-else style="color: var(--color-text-4)" />
          </span>
          <div class="tp-info">
            <div class="tp-name">{{ dim.title }}</div>
            <div v-if="dim.category" class="tp-cat">{{ categoryText(dim.category) }}</div>
          </div>
        </div>
      </div>
    </a-scrollbar>
  </div>
</template>

<script lang="ts">
export default { name: 'TestPointSidebar' };
</script>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { IconCheckCircle, IconExclamationCircle, IconCloseCircle, IconLoading, IconMinus, IconPlus } from '@arco-design/web-vue/es/icon';

const { t } = useI18n();

const props = defineProps<{
  dimensions: any[];
  activeTpId?: string;
}>();
defineEmits(['select']);

const coveredCount = computed(() => props.dimensions.filter((d: any) => d.status === 'COVERED').length);

const CATEGORY_KEY_MAP: Record<string, string> = {
  FUNCTION: 'qa.ai.caseGen.category.FUNCTION', BOUNDARY: 'qa.ai.caseGen.category.BOUNDARY', EXCEPTION: 'qa.ai.caseGen.category.EXCEPTION', PERMISSION: 'qa.ai.caseGen.category.PERMISSION', SECURITY: 'qa.ai.caseGen.category.SECURITY',
};
function categoryText(c: string) { return CATEGORY_KEY_MAP[c] ? t(CATEGORY_KEY_MAP[c]) : c || ''; }
</script>

<style scoped>
.tp-sidebar { width: 240px; flex-shrink: 0; display: flex; flex-direction: column; border-right: 1px solid var(--color-border-2); background: var(--color-bg-1); height: 100%; }
.tp-header { display: flex; align-items: center; justify-content: space-between; padding: 10px 12px; border-bottom: 1px solid var(--color-border-2); flex-shrink: 0; }
.tp-title { font-size: 13px; font-weight: 600; }
.tp-count { font-size: 12px; color: var(--color-text-3); }
.tp-list { padding: 4px 8px; }
.tp-item { display: flex; align-items: center; gap: 6px; padding: 6px 8px; border-radius: 6px; cursor: pointer; margin-bottom: 2px; }
.tp-item:hover { background: var(--color-fill-2); }
.tp-item.active { background: rgb(var(--primary-1)); border: 1px solid rgb(var(--primary-3)); }
.tp-icon { flex-shrink: 0; }
.tp-info { flex: 1; min-width: 0; }
.tp-name { font-size: 12px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tp-cat { font-size: 10px; color: var(--color-text-4); }
.tp-add { padding: 8px 12px; border-top: 1px solid var(--color-border-2); flex-shrink: 0; }
</style>
