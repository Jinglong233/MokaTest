<template>
  <div class="script-step-editor" :style="{ height: editorHeight }">
    <a-form :model="localData" layout="vertical" auto-label-width>
      <a-form-item
          label="步骤名称"
          field="stepName"
          :rules="[{ required: true, message: '步骤名称不能为空' }]"
          :validate-trigger="['change', 'input']"
      >
        <a-input
            v-model="localData.stepName"
            placeholder="请输入步骤名称"
            allow-clear
            :disabled="disabled"
            @input="formDirty = true"
        />
      </a-form-item>

      <a-form-item label="脚本内容（JavaScript，在场景变量池上下文中执行）">
        <div class="script-editor-wrap">
          <div class="script-code-pane">
            <BodyCodeEditor
                v-model="localData.scriptContent"
                lang="javascript"
                :disabled="disabled"
                :show-mock-actions="false"
                @change="handleCodeChange"
            />
          </div>
          <div class="script-help-pane">
            <div class="help-title">可用 API</div>
            <div class="help-item" v-for="item in helpItems" :key="item.code">
              <code class="help-code" @click="copyHelp(item.code)">{{ item.code }}</code>
              <div class="help-desc">{{ item.desc }}</div>
            </div>
          </div>
        </div>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import {reactive, ref, watch} from 'vue';
import {Message} from '@arco-design/web-vue';
import BodyCodeEditor from '@/views/apiManager/component/BodyCodeEditor.vue';

interface ScriptStepData {
  stepName?: string;
  stepType?: string;
  scriptContent?: string;
  [key: string]: any;
}

const props = withDefaults(defineProps<{
  modelValue: ScriptStepData;
  /** 是否只读 */
  disabled?: boolean;
  /** 编辑器根高度（由抽屉传入其视口高度，默认 80vh） */
  editorHeight?: string;
}>(), {
  editorHeight: '80vh',
});

const emit = defineEmits<{
  (e: 'update:modelValue', value: ScriptStepData): void;
}>();

// 本地数据
const localData = reactive<ScriptStepData>({
  stepName: '',
  scriptContent: '',
});

// 表单是否有未保存修改（用于抽屉关闭时的未保存确认）
const formDirty = ref(false);

// 初始化数据（保留原始数据中的所有字段）
const initData = (data: ScriptStepData) => {
  Object.assign(localData, {
    ...data,
    stepName: data.stepName || '',
    scriptContent: data.scriptContent || '',
  });
  formDirty.value = false;
};

watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    initData(newVal);
  }
}, {deep: true, immediate: true});

const handleCodeChange = () => {
  formDirty.value = true;
  emit('update:modelValue', {...props.modelValue, ...localData});
};

const helpItems = [
  {code: 'context.getVariable("token")', desc: '读取场景变量'},
  {code: 'context.setVariable("token", "abc")', desc: '写入场景变量，后续步骤可用 ${token} 引用'},
  {code: 'console.log("日志")', desc: '输出日志，执行结果中可见'},
  {code: 'context.assertCondition(1 + 1 === 2, "断言提示")', desc: '自定义断言，失败则步骤失败'},
  {code: 'context.utils.md5("text")', desc: '工具函数：md5/sha256/base64/uuid/mock/template 等'},
  {code: 'fn.函数名("参数")', desc: '调用项目自定义函数（接口测试 → 自定义函数 中维护）'},
];

const copyHelp = async (text: string) => {
  try {
    await navigator.clipboard.writeText(text);
    Message.success({content: '已复制', duration: 1000});
  } catch (e) {
    // 剪贴板不可用时静默
  }
};

// 校验方法
const validate = async () => {
  if (!localData.stepName || !localData.stepName.trim()) {
    return {valid: false, errors: {stepName: ['步骤名称不能为空']}};
  }
  return {valid: true, data: JSON.parse(JSON.stringify(localData))};
};

// 获取表单数据
const getFormData = () => JSON.parse(JSON.stringify(localData));

// 重置表单
const resetForm = () => {
  initData(props.modelValue);
};

defineExpose({
  validate,
  getFormData,
  resetForm,
  // 是否有未保存修改（抽屉关闭确认用）
  isDirty: () => formDirty.value,
});
</script>

<style scoped>
.script-step-editor {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.script-step-editor :deep(.arco-form) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.script-step-editor :deep(.arco-form-item:last-child) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.script-step-editor :deep(.arco-form-item:last-child .arco-form-item-wrapper) {
  flex: 1;
  min-height: 0;
}

.script-step-editor :deep(.arco-form-item:last-child .arco-form-item-content) {
  height: 100%;
}

.script-editor-wrap {
  height: 100%;
  min-height: 320px;
  display: flex;
  gap: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  overflow: hidden;
}

.script-code-pane {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.script-code-pane :deep(.body-code-editor) {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.script-code-pane :deep(.editor-container) {
  flex: 1;
  min-height: 0;
}

.script-help-pane {
  width: 280px;
  flex-shrink: 0;
  border-left: 1px solid var(--color-border-2);
  padding: 10px 12px;
  overflow-y: auto;
  background: var(--color-fill-1);
}

.help-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--color-text-1);
}

.help-item {
  margin-bottom: 10px;
}

.help-code {
  font-size: 12px;
  color: rgb(var(--primary-6));
  cursor: pointer;
  word-break: break-all;
}

.help-code:hover {
  text-decoration: underline;
}

.help-desc {
  font-size: 12px;
  color: var(--color-text-3);
  margin-top: 2px;
}
</style>
