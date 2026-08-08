<template>
  <a-modal
      v-model:visible="visible"
      title="插入模板数据"
      @ok="handleOk"
      @cancel="handleCancel"
      :ok-button-props="{ disabled: !canInsert }"
  >
    <a-spin :loading="loading" tip="加载中...">
      <a-form :model="form" layout="vertical">
        <a-form-item label="选择模板" field="templateId" required>
          <a-select v-model="form.templateId" placeholder="请选择数据模板">
            <a-option v-for="item in templateList" :key="item.id" :value="item.id"
            >{{ item.templateName }}
            </a-option>
          </a-select>
        </a-form-item>

        <a-form-item label="生成方式">
          <a-radio-group v-model="form.mode">
            <a-radio value="single">单条</a-radio>
            <a-radio v-if="allowBatch !== false" value="batch">批量</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item v-if="form.mode === 'batch'" label="数量">
          <a-input-number v-model="form.count" :min="1" :max="1000"/>
        </a-form-item>

        <a-form-item label="预览">
          <a-input v-model="previewExpression" readonly/>
        </a-form-item>
      </a-form>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {Message} from '@arco-design/web-vue';
import {DataTemplate} from '@/types/domain/api/DataTemplate';
import {getDataTemplateList} from '@/api/MyApi/dataTemplate';
import useProjectStore from '@/store/modules/project';

interface Props {
  visible?: boolean;
  allowBatch?: boolean;
}

const props = defineProps<Props>();
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'select', expression: string): void;
}>();

const visible = computed({
  get: () => props.visible ?? false,
  set: (val) => emit('update:visible', val)
});

const projectStore = useProjectStore();
const templateList = ref<DataTemplate[]>([]);
const loading = ref(false);

const form = reactive({
  templateId: undefined as number | undefined,
  mode: 'single' as 'single' | 'batch',
  count: 5
});

const canInsert = computed(() => !!form.templateId);

const previewExpression = computed(() => {
  if (!form.templateId) return '';
  if (form.mode === 'batch') {
    return `{{__TEMPLATE_BATCH(${form.templateId}, ${form.count})__}}`;
  }
  return `{{__TEMPLATE(${form.templateId})__}}`;
});

const loadTemplates = async () => {
  const projectId = projectStore.getProjectId;
  if (!projectId) return;
  loading.value = true;
  try {
    const {data} = await getDataTemplateList(projectId);
    templateList.value = data || [];
  } catch (e) {
    Message.error('加载模板列表失败');
  } finally {
    loading.value = false;
  }
};

watch(() => visible.value, (val) => {
  if (val) {
    loadTemplates();
    form.templateId = undefined;
    form.mode = 'single';
    form.count = 5;
  }
});

const handleOk = () => {
  emit('select', previewExpression.value);
  visible.value = false;
};

const handleCancel = () => {
  visible.value = false;
};
</script>
