<template>
  <div class="collapsible-split">
    <a-split
      class="cs-split"
      v-model:size="size"
      min="0px"
      :max="max"
      :disabled="disabled"
      @moving-end="handleMovingEnd"
    >
      <template #first>
        <div class="cs-pane" v-show="!collapsed">
          <slot name="first" />
        </div>
      </template>
      <template #second>
        <div class="cs-pane cs-pane-second">
          <slot name="second" />
        </div>
      </template>
    </a-split>
    <!-- 收起后的恢复按钮：悬浮在左边缘 -->
    <a-tooltip v-if="collapsed" :content="$t('components.collapsibleSplit.expand')" position="right">
      <a-button class="cs-expand-btn" size="mini" @click="expand">
        <template #icon><icon-right /></template>
      </a-button>
    </a-tooltip>
  </div>
</template>

<script setup lang="ts">
/**
 * 可折叠分栏（基于 Arco a-split 封装）
 * - 拖动分隔条到 threshold 以下自动完全收起左栏
 * - 收起后左边缘出现箭头按钮，点击恢复默认宽度
 * - 两栏内容 100% 自适应
 */
import { ref, watch } from 'vue';
import { IconRight } from '@arco-design/web-vue/es/icon';

const props = withDefaults(defineProps<{
  /** 默认（及恢复）宽度 */
  defaultSize?: string;
  /** 正常态最小宽度（拖动松手时小于它会回弹，除非直接收起） */
  min?: string;
  /** 最大宽度 */
  max?: string;
  /** 收起阈值（px）：拖到该值以下视为收起意图 */
  threshold?: number;
  disabled?: boolean;
}>(), {
  defaultSize: '260px',
  min: '180px',
  max: '480px',
  threshold: 80,
  disabled: false,
});

const emit = defineEmits<{
  (e: 'collapseChange', collapsed: boolean): void;
}>();

const size = ref(props.defaultSize);
const collapsed = ref(false);
const toPx = (v: string | number) => (typeof v === 'number' ? v : parseFloat(v) || 0);

// 拖动结束：低于阈值 → 收起；低于 min 但未达阈值 → 回弹到 min
const handleMovingEnd = () => {
  const v = toPx(size.value);
  if (v <= props.threshold) {
    collapsed.value = true;
    size.value = '0px';
    emit('collapseChange', true);
  } else {
    collapsed.value = false;
    if (v < toPx(props.min)) {
      size.value = props.min;
    }
    emit('collapseChange', false);
  }
};

const expand = () => {
  collapsed.value = false;
  size.value = props.defaultSize;
  emit('collapseChange', false);
};

// 外部禁用/启用时不做处理；尺寸被外部重置时同步折叠态
watch(size, (v) => {
  if (toPx(v) > props.threshold && collapsed.value) {
    collapsed.value = false;
  }
});
</script>

<style scoped lang="less">
.collapsible-split {
  position: relative;
  height: 100%;
  min-height: 0;
  flex: 1;
}

.cs-split {
  height: 100%;
  min-height: 0;
}

.cs-pane {
  height: 100%;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.cs-expand-btn {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  z-index: 20;
  border-radius: 0 4px 4px 0;
  box-shadow: 2px 0 8px rgba(0, 0, 0, 0.08);
}
</style>
