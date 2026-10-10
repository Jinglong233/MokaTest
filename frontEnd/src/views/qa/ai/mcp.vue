<template>
  <div class="ai-mcp-page">
    <Breadcrumb :items="['menu.qa', 'menu.qa.aiMcp']" />
    <a-alert type="info" :bordered="false" style="margin-bottom: 16px">
      {{ $t('qa.ai.mcp.tip') }}
    </a-alert>
    <a-card :title="$t('qa.ai.mcp.title')" :bordered="false">
      <template #extra>
        <a-button @click="loadTools"><icon-refresh /> {{ $t('qa.ai.mcp.refresh') }}</a-button>
      </template>
      <a-table :data="tools" :loading="loading" :pagination="false" size="small" row-key="name">
        <template #columns>
          <a-table-column :title="$t('qa.ai.mcp.colName')" data-index="name" :width="200" />
          <a-table-column :title="$t('qa.ai.mcp.colDesc')" data-index="description" :width="200" />
          <a-table-column :title="$t('qa.ai.mcp.colParams')">
            <template #cell="{ record }">
              <div v-for="p in record.parameters" :key="p.name" style="margin-bottom: 4px">
                <a-tag size="small" color="arcoblue">{{ p.name }}</a-tag>
                <span style="color: var(--color-text-3); margin-left: 6px; font-size: 12px">{{ p.type }}</span>
                <span style="margin-left: 6px; font-size: 12px">{{ p.description }}</span>
              </div>
            </template>
          </a-table-column>
        </template>
        <template #empty><a-empty :description="$t('qa.ai.mcp.empty')" /></template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import axios from 'axios';

const tools = ref<any[]>([]);
const loading = ref(false);

async function loadTools() {
  loading.value = true;
  try {
    const res: any = await axios.get('/api/mcp/tools');
    if (res.code === 200) tools.value = res.data || [];
  } catch {} finally { loading.value = false; }
}

onMounted(loadTools);
</script>

<style scoped>
.ai-mcp-page { padding: 12px 20px 16px; }
</style>
