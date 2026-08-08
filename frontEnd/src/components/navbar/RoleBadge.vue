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
import { useUserStore, usePermissionStore } from '@/store';

const userStore = useUserStore();
const permissionStore = usePermissionStore();

const label = computed(() => {
  if (userStore.role === 'super_admin') return '超管';
  if (permissionStore.hasPermission('team:role:manage')) return '团队管理员';
  if (permissionStore.hasPermission('project:create')) return '项目管理员';
  return '成员';
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
