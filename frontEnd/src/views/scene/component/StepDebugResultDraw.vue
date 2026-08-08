<template>
  <a-drawer v-if="visible" :width="drawerWidth" :visible="visible" @cancel="handleCancel" :footer="false" unmountOnClose>
    <template #title>
      <a-space align="center">
        <a-badge :count="stepResult.step.orderIndex" :dotStyle="{ background: '#E5E6EB', color: '#86909C' }"/>
        {{ getStepTypeChinese(stepResult.step.stepType) }}
      </a-space>
    </template>
    <div>
      <!-- 循环步骤：按每次执行展示 -->
      <div v-if="hasIterations">
        <a-tabs>
          <a-tab-pane v-for="(value,key) in stepResult.iterations" :key="key" :title="`第${key}次循环`">
            <!-- API 请求步骤：复用 API 调试结果组件 -->
            <template v-if="isApiRequestStep">
              <ApiDebugResult v-if="getApiResponse(value)" :debug-result="getApiResponse(value)" :api-type="resultApiType"/>
              <a-empty v-else description="该次循环无响应数据"/>
            </template>
            <!-- 非 API 步骤：通用展示（基本信息 + 截图 + 断言/提取 tab） -->
            <CommonStepResult v-else :value="value" :step-type="stepResult.step.stepType"/>
          </a-tab-pane>
        </a-tabs>
      </div>

      <!-- 非循环步骤 -->
      <div v-else>
        <!-- API 请求步骤：复用 API 调试结果组件，与 API 测试页/场景实时调试保持一致 -->
        <template v-if="isApiRequestStep">
          <ApiDebugResult v-if="apiResponse" :debug-result="apiResponse" :api-type="resultApiType"/>
          <a-empty v-else description="暂无调试结果"/>
        </template>
        <!-- 非 API 步骤：通用展示（基本信息 + 截图 + 断言/提取 tab） -->
        <CommonStepResult v-else :value="stepResult.result" :step-type="stepResult.step.stepType"/>
      </div>
    </div>
  </a-drawer>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from "vue";
import {getStepTypeChinese} from "@/types/enum/StepType";
import ApiDebugResult from "@/views/apiManager/component/ApiDebugResult.vue";
import CommonStepResult from "./CommonStepResult.vue";

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  stepResult: {
    type: Object,
    default: () => ({}),
    required: true
  }
});

const emit = defineEmits(['update:visible']);

const stepResult = ref<any>({...props.stepResult});

onMounted(() => {
  try {
    stepResult.value = typeof props.stepResult === 'string' ? JSON.parse(props.stepResult) : props.stepResult;
  } catch (error) {
    console.error('JSON 解析错误:', error);
    stepResult.value = null;
  }
});

const handleCancel = () => {
  emit('update:visible', false);
}

const isApiRequestStep = computed(() => ['API_REQUEST', 'SQL'].includes(stepResult.value?.step?.stepType));
const hasIterations = computed(() => stepResult.value?.iterations && Object.keys(stepResult.value.iterations).length > 0);

// API/SQL 步骤保持较宽抽屉，其他步骤与编辑抽屉宽度一致（520）
const drawerWidth = computed(() => isApiRequestStep.value ? 900 : 520);

// SQL 步骤用 SQL 结果面板渲染（执行结果表格、SQL 错误展示），与 HTTP 面板区分
const resultApiType = computed(() => stepResult.value?.step?.stepType === 'SQL' ? 'SQL' : undefined);

// API 请求步骤响应详情（优先 apiResponse，兼容 response）
const apiResponse = computed(() => stepResult.value.result?.apiResponse || stepResult.value.result?.response);

const getApiResponse = (iteration: any) => {
  return iteration?.apiResponse || iteration?.response;
};

watch(() => props.visible, (val) => {
  if (!val) {
    handleCancel()
  }
})

watch(() => props.stepResult, (val) => {
  try {
    stepResult.value = typeof props.stepResult === 'string' ? JSON.parse(props.stepResult) : props.stepResult;
  } catch (error) {
    console.error('JSON 解析错误:', error);
    stepResult.value = null;
  }
}, {immediate: true});
</script>

<style scoped>
:deep(.arco-drawer-body) {
  overflow-y: auto;
}
</style>
