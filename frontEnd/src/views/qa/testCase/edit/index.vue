<template>
  <div class="test-case-edit-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.testCase', isEdit ? 'qa.testCase.edit' : 'qa.testCase.add']"/>
    <a-card class="edit-card" :bordered="false" :title="isEdit ? $t('qa.testCase.edit') : $t('qa.testCase.add')">
      <template #extra>
        <a-space>
          <a-button @click="handleCancel">{{ $t('qa.common.cancel') }}</a-button>
          <a-button v-if="!isEdit" :loading="savingContinue" @click="handleSaveAndContinue">{{ $t('qa.testCase.saveAndContinue') }}</a-button>
          <a-button type="primary" :loading="saving" @click="handleSave">{{ $t('qa.common.save') }}</a-button>
        </a-space>
      </template>
      <TestCaseForm
          ref="formRef"
          :id="editId"
          @saved="handleSaved"
      />
    </a-card>
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'TestCaseEdit' };
</script>

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
const savingContinue = ref(false);
let isContinueCreate = false;

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
  if (isContinueCreate) {
    isContinueCreate = false;
    return;
  }
  router.push({name: 'TestCase'});
};

const handleSaveAndContinue = async () => {
  isContinueCreate = true;
  savingContinue.value = true;
  try {
    const success = await formRef.value?.save();
    if (success) {
      // 重置表单，继续新建
      await formRef.value?.initForm();
    } else {
      isContinueCreate = false;
    }
  } finally {
    savingContinue.value = false;
  }
};

const handleCancel = () => {
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
}
</style>
