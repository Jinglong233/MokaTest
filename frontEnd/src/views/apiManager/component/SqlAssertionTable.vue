<template>
  <div class="sql-assertion-table">
    <a-table :columns="columns" :data="tableData" bordered :pagination="false" size="small">
      <template #status="{ record }">
        <a-switch
            size="small"
            :checked-value="false"
            :unchecked-value="true"
            v-model="record.disabled"
            :disabled="disabled"
            @change="onDataChange"
        />
      </template>
      <template #columnName="{ record }">
        <a-input v-model="record.columnName" placeholder="列名" size="small" :disabled="disabled" @input="onDataChange" />
      </template>
      <template #jsonPath="{ record }">
        <a-tooltip content="列值为 JSON 时按路径取嵌套值后再比较，如 $.data.id；留空取整列值" position="top">
          <a-input v-model="record.jsonPath" placeholder="选填，如 $.data.id" size="small" :disabled="disabled" @input="onDataChange" />
        </a-tooltip>
      </template>
      <template #condition="{ record }">
        <a-select v-model="record.condition" placeholder="条件" size="small" :disabled="disabled" @change="onDataChange" style="width: 100%">
          <a-option value="EQUALS">等于</a-option>
          <a-option value="NOT_EQUALS">不等于</a-option>
          <a-option value="CONTAINS">包含</a-option>
          <a-option value="NOT_CONTAINS">不包含</a-option>
          <a-option value="GT">大于</a-option>
          <a-option value="LT">小于</a-option>
          <a-option value="GE">大于等于</a-option>
          <a-option value="LE">小于等于</a-option>
          <a-option value="REGULAR">正则匹配</a-option>
        </a-select>
      </template>
      <template #expectedValue="{ record }">
        <a-input v-model="record.expectedValue" placeholder="期望值" size="small" :disabled="disabled" @input="onDataChange" />
      </template>
      <template #rowIndex="{ record }">
        <a-input-number v-model="record.rowIndex" :min="0" placeholder="0" size="small" :disabled="disabled" @change="onDataChange" style="width: 100%" />
      </template>
      <template #description="{ record }">
        <a-input v-model="record.description" placeholder="描述" size="small" :disabled="disabled" @input="onDataChange" />
      </template>
      <template #operation="{ rowIndex }">
        <a-popconfirm content="确认删除该断言？" position="left" @ok="deleteRow(rowIndex)">
          <a-button type="text" status="danger" size="small" :disabled="disabled">删除</a-button>
        </a-popconfirm>
      </template>
    </a-table>
    <a-button type="dashed" long :disabled="disabled" @click="addRow" class="add-btn">+ 添加断言</a-button>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {SqlAssertion} from '@/types/domain/api/requestModel/SqlAssertion'

const columns = [
  {title: '启用', slotName: 'status', width: '60px'},
  {title: '字段', slotName: 'columnName'},
  {title: 'JSON路径', slotName: 'jsonPath', width: '150px'},
  {title: '条件', slotName: 'condition', width: '110px'},
  {title: '值', slotName: 'expectedValue'},
  {title: '下标', slotName: 'rowIndex', width: '80px'},
  {title: '描述', slotName: 'description'},
  {title: '操作', slotName: 'operation', width: '70px'}
]

const props = defineProps<{ disabled?: boolean }>()
const tableData = ref<SqlAssertion[]>([])
const emit = defineEmits(['change', 'count'])

const meaningfulCount = computed(() => tableData.value.filter(r => r.columnName?.trim()).length)
watch(meaningfulCount, (n) => emit('count', n), {immediate: true})

const addRow = () => {
  tableData.value.push({ columnName: '', jsonPath: '', condition: 'EQUALS', expectedValue: '', rowIndex: 0, disabled: false, description: '' })
  onDataChange()
}
const deleteRow = (index: number) => { tableData.value.splice(index, 1); onDataChange() }
const onDataChange = () => emit('change', tableData.value.filter(r => r.columnName?.trim()))

const setData = (data: SqlAssertion[]) => { tableData.value = data ? data.map(r => ({...r})) : [] }
const getData = (): SqlAssertion[] => tableData.value.map(r => ({...r}))

/**
 * 保存前校验：完全空的行忽略（提交时会被过滤）；
 * 有任一字段填写但必填项不完整的行视为错误，拦截保存。
 */
const validateAll = (): { valid: boolean; message: string } => {
  for (let i = 0; i < tableData.value.length; i++) {
    const r = tableData.value[i]
    const hasAny = [r.columnName, r.jsonPath, r.expectedValue, r.description]
        .some(v => v != null && String(v).trim() !== '')
    if (!hasAny) continue
    if (!r.columnName?.trim()) {
      return {valid: false, message: `断言第 ${i + 1} 行：请填写字段（列名）`}
    }
    if (!r.condition) {
      return {valid: false, message: `断言第 ${i + 1} 行：请选择断言条件`}
    }
    if (r.expectedValue == null || String(r.expectedValue).trim() === '') {
      return {valid: false, message: `断言第 ${i + 1} 行：请填写期望值`}
    }
  }
  return {valid: true, message: ''}
}

defineExpose({setData, getData, validateAll})
</script>

<style scoped>
.sql-assertion-table { height: 100%; display: flex; flex-direction: column; overflow: hidden; }
.sql-assertion-table :deep(.arco-table) { flex: 1; min-height: 0; }
.add-btn { margin-top: 8px; flex-shrink: 0; }
</style>
