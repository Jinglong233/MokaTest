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
      <div class="panel-title">{{ $t('api.scriptFunc.builtIn') }}</div>
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
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

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

const preScriptFunctions = (): FunctionGroup[] => [
  {
    name: t('api.scriptFunc.groupVar'),
    functions: [
      { label: t('api.scriptFunc.getVariable'), detail: '', template: `context.getVariable("\${1:${t('api.scriptFunc.phVarName')}}")`, desc: t('api.scriptFunc.getVariableDesc') },
      { label: t('api.scriptFunc.setVariable'), detail: '', template: `context.setVariable("\${1:${t('api.scriptFunc.phVarName')}}", "\${2:${t('api.scriptFunc.phValue')}}")`, desc: t('api.scriptFunc.setVariableDesc') },
    ]
  },
  {
    name: t('api.scriptFunc.groupRequest'),
    functions: [
      { label: t('api.scriptFunc.getUrl'), detail: '()', template: 'context.getUrl()', desc: t('api.scriptFunc.getUrlDesc') },
      { label: t('api.scriptFunc.getMethod'), detail: '()', template: 'context.getMethod()', desc: t('api.scriptFunc.getMethodDesc') },
      { label: t('api.scriptFunc.getBody'), detail: '()', template: 'context.getBody()', desc: t('api.scriptFunc.getBodyDesc') },
      { label: t('api.scriptFunc.getHeaders'), detail: '()', template: 'context.getHeaders()', desc: t('api.scriptFunc.getHeadersDesc') },
      { label: t('api.scriptFunc.getHeader'), detail: '(name)', template: 'context.getHeader("${1:headerName}")', desc: t('api.scriptFunc.getHeaderDesc') },
      { label: t('api.scriptFunc.getQueries'), detail: '()', template: 'context.getQueries()', desc: t('api.scriptFunc.getQueriesDesc') },
      { label: t('api.scriptFunc.getQuery'), detail: '(name)', template: 'context.getQuery("${1:paramName}")', desc: t('api.scriptFunc.getQueryDesc') },
      { label: t('api.scriptFunc.getCookies'), detail: '()', template: 'context.getCookies()', desc: t('api.scriptFunc.getCookiesDesc') },
      { label: t('api.scriptFunc.getCookie'), detail: '(name)', template: 'context.getCookie("${1:cookieName}")', desc: t('api.scriptFunc.getCookieDesc') },
    ]
  },
  {
    name: t('api.scriptFunc.groupModify'),
    functions: [
      { label: t('api.scriptFunc.setHeader'), detail: '(name, value)', template: 'context.setHeader("${1:name}", "${2:value}")', desc: t('api.scriptFunc.setHeaderDesc') },
      { label: t('api.scriptFunc.setQuery'), detail: '(name, value)', template: 'context.setQuery("${1:name}", "${2:value}")', desc: t('api.scriptFunc.setQueryDesc') },
      { label: t('api.scriptFunc.setCookie'), detail: '(name, value)', template: 'context.setCookie("${1:name}", "${2:value}")', desc: t('api.scriptFunc.setCookieDesc') },
      { label: t('api.scriptFunc.setBody'), detail: '(body)', template: 'context.setBody(${1:body})', desc: t('api.scriptFunc.setBodyDesc') },
      { label: t('api.scriptFunc.setUrl'), detail: '(url)', template: 'context.setUrl("${1:url}")', desc: t('api.scriptFunc.setUrlDesc') },
    ]
  },
  {
    name: t('api.scriptFunc.groupLog'),
    functions: [
      { label: t('api.scriptFunc.log'), detail: '(msg)', template: `context.log("\${1:${t('api.scriptFunc.phLogContent')}}")`, desc: t('api.scriptFunc.logDesc') },
      { label: t('api.scriptFunc.logError'), detail: '(msg)', template: `context.error("\${1:${t('api.scriptFunc.phErrorContent')}}")`, desc: t('api.scriptFunc.logErrorDesc') },
      { label: t('api.scriptFunc.assert'), detail: '(condition, msg)', template: `context.assertCondition(\${1:${t('api.scriptFunc.phCondition')}}, "\${2:${t('api.scriptFunc.phFailMsg')}}")`, desc: t('api.scriptFunc.assertDesc') },
    ]
  },
  {
    name: t('api.scriptFunc.groupUtil'),
    functions: [
      { label: 'MD5', detail: '(str)', template: `context.utils.md5("\${1:${t('api.scriptFunc.phStr')}}")`, desc: t('api.scriptFunc.md5Desc') },
      { label: 'SHA256', detail: '(str)', template: `context.utils.sha256("\${1:${t('api.scriptFunc.phStr')}}")`, desc: t('api.scriptFunc.sha256Desc') },
      { label: t('api.scriptFunc.base64Label'), detail: '(str)', template: `context.utils.base64Encode("\${1:${t('api.scriptFunc.phStr')}}")`, desc: t('api.scriptFunc.base64Desc') },
      { label: t('api.scriptFunc.urlEncodeLabel'), detail: '(str)', template: `context.utils.urlEncode("\${1:${t('api.scriptFunc.phStr')}}")`, desc: t('api.scriptFunc.urlEncodeDesc') },
      { label: 'UUID', detail: '()', template: 'context.utils.uuid()', desc: t('api.scriptFunc.uuidDesc') },
      { label: t('api.scriptFunc.timestampLabel'), detail: '()', template: 'context.utils.timestamp()', desc: t('api.scriptFunc.timestampDesc') },
      { label: t('api.scriptFunc.nowLabel'), detail: '(pattern)', template: 'context.utils.now("${1:yyyy-MM-dd HH:mm:ss}")', desc: t('api.scriptFunc.nowDesc') },
      { label: t('api.scriptFunc.randomStringLabel'), detail: '(length)', template: 'context.utils.randomString(${1:8})', desc: t('api.scriptFunc.randomStringDesc') },
    ]
  },
]

const postScriptFunctions = (): FunctionGroup[] => [
  {
    name: t('api.scriptFunc.groupVar'),
    functions: [
      { label: t('api.scriptFunc.getVariable'), detail: '', template: `context.getVariable("\${1:${t('api.scriptFunc.phVarName')}}")`, desc: t('api.scriptFunc.getVariableDesc') },
      { label: t('api.scriptFunc.setVariable'), detail: '', template: `context.setVariable("\${1:${t('api.scriptFunc.phVarName')}}", "\${2:${t('api.scriptFunc.phValue')}}")`, desc: t('api.scriptFunc.setVariableDescPost') },
    ]
  },
  {
    name: t('api.scriptFunc.groupResponse'),
    functions: [
      { label: t('api.scriptFunc.getResponseBody'), detail: '()', template: 'context.getResponseBody()', desc: t('api.scriptFunc.getResponseBodyDesc') },
      { label: t('api.scriptFunc.getResponseStatus'), detail: '()', template: 'context.getResponseStatus()', desc: t('api.scriptFunc.getResponseStatusDesc') },
      { label: t('api.scriptFunc.getResponseStatusMessage'), detail: '()', template: 'context.getResponseStatusMessage()', desc: t('api.scriptFunc.getResponseStatusMessageDesc') },
      { label: t('api.scriptFunc.getResponseHeader'), detail: '(name)', template: 'context.getResponseHeader("${1:headerName}")', desc: t('api.scriptFunc.getResponseHeaderDesc') },
      { label: t('api.scriptFunc.getResponseHeaders'), detail: '()', template: 'context.getResponseHeaders()', desc: t('api.scriptFunc.getResponseHeadersDesc') },
      { label: t('api.scriptFunc.getResponseTime'), detail: '()', template: 'context.getResponseTime()', desc: t('api.scriptFunc.getResponseTimeDesc') },
    ]
  },
  {
    name: t('api.scriptFunc.groupLog'),
    functions: [
      { label: t('api.scriptFunc.log'), detail: '(msg)', template: `context.log("\${1:${t('api.scriptFunc.phLogContent')}}")`, desc: t('api.scriptFunc.logDesc') },
      { label: t('api.scriptFunc.logError'), detail: '(msg)', template: `context.error("\${1:${t('api.scriptFunc.phErrorContent')}}")`, desc: t('api.scriptFunc.logErrorDesc') },
      { label: t('api.scriptFunc.assert'), detail: '(condition, msg)', template: `context.assertCondition(\${1:${t('api.scriptFunc.phCondition')}}, "\${2:${t('api.scriptFunc.phFailMsg')}}")`, desc: t('api.scriptFunc.assertDesc') },
    ]
  },
  {
    name: t('api.scriptFunc.groupUtil'),
    functions: [
      { label: 'MD5', detail: '(str)', template: `context.utils.md5("\${1:${t('api.scriptFunc.phStr')}}")`, desc: t('api.scriptFunc.md5Desc') },
      { label: 'SHA256', detail: '(str)', template: `context.utils.sha256("\${1:${t('api.scriptFunc.phStr')}}")`, desc: t('api.scriptFunc.sha256Desc') },
      { label: t('api.scriptFunc.base64Label'), detail: '(str)', template: `context.utils.base64Encode("\${1:${t('api.scriptFunc.phStr')}}")`, desc: t('api.scriptFunc.base64Desc') },
      { label: t('api.scriptFunc.urlEncodeLabel'), detail: '(str)', template: `context.utils.urlEncode("\${1:${t('api.scriptFunc.phStr')}}")`, desc: t('api.scriptFunc.urlEncodeDesc') },
      { label: 'UUID', detail: '()', template: 'context.utils.uuid()', desc: t('api.scriptFunc.uuidDesc') },
      { label: t('api.scriptFunc.timestampLabel'), detail: '()', template: 'context.utils.timestamp()', desc: t('api.scriptFunc.timestampDesc') },
      { label: t('api.scriptFunc.nowLabel'), detail: '(pattern)', template: 'context.utils.now("${1:yyyy-MM-dd HH:mm:ss}")', desc: t('api.scriptFunc.nowDesc') },
      { label: t('api.scriptFunc.randomStringLabel'), detail: '(length)', template: 'context.utils.randomString(${1:8})', desc: t('api.scriptFunc.randomStringDesc') },
      { label: t('api.scriptFunc.jsonParseLabel'), detail: '(str)', template: 'JSON.parse(${1:jsonString})', desc: t('api.scriptFunc.jsonParseDesc') },
      { label: t('api.scriptFunc.jsonStringifyLabel'), detail: '(obj)', template: 'JSON.stringify(${1:obj})', desc: t('api.scriptFunc.jsonStringifyDesc') },
    ]
  },
]

const functionGroups = computed(() => {
  const base = props.scriptType === 'pre' ? preScriptFunctions() : postScriptFunctions()
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
    name: t('api.scriptFunc.groupCustom'),
    functions: customFunctions.value.map(fn => ({
      label: fn.funcName || '',
      detail: fn.funcParams ? `(${fn.funcParams})` : '()',
      // fn.名称(参数占位) —— 与文本字段里的写法完全一致
      template: `fn.${fn.funcName}(${buildArgPlaceholders(fn.funcParams)})`,
      desc: fn.description || t('api.scriptFunc.customDesc'),
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
