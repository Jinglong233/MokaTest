<template>
  <a-form
      ref="formRef"
      :model="formData"
      :layout="props.layout"
      :label-align="props.labelAlign"
      auto-label-width
      :validate-trigger="['change', 'input']"
  >
    <!-- 动态渲染表单字段 -->
    <template v-for="field in visibleFields" :key="field.key">
      <a-form-item
          :label="field.label"
          :field="field.key"
          v-if="shouldShowField(field)"
          :required="field.type === 'elementSelect' || field.type === 'uploadFile' ? false : field.required"
          :rules="field.type === 'elementSelect' ? elementSelectRules : field.type === 'uploadFile' ? uploadFileRules : field.rules"
      >
        <!-- 文本输入（步骤名称除外的值字段支持 fn.名称() 自定义函数表达式，输入 @/fn. 弹出函数辅助下拉） -->
        <a-input
            v-if="field.type === 'input' && field.key === 'stepName'"
            v-model="formData[field.key]"
            :default-value="field.default"
            :placeholder="field.placeholder"
            :allow-clear="field.allowClear"
            :disabled="disabled"
            @blur="handleFieldUpdate"
        />
        <ExprSuggestInput
            v-else-if="field.type === 'input'"
            :model-value="formData[field.key]"
            :placeholder="field.placeholder"
            :allow-clear="field.allowClear"
            :disabled="disabled"
            @update:model-value="(val) => { formData[field.key] = val; }"
            @commit="handleFieldUpdate"
        />

        <!-- 数字输入 -->
        <a-input-number
            v-else-if="field.type === 'number'"
            v-model="formData[field.key]"
            :default-value="field.default"
            :placeholder="field.placeholder"
            :min="field.min"
            :max="field.max"
            :precision="field.precision || 0"
            :disabled="disabled"
            @blur="handleFieldUpdate"
        />

        <!-- 下拉选择 -->
        <a-select
            v-else-if="field.type === 'select'"
            v-model="formData[field.key]"
            :default-value="field.default"
            :placeholder="field.placeholder"
            :options="field.options"
            :multiple="field.multiple"
            :allow-clear="field.allowClear"
            :disabled="disabled"
            @change="(val) => {
              handleFieldChange(field);
              handleFieldUpdate();
            }"
        />

        <!-- 单选按钮 -->
        <a-radio-group
            v-else-if="field.type === 'radio'"
            v-model="formData[field.key]"
            :default-value="field.default"
            :options="field.options"
            :disabled="disabled"
            @change="handleFieldUpdate"
        />

        <!-- 多选框 -->
        <a-checkbox-group
            v-else-if="field.type === 'checkbox'"
            v-model="formData[field.key]"
            :default-value="field.default"
            :options="field.options"
            :disabled="disabled"
            @change="handleFieldUpdate"
        />

        <!-- 开关 -->
        <a-switch
            v-else-if="field.type === 'switch'"
            v-model="formData[field.key]"
            :default-value="field.default"
            :checked-value="field.checkedValue || 1"
            :unchecked-value="field.uncheckedValue || 0"
            :disabled="disabled"
            @change="handleFieldUpdate"
        />

        <!-- 日期选择 -->
        <a-date-picker
            v-else-if="field.type === 'date'"
            v-model="formData[field.key]"
            :placeholder="field.placeholder"
            :show-time="field.showTime"
            :disabled="disabled"
            @change="handleFieldUpdate"
        />

        <!-- 标签输入（字符串数组，如 CSS 类名列表） -->
        <a-input-tag
            v-else-if="field.type === 'inputTag'"
            v-model="formData[field.key]"
            :placeholder="field.placeholder"
            :disabled="disabled"
            allow-clear
            @change="handleFieldUpdate"
        />

        <!-- 元素选择-->
        <ElementSelect
            v-else-if="field.type === 'elementSelect' && !compactElementSelect"
            v-model="formData[field.key]"
            :project-id="1"
            :disabled="disabled"
            @change="handleFieldUpdate"
        />

        <!-- 元素选择（紧凑：摘要 + 弹层，用于工作流节点内联） -->
        <InlineElementSelect
            v-else-if="field.type === 'elementSelect' && compactElementSelect"
            v-model="formData[field.key]"
            :disabled="disabled"
            @change="handleFieldUpdate"
        />

        <!--        文件上传组件-->
        <div v-else-if="field.type === 'uploadFile'" class="upload-file-field">
          <a-upload
              v-model:file-list="fileListMap[field.key]"
              :multiple="true"
              :disabled="disabled"
              draggable
              :custom-request="(option) => handleUpload(option, field)"
              @change="(files) => handleUploadChange(files, field)"
          />
          <div class="upload-size-hint">单个文件最大 10MB</div>
        </div>


        <AssertList v-else-if="field.type === 'assertList'"
                    ref="assertListRef"
                    v-model="formData[field.key]"
                    :disabled="disabled"
                    @change="handleFieldUpdate"/>


        <!--        <AssertList v-else-if="field.type === 'assertList'"
                            :ref="(el) => { assertListRef = el }"
                            v-model="formData[field.key]"
                            @change="handleFieldUpdate"/>-->

        <!-- 自定义插槽 -->
        <slot
            v-else-if="field.type === 'slot'"
            :name="field.slotName"
            :field="field"
            :value="formData[field.key]"
            @change="handleFieldUpdate"
        />

      </a-form-item>
    </template>
  </a-form>
</template>

<script setup>
import {ref, computed, watch, toRaw, reactive} from 'vue';
import ElementSelect from "@/views/scene/component/ElementSelect.vue";
import InlineElementSelect from "@/views/scene/component/InlineElementSelect.vue";
import AssertList from "@/views/scene/component/AssertList.vue";
import ExprSuggestInput from "@/views/apiManager/component/ExprSuggestInput.vue";
import {uploadFile} from "@/api/MyApi/fileUpload";
import {Message} from "@arco-design/web-vue";

const formRef = ref(null);

// 断言列表引用
const assertListRef = ref(null);


const props = defineProps({
  layout: {
    type: String,
    default: () => 'vertical',
  },
  labelAlign: {
    type: String,
    default: () => 'right',
  },
  // 表单配置
  fields: {
    type: Array,
    required: true,
    default: () => []
  },
  // 初始表单数据
  modelValue: {
    type: Object,
    default: () => ({}),
    required: true
  },
  // 校验规则
  schemas: {
    type: Object,
    default: () => ({})
  },
  // 是否禁用
  disabled: {
    type: Boolean,
    default: false
  },
  // 元素选择是否使用紧凑模式（摘要 + 弹层），用于工作流节点内联编辑
  compactElementSelect: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits(['update:modelValue', 'change']);

// 上传文件字段的文件列表状态（key: field.key, value: FileItem[]）
const fileListMap = ref({});

// 使用计算属性实现双向绑定
const formData = computed({
  get: () => props.modelValue,
  set: (value) => {
    emit('update:modelValue', {...value});
  }
});

// 所有字段（包含隐藏字段）
const allFields = ref([...props.fields]);

// 初始化表单数据（支持默认值）
const initializeFormData = () => {
  props.fields.forEach(field => {
    if (formData.value[field.key] === undefined) {
      formData.value[field.key] = getFieldDefaultValue(field);
    }
  });
  initUploadFileList();
  // 初始化后立即通知父组件
  emit('update:modelValue', {...formData.value});
};

// 获取字段默认值（三级优先级）
const getFieldDefaultValue = (field) => {
  // 1. 优先使用 modelValue 中的值
  if (props.modelValue && props.modelValue[field.key] !== undefined) {
    return props.modelValue[field.key];
  }

  // 2. 使用字段配置中的 defaultValue
  if (field.defaultValue !== undefined) {
    return field.defaultValue;
  }

  // 3. 根据字段类型返回合适的默认值
  return getTypeDefaultValue(field.type, field);
};

// 根据字段类型返回默认值
const getTypeDefaultValue = (type, field) => {
  switch (type) {
    case 'number':
      return null;
    case 'select':
      return field.multiple ? [] : null;
    case 'checkbox':
      return [];
    case 'radio':
      return null;
    case 'switch':
      return field.uncheckedValue || 0;
    case 'date':
      return null;
    case 'uploadFile':
      return [];
    default: // input/text/slot等
      return '';
  }
};

// 计算当前应该显示的字段
const visibleFields = computed(() => {
  return allFields.value.filter(field => shouldShowField(field));
});

// 判断字段是否应该显示
const shouldShowField = (field) => {
  if (!field.conditions) return true;

  // 检查是否有operator字段来确定逻辑类型
  if (field.operator === 'OR' || field.operator === 'or') {
    // OR逻辑：满足任意一个条件即可
    return field.conditions.some(condition => {
      const fieldValue = formData.value[condition.field];

      // 处理空值情况
      if (fieldValue === null || fieldValue === undefined) {
        return condition.allowNull || false;
      }

      switch (condition.type) {
        case 'equals':
          return fieldValue === condition.value;
        case 'notEquals':
          return fieldValue !== condition.value;
        case 'includes':
          return Array.isArray(fieldValue)
              ? fieldValue.includes(condition.value)
              : String(fieldValue).includes(String(condition.value));
        case 'notIncludes':
          return Array.isArray(fieldValue)
              ? !fieldValue.includes(condition.value)
              : !String(fieldValue).includes(String(condition.value));
        case 'in':
          return Array.isArray(condition.values)
              ? condition.values.includes(fieldValue)
              : fieldValue === condition.values;
        case 'notIn':
          return Array.isArray(condition.values)
              ? !condition.values.includes(fieldValue)
              : fieldValue !== condition.values;
        case 'between':
          return fieldValue >= condition.min && fieldValue <= condition.max;
        default:
          return true;
      }
    });
  } else {
    return field.conditions.every(condition => {
      const fieldValue = formData.value[condition.field];

      // 处理空值情况
      if (fieldValue === null || fieldValue === undefined) {
        return condition.allowNull || false;
      }

      switch (condition.type) {
        case 'equals':
          return fieldValue === condition.value;
        case 'notEquals':
          return fieldValue !== condition.value;
        case 'includes':
          return Array.isArray(fieldValue)
              ? fieldValue.includes(condition.value)
              : String(fieldValue).includes(String(condition.value));
        case 'notIncludes':
          return Array.isArray(fieldValue)
              ? !fieldValue.includes(condition.value)
              : !String(fieldValue).includes(String(condition.value));
        case 'in':
          return Array.isArray(condition.values)
              ? condition.values.includes(fieldValue)
              : fieldValue === condition.values;
        case 'notIn':
          return Array.isArray(condition.values)
              ? !condition.values.includes(fieldValue)
              : fieldValue !== condition.values;
        case 'between':
          return fieldValue >= condition.min && fieldValue <= condition.max;
        default:
          return true;
      }
    });
  }
};

// 处理字段值变化
const handleFieldChange = (field) => {
  if (field.dependentFields) {
    field.dependentFields.forEach(depField => {
      formData.value[depField] = null;
    });
  }
};

// 处理字段更新通知父组件
const handleFieldUpdate = () => {
  emit('update:modelValue', {...formData.value});
  emit('change', {...formData.value});
};

// 从 fileId 中提取文件名用于展示（去掉 UUID_ 前缀）
const extractFileName = (fileId) => {
  if (!fileId) return '';
  const lastSlash = Math.max(fileId.lastIndexOf('/'), fileId.lastIndexOf('\\'));
  const baseName = lastSlash >= 0 ? fileId.substring(lastSlash + 1) : fileId;
  return baseName.replace(
      /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}_/,
      ''
  );
};

// 初始化上传字段的文件列表
const initUploadFileList = () => {
  props.fields.forEach(field => {
    if (field.type === 'uploadFile') {
      const fileIds = formData.value[field.key] || [];
      fileListMap.value[field.key] = fileIds.map(fileId => ({
        uid: fileId,
        name: extractFileName(fileId),
        url: `/api/file/download?fileId=${encodeURIComponent(fileId)}`,
        status: 'done',
        fileId: fileId,
      }));
    }
  });
};

// 上传文件大小限制
const MAX_UPLOAD_FILE_SIZE = 10 * 1024 * 1024; // 10MB

// 上传文件
const handleUpload = async (option, field) => {
  try {
    // 前端文件大小校验
    if (option.fileItem.file.size > MAX_UPLOAD_FILE_SIZE) {
      Message.error('文件大小不能超过 10MB');
      option.onError(new Error('文件大小不能超过 10MB'));
      return;
    }
    const res = await uploadFile(option.fileItem.file);
    const fileId = res.data.fileId;
    // 直接给 fileListMap 中对应的 fileItem 写入 fileId，因为 onSuccess 不会自动合并自定义字段
    const list = fileListMap.value[field.key] || [];
    const target = list.find(item => item.uid === option.fileItem.uid);
    if (target) {
      target.fileId = fileId;
      target.name = res.data.fileName || option.fileItem.name;
      target.status = 'done';
    }
    option.onSuccess();
    // 主动同步 fileIds
    handleUploadChange(list, field);
    Message.success('上传成功');
  } catch (error) {
    console.error('上传失败:', error);
    option.onError(error);
    Message.error('上传失败');
  }
};

// 上传列表变化时同步 fileIds
const handleUploadChange = (fileList, field) => {
  const doneFiles = (fileList || []).filter(item => item.status === 'done' && item.fileId);
  formData.value[field.key] = doneFiles.map(item => item.fileId);
  handleFieldUpdate();
};

// 表单校验方法
const validate = async () => {
  try {

    // console.log('validate', assertListRef.value)
    // 单独校验断言组件的内容
    if (assertListRef.value && assertListRef.value.length > 0) {
      const assertResult = await assertListRef.value[0].validate?.();

      if (assertResult && !assertResult.valid) {
        return {valid: false, errors: assertResult.errors};
      }
    }

    const errors = await formRef.value?.validate();
    if (errors) return {valid: false, errors};

    return {valid: true, data: toRaw(formData.value)};
  } catch (error) {
    console.error('验证出错:', error);
    return {valid: false, errors: {_: ['验证过程中发生错误']}};
  }
};
// 表单校验方法
/*const validate = async () => {
  try {
    console.log('234',assertListRef.value)
    console.log('234',assertListRef.value.length)

    // 单独校验断言组件的内容
    if (assertListRef.value && assertListRef.value.length > 0) {
      const assertResults = await Promise.all(
          assertListRef.value.map(async (assertForm) => {
            if (assertForm && typeof assertForm.validate === 'function') {
              return await assertForm.validate();
            }
            return { valid: true };
          })
      );

      // 检查是否有任何一个断言校验失败
      const failedResult = assertResults.find(result => result && !result.valid);
      if (failedResult) {
        return {valid: false, errors: failedResult.errors || {}};
      }
    }

    const errors = await formRef.value?.validate();
    if (errors) return {valid: false, errors: errors};

    return {valid: true, data: toRaw(formData.value)};
  } catch (error) {
    console.error('验证出错:', error);
    return {valid: false, errors: {_: ['验证过程中发生错误']}};
  }
};*/

// 获取表单数据（不验证）
const getFormData = () => {
  return toRaw(formData.value);
};

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields();
  initializeFormData();
};

// 清除校验状态
const clearErrors = () => {
  formRef.value?.clearValidate?.();
}

// 暴露方法给父组件
defineExpose({
  validate,
  getFormData,
  resetForm,
  clearErrors
});

// 初始化表单数据
initializeFormData();

// 监听字段配置变化
watch(() => props.fields, (newFields) => {
  allFields.value = [...newFields];
  initializeFormData();
}, {deep: true});

// 监听modelValue变化
watch(() => props.modelValue, (newValue, oldValue) => {
  // 比较 fileIds 是否变化，避免无意义同步
  const newFileIds = newValue?.fileIds || [];
  const oldFileIds = oldValue?.fileIds || [];
  if (JSON.stringify(newFileIds) !== JSON.stringify(oldFileIds)) {
    Object.keys(formData.value).forEach(key => {
      formData.value[key] = newValue[key];
    });
    initUploadFileList();
  }
}, {deep: true});

// 对元素选择器单独的校验规则
const elementSelectRules = [{
  validator: (value, cb) => {
    if (!value) {
      return cb('元素属性不能为空');
    }
    const isCustomValid =
        value.customLocator &&
        value.customLocator.locatorValue &&
        value.customLocator.locatorValue.trim() !== '' &&
        value.customLocator.locatorType &&
        value.customLocator.locatorType.trim() !== '';
    const isRegularValid =
        value.locator &&
        value.locator.locatorValue &&
        value.locator.locatorValue.trim() !== '';

    // 按定位来源标记校验：来源指向的一侧必须有值
    if (value.locatorSource === 'CUSTOM') {
      return isCustomValid ? cb() : cb('当前选择自定义元素，请填写定位方式和定位值');
    }
    if (value.locatorSource === 'LIBRARY') {
      return isRegularValid ? cb() : cb('当前选择库选元素，请选择元素');
    }
    // 历史数据无来源标记：任一侧有值即可
    if (isRegularValid || isCustomValid) {
      return cb();
    }
    return cb('必须提供有效的元素定位值或自定义元素定位值');
  }
}];

// 对上传文件字段的校验规则
const uploadFileRules = [{
  validator: (value, cb) => {
    if (!value || !Array.isArray(value) || value.length === 0) {
      return cb('请至少上传一个文件');
    }
    return cb();
  }
}];
</script>

<style scoped>
.upload-file-field {
  display: block;
}

.upload-file-field :deep(.arco-upload-drag) {
  width: 360px;
  margin: 0 auto;
}

.upload-file-field :deep(.arco-upload-list) {
  width: 90%;
  margin: 0 auto;
}

.upload-size-hint {
  text-align: center;
  font-size: 12px;
  color: rgb(var(--gray-6));
  margin-top: 4px;
}
</style>