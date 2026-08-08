<template>
  <div
    v-if="tabList.length > 0"
    class="api-tabs-wrapper"
    ref="wrapperRef"
  >
    <a-tabs
      class="api-tabs"
      v-model:active-key="currentActiveKey"
      type="card"
      :editable="true"
      :show-add-button="hasApiCreatePermission"
      @add="handleAdd"
    >
      <a-tab-pane
        v-for="tab in tabList"
        :key="tab.key"
        :title="tab.apiName || '未命名接口'"
        :closable="false"
      >
        <template #title>
          <div
            class="tab-title-wrapper"
            @contextmenu.prevent="(e) => openContextMenu(e, tab)"
          >
            <span>{{ tab.apiName || '未命名接口' }}</span>
            <span
              class="tab-close"
              :class="{ 'has-changes': tab.unsavedChanges }"
              @click.stop="handleCloseTab(tab.key)"
            >
              <icon-close />
            </span>
          </div>
        </template>

        <div class="tab-content-wrapper">
          <slot
            :name="`tab-${tab.key}`"
            :tab="tab"
            :on-change="
              (hasChanges) => updateUnsavedChanges(tab.key, hasChanges)
            "
          >
          </slot>
        </div>
      </a-tab-pane>
    </a-tabs>

    <!-- 右键菜单 -->
    <div
      v-if="contextMenuVisible"
      ref="menuRef"
      class="context-menu"
      :style="contextMenuStyle"
      @click.stop
    >
      <div class="menu-item" @click="handleMenuClick('close')">
        <icon-close />
        <span>关闭当前标签页</span>
        <span v-if="currentTab?.unsavedChanges" class="unsaved-badge">●</span>
      </div>
      <div class="menu-item" @click="handleMenuClick('closeOthers')">
        <icon-minus-circle />
        <span>关闭其他标签页</span>
        <span
          v-if="hasOtherUnsavedChanges(currentTab?.key)"
          class="unsaved-badge"
          >●</span
        >
      </div>
      <div class="menu-item" @click="handleMenuClick('closeLeft')">
        <icon-arrow-left />
        <span>关闭左侧标签页</span>
        <span
          v-if="hasLeftUnsavedChanges(currentTab?.key)"
          class="unsaved-badge"
          >●</span
        >
      </div>
      <div class="menu-item" @click="handleMenuClick('closeRight')">
        <icon-arrow-right />
        <span>关闭右侧标签页</span>
        <span
          v-if="hasRightUnsavedChanges(currentTab?.key)"
          class="unsaved-badge"
          >●</span
        >
      </div>
      <div class="menu-divider" />
      <div class="menu-item" @click="handleMenuClick('closeAll')">
        <icon-delete />
        <span>关闭所有标签页</span>
        <span v-if="hasAnyUnsavedChanges()" class="unsaved-badge">●</span>
      </div>
    </div>

    <!-- 确认对话框 -->
    <a-modal
      v-model:visible="confirmModal.visible"
      :title="confirmModal.title"
      @ok="handleConfirmOk"
      @cancel="handleConfirmCancel"
    >
      <p>{{ confirmModal.message }}</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { ref, watch, onUnmounted, nextTick, computed } from 'vue';
  import {
    IconClose,
    IconMinusCircle,
    IconArrowLeft,
    IconArrowRight,
    IconDelete,
  } from '@arco-design/web-vue/es/icon';
  import { ApiRequest } from '@/types/domain/api/ApiRequest';
  import usePermission from '@/hooks/permission';

  const permission = usePermission();
  const hasApiCreatePermission = computed(() => permission.hasPermission('auto:api:create'));

  interface TabItem extends ApiRequest {
    key: string;
    unsavedChanges?: boolean;
  }

  const props = defineProps<{
    modelValue?: string;
    tabs?: TabItem[];
  }>();

  const emit = defineEmits<{
    (e: 'update:modelValue', value: string): void;
    (e: 'update:tabs', tabs: TabItem[]): void;
    (e: 'add'): void;
    (e: 'close', key: string): void;
  }>();

  const currentActiveKey = ref<string>(
    props.modelValue || props.tabs?.[0]?.key || ''
  );
  const tabList = ref<TabItem[]>([...(props.tabs || [])]);
  const contextMenuVisible = ref(false);
  const contextMenuStyle = ref({ top: '0px', left: '0px' });
  const currentTab = ref<TabItem | null>(null);

  const confirmModal = ref({
    visible: false,
    title: '',
    message: '',
    type: '',
    targetKeys: [] as string[],
  });

  const updateUnsavedChanges = (key: string, hasChanges: boolean) => {
    const tab = tabList.value.find((t) => t.key === key);
    if (tab && tab.unsavedChanges !== hasChanges) {
      tab.unsavedChanges = hasChanges;
      emit('update:tabs', [...tabList.value]);
    }
  };

  const hasOtherUnsavedChanges = (currentKey?: string) => {
    if (!currentKey) return false;
    return tabList.value.some(
      (tab) => tab.key !== currentKey && tab.unsavedChanges
    );
  };

  const hasLeftUnsavedChanges = (currentKey?: string) => {
    if (!currentKey) return false;
    const index = tabList.value.findIndex((tab) => tab.key === currentKey);
    return tabList.value.slice(0, index).some((tab) => tab.unsavedChanges);
  };

  const hasRightUnsavedChanges = (currentKey?: string) => {
    if (!currentKey) return false;
    const index = tabList.value.findIndex((tab) => tab.key === currentKey);
    return tabList.value.slice(index + 1).some((tab) => tab.unsavedChanges);
  };

  const hasAnyUnsavedChanges = () => {
    return tabList.value.some((tab) => tab.unsavedChanges);
  };

  const getCloseKeys = (type: string, currentKey?: string): string[] => {
    switch (type) {
      case 'close':
        return currentKey ? [currentKey] : [];
      case 'closeOthers':
        return tabList.value
          .filter((tab) => tab.key !== currentKey)
          .map((tab) => tab.key);
      case 'closeLeft': {
        if (!currentKey) return [];
        const index = tabList.value.findIndex((tab) => tab.key === currentKey);
        return tabList.value.slice(0, index).map((tab) => tab.key);
      }
      case 'closeRight': {
        if (!currentKey) return [];
        const index = tabList.value.findIndex((tab) => tab.key === currentKey);
        return tabList.value.slice(index + 1).map((tab) => tab.key);
      }
      case 'closeAll':
        return tabList.value.map((tab) => tab.key);
      default:
        return [];
    }
  };

  const executeClose = (type: string, targetKeys: string[]) => {
    switch (type) {
      case 'close':
        if (targetKeys[0]) {
          const index = tabList.value.findIndex(
            (tab) => tab.key === targetKeys[0]
          );
          if (index !== -1) {
            tabList.value.splice(index, 1);
            if (
              currentActiveKey.value === targetKeys[0] &&
              tabList.value.length > 0
            ) {
              currentActiveKey.value =
                tabList.value[Math.min(index, tabList.value.length - 1)].key;
            }
          }
          // 通知父组件清理数据
          emit('close', targetKeys[0]);
        }
        break;
      case 'closeOthers': {
        const keepKey = currentTab.value?.key;
        if (keepKey) {
          const closedKeys = tabList.value
            .filter((tab) => tab.key !== keepKey)
            .map((tab) => tab.key);
          tabList.value = tabList.value.filter((tab) => tab.key === keepKey);
          currentActiveKey.value = keepKey;
          // 通知父组件清理所有被关闭的 tab 数据
          closedKeys.forEach((key) => emit('close', key));
        }
        break;
      }
      case 'closeLeft': {
        const currentKey = currentTab.value?.key;
        if (currentKey) {
          const index = tabList.value.findIndex(
            (tab) => tab.key === currentKey
          );
          const closedKeys = tabList.value
            .slice(0, index)
            .map((tab) => tab.key);
          tabList.value = tabList.value.slice(index);
          // 通知父组件清理
          closedKeys.forEach((key) => emit('close', key));
        }
        break;
      }
      case 'closeRight': {
        const currentKey = currentTab.value?.key;
        if (currentKey) {
          const index = tabList.value.findIndex(
            (tab) => tab.key === currentKey
          );
          const closedKeys = tabList.value
            .slice(index + 1)
            .map((tab) => tab.key);
          tabList.value = tabList.value.slice(0, index + 1);
          // 通知父组件清理
          closedKeys.forEach((key) => emit('close', key));
        }
        break;
      }
      case 'closeAll': {
        const closedKeys = [...tabList.value.map((tab) => tab.key)];
        tabList.value = [];
        currentActiveKey.value = '';
        // 通知父组件清理所有数据
        closedKeys.forEach((key) => emit('close', key));
        break;
      }
    }
    emit('update:tabs', [...tabList.value]);
    confirmModal.value.visible = false;
  };

  const showConfirm = (type: string, targetKeys: string[]) => {
    const unsavedKeys = targetKeys.filter(
      (key) => tabList.value.find((t) => t.key === key)?.unsavedChanges
    );
    if (unsavedKeys.length === 0) {
      executeClose(type, targetKeys);
      return;
    }

    const unsavedTabs = unsavedKeys
      .map(
        (key) => tabList.value.find((t) => t.key === key)?.apiName || '未命名'
      )
      .join('、');
    confirmModal.value = {
      visible: true,
      title: '提示',
      message: `以下标签页有未保存的修改：${unsavedTabs}\n确定关闭吗？`,
      type,
      targetKeys,
    };
  };

  const handleConfirmOk = () => {
    executeClose(confirmModal.value.type, confirmModal.value.targetKeys);
  };

  const handleConfirmCancel = () => {
    confirmModal.value.visible = false;
  };

  const handleCloseTab = (key: string) => {
    showConfirm('close', [key]);
  };

  const handleMenuClick = (value: string) => {
    if (!currentTab.value) return;
    const targetKeys = getCloseKeys(value, currentTab.value.key);
    if (targetKeys.length === 0) return;
    showConfirm(value, targetKeys);
    closeContextMenu();
  };

  const wrapperRef = ref(null);
  const menuRef = ref(null);

  const openContextMenu = (e: MouseEvent, tab: TabItem) => {
    e.preventDefault();
    e.stopPropagation();
    closeContextMenu();
    currentTab.value = tab;

    nextTick(() => {
      const { clientX, clientY } = e;
      const { innerWidth, innerHeight } = window;
      let top = clientY,
        left = clientX;
      if (left + 180 > innerWidth) left = innerWidth - 190;
      if (top + 200 > innerHeight) top = innerHeight - 210;
      contextMenuStyle.value = { top: `${top}px`, left: `${left}px` };
      contextMenuVisible.value = true;
      document.addEventListener('click', handleGlobalClick, true);
    });
  };

  const closeContextMenu = () => {
    contextMenuVisible.value = false;
    currentTab.value = null;
    document.removeEventListener('click', handleGlobalClick, true);
  };

  const handleGlobalClick = (e: MouseEvent) => {
    if (!menuRef.value?.contains(e.target as Node)) closeContextMenu();
  };

  const handleAdd = () => emit('add');

  watch(
    () => props.tabs,
    (newTabs) => {
      if (newTabs) {
        tabList.value = [...newTabs];
      }
    },
    { deep: true }
  );

  // 监听外部 activeKey 的变化
  watch(
    () => props.modelValue,
    (newVal) => {
      if (newVal && newVal !== currentActiveKey.value) {
        currentActiveKey.value = newVal;
      }
    }
  );

  watch(
    () => currentActiveKey.value,
    (newKey) => {
      emit('update:modelValue', newKey);
    }
  );

  onUnmounted(() => closeContextMenu());

  defineExpose({
    addTab: (tab: TabItem) => {
      tabList.value.push({ ...tab, unsavedChanges: false });
      currentActiveKey.value = tab.key;
      emit('update:tabs', [...tabList.value]);
    },
  });
</script>

<style scoped>

  .tab-title-wrapper {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    width: 100px;
    overflow: hidden;
  }

  .tab-title-wrapper span:first-child {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  .tab-close {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    cursor: pointer;
    opacity: 0;
    flex-shrink: 0;
  }

  .tab-title-wrapper:hover .tab-close {
    opacity: 1;
  }

  .tab-close.has-changes {
    opacity: 1;
    background-color: rgb(22, 93, 255);
    color: white;
  }

  .tab-close:not(.has-changes):hover {
    background-color: rgba(0, 0, 0, 0.1);
  }

  .context-menu {
    position: fixed;
    z-index: 9999;
    background: white;
    border-radius: 4px;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
    padding: 4px 0;
    min-width: 160px;
  }

  .menu-item {
    padding: 8px 16px;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .menu-item:hover {
    background: #f2f3f5;
  }

  .menu-divider {
    height: 1px;
    background: #e5e6eb;
    margin: 4px 0;
  }

  .unsaved-badge {
    color: rgb(22, 93, 255);
    font-size: 12px;
    margin-left: auto;
  }

  .api-tabs-wrapper {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  .api-tabs {
    display: flex;
    flex-direction: column;
    flex: 1;
    height: 100%;
    overflow: hidden;
  }

  .api-tabs :deep(.arco-tabs) {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  .api-tabs :deep(.arco-tabs-nav) {
    margin-bottom: 8px;
  }

  .api-tabs :deep(.arco-tabs-content) {
    flex: 1;
    overflow: hidden;
    padding: 0;
  }

  .api-tabs :deep(.arco-tabs-content-list) {
    height: 100%;
  }

  .api-tabs :deep(.arco-tabs-pane) {
    height: 100%;
    overflow: hidden;
    padding: 0;
  }

  .tab-content-wrapper {
    height: 100%;
    overflow: hidden;
  }
</style>
