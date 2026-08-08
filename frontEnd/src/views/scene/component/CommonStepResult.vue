<template>
  <div class="common-step-result">
    <!-- 基本信息 -->
    <a-descriptions :column="1">
      <a-descriptions-item label="耗时">
        <a-tag>{{ (value.timeConsuming || 0) / 1000 }} 秒</a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag v-if="value.status === StepExecutionType.SUCCESS" color="#00b42a">成功</a-tag>
        <a-tag v-else-if="value.status === StepExecutionType.FAILURE" color="#f53f3f">失败</a-tag>
        <a-tag v-else-if="value.status === StepExecutionType.SKIPPED" color="#86909c">跳过</a-tag>
        <a-tag v-else color="#86909c">未知</a-tag>
      </a-descriptions-item>
      <a-descriptions-item v-if="value.status === StepExecutionType.FAILURE" label="失败原因">
        <a-collapse>
          <a-collapse-item header="失败详情">
            <template
                v-if="value.errorMessage && value.errorMessage.includes('TIMEOUT_ERROR')"
                v-for="line in value.errorMessage.split('\n')"
                :key="line"
            >
              <a-typography-paragraph>{{ line }}</a-typography-paragraph>
            </template>
            <a-typography-text v-else>{{ value.errorMessage }}</a-typography-text>
          </a-collapse-item>
        </a-collapse>
      </a-descriptions-item>
    </a-descriptions>

    <!-- SCRIPT 步骤：脚本日志（UI 场景走 additionalInfo；API 场景成功时日志在 errorMessage） -->
    <template v-if="isScriptStep && scriptLogs">
      <div class="section-title" style="border-color: #722ed1">脚本日志</div>
      <pre class="script-log-view">{{ scriptLogs }}</pre>
    </template>

    <!-- 截图：仅当有时显示 -->
    <template v-if="value.screenshotPath">
      <div class="section-title" style="border-color: #4052ec">截图</div>
      <ScreenshotImage
          :file-id="value.screenshotPath"
          width="100%"
          height="100%"
          :image-style="{padding: '5px 0', border: '2px solid #e5e5e5', borderRadius: '4px'}"
      />
    </template>

    <!-- ASSERT / IF / SCRIPT 步骤：断言/判断结果直接展示 -->
    <template v-if="(isAssertStep || isIfStep || isScriptStep) && assertResultList.length > 0">
      <div class="section-title" style="border-color: #00b42a">
        {{ isIfStep ? '判断结果' : '断言结果' }}
        <a-space style="margin-left: 12px">
          <a-tag color="green">通过 {{ assertPassCount }}</a-tag>
          <a-tag color="red">失败 {{ assertFailCount }}</a-tag>
        </a-space>
      </div>
      <a-collapse>
        <a-collapse-item
            v-for="(assertDetail, idx) in assertResultList"
            :key="`assert-${idx}`"
            :header="`${isIfStep ? '判断' : '断言'} ${idx + 1}：${assertTypeMap[assertDetail.assertType] || assertDetail.assertType || ''}`"
        >
          <template #extra>
            <a-tag :color="assertDetail.success === true ? 'green' : 'red'">
              {{ assertDetail.success === true ? '通过' : '失败' }}
            </a-tag>
          </template>
          <a-descriptions :column="1" size="small">
            <a-descriptions-item label="断言类型">
              {{ assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '-' }}
            </a-descriptions-item>
            <a-descriptions-item v-if="assertDetail.assertRelationship" label="断言关系">
              {{ assertRelationshipMap[assertDetail.assertRelationship] || assertDetail.assertRelationship }}
            </a-descriptions-item>
            <a-descriptions-item v-if="assertDetail.assertTip" label="提示信息">
              {{ assertDetail.assertTip }}
            </a-descriptions-item>
            <a-descriptions-item v-if="assertDetail.actualValue != null" label="实际值">
              <a-typography-text copyable>{{ assertDetail.actualValue }}</a-typography-text>
            </a-descriptions-item>
            <a-descriptions-item v-if="assertDetail.source" label="来源">
              {{ sourceMap[assertDetail.source] || assertDetail.source }}
            </a-descriptions-item>
          </a-descriptions>
        </a-collapse-item>
      </a-collapse>
    </template>

    <!-- EXTRACT 步骤：提取结果直接展示 -->
    <template v-if="isExtractStep && extractResultList.length > 0">
      <div class="section-title" style="border-color: #165dff">提取结果</div>
      <a-collapse>
        <a-collapse-item
            v-for="(extractItem, idx) in extractResultList"
            :key="`extract-${idx}`"
            :header="`变量名：${extractItem.key}`"
        >
          <a-typography-text copyable>{{ extractItem.value }}</a-typography-text>
        </a-collapse-item>
      </a-collapse>
    </template>

    <!-- WHILE 步骤：循环判断结果直接展示 -->
    <template v-if="isWhileStep && hasWhileAssertResults">
      <div class="section-title" style="border-color: #ff7d00">循环判断结果</div>
      <a-collapse>
        <a-collapse-item
            v-for="(assertList, cycleIndex) in value.whileAssertResults"
            :key="`while-${cycleIndex}`"
            :header="`第${cycleIndex}次循环判断结果`"
        >
          <a-collapse>
            <a-collapse-item
                v-for="(assertDetail, assertIdx) in assertList"
                :key="`while-assert-${assertIdx}`"
                :header="`判断 ${Number(assertIdx) + 1}：${assertTypeMap[assertDetail.assertType] || assertDetail.assertType || ''}`"
            >
              <template #extra>
                <a-tag :color="assertDetail.success === true ? 'green' : 'red'">
                  {{ assertDetail.success === true ? '通过' : '失败' }}
                </a-tag>
              </template>
              <a-descriptions :column="1" size="small">
                <a-descriptions-item label="断言类型">
                  {{ assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '-' }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.assertRelationship" label="断言关系">
                  {{ assertRelationshipMap[assertDetail.assertRelationship] || assertDetail.assertRelationship }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.assertTip" label="提示信息">
                  {{ assertDetail.assertTip }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.actualValue != null" label="实际值">
                  <a-typography-text copyable>{{ assertDetail.actualValue }}</a-typography-text>
                </a-descriptions-item>
              </a-descriptions>
            </a-collapse-item>
          </a-collapse>
        </a-collapse-item>
      </a-collapse>
    </template>

    <!-- 普通步骤：断言 / 关联提取 tab -->
    <a-tabs v-if="showAssertTab || showExtractTab" class="result-tabs" type="line" size="small">
      <a-tab-pane v-if="showAssertTab" key="assert">
        <template #title>
          <a-badge
              :count="assertResultList.length"
              :offset="[10, -2]"
              :dot-style="{ background: '#E5E6EB', color: '#86909C' }"
          >
            断言
          </a-badge>
        </template>
        <template v-if="assertResultList.length > 0">
          <a-space class="assert-summary">
            <a-tag color="green">通过 {{ assertPassCount }}</a-tag>
            <a-tag color="red">失败 {{ assertFailCount }}</a-tag>
          </a-space>
          <a-collapse>
            <a-collapse-item
                v-for="(assertDetail, idx) in assertResultList"
                :key="`assert-${idx}`"
                :header="`断言 ${idx + 1}：${assertTypeMap[assertDetail.assertType] || assertDetail.assertType || ''}`"
            >
              <template #extra>
                <a-tag :color="assertDetail.success === true ? 'green' : 'red'">
                  {{ assertDetail.success === true ? '通过' : '失败' }}
                </a-tag>
              </template>
              <a-descriptions :column="1" size="small">
                <a-descriptions-item label="断言类型">
                  {{ assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '-' }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.assertRelationship" label="断言关系">
                  {{ assertRelationshipMap[assertDetail.assertRelationship] || assertDetail.assertRelationship }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.assertTip" label="提示信息">
                  {{ assertDetail.assertTip }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.actualValue != null" label="实际值">
                  <a-typography-text copyable>{{ assertDetail.actualValue }}</a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.source" label="来源">
                  {{ sourceMap[assertDetail.source] || assertDetail.source }}
                </a-descriptions-item>
              </a-descriptions>
            </a-collapse-item>
          </a-collapse>
        </template>
        <a-empty v-else description="该步骤未配置断言或断言结果为空"/>
      </a-tab-pane>
      <a-tab-pane v-if="showExtractTab" key="extract">
        <template #title>
          <a-badge
              :count="extractResultList.length"
              :offset="[10, -2]"
              :dot-style="{ background: '#E5E6EB', color: '#86909C' }"
          >
            关联提取
          </a-badge>
        </template>
        <template v-if="extractResultList.length > 0">
          <a-collapse>
            <a-collapse-item
                v-for="(extractItem, idx) in extractResultList"
                :key="`extract-${idx}`"
                :header="`变量名：${extractItem.key}`"
            >
              <a-typography-text copyable>{{ extractItem.value }}</a-typography-text>
            </a-collapse-item>
          </a-collapse>
        </template>
        <a-empty v-else description="该步骤未配置关联提取或提取结果为空"/>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {StepExecutionType} from "@/types/enum/StepExecutionType";
import ScreenshotImage from "./ScreenshotImage.vue";

interface Props {
  value: any;
  stepType: string;
}

const props = defineProps<Props>();

/**
 * 将可能为 Map/Object 的结果统一转成数组
 */
const toArray = (data: any): any[] => {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  if (typeof data === 'object') {
    return Object.values(data);
  }
  return [];
};

const assertResultList = computed(() => toArray(props.value?.assertResults));
const assertPassCount = computed(() => assertResultList.value.filter((r: any) => r.success === true).length);
const assertFailCount = computed(() => assertResultList.value.filter((r: any) => r.success !== true).length);

/**
 * 统一提取结果：后置提取（extractResults）和 EXTRACT 步骤（extractResultsList）
 */
const extractResultList = computed(() => {
  const results: { key: string; value: string }[] = [];
  const extractResults = props.value?.extractResults;
  const extractResultsList = props.value?.extractResultsList;
  if (extractResults) {
    Object.entries(extractResults).forEach(([key, value]) => {
      results.push({key, value: value === null || value === undefined ? '' : String(value)});
    });
  }
  if (extractResultsList) {
    Object.entries(extractResultsList).forEach(([key, value]) => {
      results.push({key, value: value === null || value === undefined ? '' : String(value)});
    });
  }
  return results;
});

const isAssertStep = computed(() => props.stepType === 'ASSERT');
const isIfStep = computed(() => props.stepType === 'IF');
const isScriptStep = computed(() => props.stepType === 'SCRIPT');
// 脚本日志：UI 场景写 additionalInfo；API 场景（ApiStepResult 无该字段）成功时日志放在 errorMessage
const scriptLogs = computed(() => {
  if (props.value?.additionalInfo) return props.value.additionalInfo;
  if (props.value?.status === StepExecutionType.SUCCESS && props.value?.errorMessage) {
    return props.value.errorMessage;
  }
  return null;
});
const isWhileStep = computed(() => props.stepType === 'WHILE');
const isExtractStep = computed(() => props.stepType === 'EXTRACT');

const hasWhileAssertResults = computed(() => {
  const results = props.value?.whileAssertResults;
  return results && Object.keys(results).length > 0;
});

// 断言 tab 显示条件：与 SettingTab 一致（IF / WHILE / ASSERT 不显示）
const showAssertTab = computed(() => !['IF', 'WHILE', 'ASSERT'].includes(props.stepType));
// 关联提取 tab 显示条件：与 SettingTab 一致（EXTRACT / IF 不显示）
const showExtractTab = computed(() => !['EXTRACT', 'IF'].includes(props.stepType));

/** 断言类型英文 → 中文映射 */
const assertTypeMap: Record<string, string> = {
  HEADER: '响应头',
  BODY: '响应体',
  STATUS_CODE: '状态码',
  RESPONSE_TIME: '响应时间',
  CUSTOM: '自定义',
  SCHEMA: '结构校验',
  TEXT: '文本',
  ELEMENT: '元素',
  URL: 'URL',
  TITLE: '标题',
};

/** 断言关系英文 → 中文映射 */
const assertRelationshipMap: Record<string, string> = {
  EQUALS: '等于',
  NOT_EQUALS: '不等于',
  CONTAINS: '包含',
  NOT_CONTAINS: '不包含',
  GT: '大于',
  LT: '小于',
  GE: '大于等于',
  LE: '小于等于',
  REGULAR: '正则匹配',
  EXISTS: '存在',
  NOT_EXISTS: '不存在',
};

/** 规则来源英文 → 中文映射 */
const sourceMap: Record<string, string> = {
  GLOBAL: '全局配置',
  ENVIRONMENT: '环境配置',
  SCENE: '场景配置',
  API: '接口配置',
};
</script>

<style scoped>
.common-step-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-title {
  padding-left: 6px;
  border-left: 4px solid;
  font-weight: 500;
}

.assert-summary {
  margin-bottom: 8px;
}

.script-log-view {
  margin: 0;
  padding: 10px;
  background: var(--color-fill-1);
  border-radius: 6px;
  font-size: 12px;
  max-height: 300px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.result-tabs {
  margin-top: 4px;
}
</style>
