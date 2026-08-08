<template>

  <!--公共设置tab-->
  <a-tabs type="line" size="mini">
    <a-tab-pane key="1" title="设置">
      <DynamicForm
          :layout="'horizontal'"
          :label-align="'left'"
          ref="settingFormRef"
          :fields="StepSetting"
          :schemas="extractSchema"
          :model-value="localSetting"
          :disabled="disabled"/>
    </a-tab-pane>
    <a-tab-pane key="2" v-if="excludeAssertFields">
      <template #title>
        <a-badge
            :count="localAssertList.length"
            :offset="[10, -2]"
            :dot-style="{ background: '#E5E6EB', color: '#86909C' }"
        >
          断言
        </a-badge>
      </template>
      <a-collapse>
        <a-collapse-item
            v-for="(asse, index) in localAssertList"
            :key="`assert-${index}`"
            :class="{ 'error-item': assertErrors[index] }"
        >
          <template #header>
            <span :class="{ 'error-text': assertErrors[index] }">
              断言 {{ index + 1 }}
              <a-tag v-if="assertErrors[index]===false" color="red"
                     size="small">校验失败</a-tag>
            </span>
          </template>
          <template #extra>
            <a-space>
              <icon-copy :style="{ cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.4 : 1 }" @click.stop="!disabled && copyAssert(index)"/>
              <icon-delete :style="{ cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.4 : 1 }" @click="!disabled && deleteAssert(index)"/>
            </a-space>
          </template>
          <DynamicForm
              ref="assertFormRefs"
              :fields="PostAssert"
              :schemas="assertSchema"
              @change="(newData) => handleAssertChange(index, newData)"
              :model-value="asse"
              :disabled="disabled"
          />
        </a-collapse-item>
      </a-collapse>
      <a-button :disabled="disabled" style="width: 100%" @click="addAssert">添加断言</a-button>
    </a-tab-pane>
    <a-tab-pane key="3" v-if="excludeExtractFields">
      <template #title>
        <a-badge
            :count="localExtractList.length"
            :offset="[10, -2]"
            :dot-style="{ background: '#E5E6EB', color: '#86909C' }"
        >
          关联提取
        </a-badge>
      </template>
      <a-collapse>
        <a-collapse-item
            v-for="(extr, index) in localExtractList"
            :key="`extract-${index}`"
            :class="{ 'error-item': extractErrors[index] }"
        >
          <template #header>
            <span :class="{ 'error-text': extractErrors[index] }">
              提取 {{ index + 1 }}
              <a-tag v-if="extractErrors[index]===false" color="red" size="small">校验失败</a-tag>
            </span>
          </template>
          <template #extra>
            <a-space>
              <icon-copy :style="{ cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.4 : 1 }" @click.stop="!disabled && copyExtract(index)"/>
              <icon-delete :style="{ cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.4 : 1 }" @click="!disabled && deleteExtract(index)"/>
            </a-space>
          </template>
          <DynamicForm
              ref="extractFormRefs"
              :fields="PostExtract"
              :schemas="extractSchema"
              @change="(newData) => handleExtractChange(index, newData)"
              :model-value="extr"
              :disabled="disabled"
          />
        </a-collapse-item>
      </a-collapse>
      <a-button :disabled="disabled" style="width: 100%" @click="addExtract">添加抽取</a-button>
    </a-tab-pane>
  </a-tabs>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {assertSchema} from "@/schema/operationSchema/assertOperation/AssertSchema";
import {AssertStepDTO} from "@/types/dto/stepDTO/AssertStepDTO";
import {PostAssert, PostExtract, StepSetting} from "@/schema/stepFormConfig/FormConfig";
import {extractSchema} from "@/schema/operationSchema/extractOperation/extractSchema";
import DynamicForm from "@/views/scene/component/DynamicForm.vue";
import {Message} from "@arco-design/web-vue";
import {ExtractStepDTO} from "@/types/dto/stepDTO/ExtractStepDTO";
import {Setting} from "@/types/dto/SettingDTO";

const props = defineProps({
  // 当前主表单的操作
  currentStep: {
    type: String,
  },
  assertList: {
    type: Array,
    default: () => []
  },
  extractList: {
    type: Array,
    default: () => []
  },
  setting: {
    type: Object,
    default: () => (new Setting())
  },
  // 校验规则
  assertRules: {
    type: Object,
    default: null
  },
  extractRules: {
    type: Object,
    default: null
  },
  settingRules: {
    type: Object,
    default: null
  },
  // 是否只读
  disabled: {
    type: Boolean,
    default: false
  }
});

const emit = defineEmits([
  'update:assertList',
  'update:extractList',
  'update:setting',
]);

// 本地数据
const localAssertList = ref([...props.assertList]);
const localExtractList = ref([...props.extractList]);
const localSetting = ref<Setting>({...props.setting} as Setting);

// 校验错误状态
const assertErrors = ref<boolean[]>([]);
const extractErrors = ref<boolean[]>([]);
const settingFormError = ref(false);

// 表单引用
const settingFormRef = ref();
const assertFormRefs = ref<any[]>([]);
const extractFormRefs = ref<any[]>([]);


// 数据变更处理
const handleAssertChange = (index: number, newData: any) => {
  localAssertList.value[index] = newData;
  emit('update:assertList', [...localAssertList.value]);
};

const handleExtractChange = (index: number, newData: any) => {
  localExtractList.value[index] = newData;
  emit('update:extractList', [...localExtractList.value]);
};

// 添加/删除/复制操作
const addAssert = () => {
  if (props.disabled) return;
  localAssertList.value.push(new AssertStepDTO());
  emit('update:assertList', [...localAssertList.value]);
};

const deleteAssert = (index: number) => {
  if (props.disabled) return;
  localAssertList.value.splice(index, 1);
  emit('update:assertList', [...localAssertList.value]);
};

const copyAssert = (index: number) => {
  if (props.disabled) return;
  const copy = JSON.parse(JSON.stringify(localAssertList.value[index]));
  localAssertList.value.splice(index + 1, 0, copy);
  emit('update:assertList', [...localAssertList.value]);
};

// 提取列表的类似方法...


const addExtract = () => {
  if (props.disabled) return;
  localExtractList.value.push(new ExtractStepDTO());
  emit('update:extractList', [...localExtractList.value]);
};

const deleteExtract = (index: number) => {
  if (props.disabled) return;
  localExtractList.value.splice(index, 1);
  emit('update:extractList', [...localExtractList.value]);
};

const copyExtract = (index: number) => {
  if (props.disabled) return;
  const copy = JSON.parse(JSON.stringify(localExtractList.value[index]));
  localExtractList.value.splice(index + 1, 0, copy);
  emit('update:extractList', [...localExtractList.value]);
};


// 校验方法
const validate = async () => {

  // 重置所有错误状态
  clearErrors();

  // 1. 校验设置表单
  const settingErrors = await settingFormRef.value?.validate();
  settingFormError.value = settingErrors.valid;

  // 2. 校验断言列表
  const assertResults = await Promise.all(
      assertFormRefs.value.map(async (form) => {
        if (!form) return false;
        const errors = await form.validate();
        return errors.valid;
      })
  );

  assertErrors.value = assertResults;

  if (assertResults.find((val) => !val)) {
    Message.error('存在无效的断言配置');
    return;
  }

  // 3. 校验提取列表
  const extractResults = await Promise.all(
      extractFormRefs.value.map(async (form) => {
        if (!form) return false;
        const errors = await form.validate();
        return errors.valid;
      })
  );
  extractErrors.value = extractResults;
  if (extractResults.find((val) => !val)) {
    Message.error('存在无效的提取配置');
    return;
  }


  const hasAssertErrors = Object.values(assertErrors.value).some(val => val === false);
  const hasExtractErrors = Object.values(extractErrors.value).some(val => val === false);
  // 计算整体校验状态
  const isValid = settingFormError.value && !hasAssertErrors && !hasExtractErrors;

  if (isValid) {
    return {valid: isValid, data: getFormData()}
  }
  return {valid: false};
};


// 获取表单数据
const getFormData = () => ({
  assertList: [...localAssertList.value],
  extractList: [...localExtractList.value],
  setting: {...localSetting.value}
});

// 重置表单
const resetForm = () => {
  localAssertList.value = [...props.assertList];
  localExtractList.value = [...props.extractList];
  localSetting.value = {...props.setting};
  clearErrors();
};


// 清除错误状态
const clearErrors = () => {
  // 重置设置表单错误
  settingFormError.value = false;

  // 重置断言错误状态数组
  if (assertFormRefs.value.length > 0) {
    assertErrors.value = new Array(assertFormRefs.value.length).fill(true);
  }

  // 重置提取错误状态数组
  if (extractFormRefs.value.length > 0) {
    extractErrors.value = new Array(extractFormRefs.value.length).fill(true);
  }

  // 清除DynamicForm内部的错误状态
  assertFormRefs.value.forEach(form => form?.clearValidate?.());
  extractFormRefs.value.forEach(form => form?.clearValidate?.());
  settingFormRef.value?.clearValidate?.();
};

// 排除不需要断言的操作
const excludeAssertFields = computed(() => {
  const excludeList = ["IF", "WHILE", "ASSERT"]
  return !excludeList.includes(props.currentStep)
});

// 排除不需要抽取的操作
const excludeExtractFields = computed(() => {
  const excludeList = ["EXTRACT","IF"]
  return !excludeList.includes(props.currentStep)
});


// 暴露方法
defineExpose({
  validate,
  getFormData,
  resetForm,
  clearErrors
});

// 监听props变化
watch(() => props.assertList, (val) => {
  localAssertList.value = [...(val || [])];
}, {deep: true});

watch(() => props.extractList, (val) => {
  localExtractList.value = [...(val || [])];
}, {deep: true});

watch(() => props.setting, (val) => {
  localSetting.value = {...val};
}, {deep: true});


</script>

<style scoped>

</style>