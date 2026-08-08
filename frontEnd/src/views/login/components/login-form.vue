<template>
  <div class="login-form-wrapper">
    <div class="login-form-title">{{ $t('login.form.title') }}</div>
    <div class="login-form-sub-title">{{ $t('login.form.subTitle') }}</div>
    <a-form
        ref="loginForm"
        :model="userInfo"
        class="login-form"
        layout="vertical"
        @submit="handleSubmit"
    >
      <a-form-item
          field="username"
          :rules="[{ required: true, message: $t('login.form.userName.errMsg') }]"
          :validate-trigger="['change', 'blur']"
          hide-label
      >
        <a-input
            v-model="userInfo.username"
            :placeholder="$t('login.form.userName.placeholder')"
        >
          <template #prefix>
            <icon-user/>
          </template>
        </a-input>
      </a-form-item>
      <a-form-item
          field="password"
          :rules="[{ required: true, message: $t('login.form.password.errMsg') }]"
          :validate-trigger="['change', 'blur']"
          hide-label
      >
        <a-input-password
            v-model="userInfo.password"
            :placeholder="$t('login.form.password.placeholder')"
            allow-clear
        >
          <template #prefix>
            <icon-lock/>
          </template>
        </a-input-password>
      </a-form-item>
      <a-space :size="16" direction="vertical">
        <div class="login-form-password-actions">
          <a-checkbox
              :model-value="loginConfig.rememberPassword"
              @change="setRememberPassword as any"
          >
            {{ $t('login.form.rememberPassword') }}
          </a-checkbox>
          <a-link @click="handleForgetPassword">{{ $t('login.form.forgetPassword') }}</a-link>
        </div>
        <a-button type="primary" html-type="submit" long :loading="loading">
          {{ $t('login.form.login') }}
        </a-button>
      </a-space>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
import {reactive} from 'vue';
import {useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import {ValidatedError} from '@arco-design/web-vue/es/form/interface';
import {useI18n} from 'vue-i18n';
import {useStorage} from '@vueuse/core';
import {useUserStore} from '@/store';
import useLoading from '@/hooks/loading';
import type {LoginData} from '@/api/MyApi/user';

const router = useRouter();
const {t} = useI18n();
const {loading, setLoading} = useLoading();
const userStore = useUserStore();

const loginConfig = useStorage('login-config', {
  rememberPassword: true,
  username: '',
  password: '',
});
const userInfo = reactive({
  username: loginConfig.value.username,
  password: loginConfig.value.password,
});

const handleSubmit = async ({
                              errors,
                              values,
                            }: {
  errors: Record<string, ValidatedError> | undefined;
  values: Record<string, any>;
}) => {
  if (loading.value) return;
  if (!errors) {
    setLoading(true);
    try {
      await userStore.login(values as LoginData);
      // 拉取当前登录用户信息（role 等），供后续 isSuperAdmin 等判断使用
      await userStore.info();
      const {rememberPassword} = loginConfig.value;
      const {username, password} = values;
      // 实际生产环境需要进行加密存储。
      // The actual production environment requires encrypted storage.
      loginConfig.value.username = rememberPassword ? username : '';
      loginConfig.value.password = rememberPassword ? password : '';

      // 登录成功后根据角色进入不同首页
      if (userStore.role === 'super_admin') {
        await router.push({ name: 'AdminOverview' });
      } else {
        await router.push({ name: 'TeamWorkspace' });
      }
      Message.success(t('login.form.login.success'));
    } finally {
      setLoading(false);
    }
  }
};


const setRememberPassword = (value: boolean) => {
  loginConfig.value.rememberPassword = value;
};

const handleForgetPassword = () => {
  Message.info('忘记密码请联系管理员重置');
};
</script>

<style lang="less" scoped>
.login-form {
  margin-top: 24px;

  &-wrapper {
    width: 380px;
    padding: 40px;
    background: #ffffff;
    border: 1px solid var(--color-border-2);
    border-radius: 12px;
    box-shadow: 0 12px 32px -12px rgba(29, 78, 216, 0.12);
  }

  &-title {
    color: var(--color-text-1);
    font-weight: 600;
    font-size: 28px;
    line-height: 36px;
    letter-spacing: -0.01em;
  }

  &-sub-title {
    margin-top: 8px;
    color: var(--color-text-3);
    font-size: 15px;
    line-height: 24px;
  }

  &-error-msg {
    height: 32px;
    color: rgb(var(--red-6));
    line-height: 32px;
  }

  &-password-actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }

  // 控件节奏统一：输入框与按钮同高 44px、同圆角
  :deep(.arco-input-wrapper),
  :deep(.arco-input-password) {
    height: 44px;
    border-radius: 8px;
  }

  :deep(.arco-btn) {
    height: 44px;
    font-size: 16px;
    border-radius: 8px;
  }

  :deep(.arco-link) {
    &:focus-visible {
      outline: 2px solid rgb(var(--primary-6));
      outline-offset: 2px;
      border-radius: 4px;
    }
  }
}
</style>
