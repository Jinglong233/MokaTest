<template>
  <!-- 关联已有BUG抽屉：搜索 + 分页 + 复选，确定后按差量绑定/解绑 planCaseId -->
  <a-drawer
    :visible="visible"
    title="关联已有BUG"
    :width="680"
    :footer="true"
    :body-style="{ minHeight: '450px' }"
    @cancel="handleCancel"
  >
    <a-input-search
      v-model="keyword"
      placeholder="搜索缺陷标题/编号"
      allow-clear
      style="width: 280px; margin-bottom: 12px"
      @search="handleSearch"
      @clear="handleSearch"
    />
    <a-table
      :selected-keys="selected"
      :data="list"
      :loading="loading"
      :pagination="pagination"
      :row-selection="{ type: 'checkbox', showCheckedAll: true, onlyCurrent: true }"
      :scroll="{ y: 400 }"
      row-key="id"
      size="small"
      @page-change="handlePageChange"
      @selection-change="(keys: any[]) => (selected = keys)"
    >
      <template #columns>
        <a-table-column title="编号" :width="110">
          <template #cell="{ record }">{{ record.bugCode }}</template>
        </a-table-column>
        <a-table-column title="标题" data-index="title" :ellipsis="true" />
        <a-table-column title="状态" :width="90">
          <template #cell="{ record }">
            <a-tag size="small" :color="bugStatusColor(record.status)">{{ bugStatusText(record.status) }}</a-tag>
          </template>
        </a-table-column>
        <a-table-column title="关联状态" :width="120">
          <template #cell="{ record }">
            <a-tag v-if="record.planCaseId && record.planCaseId !== planCaseId" size="small" color="orange">已关联其他计划用例</a-tag>
            <a-tag v-else-if="record.planCaseId" size="small" color="green">已关联本用例</a-tag>
            <span v-else style="color: #86909c">未关联</span>
          </template>
        </a-table-column>
      </template>
      <template #empty>
        <a-empty description="没有匹配的缺陷" />
      </template>
    </a-table>
    <div class="bug-bind-tip">勾选「已关联其他计划用例」的缺陷，保存后原关联会被替换为本用例</div>
    <template #footer>
      <a-space>
        <a-button @click="handleCancel">取消</a-button>
        <a-button type="primary" :loading="saving" @click="handleOk">确定</a-button>
      </a-space>
    </template>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import { getBugList, bindBugPlanCase } from '@/api/MyApi/qa';
import { useProjectStore } from '@/store';

const props = defineProps<{
  visible: boolean;
  /** 目标计划用例ID（绑定到 bug.plan_case_id） */
  planCaseId: number | null;
}>();

const emit = defineEmits<{
  'update:visible': [v: boolean];
  saved: [];
}>();

const projectStore = useProjectStore();

const saving = ref(false);
const loading = ref(false);
const keyword = ref('');
const list = ref<any[]>([]);
const selected = ref<number[]>([]);
const pagination = ref({ current: 1, pageSize: 10, total: 0, showTotal: true });

const bugStatusText = (s: string) => ({
  NEW: '新建', CONFIRMED: '已确认', FIXING: '修复中', FIXED: '已修复', VERIFIED: '已验证', CLOSED: '已关闭', REJECTED: '已驳回',
} as Record<string, string>)[s] || s || '-';
const bugStatusColor = (s: string) => ({
  NEW: 'red', CONFIRMED: 'orange', FIXING: 'gold', FIXED: 'green', VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray',
} as Record<string, string>)[s] || 'gray';

// 打开时重置并加载（预选当前页中 planCaseId 匹配的缺陷）
watch(() => props.visible, (v) => {
  if (!v) return;
  keyword.value = '';
  pagination.value.current = 1;
  loadList();
});

const loadList = async () => {
  if (!projectStore.getProjectId) return;
  loading.value = true;
  try {
    const res: any = await getBugList(
      projectStore.getProjectId,
      keyword.value || undefined,
      undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined,
      pagination.value.current,
      pagination.value.pageSize
    );
    list.value = res.data?.records || [];
    pagination.value.total = res.data?.total || 0;
    // 预选：本页中已关联本计划用例的缺陷
    selected.value = list.value.filter((b: any) => b.planCaseId === props.planCaseId).map((b: any) => b.id);
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  pagination.value.current = 1;
  loadList();
};

const handlePageChange = (current: number) => {
  pagination.value.current = current;
  loadList();
};

// 确定关联：与当前已关联集合做差量，新增勾选绑定、取消勾选解绑
const handleOk = async () => {
  if (!props.planCaseId) return;
  const beforeIds = list.value.filter((b: any) => b.planCaseId === props.planCaseId).map((b: any) => b.id);
  const toBind = selected.value.filter((id) => !beforeIds.includes(id));
  const toUnbind = beforeIds.filter((id) => !selected.value.includes(id));
  if (!toBind.length && !toUnbind.length) {
    handleCancel();
    return;
  }
  saving.value = true;
  try {
    await Promise.all([
      ...toBind.map((id) => bindBugPlanCase(id, props.planCaseId)),
      ...toUnbind.map((id) => bindBugPlanCase(id, null)),
    ]);
    Message.success('关联缺陷已更新');
    handleCancel();
    emit('saved');
  } catch (e) {
    console.error(e);
    Message.error('关联失败，请重试');
  } finally {
    saving.value = false;
  }
};

const handleCancel = () => {
  emit('update:visible', false);
};
</script>

<style scoped>
.bug-bind-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #86909c;
}
</style>
