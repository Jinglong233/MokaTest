<template>
  <div class="navbar">
    <div class="left-side">
      <a-space>
        <img
            alt="logo"
            src="@/assets/logo.png"
            :style="{ height: '32px' }"
        />
        <a-typography-title
            :style="{ margin: 0, fontSize: '16px' }"
            :heading="5"
        >
          Moka Test
        </a-typography-title>

        <icon-menu-fold
            v-if="!topMenu && appStore.device === 'mobile'"
            style="font-size: 22px; cursor: pointer"
            @click="toggleDrawerMenu"
        />
      </a-space>
    </div>
    <div class="center-side">
      <Menu v-if="topMenu && !workspaceEnabled && !route.path.startsWith('/user/')"/>
    </div>
    <ul class="right-side">
      <!--      todo 隐藏搜索-->
      <!--      <li>
              <a-tooltip :content="$t('settings.search')">
                <a-button class="nav-btn" type="outline" :shape="'circle'">
                  <template #icon>
                    <icon-search/>
                  </template>
                </a-button>
              </a-tooltip>
            </li>-->

      <li v-if="hasEnvEntryPermission">
        <a-tooltip content="环境管理">
          <a-button
              class="nav-btn"
              type="outline"
              :shape="'circle'"
              @click="envConfigVisible = true"
          >
            <template #icon>
              <icon-cloud/>
            </template>
          </a-button>
        </a-tooltip>
      </li>
      <li>
        <a-tooltip :content="$t('settings.language')">
          <a-button
              class="nav-btn"
              type="outline"
              :shape="'circle'"
              @click="setDropDownVisible"
          >
            <template #icon>
              <icon-language/>
            </template>
          </a-button>
        </a-tooltip>
        <a-dropdown trigger="click" @select="changeLocale as any">
          <div ref="triggerBtn" class="trigger-btn"></div>
          <template #content>
            <a-doption
                v-for="item in locales"
                :key="item.value"
                :value="item.value"
            >
              <template #icon>
                <icon-check v-show="item.value === currentLocale"/>
              </template>
              {{ item.label }}
            </a-doption>
          </template>
        </a-dropdown>
      </li>
      <li>
        <a-tooltip
            :content="
            theme === 'light'
              ? $t('settings.navbar.theme.toDark')
              : $t('settings.navbar.theme.toLight')
          "
        >
          <a-button
              class="nav-btn"
              type="outline"
              :shape="'circle'"
              @click="handleToggleTheme"
          >
            <template #icon>
              <icon-moon-fill v-if="theme === 'dark'"/>
              <icon-sun-fill v-else/>
            </template>
          </a-button>
        </a-tooltip>
      </li>
      <!--      todo 隐藏消息盒子-->
      <li>
        <a-popover
            trigger="click"
            v-model:popupVisible="messagePopoverVisible"
            position="br"
            :auto-fit-position="true"
            :arrow-style="{ display: 'none' }"
            :content-style="{ padding: 0, width: '360px' }"
            content-class="message-popover"
        >
          <a-tooltip :content="$t('settings.navbar.alerts')">
            <div class="message-box-trigger">
              <a-badge :count="unreadCount" :dot="unreadCount > 0">
                <a-button
                    class="nav-btn"
                    type="outline"
                    :shape="'circle'"
                >
                  <icon-notification/>
                </a-button>
              </a-badge>
            </div>
          </a-tooltip>
          <template #content>
            <message-box/>
          </template>
        </a-popover>
      </li>
      <li>
        <a-tooltip
            :content="
            isFullscreen
              ? $t('settings.navbar.screen.toExit')
              : $t('settings.navbar.screen.toFull')
          "
        >
          <a-button
              class="nav-btn"
              type="outline"
              :shape="'circle'"
              @click="toggleFullScreen"
          >
            <template #icon>
              <icon-fullscreen-exit v-if="isFullscreen"/>
              <icon-fullscreen v-else/>
            </template>
          </a-button>
        </a-tooltip>
      </li>
      <!--      todo 隐藏设置-->
      <!--      <li>
              <a-tooltip :content="$t('settings.title')">
                <a-button
                    class="nav-btn"
                    type="outline"
                    :shape="'circle'"
                    @click="setVisible"
                >
                  <template #icon>
                    <icon-settings/>
                  </template>
                </a-button>
              </a-tooltip>
            </li>-->
      <li>
        <a-tooltip content="录制插件">
          <a-button
              class="nav-btn"
              type="outline"
              :shape="'circle'"
              @click="recorderModalVisible = true"
          >
            <template #icon>
              <icon-video-camera/>
            </template>
          </a-button>
        </a-tooltip>
      </li>
      <!--            todo 隐藏用户相关内容-->
      <li>
        <a-dropdown trigger="click">
          <div class="user-trigger">
            <span class="user-name">{{ displayName }}</span>
            <a-avatar
                :size="32"
            >
              <img alt="avatar" :src="avatar"/>
            </a-avatar>
          </div>
          <template #content>
            <a-doption>
              <a-space @click="goToUserSetting">
                <icon-user/>
                <span>{{ $t('messageBox.userCenter') }}</span>
              </a-space>
            </a-doption>
            <a-doption>
              <a-space @click="handleLogout">
                <icon-export/>
                <span>{{ $t('messageBox.logout') }}</span>
              </a-space>
            </a-doption>
          </template>
        </a-dropdown>
      </li>
    </ul>

    <!-- 环境管理弹窗（导航栏全局入口） -->
    <EvnConfig v-model="envConfigVisible" :team-id="teamStore.getTeamId ?? undefined"></EvnConfig>

    <!-- 录制插件获取弹窗 -->
    <RecorderGuideModal v-model:visible="recorderModalVisible"/>
  </div>
</template>

<script lang="ts" setup>
import {computed, inject, onMounted, onUnmounted, ref, watch} from 'vue';
import {Message} from '@arco-design/web-vue';
import {useDark, useFullscreen, useToggle} from '@vueuse/core';
import {useAppStore, usePermissionStore, useProjectStore, useUserStore, useWorkspaceStore} from '@/store';
import {LOCALE_OPTIONS} from '@/locale';
import useLocale from '@/hooks/locale';
import useUser from '@/hooks/user';
import Menu from '@/components/menu/index.vue';
import {useRoute, useRouter} from "vue-router";
import useTeamStore from "@/store/modules/team";
import useDataStore from "@/store/modules/nav";
import {useI18n} from 'vue-i18n';
import MessageBox from '@/components/message-box/index.vue'
import EvnConfig from '@/components/env-config/index.vue'
import RecorderGuideModal from '@/components/recorder-guide/RecorderGuideModal.vue'
import usePermission from '@/hooks/permission';
import { getUnreadCount } from '@/api/message';

const {t} = useI18n();
const router = useRouter();
const route = useRoute();
const appStore = useAppStore();
const userStore = useUserStore();
const workspaceStore = useWorkspaceStore();
const {logout} = useUser();
const {changeLocale, currentLocale} = useLocale();
const {isFullscreen, toggle: toggleFullScreen} = useFullscreen();
const locales = [...LOCALE_OPTIONS];

const workspaceEnabled = computed(() => workspaceStore.hasTabs && !route.path.startsWith('/team'));
const isSuperAdmin = computed(() => userStore.role === 'super_admin');

// 根据当前布局选择正确的用户页面路由
const inTeamLayout = computed(() => route.path.startsWith('/team'));
const goToUserSetting = () => {
  router.push(inTeamLayout.value ? '/team/userSetting' : '/user/setting');
};

const avatar = computed(() => {
  return userStore.avatar;
});
const displayName = computed(() => {
  return userStore.nickname || userStore.username || '未知用户';
});
const theme = computed(() => {
  return appStore.theme;
});
const topMenu = computed(() => appStore.topMenu && appStore.menu);
const isDark = useDark({
  selector: 'body',
  attribute: 'arco-theme',
  valueDark: 'dark',
  valueLight: 'light',
  storageKey: 'arco-theme',
  onChanged(dark: boolean) {
    // overridden default behavior
    appStore.toggleTheme(dark);
  },
});

const toggleTheme = useToggle(isDark);
const handleToggleTheme = () => {
  toggleTheme();
};
const setVisible = () => {
  appStore.updateSettings({globalSettings: true});
};
const triggerBtn = ref();
const handleLogout = () => {
  logout();
};
const setDropDownVisible = () => {
  const event = new MouseEvent('click', {
    view: window,
    bubbles: true,
    cancelable: true,
  });
  triggerBtn.value.dispatchEvent(event);
};
const toggleDrawerMenu = inject('toggleDrawerMenu') as () => void;

const permissionStore = usePermissionStore();
const projectStore = useProjectStore();
const teamStore = useTeamStore();

const dataStore = useDataStore();
const hasProjects = computed(() => dataStore.data && dataStore.data.length > 0);

const unreadCount = ref(0);
let unreadPollTimer: any = null;
const messagePopoverVisible = ref(false);

// 环境管理入口：与 ApiDebugForm 同一口径（查看环境或查看全局变量即可打开，弹窗内编辑由 update 权限自控）
const permission = usePermission();
const hasEnvEntryPermission = computed(() =>
    permission.hasPermission('auto:env:view') || permission.hasPermission('auto:globalvar:view'));
const envConfigVisible = ref(false);

// 录制插件弹窗
const recorderModalVisible = ref(false);

const fetchUnreadCount = async () => {
  try {
    const res: any = await getUnreadCount();
    if (res.status === 200 || res.code === 200) {
      unreadCount.value = res.data || 0;
    }
  } catch (e) {
    // 静默失败，不影晌导航栏
  }
};

onMounted(async () => {
  await dataStore.fetchData();
  // 有项目但未选择时才提示
  if (hasProjects.value && !projectStore.projectId) {
    Message.warning({
      content: '请选择项目',
      duration: 1000,
    });
    router.push({
      name: 'ProjectInfo'
    });
  }
  // 拉取未读消息数并启动轮询
  fetchUnreadCount();
  unreadPollTimer = setInterval(fetchUnreadCount, 30000);
  window.addEventListener('message-read', fetchUnreadCount);
  window.addEventListener('close-message-popover', () => {
    messagePopoverVisible.value = false;
  });
});

onUnmounted(() => {
  if (unreadPollTimer) {
    clearInterval(unreadPollTimer);
  }
  window.removeEventListener('message-read', fetchUnreadCount);
  window.removeEventListener('close-message-popover', () => {
    messagePopoverVisible.value = false;
  });
});


const handleProjectChange = (projectId: string | number) => {
  const pid = Number(projectId);
  // 同时写入 id 和 name，避免只更新 id 导致 projectName 残留上一个项目（pinia 项目信息不一致）
  const proj = dataStore.data.find((p: any) => p.id === pid);
  projectStore.setProject(pid, proj?.projectName || '');
  // 跳转项目概览页
  router.push({
    name: 'ProjectInfo'
  })
}

watch(
    () => teamStore.getTeamId,
    async (newTeamId, oldTeamId) => {
      if (newTeamId) {
        await dataStore.fetchData();
        if (hasProjects.value) {
          // 仅当「当前未选项目」或「所选项目不属于当前团队」时才默认选第一个；
          // 否则保留用户已进入/已选择的项目，避免被强制重置（进入项目后被改、刷新后跳回第一个）
          const currentPid = projectStore.projectId;
          const stillValid =
              currentPid != null && dataStore.data.some((p: any) => p.id === currentPid);
          if (!stillValid) {
            const firstProject = dataStore.data[0] as any;
            projectStore.setProject(firstProject?.id, firstProject?.projectName);
          }
        } else {
          // 无项目时清空当前选中
          projectStore.clearProject();
        }
        // 有项目但未选择时才提示
        if (hasProjects.value && !projectStore.projectId) {
          Message.warning({
            content: '请选择项目',
            duration: 1000,
          });
          router.push({
            name: 'ProjectInfo'
          });
        }
      }
    },
    {immediate: true, deep: true} // 立即执行一次
);


</script>

<style scoped lang="less">
.navbar {
  display: flex;
  justify-content: space-between;
  height: 100%;
  background-color: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border);
}

.left-side {
  display: flex;
  align-items: center;
  padding-left: 16px;
}

.center-side {
  flex: 1;
}

.right-side {
  display: flex;
  padding-right: 16px;
  list-style: none;

  :deep(.locale-select) {
    border-radius: 20px;
  }

  li {
    display: flex;
    align-items: center;
    padding: 0 10px;
  }

  a {
    color: var(--color-text-1);
    text-decoration: none;
  }

  .nav-btn {
    border-color: rgb(var(--gray-2));
    color: rgb(var(--gray-8));
    font-size: 16px;
  }

  .trigger-btn {
    position: absolute;
    bottom: 14px;
    margin-left: 14px;
  }

  .user-trigger {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
  }

  .user-name {
    font-size: 14px;
    color: var(--color-text-1);
    max-width: 100px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

</style>

<style lang="less">
.message-popover {
  .arco-popover-content {
    margin-top: 0;
  }
}

</style>
