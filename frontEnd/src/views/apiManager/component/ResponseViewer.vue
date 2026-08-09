<template>
  <div class="response-viewer">
    <!-- 工具栏：格式化/下载/大小统一一行（MonacoViewer 内置工具栏已关闭） -->
    <div class="response-toolbar">
      <div class="toolbar-left">
        <a-radio-group
            v-if="isHtml"
            v-model="viewMode"
            type="button"
            size="small"
        >
          <a-radio value="source">源码</a-radio>
          <a-radio value="preview">预览</a-radio>
        </a-radio-group>
        <a-button
            v-if="showFormat"
            type="text"
            size="mini"
            :disabled="!canFormat"
            @click="monacoViewerRef?.formatDocument()"
        >
          <template #icon>
            <icon-brush/>
          </template>
          格式化
        </a-button>
      </div>
      <div class="toolbar-right">
        <a-button
            v-if="props.showDownload"
            type="text"
            size="mini"
            @click="downloadResponse"
        >
          <template #icon>
            <icon-download />
          </template>
          下载
        </a-button>
        <span v-if="contentSize" class="content-size">{{ contentSize }}</span>
      </div>
    </div>

    <!-- 内容区域 -->
    <div class="response-content">
      <!-- HTML 预览模式 -->
      <template v-if="isHtml && viewMode === 'preview'">
        <iframe
            :srcdoc="sanitizedContent"
            class="html-preview"
            sandbox="allow-scripts allow-same-origin"
        />
      </template>

      <!-- 图片类型 -->
      <template v-else-if="isImage">
        <div class="image-container">
          <img
              v-if="imageUrl"
              :src="imageUrl"
              :alt="props.contentType"
              class="preview-image"
          />
          <a-empty v-else description="暂无图片数据"/>
        </div>
      </template>

      <!-- 代码类型：JSON / XML / HTML 源码 / 文本 -->
      <template v-else>
        <MonacoViewer
            ref="monacoViewerRef"
            :content="content || ''"
            :lang="codeLang"
            :show-toolbar="false"
        />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue'
import {IconDownload, IconBrush} from '@arco-design/web-vue/es/icon'
import MonacoViewer from './MonacoViewer.vue'

const props = defineProps<{
  content: string
  contentType?: string
  contentLength?: number
  rawBody?: number[] | string
  showDownload?: boolean
}>()

// Monaco 实例引用（内置工具栏已关闭，格式化由本组件工具栏触发）
const monacoViewerRef = ref()

// 视图模式：source（源码） / preview（预览），仅 HTML 有效
const viewMode = ref<'source' | 'preview'>('source')
const imageUrl = ref<string>('')

onBeforeUnmount(() => {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
  }
})

const rawBodyToBlob = (fallbackType: string): Blob | null => {
  const rawBody = props.rawBody
  if (!rawBody || (Array.isArray(rawBody) && rawBody.length === 0)) return null

  const contentType = props.contentType || fallbackType
  if (typeof rawBody === 'string') {
    const byteCharacters = atob(rawBody)
    const byteNumbers = new Array(byteCharacters.length)
    for (let i = 0; i < byteCharacters.length; i++) {
      byteNumbers[i] = byteCharacters.charCodeAt(i)
    }
    const byteArray = new Uint8Array(byteNumbers)
    return new Blob([byteArray], {type: contentType})
  }

  const uint8Array = new Uint8Array(rawBody)
  return new Blob([uint8Array], {type: contentType})
}

const createImageUrl = () => {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }
  const blob = rawBodyToBlob('image/*')
  if (!blob) return
  imageUrl.value = URL.createObjectURL(blob)
}

const downloadResponse = () => {
  let blob = rawBodyToBlob('application/octet-stream')
  if (!blob) {
    const contentType = props.contentType || 'text/plain'
    blob = new Blob([props.content || ''], {type: contentType})
  }

  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `response_${Date.now()}`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

watch(() => [props.rawBody, props.contentType], createImageUrl, {immediate: true})

/**
 * 解析 Content-Type，判断响应类型
 */
const responseType = computed((): string => {
  const ct = (props.contentType || '').toLowerCase()

  if (ct.includes('application/json')) return 'json'
  if (ct.includes('text/html')) return 'html'
  if (ct.includes('application/xml') || ct.includes('text/xml')) return 'xml'
  if (ct.includes('text/css')) return 'css'
  if (ct.includes('application/javascript') || ct.includes('text/javascript')) return 'javascript'
  if (ct.startsWith('image/')) return 'image'
  if (ct.includes('text/plain')) return 'text'
  if (ct.includes('application/pdf')) return 'pdf'

  // 根据内容自动推断
  const trimmed = (props.content || '').trim()
  if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
    try {
      JSON.parse(trimmed)
      return 'json'
    } catch (e) {
      // 不是 JSON
    }
  }
  if (trimmed.startsWith('<!DOCTYPE html') || trimmed.startsWith('<html')) {
    return 'html'
  }
  if (trimmed.startsWith('<?xml') || trimmed.startsWith('<')) {
    return 'xml'
  }

  return 'text'
})

const isHtml = computed(() => responseType.value === 'html')
const isImage = computed(() => responseType.value === 'image')
const isCode = computed(() => ['json', 'xml', 'html', 'css', 'javascript', 'text'].includes(responseType.value))

// 格式化按钮：仅代码视图（非图片、非 HTML 预览）展示；可格式化语言与 MonacoViewer 保持一致
const showFormat = computed(() => !isImage.value && !(isHtml.value && viewMode.value === 'preview'))
const canFormat = computed(() => ['json', 'html', 'xml', 'css', 'javascript'].includes(codeLang.value))

/**
 * 映射为 MonacoViewer 的 lang prop
 */
const codeLang = computed(() => {
  switch (responseType.value) {
    case 'json':
      return 'json'
    case 'xml':
      return 'xml'
    case 'html':
      return 'html'
    case 'css':
      return 'css'
    case 'javascript':
      return 'javascript'
    default:
      return 'text'
  }
})

/**
 * 内容大小显示
 */
const contentSize = computed(() => {
  if (props.contentLength && props.contentLength > 0) {
    if (props.contentLength < 1024) {
      return `${props.contentLength} B`
    } else if (props.contentLength < 1024 * 1024) {
      return `${(props.contentLength / 1024).toFixed(2)} KB`
    } else {
      return `${(props.contentLength / 1024 / 1024).toFixed(2)} MB`
    }
  }
  if (props.content) {
    const bytes = new Blob([props.content]).size
    if (bytes < 1024) {
      return `${bytes} B`
    } else if (bytes < 1024 * 1024) {
      return `${(bytes / 1024).toFixed(2)} KB`
    } else {
      return `${(bytes / 1024 / 1024).toFixed(2)} MB`
    }
  }
  return ''
})

/**
 * HTML 预览时的内容（简单处理，防止样式污染）
 */
const sanitizedContent = computed(() => {
  return props.content || ''
})
</script>

<style scoped>
.response-viewer {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.response-toolbar {
  flex-shrink: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 8px;
  background: var(--color-fill-2);
  border-bottom: 1px solid var(--color-border-2);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.content-size {
  font-size: 12px;
  color: #888;
  font-family: monospace;
}

.response-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.html-preview {
  width: 100%;
  height: 100%;
  border: none;
  background: #fff;
}

.image-container {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f5f5;
}

.preview-image {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
</style>