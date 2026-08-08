<template>
  <a-form
      ref="formRef"
      :model="formData"
      class="form"
      :label-col-props="{ span: 8 }"
      :wrapper-col-props="{ span: 16 }"
  >
    <a-form-item
        field="email"
        label="邮箱"
        :rules="[
        {
          required: true,
          message: '请输入邮箱',
        },
        {
          match: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
          message: '邮箱格式不正确',
        },
      ]"
    >
      <a-input
          v-model="formData.email"
          placeholder="请输入邮箱地址"
      />
    </a-form-item>
    <a-form-item
        field="nickname"
        label="昵称"
        :rules="[
        {
          required: true,
          message: '请输入昵称',
        },
      ]"
    >
      <a-input
          v-model="formData.nickname"
          placeholder="请输入昵称"
      />
    </a-form-item>
    <a-form-item>
      <a-space>
        <a-button type="primary" :loading="saving" @click="validate">
          保存
        </a-button>
        <a-button type="secondary" @click="reset">
          重置
        </a-button>
      </a-space>
    </a-form-item>
  </a-form>
</template>

<script lang="ts" setup>
import { ref, watch } from 'vue';
import { updateUserInfo } from '@/api/MyApi/user';
import { Message } from '@arco-design/web-vue';
import type { FormInstance } from '@arco-design/web-vue';

const props = defineProps({
  userInfo: {
    type: Object,
    required: true
  }
});
const emits = defineEmits(['refreshUserInfo']);

// 本地表单数据
const formData = ref({ ...props.userInfo });

// 监听父组件传入的 userInfo 变化，同步到本地（例如刷新数据后）
watch(() => props.userInfo, (newVal) => {
  formData.value = { ...newVal };
}, { deep: true, immediate: true });

const formRef = ref<FormInstance>();
const saving = ref(false);

const validate = async () => {
  const res = await formRef.value?.validate();
  if (res) return;
  saving.value = true;
  try {
    const updateRes: any = await updateUserInfo(formData.value);
    if (updateRes.code === 200) {
      Message.success('更新成功');
      // 通知父组件刷新数据（此时父组件会重新获取最新数据并重新传入 userInfo）
      emits('refreshUserInfo');
    } else {
      Message.error(updateRes.msg || '更新失败，请稍后重试');
    }
  } catch (e) {
    console.error(e);
    Message.error('更新失败，请检查网络后重试');
  } finally {
    saving.value = false;
  }
};

const reset = () => {
  // 重置为最初传入的数据（即 props.userInfo 的副本）
  formData.value = { ...props.userInfo };
};

</script>

<style scoped lang="less">
.form {
  width: 540px;
  margin: 0 auto;
}
</style>
