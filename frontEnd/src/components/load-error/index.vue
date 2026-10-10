<template>
  <div class="load-error" role="alert">
    <div class="load-error-icon">
      <icon-exclamation-circle-fill />
    </div>
    <div class="load-error-title">{{ displayTitle }}</div>
    <div class="load-error-desc">{{ displayDescription }}</div>
    <a-button type="outline" @click="emit('retry')">
      <template #icon><icon-refresh /></template>
      {{ $t('components.loadError.retry') }}
    </a-button>
  </div>
</template>

<script setup lang="ts">
  import { computed } from 'vue';
  import { useI18n } from 'vue-i18n';

  const props = defineProps<{
    title?: string;
    description?: string;
  }>();

  const { t } = useI18n();

  const displayTitle = computed(
    () => props.title || t('components.loadError.title')
  );
  const displayDescription = computed(
    () => props.description || t('components.loadError.description')
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
