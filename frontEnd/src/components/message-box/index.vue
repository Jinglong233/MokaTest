<template>
  <div class="message-box">
    <div class="message-box-header">
      <span class="message-box-title">{{ $t('messageBox.tab.title.message') }}</span>
      <a-space :size="12" class="message-actions">
        <a href="javascript:;" class="action-link" @click.prevent="allRead">
          <icon-check />
          <span class="action-text">{{ $t('messageBox.allRead') }}</span>
        </a>
        <a-divider direction="vertical" :margin="8" />
        <a href="javascript:;" class="action-link" @click.prevent="viewMore">
          <span class="action-text">{{ $t('messageBox.viewMore') }}</span>
          <icon-arrow-right />
        </a>
      </a-space>
    </div>

    <div class="message-body">
      <div class="message-sidebar">
        <div class="sidebar-group" v-for="g in sidebarGroups" :key="g.key">
          <div class="sidebar-group-title">{{ g.title }}</div>
          <div
            v-for="s in g.subs"
            :key="s.key"
            class="sidebar-item"
            :class="{ active: activeGroup === g.key && activeSub === s.key }"
            @click="selectCategory(g.key, s.key)"
          >{{ s.label }}</div>
        </div>
      </div>
      <a-spin style="display: block; flex: 1;" :loading="loading">
      <div v-if="!renderList.length" class="message-empty">
        <a-result status="404">
          <template #subtitle>
            <a-empty>{{ $t('messageBox.noContent') }}</a-empty>
          </template>
        </a-result>
      </div>
      <div v-else class="message-list-container" @scroll="handleScroll">
        <List
          :render-list="renderList"
          @item-click="(items) => navigate(items[0])"
        />
        <div v-if="isLoadingMore" class="loading-more">
          <a-spin :size="16" />
          <span class="loading-text">加载中...</span>
        </div>
        <div v-else-if="!hasMore && renderList.length > 0" class="no-more">
          没有更多了
        </div>
      </div>
      </a-spin>
    </div>
  </div>
</template>

<script lang="ts" setup>
  import { ref, reactive, toRefs, computed } from 'vue';
  import { useRouter } from 'vue-router';
  import {
    queryMessageList,
    setMessageStatus,
    markAllMessagesRead,
    MessageRecord,
    MessageListType,
  } from '@/api/message';
  import {useMessageNavigator} from '@/hooks/message-navigator';
  import useLoading from '@/hooks/loading';
  import List from './list.vue';

  const router = useRouter();
  const { loading, setLoading } = useLoading(true);
  const {navigate} = useMessageNavigator();

  // 消息分类侧板栏
  const sidebarGroups = [
    { key: 'all', title: '全部消息', subs: [
      { key: 'all', label: '全部' },
      { key: 'mention', label: '@我的' },
      { key: 'unread', label: '未读' },
      { key: 'read', label: '已读' },
    ]},
    { key: 'qa', title: '质量管理', subs: [
      { key: 'all', label: '全部' },
      { key: 'mention', label: '@我的' },
      { key: 'unread', label: '未读' },
      { key: 'read', label: '已读' },
    ]},
    { key: 'testCase', title: '用例管理', subs: [
      { key: 'all', label: '全部' },
      { key: 'mention', label: '@我的' },
      { key: 'unread', label: '未读' },
      { key: 'read', label: '已读' },
    ]},
  ];
  const activeGroup = ref('all');
  const activeSub = ref('all');

  function selectCategory(group: string, sub: string) {
    activeGroup.value = group;
    activeSub.value = sub;
    fetchSourceData(true);
  }

  function subToParams(groupKey: string, subKey: string) {
    const params: { bizType?: string; isRead?: number; isMention?: boolean } = {};
    if (groupKey !== 'all') params.bizType = groupKey;
    if (subKey === 'unread') params.isRead = 0;
    else if (subKey === 'read') params.isRead = 1;
    if (subKey === 'mention') params.isMention = true;
    return params;
  }
  const page = ref(1);
  const pageSize = ref(10);
  const hasMore = ref(true);
  const isLoadingMore = ref(false);
  const messageData = reactive<{
    renderList: MessageRecord[];
    messageList: MessageRecord[];
  }>({
    renderList: [],
    messageList: [],
  });

  async function fetchSourceData(reset = false) {
    if (reset) {
      page.value = 1;
      hasMore.value = true;
    }
    if (!hasMore.value && !reset) return;

    if (page.value === 1) {
      setLoading(true);
    } else {
      isLoadingMore.value = true;
    }
    try {
      const p = subToParams(activeGroup.value, activeSub.value);
      const res: any = await queryMessageList(page.value, pageSize.value, p.isRead, p.bizType, p.isMention);
      const records = (res.data?.records || []).map((item: MessageRecord) => ({
        ...item,
      }));
      const total = res.data?.total || 0;
      if (reset) {
        messageData.messageList = records;
      } else {
        messageData.messageList = [...messageData.messageList, ...records];
      }
      hasMore.value = messageData.messageList.length < total;
    } catch (err) {
      // 错误处理
    } finally {
      setLoading(false);
      isLoadingMore.value = false;
    }
  }

  async function readMessage(data: MessageListType) {
    const ids = data.map((item) => item.id);
    if (ids.length > 0) {
      await setMessageStatus({ ids });
      // 通知导航栏刷新未读数
      window.dispatchEvent(new CustomEvent('message-read'));
    }
    fetchSourceData(true);
  }

  const renderList = computed(() => {
    return messageData.messageList;
  });

  const allRead = async () => {
    await markAllMessagesRead();
    window.dispatchEvent(new CustomEvent('message-read'));
    fetchSourceData(true);
  };

  const viewMore = () => {
    window.dispatchEvent(new CustomEvent('close-message-popover'));
    router.push({ name: 'Message' });
  };

  const handleScroll = (e: Event) => {
    const target = e.target as HTMLElement;
    if (!target) return;
    const bottom = target.scrollHeight - target.scrollTop - target.clientHeight;
    if (bottom < 20 && hasMore.value && !isLoadingMore.value && !loading.value) {
      page.value += 1;
      fetchSourceData();
    }
  };

  fetchSourceData(true);

  defineExpose({
    fetchSourceData,
    allRead,
  });
</script>

<style scoped lang="less">
  :deep(.arco-popover-popup-content) {
    padding: 0;
  }

  :deep(.arco-list-item-meta) {
    align-items: flex-start;
  }
  .message-box-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 16px 12px 16px;
    border-bottom: 1px solid var(--color-neutral-3);

    .message-box-title {
      font-size: 16px;
      font-weight: 500;
      color: var(--color-text-1);
    }
  }

  .message-empty {
    padding-top: 40px;
  }

  .message-body {
    display: flex;
    height: 360px;
  }

  .message-sidebar {
    width: 130px;
    border-right: 1px solid var(--color-neutral-3);
    overflow-y: auto;
    padding: 8px 0;
    flex-shrink: 0;

    .sidebar-group {
      margin-bottom: 4px;
    }

    .sidebar-group-title {
      padding: 6px 12px;
      font-size: 12px;
      color: var(--color-text-3);
      font-weight: 500;
    }

    .sidebar-item {
      padding: 6px 12px 6px 20px;
      font-size: 13px;
      color: var(--color-text-2);
      cursor: pointer;
      &:hover {
        background: var(--color-fill-2);
      }
      &.active {
        background: rgb(var(--primary-1));
        color: rgb(var(--primary-6));
        font-weight: 500;
      }
    }
  }

  .message-list-container {
    flex: 1;
    height: 360px;
    overflow-y: auto;
  }

  .message-actions {
    .action-link {
      display: inline-flex;
      align-items: center;
      color: rgb(var(--primary-6));
      text-decoration: none;
      cursor: pointer;
      font-size: 14px;
      .action-text {
        margin-left: 4px;
        margin-right: 4px;
      }
      &:hover {
        color: rgb(var(--primary-7));
      }
    }
    :deep(.arco-divider-vertical) {
      border-left-color: var(--color-neutral-3);
    }
  }

  .loading-more,
  .no-more {
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 12px 0;
    color: rgb(var(--gray-6));
    font-size: 12px;
  }

  .loading-more {
    .loading-text {
      margin-left: 8px;
    }
  }

</style>
