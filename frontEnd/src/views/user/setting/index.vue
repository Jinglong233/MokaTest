<template>
  <div class="setting-page">
    <a-row class="user-panel-row">
      <a-col :span="24">
        <UserPanel/>
      </a-col>
    </a-row>
    <a-row class="tabs-row">
      <a-col :span="24">
        <a-tabs class="setting-tabs" default-active-key="1" type="rounded">
          <a-tab-pane key="1" title="基础信息">
            <BasicInformation v-if="userInfo!=null" :user-info="userInfo" @refreshUserInfo="resetUserInfo"/>
          </a-tab-pane>
          <a-tab-pane key="2" title="安全设置">
            <SecuritySettings/>
          </a-tab-pane>
          <a-tab-pane key="3" title="我的项目">
            <MyProject/>
          </a-tab-pane>
          <a-tab-pane key="4" title="我的团队">
            <MyTeam/>
          </a-tab-pane>
          <a-tab-pane key="5" title="最新消息">
            <LatestNotification/>
          </a-tab-pane>
        </a-tabs>
      </a-col>
    </a-row>
  </div>
</template>

<script lang="ts" setup>
import {ref, onMounted} from 'vue';
import {useUserStore} from '@/store';
import {cloneDeep} from 'lodash-es'
import UserPanel from "@/views/user/setting/components/user-panel.vue";
import SecuritySettings from "@/views/user/setting/components/security-settings.vue";
import BasicInformation from "@/views/user/setting/components/basic-information.vue";
import MyProject from "@/views/user/setting/components/my-project.vue";
import MyTeam from "@/views/user/setting/components/my-team.vue";
import LatestNotification from "@/views/user/setting/components/latest-notification.vue";


const userStore = useUserStore();
const userInfo = ref(null);

onMounted(async () => {
  await resetUserInfo();
});

const resetUserInfo = async () => {
  await userStore.info(); // 从后端获取最新用户信息并存入 store
  // 深拷贝一份，避免与 store 共享引用
  userInfo.value = cloneDeep(userStore.userInfo);
};

</script>

<style scoped lang="less">
.setting-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.user-panel-row {
  flex: 0 0 auto;
  margin-bottom: 16px;
}

.tabs-row {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.tabs-row :deep(.arco-col) {
  height: 100%;
}

.setting-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
  padding: 20px 0 0 20px;
  background-color: var(--color-bg-2);
  border-radius: 4px;
}

.setting-tabs :deep(.arco-tabs-content) {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  scrollbar-width: thin;
  scrollbar-color: var(--color-text-4) transparent;
  padding-right: 20px;
}

:deep(.section-title) {
  margin-top: 0;
  margin-bottom: 16px;
  font-size: 14px;
}
</style>
