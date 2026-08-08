<template>
  <div v-if="projectStore.hasProjectSelected" class="requirement-edit-page">
    <!-- 顶部面包屑 & 全局操作栏（Sticky Header） -->
    <header class="edit-page-header">
      <div class="header-inner">
        <Breadcrumb :items="['menu.qa', 'menu.qa.requirement', isEdit ? '编辑需求' : '新建需求']" />
        <div class="header-actions">
          <a-button class="header-cancel-btn" size="large" @click="handleCancel">取消</a-button>
          <a-button type="primary" size="large" :loading="saving" @click="handleSave">保存</a-button>
        </div>
      </div>
    </header>

    <!-- 主体表单区 -->
    <main class="edit-page-body">
      <RequirementForm ref="formRef" :id="editId" @saved="handleSaved" />
    </main>

    <!-- 移动端/窄屏悬浮底部操作栏 -->
    <div class="mobile-footer-bar">
      <a-space size="medium">
        <a-button long size="large" @click="handleCancel">取消</a-button>
        <a-button type="primary" long size="large" :loading="saving" @click="handleSave">保存</a-button>
      </a-space>
    </div>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import RequirementForm from '../components/RequirementForm.vue';
import { useProjectStore } from '@/store';
import { useDirtyGuard } from '@/hooks/useDirtyGuard';

const route = useRoute();
const router = useRouter();
const projectStore = useProjectStore();

const formRef = ref();
const saving = ref(false);

useDirtyGuard(() => formRef.value?.isDirty?.() ?? false);

const isEdit = computed(() => !!route.params.id);
const editId = computed(() => {
  const paramId = route.params.id;
  return Array.isArray(paramId) ? paramId[0] : paramId;
});

const handleSave = async () => {
  if (saving.value) return;
  saving.value = true;
  try {
    const success = await formRef.value?.save();
    if (success) {
      saving.value = false;
    }
  } finally {
    saving.value = false;
  }
};

const handleSaved = () => {
  router.push({ name: 'Requirement' });
};

const handleCancel = () => {
  if (formRef.value?.isDirty?.()) {
    const leave = confirm('您有未保存的更改，确定要离开吗？');
    if (!leave) return;
  }
  router.back();
};
</script>

<style scoped lang="less">
.requirement-edit-page {
  display: flex;
  flex-direction: column;
  min-height: var(--page-container-height, calc(100vh - 60px));
  background: #f5f6f7;
}

.edit-page-header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: #fff;
  border-bottom: 1px solid var(--color-border-2);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.05);
}

.header-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 24px;
  width: 100%;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-cancel-btn {
  transition: all 0.2s ease;
}

.header-cancel-btn:hover {
  background: var(--color-fill-2);
}

.edit-page-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 24px;
  width: 100%;
  min-width: 0;
  min-height: 0;
}

/* 移动端底部悬浮操作栏：默认隐藏 */
.mobile-footer-bar {
  display: none;
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 101;
  background: #fff;
  border-top: 1px solid var(--color-border-2);
  padding: 12px 16px;
  box-shadow: 0 -2px 8px rgba(0, 0, 0, 0.06);
}

.mobile-footer-bar :deep(.arco-space) {
  width: 100%;
}

.mobile-footer-bar :deep(.arco-space-item) {
  flex: 1;
}

/* 窄屏/移动端：隐藏顶部保存按钮，显示底部悬浮栏 */
@media (max-width: 767px) {
  .header-actions {
    display: none;
  }

  .edit-page-body {
    padding: 16px;
    padding-bottom: 80px;
  }

  .mobile-footer-bar {
    display: block;
  }
}

@media (max-width: 1023px) {
  .header-inner {
    padding: 12px 16px;
  }
}
</style>
