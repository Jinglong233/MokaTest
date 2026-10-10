<template>
  <div class="api-case-list">
    <!-- 操作栏 -->
    <div class="case-list-header">
      <div class="case-count">{{ $t('api.case.totalCount', {count: caseList.length}) }}</div>
      <a-button type="primary" size="small" @click="refreshCases">
        <template #icon><icon-refresh /></template>
        {{ $t('api.common.refresh') }}
      </a-button>
    </div>

    <!-- 用例表格 -->
    <div class="case-table-wrapper">
      <a-table
        :data="caseList"
        :loading="loading"
        :pagination="false"
        size="small"
        row-key="id"
        :bordered="false"
      >
        <template #columns>
          <a-table-column :title="$t('api.case.colName')" data-index="apiName" :width="240">
            <template #cell="{ record }">
              <span class="case-name-cell">{{ record.apiName || $t('api.case.unnamed') }}</span>
            </template>
          </a-table-column>
          <a-table-column :title="$t('api.case.colMethod')" data-index="requestMethod" :width="100" align="center">
            <template #cell="{ record }">
              <a-tag
                size="small"
                :color="getMethodColor(record.requestMethod)"
              >
                {{ record.requestMethod || 'GET' }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column :title="$t('api.case.colCreateTime')" data-index="createTime" :width="160">
            <template #cell="{ record }">
              {{ formatTime(record.createTime) }}
            </template>
          </a-table-column>
          <a-table-column :title="$t('api.case.colUpdateTime')" data-index="updateTime" :width="160">
            <template #cell="{ record }">
              {{ formatTime(record.updateTime) }}
            </template>
          </a-table-column>
          <a-table-column :title="$t('qa.common.operation')" :width=120 align="center">
            <template #cell="{ record }">
              <a-space>
                <a-button v-permission="'auto:api:update'" type="text" size="mini" @click="editCase(record)">
                  <template #icon><icon-edit /></template>
                  {{ $t('api.common.edit') }}
                </a-button>
                <a-button
                  v-permission="'auto:api:delete'"
                  type="text"
                  size="mini"
                  status="danger"
                  @click="confirmDeleteCase(record)"
                >
                  <template #icon><icon-delete /></template>
                </a-button>
              </a-space>
            </template>
          </a-table-column>
        </template>
        <template #empty>
          <a-empty :description="$t('api.case.empty')" />
        </template>
      </a-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { IconRefresh, IconDelete, IconEdit } from '@arco-design/web-vue/es/icon'
import { getCases, deleteApi } from '@/api/MyApi/apiInterface'
import usePermission from '@/hooks/permission'
import { Message, Modal } from '@arco-design/web-vue'
import { ApiRequest } from '@/types/domain/api/ApiRequest'
import { useI18n } from 'vue-i18n'

interface Props {
  sourceId?: number
}

const { t } = useI18n()

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'editCase', data: ApiRequest): void
}>()

const permission = usePermission()
const hasApiUpdatePermission = computed(() => permission.hasPermission('auto:api:update'))
const hasApiDeletePermission = computed(() => permission.hasPermission('auto:api:delete'))

const caseList = ref<ApiRequest[]>([])
const loading = ref(false)

const loadCases = async () => {
  if (!props.sourceId) return
  loading.value = true
  try {
    const res = await getCases(props.sourceId)
    if (res.code === 200) {
      caseList.value = res.data || []
    }
  } catch (e) {
    console.error('加载用例列表失败', e)
  } finally {
    loading.value = false
  }
}

const refreshCases = () => {
  loadCases()
}

const deleteCase = async (id?: number) => {
  if (!id) return
  const res = await deleteApi(id)
  if (res.code === 200) {
    Message.success({ content: t('api.common.deleteSuccess'), duration: 2000 })
    loadCases()
  } else {
    Message.error({ content: res.msg || t('api.common.deleteFailed'), duration: 2000 })
  }
}

const confirmDeleteCase = (item: ApiRequest) => {
  Modal.confirm({
    title: t('api.case.confirmDeleteTitle'),
    content: t('api.case.confirmDeleteContent', {name: item.apiName || t('api.case.unnamed')}),
    okText: t('qa.common.delete'),
    cancelText: t('qa.common.cancel'),
    okButtonProps: { status: 'danger' },
    onOk: () => {
      deleteCase(item.id)
    }
  })
}

const editCase = (item: ApiRequest) => {
  emit('editCase', item)
}

const formatTime = (time?: Date | string) => {
  if (!time) return ''
  const date = new Date(time)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

const getMethodColor = (method?: string) => {
  switch ((method || 'GET').toUpperCase()) {
    case 'GET': return '#0e8a16'
    case 'POST': return '#0969da'
    case 'PUT': return '#9a6700'
    case 'DELETE': return '#cf222e'
    case 'PATCH': return '#8957e5'
    default: return '#666'
  }
}

onMounted(() => {
  loadCases()
})

watch(() => props.sourceId, () => {
  loadCases()
})

defineExpose({
  refreshCases,
  loadCases,
})
</script>

<style scoped lang="less">
.api-case-list {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.case-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e5e5;
  flex-shrink: 0;

  .case-count {
    font-size: 13px;
    color: #666;
  }
}

.case-table-wrapper {
  flex: 1;
  overflow: auto;
  padding: 8px;
}

.case-name-cell {
  font-weight: 500;
  color: #333;
}
</style>
