<template>
  <div class="workbench-panel">
    <DimensionBoard
      :dimensions="workbench.dimensions"
      :audit="workbench.audit"
      :generating="generating"
      :round-info="roundInfo"
      :req-converged="reqConverged"
      :req-stale-converged="reqStaleConverged"
      :archived-audit="archivedAudit"
      :pending-draft-count="workbench.drafts.length"
      @retry-dim="$emit('retry-dim', $event)"
      @archive="$emit('archive')"
      @go-adopt="scrollToDraftPool"
    />
    <DraftPool
      ref="draftPoolRef"
      :drafts="workbench.drafts"
      :adopted-drafts="workbench.adoptedDrafts"
      :streaming-drafts="workbench.streamingDrafts"
      :citations="workbench.citations"
      :dimensions="workbench.dimensions"
      :adopting="adopting"
      :adopt-result="adoptResult"
      @adopt-single="$emit('adopt-single', $event)"
      @adopt-batch="$emit('adopt-batch', $event)"
      @delete="$emit('delete', $event)"
      @edited="$emit('edited', $event)"
    />
  </div>
</template>

<script lang="ts">
export default { name: 'WorkbenchPanel' };
</script>

<script setup lang="ts">
import { ref } from 'vue';
import DimensionBoard from './DimensionBoard.vue';
import DraftPool from './DraftPool.vue';

defineProps<{
  workbench: any;
  generating: boolean;
  adopting: boolean;
  adoptResult: any;
  roundInfo?: any;
  reqConverged?: boolean;
  reqStaleConverged?: boolean;
  archivedAudit?: any;
}>();
defineEmits(['retry-dim', 'archive', 'adopt-single', 'adopt-batch', 'delete', 'edited']);

const draftPoolRef = ref();

function scrollToDraftPool() {
  if (draftPoolRef.value) {
    const el = (draftPoolRef.value as any).$el;
    if (el) el.scrollIntoView({ behavior: 'smooth' });
  }
}
</script>

<style scoped>
.workbench-panel {
  flex: 1; display: flex; flex-direction: column; padding: 12px;
  overflow: hidden; min-height: 0;
}
</style>
