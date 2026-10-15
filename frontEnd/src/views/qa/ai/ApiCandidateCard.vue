<template>
  <div class="entry-card">
    <div class="ac-title">{{ $t('qa.ai.cards.apiCandidatesTitle') }}</div>
    <div class="ac-list">
      <div
        v-for="api in apis"
        :key="api.id"
        class="ac-item"
        :class="{ 'ac-item-muted': disabled }"
        @click="onSelect(api)"
      >
        <span class="ac-method" :style="{ color: getMethodColor(api.method) }">{{ api.method || 'GET' }}</span>
        <span class="ac-name">{{ api.name }}</span>
        <span class="ac-id">{{ $t('qa.ai.cards.apiCandidateId', { id: api.id }) }}</span>
        <span class="ac-path">{{ api.path }}</span>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'ApiCandidateCard' };
</script>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  data?: any;
  disabled?: boolean;
}>();

const emit = defineEmits<{
  select: [api: any];
}>();

const apis = computed(() => (Array.isArray(props.data?.apis) ? props.data.apis : []));

function getMethodColor(method?: string) {
  switch ((method || 'GET').toUpperCase()) {
    case 'GET': return '#0e8a16';
    case 'POST': return '#0969da';
    case 'PUT': return '#9a6700';
    case 'DELETE': return '#cf222e';
    case 'PATCH': return '#8957e5';
    default: return '#666';
  }
}

function onSelect(api: any) {
  if (props.disabled) return;
  emit('select', api);
}
</script>

<style scoped>
.entry-card { padding: 12px 16px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); max-width: 560px; }
.ac-title { font-size: 13px; font-weight: 500; margin-bottom: 8px; }
.ac-list { display: flex; flex-direction: column; }
.ac-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 6px; cursor: pointer; transition: background 0.15s; }
.ac-item:hover { background: rgb(var(--primary-1)); }
.ac-item-muted { opacity: 0.5; cursor: not-allowed; }
.ac-item-muted:hover { background: transparent; }
.ac-method { font-size: 12px; font-weight: 700; width: 52px; flex-shrink: 0; }
.ac-name { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex-shrink: 1; min-width: 0; }
.ac-id { font-size: 12px; color: var(--color-text-3); flex-shrink: 0; }
.ac-path { font-size: 12px; color: var(--color-text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex-shrink: 2; min-width: 0; }
</style>
