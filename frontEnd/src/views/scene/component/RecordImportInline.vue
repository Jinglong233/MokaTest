<template>
  <a-upload
    drag
    accept=".json"
    :auto-upload="false"
    :show-file-list="false"
    @change="handleFileChange"
  >
    <template #upload-button>
      <div class="record-upload-area">
        <icon-upload style="font-size: 32px; color: var(--color-text-3)" />
        <div style="margin-top: 8px">{{ $t('scene.record.uploadTitle') }}</div>
        <div style="font-size: 12px; color: var(--color-text-3)"
          >{{ $t('scene.record.uploadDesc') }}</div
        >
      </div>
    </template>
  </a-upload>
</template>

<script setup lang="ts">
import { Message } from '@arco-design/web-vue';
import { IconUpload } from '@arco-design/web-vue/es/icon';
import { useI18n } from 'vue-i18n';
import {
  importRecord,
  RecordStepDraft,
  RecordImportResult,
} from '@/api/MyApi/record';

const { t } = useI18n();

const props = defineProps<{
  projectId: string;
}>();

const emit = defineEmits<{
  (e: 'stepsChange', steps: RecordStepDraft[]): void;
}>();

function reset() {
  emit('stepsChange', []);
}

async function handleFileChange(fileList: any[]) {
  if (!fileList || fileList.length === 0) {
    return;
  }
  const rawFile = fileList[0].file || fileList[0];
  if (!rawFile) {
    return;
  }
  if (rawFile.size > 2 * 1024 * 1024) {
    Message.error(t('scene.record.fileTooLarge'));
    return;
  }
  try {
    const result = await importRecord(rawFile, props.projectId);
    const data: RecordImportResult = result.data;
    emit('stepsChange', data.steps || []);
  } catch (err: any) {
    Message.error(err?.response?.data?.msg || err?.message || t('scene.record.parseFailed'));
  }
}

defineExpose({
  reset,
});
</script>

<style scoped>
.record-upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  padding: 48px 0;
  border: 1px dashed var(--color-border-2);
  border-radius: 4px;
  background: var(--color-fill-1);
}
</style>
