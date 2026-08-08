<template>
  <LoadError v-if="loadError" @retry="fetchMessages" />
  <template v-else>
    <a-list :bordered="false" :loading="loading">
      <a-list-item
          v-for="item in messageList"
          :key="item.id"
          class="message-item"
          @click="goMessageCenter"
      >
        <a-list-item-meta
            :title="item.title"
            :description="item.content"
        >
          <template #avatar>
            <a-badge :dot="!item.isRead">
              <a-avatar :style="{ backgroundColor: 'rgb(var(--arcoblue-6))' }">
                <icon-notification/>
              </a-avatar>
            </a-badge>
          </template>
        </a-list-item-meta>
        <template #actions>
          <span class="message-time">{{ item.createTime }}</span>
        </template>
      </a-list-item>
      <template #empty>
        <a-empty description="暂无消息"/>
      </template>
    </a-list>
    <div class="footer-link">
      <a-link @click="goMessageCenter">查看全部</a-link>
    </div>
  </template>
</template>

<script lang="ts" setup>
import {onMounted, ref} from 'vue';
import {useRouter} from 'vue-router';
import {queryMessageList} from '@/api/message';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';

const router = useRouter();
const {loading, loadError, track} = useLoadState();
const messageList = ref<any[]>([]);

const fetchMessages = async () => {
  const res: any = await track(queryMessageList(1, 5));
  messageList.value = res?.data?.records || [];
};

const goMessageCenter = () => {
  router.push({name: 'Message'});
};

onMounted(fetchMessages);
</script>

<style lang="less" scoped>
.message-time {
  font-size: 12px;
  color: var(--color-text-3);
}

.message-item {
  cursor: pointer;
  border-radius: 8px;
  padding: 12px;
  transition: background 0.2s;

  &:hover,
  &:focus-within {
    background: var(--color-fill-2);
  }
}

.footer-link {
  display: flex;
  justify-content: flex-end;
  padding: 8px 16px 8px 0;
}
</style>
