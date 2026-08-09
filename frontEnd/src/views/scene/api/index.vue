<template>
  <SceneCore sceneCategory="API" :breadcrumbItems="['menu.interfaceTest', 'menu.interfaceTest.sceneList']"/>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'ApiSceneList' };
</script>

<script setup lang="ts">
import SceneCore from '../index.vue';
</script>
