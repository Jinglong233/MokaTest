<template>
  <div class="parameter-table">
    <div class="parameter-table-scroll">
      <a-table :columns="columns" :data="tableData" bordered :pagination="false">
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
        <template #name="{ record, rowIndex }">
          <a-select
              v-if="isHeaderLikeContext"
              v-model="record.name"
              placeholder="参数名"
              allow-create
              allow-search
              :disabled="disabled"
              :options="commonHeaderOptions"
              @change="onDataChange"
          />
          <a-input
              v-else
              v-model="record.name"
              placeholder="参数名"
              :max-length="50"
              show-word-limit
              :disabled="disabled"
              @input="(val: string) => onNameInput(record, val)"
          />
        </template>
        <template #type="{ record }">
          <a-select
              v-model="record.type"
              size="small"
              :disabled="disabled"
              :style="{ width: '100%' }"
              @change="() => onTypeChange(record)"
          >
            <a-option v-for="opt in typeOptions" :key="opt.value" :value="opt.value">{{ opt.label }}
            </a-option>
          </a-select>
        </template>
        <template #value="{ record, rowIndex }">
          <div v-if="isMockPlaceholder(record)" class="mock-placeholder-cell">
            <a-tag
                class="mock-placeholder-tag"
                :closable="!disabled"
                @click="openMockPopover(rowIndex)"
                @close="clearMockConfig(record)"
            >
              {{ getMockPlaceholderLabel(record) }}
            </a-tag>
          </div>
          <div v-else-if="isFileType(record)" class="file-cell">
            <a-button
                type="text"
                size="mini"
                :loading="fileUploading && selectedRowIndex === rowIndex"
                :disabled="disabled"
                @click="openFileSelector(rowIndex)"
            >
              {{ record.value ? '重新选择' : '选择文件' }}
            </a-button>
            <span v-if="record.value" class="file-name" :title="getFileName(record.value)">
              {{ getFileName(record.value) }}
            </span>
          </div>
          <div v-else-if="isBooleanType(record)" class="complex-cell">
            <a-switch
                :model-value="record.value === 'true'"
                :disabled="disabled"
                size="small"
                @change="(val: string | number | boolean, _ev: Event) => onBooleanChange(record, val)"
            />
            <ParameterMockActions
                :record="record"
                :visible="mockPopoverVisibleMap[rowIndex]"
                :disabled="disabled"
                @update:visible="(val: boolean) => mockPopoverVisibleMap[rowIndex] = val"
                @select="(config: MockConfig) => onMockDataSelect(record, config, rowIndex)"
                @cancel="closeMockPopover(rowIndex)"
            />
          </div>
          <div v-else-if="isIntegerType(record)" class="complex-cell">
            <a-input-number
                :model-value="record.value ? Number(record.value) : undefined"
                :disabled="disabled"
                :step="1"
                placeholder="整数"
                hide-button
                @change="(val: number | string | undefined) => onIntegerChange(record, val)"
            >
              <template #suffix>
                <ParameterMockActions
                    :record="record"
                    :visible="mockPopoverVisibleMap[rowIndex]"
                    :disabled="disabled"
                    @update:visible="(val: boolean) => mockPopoverVisibleMap[rowIndex] = val"
                    @select="(config: MockConfig) => onMockDataSelect(record, config, rowIndex)"
                    @cancel="closeMockPopover(rowIndex)"
                />
              </template>
            </a-input-number>
          </div>
          <div v-else-if="isNumberType(record)" class="complex-cell">
            <a-input-number
                :model-value="record.value ? Number(record.value) : undefined"
                :disabled="disabled"
                :step="0.01"
                placeholder="数值"
                hide-button
                @change="(val: number | string | undefined) => onNumberChange(record, val)"
            >
              <template #suffix>
                <ParameterMockActions
                    :record="record"
                    :visible="mockPopoverVisibleMap[rowIndex]"
                    :disabled="disabled"
                    @update:visible="(val: boolean) => mockPopoverVisibleMap[rowIndex] = val"
                    @select="(config: MockConfig) => onMockDataSelect(record, config, rowIndex)"
                    @cancel="closeMockPopover(rowIndex)"
                />
              </template>
            </a-input-number>
          </div>
          <a-textarea
              v-else-if="isJsonType(record)"
              v-model="record.value"
              :disabled="disabled"
              placeholder='JSON，如 {&quot;key&quot;: &quot;value&quot;}'
              :auto-size="{ minRows: 1, maxRows: 4 }"
              @input="onDataChange"
          />
          <div v-else-if="isArrayType(record)" class="array-cell">
            <div
                v-for="(item, index) in getArrayDisplayValue(record)"
                :key="index"
                class="array-item"
            >
              <a-input
                  :model-value="item"
                  placeholder="元素值"
                  :disabled="disabled"
                  size="mini"
                  @input="(val: string) => onArrayItemChange(record, index, val)"
              >
                <template #suffix>
                  <ParameterMockActions
                      :record="record"
                      :visible="arrayItemMockVisibleMap[getArrayItemMockKey(rowIndex, index)]"
                      :disabled="disabled"
                      @update:visible="(val: boolean) => arrayItemMockVisibleMap[getArrayItemMockKey(rowIndex, index)] = val"
                      @select="(config: MockConfig) => onArrayItemMockSelect(record, config, index, rowIndex)"
                      @cancel="closeArrayItemMockPopover(rowIndex, index)"
                  />
                </template>
              </a-input>
              <a-button
                  type="text"
                  size="mini"
                  :disabled="disabled"
                  @click="addArrayItem(record, index)"
              >
                <template #icon>
                  <icon-plus />
                </template>
              </a-button>
              <a-button
                  v-if="getArrayDisplayValue(record).length > 1"
                  type="text"
                  size="mini"
                  status="danger"
                  :disabled="disabled"
                  @click="removeArrayItem(record, index)"
              >
                <template #icon>
                  <icon-close />
                </template>
              </a-button>
            </div>
          </div>
          <ExprSuggestInput
              v-else
              :model-value="record.value"
              placeholder="参数值（输入 @ 可插入函数）"
              :disabled="disabled"
              @update:model-value="(val: string) => { record.value = val; onDataChange(); }"
          >
            <template #suffix>
              <ParameterMockActions
                  :record="record"
                  :visible="mockPopoverVisibleMap[rowIndex]"
                  :disabled="disabled"
                  @update:visible="(val: boolean) => mockPopoverVisibleMap[rowIndex] = val"
                  @select="(config: MockConfig) => onMockDataSelect(record, config, rowIndex)"
                  @cancel="closeMockPopover(rowIndex)"
                />
            </template>
          </ExprSuggestInput>
        </template>
        <template #description="{ record }">
          <a-input
              v-model="record.description"
              placeholder="说明"
              :disabled="disabled"
              @input="onDataChange"
          />
        </template>
        <template #operation="{ rowIndex }">
          <a-button type="text" status="danger" :disabled="disabled" @click="deleteRow(rowIndex)">删除</a-button>
        </template>
      </a-table>
    </div>
    <a-button type="dashed" long :disabled="disabled" @click="addRow" class="add-btn">
      + 添加参数
    </a-button>
    <input
        ref="fileInputRef"
        type="file"
        style="display: none"
        @change="handleFileChange"
    />
  </div>
</template>

<script setup lang="ts">
import {ref, computed, watch} from 'vue'
import {RequestParameter} from '@/types/domain/api/requestModel/RequestParameter'
import {MockConfig} from '@/types/domain/api/requestModel/MockConfig'
import {ParameterType} from '@/types/domain/api/apiEnum/ParameterType'
import ParameterMockActions from './ParameterMockActions.vue'
import ExprSuggestInput from './ExprSuggestInput.vue'
import {uploadFile} from '@/api/MyApi/fileUpload'
import {Message} from '@arco-design/web-vue'

const parameterTypeOptions = [
  {value: ParameterType.STRING, label: 'string'},
  {value: ParameterType.INTEGER, label: 'integer'},
  {value: ParameterType.NUMBER, label: 'number'},
  {value: ParameterType.BOOLEAN, label: 'boolean'},
  {value: ParameterType.JSON, label: 'json'},
  {value: ParameterType.ARRAY, label: 'array'},
]

const parameterTypeOptionsWithFile = [
  ...parameterTypeOptions,
  {value: ParameterType.FILE, label: 'file'},
]

/**
 * 将后端枚举字符串（如 'STRING'）映射为前端 ParameterType 数值
 * 兼容已存储的数值、undefined 等旧数据
 */
const normalizeParameterType = (type: any): ParameterType => {
  if (type === undefined || type === null) {
    return ParameterType.STRING
  }
  if (typeof type === 'number') {
    return type in ParameterType ? type : ParameterType.STRING
  }
  const upperType = String(type).toUpperCase()
  const mapped = (ParameterType as any)[upperType]
  if (typeof mapped === 'number') {
    return mapped
  }
  return ParameterType.STRING
}

const commonHeaderNames = [
  'Accept',
  'Accept-Charset',
  'Accept-Encoding',
  'Accept-Language',
  'Authorization',
  'Cache-Control',
  'Connection',
  'Content-Length',
  'Content-Type',
  'Cookie',
  'Host',
  'If-Match',
  'If-Modified-Since',
  'If-None-Match',
  'If-Unmodified-Since',
  'Origin',
  'Pragma',
  'Referer',
  'User-Agent',
  'X-Auth-Token',
  'X-Forwarded-For',
  'X-Requested-With',
]

const commonHeaderOptions = computed(() =>
    commonHeaderNames.map(name => ({label: name, value: name}))
)

const isHeaderLikeContext = computed(() =>
    props.context === 'header' || props.context === 'mockHeader'
)

const columns = computed(() => {
  if (props.context === 'header' || props.context === 'cookie' || props.context === 'mockHeader') {
    return [
      {title: '状态', slotName: 'status', width: 60},
      {title: '参数名', slotName: 'name', width: 200},
      {title: '参数值', slotName: 'value', width: 240},
      {title: '说明', slotName: 'description', width: 160},
      {title: '操作', slotName: 'operation', width: 80}
    ]
  }
  return [
    {title: '状态', slotName: 'status', width: 60},
    {title: '类型', slotName: 'type', width: 100},
    {title: '参数名', slotName: 'name', width: 160},
    {title: '参数值', slotName: 'value', width: 220},
    {title: '说明', slotName: 'description', width: 160},
    {title: '操作', slotName: 'operation', width: 80}
  ]
})

type ParameterContext = 'header' | 'cookie' | 'query' | 'formData' | 'xWwwFormUrlencoded' | 'variable' | 'mockHeader'

const props = defineProps<{
  disabled?: boolean;
  allowFileType?: boolean;
  context?: ParameterContext;
}>()

const typeOptions = computed(() => {
  let options = props.allowFileType ? parameterTypeOptionsWithFile : parameterTypeOptions
  // Query 参数只支持 STRING / INTEGER / NUMBER / BOOLEAN / ARRAY
  if (props.context === 'query') {
    options = options.filter(opt =>
        opt.value === ParameterType.STRING ||
        opt.value === ParameterType.INTEGER ||
        opt.value === ParameterType.NUMBER ||
        opt.value === ParameterType.BOOLEAN ||
        opt.value === ParameterType.ARRAY
    )
  }
  // Header / Cookie / Mock 响应头只支持 STRING，隐藏类型列
  if (props.context === 'header' || props.context === 'cookie' || props.context === 'mockHeader') {
    options = options.filter(opt => opt.value === ParameterType.STRING)
  }
  // 场景变量不支持 FILE
  if (props.context === 'variable') {
    options = options.filter(opt => opt.value !== ParameterType.FILE)
  }
  return options
})

const tableData = ref<RequestParameter[]>([])
const emit = defineEmits(['change', 'count'])

// 有效参数个数（参数名非空），用于外层 tab 徽标显示，随增删改实时上报
const meaningfulCount = computed(
    () => tableData.value.filter(r => r.name != null && String(r.name).trim() !== '').length
)
watch(meaningfulCount, (n) => emit('count', n), {immediate: true})

const selectedRowIndex = ref<number | null>(null)
const mockPopoverVisibleMap = ref<Record<number, boolean>>({})
// array 类型每个元素独立的 Mock 弹窗显隐状态（key: rowIndex-itemIndex）
const arrayItemMockVisibleMap = ref<Record<string, boolean>>({})
const fileInputRef = ref<HTMLInputElement | null>(null)
const fileUploading = ref(false)

const getArrayItemMockKey = (rowIndex: number, itemIndex: number): string => `${rowIndex}-${itemIndex}`

const onMockDataSelect = (record: RequestParameter, config: MockConfig, rowIndex: number) => {
  applyMockConfig(record, config);
  mockPopoverVisibleMap.value[rowIndex] = false;
};

const closeMockPopover = (rowIndex: number) => {
  mockPopoverVisibleMap.value[rowIndex] = false;
};

const applyMockConfig = (record: RequestParameter, config: MockConfig) => {
  record.mockConfig = JSON.parse(JSON.stringify(config));
  record.value = MOCK_PLACEHOLDER;
  console.log('[MockConfig] applyMockConfig', record.name, record.mockConfig, record.value);
  onDataChange();
};

const MOCK_PLACEHOLDER = '{{__MOCK__}}';

const isMockPlaceholder = (record: RequestParameter) => record.value === MOCK_PLACEHOLDER;

const getMockPlaceholderLabel = (record: RequestParameter) => {
  return buildMockExpression(record.mockConfig);
};

function buildMockExpression(config?: MockConfig): string {
  if (!config?.type) return MOCK_PLACEHOLDER;

  function needsQuote(value: string): boolean {
    return /[\s,():'"\\]/.test(value) || value === '';
  }

  function formatArg(value: any): string {
    if (value === undefined || value === null) return '';
    const str = String(value);
    if (needsQuote(str)) {
      return `'${str}'`;
    }
    return str;
  }

  const type = config.type;
  const args: string[] = [];

  switch (type) {
    case 'fixed':
      return `@fixed(${formatArg(config.fixedValue)})`;
    case 'template':
      return config.templateId ? `@template(${config.templateId})` : '@template()';
    case 'name':
    case 'company':
    case 'address':
      args.push(formatArg(config.locale || 'zh'));
      break;
    case 'character':
      args.push(formatArg(config.caseType || 'lower'));
      if (config.length !== undefined && config.length !== null) {
        args.push(formatArg(config.length));
      }
      break;
    case 'text':
      if (config.length !== undefined && config.length !== null) {
        args.push(formatArg(config.length));
      }
      break;
    case 'int':
    case 'long':
      args.push(formatArg(config.min ?? 0));
      args.push(formatArg(config.max ?? 100));
      break;
    case 'float':
    case 'double':
      args.push(formatArg(config.min ?? 0));
      args.push(formatArg(config.max ?? 100));
      if (config.scale !== undefined && config.scale !== null) {
        args.push(formatArg(config.scale));
      }
      break;
    case 'date':
    case 'datetime':
    case 'time':
      args.push(formatArg(config.format || 'yyyy-MM-dd HH:mm:ss'));
      break;
    case 'choice':
      args.push(formatArg(config.choices || ''));
      break;
  }

  if (args.length === 0) {
    return `@${type}()`;
  }
  return `@${type}(${args.join(', ')})`;
}

const openMockPopover = (rowIndex: number) => {
  mockPopoverVisibleMap.value[rowIndex] = true;
};

const clearMockConfig = (record: RequestParameter) => {
  record.mockConfig = undefined;
  record.value = '';
  onDataChange();
};

const insertExpression = (record: RequestParameter, expression: string) => {
  if (record.type === ParameterType.ARRAY) {
    const arr = parseJsonArray(record.value)
    arr.push(expression)
    record.value = JSON.stringify(arr)
  } else {
    const currentValue = record.value || ''
    if (!currentValue) {
      record.value = expression
    } else {
      const separator = currentValue.endsWith(' ') ? '' : ' '
      record.value = currentValue + separator + expression
    }
  }
  onDataChange()
};

const onTypeChange = (record: RequestParameter) => {
  // 类型切换后清空旧值，防止脏数据导致前端解析报错
  switch (record.type) {
    case ParameterType.BOOLEAN:
      record.value = 'true'
      break
    case ParameterType.INTEGER:
    case ParameterType.NUMBER:
      record.value = '0'
      break
    case ParameterType.JSON:
      record.value = '{}'
      break
    case ParameterType.ARRAY:
      record.value = '[""]'  // 默认一个空元素
      break
    case ParameterType.FILE:
      record.value = ''
      break
    case ParameterType.STRING:
    default:
      record.value = ''
      break
  }
  onDataChange()
}

const onBooleanChange = (record: RequestParameter, val: boolean | string | number) => {
  record.value = String(val === true || val === 'true')
  onDataChange()
}

const onIntegerChange = (record: RequestParameter, val: number | string | undefined) => {
  if (val === undefined || val === '') {
    record.value = '0'
  } else {
    const num = typeof val === 'string' ? Number(val) : val
    record.value = String(Math.floor(isNaN(num) ? 0 : num))
  }
  onDataChange()
}

const onNumberChange = (record: RequestParameter, val: number | string | undefined) => {
  if (val === undefined || val === '') {
    record.value = '0'
  } else {
    const num = typeof val === 'string' ? Number(val) : val
    record.value = String(isNaN(num) ? 0 : num)
  }
  onDataChange()
}

// ==================== ARRAY 类型处理 ====================

const parseJsonArray = (value?: string): string[] => {
  if (!value) return []
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map(String) : []
  } catch {
    return []
  }
}

const getArrayValue = (record: RequestParameter): string[] => {
  return parseJsonArray(record.value)
}

// 用于 UI 展示：至少保留一个空元素输入框，避免无输入框可操作
const getArrayDisplayValue = (record: RequestParameter): string[] => {
  const arr = getArrayValue(record)
  return arr.length > 0 ? arr : ['']
}

const onArrayItemChange = (record: RequestParameter, index: number, val: string) => {
  const arr = getArrayValue(record)
  arr[index] = val
  record.value = JSON.stringify(arr)
  onDataChange()
}

const addArrayItem = (record: RequestParameter, index: number) => {
  const arr = getArrayValue(record)
  // 如果实际数组为空（UI 展示了一个兜底空元素），先补一个空元素再往后插入
  if (arr.length === 0) {
    arr.splice(0, 0, '')
  }
  arr.splice(index + 1, 0, '')
  record.value = JSON.stringify(arr)
  onDataChange()
}

const removeArrayItem = (record: RequestParameter, index: number) => {
  const arr = getArrayValue(record)
  arr.splice(index, 1)
  record.value = JSON.stringify(arr)
  onDataChange()
}

// array 元素级 Mock：把 @expression 写入对应下标的元素
const onArrayItemMockSelect = (record: RequestParameter, config: MockConfig, itemIndex: number, rowIndex: number) => {
  const expression = buildMockExpression(config)
  const arr = getArrayValue(record)
  arr[itemIndex] = expression
  record.value = JSON.stringify(arr)
  arrayItemMockVisibleMap.value[getArrayItemMockKey(rowIndex, itemIndex)] = false
  onDataChange()
}

const closeArrayItemMockPopover = (rowIndex: number, itemIndex: number) => {
  arrayItemMockVisibleMap.value[getArrayItemMockKey(rowIndex, itemIndex)] = false
}

const isStringType = (record: RequestParameter) => record.type === undefined || record.type === ParameterType.STRING
const isIntegerType = (record: RequestParameter) => record.type === ParameterType.INTEGER
const isNumberType = (record: RequestParameter) => record.type === ParameterType.NUMBER
const isBooleanType = (record: RequestParameter) => record.type === ParameterType.BOOLEAN
const isJsonType = (record: RequestParameter) => record.type === ParameterType.JSON
const isFileType = (record: RequestParameter) => record.type === ParameterType.FILE
const isArrayType = (record: RequestParameter) => record.type === ParameterType.ARRAY

const openFileSelector = (rowIndex: number) => {
  selectedRowIndex.value = rowIndex
  fileInputRef.value?.click()
}

const handleFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || selectedRowIndex.value === null) return

  const record = tableData.value[selectedRowIndex.value]
  if (!record) return

  fileUploading.value = true
  try {
    const res: any = await uploadFile(file)
    if (res.code === 200 && res.data) {
      record.value = res.data.fileId
      onDataChange()
      Message.success('文件上传成功')
    } else {
      Message.error(res.msg || '文件上传失败')
    }
  } catch (e) {
    Message.error('文件上传异常')
  } finally {
    fileUploading.value = false
    input.value = ''
  }
}

const getFileName = (fileId?: string) => {
  if (!fileId) return ''
  const parts = fileId.split('_')
  return parts.length > 1 ? decodeURIComponent(parts[parts.length - 1]) : fileId
}

const addRow = () => {
  if (props.disabled) return;
  tableData.value.push(new RequestParameter())
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
 * 参数名输入处理：过滤非法字符，只允许中文、英文、数字、下划线
 * Header / Mock 响应头除外：允许标准 HTTP token 字符（含连字符）
 */
const onNameInput = (record: RequestParameter, value: string) => {
  if (isHeaderLikeContext.value) {
    return;
  }
  const filtered = value.replace(/[^一-龥a-zA-Z0-9_]/g, '');
  if (filtered !== value) {
    record.name = filtered;
  }
  onDataChange();
};

/**
 * 校验所有启用的参数
 * 规则：
 *   1. 参数名必填、长度1-50、只能包含中文/英文/数字/下划线
 *   2. ARRAY 类型值必须是合法 JSON 数组
 */
const validateAll = (): { valid: boolean; message: string } => {
  for (let i = 0; i < tableData.value.length; i++) {
    const record = tableData.value[i];
    if (record.disabled) continue;

    const name = record.name?.trim();
    if (!name) {
      return { valid: false, message: `第 ${i + 1} 行参数名不能为空` };
    }
    if (name.length > 50) {
      return { valid: false, message: `第 ${i + 1} 行参数名长度不能超过50个字符` };
    }
    if (isHeaderLikeContext.value) {
      // HTTP header 名按 RFC 7230 token 规则校验：字母/数字 + !#$%&'*+-.^_`|~
      const headerRegex = /^[A-Za-z0-9!#$%&'*+\-.^_`|~]+$/;
      if (!headerRegex.test(name)) {
        return { valid: false, message: `第 ${i + 1} 行参数名包含非法字符，HTTP Header 名只能包含字母、数字、连字符及标准符号` };
      }
    } else {
      const regex = /^[一-龥a-zA-Z0-9_]+$/;
      if (!regex.test(name)) {
        return { valid: false, message: `第 ${i + 1} 行参数名只能包含中文、英文、数字、下划线` };
      }
    }

    if (record.type === ParameterType.ARRAY) {
      try {
        const parsed = JSON.parse(record.value || '[]')
        if (!Array.isArray(parsed)) {
          return { valid: false, message: `第 ${i + 1} 行数组类型参数值必须是 JSON 数组` };
        }
      } catch {
        return { valid: false, message: `第 ${i + 1} 行数组类型参数值 JSON 格式非法` };
      }
    }
  }
  return { valid: true, message: '' };
};

const getData = () => tableData.value
const setData = (data: any[]) => {
  if (data && data.length) {
    tableData.value = data.map(item => {
      // 后端返回枚举名（如 'STRING'）时转为前端枚举数值
      let type = normalizeParameterType(item.type)
      let value = item.value

      // 根据上下文规范化不支持的类型
      if (props.context === 'query' && (
          type === ParameterType.JSON ||
          type === ParameterType.FILE
      )) {
        type = ParameterType.STRING
      }
      if ((props.context === 'header' || props.context === 'cookie' || props.context === 'mockHeader') &&
          type !== ParameterType.STRING) {
        type = ParameterType.STRING
      }
      if (props.context === 'variable' && type === ParameterType.FILE) {
        type = ParameterType.STRING
      }

      // ARRAY 值非法时兜底，防止前端解析报错
      if (type === ParameterType.ARRAY) {
        try {
          const parsed = JSON.parse(value || '[]')
          if (!Array.isArray(parsed)) value = '[]'
        } catch {
          value = '[]'
        }
      }

      return {
        ...item,
        type,
        value,
        disabled: item.disabled ?? false,
      }
    })
  } else {
    tableData.value = []
  }
}

defineExpose({getData, setData, addRow, validateAll})
</script>

<style scoped lang="less">
.parameter-table {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.parameter-table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.parameter-table .add-btn {
  flex-shrink: 0;
  margin-top: 10px;
}

:deep(.arco-table-cell) {
  padding: 6px 8px !important;
}

:deep(.arco-table-element) {
  table-layout: fixed;
}

:deep(.arco-table-td) {
  overflow: hidden;
}

:deep(.arco-table-cell) .arco-input,
:deep(.arco-table-cell) .arco-select,
:deep(.arco-table-cell) .arco-textarea,
:deep(.arco-table-cell) .arco-input-number {
  width: 100%;
}

.file-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.file-cell .file-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-text-2);
  font-size: 13px;
}

.complex-cell {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 4px;
  min-width: 0;
}

.complex-cell > .arco-input-number {
  flex: 1;
  min-width: 0;
}

.complex-cell > .arco-switch {
  flex-shrink: 0;
}

.complex-cell .cell-actions {
  flex-shrink: 0;
}

.array-cell {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.array-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.array-item .arco-input {
  flex: 1;
  min-width: 0;
}

.array-item .arco-btn {
  flex-shrink: 0;
  padding: 0 4px;
}

.mock-placeholder-cell {
  display: flex;
  align-items: center;
  min-height: 32px;
}

.mock-placeholder-tag {
  cursor: pointer;
  user-select: none;
}

.mock-placeholder-tag :deep(.arco-tag-close-btn) {
  margin-left: 4px;
}
</style>
