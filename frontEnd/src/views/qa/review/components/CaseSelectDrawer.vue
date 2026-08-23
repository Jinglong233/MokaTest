<template>
  <a-drawer
    :visible="visible"
    :width="fullScreen ? '100%' : 1100"
    :footer="false"
    :mask-closable="true"
    @cancel="handleClose"
  >
    <template #title>
      <div class="drawer-title-wrap">
        <span>关联用例</span>
        <a-button type="text" size="small" class="fullscreen-btn" @click="fullScreen = !fullScreen">
          <template #icon>
            <icon-fullscreen v-if="!fullScreen" />
            <icon-fullscreen-exit v-else />
          </template>
        </a-button>
      </div>
    </template>
    <div class="case-select-body">
      <!-- 左侧用例模块树 -->
      <div class="case-select-side">
        <a-input-search
          v-model="moduleKeyword"
          placeholder="搜索模块"
          style="margin-bottom: 8px"
          size="small"
        />
        <a-scrollbar style="flex: 1; overflow: auto;">
          <a-tree
            :data="filteredModuleTree"
            :field-names="{ key: 'id', title: 'moduleName' }"
            :default-expand-all="true"
            :selected-keys="selectedModuleKeys"
            @select="handleModuleSelect"
          >
            <template #title="nodeData">
              <span>{{ nodeData.moduleName }}</span>
              <span v-if="nodeData.id !== 0" style="color: #86909c; margin-left: 4px;">[{{ nodeData.caseCount ?? 0 }}]</span>
            </template>
          </a-tree>
        </a-scrollbar>
      </div>

      <!-- 右侧用例列表 -->
      <div class="case-select-main">
        <div class="case-select-toolbar">
          <a-input-search
            v-model="searchKeyword"
            placeholder="搜索用例编号/名称"
            allow-clear
            style="width: 240px"
            @search="handleSearch"
            @press-enter="handleSearch"
          />
          <span class="selected-count">已选 {{ tempSelectedIds.length }} 条</span>
        </div>

        <a-table
          :data="caseList"
          :loading="loading"
          :pagination="pagination"
          :row-selection="{ type: 'checkbox', showCheckedAll: true, selectedRowKeys: tempSelectedIds }"
          row-key="id"
          size="small"
          :scroll="{ y: 420 }"
          @page-change="handlePageChange"
          @select="handleSelect"
          @select-all="handleSelectAll"
        >
          <template #columns>
            <a-table-column title="用例编号" data-index="caseCode" :width="130" />
            <a-table-column title="用例名称" data-index="caseName" :ellipsis="true" />
            <a-table-column title="用例等级" :width="90" align="center">
              <template #cell="{ record }">
                <span class="cell-with-icon">
                  <icon-star-fill :size="14" class="icon-priority" />
                  {{ record.priority || '-' }}
                </span>
              </template>
            </a-table-column>
            <a-table-column title="评审结果" :width="100" align="center">
              <template #cell="{ record }">
                <span class="cell-with-icon">
                  <icon-check-circle-fill v-if="record.finalResult === 'PASS'" :size="14" class="icon-pass" />
                  <icon-close-circle-fill v-else-if="record.finalResult === 'REJECT'" :size="14" class="icon-reject" />
                  <icon-exclamation-circle-fill v-else :size="14" class="icon-pending" />
                  <span v-if="record.finalResult === 'PASS'" class="text-pass">已通过</span>
                  <span v-else-if="record.finalResult === 'REJECT'" class="text-reject">不通过</span>
                  <span v-else class="text-pending">未评审</span>
                </span>
              </template>
            </a-table-column>
            <a-table-column title="执行结果" :width="100" align="center">
              <template #cell>
                <span class="cell-with-icon">
                  <icon-info-circle-fill :size="14" class="icon-pending" />
                  <span class="text-pending">-</span>
                </span>
              </template>
            </a-table-column>
            <a-table-column title="标签" :width="120">
              <template #cell="{ record }">
                <template v-if="record.tags">
                  <a-tag v-for="tag in String(record.tags).split(',').filter(Boolean)" :key="tag" size="small">{{ tag }}</a-tag>
                </template>
                <span v-else style="color: #86909c;">-</span>
              </template>
            </a-table-column>
            <a-table-column title="创建人" data-index="createUserName" :width="100" />
            <a-table-column title="创建时间" data-index="createTime" :width="160" />
          </template>
        </a-table>
      </div>
    </div>

    <div class="case-select-footer">
      <a-space>
        <span style="color: #86909c;">已选 {{ tempSelectedIds.length }} 条用例</span>
      </a-space>
      <a-space>
        <a-button @click="handleClose">取消</a-button>
        <a-button type="primary" @click="handleConfirm">确定</a-button>
      </a-space>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { getTestCaseList, getQaModuleTree } from '@/api/MyApi/qa';

const props = defineProps<{
  visible: boolean;
  projectId?: number;
  selectedIds?: number[];
  excludeIds?: number[];
  excludePlanId?: number;
}>();

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void;
  (e: 'confirm', cases: any[]): void;
}>();

const loading = ref(false);
const fullScreen = ref(false);
const caseList = ref<any[]>([]);
const searchKeyword = ref('');
const searchStatus = ref<string | undefined>(undefined);
const searchModuleId = ref<number | undefined>(undefined);
const pagination = ref({ current: 1, pageSize: 20, total: 0, showTotal: true, showPageSize: true });

// 模块树
const moduleTree = ref<any[]>([]);
const moduleKeyword = ref('');
const selectedModuleKeys = ref<(string | number)[]>([]);

// 临时选中（打开抽屉时从 props.selectedIds 初始化，确认后才 emit）
const tempSelectedIds = ref<number[]>([]);
// 已选用例完整信息（用于确认时返回）
const tempSelectedCases = ref<Map<number, any>>(new Map());

// 搜索过滤模块树
const filteredModuleTree = computed(() => {
  if (!moduleKeyword.value) return moduleTree.value;
  const filter = (list: any[]): any[] => {
    const result: any[] = [];
    list.forEach((item: any) => {
      if (item.moduleName?.indexOf(moduleKeyword.value) > -1) {
        result.push({ ...item });
      } else if (item.children) {
        const filtered = filter(item.children);
        if (filtered.length) result.push({ ...item, children: filtered });
      }
    });
    return result;
  };
  return filter(moduleTree.value);
});

const loadModuleTree = async () => {
  if (!props.projectId) return;
  try {
    const res: any = await getQaModuleTree(props.projectId, 'CASE');
    moduleTree.value = res.data || [];
  } catch (e) {
    console.error(e);
  }
};

const loadData = async () => {
  if (!props.projectId) return;
  loading.value = true;
  try {
    const res: any = await getTestCaseList(
      props.projectId,
      searchModuleId.value,
      undefined, undefined,
      searchKeyword.value || undefined,
      undefined,
      pagination.value.current,
      pagination.value.pageSize,
      props.excludePlanId,
      searchStatus.value || undefined
    );
    caseList.value = (res.data?.records || []).filter((c: any) => !(props.excludeIds || []).includes(c.id));
    pagination.value.total = caseList.value.length;
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

const handleModuleSelect = (keys: (string | number)[], nodeData: any) => {
  const id = Number(nodeData?.id ?? 0);
  searchModuleId.value = id === 0 ? undefined : id;
  selectedModuleKeys.value = keys;
  pagination.value.current = 1;
  loadData();
};

const handleSearch = () => {
  pagination.value.current = 1;
  loadData();
};

const handlePageChange = (page: number) => {
  pagination.value.current = page;
  loadData();
};

const handleSelect = (rowKeys: (string | number)[], rowKey: (string | number), record: any) => {
  const id = Number(rowKey);
  if (tempSelectedIds.value.includes(id)) {
    // 取消选中
    tempSelectedIds.value = tempSelectedIds.value.filter(x => x !== id);
    tempSelectedCases.value.delete(id);
  } else {
    // 选中
    tempSelectedIds.value.push(id);
    tempSelectedCases.value.set(id, record);
  }
};

// 表头全选/取消全选：仅操作当前页数据
const handleSelectAll = (checked: boolean) => {
  const pageIds = caseList.value.map((c: any) => Number(c.id));
  if (checked) {
    pageIds.forEach((id) => {
      if (!tempSelectedIds.value.includes(id)) {
        tempSelectedIds.value.push(id);
        const record = caseList.value.find((c: any) => Number(c.id) === id);
        if (record) tempSelectedCases.value.set(id, record);
      }
    });
  } else {
    tempSelectedIds.value = tempSelectedIds.value.filter(id => !pageIds.includes(id));
    pageIds.forEach((id) => tempSelectedCases.value.delete(id));
  }
};

const handleConfirm = () => {
  const cases = tempSelectedIds.value
    .map(id => tempSelectedCases.value.get(id))
    .filter(Boolean);
  emit('confirm', cases);
  emit('update:visible', false);
};

const handleClose = () => {
  emit('update:visible', false);
};

// 打开抽屉时初始化
watch(() => props.visible, (val) => {
  if (val) {
    tempSelectedIds.value = [...(props.selectedIds || [])];
    tempSelectedCases.value = new Map();
    searchKeyword.value = '';
    searchStatus.value = undefined;
    searchModuleId.value = undefined;
    selectedModuleKeys.value = [];
    pagination.value.current = 1;
    loadModuleTree();
    loadData();
  }
});

const priorityColor = (p: string) => ({ P0: 'red', P1: 'orange', P2: 'blue' } as Record<string, string>)[p] || 'gray';
const caseStatusColor = (s: string) => ({ DRAFT: 'gray', REVIEWING: 'gold', REVIEWED: 'green', DEPRECATED: 'red' } as Record<string, string>)[s] || 'gray';
const caseStatusText = (s: string) => ({ DRAFT: '草稿', REVIEWING: '评审中', REVIEWED: '已评审', DEPRECATED: '已废弃' } as Record<string, string>)[s] || s;
</script>

<style scoped lang="less">
.drawer-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}
.fullscreen-btn {
  margin-left: auto;
  flex-shrink: 0;
  color: var(--color-text-2);
}
.cell-with-icon {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.icon-priority { color: #ff7d00; }
.icon-pass { color: #00b42a; }
.icon-reject { color: #f53f3f; }
.icon-pending { color: #86909c; }
.text-pass { color: #00b42a; }
.text-reject { color: #f53f3f; }
.text-pending { color: #86909c; }

.case-select-body {
  display: flex;
  gap: 12px;
  height: calc(100% - 52px);
  min-height: 0;
}

.case-select-side {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-right: 1px solid var(--color-border-2);
  padding-right: 8px;
}

.case-select-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.case-select-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;

  .selected-count {
    margin-left: auto;
    color: var(--color-text-2);
    font-size: 13px;
  }
}

.case-select-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-top: 1px solid var(--color-border-2);
  background: var(--color-bg-2);
}
</style>
