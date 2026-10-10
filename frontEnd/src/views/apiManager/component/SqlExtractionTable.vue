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
            :placeholder="$t('api.sqlExtraction.variableNamePlaceholder')"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <template #columnName="{ record }">
        <a-input
            v-model="record.columnName"
            :placeholder="$t('api.sqlExtraction.columnNamePlaceholder')"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <template #jsonPath="{ record }">
        <a-tooltip :content="$t('api.sqlExtraction.jsonPathTip')" position="top">
          <a-input
              v-model="record.jsonPath"
              :placeholder="$t('api.sqlAssertion.jsonPathPlaceholder')"
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
            :placeholder="$t('api.sqlExtraction.defaultValuePlaceholder')"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <template #description="{ record }">
        <a-input
            v-model="record.description"
            :placeholder="$t('api.sqlAssertion.descPlaceholder')"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <template #operation="{ rowIndex }">
        <a-popconfirm :content="$t('api.sqlExtraction.confirmDelete')" position="left" @ok="deleteRow(rowIndex)">
          <a-button type="text" status="danger" size="small" :disabled="disabled">
            {{ $t('qa.common.delete') }}
          </a-button>
        </a-popconfirm>
      </template>
    </a-table>
    <a-button type="dashed" long :disabled="disabled" @click="addRow" class="add-btn">
      {{ $t('api.sqlExtraction.add') }}
    </a-button>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {SqlExtraction} from '@/types/domain/api/requestModel/SqlExtraction'

const {t} = useI18n()

const columns = computed(() => [
  {title: t('api.sqlAssertion.colEnabled'), slotName: 'status', width: '70px'},
  {title: t('api.sqlExtraction.colVariableName'), slotName: 'variableName'},
  {title: t('api.sqlAssertion.colField'), slotName: 'columnName'},
  {title: t('api.sqlAssertion.colJsonPath'), slotName: 'jsonPath', width: '150px'},
  {title: t('api.sqlAssertion.colRowIndex'), slotName: 'rowIndex', width: '80px'},
  {title: t('api.sqlExtraction.colDefaultValue'), slotName: 'defaultValue'},
  {title: t('api.sqlAssertion.colDesc'), slotName: 'description'},
  {title: t('qa.common.operation'), slotName: 'operation', width: '80px'}
])

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
      return {valid: false, message: t('api.sqlExtraction.rowVarRequired', {row: i + 1})}
    }
    if (!r.columnName?.trim()) {
      return {valid: false, message: t('api.sqlExtraction.rowFieldRequired', {row: i + 1})}
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
