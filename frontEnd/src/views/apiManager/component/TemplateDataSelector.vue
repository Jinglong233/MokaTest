<template>
  <a-modal
      v-model:visible="visible"
      :title="$t('api.template.insertTitle')"
      @ok="handleOk"
      @cancel="handleCancel"
      :ok-button-props="{ disabled: !canInsert }"
  >
    <a-spin :loading="loading" :tip="$t('api.common.loading')">
      <a-form :model="form" layout="vertical">
        <a-form-item :label="$t('api.template.selectTemplate')" field="templateId" required>
          <a-select v-model="form.templateId" :placeholder="$t('api.template.selectPlaceholder')">
            <a-option v-for="item in templateList" :key="item.id" :value="item.id"
            >{{ item.templateName }}
            </a-option>
          </a-select>
        </a-form-item>

        <a-form-item :label="$t('api.template.genMode')">
          <a-radio-group v-model="form.mode">
            <a-radio value="single">{{ $t('api.template.single') }}</a-radio>
            <a-radio v-if="allowBatch !== false" value="batch">{{ $t('api.template.batch') }}</a-radio>
          </a-radio-group>
        </a-form-item>

        <a-form-item v-if="form.mode === 'batch'" :label="$t('api.template.count')">
          <a-input-number v-model="form.count" :min="1" :max="1000"/>
        </a-form-item>

        <a-form-item :label="$t('api.template.preview')">
          <a-input v-model="previewExpression" readonly/>
        </a-form-item>
      </a-form>
    </a-spin>
  </a-modal>
</template>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {Message} from '@arco-design/web-vue';
import {useI18n} from 'vue-i18n';
import {DataTemplate} from '@/types/domain/api/DataTemplate';
import {getDataTemplateList} from '@/api/MyApi/dataTemplate';
import useProjectStore from '@/store/modules/project';

interface Props {
  visible?: boolean;
  allowBatch?: boolean;
}

const {t} = useI18n();

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
    Message.error(t('api.template.loadFailed'));
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
