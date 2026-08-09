<template>
  <div class="plan-page">
    <Breadcrumb :items="['menu.testRun', 'menu.testRun.planList']"/>
    <a-card class="plan-card" :bordered="false">
      <template #title>
        <div class="plan-card-header">
          <span class="plan-card-title">{{ $t('menu.plan.planList') }}</span>
          <a-input-search
              v-model="searchKeyword"
              placeholder="输入任务名称搜索"
              allow-clear
              style="width: 240px"
          />
        </div>
      </template>
      <template #extra>
        <a-button v-permission="'auto:plan:create'" type="primary" @click="openAddPlan">
          <template #icon><icon-plus /></template>
          新建任务
        </a-button>
      </template>

      <!-- 统计卡片 -->
      <a-row :gutter="16" class="stat-row">
        <a-col :span="6">
          <a-card class="stat-card" :bordered="false">
            <div class="stat-value">{{ planList.length }}</div>
            <div class="stat-label">总任务</div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card stat-normal" :bordered="false">
            <div class="stat-value">{{ normalCount }}</div>
            <div class="stat-label">普通任务</div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card stat-timing" :bordered="false">
            <div class="stat-value">{{ timingCount }}</div>
            <div class="stat-label">定时任务</div>
          </a-card>
        </a-col>
        <a-col :span="6">
          <a-card class="stat-card stat-active" :bordered="false">
            <div class="stat-value">{{ activeCount }}</div>
            <div class="stat-label">启用中</div>
          </a-card>
        </a-col>
      </a-row>

      <a-tabs class="plan-tabs" :default-active-tab="1" type="rounded">
        <a-tab-pane key="1" :title="$t('menu.plan.planList.all')">
          <PlanCard :plan-list="filteredPlanList" @reloadPlanList="reloadPlanList"/>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 新建任务弹窗 -->
    <a-modal
        width="600px"
        :visible="addPlanVisible"
        title="新建任务"
        @cancel="addPlanVisible = false"
        @before-ok="handleSubmitPlan"
    >
      <a-form ref="addPlanFormRef" :model="addPlanForm" layout="vertical" :rules="rules">
        <a-form-item field="planName" label="任务名称">
          <a-input v-model="addPlanForm.planName" placeholder="请输入任务名称"/>
        </a-form-item>
        <a-form-item field="description" label="任务描述">
          <a-textarea v-model="addPlanForm.description" placeholder="请输入任务描述"/>
        </a-form-item>
        <a-form-item field="taskType" label="任务类型">
          <a-radio-group v-model="addPlanForm.taskType">
            <a-radio value="NORMAL">普通任务</a-radio>
            <a-radio value="TIMING">定时任务</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item field="planCategory" label="计划类型">
          <a-radio-group v-model="addPlanForm.planCategory">
            <a-radio value="UI">UI 自动化</a-radio>
            <a-radio value="API">API 自动化</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="addPlanForm.taskType === TaskType.TIMING" field="cronExpression" label="Cron 表达式">
          <CronConfig v-model="addPlanForm.cronExpression"/>
        </a-form-item>
        <a-form-item field="executionType" label="执行类型">
          <a-radio-group v-model="addPlanForm.executionType">
            <a-radio value="ORDER">顺序执行</a-radio>
            <a-radio value="PARALLEL">并发执行</a-radio>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'PlanList' };
</script>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue";
import {getAllPlan} from "@/api/MyApi/plan";
import PlanCard from "@/views/plan/components/plan-card.vue";
import {useProjectStore} from "@/store";
import {Message} from "@arco-design/web-vue";
import {add} from "@/api/MyApi/plan";
import {Plan} from "@/types/domain/Plan";
import CronConfig from "@/views/plan/components/CronConfig.vue";
import {TaskType} from "@/types/enum/task/TaskType";
import {PlanCategory} from "@/types/enum/plan/PlanCategory";

const planList = ref<Plan[]>([]);
const searchKeyword = ref('');

const filteredPlanList = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return planList.value;
  return planList.value.filter((p) => (p.planName || '').toLowerCase().includes(kw));
});

const projectStore = useProjectStore();

const reloadPlanList = async () => {
  const result = await getAllPlan(projectStore.getProjectId);
  planList.value = result.data || [];
}

const normalCount = computed(() => planList.value.filter((p) => p.taskType === TaskType.NORMAL).length);
const timingCount = computed(() => planList.value.filter((p) => p.taskType === TaskType.TIMING).length);
const activeCount = computed(() => planList.value.filter((p) => p.isActive === 1).length);

onMounted(async () => {
  await reloadPlanList();
})

watch(
    () => projectStore.getProjectId,
    (newProjectId) => {
      if (newProjectId) {
        reloadPlanList();
      }
    },
    {immediate: true}
);

// 新建任务
const addPlanVisible = ref(false);
const addPlanForm = ref<Plan>(new Plan());
const addPlanFormRef = ref<any>(null);

const openAddPlan = () => {
  addPlanFormRef.value?.clearValidate?.();
  addPlanForm.value = new Plan();
  addPlanForm.value.planCategory = PlanCategory.UI;
  addPlanVisible.value = true;
}

const handleSubmitPlan = async (done: (closed: boolean) => void) => {
  const error = await addPlanFormRef.value?.validate();
  if (error) {
    done(false);
    return;
  }

  addPlanForm.value.projectId = projectStore.getProjectId;
  try {
    const result = await add(addPlanForm.value);
    if (result.data) {
      Message.success({ content: '添加成功', duration: 1000 });
      addPlanVisible.value = false;
      await reloadPlanList();
      done(true);
    } else {
      Message.error({ content: '添加失败', duration: 1000 });
      done(false);
    }
  } catch (e) {
    Message.error({ content: '添加失败', duration: 1000 });
    done(false);
  }
}

const rules = {
  planName: [{ required: true, message: '任务名称不能为空' }],
  description: [{ max: 100, message: '任务描述不能超过100个字符' }],
  taskType: [{ required: true, message: '任务类型不能为空' }],
  planCategory: [{ required: true, message: '计划类型不能为空' }],
  executionType: [{ required: true, message: '任务执行类型不能为空' }],
}
</script>

<style scoped lang="less">
.plan-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.plan-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;

  :deep(.arco-card-body) {
    flex: 1;
    height: 100%;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
}

.plan-card-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.plan-card-title {
  font-size: 16px;
  font-weight: 600;
}

.stat-row {
  margin-bottom: 16px;
}

.stat-card {
  text-align: center;
  border-radius: 8px;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4e7ed 100%);

  .stat-value {
    font-size: 28px;
    font-weight: 700;
    color: var(--color-text-1);
    line-height: 1.2;
  }

  .stat-label {
    margin-top: 4px;
    font-size: 13px;
    color: var(--color-text-2);
  }

  &.stat-normal {
    background: linear-gradient(135deg, #e8ffea 0%, #c9f7d6 100%);

    .stat-value {
      color: #00b42a;
    }
  }

  &.stat-timing {
    background: linear-gradient(135deg, #fff7e8 0%, #ffe4ba 100%);

    .stat-value {
      color: #ff7d00;
    }
  }

  &.stat-active {
    background: linear-gradient(135deg, #e8f3ff 0%, #cbe3ff 100%);

    .stat-value {
      color: #165dff;
    }
  }
}

.plan-tabs {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.plan-tabs :deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--color-text-4) transparent;
}

:deep(.arco-list-content) {
  overflow-x: hidden;
}

:deep(.arco-card-meta-title) {
  font-size: 14px;
}

:deep(.arco-list-col) {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: space-between;
}

:deep(.arco-list-item) {
  width: 33%;
}

:deep(.block-title) {
  margin: 0 0 12px 0;
  font-size: 14px;
}

:deep(.list-wrap) {
  .list-row {
    align-items: stretch;

    .list-col {
      margin-bottom: 16px;
    }
  }
}
</style>