<template>
  <a-list :bordered="false">
    <a-list-item>
      <a-list-item-meta>
        <template #avatar>
          <a-typography-paragraph>
            登录密码
          </a-typography-paragraph>
        </template>
        <template #description>
          <div class="content">
            <a-typography-paragraph>
              登录密码必须大于等于六个字符，小于等于20个字符。只能包含字母、数字和下划线
            </a-typography-paragraph>
          </div>
          <div class="operation">
            <a-link @click="()=>{updatePasswordVisible=true}">
              修改
            </a-link>
          </div>
        </template>
      </a-list-item-meta>
    </a-list-item>
  </a-list>

  <a-modal :visible="updatePasswordVisible" title="修改密码"
           :mask-closable="false"
           :ok-loading="pwdSaving"
           @cancel="resetPwdDialog"
           @before-ok="handleUpdatePwd">
    <a-form :model="updatePasswordForm" ref="updatePasswordFormRef">
      <a-form-item field="oldPassword" label="原密码"
                   :rules="[{required: true, message: '请输入旧密码'}]">
        <a-input-password v-model="updatePasswordForm.oldPassword"/>
      </a-form-item>
      <a-form-item field="newPassword" label="新密码"
                   :rules="[{required: true, message: '请输入新密码'},
                   {minLength: 6, message: '密码长度不能小于6位'},
                   {maxLength: 20, message: '密码长度不能大于20位'},
                   {match: /^[a-zA-Z0-9_]+$/, message: '密码只能包含字母、数字和下划线'},
                   {validator: (value: string, cb: any) => {
                     if (value && value === updatePasswordForm.oldPassword) {
                       cb('新密码不能与原密码相同');
                     } else {
                       cb();
                     }
                   }}]">
        <a-input-password v-model="updatePasswordForm.newPassword"/>
      </a-form-item>
      <a-form-item field="confirmPassword" label="确认新密码"
                   :rules="[{required: true, message: '请再次输入新密码'},
                   {validator: (value: string, cb: any) => {
                     if (value !== updatePasswordForm.newPassword) {
                       cb('两次输入的密码不一致');
                     } else {
                       cb();
                     }
                   }}]">
        <a-input-password v-model="updatePasswordForm.confirmPassword"/>
      </a-form-item>
    </a-form>
  </a-modal>

</template>

<script lang="ts" setup>

import {ref} from "vue";
import {Message, Modal} from "@arco-design/web-vue";
import type {FormInstance} from "@arco-design/web-vue";
import {updatePassword} from "@/api/MyApi/user";
import {useUserStore} from "@/store";
import {useRouter} from "vue-router";

const userStore = useUserStore();

const router = useRouter();

const updatePasswordForm = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const updatePasswordFormRef = ref<FormInstance>();
const updatePasswordVisible = ref(false);
const pwdSaving = ref(false);

const resetPwdDialog = () => {
  updatePasswordVisible.value = false;
  updatePasswordForm.value = {
    oldPassword: '',
    newPassword: '',
    confirmPassword: '',
  }
  updatePasswordFormRef.value?.clearValidate();
}

const handleUpdatePwd = async (done: (closed: boolean) => void) => {
  const validate = await updatePasswordFormRef.value?.validate();
  if (validate) {
    done(false);
    return;
  }
  pwdSaving.value = true;
  try {
    const res: any = await updatePassword({
      oldPassword: updatePasswordForm.value.oldPassword,
      newPassword: updatePasswordForm.value.newPassword,
    });
    if (res.code === 200) {
      done(true);
      // 修改成功后明确告知，由用户确认后再登出，避免一闪而过的告别
      Modal.success({
        title: '密码已修改',
        content: '请使用新密码重新登录',
        okText: '重新登录',
        hideCancel: true,
        maskClosable: false,
        escToClose: false,
        onOk: async () => {
          await userStore.logout();
          router.push({ name: 'login' });
        },
      });
      resetPwdDialog();
    } else {
      Message.error(res.msg || '修改失败，请检查原密码是否正确');
      done(false);
    }
  } catch (e: any) {
    console.error(e);
    Message.error(e?.response?.data?.msg || '修改失败，请检查原密码是否正确');
    done(false);
  } finally {
    pwdSaving.value = false;
  }
}
</script>

<style scoped lang="less">
:deep(.arco-list-item) {
  border-bottom: none !important;

  .arco-typography {
    margin-bottom: 20px;
  }

  .arco-list-item-meta-avatar {
    margin-bottom: 1px;
  }

  .arco-list-item-meta {
    padding: 0;
  }
}

:deep(.arco-list-item-meta-content) {
  flex: 1;
  border-bottom: 1px solid var(--color-neutral-3);

  .arco-list-item-meta-description {
    display: flex;
    flex-flow: row;
    justify-content: space-between;

    .tip {
      color: rgb(var(--gray-6));
    }

    .operation {
      margin-right: 6px;
    }
  }
}
</style>
