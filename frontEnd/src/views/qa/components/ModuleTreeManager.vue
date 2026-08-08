<template>
  <div class="module-tree-manager">
    <div v-if="showSearch" class="module-list-search-wrap">
      <a-input-search
        v-model="searchKey"
        placeholder="输入模块名称筛选"
        class="module-list-search"
      />
    </div>
    <a-button-group v-if="showActions" class="module-list-actions">
      <a-popover
        :title="isExpandAll ? '展开全部' : '折叠全部'"
        style="flex: 1"
      >
        <a-button style="width: 100%" @click="handleTreeExpand">
          <template #icon>
            <icon-menu-fold v-if="isExpandAll" />
            <icon-menu-unfold v-if="!isExpandAll" />
          </template>
        </a-button>
      </a-popover>
    </a-button-group>
    <div class="module-list-scrollbar">
      <a-scrollbar
        :disable-horizontal="true"
        :outer-style="{ height: '100%' }"
        style="height: 100%; overflow-x: hidden; overflow-y: auto"
      >
        <a-skeleton v-if="loading" :animation="true" class="tree-skeleton">
          <a-skeleton-line :rows="10" :widths="['100%']" />
        </a-skeleton>
        <a-tree
          v-show="!loading"
          ref="moduleTreeRef"
          :data="displayTreeData"
          :field-names="{ key: 'id', title: 'moduleName' }"
          :draggable="!searchKey"
          block-node
          show-line
          :selected-keys="selectedKeys"
          :default-expand-all="defaultExpandAll"
          @select="handleSelect"
          @drop="handleTreeDrop"
          @drag-start="isDragging = true"
          @drag-end="isDragging = false"
        >
          <template #drag-icon> </template>
          <template #switcher-icon>
            <icon-caret-down
              class="switcher-icon-expanded"
              style="font-size: 12px; color: rgb(var(--color-text-3))"
            />
            <icon-caret-right
              class="switcher-icon-collapsed"
              style="font-size: 12px; color: rgb(var(--color-text-3))"
            />
          </template>
          <template #title="nodeData">
            <a-tooltip
              :content="nodeData.moduleName"
              position="top"
              :disabled="isDragging"
            >
              <div class="tree-node-title">
                <icon-apps
                  v-if="nodeData.id === 0"
                  style="color: rgb(var(--primary-6)); flex-shrink: 0"
                />
                <icon-folder
                  v-else-if="nodeData.parentId === 0"
                  style="color: rgb(var(--arcoblue-6)); flex-shrink: 0"
                />
                <icon-storage
                  v-else
                  style="color: rgb(var(--green-6)); flex-shrink: 0"
                />
                <span class="tree-node-name">{{ nodeData.moduleName }}</span>
                <span class="tree-node-count"
                  >[{{ nodeData.totalCaseCount ?? nodeData.caseCount ?? 0 }}]</span
                >
              </div>
            </a-tooltip>
          </template>
          <template #extra="nodeData">
            <span class="node-extra-actions">
              <a-dropdown position="bottom">
                <a-button type="text" size="mini" class="node-extra-btn">
                  <template #icon>
                    <icon-more class="node-extra-icon" />
                  </template>
                </a-button>
                <template #content>
                  <a-doption
                    v-permission="'qa:module:create'"
                    @click.stop="handleAddSub(nodeData)"
                    >{{ nodeData.id === 0 ? '新增根模块' : '新增子模块' }}</a-doption
                  >
                  <a-doption
                    v-if="nodeData.id !== 0"
                    v-permission="'qa:module:update'"
                    @click.stop="handleEdit(nodeData)"
                    >编辑</a-doption
                  >
                  <a-doption
                    v-if="nodeData.id !== 0"
                    v-permission="'qa:module:delete'"
                    @click.stop="handleDeleteConfirm(nodeData.id)"
                    >删除</a-doption
                  >
                </template>
              </a-dropdown>
            </span>
          </template>
        </a-tree>
      </a-scrollbar>
    </div>

    <!-- 新增/编辑弹窗 -->
    <a-modal
      v-model:visible="modalVisible"
      :title="modalTitle"
      width="480px"
      :mask-closable="false"
      @ok="handleSave"
      @cancel="modalVisible = false"
    >
      <a-form :model="formData" layout="vertical">
        <a-form-item label="模块名称" required>
          <a-input v-model="formData.moduleName" placeholder="请输入模块名称" />
        </a-form-item>
        <a-form-item label="父模块">
          <a-tree-select
            v-model="formData.parentId"
            :data="parentModuleTreeData"
            :field-names="{ key: 'id', title: 'moduleName' }"
            placeholder="不选则为根模块"
            allow-clear
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, computed, nextTick } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import {
  IconFolder,
  IconStorage,
  IconApps,
  IconMenuFold,
  IconMenuUnfold,
  IconMore,
  IconCaretRight,
  IconCaretDown,
} from '@arco-design/web-vue/es/icon';
import { useProjectStore } from '@/store';
import {
  getQaModuleList,
  saveQaModule,
  deleteQaModule,
  sortQaModule,
} from '@/api/MyApi/qa';

interface Props {
  showSearch?: boolean;
  showActions?: boolean;
  showVirtualRoot?: boolean;
  selectedKeys?: (string | number)[];
  defaultExpandAll?: boolean;
}

const props = withDefaults(defineProps<Props>(), {
  showSearch: true,
  showActions: true,
  showVirtualRoot: false,
  selectedKeys: () => [],
  defaultExpandAll: false,
});

const emit = defineEmits<{
  (e: 'select', selectedKeys: any[], nodeData?: any): void;
  (e: 'changed', type: 'add' | 'update' | 'delete' | 'sort', payload?: any): void;
}>();

const projectStore = useProjectStore();

const loading = ref(false);
const moduleList = ref<any[]>([]);

const modalVisible = ref(false);
const modalTitle = ref('');
const formData = ref<any>({});
const isEdit = ref(false);
const currentEditId = ref<number | null>(null);

const searchKey = ref('');
const moduleTreeRef = ref();
const isExpandAll = ref(false);
const isDragging = ref(false);

// 加载模块列表
const loadData = async () => {
  if (!projectStore.getProjectId) return;
  loading.value = true;
  try {
    const res: any = await getQaModuleList(projectStore.getProjectId);
    moduleList.value = res.data || [];
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

// 构建树形数据（同时聚合子模块用例数，便于展示 [xx]）
const buildTree = (list: any[], parentId = 0): any[] => {
  return list
    .filter((item) => item.parentId === parentId)
    .sort((a, b) => (a.sort || 0) - (b.sort || 0))
    .map((item) => {
      const children = buildTree(list, item.id);
      const childrenCount = children.reduce(
        (sum, child) => sum + (child.totalCaseCount || 0),
        0
      );
      return {
        ...item,
        children,
        totalCaseCount: (item.caseCount || 0) + childrenCount,
      };
    });
};

const moduleTreeData = computed(() => {
  return buildTree(moduleList.value);
});

// 虚拟根节点包装
const treeWithVirtualRoot = computed(() => {
  if (!props.showVirtualRoot) return moduleTreeData.value;
  const totalCount = moduleTreeData.value.reduce(
    (sum, node) => sum + (node.totalCaseCount || 0),
    0
  );
  return [
    {
      id: 0,
      moduleName: '全部用例',
      parentId: -1,
      totalCaseCount: totalCount,
      children: moduleTreeData.value,
    },
  ];
});

// 搜索过滤
const searchData = (keyword: string, data: any[]) => {
  const loop = (list: any[]): any[] => {
    const result: any[] = [];
    list.forEach((item: any) => {
      if (item.moduleName.indexOf(keyword) > -1) {
        result.push({ ...item });
      } else if (item.children) {
        const filterData = loop(item.children);
        if (filterData.length) {
          result.push({
            ...item,
            children: filterData,
          });
        }
      }
    });
    return result;
  };
  return loop(data);
};

const displayTreeData = computed(() => {
  const source = treeWithVirtualRoot.value;
  if (!searchKey.value) return source;
  return searchData(searchKey.value, source);
});

// 父模块选择树：编辑时排除当前模块及其子模块，避免循环引用
const parentModuleTreeData = computed(() => {
  const filterTree = (list: any[]): any[] => {
    return list
      .filter((item) => item.id !== currentEditId.value)
      .map((item) => ({
        ...item,
        children: filterTree(item.children || []),
      }));
  };
  return [
    {
      id: 0,
      moduleName: '根模块',
      children: filterTree(moduleTreeData.value),
    },
  ];
});

// 展开/折叠全部
const handleTreeExpand = () => {
  const newExpandState = !isExpandAll.value;
  isExpandAll.value = newExpandState;
  nextTick(() => {
    if (moduleTreeRef.value) {
      moduleTreeRef.value.expandAll(newExpandState);
    }
  });
};

const handleSelect = (selectedKeys: any[], nodeData: any) => {
  emit('select', selectedKeys, nodeData);
};

const handleAddSub = (record: any) => {
  isEdit.value = false;
  currentEditId.value = null;
  modalTitle.value =
    record.id === 0 ? '新增根模块' : `新增「${record.moduleName}」的子模块`;
  formData.value = {
    projectId: projectStore.getProjectId,
    parentId: record.id,
  };
  modalVisible.value = true;
};

const handleEdit = (record: any) => {
  isEdit.value = true;
  currentEditId.value = record.id;
  modalTitle.value = '编辑模块';
  formData.value = { ...record };
  modalVisible.value = true;
};

const handleSave = async () => {
  if (!formData.value.moduleName || formData.value.moduleName.trim() === '') {
    Message.warning('请输入模块名称');
    return;
  }
  try {
    const res: any = await saveQaModule(formData.value);
    if (res.code === 200) {
      Message.success('保存成功');
      modalVisible.value = false;
      await loadData();
      emit('changed', isEdit.value ? 'update' : 'add', { ...formData.value });
    }
  } catch (e) {
    console.error(e);
  }
};

const handleDelete = async (id: number) => {
  try {
    const res: any = await deleteQaModule(id);
    if (res.code === 200) {
      Message.success('删除成功');
      await loadData();
      emit('changed', 'delete', { id });
    }
  } catch (e) {
    console.error(e);
  }
};

const handleDeleteConfirm = (id: number) => {
  Modal.warning({
    title: '确认删除？',
    content:
      '删除后，模块将不再可见；关联的需求/Bug/用例将失去模块归属，子模块将变为根模块。',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      await handleDelete(id);
    },
  });
};

// 收集当前节点及其所有后代节点的ID
const collectDescendantIds = (node: any): number[] => {
  const ids = [node.id];
  const children = node.children || [];
  children.forEach((child: any) => {
    ids.push(...collectDescendantIds(child));
  });
  return ids;
};

const handleTreeDrop = async ({ dragNode, dropNode, dropPosition }: any) => {
  // Arco Tree drop 事件返回原始数据字段（id/moduleName），不是 fieldNames 映射后的 key/title
  const moduleId = Number(dragNode.id);
  const dropNodeId = Number(dropNode.id);

  // 计算目标父节点ID
  let targetParentId: number;
  if (dropPosition === 0) {
    // 放到目标节点内部，成为子节点
    targetParentId = dropNodeId;
  } else {
    // 放到目标节点前面或后面，同级
    targetParentId = dropNode.parentId ?? 0;
  }

  // 不能把自己拖到自己里面
  if (moduleId === targetParentId) {
    Message.warning('不能将模块移动到自身内部');
    return;
  }

  // 不能把自己拖到后代节点里
  const descendantIds = collectDescendantIds(dragNode);
  if (descendantIds.includes(targetParentId)) {
    Message.warning('不能将模块移动到其子模块下');
    return;
  }

  // 计算目标索引
  const getSiblings = (parentId: number): any[] => {
    if (parentId === 0) {
      return moduleTreeData.value;
    }
    const findNode = (nodes: any[]): any =>
      nodes.find((node) => {
        if (node.id === parentId) return true;
        if (node.children?.length) {
          return !!findNode(node.children);
        }
        return false;
      }) || null;
    const parent = findNode(moduleTreeData.value);
    return parent?.children || [];
  };

  const siblings = getSiblings(targetParentId).filter(
    (s: any) => s.id !== moduleId
  );
  let targetIndex: number;
  if (dropPosition === 0) {
    // 放入内部，追加到子节点末尾
    targetIndex = siblings.length;
  } else {
    targetIndex = siblings.findIndex((s: any) => s.id === dropNodeId);
    if (targetIndex === -1) targetIndex = 0;
    if (dropPosition === 1) {
      targetIndex += 1;
    }
  }

  try {
    const res: any = await sortQaModule(
      moduleId,
      targetParentId,
      targetIndex
    );
    if (res.code === 200) {
      Message.success('排序已保存');
      await loadData();
      emit('changed', 'sort', { moduleId, targetParentId, targetIndex });
    }
  } catch (e) {
    console.error(e);
  }
};

watch(
  () => searchKey.value,
  (val) => {
    if (val) {
      isExpandAll.value = true;
      nextTick(() => {
        if (moduleTreeRef.value) {
          moduleTreeRef.value.expandAll(true);
        }
      });
    }
  }
);

onMounted(() => {
  loadData().then(() => {
    if (props.defaultExpandAll) {
      isExpandAll.value = true;
      nextTick(() => {
        moduleTreeRef.value?.expandAll?.(true);
      });
    }
  });
});

watch(
  () => projectStore.getProjectId,
  (newId) => {
    if (newId) loadData();
  },
  { immediate: true }
);

// 暴露展开/折叠方法给父组件
defineExpose({
  loadData,
  expandAll: (state: boolean) => {
    isExpandAll.value = state;
    nextTick(() => {
      moduleTreeRef.value?.expandAll?.(state);
    });
  },
});
</script>

<style scoped lang="less">
.module-tree-manager {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.module-list-search-wrap {
  padding: 0 16px;
  margin-bottom: 8px;
}

.module-list-search {
  width: 100%;
}

.module-list-actions {
  display: flex;
  width: 100%;
  padding: 0 16px;
  margin-bottom: 8px;
}

.module-list-scrollbar {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.module-list-scrollbar :deep(.arco-scrollbar-container) {
  overflow-x: hidden !important;
  overflow-y: auto !important;
  padding-right: 12px;
}

.tree-skeleton {
  padding: 16px;
}

:deep(.arco-tree-node) {
  border-radius: 4px;
}

:deep(.arco-tree-node-selected) {
  background-color: var(--color-primary-light-1);
}

:deep(.arco-tree-node-selected .arco-tree-node-title) {
  background-color: transparent;
}

:deep(.arco-tree-node:hover:not(.arco-tree-node-selected)) {
  background-color: var(--color-fill-1);
}

/* 隐藏 Arco Tree 默认拖拽手柄，避免占位；整个节点标题区域可拖动 */
:deep(.arco-tree-node-drag-icon) {
  display: none;
}

/* 展开/折叠 switcher 样式 */
:deep(.arco-tree-node-switcher) {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  min-width: 18px;
  margin-right: 2px;
}

:deep(.arco-tree-node-switcher-icon) {
  display: flex;
  align-items: center;
  justify-content: center;
}

:deep(.arco-tree-node-is-leaf .arco-tree-node-switcher) {
  width: 18px;
}

:deep(.arco-tree-node-switcher:hover) {
  color: rgb(var(--primary-6));
}

.switcher-icon-expanded,
.switcher-icon-collapsed {
  display: none;
}

:deep(.arco-tree-node-expanded) .switcher-icon-expanded {
  display: inline-flex;
}

:deep(.arco-tree-node-expanded) .switcher-icon-collapsed {
  display: none;
}

:deep(.arco-tree-node:not(.arco-tree-node-expanded):not(.arco-tree-node-is-leaf))
  .switcher-icon-expanded {
  display: none;
}

:deep(.arco-tree-node:not(.arco-tree-node-expanded):not(.arco-tree-node-is-leaf))
  .switcher-icon-collapsed {
  display: inline-flex;
}

/* 保留 min-width:0，确保 flex 子项可以收缩；不设置 overflow:hidden，
   否则 Arco Tree 拖拽落点指示线（::before 在标题上下沿外侧）会被裁掉 */
:deep(.arco-tree-node-title) {
  min-width: 0;
  margin: 1px 0;
}

:deep(.arco-tree-node-title-text) {
  display: flex !important;
  align-items: center;
  flex: 1 !important;
  min-width: 0 !important;
}

.tree-node-title {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  padding-right: 24px;
}

.tree-node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.tree-node-count {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-3);
  margin-left: 2px;
  font-variant-numeric: tabular-nums;
}

/* 更多操作默认隐藏，悬浮时绝对定位到节点标题右侧 */
:deep(.arco-tree-node) .node-extra-actions {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  visibility: hidden;
  z-index: 1;
}

:deep(.arco-tree-node:hover) .node-extra-actions {
  visibility: visible;
}

.node-extra-btn {
  padding: 0 4px;
}

.node-extra-icon {
  color: rgb(var(--primary-6));
  font-size: 12px;
}
</style>
