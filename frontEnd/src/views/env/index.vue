<template>
  <div class="env-config-page">
    <EnvConfig v-if="localTeamId" :team-id="localTeamId" inline />
    <a-empty v-else description="请先选择团队" />
  </div>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'InterfaceEnvConfig' };
</script>

<script setup lang="ts">
  import EnvConfig from '@/components/env-config/index.vue';
  import useTeamStore from '@/store/modules/team';

  const teamStore = useTeamStore();
  // 在组件实例创建时固定当前团队 ID，避免多工作区标签页切换时互相串数据
  const localTeamId = teamStore.getTeamId;
</script>

<style scoped lang="scss">
  .env-config-page {
    width: 100%;
    height: 100%;
    padding: 12px;
    overflow: hidden;
    box-sizing: border-box;
  }
</style>
