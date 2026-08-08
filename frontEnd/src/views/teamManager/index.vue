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
            :style="{ margin: 0, fontSize: '18px' }"
            :heading="5"
        >
          Moka Test
        </a-typography-title>
      </a-space>
    </div>
    <ul class="right-side">
      <li>
        <a-dropdown trigger="click" @select="changeLocale as any">
          <a-tooltip content="语言">
            <a-button
                class="nav-btn"
                type="outline"
                :shape="'circle'"
                aria-label="切换语言"
            >
              <template #icon>
                <icon-language/>
              </template>
            </a-button>
          </a-tooltip>
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
              ? '点击切换为暗黑模式'
              : '点击切换为亮色模式'
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
      <li>
        <a-tooltip
            :content="
            isFullscreen
              ? '点击退出全屏模式'
              : '点击切换全屏模式'
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
      <li>
        <a-dropdown trigger="click">
          <div class="user-trigger">
            <span class="user-name">{{ displayName }}</span>
            <a-avatar
                :size="32"
                :style="{ cursor: 'pointer' }"
            >
              <img alt="avatar" :src="avatar"/>
            </a-avatar>
          </div>
          <template #content>
            <a-doption>
              <a-space @click="$router.push('/team/userSetting')">
                <icon-settings/>
                <span>个人设置</span>
              </a-space>
            </a-doption>
            <a-doption>
              <a-space @click="handleLogout">
                <icon-export/>
                <span>退出登录</span>
              </a-space>
            </a-doption>
          </template>
        </a-dropdown>
      </li>
    </ul>
  </div>
  <a-layout class="layout-demo">
    <a-layout-sider
        hide-trigger
        collapsible
    >
      <div class="logo"/>
      <a-menu
          :selected-keys="[selectedKey]"
          :style="{ width: '100%' }"
      >
        <template v-if="isSuperAdmin"
        >
          <a-menu-item key="overview">
            <router-link to="/team/overview">
              <icon-dashboard/>
              平台总览
            </router-link>
          </a-menu-item>
          <a-menu-item key="userManager">
            <router-link to="/team/userManager">
              <icon-user/>
              用户管理
            </router-link>
          </a-menu-item>
          <a-menu-item key="roleManager">
            <router-link to="/team/roleManager">
              <icon-lock/>
              权限模板
            </router-link>
          </a-menu-item>
          <a-menu-item key="operationLog">
            <router-link to="/team/operationLog">
              <icon-history/>
              操作日志
            </router-link>
          </a-menu-item>
          <a-menu-item key="aiConfig">
            <router-link to="/team/aiConfig">
              <icon-robot/>
              AI 配置
            </router-link>
          </a-menu-item>
          <a-menu-item key="loginLog">
            <router-link to="/team/loginLog">
              <icon-safe/>
              登录日志
            </router-link>
          </a-menu-item>
          <a-menu-item key="divider" disabled aria-hidden="true" style="cursor: default; opacity: 0.3; font-size: 12px; padding: 0 24px; border-top: 1px solid var(--color-border-2); margin: 8px 0;" />
          <a-sub-menu key="personal">
            <template #title>
              <icon-user/>
              个人中心
            </template>
            <a-menu-item key="userSetting">
              <router-link to="/team/userSetting">个人设置</router-link>
            </a-menu-item>
          </a-sub-menu>
        </template>
        <template v-else>
          <a-menu-item key="workspace">
            <router-link to="/team/workspace">
              <IconApps/>
              我的团队
            </router-link>
          </a-menu-item>
          <a-menu-item key="divider" disabled aria-hidden="true" style="cursor: default; opacity: 0.3; font-size: 12px; padding: 0 24px; border-top: 1px solid var(--color-border-2); margin: 8px 0;" />
          <a-sub-menu key="personal">
            <template #title>
              <icon-user/>
              个人中心
            </template>
            <a-menu-item key="userSetting">
              <router-link to="/team/userSetting">个人设置</router-link>
            </a-menu-item>
          </a-sub-menu>
        </template>
      </a-menu>
    </a-layout-sider>
    <a-layout-content>
      <div style="padding-top: 20px">
        <router-view/>
      </div>
    </a-layout-content>
  </a-layout>
</template>

<script setup lang="ts">

import {computed, ref, watch} from 'vue';
import {Message, Modal} from '@arco-design/web-vue';
import {useDark, useFullscreen, useToggle} from '@vueuse/core';
import {useAppStore, useUserStore} from '@/store';
import {usePermissionStore} from '@/store/modules/permission';
import {LOCALE_OPTIONS} from '@/locale';
import useLocale from '@/hooks/locale';
import useUser from '@/hooks/user';
import {useRoute} from "vue-router";

const appStore = useAppStore();
const userStore = useUserStore();
const permissionStore = usePermissionStore();
const {logout} = useUser();
const {changeLocale, currentLocale} = useLocale();
const {isFullscreen, toggle: toggleFullScreen} = useFullscreen();
const locales = [...LOCALE_OPTIONS];
const avatar = computed(() => {
  return userStore.avatar;
});
const displayName = computed(() => userStore.nickname || userStore.username || '未知用户');
const theme = computed(() => {
  return appStore.theme;
});
const isSuperAdmin = computed(() => userStore.role === 'super_admin');
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
const handleLogout = () => {
  Modal.confirm({
    title: '退出登录',
    content: '确定要退出当前账号吗？',
    okText: '退出',
    cancelText: '取消',
    onOk: () => logout(),
  });
};
const selectedKey = ref('workspace')

const route = useRoute();
watch(() => route.path, (newPath) => {
  // 工作台内部 Tab 页也保持高亮工作台
  if (newPath.includes('workspace')) selectedKey.value = 'workspace'
  else if (newPath.includes('overview')) selectedKey.value = 'overview'
  else if (newPath.includes('userManager')) selectedKey.value = 'userManager'
  else if (newPath.includes('roleManager')) selectedKey.value = 'roleManager'
  else if (newPath.includes('operationLog')) selectedKey.value = 'operationLog'
  else if (newPath.includes('aiConfig')) selectedKey.value = 'aiConfig'
  else if (newPath.includes('loginLog')) selectedKey.value = 'loginLog'
  else if (newPath.includes('/team/userSetting')) selectedKey.value = 'userSetting'
}, {immediate: true})


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
  padding-left: 20px;
}

.center-side {
  flex: 1;
}

.right-side {
  display: flex;
  padding-right: 20px;
  list-style: none;

  :deep(.locale-select) {
    border-radius: 20px;
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
}
</style>

<style lang="less">
.message-popover {
  .arco-popover-content {
    margin-top: 0;
  }
}
</style>
