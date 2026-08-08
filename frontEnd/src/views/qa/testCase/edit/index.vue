<template>
  <div class="test-case-edit-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testCase', isEdit ? '编辑用例' : '新建用例']"/>
    <a-card class="edit-card" :title="isEdit ? '编辑用例' : '新建用例'">
      <TestCaseForm
          ref="formRef"
          :id="editId"
          @saved="handleSaved"
      />
      <div class="footer-actions">
        <a-space>
          <a-button size="large" @click="handleCancel">取消</a-button>
          <a-button type="primary" size="large" :loading="saving" @click="handleSave">保存</a-button>
        </a-space>
      </div>
    </a-card>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script setup lang="ts">
import {ref, computed} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import TestCaseForm from '../components/TestCaseForm.vue';
import {useProjectStore} from '@/store';
import {useDirtyGuard} from '@/hooks/useDirtyGuard';

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
  router.push({name: 'TestCase'});
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
.test-case-edit-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.edit-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.edit-card :deep(.arco-card-body) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: auto;
  display: flex;
  flex-direction: column;
  padding-bottom: 0;
}

.footer-actions {
  position: sticky;
  bottom: 0;
  z-index: 200;
  background: var(--color-bg-2);
  padding: 16px 0;
  border-top: 1px solid var(--color-border-2);
  margin-top: 16px;
  text-align: right;
}
</style>
