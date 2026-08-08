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

    <a-spin style="display: block" :loading="loading">
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
          @item-click="handleViewDetail"
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

  <!-- 消息详情弹窗 -->
  <a-modal
    v-model:visible="detailVisible"
    title="消息详情"
    width="560px"
    :footer="false"
    :mask-closable="true"
  >
    <div v-if="currentMessage" class="message-detail">
      <a-space direction="vertical" fill size="large">
        <div>
          <div class="detail-label">标题</div>
          <div class="detail-value">{{ currentMessage.title }}</div>
        </div>

        <div>
          <div class="detail-label">内容</div>
          <div class="detail-value">{{ currentMessage.content }}</div>
        </div>

        <div class="detail-row">
          <div>
            <div class="detail-label">状态</div>
            <a-tag v-if="!currentMessage.isRead" color="red">未读</a-tag>
            <a-tag v-else color="gray">已读</a-tag>
          </div>
          <div>
            <div class="detail-label">业务类型</div>
            <a-tag v-if="currentMessage.bizType === 'bug'" color="red">BUG</a-tag>
            <a-tag v-else-if="currentMessage.bizType === 'requirement'" color="arcoblue">需求</a-tag>
            <span v-else style="color: #86909c;">-</span>
          </div>
          <div>
            <div class="detail-label">接收时间</div>
            <div class="detail-value">{{ currentMessage.createTime }}</div>
          </div>
        </div>

        <!-- 来源快照 -->
        <div v-if="currentMessage.snapshot" class="snapshot-card">
          <div class="snapshot-title">
            <icon-history />
            <span>消息来源（发送时状态）</span>
          </div>

          <!-- BUG 快照 -->
          <template v-if="currentMessage.bizType === 'bug'">
            <div class="snapshot-grid">
              <div class="snapshot-item">
                <div class="snapshot-label">编号</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.bugCode || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">标题</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.title || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">所属团队</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.teamName || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">所属项目</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.projectName || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">严重程度</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.severity || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">优先级</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.priority || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">状态</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.status || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">环境</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.environment || '-' }}</div>
              </div>
            </div>
            <div class="snapshot-item block">
              <div class="snapshot-label">描述</div>
              <div class="snapshot-value">{{ currentMessage.snapshot.description || '-' }}</div>
            </div>
          </template>

          <!-- 需求快照 -->
          <template v-else-if="currentMessage.bizType === 'requirement'">
            <div class="snapshot-grid">
              <div class="snapshot-item">
                <div class="snapshot-label">编号</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.reqCode || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">标题</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.title || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">所属团队</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.teamName || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">所属项目</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.projectName || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">优先级</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.priority || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">状态</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.status || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">需求类型</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.reqType || '-' }}</div>
              </div>
              <div class="snapshot-item">
                <div class="snapshot-label">来源</div>
                <div class="snapshot-value">{{ currentMessage.snapshot.source || '-' }}</div>
              </div>
            </div>
            <div class="snapshot-item block">
              <div class="snapshot-label">描述</div>
              <div class="snapshot-value">{{ currentMessage.snapshot.description || '-' }}</div>
            </div>
          </template>
        </div>

        <a-button
          v-if="currentMessage.bizType && currentMessage.bizId"
          type="primary"
          long
          @click="handleGoToSource"
        >
          查看来源
        </a-button>
      </a-space>
    </div>
  </a-modal>
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
  const detailVisible = ref(false);
  const currentMessage = ref<MessageRecord | null>(null);
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

  const parseSnapshot = (extraData?: string): Record<string, any> | undefined => {
    if (!extraData) return undefined;
    try {
      return JSON.parse(extraData);
    } catch (e) {
      return undefined;
    }
  };

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
      const res: any = await queryMessageList(page.value, pageSize.value);
      const records = (res.data?.records || []).map((item: MessageRecord) => ({
        ...item,
        snapshot: parseSnapshot(item.extraData),
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

  const handleViewDetail = (items: MessageListType) => {
    const item = Array.isArray(items) ? items[0] : items;
    if (!item) return;
    currentMessage.value = item;
    detailVisible.value = true;
    if (!item.isRead) {
      readMessage([item]);
    }
  };

  const handleGoToSource = async () => {
    if (!currentMessage.value) return;
    await navigate(currentMessage.value, {closePopover: true});
  };

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

  .message-list-container {
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

  .message-detail {
    padding: 16px 0;
  }

  .detail-label {
    color: rgb(var(--gray-6));
    font-size: 12px;
    margin-bottom: 8px;
  }

  .detail-value {
    color: rgb(var(--gray-8));
    font-size: 14px;
    word-break: break-all;
  }

  .detail-row {
    display: flex;
    gap: 24px;
  }

  .snapshot-card {
    background-color: var(--color-fill-2);
    border-radius: 4px;
    padding: 16px;

    .snapshot-title {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 14px;
      font-weight: 500;
      color: var(--color-text-1);
      margin-bottom: 12px;
    }

    .snapshot-grid {
      display: grid;
      grid-template-columns: repeat(2, 1fr);
      gap: 12px;
    }

    .snapshot-item {
      &.block {
        grid-column: span 2;
        margin-top: 4px;
      }

      .snapshot-label {
        color: rgb(var(--gray-6));
        font-size: 12px;
        margin-bottom: 4px;
      }

      .snapshot-value {
        color: rgb(var(--gray-8));
        font-size: 13px;
        word-break: break-all;
      }
    }
  }

</style>
