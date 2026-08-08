<template>
  <div v-if="workspaceStore.hasTabs" class="workspace-tabs">
    <a-tabs
      :active-key="workspaceStore.activeTabId"
      type="card"
      size="medium"
      :editable="true"
      @change="handleSwitchTab"
      @delete="handleCloseTab"
    >
      <a-tab-pane
        v-for="tab in workspaceStore.tabs"
        :key="tab.id"
        :title="tab.title"
      />
    </a-tabs>
  </div>
</template>

<script lang="ts" setup>
import {useRouter} from 'vue-router';
import {useWorkspaceStore, useProjectStore} from '@/store';
import useTeamStore from '@/store/modules/team';
import useDataStore from '@/store/modules/nav';

const workspaceStore = useWorkspaceStore();
const projectStore = useProjectStore();
const teamStore = useTeamStore();
const dataStore = useDataStore();
const router = useRouter();

// 关闭全部页签 / 新建工作区时的返回入口统一回到团队工作台
const backRouteName = 'TeamWorkspace';

/**
 * 同步当前激活 tab 的团队/项目到全局 store，保持旧代码兼容
 */
async function syncActiveTabContext() {
  const tab = workspaceStore.activeTab;
  if (!tab) return;
  teamStore.setTeam(String(tab.teamId), tab.teamName || '');
  await dataStore.fetchData();
  if (tab.activeProjectId) {
    const project = dataStore.data.find((p: any) => p.id === tab.activeProjectId);
    projectStore.setProject(tab.activeProjectId, project?.projectName || tab.projectName || '');
  } else {
    projectStore.clearProject();
  }
}

const handleSwitchTab = async (tabId: string | number) => {
  const switched = workspaceStore.switchTab(String(tabId));
  if (!switched) return;
  await syncActiveTabContext();
  if (switched.savedRoute?.name) {
    router.push({
      name: switched.savedRoute.name,
      query: switched.savedRoute.query,
    });
  } else {
    router.push({name: 'ProjectInfo'});
  }
};

const handleCloseTab = async (tabId: string | number) => {
  const nextId = workspaceStore.closeTab(String(tabId));
  if (!nextId) {
    projectStore.clearProject();
    // 返回团队工作台，保留当前团队上下文
    router.push({name: backRouteName});
    return;
  }
  await syncActiveTabContext();
  const nextTab = workspaceStore.activeTab;
  if (nextTab?.savedRoute?.name) {
    router.push({
      name: nextTab.savedRoute.name,
      query: nextTab.savedRoute.query,
    });
  } else {
    router.push({name: 'ProjectInfo'});
  }
};
</script>

<style scoped lang="less">
.workspace-tabs {
  background-color: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border);
  padding: 0 12px 0 0;

  :deep(.arco-tabs) {
    width: 100%;
  }

  :deep(.arco-tabs-nav) {
    margin-bottom: 0;
    padding-left: 0;
  }

  :deep(.arco-tabs-nav-scroll) {
    margin-left: 0 !important;
  }

  :deep(.arco-tabs-content) {
    display: none;
  }

  :deep(.arco-tabs-tab) {
    user-select: none;
    height: 32px;
    line-height: 32px;
    font-size: 13px;
  }

  :deep(.arco-tabs-tab-active) {
    position: relative;
    color: rgb(var(--primary-6));
    font-weight: 500;
    background-color: var(--color-bg-1);
  }

  :deep(.arco-tabs-tab-active::after) {
    content: '';
    position: absolute;
    bottom: 0;
    left: 0;
    right: 0;
    height: 2px;
    background-color: rgb(var(--primary-6));
  }
}
</style>
