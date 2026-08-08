<template>
  <a-modal
    :visible="visible"
    title="导入 Swagger / OpenAPI"
    title-align="start"
    :mask-closable="false"
    :esc-to-close="false"
    @ok="handleSubmit"
    @cancel="handleClose"
    @update:visible="(v) => emit('update:visible', v)"
    :ok-loading="loading"
    :ok-text="result ? '确定' : '开始导入'"
    :hide-cancel="result !== null"
  >
    <a-spin :loading="loading" tip="正在解析并导入...">
      <template v-if="!result">
        <a-tabs v-model:active-key="activeTab" type="rounded">
          <a-tab-pane key="file" title="上传文件">
            <a-form :model="formState" layout="vertical">
              <a-form-item label="Swagger 文件" required>
                <a-upload
                  :auto-upload="false"
                  :limit="1"
                  accept=".json,.yaml,.yml"
                  @change="handleFileChange"
                >
                  <template #upload-button>
                    <a-button>
                      <template #icon><icon-upload /></template>
                      选择文件
                    </a-button>
                  </template>
                </a-upload>
                <div v-if="fileName" class="file-name">已选择：{{ fileName }}</div>
              </a-form-item>
            </a-form>
          </a-tab-pane>
          <a-tab-pane key="url" title="URL 导入">
            <a-form :model="formState" layout="vertical">
              <a-form-item label="Swagger URL" required>
                <a-input
                  v-model="url"
                  placeholder="https://petstore.swagger.io/v2/swagger.json"
                  allow-clear
                />
              </a-form-item>
            </a-form>
          </a-tab-pane>
        </a-tabs>

        <a-form :model="formState" layout="vertical" class="import-options">
          <a-form-item label="导入到目录">
            <a-tree-select
              v-model="parentId"
              :data="folderList"
              placeholder="选择目标目录"
              :field-names="{ key: 'id', title: 'apiName' }"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="按 Tag 自动分组">
            <a-switch v-model="groupByTags" />
            <span class="switch-tip">关闭后所有接口平铺到所选目录</span>
          </a-form-item>
          <a-form-item label="覆盖已存在接口">
            <a-switch v-model="overwrite" />
            <span class="switch-tip">开启后重复的接口会更新请求头、参数、请求体等字段</span>
          </a-form-item>
        </a-form>
      </template>

      <template v-else>
        <a-alert type="success" title="导入完成">
          <template #content>
            成功导入 {{ result.interfaceCount }} 个接口，覆盖 {{ result.overwrittenCount }} 个接口，新建/复用 {{ result.folderCount }} 个目录，跳过 {{ result.skippedCount }} 个。
          </template>
        </a-alert>
        <a-table
          v-if="result.skippedCount > 0"
          class="skipped-table"
          :data="result.skipped"
          :pagination="false"
          size="small"
          :bordered="true"
        >
          <template #columns>
            <a-table-column title="路径" data-index="path" />
            <a-table-column title="方法" data-index="method" :width="80" />
            <a-table-column title="原因" data-index="reason" />
          </template>
        </a-table>
      </template>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import { IconUpload } from '@arco-design/web-vue/es/icon';
import { importSwagger } from '@/api/MyApi/apiInterface';
import { ApiFolderTreeVO } from '@/types/domain/api/vo/ApiFolderTreeVO';
import { SwaggerImportResultVO } from '@/types/domain/api/vo/SwaggerImportResultVO';
import { useProjectStore } from '@/store';
import useTeamStore from '@/store/modules/team';

const props = defineProps<{
  visible: boolean;
  folderList: ApiFolderTreeVO[];
}>();

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'success'): void;
}>();

const projectStore = useProjectStore();
const teamStore = useTeamStore();

const activeTab = ref<'file' | 'url'>('file');
const file = ref<File | null>(null);
const fileName = computed(() => file.value?.name || '');
const url = ref('');
const parentId = ref<number>(0);
const groupByTags = ref(true);
const overwrite = ref(false);
const loading = ref(false);
const result = ref<SwaggerImportResultVO | null>(null);

// 用于 a-form 绑定，避免单个字段校验
const formState = reactive({
  activeTab,
  url,
  parentId,
  groupByTags,
  overwrite,
});

const reset = () => {
  activeTab.value = 'file';
  file.value = null;
  url.value = '';
  parentId.value = 0;
  groupByTags.value = true;
  overwrite.value = false;
  result.value = null;
  loading.value = false;
};

watch(
  () => props.visible,
  (v) => {
    if (v) {
      reset();
    }
  }
);

const handleFileChange = (fileList: any[]) => {
  const item = fileList?.[0];
  file.value = item?.file || null;
};

const handleSubmit = async () => {
  if (result.value) {
    handleClose();
    return;
  }

  if (activeTab.value === 'file' && !file.value) {
    Message.error('请选择 Swagger 文件');
    return;
  }
  if (activeTab.value === 'url' && !url.value.trim()) {
    Message.error('请输入 Swagger URL');
    return;
  }

  const formData = new FormData();
  formData.append('projectId', String(projectStore.getProjectId ?? ''));
  formData.append('teamId', String(teamStore.getTeamId ?? ''));
  formData.append('parentId', String(parentId.value ?? 0));
  formData.append('groupByTags', String(groupByTags.value));
  formData.append('overwrite', String(overwrite.value));
  if (activeTab.value === 'file' && file.value) {
    formData.append('file', file.value);
  } else {
    formData.append('url', url.value.trim());
  }

  loading.value = true;
  try {
    const res: any = await importSwagger(formData);
    if (res.code === 200) {
      result.value = res.data as SwaggerImportResultVO;
      Message.success('导入成功');
      emit('success');
    } else {
      Message.error(res.msg || '导入失败');
    }
  } catch (err: any) {
    Message.error(err?.response?.data?.msg || err?.message || '导入异常');
  } finally {
    loading.value = false;
  }
};

const handleClose = () => {
  emit('update:visible', false);
};
</script>

<style scoped>
.file-name {
  margin-top: 8px;
  color: var(--color-text-2);
  font-size: 12px;
}
.import-options {
  margin-top: 16px;
}
.switch-tip {
  margin-left: 8px;
  color: var(--color-text-3);
  font-size: 12px;
}
.skipped-table {
  margin-top: 16px;
}
</style>
