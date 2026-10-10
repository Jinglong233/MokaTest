<template>
  <div class="common-step-result">
    <!-- 基本信息 -->
    <a-descriptions :column="1">
      <a-descriptions-item :label="$t('scene.result.timeCost')">
        <a-tag>{{ (value.timeConsuming || 0) / 1000 }} {{ $t('scene.result.seconds') }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item :label="$t('scene.result.status')">
        <a-tag v-if="value.status === StepExecutionType.SUCCESS" color="#00b42a">{{ $t('scene.result.success') }}</a-tag>
        <a-tag v-else-if="value.status === StepExecutionType.FAILURE" color="#f53f3f">{{ $t('scene.result.failure') }}</a-tag>
        <a-tag v-else-if="value.status === StepExecutionType.SKIPPED" color="#86909c">{{ $t('scene.result.skipped') }}</a-tag>
        <a-tag v-else color="#86909c">{{ $t('scene.result.unknown') }}</a-tag>
      </a-descriptions-item>
      <a-descriptions-item v-if="value.status === StepExecutionType.FAILURE" :label="$t('scene.result.failReason')">
        <a-collapse>
          <a-collapse-item :header="$t('scene.result.failDetail')">
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
      <!-- AI 失败分诊结果（triageCategory/triageDetail 随报告 JSON 下发） -->
      <a-descriptions-item v-if="value.triageCategory" :label="$t('scene.result.aiTriage')">
        <a-space align="start">
          <a-tag :color="triageColor">{{ triageText }}</a-tag>
          <a-typography-text style="white-space: pre-wrap;">{{ triageDetailText }}</a-typography-text>
        </a-space>
      </a-descriptions-item>
    </a-descriptions>

    <!-- SCRIPT 步骤：脚本日志（UI 场景走 additionalInfo；API 场景成功时日志在 errorMessage） -->
    <template v-if="isScriptStep && scriptLogs">
      <div class="section-title" style="border-color: #722ed1">{{ $t('scene.result.scriptLog') }}</div>
      <pre class="script-log-view">{{ scriptLogs }}</pre>
    </template>

    <!-- 截图：仅当有时显示 -->
    <template v-if="value.screenshotPath">
      <div class="section-title" style="border-color: #4052ec">{{ $t('scene.result.screenshot') }}</div>
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
        {{ isIfStep ? $t('scene.result.judgeResult') : $t('scene.result.assertResult') }}
        <a-space style="margin-left: 12px">
          <a-tag color="green">{{ $t('scene.result.pass') }} {{ assertPassCount }}</a-tag>
          <a-tag color="red">{{ $t('scene.result.fail') }} {{ assertFailCount }}</a-tag>
        </a-space>
      </div>
      <a-collapse>
        <a-collapse-item
            v-for="(assertDetail, idx) in assertResultList"
            :key="`assert-${idx}`"
            :header="$t('scene.result.assertHeader', { name: isIfStep ? $t('scene.result.judge') : $t('scene.result.assert'), index: idx + 1, type: assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '' })"
        >
          <template #extra>
            <a-tag :color="assertDetail.success === true ? 'green' : 'red'">
              {{ assertDetail.success === true ? $t('scene.result.pass') : $t('scene.result.fail') }}
            </a-tag>
          </template>
          <a-descriptions :column="1" size="small">
            <a-descriptions-item :label="$t('scene.result.assertType')">
              {{ assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '-' }}
            </a-descriptions-item>
            <a-descriptions-item v-if="assertDetail.assertRelationship" :label="$t('scene.result.assertRelation')">
              {{ assertRelationshipMap[assertDetail.assertRelationship] || assertDetail.assertRelationship }}
            </a-descriptions-item>
            <a-descriptions-item v-if="assertDetail.assertTip" :label="$t('scene.result.tip')">
              {{ assertDetail.assertTip }}
            </a-descriptions-item>
            <a-descriptions-item v-if="assertDetail.actualValue != null" :label="$t('scene.result.actualValue')">
              <a-typography-text copyable>{{ assertDetail.actualValue }}</a-typography-text>
            </a-descriptions-item>
            <a-descriptions-item v-if="assertDetail.source" :label="$t('scene.result.source')">
              {{ sourceMap[assertDetail.source] || assertDetail.source }}
            </a-descriptions-item>
          </a-descriptions>
        </a-collapse-item>
      </a-collapse>
    </template>

    <!-- EXTRACT 步骤：提取结果直接展示 -->
    <template v-if="isExtractStep && extractResultList.length > 0">
      <div class="section-title" style="border-color: #165dff">{{ $t('scene.result.extractResult') }}</div>
      <a-collapse>
        <a-collapse-item
            v-for="(extractItem, idx) in extractResultList"
            :key="`extract-${idx}`"
            :header="$t('scene.result.varHeader', { key: extractItem.key })"
        >
          <a-typography-text copyable>{{ extractItem.value }}</a-typography-text>
        </a-collapse-item>
      </a-collapse>
    </template>

    <!-- WHILE 步骤：循环判断结果直接展示 -->
    <template v-if="isWhileStep && hasWhileAssertResults">
      <div class="section-title" style="border-color: #ff7d00">{{ $t('scene.result.whileResult') }}</div>
      <a-collapse>
        <a-collapse-item
            v-for="(assertList, cycleIndex) in value.whileAssertResults"
            :key="`while-${cycleIndex}`"
            :header="$t('scene.result.whileIteration', { index: cycleIndex })"
        >
          <a-collapse>
            <a-collapse-item
                v-for="(assertDetail, assertIdx) in assertList"
                :key="`while-assert-${assertIdx}`"
                :header="$t('scene.result.assertHeader', { name: $t('scene.result.judge'), index: Number(assertIdx) + 1, type: assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '' })"
            >
              <template #extra>
                <a-tag :color="assertDetail.success === true ? 'green' : 'red'">
                  {{ assertDetail.success === true ? $t('scene.result.pass') : $t('scene.result.fail') }}
                </a-tag>
              </template>
              <a-descriptions :column="1" size="small">
                <a-descriptions-item :label="$t('scene.result.assertType')">
                  {{ assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '-' }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.assertRelationship" :label="$t('scene.result.assertRelation')">
                  {{ assertRelationshipMap[assertDetail.assertRelationship] || assertDetail.assertRelationship }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.assertTip" :label="$t('scene.result.tip')">
                  {{ assertDetail.assertTip }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.actualValue != null" :label="$t('scene.result.actualValue')">
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
            {{ $t('scene.setting.tabAssert') }}
          </a-badge>
        </template>
        <template v-if="assertResultList.length > 0">
          <a-space class="assert-summary">
            <a-tag color="green">{{ $t('scene.result.pass') }} {{ assertPassCount }}</a-tag>
            <a-tag color="red">{{ $t('scene.result.fail') }} {{ assertFailCount }}</a-tag>
          </a-space>
          <a-collapse>
            <a-collapse-item
                v-for="(assertDetail, idx) in assertResultList"
                :key="`assert-${idx}`"
                :header="$t('scene.result.assertHeader', { name: $t('scene.result.assert'), index: idx + 1, type: assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '' })"
            >
              <template #extra>
                <a-tag :color="assertDetail.success === true ? 'green' : 'red'">
                  {{ assertDetail.success === true ? $t('scene.result.pass') : $t('scene.result.fail') }}
                </a-tag>
              </template>
              <a-descriptions :column="1" size="small">
                <a-descriptions-item :label="$t('scene.result.assertType')">
                  {{ assertTypeMap[assertDetail.assertType] || assertDetail.assertType || '-' }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.assertRelationship" :label="$t('scene.result.assertRelation')">
                  {{ assertRelationshipMap[assertDetail.assertRelationship] || assertDetail.assertRelationship }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.assertTip" :label="$t('scene.result.tip')">
                  {{ assertDetail.assertTip }}
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.actualValue != null" :label="$t('scene.result.actualValue')">
                  <a-typography-text copyable>{{ assertDetail.actualValue }}</a-typography-text>
                </a-descriptions-item>
                <a-descriptions-item v-if="assertDetail.source" :label="$t('scene.result.source')">
                  {{ sourceMap[assertDetail.source] || assertDetail.source }}
                </a-descriptions-item>
              </a-descriptions>
            </a-collapse-item>
          </a-collapse>
        </template>
        <a-empty v-else :description="$t('scene.result.noAssert')"/>
      </a-tab-pane>
      <a-tab-pane v-if="showExtractTab" key="extract">
        <template #title>
          <a-badge
              :count="extractResultList.length"
              :offset="[10, -2]"
              :dot-style="{ background: '#E5E6EB', color: '#86909C' }"
          >
            {{ $t('scene.setting.tabExtract') }}
          </a-badge>
        </template>
        <template v-if="extractResultList.length > 0">
          <a-collapse>
            <a-collapse-item
                v-for="(extractItem, idx) in extractResultList"
                :key="`extract-${idx}`"
                :header="$t('scene.result.varHeader', { key: extractItem.key })"
            >
              <a-typography-text copyable>{{ extractItem.value }}</a-typography-text>
            </a-collapse-item>
          </a-collapse>
        </template>
        <a-empty v-else :description="$t('scene.result.noExtract')"/>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {useI18n} from 'vue-i18n';
import {StepExecutionType} from "@/types/enum/StepExecutionType";
import ScreenshotImage from "./ScreenshotImage.vue";

interface Props {
  value: any;
  stepType: string;
}

const props = defineProps<Props>();
const { t } = useI18n();

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

// ==================== AI 失败分诊 ====================
const TRIAGE_TEXTS = computed<Record<string, string>>(() => ({
  DATA: t('scene.triage.catData'), ENV: t('scene.triage.catEnv'), PRODUCT_BUG: t('scene.triage.catProductBug'), LOCATOR: t('scene.triage.catLocator'),
}));
const TRIAGE_COLORS: Record<string, string> = {
  DATA: 'arcoblue', ENV: 'orange', PRODUCT_BUG: 'red', LOCATOR: 'purple',
};
const triageText = computed(() => TRIAGE_TEXTS.value[props.value?.triageCategory] || props.value?.triageCategory);
const triageColor = computed(() => TRIAGE_COLORS[props.value?.triageCategory] || 'gray');
/** PRODUCT_BUG 时 detail 是 BUG 草稿 JSON，渲染为可读文本 */
const triageDetailText = computed(() => {
  const raw = props.value?.triageDetail;
  if (!raw) return '';
  if (props.value?.triageCategory === 'PRODUCT_BUG' && typeof raw === 'string' && raw.startsWith('{')) {
    try {
      const d = JSON.parse(raw);
      return `${d.title || ''}\n${d.description || ''}\n${t('scene.triage.reproduceSteps')}\n${d.reproduceSteps || ''}\n${t('scene.triage.severity')}${d.severity || ''}`.trim();
    } catch { return raw; }
  }
  return raw;
});

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

/** 断言类型枚举 → 文案映射 */
const assertTypeMap = computed<Record<string, string>>(() => ({
  HEADER: t('scene.assertType.header'),
  BODY: t('scene.assertType.body'),
  STATUS_CODE: t('scene.assertType.statusCode'),
  RESPONSE_TIME: t('scene.assertType.responseTime'),
  CUSTOM: t('scene.assertType.custom'),
  SCHEMA: t('scene.assertType.schema'),
  TEXT: t('scene.assertType.text'),
  ELEMENT: t('scene.assertType.element'),
  URL: 'URL',
  TITLE: t('scene.assertType.title'),
}));

/** 断言关系枚举 → 文案映射 */
const assertRelationshipMap = computed<Record<string, string>>(() => ({
  EQUALS: t('scene.assertRel.equals'),
  NOT_EQUALS: t('scene.assertRel.notEquals'),
  CONTAINS: t('scene.assertRel.contains'),
  NOT_CONTAINS: t('scene.assertRel.notContains'),
  GT: t('scene.assertRel.gt'),
  LT: t('scene.assertRel.lt'),
  GE: t('scene.assertRel.ge'),
  LE: t('scene.assertRel.le'),
  REGULAR: t('scene.assertRel.regular'),
  EXISTS: t('scene.assertRel.exists'),
  NOT_EXISTS: t('scene.assertRel.notExists'),
}));

/** 规则来源枚举 → 文案映射 */
const sourceMap = computed<Record<string, string>>(() => ({
  GLOBAL: t('scene.ruleSource.global'),
  ENVIRONMENT: t('scene.ruleSource.environment'),
  SCENE: t('scene.ruleSource.scene'),
  API: t('scene.ruleSource.api'),
}));
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
