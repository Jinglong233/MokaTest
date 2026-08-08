<template>
  <a-spin :loading="loading" style="width: 100%">
    <LoadError v-if="loadError" @retry="loadProjects" />
    <template v-else>
      <a-row :gutter="16">
      <a-col
          v-for="(project, index) in projectList"
          :key="project.id || index"
          :xs="12"
          :sm="12"
          :md="12"
          :lg="12"
          :xl="8"
          :xxl="8"
          class="my-project-item"
      >
        <a-card hoverable @click="goProject(project)">
          <a-space direction="vertical" :size="4">
            <a-typography-text bold>{{ project.projectName }}</a-typography-text>
            <a-typography-text type="secondary" class="project-desc">
              {{ project.description || '暂无描述' }}
            </a-typography-text>
            <a-typography-text type="secondary" style="font-size: 12px">
              {{ formatTime(project.createTime || project.createdAt) }}
            </a-typography-text>
          </a-space>
        </a-card>
      </a-col>
      </a-row>
      <a-empty v-if="!loading && projectList.length === 0" description="暂无项目"/>
    </template>
  </a-spin>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {allProject} from "@/api/MyApi/project";
import useLoadState from "@/hooks/useLoadState";
import LoadError from "@/components/load-error/index.vue";
import {useProjectStore} from "@/store";

const router = useRouter();
const projectStore = useProjectStore();
const {loading, loadError, track} = useLoadState();
const projectList = ref<any[]>([]);

const formatTime = (t?: string) => {
  if (!t) return '';
  // 后端返回 'yyyy-MM-dd HH:mm:ss' 或 ISO，统一只展示日期部分
  return String(t).replace('T', ' ').slice(0, 10);
};

const goProject = (project: any) => {
  if (!project?.id) return;
  projectStore.setProject(project.id, project.projectName || '');
  router.push({name: 'ProjectInfo'});
};

const loadProjects = async () => {
  const res: any = await track(allProject());
  projectList.value = res?.data || [];
};

onMounted(loadProjects);
</script>

<style scoped lang="less">
.my-project-item {
  margin-bottom: 16px;

  :deep(.arco-card) {
    cursor: pointer;
    height: 100%;
  }
}

.project-desc {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}
</style>
