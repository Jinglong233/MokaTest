<template>
  <div v-if="workspaceStore.hasTabs" class="workspace-menu-bar">
    <a-space class="workspace-context" size="medium">
      <div class="team-name">
        <icon-apps />
        <span>{{ workspaceStore.activeTab?.teamName || '当前团队' }}</span>
      </div>
      <a-divider direction="vertical" />
      <a-select
        :style="{width:'280px'}"
        :model-value="workspaceStore.activeProjectId"
        :placeholder="hasProjects ? '请选择项目' : '暂无项目'"
        :disabled="!hasProjects"
        @change="handleProjectChange"
        @dropdown-visible-change="handleDropdownVisibleChange"
      >
        <template #prefix>
          {{ hasProjects ? '项目:' : '暂无项目' }}
        </template>
        <a-option
          v-for="project in dataStore.data"
          :key="project.id"
          :value="project.id"
        >
          <div class="project-option">
            <span class="project-option-name">{{ project.projectName }}</span>
            <a-tag
              v-if="project.myRoleName"
              size="small"
              :color="myRoleTagColor(project.myRoleCode)"
            >{{ project.myRoleName }}</a-tag>
          </div>
        </a-option>
      </a-select>
    </a-space>

    <div class="workspace-menu">
      <Menu mode="horizontal" />
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted} from 'vue';
import {useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import {useWorkspaceStore, useProjectStore} from '@/store';
import useDataStore from '@/store/modules/nav';
import Menu from '@/components/menu/index.vue';

const workspaceStore = useWorkspaceStore();
const projectStore = useProjectStore();
const dataStore = useDataStore();
const router = useRouter();

const hasProjects = computed(() => dataStore.data && dataStore.data.length > 0);

// 项目角色标签颜色（与项目列表页一致）
const myRoleTagColor = (code?: string) => {
  switch (code) {
    case 'project_admin':
      return 'red';
    case 'team_admin':
      return 'purple';
    case 'super_admin':
      return 'red';
    case 'creator':
      return 'orange';
    default:
      return 'arcoblue';
  }
};

// 下拉展开时实时刷新项目列表（含当前用户在每个项目的角色），保证角色变更后立即生效
const handleDropdownVisibleChange = async (visible: boolean) => {
  if (!visible) return;
  try {
    await dataStore.fetchData();
  } catch (e) {
    // 刷新失败不影响下拉使用（沿用现有数据）
  }
};

const handleProjectChange = (projectId: string | number) => {
  const pid = Number(projectId);
  const project = dataStore.data.find((p) => p.id === pid);
  workspaceStore.updateActiveProject(pid, project?.projectName);
  projectStore.setProject(pid, project?.projectName || '');
  // 切换项目后跳转到项目概览，保持菜单状态清晰
  router.push({name: 'ProjectInfo'});
};

onMounted(async () => {
  // 从持久化恢复时，若项目列表为空则重新拉取
  if (workspaceStore.activeTabId && dataStore.data.length === 0) {
    try {
      await dataStore.fetchData();
    } catch (e) {
      Message.warning('加载项目列表失败');
    }
  }
});
</script>

<style scoped lang="less">
.project-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;

  .project-option-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.workspace-menu-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 12px;
  background-color: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border);

  .workspace-context {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    width: 420px;
    max-width: 420px;

    .team-name {
      display: flex;
      align-items: center;
      gap: 8px;
      width: 120px;
      max-width: 120px;
      font-weight: 500;
      color: var(--color-text-1);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;

      span {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
      }
    }
  }

  .workspace-menu {
    flex: 1;
    min-width: 0;
    height: 48px;
    min-height: 48px;
    max-height: 48px;
    overflow: hidden;
    display: flex;
    justify-content: flex-end;

    :deep(.arco-menu-horizontal) {
      height: 48px;
      min-height: 48px;
      max-height: 48px;
      border-bottom: none;
      overflow: hidden;
      white-space: nowrap;
      transition: none;
    }

    :deep(.arco-menu-inner) {
      height: 48px;
      min-height: 48px;
      max-height: 48px;
      overflow: hidden;
      white-space: nowrap;
      transition: none;
    }

    :deep(.arco-menu-item),
    :deep(.arco-sub-menu-title) {
      height: 48px;
      line-height: 48px;
      padding: 0 12px;
      transition: none;
    }

    :deep(.arco-menu-selected-label) {
      transition: none !important;
    }
  }
}
</style>
