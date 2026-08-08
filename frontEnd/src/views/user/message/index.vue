<template>
  <div class="message-list-page">
    <Breadcrumb :items="['menu.user', 'menu.user.message']" />

    <a-card class="general-card" :bordered="false">
      <template #title>
        我的消息
      </template>
      <template #extra>
        <a-space>
          <a-radio-group v-model="filterStatus" type="button">
            <a-radio value="all">全部</a-radio>
            <a-radio value="unread">未读</a-radio>
            <a-radio value="read">已读</a-radio>
          </a-radio-group>
          <a-button type="primary" @click="handleAllRead">全部已读</a-button>
        </a-space>
      </template>

      <a-table
          :data="messageList"
          :loading="loading"
          :pagination="pagination"
          row-key="id"
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
              <a-link :style="{ fontWeight: record.isRead ? 'normal' : 'bold' }" @click="handleViewDetail(record)">
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
  </div>
</template>

<script lang="ts" setup>
import {ref, reactive, onMounted, watch} from 'vue';
import {useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
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
const filterStatus = ref('all');
const detailVisible = ref(false);
const currentMessage = ref<MessageRecord | null>(null);

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  showTotal: true,
  showPageSize: true,
});

const getIsReadParam = () => {
  if (filterStatus.value === 'unread') return 0;
  if (filterStatus.value === 'read') return 1;
  return undefined;
};

const loadData = async () => {
  loading.value = true;
  try {
    const isRead = getIsReadParam();
    const res: any = await queryMessageList(pagination.current, pagination.pageSize, isRead);
    if (res.data) {
      messageList.value = (res.data.records || []).map((item: MessageRecord) => ({
        ...item,
        snapshot: parseSnapshot(item.extraData),
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

const handlePageChange = (page: number) => {
  pagination.current = page;
  loadData();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadData();
};

const handleFilterChange = () => {
  pagination.current = 1;
  loadData();
};

const parseSnapshot = (extraData?: string): Record<string, any> | undefined => {
  if (!extraData) return undefined;
  try {
    return JSON.parse(extraData);
  } catch (e) {
    return undefined;
  }
};

watch(filterStatus, () => {
  handleFilterChange();
});

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

const handleViewDetail = async (record: MessageRecord) => {
  currentMessage.value = record;
  detailVisible.value = true;
  if (!record.isRead) {
    await handleRead(record);
  }
};

const handleGoToSource = async () => {
  if (!currentMessage.value) return;
  await navigate(currentMessage.value, {closePopover: false});
};

onMounted(() => {
  loadData();
});
</script>

<style scoped lang="less">
.message-list-page {
  padding: 20px;
}

.general-card {
  border-radius: 4px;
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
