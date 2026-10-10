<template>
  <div class="no-project-placeholder">
    <div class="placeholder-card">
      <div class="placeholder-icon">
        <Inbox :size="48" stroke-width="1.5" />
      </div>
      <div class="placeholder-title">{{ $t('components.noProject.title') }}</div>
      <div class="placeholder-desc">
        {{ $t('components.noProject.description') }}
      </div>
      <a-button type="primary" @click="goToProject">
        <template #icon>
          <IconApps />
        </template>
        {{ $t('components.noProject.create') }}
      </a-button>
      <a-button type="text" @click="goBackToList">
        <template #icon>
          <IconArrowLeft />
        </template>
        {{ $t('components.noProject.back') }}
      </a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Inbox } from 'lucide-vue-next';
import { IconApps, IconArrowLeft } from '@arco-design/web-vue/es/icon';
import { useRouter } from 'vue-router';

const router = useRouter();

const goToProject = () => {
  router.push({ name: 'ProjectInfo' });
};

const goBackToList = () => {
  router.replace({ name: 'TeamWorkspace' });
};
</script>

<style scoped>
.no-project-placeholder {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 360px;
  padding: 2rem;
}
.placeholder-card {
  width: 100%;
  max-width: 480px;
  text-align: center;
  padding: 2.5rem 2rem;
  border-radius: 1.5rem;
  background: rgba(255, 255, 255, 0.95);
  border: 1px solid #e5e7eb;
  box-shadow: 0 12px 32px -12px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
}
.placeholder-icon {
  width: 88px;
  height: 88px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #f2f5fa;
  color: #94a3b8;
}
.placeholder-title {
  font-size: 1.15rem;
  font-weight: 700;
  color: #1f2937;
}
.placeholder-desc {
  font-size: 0.9rem;
  color: #6b7280;
  line-height: 1.6;
  max-width: 360px;
}
</style>
