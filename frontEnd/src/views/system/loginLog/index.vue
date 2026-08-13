<template>
  <div class="login-log-page">
    <a-card class="general-card" title="登录日志">
      <!-- 筛选栏 -->
      <a-row class="toolbar" justify="space-between" style="margin-bottom: 16px;">
        <a-space wrap>
          <a-select
            v-model="searchStatus"
            placeholder="状态"
            allow-clear
            style="width: 100px"
          >
            <a-option value="SUCCESS">成功</a-option>
            <a-option value="FAIL">失败</a-option>
          </a-select>
          <a-input
            v-model="searchKeyword"
            placeholder="搜索用户名或昵称"
            allow-clear
            style="width: 180px"
            @press-enter="handleSearch"
          />
          <a-input
            v-model="searchIp"
            placeholder="搜索IP"
            allow-clear
            style="width: 150px"
            @press-enter="handleSearch"
          />
          <a-range-picker
            v-model="searchTimeRange"
            show-time
            format="YYYY-MM-DD HH:mm:ss"
            style="width: 340px"
          />
          <a-button type="primary" @click="handleSearch">
            <template #icon><icon-search /></template>
            查询
          </a-button>
          <a-button @click="handleReset">重置</a-button>
        </a-space>
        <a-space wrap>
          <a-button
            status="danger"
            :disabled="selectedIds.length === 0"
            @click="handleBatchDelete"
          >
            <template #icon><icon-delete /></template>
            批量删除
          </a-button>
          <a-popconfirm
            content="确定清空全部登录日志吗？该操作不可恢复！"
            type="error"
            @ok="handleClear"
          >
            <a-button status="danger">
              <template #icon><icon-eraser /></template>
              清空
            </a-button>
          </a-popconfirm>
          <a-button @click="handleExport">
            <template #icon><icon-download /></template>
            导出
          </a-button>
        </a-space>
      </a-row>

      <!-- 表格 -->
      <LoadError v-if="loadError" @retry="loadList" />
      <a-table
        v-else
        v-model:selected-keys="selectedIds"
        :data="logList"
        :loading="loading"
        :pagination="pagination"
        :row-selection="{ type: 'checkbox', showCheckedAll: true }"
        :scroll="{ x: 1300, y: tableHeight }"
        row-key="id"
        stripe
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="时间" data-index="operateTime" :width="165" />
          <a-table-column title="用户" data-index="username" :width="170">
            <template #cell="{ record }">
              <a-space size="mini">
                <span>{{ record.nickname || record.username || '-' }}</span>
                <span v-if="record.nickname && record.username" style="color: #86909c; font-size: 12px;">
                  ({{ record.username }})
                </span>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="状态" data-index="status" :width="80" align="center">
            <template #cell="{ record }">
              <a-tag size="small" :color="record.status === 'SUCCESS' ? 'green' : 'red'">
                {{ record.status === 'SUCCESS' ? '成功' : '失败' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="失败原因" data-index="message" :width="130">
            <template #cell="{ record }">
              <span v-if="record.message" style="color: #f53f3f;">{{ record.message }}</span>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>
          <a-table-column title="IP" data-index="ip" :width="130">
            <template #cell="{ record }">
              <span style="font-family: monospace;">{{ record.ip || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="归属地" data-index="ipRegion" :width="160">
            <template #cell="{ record }">
              <a-tag v-if="record.ipRegion" size="small" :color="record.ipRegion === '内网IP' ? 'gray' : 'arcoblue'">
                {{ record.ipRegion }}
              </a-tag>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>
          <a-table-column title="浏览器" :width="110">
            <template #cell="{ record }">
              <a-tooltip :content="record.userAgent" position="tl">
                <span>{{ parseBrowser(record.userAgent) }}</span>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="操作系统" :width="110">
            <template #cell="{ record }">
              <a-tooltip :content="record.userAgent" position="tl">
                <span>{{ parseOs(record.userAgent) }}</span>
              </a-tooltip>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="80" align="center" fixed="right">
            <template #cell="{ record }">
              <a-popconfirm content="确定删除该条日志吗？" @ok="handleDelete(record)">
                <a-tooltip content="删除">
                  <a-button type="text" size="small" status="danger">
                    <template #icon><icon-delete /></template>
                  </a-button>
                </a-tooltip>
              </a-popconfirm>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, reactive } from 'vue';
import { Message } from '@arco-design/web-vue';
import dayjs from 'dayjs';
import {
  getLoginLogList,
  deleteLoginLog,
  batchDeleteLoginLog,
  clearLoginLog,
  exportLoginLog,
} from '@/api/MyApi/loginLog';
import { parseBrowser, parseOs } from '@/utils/uaParse';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';

const { loading, loadError, track } = useLoadState();
const logList = ref<any[]>([]);
const selectedIds = ref<number[]>([]);

// 表格体高度 = 视口高 - 顶栏/卡片头/筛选栏/分页器等固定占位，窗口变化时重算
const tableHeight = ref(500);
const calcTableHeight = () => {
  tableHeight.value = Math.max(300, window.innerHeight - 330);
};

const searchStatus = ref(undefined);
const searchKeyword = ref('');
const searchIp = ref('');
const searchTimeRange = ref<any[]>([]);

const pagination = reactive({
  total: 0,
  current: 1,
  pageSize: 20,
  showTotal: true,
  showJumper: true,
  showPageSize: true,
});

const buildParams = () => {
  const params: any = {
    status: searchStatus.value,
    keyword: searchKeyword.value || undefined,
    ip: searchIp.value || undefined,
  };
  if (searchTimeRange.value && searchTimeRange.value.length === 2) {
    // range-picker 可能返回 Date 对象，统一格式化成 MySQL 可识别的字符串
    params.startTime = dayjs(searchTimeRange.value[0]).format('YYYY-MM-DD HH:mm:ss');
    params.endTime = dayjs(searchTimeRange.value[1]).format('YYYY-MM-DD HH:mm:ss');
  }
  return params;
};

const loadList = async () => {
  const params: any = {
    ...buildParams(),
    pageNum: pagination.current,
    pageSize: pagination.pageSize,
  };
  const res: any = await track(getLoginLogList(params));
  if (res?.code === 200) {
    logList.value = res.data.list || [];
    pagination.total = res.data.total || 0;
    selectedIds.value = [];
  } else {
    loadError.value = true;
  }
};

const handleSearch = () => {
  pagination.current = 1;
  loadList();
};

const handleReset = () => {
  searchStatus.value = undefined;
  searchKeyword.value = '';
  searchIp.value = '';
  searchTimeRange.value = [];
  pagination.current = 1;
  loadList();
};

const handleDelete = async (record: any) => {
  const res: any = await deleteLoginLog(record.id);
  if (res?.code === 200) {
    Message.success('删除成功');
    loadList();
  } else {
    Message.error(res?.msg || '删除失败');
  }
};

const handleBatchDelete = async () => {
  if (selectedIds.value.length === 0) return;
  const res: any = await batchDeleteLoginLog(selectedIds.value);
  if (res?.code === 200) {
    Message.success(`已删除 ${selectedIds.value.length} 条日志`);
    loadList();
  } else {
    Message.error(res?.msg || '批量删除失败');
  }
};

const handleClear = async () => {
  const res: any = await clearLoginLog();
  if (res?.code === 200) {
    Message.success('已清空登录日志');
    pagination.current = 1;
    loadList();
  } else {
    Message.error(res?.msg || '清空失败');
  }
};

const handleExport = async () => {
  try {
    const res: any = await exportLoginLog(buildParams());
    const blobData = res.data || res;
    const blob = blobData instanceof Blob ? blobData : new Blob([blobData], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `login-log-${Date.now()}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    Message.error('导出失败');
  }
};

const handlePageChange = (current: number) => {
  pagination.current = current;
  loadList();
};

const handlePageSizeChange = (pageSize: number) => {
  pagination.pageSize = pageSize;
  pagination.current = 1;
  loadList();
};

onMounted(() => {
  calcTableHeight();
  window.addEventListener('resize', calcTableHeight);
  loadList();
});

onUnmounted(() => {
  window.removeEventListener('resize', calcTableHeight);
});
</script>

<style scoped>
.login-log-page {
  padding: 16px;
}

.text-ellipsis {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
