<!--
  API 断言规则配置表格组件

  功能说明：提供接口断言规则的可视化配置界面，支持配置响应体、响应头、状态码、响应时间等断言目标

  表格列：
    1. 启用      - 开关控制每条断言是否生效
    2. 断言目标  - BODY（响应体）、HEADER（响应头）、STATUS_CODE（状态码）、RESPONSE_TIME（响应时间）、CUSTOM（自定义）
    3. 字段      - 根据断言目标自动适配：JSONPath（BODY）、响应头名称（HEADER）、自动禁用（STATUS_CODE/RESPONSE_TIME）
    4. 断言关系  - 等于、不等于、包含、不包含、大于、小于、大于等于、小于等于、正则匹配
    5. 预期值    - 用于与实际值比较的预期结果
    6. 操作      - 删除单条断言规则

  智能适配：
    - STATUS_CODE / RESPONSE_TIME 类型不需要字段，自动禁用字段输入，关系限定为数值比较
    - HEADER 类型关系限定为等于、包含、正则
    - BODY / CUSTOM 类型支持全部关系

  事件：
    @change - 数据变更时触发，通知父组件更新修改状态

  暴露方法（通过 defineExpose）：
    - getData()      : 获取所有启用的有效断言规则
    - setData(data)  : 设置断言规则数据（初始化回显）
    - validateAll()  : 校验所有启用的规则，返回 { valid, message }

  @author JingLong
  @since 2026-05-26
-->
<template>
  <div class="assertion-table">
    <div class="assertion-table-scroll">
      <a-table :columns="columns" :data="tableData" bordered :pagination="false">
      <!-- 启用开关：false 表示启用，true 表示禁用（与提取规则保持一致） -->
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
      <!-- 断言类型选择 -->
      <template #assertType="{ record, rowIndex }">
        <a-select
            v-model="record.apiAssertType"
            placeholder="选择断言目标"
            size="small"
            style="width: 120px"
            :disabled="disabled"
            @change="(val) => onAssertTypeChange(record, val)"
        >
          <a-option
              v-for="type in assertTypeOptions"
              :key="type"
              :value="type"
          >
            {{ assertTypeLabel(type) }}
          </a-option>
        </a-select>
      </template>
      <!-- 字段输入 -->
      <template #field="{ record, rowIndex }">
        <a-input
            v-model="record.field"
            :placeholder="fieldPlaceholder(record.apiAssertType)"
            :disabled="isFieldDisabled(record.apiAssertType) || disabled"
            :status="fieldError(record) ? 'error' : undefined"
            size="small"
            @blur="validateRow(record)"
            @input="onDataChange"
        />
      </template>
      <!-- 断言关系选择 -->
      <template #relationship="{ record, rowIndex }">
        <a-select
            v-model="record.assertRelationship"
            placeholder="选择关系"
            size="small"
            style="width: 120px"
            :disabled="disabled"
            @change="onDataChange"
        >
          <a-option
              v-for="rel in availableRelationships(record.apiAssertType)"
              :key="rel"
              :value="rel"
          >
            {{ relationshipLabel(rel) }}
          </a-option>
        </a-select>
      </template>
      <!-- 预期值输入 -->
      <template #assertValue="{ record, rowIndex }">
        <ExprSuggestInput
            v-model="record.assertValue"
            placeholder="预期值（输入 @ 可插入函数）"
            :disabled="disabled"
            @update:model-value="onDataChange"
            @commit="() => { validateRow(record); onDataChange(); }"
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
      + 添加断言规则
    </a-button>
  </div>
</template>

<script setup lang="ts">
/**
 * API 断言规则配置组件脚本
 *
 * 设计思路：
 *   1. 表格内联编辑，用户直接在表格行中完成断言规则的增删改
 *   2. 断言目标变更时自动适配字段和关系选项，减少用户误配
 *   3. 启用/禁用通过 disabled 字段控制（true=禁用，false=启用），与提取规则保持一致的交互逻辑
 *   4. 校验在保存时统一触发（validateAll），避免打断用户输入体验
 *
 * 数据流：
 *   父组件通过 setData 初始化数据 -> 用户在表格中编辑 -> 触发 @change 通知父组件有修改
 *   -> 父组件保存时调用 validateAll 校验 -> 校验通过后调用 getData 获取有效数据
 */
import {ref, computed, watch} from 'vue'
import ExprSuggestInput from './ExprSuggestInput.vue'
import {AssertParameter} from '@/types/domain/api/requestModel/AssertParameter'
import {ApiAssertType} from '@/types/domain/api/apiEnum/ApiAssertType'
import {AssertRelationship} from '@/types/enum/condation/AssertRelationship'

/**
 * 表格列定义
 * slotName 对应 template 中的具名插槽
 */
const columns = [
  {title: '启用', slotName: 'status', width: '8%'},
  {title: '断言目标', slotName: 'assertType', width: '14%'},
  {title: '字段', slotName: 'field', width: '22%'},
  {title: '断言关系', slotName: 'relationship', width: '14%'},
  {title: '预期值', slotName: 'assertValue', width: '22%'},
  {title: '操作', slotName: 'operation', width: '10%'}
]

const props = defineProps<{
  disabled?: boolean;
}>()

/** 表格数据源，每条数据对应一个 AssertParameter 实例 */
const tableData = ref<AssertParameter[]>([])

/** 断言目标选项列表（字符串枚举，Object.values 直接返回枚举值；SCHEMA 由响应定义自动产生，不允许手动配置） */
const assertTypeOptions = (Object.values(ApiAssertType) as ApiAssertType[])
    .filter(t => t !== ApiAssertType.SCHEMA)

/** 定义对外事件：change 在数据发生变更时触发；count 实时上报有效断言个数 */
const emit = defineEmits(['change', 'count'])

// 有效断言个数（预期值非空），用于外层 tab 徽标，实时上报
const meaningfulCount = computed(
    () => tableData.value.filter(r => r.assertValue != null && String(r.assertValue).trim() !== '').length
)
watch(meaningfulCount, (n) => emit('count', n), {immediate: true})

/**
 * 获取断言类型的中文标签
 */
const assertTypeLabel = (type: ApiAssertType): string => {
  const labels: Record<string, string> = {
    [ApiAssertType.BODY]: '响应体',
    [ApiAssertType.HEADER]: '响应头',
    [ApiAssertType.STATUS_CODE]: '状态码',
    [ApiAssertType.RESPONSE_TIME]: '响应时间',
    [ApiAssertType.CUSTOM]: '自定义',
    [ApiAssertType.SCHEMA]: '结构校验'
  }
  return labels[type] || type
}

/**
 * 获取断言关系的中文标签
 */
const relationshipLabel = (rel: AssertRelationship): string => {
  const labels: Record<string, string> = {
    [AssertRelationship.EQUALS]: '等于',
    [AssertRelationship.NOT_EQUALS]: '不等于',
    [AssertRelationship.CONTAINS]: '包含',
    [AssertRelationship.NOT_CONTAINS]: '不包含',
    [AssertRelationship.GT]: '大于',
    [AssertRelationship.LT]: '小于',
    [AssertRelationship.GE]: '大于等于',
    [AssertRelationship.LE]: '小于等于',
    [AssertRelationship.REGULAR]: '正则匹配'
  }
  return labels[rel] || rel
}

/**
 * 根据断言类型返回字段输入框的占位提示
 */
const fieldPlaceholder = (type?: ApiAssertType): string => {
  switch (type) {
    case ApiAssertType.BODY:
      return 'JSONPath，如 $.data.code'
    case ApiAssertType.HEADER:
      return '响应头名称，如 Content-Type'
    case ApiAssertType.STATUS_CODE:
      return '无需填写'
    case ApiAssertType.RESPONSE_TIME:
      return '无需填写（单位：ms）'
    case ApiAssertType.CUSTOM:
      return '自定义表达式'
    default:
      return '字段'
  }
}

/**
 * 判断某断言类型是否需要填写字段
 * STATUS_CODE 和 RESPONSE_TIME 不需要字段
 */
const isFieldDisabled = (type?: ApiAssertType): boolean => {
  return type === ApiAssertType.STATUS_CODE || type === ApiAssertType.RESPONSE_TIME
}

/**
 * 根据断言类型返回可用的断言关系列表
 * 不同断言目标适用的比较关系不同
 */
const availableRelationships = (type?: ApiAssertType): AssertRelationship[] => {
  const all = [
    AssertRelationship.EQUALS,
    AssertRelationship.NOT_EQUALS,
    AssertRelationship.CONTAINS,
    AssertRelationship.NOT_CONTAINS,
    AssertRelationship.GT,
    AssertRelationship.LT,
    AssertRelationship.GE,
    AssertRelationship.LE,
    AssertRelationship.REGULAR
  ]
  switch (type) {
    case ApiAssertType.STATUS_CODE:
    case ApiAssertType.RESPONSE_TIME:
      // 状态码和响应时间只支持数值比较和等于
      return [
        AssertRelationship.EQUALS,
        AssertRelationship.NOT_EQUALS,
        AssertRelationship.GT,
        AssertRelationship.LT,
        AssertRelationship.GE,
        AssertRelationship.LE
      ]
    case ApiAssertType.HEADER:
      // 响应头支持等于、包含、正则
      return [
        AssertRelationship.EQUALS,
        AssertRelationship.NOT_EQUALS,
        AssertRelationship.CONTAINS,
        AssertRelationship.NOT_CONTAINS,
        AssertRelationship.REGULAR
      ]
    case ApiAssertType.BODY:
    case ApiAssertType.CUSTOM:
    default:
      return all
  }
}

/**
 * 判断字段是否非法（用于标红输入框）
 * STATUS_CODE 和 RESPONSE_TIME 不需要字段，其他类型必填
 */
const fieldError = (record: AssertParameter): boolean => {
  if (isFieldDisabled(record.apiAssertType)) return false
  const field = record.field?.trim()
  if (!field) return false
  return false
}

/**
 * 判断预期值是否非法（用于标红输入框）
 */
const assertValueError = (record: AssertParameter): boolean => {
  const value = record.assertValue?.trim()
  if (!value) return false
  return false
}

/**
 * 校验单行数据是否有效
 * 返回错误信息，空字符串表示有效
 */
const validateRow = (record: AssertParameter): string => {
  // 字段校验（STATUS_CODE 和 RESPONSE_TIME 除外）
  if (!isFieldDisabled(record.apiAssertType)) {
    const field = record.field?.trim()
    if (!field) {
      return '字段不能为空'
    }
  }

  // 预期值必填
  const value = record.assertValue?.trim()
  if (!value) {
    return '预期值不能为空'
  }

  // 状态码预期值必须是数字
  if (record.apiAssertType === ApiAssertType.STATUS_CODE) {
    if (isNaN(Number(value))) {
      return '状态码预期值必须是数字'
    }
  }

  // 响应时间预期值必须是数字
  if (record.apiAssertType === ApiAssertType.RESPONSE_TIME) {
    if (isNaN(Number(value))) {
      return '响应时间预期值必须是数字'
    }
  }

  return ''
}

/**
 * 断言类型变更时的处理
 * 1. STATUS_CODE / RESPONSE_TIME 清空字段
 * 2. 重置断言关系到默认值
 */
const onAssertTypeChange = (record: AssertParameter, type: ApiAssertType) => {
  record.apiAssertType = type
  if (isFieldDisabled(type)) {
    record.field = ''
  }
  // 自动选择一个合适的默认关系
  const available = availableRelationships(type)
  if (available.length > 0 && !available.includes(record.assertRelationship!)) {
    record.assertRelationship = available[0]
  }
  onDataChange()
}

/**
 * 添加一条新的断言规则
 * 默认使用 AssertParameter 构造方法的初始值（BODY + EQUALS）
 */
const addRow = () => {
  if (props.disabled) return;
  tableData.value.push(new AssertParameter())
  emit('change')
}

/**
 * 删除指定索引的断言规则
 * @param index 表格行索引
 */
const deleteRow = (index: number) => {
  if (props.disabled) return;
  tableData.value.splice(index, 1)
  emit('change')
}

/**
 * 数据变更时的统一处理
 * 触发 change 事件通知父组件，用于更新未保存修改标记
 */
const onDataChange = () => {
  emit('change')
}

/**
 * 获取有效的断言规则数据
 * 过滤掉禁用的和校验不通过的数据，供父组件保存时调用
 */
const getData = (): AssertParameter[] => {
  return tableData.value.filter(item => {
    // 跳过禁用的规则
    if (item.disabled) return false

    // 字段校验（STATUS_CODE 和 RESPONSE_TIME 除外）
    if (!isFieldDisabled(item.apiAssertType)) {
      const field = item.field?.trim()
      if (!field) return false
    }

    // 预期值必填
    const value = item.assertValue?.trim()
    if (!value) return false

    return true
  })
}

/**
 * 设置断言规则数据，用于父组件初始化回显
 * @param data 断言规则列表，为空数组时清空表格
 */
const setData = (data: AssertParameter[]) => {
  const incoming = data && data.length ? data : []
  // 父组件 v-model 回流时会用「过滤后的有效数据」再次 setData，若与当前有效数据一致，
  // 则保留当前编辑状态（含刚点「添加」、还没填写的空行），避免空行被清掉导致「第一次添加无反应」。
  if (JSON.stringify(incoming) === JSON.stringify(getData())) {
    return
  }
  tableData.value = [...incoming]
}

/**
 * 校验所有启用的断言规则
 * 返回校验结果和错误信息
 */
const validateAll = (): { valid: boolean; message: string } => {
  for (let i = 0; i < tableData.value.length; i++) {
    const record = tableData.value[i]
    // 跳过禁用的
    if (record.disabled) continue

    const error = validateRow(record)
    if (error) {
      return {valid: false, message: `第 ${i + 1} 条断言规则：${error}`}
    }
  }
  return {valid: true, message: ''}
}

/**
 * 暴露给父组件的方法
 * getData    : 获取所有启用的有效断言规则
 * setData    : 初始化表格数据
 * addRow     : 添加一条空规则
 * validateAll: 校验所有规则并返回结果
 */
defineExpose({getData, setData, addRow, validateAll})
</script>

<style scoped lang="less">
.assertion-table {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.assertion-table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.assertion-table .add-btn {
  flex-shrink: 0;
  margin-top: 10px;
}
</style>
