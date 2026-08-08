<template>
  <a-drawer
      :width="drawerWidth"
      :visible="visible"
      @ok="handleOk"
      @cancel="handleCancel"
      :ok-text="okText"
      :cancel-text="cancelText"
      :mask-closable="maskClosable"
      :ok-button-props="{ disabled: submitDisabled }"
  >
    <template #title>
      <a-badge v-if="formData.orderIndex" :count="formData.orderIndex"/>
      {{title}}
    </template>
    <a-space direction="vertical" fill>
      <a-scrollbar :style="`height:${scrollHeight};overflow-y:auto;overflow-x:hidden`">
        <!-- API请求步骤使用专用编辑器 -->
        <ApiRequestStepEditor
            v-if="visible && isApiRequestStep"
            ref="apiRequestStepEditorRef"
            v-model="localStepDetail"
            :scene-environment-id="sceneEnvironmentId"
            :disabled="disabled"
            :editor-height="scrollHeight"
        />
        <!-- SQL步骤使用专用编辑器 -->
        <SqlRequestStepEditor
            v-else-if="visible && isSqlStep"
            ref="sqlRequestStepEditorRef"
            v-model="localStepDetail"
            :scene-environment-id="sceneEnvironmentId"
            :disabled="disabled"
            :editor-height="scrollHeight"
        />
        <!-- 脚本步骤使用专用编辑器 -->
        <ScriptStepEditor
            v-else-if="visible && isScriptStep"
            ref="scriptStepEditorRef"
            v-model="localStepDetail"
            :disabled="disabled"
            :editor-height="scrollHeight"
        />
        <!-- 其他步骤使用动态表单 + 设置标签页 -->
        <template v-else>
          <!-- 动态表单部分 -->
          <DynamicForm
              v-if="visible"
              ref="dynamicFormRef"
              :fields="formFields"
              :schemas="schema"
              v-model="localStepDetail"
              :disabled="disabled"
              @change="handleFormChange"
          />
          <!-- 设置标签页部分 -->
          <SettingTab
              v-if="showSettingTab"
              ref="settingTabRef"
              :current-step="localStepDetail.stepType"
              :assert-list="localStepDetail.assertList"
              :extract-list="localStepDetail.extractList"
              :setting="localStepDetail.setting"
              :disabled="disabled"
              @update:assert-list="handleAssertListUpdate"
              @update:extract-list="handleExtractListUpdate"
          />
        </template>
      </a-scrollbar>

    </a-space>
  </a-drawer>
</template>

<script setup lang="ts">
import {ref, watch, computed} from 'vue';
import {Modal} from '@arco-design/web-vue';
import DynamicForm from './DynamicForm.vue';
import SettingTab from './SettingTab.vue';
import ApiRequestStepEditor from './ApiRequestStepEditor.vue';
import SqlRequestStepEditor from './SqlRequestStepEditor.vue';
import ScriptStepEditor from './ScriptStepEditor.vue';
import {StepType} from "@/types/dto/StepDetailDTO";

const props = defineProps({
  // 抽屉基础属性
  width: {
    type: [String, Number],
    default: 340
  },
  visible: {
    type: Boolean,
    required: true
  },
  title: {
    type: String,
    default: '操作详情'
  },
  okText: {
    type: String,
    default: '确定'
  },
  cancelText: {
    type: String,
    default: '取消'
  },
  maskClosable: {
    type: Boolean,
    default: true
  },
  scrollHeight: {
    type: String,
    default: '80vh'
  },

  // 表单相关属性
  formFields: {
    type: Array,
    default: () => []
  },
  formData: {
    type: Object as () => StepType,
    required: true
  },
  schema: {
    type: Object,
    default: null
  },

  // 设置标签页属性
  showSettingTab: {
    type: Boolean,
    default: true
  },
  /**
   * 场景关联的环境ID
   * 传入后，API请求步骤编辑器中的环境选择会自动选中该环境
   */
  sceneEnvironmentId: {
    type: Number,
    default: undefined
  },
  /**
   * 是否禁用确定按钮（无保存权限时只读）
   */
  submitDisabled: {
    type: Boolean,
    default: false
  },
  /**
   * 是否只读模式（无保存权限时禁用子表单）
   */
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:visible', 'submit', 'cancel', 'update:formData', 'validate']);

// 本地数据副本
const localStepDetail = ref<StepType>({...props.formData});

// 是否为API请求步骤
const isApiRequestStep = computed(() => localStepDetail.value?.stepType === 'API_REQUEST');

// 是否为SQL步骤
const isSqlStep = computed(() => localStepDetail.value?.stepType === 'SQL');

// 是否为脚本步骤
const isScriptStep = computed(() => localStepDetail.value?.stepType === 'SCRIPT');

// 是否使用专用编辑器（API请求 / SQL / 脚本）
const useDedicatedEditor = computed(() => isApiRequestStep.value || isSqlStep.value || isScriptStep.value);

// 抽屉宽度：API请求/SQL步骤需要更宽的空间
const drawerWidth = computed(() => {
  if (useDedicatedEditor.value) {
    return 1100;
  }
  return props.width;
});

const dynamicFormRef = ref();
const settingTabRef = ref();
const apiRequestStepEditorRef = ref();
const sqlRequestStepEditorRef = ref();
const scriptStepEditorRef = ref();

// 当前专用编辑器 ref（API / SQL / 脚本）
const currentEditorRef = computed(() => {
  if (isSqlStep.value) return sqlRequestStepEditorRef;
  if (isScriptStep.value) return scriptStepEditorRef;
  return apiRequestStepEditorRef;
});

// 处理断言列表更新
const handleAssertListUpdate = (newList: any[]) => {
  localStepDetail.value.assertList = newList;
  emit('update:formData', localStepDetail.value);
};

// 处理提取列表更新
const handleExtractListUpdate = (newList: any[]) => {
  localStepDetail.value.extractList = newList;
  emit('update:formData', localStepDetail.value);
};

// 处理确定按钮
const handleOk = async () => {
  // API请求/SQL/脚本步骤使用专用编辑器校验
  if (useDedicatedEditor.value) {
    const r = await currentEditorRef.value.value?.validate();
    if (r?.valid) {
      emit('submit', r.data);
      emit('update:visible', false);
    }
    return;
  }

  // 其他步骤使用动态表单 + 设置标签页校验
  const r1 = await dynamicFormRef.value.validate();
  const r2 = props.showSettingTab ? await settingTabRef.value?.validate() : {valid: true, data: {}};
  if (r1.valid && r2.valid) {
    emit('submit', {
      ...r1.data,
      ...(r2.data || {})
    });
    emit('update:visible', false);
  }
};

// 处理取消按钮：API/SQL/脚本步骤有未保存修改时二次确认（发送 ≠ 保存，防止误关丢改动）
const handleCancel = () => {
  if (useDedicatedEditor.value) {
    if (currentEditorRef.value.value?.isDirty?.()) {
      Modal.confirm({
        title: '未保存的修改',
        content: '当前步骤有未保存的修改，关闭后将丢失，确定关闭吗？',
        okText: '放弃修改',
        okButtonProps: {status: 'danger'},
        cancelText: '继续编辑',
        onOk: () => {
          localStepDetail.value = {...props.formData};
          emit('cancel');
          emit('update:visible', false);
        },
      });
      return;
    }
  }
  // 重置为原始数据
  localStepDetail.value = {...props.formData};
  emit('cancel');
  emit('update:visible', false);
};

// 校验
const validate = () => {
  if (useDedicatedEditor.value) {
    return currentEditorRef.value.value?.validate();
  }
  // 校验主表单
  const {stepIsValid, data} = dynamicFormRef.value.validate();
  console.log("校验结果", stepIsValid, data)
  // 校验设置表单
  const {isValid, settingdata} = settingTabRef.value.validate();
  console.log("校验结果", isValid, settingdata)
}


const handleFormChange = (newValue: any) => {
  // console.log("seetingChange", newValue);
}


// 暴露方法给父组件
defineExpose({
  getFormData: () => {
    if (useDedicatedEditor.value) {
      return {
        ...localStepDetail.value,
        stepDetail: currentEditorRef.value.value?.getFormData()
      };
    }
    return {
      ...localStepDetail.value,
      stepDetail: dynamicFormRef.value?.getFormData()
    };
  },
  resetForm: () => {
    localStepDetail.value = {...props.formData};
    if (useDedicatedEditor.value) {
      currentEditorRef.value.value?.resetForm();
    } else {
      dynamicFormRef.value?.resetForm();
    }
  },
  getSettingTabRef: () => settingTabRef.value
});

// 监听draw的显隐状态
watch(() => props.visible, (newVal) => {
  // 清空setting和其他tab的校验状态
  if (!useDedicatedEditor.value) {
    if (settingTabRef.value) {
      settingTabRef.value.clearErrors();
    }
    if (dynamicFormRef.value) {
      dynamicFormRef.value.clearErrors();
    }
  }
  if (newVal) {
    // 打开时同步外部数据
    localStepDetail.value = {...props.formData};
  } else {
    // 关闭时重置为初始状态
    localStepDetail.value = {...props.formData};
  }
})


// 监听外部数据变化
watch(() => props.formData, (newVal) => {
  localStepDetail.value = {...newVal};
}, {deep: true, immediate: true});

</script>
<style scoped>
/* 横向截断：a-space 子项默认 min-width:auto，宽内容（多列 SQL 结果表格）会撑破抽屉，
   截断后由编辑器内部的 overflow + vxe-table scroll-x 接管 */
:deep(.arco-space-item) {
  min-width: 0;
}
</style>
