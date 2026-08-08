<template>
  <div class="mock-config-panel">
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-space>
          <span>启用 Mock</span>
          <a-switch
            v-model="localMockResponse.enabled"
            :disabled="disabled"
            @update:model-value="onDataChange"
          />
        </a-space>
      </a-col>
      <a-col :span="6">
        <a-space>
          <span>状态码</span>
          <a-input-number
            v-model="localMockResponse.statusCode"
            :min="100"
            :max="999"
            :disabled="disabled"
            @change="onDataChange"
          />
        </a-space>
      </a-col>
      <a-col :span="6">
        <a-space>
          <span>延迟(ms)</span>
          <a-input-number
            v-model="localMockResponse.delayMs"
            :min="0"
            :max="60000"
            :disabled="disabled"
            @change="onDataChange"
          />
        </a-space>
      </a-col>
    </a-row>

    <a-row :gutter="16" class="mock-content-row">
      <a-col :span="14" class="mock-left">
        <a-tabs class="mock-inner-tabs">
          <a-tab-pane key="header" title="响应头">
            <ParameterTable
              ref="headerTableRef"
              context="mockHeader"
              :disabled="disabled"
              @change="onDataChange"
            />
          </a-tab-pane>
          <a-tab-pane key="body" title="响应体">
            <div class="mock-body-follow">
              <a-alert v-if="hasResponseSchema" type="info">
                Mock 响应体跟随「响应定义」生成，一处定义、Mock 与结构校验共用。
                修改结构请到「响应定义」Tab。
              </a-alert>
              <a-alert v-else type="warning">
                当前接口未配置响应定义，Mock 响应体为空。请先到「响应定义」Tab 绑定数据模板或内联定义结构。
              </a-alert>
            </div>
          </a-tab-pane>
        </a-tabs>
      </a-col>

      <a-col :span="10" class="mock-right">
        <a-card title="实时预览" size="small" class="preview-card"
        >
          <template #extra>
            <a-button
              type="primary"
              size="small"
              :loading="previewLoading"
              @click="loadPreview"
            >重新生成
            </a-button>
          </template>
          <pre class="preview-code">{{
              previewResult || '点击"重新生成"预览 Mock 响应'
            }}</pre>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
  import {
    computed,
    nextTick,
    onMounted,
    ref,
    watch,
  } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import ParameterTable from './ParameterTable.vue';
  import { MockResponse } from '@/types/domain/api/requestModel/MockResponse';
  import { ResponseSchema } from '@/types/domain/api/requestModel/ResponseSchema';
  import { previewResponseSchema } from '@/api/MyApi/mock';

  const props = defineProps<{
    modelValue?: MockResponse;
    /** 接口的响应定义（Mock 响应体跟随其生成） */
    responseSchema?: ResponseSchema;
    disabled?: boolean;
  }>();

  const emit = defineEmits<{
    (e: 'update:modelValue', value: MockResponse): void;
    (e: 'change'): void;
  }>();

  const localMockResponse = ref<MockResponse>(new MockResponse());
  const headerTableRef = ref();
  const previewLoading = ref(false);
  const previewResult = ref('');

  const hasResponseSchema = computed(() => {
    const rs = props.responseSchema;
    return !!rs && rs.mode && rs.mode !== 'NONE';
  });

  watch(
    () => props.modelValue,
    (newVal) => {
      if (newVal) {
        const cloned = JSON.parse(JSON.stringify(newVal));
        if (!cloned.statusCode) {
          cloned.statusCode = 200;
        }
        localMockResponse.value = cloned;
        nextTick(() => {
          headerTableRef.value?.setData(localMockResponse.value.headers || []);
        });
      }
    },
    { immediate: true }
  );

  const onDataChange = () => {
    if (headerTableRef.value) {
      localMockResponse.value.headers = headerTableRef.value.getData();
    }
    emit(
      'update:modelValue',
      JSON.parse(JSON.stringify(localMockResponse.value))
    );
    emit('change');
  };

  const loadPreview = async () => {
    if (!hasResponseSchema.value || !props.responseSchema) {
      previewResult.value = '';
      return;
    }
    previewLoading.value = true;
    try {
      const { data } = await previewResponseSchema(props.responseSchema);
      try {
        const parsed = JSON.parse(data);
        previewResult.value = JSON.stringify(parsed, null, 2);
      } catch (e) {
        previewResult.value = data;
      }
    } catch (e) {
      previewResult.value = '';
      Message.error('预览生成失败：请检查响应定义是否已配置');
    } finally {
      previewLoading.value = false;
    }
  };

  // 响应定义变化时自动刷新预览
  watch(() => props.responseSchema, loadPreview, { deep: true });

  onMounted(() => {
    loadPreview();
  });

  defineExpose({
    getData: () => {
      return {
        ...JSON.parse(JSON.stringify(localMockResponse.value)),
        headers: headerTableRef.value?.getData() || [],
      };
    },
  });
</script>

<style scoped>
  .mock-config-panel {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  .mock-content-row {
    flex: 1;
    min-height: 0;
  }

  .mock-left,
  .mock-right {
    height: 100%;
  }

  .mock-inner-tabs {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  .mock-inner-tabs :deep(.arco-tabs-content) {
    flex: 1;
    min-height: 0;
    padding: 0;
  }

  .mock-inner-tabs :deep(.arco-tabs-pane) {
    height: 100%;
    padding: 0;
  }

  .mock-body-follow {
    padding: 4px 0;
  }

  .preview-card {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  .preview-card :deep(.arco-card-body) {
    flex: 1;
    min-height: 0;
  }

  .preview-code {
    height: 100%;
    overflow: auto;
    background: #f5f5f5;
    padding: 12px;
    border-radius: 4px;
    font-family: monospace;
    white-space: pre-wrap;
    word-break: break-all;
    margin: 0;
  }
</style>
