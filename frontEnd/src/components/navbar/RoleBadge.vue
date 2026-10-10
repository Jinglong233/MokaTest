<template>
  <a-tag
    v-if="label"
    :color="color"
    size="small"
    class="role-badge"
  >
    {{ label }}
  </a-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { useUserStore, usePermissionStore } from '@/store';

const { t } = useI18n();
const userStore = useUserStore();
const permissionStore = usePermissionStore();

const label = computed(() => {
  if (userStore.role === 'super_admin') return t('components.roleBadge.superAdmin');
  if (permissionStore.hasPermission('team:role:manage')) return t('components.roleBadge.teamAdmin');
  if (permissionStore.hasPermission('project:create')) return t('components.roleBadge.projectAdmin');
  return t('components.roleBadge.member');
});

const color = computed(() => {
  if (userStore.role === 'super_admin') return 'orange';
  if (permissionStore.hasPermission('team:role:manage')) return 'purple';
  if (permissionStore.hasPermission('project:create')) return 'green';
  return 'gray';
});
</script>

<style scoped lang="less">
.role-badge {
  user-select: none;
}
</style>
