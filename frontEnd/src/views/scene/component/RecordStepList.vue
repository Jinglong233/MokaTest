<template>
  <a-card size="small" class="record-step-list-card">
    <template #title>
      <span>步骤列表（{{ innerSteps.length }}）</span>
    </template>
    <template #extra>
      <a-button type="text" size="mini" @click="toggleLastFive">
        {{ isLastFiveAllExpanded ? '折叠' : '展开' }}后5个
      </a-button>
    </template>

    <a-alert v-if="warningList.length > 0" type="warning" style="margin-bottom: 12px"
    >
      <div v-for="(w, i) in warningList" :key="i">{{ w }}</div>
    </a-alert>

    <a-scrollbar style="height: 380px; overflow: auto">
      <a-collapse
        v-if="displayedSteps.length > 0"
        :bordered="false"
        v-model:active-key="expandedKeys"
      >
        <a-collapse-item v-for="(item, idx) in displayedSteps" :key="idx" :value="idx">
          <template #header>
            <a-space size="small">
              <span class="record-step-index">{{ idx + 1 }}</span>
              <a-tag size="small">{{ item.stepType }}</a-tag>
              <a-typography-text
                class="record-step-name"
                :ellipsis="{ rows: 1, showTooltip: true }"
              >
                {{ item.stepName }}
              </a-typography-text>
            </a-space>
          </template>

          <template #extra>
            <a-space size="mini">
              <a-button
                type="text"
                size="mini"
                @click.stop="moveUp(idx)"
                :disabled="idx === 0"
              >
                <template #icon>
                  <icon-up />
                </template>
              </a-button>
              <a-button
                type="text"
                size="mini"
                @click.stop="moveDown(idx)"
                :disabled="idx === displayedSteps.length - 1"
              >
                <template #icon>
                  <icon-down />
                </template>
              </a-button>
              <a-button
                type="text"
                size="mini"
                status="danger"
                @click.stop="removeStep(idx)"
              >
                <template #icon>
                  <icon-delete />
                </template>
              </a-button>
            </a-space>
          </template>

          <div class="record-step-detail">
            <div v-if="item.meta" class="record-step-detail-item"
            >
              <span class="record-step-detail-label">摘要：</span>
              <span class="record-step-detail-value">{{ item.meta }}</span>
            </div>
            <div
              v-for="(value, key) in detailEntries(item)"
              :key="key"
              class="record-step-detail-item"
            >
              <span class="record-step-detail-label">{{ key }}：</span>
              <span class="record-step-detail-value">{{ value }}</span>
            </div>
          </div>
        </a-collapse-item>
      </a-collapse>

      <a-empty v-else description="暂无步骤" />
    </a-scrollbar>
  </a-card>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  IconUp,
  IconDown,
  IconDelete,
} from '@arco-design/web-vue/es/icon';
import type { RecordStepDraft } from '@/api/MyApi/record';

interface DisplayStep extends RecordStepDraft {
  meta?: string;
}

const props = defineProps<{
  steps: RecordStepDraft[];
  warnings?: string[];
}>();

const emit = defineEmits<{
  (e: 'update:steps', steps: RecordStepDraft[]): void;
}>();

const expandedKeys = ref<(number | string)[]>([]);
const warningList = computed(() => props.warnings || []);

const innerSteps = computed({
  get: () => props.steps,
  set: (val) => emit('update:steps', val),
});

const displayedSteps = computed<DisplayStep[]>(() => {
  return innerSteps.value.map((item) => {
    const detail = item.stepDetail || {};
    const metaParts: string[] = [];

    const element = detail.element;
    if (element) {
      const target = element.customLocator || element.locator;
      if (target?.locatorType && target?.locatorValue) {
        metaParts.push(`${target.locatorType}=${target.locatorValue}`);
      }
    }

    if (detail.switchIframeType && detail.switchIframeType !== 'EXIT') {
      if (detail.url) metaParts.push(`URL=${detail.url}`);
      if (detail.iframeName) metaParts.push(`NAME=${detail.iframeName}`);
      if (detail.iframeId) metaParts.push(`ID=${detail.iframeId}`);
      if (detail.iframeIndex != null) metaParts.push(`INDEX=${detail.iframeIndex}`);
    }

    if (detail.inputValue != null) {
      metaParts.push(`值：${item.isPassword ? '******' : detail.inputValue}`);
    }
    if (detail.optionValue != null) {
      metaParts.push(`选项：${detail.optionValue}`);
    }

    if (detail.url && detail.stepType === 'OPEN_PAGE') {
      metaParts.push(`URL=${detail.url}`);
    }

    return {
      ...item,
      meta: metaParts.join(' | '),
    };
  });
});

const lastFiveStart = computed(() => Math.max(0, displayedSteps.value.length - 5));
const lastFiveKeys = computed(() =>
  displayedSteps.value
    .map((_, idx) => idx)
    .slice(lastFiveStart.value)
);

const isLastFiveAllExpanded = computed(() =>
  lastFiveKeys.value.length > 0 &&
  lastFiveKeys.value.every((key) => expandedKeys.value.includes(key))
);

watch(
  () => displayedSteps.value.length,
  (len, prevLen) => {
    if (len > 0 && (!prevLen || len > prevLen)) {
      // 新导入或初次渲染后默认展开前 4 个
      expandedKeys.value = displayedSteps.value
        .map((_, idx) => idx)
        .slice(0, Math.min(4, len));
    } else if (len === 0) {
      expandedKeys.value = [];
    }
  },
  { immediate: true }
);

function toggleLastFive() {
  const keys = lastFiveKeys.value;
  if (keys.length === 0) return;
  if (isLastFiveAllExpanded.value) {
    expandedKeys.value = expandedKeys.value.filter(
      (key) => !keys.includes(key as number)
    );
  } else {
    const set = new Set(expandedKeys.value.map((k) => k as number));
    keys.forEach((key) => set.add(key));
    expandedKeys.value = Array.from(set);
  }
}

function removeStep(index: number) {
  const newSteps = [...innerSteps.value];
  newSteps.splice(index, 1);
  innerSteps.value = newSteps;
}

function moveUp(index: number) {
  if (index <= 0) return;
  const newSteps = [...innerSteps.value];
  const temp = newSteps[index];
  newSteps[index] = newSteps[index - 1];
  newSteps[index - 1] = temp;
  innerSteps.value = newSteps;
}

function moveDown(index: number) {
  if (index >= innerSteps.value.length - 1) return;
  const newSteps = [...innerSteps.value];
  const temp = newSteps[index];
  newSteps[index] = newSteps[index + 1];
  newSteps[index + 1] = temp;
  innerSteps.value = newSteps;
}

function detailEntries(item: RecordStepDraft): Record<string, string> {
  const detail = item.stepDetail || {};
  const entries: Record<string, string> = {};

  // 元素定位
  const element = detail.element;
  if (element) {
    const target = element.customLocator || element.locator;
    if (target?.locatorType && target?.locatorValue) {
      entries['定位方式'] = target.locatorType;
      entries['定位值'] = target.locatorValue;
    }
  }

  // iframe
  if (detail.switchIframeType) {
    entries['iframe 类型'] = detail.switchIframeType;
    if (detail.iframeName) entries['iframe 名称'] = detail.iframeName;
    if (detail.iframeId) entries['iframe ID'] = detail.iframeId;
    if (detail.iframeIndex != null) entries['iframe 索引'] = String(detail.iframeIndex);
  }

  // 输入/选择值
  if (detail.inputValue != null) {
    entries['输入值'] = item.isPassword ? '******' : String(detail.inputValue);
  }
  if (detail.optionValue != null) {
    entries['选项值'] = String(detail.optionValue);
  }

  // URL
  if (detail.url) {
    entries['URL'] = detail.url;
  }

  // 等待/断言等通用字段
  if (detail.timeout != null) entries['超时时间'] = `${detail.timeout}ms`;
  if (detail.expectValue != null) entries['期望值'] = String(detail.expectValue);
  if (detail.variableName) entries['变量名'] = detail.variableName;
  if (detail.attributeName) entries['属性名'] = detail.attributeName;

  return entries;
}
</script>

<style scoped>
.record-step-list-card :deep(.arco-card-body) {
  padding: 12px;
}

.record-step-index {
  display: inline-block;
  width: 20px;
  text-align: center;
  color: var(--color-text-3);
}

.record-step-name {
  max-width: 180px;
}

.record-step-detail {
  padding: 8px 12px;
  background: var(--color-fill-1);
  border-radius: 4px;
}

.record-step-detail-item {
  display: flex;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
  line-height: 1.6;
  word-break: break-all;
}

.record-step-detail-label {
  flex-shrink: 0;
  color: var(--color-text-2);
  font-weight: 500;
}

.record-step-detail-value {
  color: var(--color-text-1);
}

.record-step-list-card :deep(.arco-collapse-item-header-right) {
  align-items: center;
}
</style>
