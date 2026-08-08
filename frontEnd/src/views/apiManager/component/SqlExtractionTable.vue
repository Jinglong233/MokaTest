<template>
  <div class="sql-extraction-table">
    <a-table :columns="columns" :data="tableData" bordered :pagination="false" size="small">
      <template #status="{ record, rowIndex }">
        <a-switch
            size="small"
            :checked-value="false"
            :unchecked-value="true"
            v-model="record.disabled"
            :disabled="disabled"
            @change="onDataChange"
        />
      </template>
      <template #variableName="{ record }">
        <a-input
            v-model="record.variableName"
            placeholder="变量名"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <template #columnName="{ record }">
        <a-input
            v-model="record.columnName"
            placeholder="结果列名"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <template #jsonPath="{ record }">
        <a-tooltip content="列值为 JSON 时按路径继续提取，如 $.data.id；留空取整列值" position="top">
          <a-input
              v-model="record.jsonPath"
              placeholder="选填，如 $.data.id"
              size="small"
              :disabled="disabled"
              @input="onDataChange"
          />
        </a-tooltip>
      </template>
      <template #rowIndex="{ record }">
        <a-input-number
            v-model="record.rowIndex"
            :min="0"
            placeholder="0"
            size="small"
            :disabled="disabled"
            @change="onDataChange"
            style="width: 100%"
        />
      </template>
      <template #defaultValue="{ record }">
        <a-input
            v-model="record.defaultValue"
            placeholder="默认值"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <template #description="{ record }">
        <a-input
            v-model="record.description"
            placeholder="描述"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <template #operation="{ rowIndex }">
        <a-popconfirm content="确认删除该提取规则？" position="left" @ok="deleteRow(rowIndex)">
          <a-button type="text" status="danger" size="small" :disabled="disabled">
            删除
          </a-button>
        </a-popconfirm>
      </template>
    </a-table>
    <a-button type="dashed" long :disabled="disabled" @click="addRow" class="add-btn">
      + 添加提取规则
    </a-button>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {SqlExtraction} from '@/types/domain/api/requestModel/SqlExtraction'

const columns = [
  {title: '启用', slotName: 'status', width: '70px'},
  {title: '变量名', slotName: 'variableName'},
  {title: '字段', slotName: 'columnName'},
  {title: 'JSON路径', slotName: 'jsonPath', width: '150px'},
  {title: '下标', slotName: 'rowIndex', width: '80px'},
  {title: '默认值', slotName: 'defaultValue'},
  {title: '描述', slotName: 'description'},
  {title: '操作', slotName: 'operation', width: '80px'}
]

const props = defineProps<{
  disabled?: boolean;
}>()

const tableData = ref<SqlExtraction[]>([])
const emit = defineEmits(['change', 'count'])

const meaningfulCount = computed(
    () => tableData.value.filter(r => r.variableName != null && String(r.variableName).trim() !== '').length
)
watch(meaningfulCount, (n) => emit('count', n), {immediate: true})

const addRow = () => {
  tableData.value.push({
    variableName: '',
    columnName: '',
    jsonPath: '',
    rowIndex: 0,
    defaultValue: '',
    disabled: false,
    description: ''
  })
  onDataChange()
}

const deleteRow = (index: number) => {
  tableData.value.splice(index, 1)
  onDataChange()
}

const onDataChange = () => {
  emit('change', tableData.value.filter(r => r.variableName?.trim()))
}

const setData = (data: SqlExtraction[]) => {
  tableData.value = data ? data.map(r => ({...r})) : []
}

const getData = (): SqlExtraction[] => {
  return tableData.value.map(r => ({...r}))
}

/**
 * 保存前校验：完全空的行忽略（提交时会被过滤）；
 * 有任一字段填写但必填项不完整的行视为错误，拦截保存。
 */
const validateAll = (): { valid: boolean; message: string } => {
  for (let i = 0; i < tableData.value.length; i++) {
    const r = tableData.value[i]
    const hasAny = [r.variableName, r.columnName, r.jsonPath, r.defaultValue, r.description]
        .some(v => v != null && String(v).trim() !== '')
    if (!hasAny) continue
    if (!r.variableName?.trim()) {
      return {valid: false, message: `提取规则第 ${i + 1} 行：请填写变量名`}
    }
    if (!r.columnName?.trim()) {
      return {valid: false, message: `提取规则第 ${i + 1} 行：请填写字段（结果列名）`}
    }
  }
  return {valid: true, message: ''}
}

defineExpose({setData, getData, validateAll})
</script>

<style scoped>
.sql-extraction-table {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sql-extraction-table :deep(.arco-table) {
  flex: 1;
  min-height: 0;
}

.add-btn {
  margin-top: 8px;
  flex-shrink: 0;
}
</style>
