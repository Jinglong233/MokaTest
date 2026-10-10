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
        <a-input v-model="record.columnName" :placeholder="$t('api.sqlAssertion.columnNamePlaceholder')" size="small" :disabled="disabled" @input="onDataChange" />
      </template>
      <template #jsonPath="{ record }">
        <a-tooltip :content="$t('api.sqlAssertion.jsonPathTip')" position="top">
          <a-input v-model="record.jsonPath" :placeholder="$t('api.sqlAssertion.jsonPathPlaceholder')" size="small" :disabled="disabled" @input="onDataChange" />
        </a-tooltip>
      </template>
      <template #condition="{ record }">
        <a-select v-model="record.condition" :placeholder="$t('api.sqlAssertion.conditionPlaceholder')" size="small" :disabled="disabled" @change="onDataChange" style="width: 100%">
          <a-option value="EQUALS">{{ $t('api.condition.equals') }}</a-option>
          <a-option value="NOT_EQUALS">{{ $t('api.condition.notEquals') }}</a-option>
          <a-option value="CONTAINS">{{ $t('api.condition.contains') }}</a-option>
          <a-option value="NOT_CONTAINS">{{ $t('api.condition.notContains') }}</a-option>
          <a-option value="GT">{{ $t('api.condition.gt') }}</a-option>
          <a-option value="LT">{{ $t('api.condition.lt') }}</a-option>
          <a-option value="GE">{{ $t('api.condition.ge') }}</a-option>
          <a-option value="LE">{{ $t('api.condition.le') }}</a-option>
          <a-option value="REGULAR">{{ $t('api.condition.regex') }}</a-option>
        </a-select>
      </template>
      <template #expectedValue="{ record }">
        <a-input v-model="record.expectedValue" :placeholder="$t('api.sqlAssertion.expectedValuePlaceholder')" size="small" :disabled="disabled" @input="onDataChange" />
      </template>
      <template #rowIndex="{ record }">
        <a-input-number v-model="record.rowIndex" :min="0" placeholder="0" size="small" :disabled="disabled" @change="onDataChange" style="width: 100%" />
      </template>
      <template #description="{ record }">
        <a-input v-model="record.description" :placeholder="$t('api.sqlAssertion.descPlaceholder')" size="small" :disabled="disabled" @input="onDataChange" />
      </template>
      <template #operation="{ rowIndex }">
        <a-popconfirm :content="$t('api.sqlAssertion.confirmDelete')" position="left" @ok="deleteRow(rowIndex)">
          <a-button type="text" status="danger" size="small" :disabled="disabled">{{ $t('qa.common.delete') }}</a-button>
        </a-popconfirm>
      </template>
    </a-table>
    <a-button type="dashed" long :disabled="disabled" @click="addRow" class="add-btn">{{ $t('api.sqlAssertion.add') }}</a-button>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {SqlAssertion} from '@/types/domain/api/requestModel/SqlAssertion'

const {t} = useI18n()

const columns = computed(() => [
  {title: t('api.sqlAssertion.colEnabled'), slotName: 'status', width: '60px'},
  {title: t('api.sqlAssertion.colField'), slotName: 'columnName'},
  {title: t('api.sqlAssertion.colJsonPath'), slotName: 'jsonPath', width: '150px'},
  {title: t('api.sqlAssertion.colCondition'), slotName: 'condition', width: '110px'},
  {title: t('api.sqlAssertion.colValue'), slotName: 'expectedValue'},
  {title: t('api.sqlAssertion.colRowIndex'), slotName: 'rowIndex', width: '80px'},
  {title: t('api.sqlAssertion.colDesc'), slotName: 'description'},
  {title: t('qa.common.operation'), slotName: 'operation', width: '70px'}
])

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
      return {valid: false, message: t('api.sqlAssertion.rowFieldRequired', {row: i + 1})}
    }
    if (!r.condition) {
      return {valid: false, message: t('api.sqlAssertion.rowConditionRequired', {row: i + 1})}
    }
    if (r.expectedValue == null || String(r.expectedValue).trim() === '') {
      return {valid: false, message: t('api.sqlAssertion.rowExpectedRequired', {row: i + 1})}
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
