<template>
  <div>
    <a-layout class="layout" :class="{ mobile: appStore.hideMenu }" :style="layoutStyle">
      <div v-if="navbar" class="navbar-wrapper">
        <NavBar/>
      </div>
      <WorkspaceTabs v-if="workspaceEnabled" />
      <a-layout class="layout-main">
        <a-layout-sider
            v-if="showSidebar"
            v-show="!hideMenu"
            class="layout-sider"
            breakpoint="xl"
            :collapsed="collapsed"
            :collapsible="true"
            :width=Number(menuWidth)
            :hide-trigger="true"
            @collapse="setCollapsed"
        >
          <div v-if="workspaceEnabled && !collapsed" class="workspace-sider-context">
            <div class="workspace-team-name">
              <icon-apps />
              <a-tooltip :content="workspaceStore.activeTab?.teamName || '未选择团队'">
                <div class="team-info">
                  <span class="team-label">当前团队</span>
                  <span class="team-name">{{ workspaceStore.activeTab?.teamName || '未选择团队' }}</span>
                </div>
              </a-tooltip>
            </div>
            <a-select
                :style="{ width: '100%' }"
                size="small"
                :model-value="workspaceStore.activeProjectId"
                :placeholder="hasProjects ? '请选择项目' : '暂无项目'"
                :disabled="!hasProjects"
                @change="handleProjectChange"
                @dropdown-visible-change="handleProjectDropdownVisibleChange"
            >
              <template #prefix>
                <icon-folder />
              </template>
              <template #label>
                <div class="project-option">
                  <span class="project-option-name">{{ currentProject?.projectName || '' }}</span>
                  <a-tag
                    v-if="currentProject?.myRoleName"
                    size="small"
                    :color="myRoleTagColor(currentProject.myRoleCode)"
                  >{{ currentProject.myRoleName }}</a-tag>
                </div>
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
          </div>
          <div class="menu-wrapper">
            <Menu mode="vertical" />
          </div>
        </a-layout-sider>

        <a-drawer
            v-if="hideMenu && showSidebar"
            :visible="drawerVisible"
            placement="left"
            :footer="false"
            mask-closable
            :closable="false"
            @cancel="drawerCancel"
        >
          <div v-if="workspaceEnabled && !collapsed" class="workspace-sider-context">
            <div class="workspace-team-name">
              <icon-apps />
              <a-tooltip :content="workspaceStore.activeTab?.teamName || '未选择团队'">
                <div class="team-info">
                  <span class="team-label">当前团队</span>
                  <span class="team-name">{{ workspaceStore.activeTab?.teamName || '未选择团队' }}</span>
                </div>
              </a-tooltip>
            </div>
            <a-select
                :style="{ width: '100%' }"
                size="small"
                :model-value="workspaceStore.activeProjectId"
                :placeholder="hasProjects ? '请选择项目' : '暂无项目'"
                :disabled="!hasProjects"
                @change="handleProjectChange"
                @dropdown-visible-change="handleProjectDropdownVisibleChange"
            >
              <template #prefix>
                <icon-folder />
              </template>
              <template #label>
                <div class="project-option">
                  <span class="project-option-name">{{ currentProject?.projectName || '' }}</span>
                  <a-tag
                    v-if="currentProject?.myRoleName"
                    size="small"
                    :color="myRoleTagColor(currentProject.myRoleCode)"
                  >{{ currentProject.myRoleName }}</a-tag>
                </div>
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
          </div>
          <Menu mode="vertical" />
        </a-drawer>

        <a-layout class="layout-content" :style="contentPaddingStyle">
          <TabBar v-if="appStore.tabBar"/>
          <a-layout-content class="layout-page-container">
            <PageLayout/>
          </a-layout-content>
          <Footer v-if="footer"/>
        </a-layout>
      </a-layout>
    </a-layout>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, provide, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import {useAppStore, useUserStore, useWorkspaceStore, useProjectStore} from '@/store';
import useDataStore from '@/store/modules/nav';
import NavBar from '@/components/navbar/index.vue';
import Menu from '@/components/menu/index.vue';
import Footer from '@/components/footer/index.vue';
import TabBar from '@/components/tab-bar/index.vue';
import WorkspaceTabs from '@/components/workspace-tabs/index.vue';
import usePermission from '@/hooks/permission';
import useResponsive from '@/hooks/responsive';
import PageLayout from './page-layout.vue';

const isInit = ref(false);
const appStore = useAppStore();
const userStore = useUserStore();
const workspaceStore = useWorkspaceStore();
const projectStore = useProjectStore();
const dataStore = useDataStore();
const router = useRouter();
const route = useRoute();
const permission = usePermission();
useResponsive(true);
const navbar = computed(() => appStore.navbar);
const workspaceEnabled = computed(() => workspaceStore.hasTabs && !route.path.startsWith('/team'));
const renderMenu = computed(() => appStore.menu && !appStore.topMenu);
const showSidebar = computed(() => {
  // 用户中心/设置页面强制显示侧边栏
  if (route.path.startsWith('/user/')) return true;
  if (!appStore.menu || hideMenu.value) return false;
  if (workspaceEnabled.value) return true;
  return renderMenu.value;
});
const hideMenu = computed(() => appStore.hideMenu);
const footer = computed(() => appStore.footer);
const menuWidth = computed(() => {
  return appStore.menuCollapse ? '48' : `${appStore.menuWidth}`;
});
const collapsed = computed(() => {
  return appStore.menuCollapse;
});
const pageContainerHeight = computed(() => {
  let offset = 0;
  if (navbar.value) offset += 48;
  if (workspaceEnabled.value) offset += 40;
  if (appStore.tabBar) offset += 33; // 页签栏：32px 高度 + 1px 底边框
  if (footer.value) offset += 20;
  return `calc(100vh - ${offset}px)`;
});
const layoutStyle = computed(() => ({
  '--page-container-height': pageContainerHeight.value,
}));
const contentPaddingStyle = computed(() => {
  return {};
});

const hasProjects = computed(() => dataStore.data && dataStore.data.length > 0);

// 当前选中项目（用于 select 选中态展示项目名 + 我的角色标签）
const currentProject = computed(() =>
  dataStore.data.find(
    (p) => String(p.id) === String(workspaceStore.activeProjectId)
  )
);

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

// 项目下拉展开时实时刷新项目列表（含当前用户在每个项目的角色），保证角色变更后立即生效
const handleProjectDropdownVisibleChange = async (visible: boolean) => {
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
  router.push({name: 'ProjectInfo'});
};

const setCollapsed = (val: boolean) => {
  if (!isInit.value) return;
  appStore.updateSettings({menuCollapse: val});
};
watch(
    () => userStore.role,
    (roleValue) => {
      if (roleValue && !permission.accessRouter(route))
        router.push({name: 'notFound'});
    }
);
const drawerVisible = ref(false);
const drawerCancel = () => {
  drawerVisible.value = false;
};
provide('toggleDrawerMenu', () => {
  drawerVisible.value = !drawerVisible.value;
});

// 向子模块注入当前工作区的 teamId 和 projectId
provide('teamId', computed(() => workspaceStore.activeTeamId));
provide('projectId', computed(() => workspaceStore.activeProjectId));

// 监听路由变化，保存当前工作区的路由快照
watch(
    () => ({
      name: typeof route.name === 'string' ? route.name : undefined,
      path: route.path,
      query: {...route.query},
    }),
    (routeSnapshot) => {
      if (workspaceStore.activeTabId) {
        workspaceStore.updateActiveRoute(routeSnapshot);
      }
    },
    {deep: true}
);

onMounted(async () => {
  isInit.value = true;
  if (workspaceEnabled.value && workspaceStore.activeTabId && dataStore.data.length === 0) {
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
@layout-max-width: 1100px;

.layout {
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.layout-main {
  flex: 1;
  min-height: 0;
}

.layout-sider {
  height: 100%;
  min-width: v-bind(menuWidth) !important;
  max-width: v-bind(menuWidth) !important;
  transition: all 0.2s cubic-bezier(0.34, 0.69, 0.1, 1);

  &::after {
    position: absolute;
    top: 0;
    right: -1px;
    display: block;
    width: 1px;
    height: 100%;
    background-color: var(--color-border);
    content: '';
  }

  > :deep(.arco-layout-sider-children) {
    display: flex;
    flex-direction: column;
    overflow-y: hidden;
  }
}

.workspace-sider-context {
  flex-shrink: 0;
  padding: 12px;
  border-bottom: 1px solid var(--color-border);

  .workspace-team-name {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    overflow: hidden;

    .team-info {
      display: flex;
      align-items: center;
      gap: 4px;
      overflow: hidden;
      flex: 1;
      min-width: 0;
    }

    .team-label {
      flex-shrink: 0;
      font-size: 12px;
      color: var(--color-text-3);
    }

    .team-name {
      flex: 1;
      min-width: 0;
      font-weight: 500;
      font-size: 14px;
      color: var(--color-text-1);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
    }

    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.menu-wrapper {
  flex: 1;
  min-width: 0;
  overflow: auto;
  overflow-x: hidden;

  :deep(.arco-menu) {
    min-width: 100%;
  }

  :deep(.arco-menu-inner) {
    ::-webkit-scrollbar {
      width: 12px;
      height: 4px;
    }

    ::-webkit-scrollbar-thumb {
      border: 4px solid transparent;
      background-clip: padding-box;
      border-radius: 7px;
      background-color: var(--color-text-4);
    }

    ::-webkit-scrollbar-thumb:hover {
      background-color: var(--color-text-3);
    }
  }
}

.layout-content {
  flex: 1;
  overflow: hidden;
  background-color: var(--color-fill-2);
}

.layout-page-container {
  height: var(--page-container-height, calc(100vh - 48px));
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.navbar-wrapper {
  height: 48px;
  flex-shrink: 0;
}
</style>
