<template>
  <div class="tool-status-node" :class="{ 'is-error': isError }">
    <span class="tool-icon">{{ isError ? '✕' : '✓' }}</span>
    <span class="tool-label">{{ label }}</span>
    <span v-if="duration" class="tool-duration">{{ duration }}ms</span>
  </div>
</template>

<script lang="ts">
export default { name: 'ToolStatusNode', inheritAttrs: false };
</script>

<script setup lang="ts">
import { useAttrs } from 'vue';
import { useI18n } from 'vue-i18n';

const props = defineProps<{ node?: any }>();
const $attrs = useAttrs();
const { t } = useI18n();

// markstream-vue 可能把标签属性放在 node.attrs 或通过 fallthrough attrs 传递
const name = (props.node?.attrs?.name || $attrs.name || '') as string;
const duration = (props.node?.attrs?.duration || $attrs.duration || '') as string;
const errorVal = props.node?.attrs?.error ?? $attrs.error;
const isError = errorVal === 'true' || errorVal === true;

const labelKeyMap: Record<string, string> = {
  search_requirements: 'qa.ai.tool.searchRequirements',
  search_cases: 'qa.ai.tool.searchCases',
  search_bugs: 'qa.ai.tool.searchBugs',
  search_knowledge: 'qa.ai.tool.searchKnowledge',
  get_requirement_trace: 'qa.ai.tool.getRequirementTrace',
  create_test_case: 'qa.ai.tool.createTestCase',
  start_case_generation: 'qa.ai.tool.startCaseGeneration',
};

const toolLabel = labelKeyMap[name] ? t(labelKeyMap[name]) : name;
const label = isError
  ? t('qa.ai.tool.failed', { name: toolLabel })
  : t('qa.ai.tool.done', { name: toolLabel });
</script>

<style scoped>
.tool-status-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: var(--color-text-2);
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
}
.tool-status-node.is-error {
  border-color: rgb(var(--danger-3));
  color: rgb(var(--danger-6));
}
.tool-icon { font-weight: bold; flex-shrink: 0; }
.tool-label { font-weight: 500; }
.tool-duration { color: var(--color-text-3); flex-shrink: 0; }
</style>
