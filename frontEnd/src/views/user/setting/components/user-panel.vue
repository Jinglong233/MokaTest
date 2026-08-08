<template>
  <a-card :bordered="false">
    <a-space :size="54">
      <a-upload
          :custom-request="customRequest"
          list-type="picture-card"
          :file-list="fileList"
          :show-upload-button="true"
          :show-file-list="false"
          accept="image/*"
          @change="uploadChange"
      >
        <template #upload-button>
          <a-avatar :size="100" class="info-avatar">
            <template #trigger-icon>
              <icon-camera/>
            </template>
            <img v-if="fileList.length" :src="fileList[0].url"/>
          </a-avatar>
          <div class="avatar-upload-hint">图片不超过 1MB</div>
        </template>
      </a-upload>
      <a-descriptions
          :data="renderData"
          :column="2"
          align="right"
          layout="inline-horizontal"
          :label-style="{
          width: '140px',
          fontWeight: 'normal',
          color: 'rgb(var(--gray-8))',
        }"
          :value-style="{
          width: '200px',
          paddingLeft: '8px',
          textAlign: 'left',
        }"
      >
        <template #label="{ label }">{{ label }} :</template>
        <template #value="{ value }">
          <span>{{ value || '-' }}</span>
        </template>
      </a-descriptions>
    </a-space>
  </a-card>
</template>

<script lang="ts" setup>
import {computed, ref} from 'vue';
import type {
  FileItem,
  RequestOption,
} from '@arco-design/web-vue/es/upload/interfaces';
import {useUserStore} from '@/store';
import {Message} from '@arco-design/web-vue';
import {uploadAvatar} from '@/api/MyApi/user';

const userStore = useUserStore();


const file = {
  uid: '-2',
  name: 'avatar.png',
  url: userStore.userInfo.avatar,
};
const renderData = computed<any[]>(() => [
  {
    label: '用户名',
    value: userStore.userInfo.username,
  },
  {
    label: '昵称',
    value: userStore.userInfo.nickname,
  },
  {
    label: '邮箱',
    value: userStore.userInfo.email,
  },
  {
    label: '手机号码',
    value: userStore.userInfo.phone,
  },
]);
const fileList = ref<FileItem[]>([file]);
const uploadChange = (fileItemList: FileItem[], fileItem: FileItem) => {
  fileList.value = [fileItem];
};
const customRequest = (options: RequestOption) => {
  const controller = new AbortController();

  (async function requestWrap() {
    const {
      onProgress,
      onError,
      onSuccess,
      fileItem,
      name = 'file',
    } = options;
    onProgress(20);
    const formData = new FormData();
    formData.append(name as string, fileItem.file as Blob);
    const onUploadProgress = (event: ProgressEvent) => {
      let percent;
      if (event.total > 0) {
        percent = (event.loaded / event.total) * 100;
      }
      onProgress(parseInt(String(percent), 10), event);
    };

    try {
      // 头像文件大小校验：最大 1MB
      const avatarFile = fileItem.file as File;
      if (avatarFile.size > 1 * 1024 * 1024) {
        Message.error('头像文件大小不能超过 1MB');
        onError(new Error('头像文件大小不能超过 1MB'));
        return;
      }
      // 头像文件格式校验：仅允许图片
      if (!avatarFile.type.startsWith('image/')) {
        Message.error('头像仅支持图片格式（jpg、png、gif 等）');
        onError(new Error('头像仅支持图片格式'));
        return;
      }
      const res = await uploadAvatar(avatarFile);
      // 更新 store 中的头像
      userStore.setInfo({ avatar: res.data });
      // 更新文件列表中的头像 URL
      fileList.value = [{
        uid: '-2',
        name: 'avatar.png',
        url: res.data,
      }];
      Message.success('头像更新成功');
      onSuccess(res.data);
    } catch (error) {
      Message.error('头像上传失败');
      onError(error);
    }
  })();
  return {
    abort() {
      controller.abort();
    },
  };
};
</script>

<style scoped lang="less">
.arco-card {
  padding: 14px 0 4px 4px;
  border-radius: 4px;
}

:deep(.arco-avatar-trigger-icon-button) {
  width: 32px;
  height: 32px;
  line-height: 32px;
  background-color: #e8f3ff;

  .arco-icon-camera {
    margin-top: 8px;
    color: rgb(var(--arcoblue-6));
    font-size: 14px;
  }
}

.avatar-upload-hint {
  text-align: center;
  font-size: 12px;
  color: rgb(var(--gray-6));
  margin-top: 8px;
}
</style>
