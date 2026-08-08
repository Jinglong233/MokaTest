<template>
  <div class="drop-indicator">
    <!-- 插入位置提示线 -->
    <div
        v-if="line"
        class="indicator-line"
        :class="{ 'is-container': label === '放入容器' }"
        :style="lineStyle"
    >
      <div class="indicator-arrow indicator-arrow-top"/>
      <div class="indicator-arrow indicator-arrow-bottom"/>
      <div class="indicator-label">{{ label }}</div>
    </div>

    <!-- 容器作为父节点的高亮壳 -->
    <div
        v-if="container"
        class="indicator-container"
        :style="containerStyle"
    >
      <div class="indicator-container-label">放入容器
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed} from 'vue';

interface Props {
  // 插入线位置：x 为左侧坐标，y 为纵坐标，height 为线高度
  line?: { x: number; y: number; height: number } | null;
  // 容器落点位置
  container?: { x: number; y: number; width: number; height: number } | null;
  // 容器落点位置类型描述
  label?: string;
}

const props = withDefaults(defineProps<Props>(), {
  label: '插入',
});

const lineStyle = computed(() => {
  if (!props.line) return {};
  return {
    left: `${props.line.x}px`,
    top: `${props.line.y}px`,
    height: `${props.line.height}px`,
  };
});

const containerStyle = computed(() => {
  if (!props.container) return {};
  return {
    left: `${props.container.x}px`,
    top: `${props.container.y}px`,
    width: `${props.container.width}px`,
    height: `${props.container.height}px`,
  };
});
</script>

<style scoped>
.drop-indicator {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 999;
}

.indicator-line {
  position: absolute;
  width: 4px;
  background: rgb(var(--arcoblue-6));
  border-radius: 2px;
  transform: translateX(-50%);
  box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.2), 0 0 12px rgba(22, 93, 255, 0.5);
  animation: pulse-line 1.2s ease-in-out infinite;
}

.indicator-line.is-container {
  background: rgb(var(--green-6));
  box-shadow: 0 0 0 2px rgba(0, 180, 42, 0.2), 0 0 12px rgba(0, 180, 42, 0.5);
}

@keyframes pulse-line {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.7;
  }
}

.indicator-arrow {
  position: absolute;
  left: 50%;
  width: 0;
  height: 0;
  border-left: 6px solid transparent;
  border-right: 6px solid transparent;
  transform: translateX(-50%);
}

.indicator-arrow-top {
  top: -6px;
  border-bottom: 7px solid rgb(var(--arcoblue-6));
}

.indicator-line.is-container .indicator-arrow-top {
  border-bottom-color: rgb(var(--green-6));
}

.indicator-arrow-bottom {
  bottom: -6px;
  border-top: 7px solid rgb(var(--arcoblue-6));
}

.indicator-line.is-container .indicator-arrow-bottom {
  border-top-color: rgb(var(--green-6));
}

.indicator-label {
  position: absolute;
  top: 50%;
  left: 16px;
  transform: translateY(-50%);
  background: rgb(var(--arcoblue-6));
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  padding: 3px 10px;
  border-radius: 4px;
  white-space: nowrap;
  box-shadow: 0 2px 8px rgba(22, 93, 255, 0.3);
}

.indicator-line.is-container .indicator-label {
  background: rgb(var(--green-6));
  box-shadow: 0 2px 8px rgba(0, 180, 42, 0.3);
}

.indicator-container {
  position: absolute;
  border: 2px dashed rgb(var(--green-6));
  border-radius: 10px;
  background: rgba(0, 180, 42, 0.08);
  box-shadow: 0 0 0 3px rgba(0, 180, 42, 0.12), inset 0 0 20px rgba(0, 180, 42, 0.06);
  display: flex;
  align-items: center;
  justify-content: center;
  animation: pulse-container 1.2s ease-in-out infinite;
}

@keyframes pulse-container {
  0%, 100% {
    box-shadow: 0 0 0 3px rgba(0, 180, 42, 0.12), inset 0 0 20px rgba(0, 180, 42, 0.06);
  }
  50% {
    box-shadow: 0 0 0 5px rgba(0, 180, 42, 0.18), inset 0 0 30px rgba(0, 180, 42, 0.12);
  }
}

.indicator-container-label {
  background: rgb(var(--green-6));
  color: #fff;
  font-size: 12px;
  font-weight: 500;
  padding: 5px 14px;
  border-radius: 4px;
  box-shadow: 0 2px 8px rgba(0, 180, 42, 0.3);
}
</style>
