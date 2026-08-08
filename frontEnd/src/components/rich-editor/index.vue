<template>
  <div
    class="rich-editor"
    :style="{ width: '100%', height: props.height || '300px', border: '1px solid var(--color-border-3)', borderRadius: '4px', zIndex: 100 }"
  >
    <Toolbar
      style="border-bottom: 1px solid var(--color-border-2); flex-shrink: 0;"
      :editor="editorRef"
      :defaultConfig="toolbarConfig"
      mode="default"
    />
    <div class="editor-container">
      <Editor
        style="width: 100%; height: 100%;"
        :defaultConfig="editorConfig"
        mode="default"
        @onCreated="handleCreated"
        @onChange="handleChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import {shallowRef, ref, watch, onBeforeUnmount, nextTick, computed} from 'vue';
import '@wangeditor/editor/dist/css/style.css';
import {Editor, Toolbar} from '@wangeditor/editor-for-vue';
import {getToken} from '@/utils/auth';

// 一次性过滤 wangEditor 的「编辑区域高度 < 300px」控制台警告
// 部分场景（如测试步骤的紧凑编辑器）需要 80px 左右的高度，无法强制 300px
let warnPatched = false
const patchWangEditorHeightWarn = () => {
  if (warnPatched) return
  warnPatched = true
  const originalWarn = console.warn
  // 防御性转换：某些调用方会传对象参数，直接 join 可能抛
  // "Cannot convert object to primitive value"，把 console.warn 整个打断
  const safeText = (a: any) => {
    if (typeof a === 'string') return a
    try {
      return String(a)
    } catch {
      return '[unprintable]'
    }
  }
  console.warn = (...args: any[]) => {
    const msg = args.map(safeText).join(' ')
    if (msg.includes('编辑区域高度') || msg.includes('Textarea height')) {
      return
    }
    originalWarn.apply(console, args)
  }
}
patchWangEditorHeightWarn()

const props = defineProps<{
  modelValue?: string;
  placeholder?: string;
  height?: string;
  compact?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'uploaded', fileId: string): void;
}>();

const editorRef = shallowRef<any>(null);
const valueHtml = ref(props.modelValue || '');
const isEditorReady = ref(false);

const toolbarConfig = computed(() => {
  if (props.compact) {
    return {
      toolbarKeys: [
        'bold',
        'italic',
        'bulletedList',
        'numberedList',
        '|',
        'codeBlock',
        'insertImage'
      ]
    };
  }
  return {
    excludeKeys: [
      'group-video',      // 去掉视频
      'insertTable',      // 去掉表格
      'codeBlock',        // 去掉代码块
      'fullScreen'        // 去掉全屏
    ]
  };
});

watch(() => props.modelValue, (val) => {
  const nextVal = val || '';
  if (nextVal === valueHtml.value) return;
  valueHtml.value = nextVal;
  if (isEditorReady.value && editorRef.value) {
    safeSetHtml(nextVal);
  }
});

const handleChange = (editor: any) => {
  if (!editor) return;
  const html = editor.getHtml();
  if (html !== valueHtml.value) {
    valueHtml.value = html;
    emit('update:modelValue', html);
  }
};

const handleCreated = (editor: any) => {
  editorRef.value = editor;
  // 等待 DOM 渲染完成再设置初始内容，避免 Slate 解析异常
  nextTick(() => {
    isEditorReady.value = true;
    if (valueHtml.value) {
      safeSetHtml(valueHtml.value);
    }
  });
};

const safeSetHtml = (html: string) => {
  if (!editorRef.value || !isEditorReady.value) return;
  try {
    editorRef.value.setHtml(sanitizeHtml(html));
  } catch (e) {
    console.warn('富文本设置 HTML 失败，降级为空内容', e);
    try {
      editorRef.value.setHtml('');
    } catch (e2) {
      // 忽略二次失败
    }
  }
};

/**
 * 对 wangEditor 的 HTML 内容进行安全预处理。
 *
 * 主要解决：HTML 只有 <img> 标签没有文本节点时，Slate 解析会抛出
 * "Cannot resolve a DOM node from Slate node" 错误。
 */
const sanitizeHtml = (html: string): string => {
  if (!html || html.trim() === '') {
    return '';
  }
  const trimmed = html.trim();
  // 如果以 <img 开头，包到 <p> 里，确保有文本节点
  if (trimmed.startsWith('<img')) {
    return `<p>${trimmed}</p>`;
  }
  return trimmed;
};

const editorConfig = {
  placeholder: props.placeholder || '请输入内容...',
  MENU_CONF: {
    uploadImage: {
      server: '/api/file/uploadImage',
      fieldName: 'file',
      maxFileSize: 5 * 1024 * 1024,
      maxNumberOfFiles: 10,
      allowedFileTypes: ['image/*'],
      withCredentials: true,
      timeout: 30 * 1000,
      headers: {
        Authorization: getToken() || ''
      },
      customInsert(res: any, insertFn: any) {
        if (res.errno !== 0) {
          console.error('图片上传失败', res.message);
          return;
        }
        // 编辑器可能已被卸载，安全判断
        if (!editorRef.value || !isEditorReady.value) {
          return;
        }
        insertFn(res.data.url, res.data.alt, res.data.href);
        // 提取 fileId 通知父组件，用于编辑会话内上传但未保存的图片清理
        const url = res.data?.url || '';
        const match = /\/api\/file\/download\?fileId=([^"'\s&)]+)/.exec(url);
        if (match) {
          emit('uploaded', decodeURIComponent(match[1]));
        }
      },
    }
  }
};

/**
 * 从 HTML 中提取富文本图片 fileId
 * @param html 富文本 HTML 内容
 * @returns fileId 集合
 */
const extractFileIds = (html?: string): Set<string> => {
  if (!html) return new Set();
  const regex = /\/api\/file\/download\?fileId=([^"'\s&)]+)/g;
  const result = new Set<string>();
  let match;
  while ((match = regex.exec(html)) !== null) {
    result.add(decodeURIComponent(match[1]));
  }
  return result;
};

defineExpose({
  extractFileIds,
});

onBeforeUnmount(() => {
  isEditorReady.value = false;
  if (editorRef.value) {
    try {
      editorRef.value.destroy();
    } catch (e) {
      // 忽略销毁异常
    }
    editorRef.value = null;
  }
});
</script>

<style scoped lang="less">
.rich-editor {
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .editor-container {
    flex: 1 1 0;
    min-height: 0;
    overflow: hidden;
  }

  :deep(.w-e-text-container) {
    width: 100%;
    background-color: var(--color-bg-2);
  }

  // wangEditor 默认不给滚动容器加 overflow，内容变长会撑破容器
  :deep(.w-e-scroll) {
    overflow-y: auto;
  }

  :deep(.w-e-bar) {
    background-color: var(--color-bg-2);
  }
}
</style>
