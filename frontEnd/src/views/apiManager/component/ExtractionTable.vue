<template>
  <div class="extraction-table">
    <div class="extraction-table-scroll">
      <a-table :columns="columns" :data="tableData" bordered :pagination="false">
      <!-- 状态开关 -->
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
      <!-- 提取类型选择 -->
      <template #type="{ record, rowIndex }">
        <a-select
            v-model="record.type"
            placeholder="选择类型"
            size="small"
            style="width: 120px"
            :disabled="disabled"
            @change="(val) => onTypeChange(record, val)"
        >
          <a-option
              v-for="type in Object.values(ExtractType)"
              :key="type"
              :value="type"
          >
            {{ extractTypeLabel(type) }}
          </a-option>
        </a-select>
      </template>
      <!-- 表达式输入 -->
      <template #expression="{ record, rowIndex }">
        <a-input
            v-model="record.expression"
            :placeholder="expressionPlaceholder(record.type)"
            :status="expressionError(record) ? 'error' : undefined"
            size="small"
            :disabled="disabled"
            @blur="validateRow(record)"
            @input="onDataChange"
        />
      </template>
      <!-- 变量名输入 -->
      <template #variableName="{ record, rowIndex }">
        <a-input
            v-model="record.variableName"
            placeholder="变量名"
            :status="variableNameError(record) ? 'error' : undefined"
            size="small"
            :disabled="disabled"
            @blur="validateRow(record)"
            @input="(val) => onVariableNameInput(record, val)"
        />
      </template>
      <!-- 默认值输入 -->
      <template #defaultValue="{ record, rowIndex }">
        <a-input
            v-model="record.defaultValue"
            placeholder="默认值"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <!-- 描述输入 -->
      <template #description="{ record, rowIndex }">
        <a-input
            v-model="record.description"
            placeholder="描述"
            size="small"
            :disabled="disabled"
            @input="onDataChange"
        />
      </template>
      <!-- 操作列 -->
      <template #operation="{ rowIndex }">
        <a-button type="text" status="danger" size="small" :disabled="disabled" @click="deleteRow(rowIndex)">
          删除
        </a-button>
      </template>
    </a-table>
    </div>
    <a-button type="dashed" long :disabled="disabled" @click="addRow" class="add-btn">
      + 添加提取规则
    </a-button>
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {ApiExtraction} from '@/types/domain/api/requestModel/ApiExtraction'
import {ExtractType} from '@/types/domain/api/apiEnum/ExtractType'

const columns = [
  {title: '启用', slotName: 'status', width: '8%'},
  {title: '提取类型', slotName: 'type', width: '14%'},
  {title: '提取表达式', slotName: 'expression', width: '22%'},
  {title: '变量名', slotName: 'variableName', width: '14%'},
  {title: '默认值', slotName: 'defaultValue', width: '14%'},
  {title: '描述', slotName: 'description', width: '18%'},
  {title: '操作', slotName: 'operation', width: '10%'}
]

const props = defineProps<{
  disabled?: boolean;
}>()

const tableData = ref<ApiExtraction[]>([])
const emit = defineEmits(['change', 'count'])

// 有效提取规则个数（变量名非空），用于外层 tab 徽标，实时上报
const meaningfulCount = computed(
    () => tableData.value.filter(r => r.variableName != null && String(r.variableName).trim() !== '').length
)
watch(meaningfulCount, (n) => emit('count', n), {immediate: true})

// 变量名校验正则：只能是英文、数字、下划线
const VARIABLE_NAME_REGEX = /^[a-zA-Z0-9_]+$/

/**
 * 获取提取类型的中文标签
 */
const extractTypeLabel = (type: ExtractType): string => {
  const labels: Record<string, string> = {
    [ExtractType.JSON_PATH]: 'JSONPath',
    [ExtractType.REGEX]: '正则',
    [ExtractType.HEADER]: '响应头',
    [ExtractType.COOKIE]: 'Cookie',
    [ExtractType.STATUS_CODE]: '状态码'
  }
  return labels[type] || type
}

/**
 * 根据提取类型返回表达式输入框的占位提示
 */
const expressionPlaceholder = (type?: ExtractType): string => {
  switch (type) {
    case ExtractType.JSON_PATH:
      return '$.data.token'
    case ExtractType.REGEX:
      return 'token:"([^"]+)"'
    case ExtractType.HEADER:
      return 'X-Request-Id'
    case ExtractType.COOKIE:
      return 'sessionId'
    case ExtractType.STATUS_CODE:
      return '无需填写'
    default:
      return '提取表达式'
  }
}

/**
 * 判断变量名是否非法（用于标红输入框）
 */
const variableNameError = (record: ApiExtraction): boolean => {
  const name = record.variableName?.trim()
  if (!name) return false
  return !VARIABLE_NAME_REGEX.test(name)
}

/**
 * 判断表达式是否非法（用于标红输入框）
 * STATUS_CODE 类型不需要表达式，其他类型必填
 */
const expressionError = (record: ApiExtraction): boolean => {
  if (record.type === ExtractType.STATUS_CODE) return false
  const expr = record.expression?.trim()
  if (!expr) return false
  return false
}

/**
 * 校验单行数据是否有效
 * 返回错误信息，空字符串表示有效
 */
const validateRow = (record: ApiExtraction): string => {
  const name = record.variableName?.trim()

  // 变量名必填
  if (!name) {
    return '变量名不能为空'
  }

  // 变量名格式校验
  if (!VARIABLE_NAME_REGEX.test(name)) {
    return '变量名只能包含英文、数字、下划线'
  }

  // 表达式必填（STATUS_CODE 除外）
  if (record.type !== ExtractType.STATUS_CODE) {
    const expr = record.expression?.trim()
    if (!expr) {
      return '提取表达式不能为空'
    }
  }

  return ''
}

/**
 * 变量名输入时自动过滤非法字符
 */
const onVariableNameInput = (record: ApiExtraction, value: string) => {
  // 过滤掉空格和非法字符
  record.variableName = value.replace(/[^a-zA-Z0-9_]/g, '')
  onDataChange()
}

/**
 * 提取类型变更时清空表达式
 */
const onTypeChange = (record: ApiExtraction, type: ExtractType) => {
  record.type = type
  if (type === ExtractType.STATUS_CODE) {
    record.expression = ''
  }
  onDataChange()
}

const addRow = () => {
  if (props.disabled) return;
  tableData.value.push(new ApiExtraction())
  emit('change')
}

const deleteRow = (index: number) => {
  if (props.disabled) return;
  tableData.value.splice(index, 1)
  emit('change')
}

const onDataChange = () => {
  emit('change')
}

/**
 * 获取有效的提取规则数据
 * 过滤掉禁用的和校验不通过的数据
 */
const getData = (): ApiExtraction[] => {
  return tableData.value.filter(item => {
    // 跳过禁用的规则
    if (item.disabled) return false

    // 跳过变量名为空的
    const name = item.variableName?.trim()
    if (!name) return false

    // 跳过变量名格式错误的
    if (!VARIABLE_NAME_REGEX.test(name)) return false

    // 跳过表达式为空的（STATUS_CODE 除外）
    if (item.type !== ExtractType.STATUS_CODE) {
      const expr = item.expression?.trim()
      if (!expr) return false
    }

    return true
  })
}

const setData = (data: ApiExtraction[]) => {
  const incoming = data && data.length ? data : []
  // 父组件 v-model 回流时会用「过滤后的有效数据」再次 setData，若与当前有效数据一致，
  // 则保留当前编辑状态（含刚点「添加」、还没填写的空行），避免空行被清掉导致「第一次添加无反应」。
  if (JSON.stringify(incoming) === JSON.stringify(getData())) {
    return
  }
  tableData.value = [...incoming]
}

/**
 * 校验所有启用的提取规则
 * 返回校验结果和错误信息
 */
const validateAll = (): { valid: boolean; message: string } => {
  for (let i = 0; i < tableData.value.length; i++) {
    const record = tableData.value[i]
    // 跳过禁用的
    if (record.disabled) continue

    const error = validateRow(record)
    if (error) {
      return { valid: false, message: `第 ${i + 1} 条提取规则：${error}` }
    }
  }
  return { valid: true, message: '' }
}

defineExpose({getData, setData, addRow, validateAll})
</script>

<style scoped lang="less">
.extraction-table {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.extraction-table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.extraction-table .add-btn {
  flex-shrink: 0;
  margin-top: 10px;
}
</style>
