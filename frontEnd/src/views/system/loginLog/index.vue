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
            style="width: 120px"
          >
            <a-option value="SUCCESS">成功</a-option>
            <a-option value="FAIL">失败</a-option>
          </a-select>
          <a-input
            v-model="searchKeyword"
            placeholder="搜索用户名或昵称"
            allow-clear
            style="width: 200px"
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
      </a-row>

      <!-- 表格 -->
      <LoadError v-if="loadError" @retry="loadList" />
      <a-table
        v-else
        :data="logList"
        :loading="loading"
        :pagination="pagination"
        row-key="id"
        stripe
        @page-change="handlePageChange"
        @page-size-change="handlePageSizeChange"
      >
        <template #columns>
          <a-table-column title="时间" data-index="operateTime" :width="170" />
          <a-table-column title="用户" data-index="username" :width="180">
            <template #cell="{ record }">
              <a-space size="mini">
                <span>{{ record.nickname || record.username || '-' }}</span>
                <span v-if="record.nickname && record.username" style="color: #86909c; font-size: 12px;">
                  ({{ record.username }})
                </span>
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="状态" data-index="status" :width="90">
            <template #cell="{ record }">
              <a-tag size="small" :color="record.status === 'SUCCESS' ? 'green' : 'red'">
                {{ record.status === 'SUCCESS' ? '成功' : '失败' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="失败原因" data-index="message" :width="140">
            <template #cell="{ record }">
              <span v-if="record.message" style="color: #f53f3f;">{{ record.message }}</span>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>
          <a-table-column title="IP" data-index="ip" :width="140">
            <template #cell="{ record }">
              <span style="font-family: monospace;">{{ record.ip || '-' }}</span>
            </template>
          </a-table-column>
          <a-table-column title="归属地" data-index="ipRegion" :width="180">
            <template #cell="{ record }">
              <a-tag v-if="record.ipRegion" size="small" :color="record.ipRegion === '内网IP' ? 'gray' : 'arcoblue'">
                {{ record.ipRegion }}
              </a-tag>
              <span v-else style="color: #86909c;">-</span>
            </template>
          </a-table-column>
          <a-table-column title="User Agent" data-index="userAgent">
            <template #cell="{ record }">
              <span
                class="text-ellipsis"
                style="font-size: 12px; color: #86909c;"
                :title="record.userAgent"
              >
                {{ record.userAgent || '-' }}
              </span>
            </template>
          </a-table-column>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue';
import { getLoginLogList } from '@/api/MyApi/loginLog';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';

const { loading, loadError, track } = useLoadState();
const logList = ref<any[]>([]);

const searchStatus = ref(undefined);
const searchKeyword = ref('');
const searchTimeRange = ref<string[]>([]);

const pagination = reactive({
  total: 0,
  current: 1,
  pageSize: 20,
  showTotal: true,
  showJumper: true,
  showPageSize: true,
});

const loadList = async () => {
  const params: any = {
    pageNum: pagination.current,
    pageSize: pagination.pageSize,
    status: searchStatus.value,
    keyword: searchKeyword.value || undefined,
  };
  if (searchTimeRange.value && searchTimeRange.value.length === 2) {
    params.startTime = searchTimeRange.value[0];
    params.endTime = searchTimeRange.value[1];
  }
  const res: any = await track(getLoginLogList(params));
  if (res?.code === 200) {
    logList.value = res.data.list || [];
    pagination.total = res.data.total || 0;
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
  searchTimeRange.value = [];
  pagination.current = 1;
  loadList();
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
  loadList();
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
