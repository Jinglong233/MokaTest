<template>
  <div class="message-list-page">
    <Breadcrumb :items="['menu.user', 'menu.user.message']" />

    <div class="message-layout">
      <a-card class="sidebar-card" :bordered="false">
        <div
          v-for="g in sidebarGroups"
          :key="g.key"
          class="sidebar-item"
          :class="{ active: activeGroup === g.key }"
          @click="selectCategory(g.key)"
        >{{ g.title }}</div>
      </a-card>
    <a-card class="general-card" :bordered="false">
      <template #title>
        我的消息
      </template>
      <template #extra>
        <a-space>
          <a-button shape="circle" @click="loadData()"><icon-refresh /></a-button>
          <a-popconfirm content="确认删除选中的消息吗？" type="warning" @ok="handleBatchDelete">
            <a-button status="danger" :disabled="!selectedKeys.length">批量删除</a-button>
          </a-popconfirm>
          <a-button type="primary" @click="handleAllRead">全部已读</a-button>
        </a-space>
      </template>

      <a-tabs v-model:active-key="activeTab" @change="onTabChange" type="line">
        <a-tab-pane key="all" title="全部" />
        <a-tab-pane key="mention" title="@我的" />
        <a-tab-pane key="unread" title="未读" />
        <a-tab-pane key="read" title="已读" />
      </a-tabs>
      <a-table
          :data="messageList"
          :loading="loading"
          :pagination="pagination"
          row-key="id"
          :row-selection="{ type: 'checkbox', showCheckedAll: true }"
          v-model:selected-keys="selectedKeys"
          @page-change="handlePageChange"
          @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="状态" data-index="isRead" :width="90">
            <template #cell="{ record }">
              <a-tag v-if="!record.isRead" color="red">未读</a-tag>
              <a-tag v-else color="gray">已读</a-tag>
            </template>
          </a-table-column>

          <a-table-column title="标题" data-index="title">
            <template #cell="{ record }">
              <a-link :style="{ fontWeight: record.isRead ? 'normal' : 'bold' }" @click="navigate(record)">
                {{ record.title }}
              </a-link>
            </template>
          </a-table-column>

          <a-table-column title="内容" data-index="content">
            <template #cell="{ record }">
              <a-typography-paragraph :ellipsis="{ rows: 1 }" :style="{ marginBottom: 0 }">
                {{ record.content }}
              </a-typography-paragraph>
            </template>
          </a-table-column>

          <a-table-column title="业务类型" data-index="bizType" :width="110">
            <template #cell="{ record }">
              <a-tag v-if="record.bizType === 'bug'" color="red">BUG</a-tag>
              <a-tag v-else-if="record.bizType === 'requirement'" color="arcoblue">需求</a-tag>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>

          <a-table-column title="时间" data-index="createTime" :width="170" />

          <a-table-column title="操作" :width="160" fixed="right">
            <template #cell="{ record }">
              <a-space>
                <a-button v-if="!record.isRead" type="text" size="small" @click="handleRead(record)"
                >标为已读
                </a-button>
                <a-popconfirm content="确认删除该消息吗？" type="warning" @ok="handleDelete(record.id)"
                >
                  <a-button type="text" size="small" status="danger">删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>
    </div>
  </div>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'Message' };
</script>

<script lang="ts" setup>
import {ref, reactive, onMounted, watch} from 'vue';
import {useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import { IconRefresh } from '@arco-design/web-vue/es/icon';
import {
  queryMessageList,
  markMessageRead,
  markAllMessagesRead,
  deleteMessage,
  MessageRecord,
} from '@/api/message';
import {useMessageNavigator} from '@/hooks/message-navigator';

const router = useRouter();
const {navigate} = useMessageNavigator();
const loading = ref(false);
const messageList = ref<MessageRecord[]>([]);

const sidebarGroups = [
  { key: 'all', title: '全部消息' },
  { key: 'testPlan', title: '测试计划' },
  { key: 'bug', title: 'BUG池' },
  { key: 'requirement', title: '需求池' },
  { key: 'testCase', title: '测试用例' },
];
const activeGroup = ref('all');
const activeTab = ref('all');
const selectedKeys = ref<number[]>([]);

function selectCategory(group: string) {
  activeGroup.value = group;
  selectedKeys.value = [];
  pagination.current = 1;
  loadData();
}

function onTabChange() {
  selectedKeys.value = [];
  pagination.current = 1;
  loadData();
}

function getParams() {
  const params: { bizType?: string; isRead?: number; isMention?: boolean } = {};
  if (activeGroup.value !== 'all') params.bizType = activeGroup.value;
  if (activeTab.value === 'unread') params.isRead = 0;
  else if (activeTab.value === 'read') params.isRead = 1;
  if (activeTab.value === 'mention') params.isMention = true;
  return params;
}

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
});

const handlePageChange = (page: number) => {
  pagination.current = page;
  loadData();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadData();
};

const loadData = async () => {
  loading.value = true;
  try {
    const p = getParams();
    const res: any = await queryMessageList(pagination.current, pagination.pageSize, p.isRead, p.bizType, p.isMention);
    if (res.data) {
      messageList.value = (res.data.records || []).map((item: MessageRecord) => ({
        ...item,
      }));
      pagination.total = res.data.total || 0;
      pagination.current = res.data.current || 1;
      pagination.pageSize = res.data.size || 10;
    }
  } catch (e) {
    Message.error('加载消息列表失败');
  } finally {
    loading.value = false;
  }
};

const handleRead = async (record: MessageRecord) => {
  try {
    await markMessageRead(record.id);
    record.isRead = 1;
    window.dispatchEvent(new CustomEvent('message-read'));
    Message.success('已标记为已读');
  } catch (e) {
    Message.error('标记已读失败');
  }
};

const handleAllRead = async () => {
  try {
    await markAllMessagesRead();
    messageList.value.forEach((item) => (item.isRead = 1));
    window.dispatchEvent(new CustomEvent('message-read'));
    Message.success('全部已读');
  } catch (e) {
    Message.error('全部已读失败');
  }
};

const handleBatchDelete = async () => {
  try {
    await Promise.all(selectedKeys.value.map((id) => deleteMessage(id)));
    messageList.value = messageList.value.filter((item) => !selectedKeys.value.includes(item.id));
    selectedKeys.value = [];
    window.dispatchEvent(new CustomEvent('message-read'));
    Message.success('批量删除成功');
  } catch (e) {
    Message.error('批量删除失败');
  }
};

const handleDelete = async (id: number) => {
  try {
    await deleteMessage(id);
    messageList.value = messageList.value.filter((item) => item.id !== id);
    window.dispatchEvent(new CustomEvent('message-read'));
    Message.success('删除成功');
  } catch (e) {
    Message.error('删除失败');
  }
};

onMounted(() => {
  loadData();
});
</script>

<style scoped lang="less">
.message-list-page {
  padding: 20px;
  height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}

.message-layout {
  display: flex;
  flex: 1;
  min-height: 0;
}

.sidebar-card {
  width: 140px;
  flex-shrink: 0;

  :deep(.arco-card-body) {
    padding: 8px 0;
    height: 100%;
    border-right: 1px solid var(--color-neutral-3);
  }

  .sidebar-item {
    padding: 8px 16px;
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

.general-card {
  flex: 1;
  border-radius: 4px;
}

</style>
