<template>
  <div class="project-member-page">
    <ProjectMemberPanel
      v-if="projectStore.projectId"
      :key="projectStore.projectId"
      :project-id="projectStore.projectId"
      :team-id="currentProject?.teamId"
    />
    <a-empty v-else style="margin-top: 120px" description="请先选择一个项目">
      <template #image>
        <icon-apps style="font-size: 48px; color: var(--color-text-4)" />
      </template>
      <a-button type="primary" @click="router.push({ name: 'TeamWorkspace' })">
        去选择项目
      </a-button>
    </a-empty>
  </div>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'ProjectMemberIndex' };
</script>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useProjectStore } from '@/store';
import { getProjectById } from '@/api/MyApi/project';
import ProjectMemberPanel from '@/views/project/components/ProjectMemberPanel.vue';

const router = useRouter();
const projectStore = useProjectStore();
const currentProject = ref<{ teamId?: string } | null>(null);

const loadProject = async () => {
  if (!projectStore.projectId) {
    currentProject.value = null;
    return;
  }
  try {
    const res: any = await getProjectById(String(projectStore.projectId));
    currentProject.value = res?.data || null;
  } catch (e) {
    currentProject.value = null;
  }
};

watch(
  () => projectStore.projectId,
  () => {
    loadProject();
  },
  { immediate: true }
);
</script>

<style scoped lang="less">
.project-member-page {
  padding: 20px;
}
</style>
