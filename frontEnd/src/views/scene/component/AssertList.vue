<template>
  <div class="assert-list">
    <a-collapse>
      <a-collapse-item
          v-for="(item, index) in modelValue"
          :key="`assert-${index}`"
          :class="{ 'error-item': assertErrors[index] }"
      >
        <template #header>
            <span :class="{ 'error-text': assertErrors[index] }">
              断言 {{ index + 1 }}
              <a-tag v-if="assertErrors[index] === false" color="red" size="small">校验失败</a-tag>
            </span>
        </template>
        <template #extra>
          <a-space>
            <icon-copy :style="{ cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.4 : 1 }" @click.stop="!disabled && copyItem(index)"/>
            <icon-delete :style="{ cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.4 : 1 }" @click.stop="!disabled && deleteItem(index)"/>
          </a-space>
        </template>
        <DynamicForm
            ref="assertListFormRefs"
            :fields="PostAssert"
            :model-value="item"
            :disabled="disabled"
            @update:modelValue="(val) => updateItem(index, val)"
        />
      </a-collapse-item>
    </a-collapse>
    <a-button :disabled="disabled" style="width: 100%" @click="addItem">
      <template #icon>
        <icon-plus/>
      </template>
      添加断言
    </a-button>
  </div>
</template>

<script setup lang="ts">
import {ref} from 'vue';
import DynamicForm from "@/views/scene/component/DynamicForm.vue";
import {PostAssert} from "@/schema/stepFormConfig/FormConfig";
import {AssertStepDTO} from "@/types/dto/stepDTO/AssertStepDTO";

const props = defineProps({
  modelValue: {
    type: Array,
    required: true,
    default: () => []
  },
  disabled: {
    type: Boolean,
    default: false
  }
});


const emit = defineEmits(['update:modelValue']);

const assertListFormRefs = ref([]);

// 更新单项数据
const updateItem = (index: any, newValue: any) => {
  if (props.disabled) return;
  const newList = [...props.modelValue];
  newList[index] = newValue;
  emit('update:modelValue', newList);
};

// 添加新项
const addItem = () => {
  if (props.disabled) return;
  const newList = [...props.modelValue, new AssertStepDTO()];
  emit('update:modelValue', newList);
};

// 删除项
const deleteItem = (index: number) => {
  if (props.disabled) return;
  const newList = [...props.modelValue];
  newList.splice(index, 1);
  emit('update:modelValue', newList);
};

// 复制项
const copyItem = (index: number) => {
  if (props.disabled) return;
  const newList = [...props.modelValue];
  const copy = JSON.parse(JSON.stringify(newList[index]));
  newList.splice(index + 1, 0, copy);
  emit('update:modelValue', newList);
};

const assertErrors = ref([]);


const validate = async () => {
  clearErrors();

  const assertResults = await Promise.all(
      assertListFormRefs.value.map(async (form) => {
        if (!form) return false;
        const errors = await form.validate();
        return errors.valid;
      })
  );
  assertErrors.value = assertResults;
  const isValid = assertErrors.value.every(result => result);
  if (isValid) {
    return {valid: isValid, data: props.modelValue};
  }

  return {valid: false};
};
// 清空校验状态
const clearErrors = () => {

  // 重置断言错误状态数组
  if (assertListFormRefs.value.length > 0) {
    assertErrors.value = new Array(assertListFormRefs.value.length).fill(true);
  }

}

// 暴露验证方法
defineExpose({
  validate,
});

</script>

<style scoped>
.assert-list {
  width: 100%;
}

.error-item {
  border-left: 2px solid var(--color-danger);
}

.error-text {
  color: var(--color-danger);
}
</style>