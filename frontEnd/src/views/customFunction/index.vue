<template>
  <div class="custom-function-page" v-if="projectStore.getProjectId">
    <Breadcrumb :items="['menu.interfaceTest', 'menu.interfaceTest.customFunction']"/>
    <div class="main-row">
      <!-- 左侧函数列表 -->
      <div class="side-col">
        <a-card class="list-card" size="small">
          <template #title>
            <span>函数列表</span>
          </template>
          <div class="list-search-wrap">
            <a-input-search v-model="searchKeyword" placeholder="搜索函数" allow-clear/>
          </div>
          <div class="list-actions">
            <a-button v-permission="'auto:function:create'" type="primary" long @click="handleAdd">
              <template #icon>
                <icon-plus/>
              </template>
              新建函数
            </a-button>
          </div>
          <div class="list-scroll-wrapper">
            <a-scrollbar style="height: 100%; overflow-y: auto;">
              <a-empty v-if="filteredList.length === 0" description="暂无函数"/>
              <div
                  v-for="item in filteredList"
                  :key="item.id"
                  class="func-item"
                  :class="{ 'is-active': selectedId === item.id }"
                  @click="handleSelect(item)"
              >
                <div class="func-item-main">
                  <span class="func-item-name">{{ item.funcName }}</span>
                  <span class="func-item-signature">{{ buildSignature(item) }}</span>
                </div>
                <a-dropdown position="bottom" @click.stop>
                  <a-button type="text" size="mini" class="func-item-more" @click.stop>
                    <template #icon>
                      <icon-more/>
                    </template>
                  </a-button>
                  <template #content>
                    <a-doption v-permission="'auto:function:update'" @click.stop="handleCopyCall(item)">
                      复制调用表达式
                    </a-doption>
                    <a-doption v-permission="'auto:function:delete'" class="danger-option"
                               @click.stop="handleDelete(item)">
                      删除
                    </a-doption>
                  </template>
                </a-dropdown>
              </div>
            </a-scrollbar>
          </div>
        </a-card>
      </div>

      <!-- 右侧编辑区 -->
      <div class="content-col">
        <a-card class="editor-card" :title="editorTitle">
          <template #extra>
            <a-space v-if="editing">
              <a-button type="primary" :loading="saving" @click="handleSave()">保存</a-button>
              <a-button @click="handleCancel">取消</a-button>
            </a-space>
          </template>

          <a-empty v-if="!editing" description="从左侧选择函数，或点击「新建函数」"/>
          <div v-else class="editor-body">
            <a-form :model="form" layout="vertical" size="small">
              <a-row :gutter="16">
                <a-col :span="12">
                  <a-form-item label="函数名称" required>
                    <a-input v-model="form.funcName" placeholder="如：请求签名" :max-length="100"/>
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="参数定义（逗号分隔，可为空）">
                    <a-input v-model="form.funcParams" placeholder="如：text,key"/>
                  </a-form-item>
                </a-col>
              </a-row>
              <a-form-item label="描述">
                <a-input v-model="form.description" placeholder="函数用途说明（可选）" :max-length="500"/>
              </a-form-item>
              <a-form-item label="函数体（JavaScript，用 return 返回结果）">
                <div class="code-editor-wrap">
                  <BodyCodeEditor v-model="form.funcCode" lang="javascript" :show-mock-actions="false"/>
                </div>
              </a-form-item>
            </a-form>

            <!-- 试运行 -->
            <a-divider orientation="left">试运行</a-divider>
            <div class="test-run-panel">
              <div class="test-run-input-row">
                <a-input
                    v-model="testArgsInput"
                    placeholder='示例参数（JSON 数组），如 ["hello", 123]'
                    class="test-args-input"
                />
                <a-button type="outline" :loading="testRunning" @click="handleTestRun">运行</a-button>
              </div>
              <a-alert v-if="arityMismatchTip" type="warning" class="arity-tip">{{ arityMismatchTip }}</a-alert>
              <div v-if="testResult" class="test-run-result">
                <a-alert :type="testResult.success ? 'success' : 'error'" class="test-result-alert">
                  <template #title>
                    {{ testResult.success ? '执行成功' : '执行失败' }}
                    <span v-if="testResult.executionTimeMs != null" class="test-cost">
                      （{{ testResult.executionTimeMs }}ms）
                    </span>
                  </template>
                  <div v-if="testResult.success" class="test-result-value">{{ testResult.value }}</div>
                  <div v-else class="test-result-error">{{ testResult.errorMessage }}</div>
                </a-alert>
                <pre v-if="testResult.consoleLogs?.length" class="test-logs">{{
                    testResult.consoleLogs.join('\n')
                  }}</pre>
              </div>
            </div>
          </div>
        </a-card>
      </div>
    </div>
  </div>
  <NoProjectPlaceholder v-else/>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'CustomFunction' };
</script>

<script setup lang="ts">
import {computed, reactive, ref, watch} from 'vue';
import {Message, Modal} from '@arco-design/web-vue';
import {IconMore, IconPlus} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import BodyCodeEditor from '@/views/apiManager/component/BodyCodeEditor.vue';
import {CustomFunction} from '@/types/domain/api/CustomFunction';
import {
  deleteCustomFunction,
  getCustomFunctionList,
  saveCustomFunction,
  testRunCustomFunction,
  CustomFunctionTestRunResult,
} from '@/api/MyApi/customFunction';
import useProjectStore from '@/store/modules/project';
import useTeamStore from '@/store/modules/team';

const projectStore = useProjectStore();
const teamStore = useTeamStore();

const functionList = ref<CustomFunction[]>([]);
const searchKeyword = ref('');
const selectedId = ref<number | null>(null);
const editing = ref(false);
const saving = ref(false);

const emptyForm = (): CustomFunction => ({
  funcName: '',
  funcParams: '',
  funcCode: '// 可用 context.utils 中的工具函数（md5/base64Encode/uuid/mock/template 等）\n// 示例：return context.utils.md5(text + key);\nreturn "";',
  description: '',
});
const form = reactive<CustomFunction>(emptyForm());
/** 已保存快照，用于未保存更改判断 */
let savedSnapshot = '';

const testArgsInput = ref('');
const testRunning = ref(false);
const testResult = ref<CustomFunctionTestRunResult | null>(null);

// 参数个数与定义不一致时的提示（不阻断运行，JS 语义：少传为 undefined，多传忽略）
const arityMismatchTip = computed(() => {
  const declared = (form.funcParams || '').split(',').map(p => p.trim()).filter(Boolean).length;
  const input = testArgsInput.value.trim();
  if (!input) return '';
  try {
    const parsed = JSON.parse(input);
    if (!Array.isArray(parsed)) return '';
    if (parsed.length === declared) return '';
    return `参数个数不一致：函数定义了 ${declared} 个参数，示例传了 ${parsed.length} 个（少传为 undefined，多传忽略）`;
  } catch (e) {
    return '';
  }
});

const filteredList = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase();
  if (!kw) return functionList.value;
  return functionList.value.filter(f =>
      (f.funcName || '').toLowerCase().includes(kw) ||
      (f.description || '').toLowerCase().includes(kw));
});

const editorTitle = computed(() => {
  if (!editing.value) return '函数详情';
  return selectedId.value ? `编辑函数：${form.funcName || ''}` : '新建函数';
});

const buildSignature = (item: CustomFunction) =>
    `fn.${item.funcName}(${item.funcParams || ''})`;

const snapshot = () => JSON.stringify({
  funcName: form.funcName,
  funcParams: form.funcParams,
  funcCode: form.funcCode,
  description: form.description,
});

const isDirty = () => editing.value && snapshot() !== savedSnapshot;

const loadList = async () => {
  const projectId = projectStore.getProjectId;
  if (!projectId) return;
  try {
    const res = await getCustomFunctionList(Number(projectId));
    functionList.value = res.data || [];
  } catch (e) {
    functionList.value = [];
  }
};

const resetEditor = () => {
  editing.value = false;
  selectedId.value = null;
  Object.assign(form, emptyForm());
  savedSnapshot = '';
  testResult.value = null;
  testArgsInput.value = '';
};

const confirmDiscardIfDirty = (): Promise<boolean> => {
  if (!isDirty()) return Promise.resolve(true);
  return new Promise(resolve => {
    Modal.confirm({
      title: '未保存的修改',
      content: '当前函数有未保存的修改，确定要放弃吗？',
      okText: '放弃修改',
      okButtonProps: {status: 'danger'},
      cancelText: '继续编辑',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
};

const fillForm = (item: CustomFunction) => {
  Object.assign(form, {
    id: item.id,
    funcName: item.funcName || '',
    funcParams: item.funcParams || '',
    funcCode: item.funcCode || '',
    description: item.description || '',
    sort: item.sort,
  });
  savedSnapshot = snapshot();
};

const handleSelect = async (item: CustomFunction) => {
  if (selectedId.value === item.id && editing.value) return;
  if (!(await confirmDiscardIfDirty())) return;
  selectedId.value = item.id!;
  editing.value = true;
  fillForm(item);
  testResult.value = null;
};

const handleAdd = async () => {
  if (!(await confirmDiscardIfDirty())) return;
  selectedId.value = null;
  editing.value = true;
  Object.assign(form, emptyForm());
  delete form.id;
  savedSnapshot = snapshot();
  testResult.value = null;
};

const handleSave = async (showTip = true): Promise<boolean> => {
  if (!form.funcName?.trim()) {
    Message.warning('请填写函数名称');
    return false;
  }
  if (!form.funcCode?.trim()) {
    Message.warning('请填写函数体');
    return false;
  }
  saving.value = true;
  try {
    const res = await saveCustomFunction({
      ...form,
      projectId: projectStore.getProjectId ?? undefined,
      teamId: teamStore.getTeamId ?? undefined,
    });
    const saved = res.data as CustomFunction;
    if (saved?.id) {
      selectedId.value = saved.id;
      fillForm(saved);
    } else {
      savedSnapshot = snapshot();
    }
    if (showTip) Message.success('保存成功');
    await loadList();
    return true;
  } catch (e) {
    return false;
  } finally {
    saving.value = false;
  }
};

const handleCancel = async () => {
  if (!(await confirmDiscardIfDirty())) return;
  resetEditor();
};

const handleDelete = (item: CustomFunction) => {
  Modal.confirm({
    title: '删除函数',
    content: `确定删除函数「${item.funcName}」吗？已引用该函数的 {{__CUSTOM(${item.id})__}} 表达式将失效。`,
    okText: '删除',
    okButtonProps: {status: 'danger'},
    onOk: async () => {
      await deleteCustomFunction(item.id!);
      Message.success('删除成功');
      if (selectedId.value === item.id) {
        resetEditor();
      }
      await loadList();
    },
  });
};

const handleCopyCall = async (item: CustomFunction) => {
  const text = buildSignature(item);
  try {
    await navigator.clipboard.writeText(text);
  } catch (e) {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    document.body.appendChild(textarea);
    textarea.select();
    document.execCommand('copy');
    document.body.removeChild(textarea);
  }
  Message.success('已复制调用表达式');
};

const handleTestRun = async () => {
  if (!selectedId.value) {
    Message.warning('请先保存函数再试运行');
    return;
  }
  let args: any[] = [];
  if (testArgsInput.value.trim()) {
    try {
      const parsed = JSON.parse(testArgsInput.value);
      if (!Array.isArray(parsed)) {
        Message.warning('示例参数必须是 JSON 数组，如 ["hello", 123]');
        return;
      }
      args = parsed;
    } catch (e) {
      Message.warning('示例参数 JSON 格式不正确');
      return;
    }
  }
  testRunning.value = true;
  testResult.value = null;
  try {
    // 试运行前先保存，保证跑的是当前编辑内容
    if (isDirty()) {
      const saved = await handleSave(false);
      if (!saved) return;
    }
    const res = await testRunCustomFunction(selectedId.value, args);
    testResult.value = res.data;
  } finally {
    testRunning.value = false;
  }
};

watch(() => projectStore.getProjectId, async () => {
  if (isDirty()) {
    // 切项目时直接放弃未保存修改（与项目数据边界一致，避免跨项目误保存）
    Message.info('已放弃未保存的修改');
  }
  resetEditor();
  await loadList();
}, {immediate: true});
</script>

<style scoped>
.custom-function-page {
  padding: 0 20px 20px;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.main-row {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}

.side-col {
  width: 300px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
}

.list-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.list-card :deep(.arco-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.list-search-wrap {
  margin-bottom: 8px;
}

.list-actions {
  margin-bottom: 8px;
}

.list-scroll-wrapper {
  flex: 1;
  min-height: 0;
}

.func-item {
  display: flex;
  align-items: center;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  gap: 4px;
}

.func-item:hover {
  background: var(--color-fill-2);
}

.func-item.is-active {
  background: rgb(var(--primary-1));
}

.func-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.func-item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.func-item-signature {
  font-size: 12px;
  color: var(--color-text-3);
  font-family: monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.func-item-more {
  flex-shrink: 0;
}

.danger-option {
  color: rgb(var(--red-6));
}

.content-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.editor-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.editor-card :deep(.arco-card-body) {
  flex: 1;
  overflow-y: auto;
}

.editor-body {
  min-width: 0;
}

.code-editor-wrap {
  width: 100%;
  min-width: 0;
  height: 320px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  overflow: hidden;
}

.code-editor-wrap :deep(.body-code-editor) {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  height: 100%;
  display: flex;
  flex-direction: column;
}

.code-editor-wrap :deep(.editor-container) {
  flex: 1;
  min-height: 0;
}

.test-run-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.arity-tip {
  margin-bottom: 0;
}

.test-run-input-row {
  display: flex;
  gap: 8px;
}

.test-args-input {
  flex: 1;
}

.test-result-alert {
  margin-bottom: 8px;
}

.test-cost {
  color: var(--color-text-3);
  font-size: 12px;
}

.test-result-value {
  font-family: monospace;
  word-break: break-all;
  white-space: pre-wrap;
}

.test-result-error {
  word-break: break-all;
  white-space: pre-wrap;
}

.test-logs {
  margin: 0;
  padding: 10px;
  background: var(--color-fill-1);
  border-radius: 6px;
  font-size: 12px;
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
