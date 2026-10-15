<template>
  <div class="draft-card">
    <a-table
        :data="drafts"
        :pagination="false"
        size="small"
        :scroll="{ y: 260 }"
    >
      <template #columns>
        <a-table-column v-if="editable" :width="40" title="">
          <template #cell="{ record }">
            <a-checkbox v-model="record._checked" :disabled="record._adopted"/>
          </template>
        </a-table-column>
        <a-table-column :title="$t('components.aiGenerate.colName')" :width="200">
          <template #cell="{ record }">
            <a-input v-if="editable && !record._adopted" v-model="record.caseName" size="mini"/>
            <span v-else>{{ record.caseName }}</span>
            <a-tag v-if="record._adopted" size="small" color="green" style="margin-left: 4px">{{ $t('components.aiGenerate.adoptedTag') }}</a-tag>
          </template>
        </a-table-column>
        <template v-if="scene === 'case'">
          <a-table-column :title="$t('components.aiGenerate.colType')" :width="90">
            <template #cell="{ record }">
              <a-tag size="small" :color="record.enumFallback ? 'orange' : undefined">{{ record.caseType }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="$t('components.aiGenerate.colPriority')" :width="70">
            <template #cell="{ record }">
              <a-tag size="small" :color="record.enumFallback ? 'orange' : undefined">{{ record.priority }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="$t('components.aiGenerate.colSteps')" :width="70">
            <template #cell="{ record }">{{ record.testSteps?.length || 0 }}</template>
          </a-table-column>
        </template>
        <template v-else>
          <a-table-column :title="$t('components.aiGenerate.colDescription')" :width="200">
            <template #cell="{ record }">{{ record.description || '-' }}</template>
          </a-table-column>
          <a-table-column :title="$t('components.aiGenerate.colAssert')" :width="60">
            <template #cell="{ record }">{{ record.assertions?.length || 0 }}</template>
          </a-table-column>
          <a-table-column title="Body" :width="70">
            <template #cell="{ record }">
              <a-tag v-if="record.bodyJson" size="small" color="blue">{{ $t('components.aiGenerate.bodyOverride') }}</a-tag>
              <span v-else style="color: var(--color-text-3)">{{ $t('components.aiGenerate.bodyInherit') }}</span>
            </template>
          </a-table-column>
        </template>
      </template>
    </a-table>
    <!-- 采纳操作条：仅最新一轮且未过期可编辑时出现 -->
    <div v-if="editable" class="draft-actions">
      <a-button size="mini" :disabled="adoptableCount === 0" @click="toggleAll">
        {{ allChecked ? $t('components.aiGenerate.deselectAll') : $t('components.aiGenerate.selectAll') }}
      </a-button>
      <a-button
          v-if="adoptableCount > 0"
          type="primary"
          size="mini"
          :loading="adopting"
          :disabled="checkedCount === 0"
          @click="handleAdopt"
      >
        {{ $t('components.aiGenerate.adoptSelected', { count: checkedCount }) }}
      </a-button>
      <a-tag v-else color="green">{{ $t('components.aiGenerate.allAdopted') }}</a-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';

/**
 * AI 生成草稿表格卡片（scene='case' 与 scene='apiCase' 两套列）
 *
 * 纯展示 + 勾选/行内改名，勾选态直接落在 drafts 元素的 _checked/_adopted 上；
 * 点击入库只 emit 洗净的草稿数据，API 调用与结果回写由父组件完成。
 */
const props = defineProps<{
    scene: 'case' | 'apiCase';
    drafts: any[];
    /** 是否可编辑（最新一轮且会话未过期）：控制勾选列、名称编辑与采纳操作条 */
    editable: boolean;
    adopting?: boolean;
}>();

const emit = defineEmits<{
    (e: 'adopt', items: any[]): void;
}>();

const checkedCount = computed(() =>
    props.drafts.filter((d: any) => d._checked && !d._adopted).length);

const adoptableCount = computed(() =>
    props.drafts.filter((d: any) => !d._adopted).length);

const allChecked = computed(() =>
    adoptableCount.value > 0 && checkedCount.value === adoptableCount.value);

function toggleAll() {
    const target = !allChecked.value;
    props.drafts.forEach((d: any) => { if (!d._adopted) d._checked = target; });
}

function handleAdopt() {
    const items = props.drafts
        .filter((d: any) => d._checked && !d._adopted)
        .map(({_checked, _adopted, ...rest}: any) => rest);
    emit('adopt', items);
}
</script>

<style scoped>
.draft-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
  align-items: center;
}
</style>
