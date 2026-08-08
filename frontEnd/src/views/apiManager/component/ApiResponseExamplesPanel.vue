<template>
  <div class="response-examples-panel">
    <div class="response-examples-toolbar">
      <a-button
        v-if="!disabled"
        type="primary"
        size="small"
        @click="addExample"
      >
        <template #icon><icon-plus /></template>
        新增响应示例
      </a-button>
    </div>

    <a-empty v-if="!displayExamples.length" description="暂无响应示例" />
    <template v-else>
      <a-tabs v-model:active-key="activeKey" type="line" class="response-tabs">
        <a-tab-pane
          v-for="(item, index) in displayExamples"
          :key="item._key"
        >
          <template #title>
            <span
              class="example-tab-title"
              :draggable="!disabled"
              :class="{
                'is-dragging': dragSourceIndex === index,
                'is-drag-over': dragOverIndex === index && dragSourceIndex !== index
              }"
              @dragstart="handleDragStart(index)"
              @dragover.prevent="handleDragOver(index)"
              @drop="handleDrop(index)"
              @dragend="handleDragEnd"
            >
              {{ tabTitle(item) }}
            </span>
          </template>
          <div class="response-form">
            <div class="response-form-row">
              <a-form-item label="状态码" class="compact-form-item status-code-form-item">
                <a-input-number
                  v-model="item.statusCode"
                  :min="100"
                  :max="599"
                  :disabled="disabled"
                  :hide-button="true"
                  @change="onChange"
                  class="status-code-input"
                />
              </a-form-item>
              <a-form-item label="Content-Type" class="compact-form-item content-type-form-item">
                <a-select
                  v-model="item.contentType"
                  placeholder="请选择"
                  :disabled="disabled"
                  :options="contentTypeOptions"
                  allow-create
                  @change="onChange"
                  class="content-type-select"
                />
              </a-form-item>
              <a-form-item label="描述" class="compact-form-item description-form-item">
                <a-input
                  v-model="item.description"
                  placeholder="响应描述"
                  :disabled="disabled"
                  @input="onChange"
                  class="description-input"
                />
              </a-form-item>
              <a-button
                v-if="!disabled"
                type="text"
                status="danger"
                size="small"
                @click="removeExample(item)"
              >
                <template #icon><icon-delete /></template>
                删除
              </a-button>
            </div>
          </div>

          <div class="response-section">
            <div class="section-title">响应头</div>
            <a-scrollbar
              v-if="item.headers?.length"
              style="height: 180px; overflow: auto;"
            >
              <div class="header-list">
                <div
                  v-for="(header, hIndex) in item.headers"
                  :key="hIndex"
                  class="header-row"
                >
                  <a-input
                    v-model="header.name"
                    placeholder="Header 名"
                    :disabled="disabled"
                    @input="onChange"
                    class="header-input"
                  />
                  <a-input
                    v-model="header.value"
                    placeholder="Header 值"
                    :disabled="disabled"
                    @input="onChange"
                    class="header-input"
                  />
                  <a-button
                    v-if="!disabled"
                    type="text"
                    size="mini"
                    status="danger"
                    @click="removeHeader(item, hIndex)"
                  >
                    <template #icon><icon-delete /></template>
                  </a-button>
                </div>
              </div>
            </a-scrollbar>
            <a-button
              v-if="!disabled"
              type="text"
              size="mini"
              @click="addHeader(item)"
            >
              <template #icon><icon-plus /></template>
              添加响应头
            </a-button>
          </div>

          <div class="response-section response-body-section">
            <div class="section-title">响应体</div>
            <div class="response-body-editor">
              <BodyCodeEditor
                :model-value="item.body || ''"
                :lang="bodyLang(item.contentType)"
                :disabled="disabled"
                :show-mock-actions="false"
                @update:model-value="(v) => updateBody(item, v)"
              />
            </div>
          </div>
        </a-tab-pane>
      </a-tabs>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { IconPlus, IconDelete } from '@arco-design/web-vue/es/icon';
import { ApiResponseExample } from '@/types/domain/api/requestModel/ApiResponseExample';
import { RequestParameter } from '@/types/domain/api/requestModel/RequestParameter';
import BodyCodeEditor from './BodyCodeEditor.vue';

interface InternalExample extends ApiResponseExample {
  _key: string;
}

const props = defineProps<{
  examples?: ApiResponseExample[];
  disabled?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:examples', value: ApiResponseExample[]): void;
  (e: 'change'): void;
}>();

const localExamples = ref<InternalExample[]>([]);
const activeKey = ref('');
const dragSourceIndex = ref<number | null>(null);
const dragOverIndex = ref<number | null>(null);

const contentTypeOptions = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/xml', value: 'application/xml' },
  { label: 'text/xml', value: 'text/xml' },
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/html', value: 'text/html' },
];

const generateKey = () => {
  return Date.now().toString(36) + Math.random().toString(36).slice(2);
};

const wrapExamples = (list?: ApiResponseExample[]): InternalExample[] => {
  return (list || []).map((item) => ({
    ...item,
    _key: generateKey(),
  }));
};

const stripExamples = (list: InternalExample[]): ApiResponseExample[] => {
  return list.map(({ _key, ...rest }) => rest);
};

const syncExamples = () => {
  const incoming = JSON.stringify(props.examples || []);
  const current = JSON.stringify(stripExamples(localExamples.value));
  if (incoming !== current) {
    localExamples.value = wrapExamples(props.examples);
    if (localExamples.value.length) {
      activeKey.value = localExamples.value[0]._key;
    } else {
      activeKey.value = '';
    }
  }
};

watch(() => props.examples, syncExamples, { immediate: true });

const displayExamples = computed(() => {
  return localExamples.value;
});

watch(
  () => displayExamples.value,
  (list) => {
    if (list.length && !list.find((item) => item._key === activeKey.value)) {
      activeKey.value = list[0]._key;
    }
  },
  { immediate: true }
);

const tabTitle = (item: InternalExample) => {
  return `${item.statusCode} ${item.description || ''}`;
};

const bodyLang = (contentType?: string) => {
  if (!contentType) return 'json';
  if (contentType.includes('json')) return 'json';
  if (contentType.includes('xml')) return 'xml';
  if (contentType.includes('javascript') || contentType.includes('script')) return 'javascript';
  return 'json';
};

const emitChange = () => {
  emit('update:examples', JSON.parse(JSON.stringify(stripExamples(localExamples.value))));
  emit('change');
};

const onChange = () => {
  emitChange();
};

const updateBody = (item: InternalExample, value: string) => {
  item.body = value;
  emitChange();
};

const addExample = () => {
  const example: InternalExample = {
    statusCode: 200,
    description: '成功',
    contentType: 'application/json',
    headers: [],
    body: '{}',
    bodyMode: 'RAW',
    _key: generateKey(),
  };
  localExamples.value.push(example);
  activeKey.value = example._key;
  emitChange();
};

const removeExample = (item: InternalExample) => {
  const index = localExamples.value.findIndex((e) => e._key === item._key);
  if (index > -1) {
    localExamples.value.splice(index, 1);
  }
  emitChange();
};

const addHeader = (item: InternalExample) => {
  if (!item.headers) {
    item.headers = [];
  }
  const header = new RequestParameter();
  header.name = 'X-Header';
  header.value = '';
  item.headers.push(header);
  emitChange();
};

const removeHeader = (item: InternalExample, hIndex: number) => {
  item.headers?.splice(hIndex, 1);
  emitChange();
};

const handleDragStart = (index: number) => {
  dragSourceIndex.value = index;
};

const handleDragOver = (index: number) => {
  dragOverIndex.value = index;
};

const handleDrop = (targetIndex: number) => {
  const sourceIndex = dragSourceIndex.value;
  if (sourceIndex === null || sourceIndex === targetIndex) {
    dragSourceIndex.value = null;
    dragOverIndex.value = null;
    return;
  }

  const list = [...localExamples.value];
  const [moved] = list.splice(sourceIndex, 1);
  list.splice(targetIndex, 0, moved);

  localExamples.value = list;
  dragSourceIndex.value = null;
  dragOverIndex.value = null;
  emitChange();
};

const handleDragEnd = () => {
  dragSourceIndex.value = null;
  dragOverIndex.value = null;
};
</script>

<style scoped>
.response-examples-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 12px;
}

.response-examples-toolbar {
  margin-bottom: 12px;
}

.response-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.response-tabs :deep(.arco-tabs-content) {
  padding: 12px 0 0 0;
}

.response-form {
  margin-bottom: 16px;
}

.response-form-row {
  display: flex;
  align-items: flex-end;
  gap: 16px;
}

.compact-form-item {
  margin-bottom: 0;
}

.compact-form-item :deep(.arco-form-item-label) {
  padding-bottom: 4px;
  font-size: 12px;
}

.flex-1 {
  flex: 1;
}

.status-code-form-item {
  width: 120px;
}

.status-code-input {
  width: 100%;
}

.content-type-form-item {
  width: 280px;
}

.content-type-select {
  width: 100%;
}

.description-form-item {
  flex: 1;
  min-width: 200px;
}

.description-input {
  width: 100%;
}

.response-section {
  margin-bottom: 16px;
}

.section-title {
  font-weight: 500;
  margin-bottom: 8px;
  color: var(--color-text-1);
}

.header-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 8px;
}

.header-row {
  display: flex;
  gap: 8px;
  align-items: center;
}

.header-input {
  flex: 1;
}

.response-body-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.response-body-editor {
  height: 360px;
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  overflow: hidden;
}

.mini-empty :deep(.arco-empty-description) {
  color: var(--color-text-3);
  font-size: 12px;
}

.example-tab-title {
  display: inline-block;
  padding: 2px 6px;
  cursor: grab;
  user-select: none;
  border-radius: 4px;
  transition: background 0.15s, transform 0.15s, box-shadow 0.15s;
}

.example-tab-title[draggable="false"] {
  cursor: default;
}

.example-tab-title:active {
  cursor: grabbing;
}

.example-tab-title.is-dragging {
  opacity: 0.9;
  background: var(--color-primary-light-1);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  transform: scale(1.05);
}

.example-tab-title.is-drag-over {
  background: var(--color-primary-light-1);
  box-shadow: inset 0 -2px 0 rgb(var(--primary-6));
}
</style>
