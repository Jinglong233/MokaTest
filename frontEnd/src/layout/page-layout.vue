<template>
  <router-view v-slot="{ Component, route }">
    <transition name="fade" appear>
      <component
        :is="Component"
        v-if="route.meta.ignoreCache"
        :key="route.fullPath"
      />
      <keep-alive v-else :include="cacheList">
        <component :is="Component" :key="cacheKey" />
      </keep-alive>
    </transition>
  </router-view>
</template>

<script lang="ts" setup>
  import { computed, onErrorCaptured, watch } from 'vue';
  import { useRoute } from 'vue-router';
  import { useTabBarStore } from '@/store';
  import useWorkspaceStore from '@/store/modules/workspace';

  const tabBarStore = useTabBarStore();
  const route = useRoute();
  const workspaceStore = useWorkspaceStore();

  const cacheList = computed(() => tabBarStore.getCacheList);
  const cacheKey = computed(() => {
    const tabId = workspaceStore.activeTabId || '';
    return `${route.fullPath}-${tabId}`;
  });

  watch(
    () => route.name,
    (name) => {
      console.log('[PageLayout] route changed to:', name, 'fullPath:', route.fullPath);
    }
  );

  onErrorCaptured((err, instance, info) => {
    console.error('[PageLayout] Error captured:', err, info, instance);
    return false;
  });
</script>

<style scoped lang="less">
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
