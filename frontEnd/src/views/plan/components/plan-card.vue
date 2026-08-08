<template>
  <div class="list-wrap">
    <a-row class="list-row" :gutter="[16, 16]">
      <a-col
          v-for="item in planList"
          :key="item.id"
          class="list-col"
          :xs="12"
          :sm="12"
          :md="12"
          :lg="8"
          :xl="6"
          :xxl="6"
      >
        <CardWrap
            :loading="loading"
            :title="item.planName"
            :update-time="item.updatedAt"
            :task-type="item.taskType"
            :is-active="item.isActive"
            :icon="item.icon"
            :plan-id="item.id"
            :plan-name="item.planName"
            :description="item.description"
            :task-type-desc="getTaskTypeDescription(item.taskType)"
            :execution-type-desc="getSceneExecuteTypeDescription(item.executionType)"
            :cron-expression="item.cronExpression"
            :status-desc="getTaskExecuteStatusDescription(item.status)"
            @reloadPlanList="emits('reloadPlanList')"
        />
      </a-col>
    </a-row>
    <a-empty v-if="!loading && planList.length === 0" description="暂无任务，点击右上角新建任务" />
  </div>
</template>

<script lang="ts" setup>
import CardWrap from './card-wrap.vue';
import {Plan} from "@/types/domain/Plan";
import {getTaskTypeDescription, TaskType} from "@/types/enum/task/TaskType";
import {getSceneExecuteTypeDescription} from "@/types/enum/task/SceneExecuteType";
import {getTaskExecuteStatusDescription} from "@/types/enum/task/TaskExecuteStatus";

const props = defineProps<{
  planList: Plan[];
  loading?: boolean;
}>();

const emits = defineEmits<{
  (e: 'reloadPlanList'): void;
}>()
</script>

<style scoped lang="less">
.list-wrap {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-row {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--color-text-4) transparent;
}

.list-col {
  margin-bottom: 16px;
}
</style>
