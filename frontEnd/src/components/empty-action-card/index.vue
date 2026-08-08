<template>
  <a-card class="empty-action-card" hoverable @click="emit('click')">
    <div class="card-icon" :class="{ 'swagger-icon': swagger }">
      <component :is="icon" v-if="icon && !swagger" />
      <svg v-if="swagger" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="10" fill="#85EA2D"/>
        <path
            d="M8.5 8.5C8.5 8.5 10 7 12 7C14.5 7 15.5 8.5 15.5 9.5C15.5 11 14 11.5 12.5 11.5H10.5C9.5 11.5 9 12 9 13C9 14 9.8 14.5 11 14.5H15.5"
            stroke="white"
            stroke-width="1.8"
            stroke-linecap="round"
            fill="none"
        />
      </svg>
    </div>
    <div class="card-title">{{ title }}</div>
  </a-card>
</template>

<script setup lang="ts">
const props = defineProps<{
  title: string;
  icon?: any;
  swagger?: boolean;
}>();

const emit = defineEmits<{
  (e: 'click'): void;
}>();
</script>

<style scoped lang="less">
.empty-action-card {
  width: 180px;
  min-height: 200px;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
  text-align: center;
  border-radius: 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);

  :deep(.arco-card-body) {
    padding: 40px 16px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 200px;
  }
}

.empty-action-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  background: var(--color-bg-1);
}

.card-icon {
  position: relative;
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--color-primary-light-1);
  color: rgb(var(--primary-6));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  margin-bottom: 18px;
}

.card-icon::before,
.card-icon::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  border: 1px solid rgba(var(--primary-6), 0.25);
  animation: ripple 2.4s ease-out infinite;
}

.card-icon::after {
  animation-delay: 1.2s;
}

@keyframes ripple {
  0% {
    transform: scale(1);
    opacity: 0.55;
  }
  100% {
    transform: scale(2.6);
    opacity: 0;
  }
}

.card-icon.swagger-icon {
  background: transparent;
  padding: 0;

  &::before,
  &::after {
    border-color: rgba(133, 234, 45, 0.25);
  }

  svg {
    width: 100%;
    height: 100%;
  }
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
}
</style>
