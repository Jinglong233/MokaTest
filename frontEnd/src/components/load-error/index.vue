<template>
  <div class="load-error" role="alert">
    <div class="load-error-icon">
      <icon-exclamation-circle-fill />
    </div>
    <div class="load-error-title">{{ title }}</div>
    <div class="load-error-desc">{{ description }}</div>
    <a-button type="outline" @click="emit('retry')">
      <template #icon><icon-refresh /></template>
      重新加载
    </a-button>
  </div>
</template>

<script setup lang="ts">
  withDefaults(
    defineProps<{
      title?: string;
      description?: string;
    }>(),
    {
      title: '加载失败',
      description: '数据加载失败，请检查网络连接后重试',
    }
  );

  const emit = defineEmits<{
    (e: 'retry'): void;
  }>();
</script>

<style scoped lang="less">
  .load-error {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    padding: 48px 16px;
    text-align: center;
  }

  .load-error-icon {
    margin-bottom: 12px;
    color: rgb(var(--red-5));
    font-size: 36px;
    line-height: 1;
  }

  .load-error-title {
    margin-bottom: 4px;
    color: var(--color-text-1);
    font-weight: 500;
    font-size: 15px;
  }

  .load-error-desc {
    margin-bottom: 16px;
    color: var(--color-text-3);
    font-size: 13px;
  }
</style>
