<template>
  <div>
    <a-drawer
        :visible="visible"
        :width="900"
        @cancel="handleCancel"
        :footer="false"
        unmountOnClose
    >
      <template #title>
        <a-space align="center">
          <a-tag color="arcoblue">{{ stepTypeLabel }}</a-tag>
          <span>{{ stepName }}</span>
          <a-tag v-if="status === 'SUCCESS'" color="green">{{ $t('scene.result.success') }}</a-tag>
          <a-tag v-else-if="status === 'FAILURE'" color="red">{{ $t('scene.result.failure') }}</a-tag>
          <a-tag v-else color="gray">{{ $t('scene.result.skipped') }}</a-tag>
          <a-tag v-if="hasChildren" color="orange">{{ $t('scene.apiResult.childTimes', { count: childrenCount }) }}</a-tag>
        </a-space>
      </template>

      <!-- 有子步骤结果时（循环/条件步骤）：用 tab 展示每一次执行，与 UI 场景一致 -->
      <div v-if="hasChildren" class="loop-result-tabs">
        <a-tabs type="card-gutter" :default-active-key="0" lazy-load>
          <a-tab-pane v-for="(child, index) in childrenResults" :key="index">
            <template #title>
              <span>{{ $t('scene.apiResult.nthTime', { index: Number(index) + 1 }) }}</span>
              <a-tag v-if="child.result?.status === 'SUCCESS'" color="green" size="small" style="margin-left: 4px">{{ $t('scene.result.success') }}</a-tag>
              <a-tag v-else-if="child.result?.status === 'FAILURE'" color="red" size="small" style="margin-left: 4px">{{ $t('scene.result.failure') }}</a-tag>
              <a-tag v-else color="gray" size="small" style="margin-left: 4px">{{ $t('scene.result.skipped') }}</a-tag>
            </template>
            <a-space align="center" style="margin-bottom: 8px">
              <span>{{ child.step?.stepName || $t('scene.node.unnamedStep') }}</span>
              <a-tag v-if="child.result?.response?.statusCode" size="small"
                     :color="child.result.response.statusCode >= 200 && child.result.response.statusCode < 300 ? 'green' : 'red'">
                {{ child.result.response.statusCode }}
              </a-tag>
              <a-tag v-if="child.result?.timeConsuming != null" color="arcoblue" size="small">
                {{ child.result.timeConsuming }}ms
              </a-tag>
              <a-typography-text v-if="child.result?.errorMessage" type="danger" style="font-size: 12px">
                {{ child.result.errorMessage }}
              </a-typography-text>
            </a-space>
            <div class="api-result-host">
              <ApiDebugResult
                  v-if="child.result?.response"
                  :debug-result="child.result.response"
                  :api-type="apiTypeOf(child.step, child.result.response)"
              />
              <a-empty v-else :description="$t('scene.apiResult.noResponse')"/>
            </div>
          </a-tab-pane>
        </a-tabs>
      </div>

      <!-- 无子步骤时（普通 API 请求）：直接展示结果 -->
      <div v-else>
        <!-- 错误信息（如条件评估异常、请求发送失败等无响应数据的失败） -->
        <a-alert v-if="errorMessage" type="error" show-icon style="margin-bottom: 12px">
          <template #title>{{ $t('scene.apiResult.execFailed') }}</template>
          {{ errorMessage }}
        </a-alert>
        <!-- 条件/断言评估结果（IF/断言步骤：展示每个条件的求值明细） -->
        <div v-if="assertResultList.length > 0" class="condition-results">
          <div class="condition-results-title">{{ $t('scene.apiResult.condResults') }}</div>
          <div v-for="item in assertResultList" :key="item.index" class="condition-item">
            <icon-check-circle-fill v-if="item.success" class="cond-icon cond-success"/>
            <icon-close-circle-fill v-else class="cond-icon cond-failure"/>
            <span class="cond-tip">{{ item.assertTip || item.assertType || '—' }}</span>
          </div>
        </div>
        <!-- WHILE 每轮循环的条件评估结果 -->
        <div v-if="whileAssertResultList.length > 0" class="condition-results">
          <div class="condition-results-title">{{ $t('scene.apiResult.loopCondResults') }}</div>
          <div v-for="item in whileAssertResultList" :key="item.index" class="condition-item">
            <a-tag size="small" color="arcoblue">{{ $t('scene.apiResult.nthRound', { index: item.cycle }) }}</a-tag>
            <icon-check-circle-fill v-if="item.success" class="cond-icon cond-success"/>
            <icon-close-circle-fill v-else class="cond-icon cond-failure"/>
            <span class="cond-tip">{{ item.assertTip || item.assertType || '—' }}</span>
          </div>
        </div>
        <div v-if="response" class="api-result-host">
          <ApiDebugResult
              :debug-result="response"
              :api-type="apiTypeOf(normalizedStepResult?.step, response)"
          />
        </div>
        <a-empty v-else-if="!errorMessage && assertResultList.length === 0 && whileAssertResultList.length === 0"
                 :description="$t('scene.debugResult.empty')"/>
      </div>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import {useI18n} from 'vue-i18n';
import ApiDebugResult from "@/views/apiManager/component/ApiDebugResult.vue";

interface Props {
  visible: boolean;
  stepResult: any;
}

const props = defineProps<Props>();
const { t } = useI18n();
const emit = defineEmits(['update:visible']);

const visible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
});

// 统一归一化：result.apiResponse -> result.response
const normalizeResponse = (step: any) => {
  if (step?.result?.apiResponse && !step.result.response) {
    return {...step, result: {...step.result, response: step.result.apiResponse}};
  }
  return step;
};

const normalizedStepResult = computed(() => normalizeResponse(props.stepResult));

const stepName = computed(() => normalizedStepResult.value?.step?.stepName || '');
const stepType = computed(() => normalizedStepResult.value?.step?.stepType || 'API_REQUEST');
const status = computed(() => normalizedStepResult.value?.result?.status || '');
const response = computed(() => normalizedStepResult.value?.result?.response || null);
const errorMessage = computed(() => normalizedStepResult.value?.result?.errorMessage || '');

const stepTypeLabel = computed(() => {
  const labels: Record<string, string> = {
    'API_REQUEST': t('scene.action.httpRequest'),
    'WAIT': t('scene.action.wait'),
    'IF': t('scene.stepType.conditionIf'),
    'FOR': t('scene.stepType.forLoop'),
    'WHILE': t('scene.stepType.whileLoop'),
    'SQL': t('scene.action.sqlQuery'),
    'ASSERT': t('scene.action.assert'),
    'EXTRACT': t('scene.action.extract')
  };
  return labels[stepType.value] || stepType.value;
});

// 子步骤结果：优先使用 childrenResults，兼容 iterations 对象
const childrenResults = computed(() => {
  const rawChildren = normalizedStepResult.value?.childrenResults || [];
  if (rawChildren.length > 0) {
    return rawChildren.map(normalizeResponse);
  }
  const iterations = normalizedStepResult.value?.iterations;
  if (iterations && Object.keys(iterations).length > 0) {
    return Object.entries(iterations).map(([key, value]: [string, any]) =>
        normalizeResponse({
          step: {...normalizedStepResult.value.step, stepName: `${normalizedStepResult.value.step.stepName} - ${t('scene.apiResult.nthTime', { index: key })}`},
          result: value
        })
    );
  }
  return [];
});
const hasChildren = computed(() => childrenResults.value.length > 0);
const childrenCount = computed(() => childrenResults.value.length);

/**
 * 判断结果是否应按 SQL 面板渲染（执行结果表格、SQL 错误展示），
 * 与 HTTP 接口的结果面板区分开：步骤类型为 SQL，或响应标记为 SQL 执行。
 */
const apiTypeOf = (step: any, resp: any): string | undefined => {
  if (step?.stepType === 'SQL' || resp?.requestMethod === 'SQL') return 'SQL';
  return undefined;
};

const handleCancel = () => {
  visible.value = false;
};

// IF/断言步骤的条件评估结果（后端是 Map<序号, AssertResult>，序列化为对象）
const assertResultList = computed(() => {
  const ar = normalizedStepResult.value?.result?.assertResults;
  if (!ar) return [];
  if (Array.isArray(ar)) return ar;
  return Object.entries(ar).map(([k, v]: [string, any]) => ({index: k, ...(v || {})}));
});

// WHILE 步骤每轮循环的条件评估结果（Map<轮次, Map<序号, AssertResult>>，拍平展示）
const whileAssertResultList = computed(() => {
  const war = normalizedStepResult.value?.result?.whileAssertResults;
  if (!war) return [];
  const list: any[] = [];
  for (const [cycle, results] of Object.entries(war as Record<string, any>)) {
    for (const [idx, v] of Object.entries(results || {})) {
      list.push({index: `${cycle}-${idx}`, cycle, ...(v as any)});
    }
  }
  return list;
});
</script>

<style scoped>
/* 给结果区一个确定高度：ApiDebugResult 内部是 height:100% 的 flex 布局，
   其中实时响应用 Monaco（需要确定像素高度才能渲染），抽屉里父级无显式高度会塌成 0，
   导致编辑器不可见。这里用视口高度兜底。 */
.api-result-host {
  height: 70vh;
  min-height: 360px;
}

/* 条件/断言评估结果列表 */
.condition-results {
  margin-bottom: 12px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
}

.condition-results-title {
  font-weight: 500;
  margin-bottom: 8px;
  color: var(--color-text-1);
}

.condition-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 0;
  font-size: 13px;
  color: var(--color-text-2);
}

.cond-icon {
  flex-shrink: 0;
  font-size: 14px;
}

.cond-success {
  color: rgb(var(--green-6));
}

.cond-failure {
  color: rgb(var(--red-6));
}

.cond-tip {
  word-break: break-all;
}
</style>
