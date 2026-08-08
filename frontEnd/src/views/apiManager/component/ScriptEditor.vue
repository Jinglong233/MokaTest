<template>
  <div class="script-editor">
    <!-- 左侧：代码编辑器 -->
    <div class="editor-wrapper">
      <BodyCodeEditor
        ref="editorRef"
        v-model="modelValue"
        lang="javascript"
        :disabled="disabled"
        @change="onChange"
      />
    </div>

    <!-- 右侧：函数列表面板 -->
    <div class="function-panel">
      <div class="panel-title">内置函数</div>
      <div class="panel-content">
        <div
          v-for="group in functionGroups"
          :key="group.name"
          class="function-group"
        >
          <div class="group-name">{{ group.name }}</div>
          <div
            v-for="fn in group.functions"
            :key="fn.label"
            class="function-item"
            :class="{ 'is-disabled': disabled }"
            :title="fn.desc"
            @click="!disabled && insertFunction(fn)"
          >
            <span class="function-label">{{ fn.label }}</span>
            <span class="function-detail">{{ fn.detail }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import BodyCodeEditor from './BodyCodeEditor.vue'
import { ref, onMounted } from 'vue'
import { CustomFunction } from '@/types/domain/api/CustomFunction'
import { getCustomFunctionList } from '@/api/MyApi/customFunction'
import useProjectStore from '@/store/modules/project'

const projectStore = useProjectStore()

interface Props {
  modelValue?: string
  scriptType: 'pre' | 'post'
  disabled?: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change'): void
}>()

const editorRef = ref<InstanceType<typeof BodyCodeEditor>>()

const modelValue = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val || '')
})

const onChange = () => {
  emit('change')
}

// 函数定义
interface ScriptFunction {
  label: string
  detail: string
  template: string
  desc: string
}

interface FunctionGroup {
  name: string
  functions: ScriptFunction[]
}

const preScriptFunctions: FunctionGroup[] = [
  {
    name: '变量操作',
    functions: [
      { label: '获取变量', detail: '', template: 'context.getVariable("${1:变量名}")', desc: '获取指定变量值' },
      { label: '设置变量', detail: '', template: 'context.setVariable("${1:变量名}", "${2:值}")', desc: '设置变量值' },
    ]
  },
  {
    name: '请求信息',
    functions: [
      { label: '获取 URL', detail: '()', template: 'context.getUrl()', desc: '获取当前请求 URL' },
      { label: '获取方法', detail: '()', template: 'context.getMethod()', desc: '获取请求方法（GET/POST 等）' },
      { label: '获取 Body', detail: '()', template: 'context.getBody()', desc: '获取请求体内容' },
      { label: '获取 Headers', detail: '()', template: 'context.getHeaders()', desc: '获取所有请求头（Map）' },
      { label: '获取 Header', detail: '(name)', template: 'context.getHeader("${1:headerName}")', desc: '获取指定请求头' },
      { label: '获取 Queries', detail: '()', template: 'context.getQueries()', desc: '获取所有 Query 参数（Map）' },
      { label: '获取 Query', detail: '(name)', template: 'context.getQuery("${1:paramName}")', desc: '获取指定 Query 参数' },
      { label: '获取 Cookies', detail: '()', template: 'context.getCookies()', desc: '获取所有 Cookie（Map）' },
      { label: '获取 Cookie', detail: '(name)', template: 'context.getCookie("${1:cookieName}")', desc: '获取指定 Cookie' },
    ]
  },
  {
    name: '修改请求',
    functions: [
      { label: '设置 Header', detail: '(name, value)', template: 'context.setHeader("${1:name}", "${2:value}")', desc: '设置/添加请求头' },
      { label: '设置 Query', detail: '(name, value)', template: 'context.setQuery("${1:name}", "${2:value}")', desc: '设置/添加 Query 参数' },
      { label: '设置 Cookie', detail: '(name, value)', template: 'context.setCookie("${1:name}", "${2:value}")', desc: '设置/添加 Cookie' },
      { label: '设置 Body', detail: '(body)', template: 'context.setBody(${1:body})', desc: '设置请求体' },
      { label: '设置 URL', detail: '(url)', template: 'context.setUrl("${1:url}")', desc: '设置请求 URL' },
    ]
  },
  {
    name: '日志断言',
    functions: [
      { label: '输出日志', detail: '(msg)', template: 'context.log("${1:日志内容}")', desc: '输出信息日志到控制台' },
      { label: '输出错误', detail: '(msg)', template: 'context.error("${1:错误内容}")', desc: '输出错误日志到控制台' },
      { label: '断言', detail: '(condition, msg)', template: 'context.assertCondition(${1:条件}, "${2:失败提示}")', desc: '自定义断言' },
    ]
  },
  {
    name: '工具函数',
    functions: [
      { label: 'MD5', detail: '(str)', template: 'context.utils.md5("${1:字符串}")', desc: 'MD5 加密' },
      { label: 'SHA256', detail: '(str)', template: 'context.utils.sha256("${1:字符串}")', desc: 'SHA256 加密' },
      { label: 'Base64 编码', detail: '(str)', template: 'context.utils.base64Encode("${1:字符串}")', desc: 'Base64 编码' },
      { label: 'URL 编码', detail: '(str)', template: 'context.utils.urlEncode("${1:字符串}")', desc: 'URL 编码' },
      { label: 'UUID', detail: '()', template: 'context.utils.uuid()', desc: '生成 UUID' },
      { label: '时间戳', detail: '()', template: 'context.utils.timestamp()', desc: '获取当前时间戳（毫秒）' },
      { label: '当前时间', detail: '(pattern)', template: 'context.utils.now("${1:yyyy-MM-dd HH:mm:ss}")', desc: '获取当前日期时间' },
      { label: '随机字符串', detail: '(length)', template: 'context.utils.randomString(${1:8})', desc: '生成随机字符串' },
    ]
  },
]

const postScriptFunctions: FunctionGroup[] = [
  {
    name: '变量操作',
    functions: [
      { label: '获取变量', detail: '', template: 'context.getVariable("${1:变量名}")', desc: '获取指定变量值' },
      { label: '设置变量', detail: '', template: 'context.setVariable("${1:变量名}", "${2:值}")', desc: '设置变量值，供后续接口使用' },
    ]
  },
  {
    name: '响应信息',
    functions: [
      { label: '获取响应体', detail: '()', template: 'context.getResponseBody()', desc: '获取响应体字符串' },
      { label: '获取状态码', detail: '()', template: 'context.getResponseStatus()', desc: '获取 HTTP 状态码' },
      { label: '获取状态消息', detail: '()', template: 'context.getResponseStatusMessage()', desc: '获取 HTTP 状态消息' },
      { label: '获取响应头', detail: '(name)', template: 'context.getResponseHeader("${1:headerName}")', desc: '获取指定响应头' },
      { label: '获取所有响应头', detail: '()', template: 'context.getResponseHeaders()', desc: '获取所有响应头（Map）' },
      { label: '获取响应时间', detail: '()', template: 'context.getResponseTime()', desc: '获取响应耗时（毫秒）' },
    ]
  },
  {
    name: '日志断言',
    functions: [
      { label: '输出日志', detail: '(msg)', template: 'context.log("${1:日志内容}")', desc: '输出信息日志到控制台' },
      { label: '输出错误', detail: '(msg)', template: 'context.error("${1:错误内容}")', desc: '输出错误日志到控制台' },
      { label: '断言', detail: '(condition, msg)', template: 'context.assertCondition(${1:条件}, "${2:失败提示}")', desc: '自定义断言' },
    ]
  },
  {
    name: '工具函数',
    functions: [
      { label: 'MD5', detail: '(str)', template: 'context.utils.md5("${1:字符串}")', desc: 'MD5 加密' },
      { label: 'SHA256', detail: '(str)', template: 'context.utils.sha256("${1:字符串}")', desc: 'SHA256 加密' },
      { label: 'Base64 编码', detail: '(str)', template: 'context.utils.base64Encode("${1:字符串}")', desc: 'Base64 编码' },
      { label: 'URL 编码', detail: '(str)', template: 'context.utils.urlEncode("${1:字符串}")', desc: 'URL 编码' },
      { label: 'UUID', detail: '()', template: 'context.utils.uuid()', desc: '生成 UUID' },
      { label: '时间戳', detail: '()', template: 'context.utils.timestamp()', desc: '获取当前时间戳（毫秒）' },
      { label: '当前时间', detail: '(pattern)', template: 'context.utils.now("${1:yyyy-MM-dd HH:mm:ss}")', desc: '获取当前日期时间' },
      { label: '随机字符串', detail: '(length)', template: 'context.utils.randomString(${1:8})', desc: '生成随机字符串' },
      { label: 'JSON 解析', detail: '(str)', template: 'JSON.parse(${1:jsonString})', desc: 'JSON 字符串转对象' },
      { label: 'JSON 序列化', detail: '(obj)', template: 'JSON.stringify(${1:obj})', desc: '对象转 JSON 字符串' },
    ]
  },
]

const functionGroups = computed(() => {
  const base = props.scriptType === 'pre' ? preScriptFunctions : postScriptFunctions
  if (customFunctionGroup.value) {
    return [...base, customFunctionGroup.value]
  }
  return base
})

// ==================== 自定义函数组（按当前项目动态加载，点击插入 fn.名称(...)） ====================

const customFunctions = ref<CustomFunction[]>([])

const customFunctionGroup = computed<FunctionGroup | null>(() => {
  if (!customFunctions.value.length) return null
  return {
    name: '自定义函数',
    functions: customFunctions.value.map(fn => ({
      label: fn.funcName || '',
      detail: fn.funcParams ? `(${fn.funcParams})` : '()',
      // fn.名称(参数占位) —— 与文本字段里的写法完全一致
      template: `fn.${fn.funcName}(${buildArgPlaceholders(fn.funcParams)})`,
      desc: fn.description || '项目自定义函数',
    })),
  }
})

const buildArgPlaceholders = (funcParams?: string): string => {
  if (!funcParams) return ''
  return funcParams.split(',')
    .map(p => p.trim())
    .filter(Boolean)
    .map((p, idx) => `\${${idx + 1}:${p}}`)
    .join(', ')
}

const loadCustomFunctions = async () => {
  const projectId = projectStore.getProjectId
  if (!projectId) {
    customFunctions.value = []
    return
  }
  try {
    const res = await getCustomFunctionList(Number(projectId))
    customFunctions.value = res.data || []
  } catch (e) {
    customFunctions.value = []
  }
}

onMounted(loadCustomFunctions)

// 插入函数模板（移除占位符，保留括号）
const insertFunction = (fn: ScriptFunction) => {
  if (props.disabled) return
  // 将 ${1:xxx} 占位符替换为实际内容或空值
  let template = fn.template
    // 移除 ${数字:内容} 格式的占位符，保留内容
    .replace(/\$\{\d+:(.*?)\}/g, '$1')
    // 如果还有剩余的数字占位符，也替换掉
    .replace(/\$\{\d+\}/g, '')
  editorRef.value?.insertText(template)
}
</script>

<style scoped lang="less">
.script-editor {
  display: flex;
  height: 300px;          /* 固定高度 */
  max-height: 300px;      /* 最大高度 */
  gap: 8px;
  overflow: hidden;       /* 组件内部滚动，不出现浏览器滚动条 */
}

.editor-wrapper {
  flex: 1;
  min-width: 0;
  height: 100%;           /* 填满父容器高度 */
}

.function-panel {
  width: 200px;
  flex-shrink: 0;
  height: 100%;           /* 与左侧等高 */
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  background: #fff;
  display: flex;
  flex-direction: column;
  overflow: hidden;       /* 整体不溢出，内容由内部滚动 */
}

.panel-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 500;
  color: #333;
  border-bottom: 1px solid #e5e5e5;
  background: #f8f9fa;
  flex-shrink: 0;         /* 标题不压缩 */
}

.panel-content {
  flex: 1;
  overflow-y: auto;       /* 内容超出时内部滚动 */
  padding: 8px 0;
}

.function-group {
  margin-bottom: 8px;

  &:last-child {
    margin-bottom: 0;
  }
}

.group-name {
  padding: 4px 12px;
  font-size: 11px;
  font-weight: 500;
  color: #666;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.function-item {
  padding: 5px 12px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  transition: background 0.15s;

  &:hover {
    background: #f0f7ff;
  }

  &.is-disabled {
    cursor: not-allowed;
    opacity: 0.5;

    &:hover {
      background: transparent;
    }
  }
}

.function-label {
  font-size: 12px;
  color: #333;
  white-space: nowrap;
}

.function-detail {
  font-size: 11px;
  color: #888;
  white-space: nowrap;
}
</style>
