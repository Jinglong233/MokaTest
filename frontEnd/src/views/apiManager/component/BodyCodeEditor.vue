<template>
  <div class="body-code-editor">
    <div class="editor-toolbar">
      <a-space v-if="!isScriptMode && showMockActions">
        <a-button type="text" size="mini" @click="openMockSelector">
          <template #icon>
            <icon-plus />
          </template>
          插入 Mock 数据
        </a-button>
        <a-button type="text" size="mini" @click="openTemplateSelector">
          <template #icon>
            <icon-plus />
          </template>
          插入模板数据
        </a-button>
      </a-space>
      <a-space v-if="canFormat">
        <a-button type="text" size="mini" @click="beautifyCode">
          <template #icon>
            <icon-brush />
          </template>
          美化
        </a-button>
        <a-button v-if="props.lang === 'json'" type="text" size="mini" @click="compactJson">
          <template #icon>
            <icon-shrink />
          </template>
          简化
        </a-button>
      </a-space>
    </div>
    <div ref="editorContainerRef" class="editor-container">
      <div v-if="loading" class="editor-loading">
        <a-spin />
        <span class="editor-loading-text">编辑器加载中…</span>
      </div>
    </div>
    <MockDataSelector v-model:visible="mockSelectorVisible" @select="onMockDataSelect" />
    <TemplateDataSelector v-model:visible="templateSelectorVisible" @select="onTemplateDataSelect" />
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, onBeforeUnmount, watch, computed} from 'vue'
import {IconPlus, IconBrush, IconShrink} from '@arco-design/web-vue/es/icon'
import {Message} from '@arco-design/web-vue'
import beautify from 'js-beautify'
import MockDataSelector from './MockDataSelector.vue'
import TemplateDataSelector from './TemplateDataSelector.vue'
import {loadMonaco} from '@/utils/monacoLoader'
import {jsApiCompletions, JsCompletionItem} from './jsApiCompletions'

interface Props {
  modelValue?: string
  lang?: 'json' | 'xml' | 'javascript'
  disabled?: boolean
  showMockActions?: boolean
}

const props = defineProps<Props>()
const showMockActions = computed(() => props.showMockActions !== false)
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'change'): void
}>()

// Monaco 运行时与编辑器实例
let monaco: any = null
let editor: any = null
// 程序化设置内容时抑制 change 监听，避免回环
let suppressChange = false

const editorContainerRef = ref<HTMLDivElement>()
const loading = ref(true)
const mockSelectorVisible = ref(false)
const templateSelectorVisible = ref(false)

const langToMonaco = (lang?: string): string => {
  if (lang === 'xml') return 'xml'
  if (lang === 'javascript') return 'javascript'
  return 'json'
}

// === 全局只注册一次 JS 自定义补全 provider ===
let jsCompletionRegistered = false

const mapCompletionKind = (m: any, type: JsCompletionItem['type']): number => {
  const K = m.languages.CompletionItemKind
  switch (type) {
    case 'method': return K.Method
    case 'function': return K.Function
    case 'property': return K.Property
    case 'class': return K.Class
    case 'keyword': return K.Keyword
    case 'constant': return K.Constant
    default: return K.Text
  }
}

const registerJsCompletions = (m: any) => {
  if (jsCompletionRegistered) return
  jsCompletionRegistered = true
  m.languages.registerCompletionItemProvider('javascript', {
    triggerCharacters: ['.'],
    provideCompletionItems(model: any, position: any) {
      // 取光标前包含 . 的连续标识符片段（mimic 原 CodeMirror /[\w.]+/ 行为）
      const lineContent = model.getLineContent(position.lineNumber)
      const textUntil = lineContent.substring(0, position.column - 1)
      const match = textUntil.match(/[\w.]+$/)
      const prefix = match ? match[0] : ''

      // 拆成「对象路径前缀」+「最后一段」：替换范围只覆盖最后一段，
      // 否则变量成员访问（如 text.to）会整段被替换掉对象名
      const lastDot = prefix.lastIndexOf('.')
      const base = lastDot >= 0 ? prefix.substring(0, lastDot + 1) : ''
      const lastSeg = lastDot >= 0 ? prefix.substring(lastDot + 1) : prefix
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: position.column - lastSeg.length,
        endColumn: position.column,
      }

      const lower = prefix.toLowerCase()
      const lastLower = lastSeg.toLowerCase()
      const suggestions = jsApiCompletions
        .filter(item => {
          const label = item.label.toLowerCase()
          // 完整点路径前缀匹配：context.u → context.utils / context.utils.md5
          if (lower && label.startsWith(lower)) return true
          // 成员匹配：任意对象的成员访问 text.to → toLowerCase/toUpperCase...
          const itemLastSeg = label.split('.').pop() || label
          return !!lastLower && itemLastSeg.startsWith(lastLower)
        })
        .map(item => {
          const label = item.label.toLowerCase()
          const itemLastSeg = item.label.split('.').pop() || item.label
          // 与已输入的对象路径同源（context. → context.utils.md5）时只补剩余部分；
          // 否则按成员方法只插入方法名（text. + toLowerCase → text.toLowerCase）
          const insertText = base && label.startsWith(base.toLowerCase())
            ? item.label.substring(base.length)
            : (base ? itemLastSeg : item.label)
          return {
            label: item.label,
            kind: mapCompletionKind(m, item.type),
            detail: item.detail,
            documentation: item.info,
            insertText,
            range,
          }
        })
      return {suggestions}
    },
  })
}

// 暴露方法供父组件调用：在光标处插入文本
const insertText = (text: string) => {
  if (!editor || !monaco) return
  const selection = editor.getSelection()
  const range = selection || editor.getModel()?.getFullModelRange()
  editor.executeEdits('insert-snippet', [
    {range, text, forceMoveMarkers: true},
  ])
  editor.focus()
}

defineExpose({ insertText })

const openMockSelector = () => {
  mockSelectorVisible.value = true
}

const openTemplateSelector = () => {
  templateSelectorVisible.value = true
}

const onMockDataSelect = (expression: string) => {
  insertText(expression)
  mockSelectorVisible.value = false
}

const onTemplateDataSelect = (expression: string) => {
  insertText(expression)
  templateSelectorVisible.value = false
}

const getEditorValue = (): string => editor?.getValue() || ''

/**
 * 程序化设置内容（美化/简化复用），由 change 监听负责对外 emit
 */
const setEditorValue = (value: string) => {
  if (!editor) return
  editor.setValue(value)
}

/**
 * 是否支持「美化」（JSON / XML / JavaScript）
 */
const canFormat = computed(() => ['json', 'xml', 'javascript'].includes(props.lang || 'json'))

/**
 * 是否脚本编辑模式（JavaScript）
 */
const isScriptMode = computed(() => props.lang === 'javascript')

/**
 * 一键美化：按当前语言选择对应格式化器
 * - JSON → JSON.stringify 缩进
 * - XML  → js-beautify html
 * - JavaScript → js-beautify js
 */
const beautifyCode = () => {
  const raw = getEditorValue().trim()
  if (!raw) return
  const lang = props.lang || 'json'
  try {
    let formatted = raw
    if (lang === 'json') {
      formatted = JSON.stringify(JSON.parse(raw), null, 2)
    } else if (lang === 'xml') {
      formatted = beautify.html(raw, {
        indent_size: 2,
        indent_char: ' ',
        max_preserve_newlines: 2,
        preserve_newlines: true,
        wrap_line_length: 0,
        extra_liners: [],
      })
    } else if (lang === 'javascript') {
      formatted = beautify.js(raw, {
        indent_size: 2,
        indent_char: ' ',
        max_preserve_newlines: 2,
        preserve_newlines: true,
      })
    }
    setEditorValue(formatted)
    Message.success({ content: '美化成功', duration: 1500 })
  } catch (e) {
    Message.error({ content: '格式错误，无法美化', duration: 2000 })
  }
}

/**
 * 压缩 JSON（简化）
 */
const compactJson = () => {
  const raw = getEditorValue().trim()
  if (!raw) return
  try {
    const parsed = JSON.parse(raw)
    setEditorValue(JSON.stringify(parsed))
    Message.success({ content: 'JSON 简化成功', duration: 1500 })
  } catch (e) {
    Message.error({ content: 'JSON 格式错误，无法简化', duration: 2000 })
  }
}

const initEditor = async (): Promise<void> => {
  try {
    monaco = await loadMonaco()
  } catch (e: any) {
    loading.value = false
    Message.error({ content: e?.message || '编辑器加载失败', duration: 3000 })
    return
  }
  // 组件可能在异步加载期间被卸载
  if (!editorContainerRef.value) return

  const lang = langToMonaco(props.lang)
  registerJsCompletions(monaco)

  editor = monaco.editor.create(editorContainerRef.value, {
    value: props.modelValue || '',
    language: lang,
    theme: 'vs', // VS Code 浅色主题
    readOnly: !!props.disabled,
    automaticLayout: true, // 自适应容器尺寸变化
    fontFamily: "'SF Mono', 'Monaco', 'Cascadia Code', 'Fira Code', monospace",
    fontSize: 13,
    lineHeight: 20,
    tabSize: 2,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    fixedOverflowWidgets: true,
    wordWrap: 'off',
    renderLineHighlight: 'line',
    smoothScrolling: true,
    suggestOnTriggerCharacters: true,
    quickSuggestions: {other: true, comments: false, strings: true},
    scrollbar: {
      verticalScrollbarSize: 10,
      horizontalScrollbarSize: 10,
    },
  })

  loading.value = false

  // 内容变化 → 对外同步
  editor.onDidChangeModelContent(() => {
    if (suppressChange) return
    const newValue = editor.getValue()
    emit('update:modelValue', newValue)
    emit('change')
  })
}

// 外部更新内容时同步到编辑器（避免回环）
watch(() => props.modelValue, (newVal) => {
  if (!editor) return
  const current = editor.getValue()
  if ((newVal || '') === current) return
  suppressChange = true
  // 保留撤销栈、光标位置的轻量更新
  editor.setValue(newVal || '')
  suppressChange = false
})

// 语言变化时切换 model language（无需重建编辑器）
watch(() => props.lang, (newLang) => {
  if (!editor || !monaco) return
  const model = editor.getModel()
  if (model) monaco.editor.setModelLanguage(model, langToMonaco(newLang))
})

// 只读状态变化时同步
watch(() => props.disabled, (val) => {
  if (editor) editor.updateOptions({ readOnly: !!val })
})

onMounted(() => {
  initEditor()
})

onBeforeUnmount(() => {
  if (editor) {
    const model = editor.getModel()
    editor.dispose()
    if (model) model.dispose()
    editor = null
  }
})
</script>

<style scoped>
.body-code-editor {
  height: 100%;
  border: 1px solid #e5e5e5;
  border-radius: 4px;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.editor-toolbar {
  padding: 4px 8px;
  border-bottom: 1px solid #e5e5e5;
  background: #fafafa;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.editor-container {
  flex: 1;
  min-height: 0;
  position: relative;
}

.editor-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #ffffff;
  color: #888;
  font-size: 13px;
  z-index: 1;
}
</style>
