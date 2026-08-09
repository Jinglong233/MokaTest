<template>
  <div class="report-page">
    <Breadcrumb :items="['menu.testRun', 'menu.testRun.reportList']"/>
    <a-card class="report-card general-card" :title="$t('menu.list.reportList')">
      <div class="report-toolbar">
        <a-row>
          <a-col :flex="1">
            <a-form
              :model="reportSearchForm"
              :label-col-props="{ span: 6 }"
              :wrapper-col-props="{ span: 18 }"
              label-align="left"
          >
            <a-row :gutter="16">
              <a-col :span="8">
                <a-form-item
                    field="reportName"
                    label="报告名称"
                >
                  <a-input
                      v-model="reportSearchForm.reportName"
                      placeholder="输入报告名称"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item field="planName" label="任务名称">
                  <a-input
                      v-model="reportSearchForm.planName"
                      placeholder="输入任务名称"
                  />
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item
                    field="taskType"
                    label="任务类型"
                >
                  <a-select
                      v-model="reportSearchForm.taskType"
                      placeholder="选择任务类型"
                      allow-clear
                  >
                    <a-option :value="TaskType.TIMING">定时任务</a-option>
                    <a-option :value="TaskType.NORMAL">普通任务</a-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="8">
                <a-form-item
                    field="status"
                    label="状态"
                >
                  <a-select
                      v-model="reportSearchForm.status"
                      placeholder="选择状态"
                      allow-clear
                  >
                    <a-option value="1">已完成</a-option>
                    <a-option value="0">进行中</a-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
          </a-form>
        </a-col>
        <a-divider style="height: 84px" direction="vertical"/>
        <a-col :flex="'86px'" style="text-align: right">
          <a-space direction="vertical" :size="18">
            <a-button type="primary" @click="search">
              <template #icon>
                <icon-search/>
              </template>
              搜索
            </a-button>
            <a-button @click="reset">
              <template #icon>
                <icon-refresh/>
              </template>
              重置
            </a-button>
          </a-space>
        </a-col>
      </a-row>
      </div>
      <div class="table-wrapper">
        <a-table
            row-key="id"
            :loading="loading"
            :pagination="pagination"
            :columns="(cloneColumns as TableColumnData[])"
            :data="renderData"
            :bordered="false"
            :size="size"
            @page-change="onPageChange"
        >
        <template #index="{ rowIndex }">
          {{ rowIndex + 1 + (pagination.current - 1) * pagination.pageSize }}
        </template>
        <template #taskType="{record}">
          <a-tag v-if="record.taskType === TaskType.NORMAL" color="arcoblue">
            <template #icon>
              <icon-thumb-up/>
            </template>
            普通任务
          </a-tag>
          <a-tag v-else-if="record.taskType === TaskType.TIMING" color="arcoblue">
            <template #icon>
              <icon-clock-circle/>
            </template>
            定时任务
          </a-tag>
          <a-tag v-else color="red">
            <template #icon>
              <icon-question-circle/>
            </template>
            未知类型
          </a-tag>
        </template>

        <template #status="{record}">
          <a-tag v-if="record.status === 1" color="arcoblue">已完成</a-tag>
          <a-tag v-if="record.status === 0" color="arcoblue">
            <a-space align="center">
              <a-spin :size="14"/>
              进行中
            </a-space>
          </a-tag>
        </template>
        <template #options="{record }">
          <a-button :disabled="record.status===0" type="text" @click="getReportDetail(record.id)">
            查看
          </a-button>
          <a-divider v-if="record.status === 1" direction="vertical"/>
          <a-popconfirm
              v-if="record.status === 1"
              content="确认删除该报告吗？"
              type="warning"
              @ok="handleDeleteReport(record.id)"
          >
            <a-button v-permission="PERMISSIONS.REPORT_DELETE" type="text" status="danger">删除</a-button>
          </a-popconfirm>
        </template>

      </a-table>
      </div>
    </a-card>
  </div>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'ReportList' };
</script>

<script lang="ts" setup>
import {computed, reactive, ref, watch} from 'vue';
import {useI18n} from 'vue-i18n';
import useLoading from '@/hooks/loading';
import type {TableColumnData} from '@arco-design/web-vue/es/table/interface';
import cloneDeep from 'lodash/cloneDeep';
import {reportPageList, deleteReport} from "@/api/MyApi/report";
import {useRouter} from "vue-router";
import {ReportQueryDTO} from "@/types/dto/queryDTO/ReportQueryDTO";
import {BasePageQueryDTO} from "@/types/dto/queryDTO/BasePageQueryDTO";
import {TaskType} from "@/types/enum/task/TaskType";
import {useProjectStore} from "@/store";
import PERMISSIONS from "@/constants/permissions";
import {Message} from "@arco-design/web-vue";

type SizeProps = 'mini' | 'small' | 'medium' | 'large';
type Column = TableColumnData & { checked?: true };

const router = useRouter();
const {loading, setLoading} = useLoading(true);
const {t} = useI18n();
const renderData = ref<any[]>([]);
const reportSearchForm = ref<ReportQueryDTO>(new ReportQueryDTO());
const cloneColumns = ref<Column[]>([]);
const showColumns = ref<Column[]>([]);


const size = ref<SizeProps>('medium');

const basePagination: BasePageQueryDTO = {
  pageNum: 1,
  pageSize: 10,
};
const pagination = reactive({
  ...basePagination,
});
const columns = computed<TableColumnData[]>(() => [
  {
    title: t('序号'),
    dataIndex: 'index',
    slotName: 'index',
  },
  {
    title: '报告名称',
    dataIndex: 'reportName',
  },
  {
    title: '任务类型',
    dataIndex: 'taskType',
    slotName: 'taskType',
  },

  {
    title: '任务名称',
    dataIndex: 'planName',
  },
  {
    title: '执行者',
    dataIndex: 'executionUserName',
  },
  {
    title: '状态',
    dataIndex: 'status',
    slotName: 'status',
  },
  {
    title: '操作',
    slotName: 'options',
  }
]);

const fetchData = async () => {
  setLoading(true);
  try {
    reportSearchForm.value.projectId = useProjectStore().getProjectId;
    const result = await reportPageList(reportSearchForm.value);
    renderData.value = result.data.records;
    pagination.current = result.data.current;
    pagination.total = result.data.total;
  } catch (err) {
    // you can report use errorHandler or other
  } finally {
    setLoading(false);
  }
};

const search = async () => {
  await fetchData();
};
const onPageChange = async (current: number) => {
  reportSearchForm.value.pageNum = current;
  await fetchData();
};

fetchData();
const reset = async () => {
  reportSearchForm.value = new ReportQueryDTO();
  await fetchData();
};


// 获取报告详情
const getReportDetail = (reportId: any) => {
  router.push({
    name: 'ReportDetail',
    params: {
      reportId,
    }
  })
}

// 删除报告
const handleDeleteReport = async (reportId: number) => {
  try {
    const res = await deleteReport(reportId);
    if (res.data === true) {
      Message.success('删除成功');
      await fetchData();
    } else {
      Message.error('删除失败');
    }
  } catch (e: any) {
    Message.error(e?.response?.data?.msg || '删除失败');
  }
}


watch(
    () => columns.value,
    (val) => {
      cloneColumns.value = cloneDeep(val);
      cloneColumns.value.forEach((item, index) => {
        item.checked = true;
      });
      showColumns.value = cloneDeep(cloneColumns.value);
    },
    {deep: true, immediate: true}
);

watch(
    () => useProjectStore().getProjectId,
    (newProjectId, oldProjectId) => {
      // 当 projectId 变化时重新获取数据
      if (newProjectId) {
        fetchData();
      }
    },
    {immediate: true} // 立即执行一次
);
</script>


<style scoped lang="less">
.report-page {
  padding: 0 20px 20px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.report-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.report-card :deep(.arco-card-body) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.report-toolbar {
  flex: 0 0 auto;
  margin-bottom: 16px;
}

.table-wrapper {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

:deep(.arco-list-content) {
  overflow-x: hidden;
}

:deep(.arco-card-meta-title) {
  font-size: 14px;
}

</style>
