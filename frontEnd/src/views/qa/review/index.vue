<template>
  <div class="review-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.review']"/>
    <ReviewList />
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'qaReview' };
</script>

<script setup lang="ts">
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import ReviewList from './ReviewList.vue';
import {useProjectStore} from '@/store';

const projectStore = useProjectStore();
</script>

<style scoped lang="less">
.review-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}
</style>
