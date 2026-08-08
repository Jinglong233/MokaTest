<template>
  <div class="card-wrap">
    <a-card v-if="loading" :bordered="false" hoverable>
      <slot name="skeleton"></slot>
    </a-card>
    <a-card v-else :bordered="false" hoverable class="plan-item-card">
      <div class="plan-card-header">
        <a-avatar v-if="icon" :size="32" class="plan-icon">
          <icon-filter/>
        </a-avatar>
        <div class="plan-title-wrap">
          <a-typography-text class="plan-title" :ellipsis="{ rows: 1, showTooltip: true }">
            {{ title }}
          </a-typography-text>
          <a-tag :color="taskTypeColor" size="small">
            <template v-if="taskType === TaskType.TIMING" #icon>
              <icon-clock-circle/>
            </template>
            {{ taskTypeDesc }}
          </a-tag>
        </div>
        <a-switch
            v-if="taskType === TaskType.TIMING"
            v-model="isActive"
            size="small"
            :checked-value="1"
            :unchecked-value="0"
            checked-color="#4e8752"
            unchecked-color="#ff4109"
            @change="handleActivePlan"
        />
      </div>

      <div class="plan-card-body">
        <div v-if="description" class="plan-desc">{{ description }}</div>
        <div class="plan-meta">
          <div class="meta-item">
            <span class="meta-label">执行类型</span>
            <span class="meta-value">{{ executionTypeDesc || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">状态</span>
            <span class="meta-value">{{ statusDesc || '-' }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">{{ taskType === TaskType.TIMING ? '执行频率' : '更新时间' }}</span>
            <span class="meta-value">{{ frequencyText }}</span>
          </div>
        </div>
      </div>

      <template #actions>
        <div class="plan-card-actions">
          <a-space>
            <a-button v-permission="'auto:plan:update'" size="small" @click="editPlan">
              <template #icon><icon-edit /></template>
              编辑
            </a-button>
            <a-button v-permission="'auto:plan:delete'" size="small" status="danger" @click.stop="handleDeletePlan">
              <template #icon><icon-delete /></template>
              删除
            </a-button>
          </a-space>
        </div>
      </template>
    </a-card>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from 'vue';
import {useRouter} from "vue-router";
import {Message, Modal} from "@arco-design/web-vue";
import {deletePlan} from "@/api/MyApi/plan";
import {activeTask, cancelTask} from "@/api/MyApi/task";
import {TaskType} from "@/types/enum/task/TaskType";
import {parseCronToChinese} from '@/utils/cronParser';

const props = defineProps({
  taskType: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  title: { type: String, default: '' },
  updateTime: { type: String, default: '' },
  icon: { type: String, default: '' },
  planId: { type: null, default: null },
  planName: { type: String, default: '' },
  isActive: { type: Number, default: 0 },
  expires: { type: Boolean, default: false },
  description: { type: String, default: '' },
  taskTypeDesc: { type: String, default: '' },
  executionTypeDesc: { type: String, default: '' },
  cronExpression: { type: String, default: '' },
  statusDesc: { type: String, default: '' },
});

const router = useRouter();
const isActive = ref(props.isActive);

const emits = defineEmits(['reloadPlanList']);

const taskTypeColor = computed(() => {
  return props.taskType === TaskType.TIMING ? 'arcoblue' : 'orange';
});

const frequencyText = computed(() => {
  if (props.taskType === TaskType.TIMING) {
    return props.cronExpression ? parseCronToChinese(props.cronExpression) : '未配置';
  }
  return props.updateTime || '-';
});

const handleDeletePlan = () => {
  Modal.warning({
    title: "删除确认",
    content: () => `确认删除任务【${props.planName}】吗？`,
    cancelText: "取消",
    okText: "确认",
    hideCancel: false,
    onOk: async () => {
      const res = await deletePlan(props.planId);
      if (res.data === true) {
        Message.success("删除成功");
        emits('reloadPlanList');
      } else {
        Message.error("删除失败");
      }
    },
  });
};

const editPlan = () => {
  router.push({
    name: 'PlanDetail',
    params: { planId: props.planId },
  });
};

const handleActivePlan = async (value: boolean) => {
  const api = value ? activeTask : cancelTask;
  const successMsg = value ? '任务已激活' : '任务已停止';
  try {
    const result = await api(props.planId);
    if (result.data) {
      Message.success({ content: successMsg, duration: 1000 });
    } else {
      Message.error({ content: result.message || '操作失败', duration: 1000 });
    }
    emits('reloadPlanList');
  } catch (e) {
    Message.error({ content: '操作失败', duration: 1000 });
  }
};
</script>

<style scoped lang="less">
.card-wrap {
  height: 100%;
  transition: all 0.3s;

  &:hover {
    transform: translateY(-4px);
  }
}

.plan-item-card {
  height: 100%;
  border: 1px solid var(--color-neutral-3);
  border-radius: 8px;

  :deep(.arco-card-body) {
    padding: 16px;
  }
}

.plan-card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.plan-icon {
  flex-shrink: 0;
  background: linear-gradient(135deg, #626aea 0%, #8b5cf6 100%);
  color: #fff;
}

.plan-title-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.plan-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.plan-desc {
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.5;
  margin-bottom: 12px;
  min-height: 20px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.plan-meta {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 12px;
}

.meta-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.meta-label {
  color: var(--color-text-3);
}

.meta-value {
  color: var(--color-text-1);
  font-weight: 500;
}

.plan-card-actions {
  display: flex;
  justify-content: flex-end;

  :deep(.arco-btn) {
    padding: 0 12px;
  }
}
</style>
