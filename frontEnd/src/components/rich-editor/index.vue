<template>
  <div
    class="rich-editor"
    :class="{ 'auto-height': props.autoHeight || props.readonly, 'is-readonly': props.readonly, 'mention-open': mentionVisible }"
    :style="{
      width: '100%',
      height: props.autoHeight || props.readonly ? 'auto' : (props.height || '300px'),
      border: props.readonly ? 'none' : '1px solid var(--color-border-3)',
      borderRadius: '4px',
      zIndex: 100,
    }"
  >
    <Toolbar
      v-if="!props.readonly"
      style="border-bottom: 1px solid var(--color-border-2); flex-shrink: 0;"
      :editor="editorRef"
      :defaultConfig="toolbarConfig"
      mode="default"
    />
    <div class="editor-container">
      <Editor
        :style="{ width: '100%', height: props.autoHeight || props.readonly ? 'auto' : '100%' }"
        :defaultConfig="editorConfig"
        mode="default"
        @onCreated="handleCreated"
        @onChange="handleChange"
      />
    </div>
    <!-- @提及下拉浮层：mousedown.prevent 阻止点击时编辑器失焦 -->
    <div
      v-if="mentionVisible && props.mentionList?.length"
      class="mention-dropdown"
      :style="{ top: mentionPos.top, left: mentionPos.left }"
    >
      <div
        v-for="item in props.mentionList"
        :key="item.value"
        class="mention-item"
        @mousedown.prevent="insertMention(item)"
      >
        {{ item.label }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {shallowRef, ref, watch, onBeforeUnmount, nextTick, computed} from 'vue';
import '@wangeditor/editor/dist/css/style.css';
import {Editor, Toolbar} from '@wangeditor/editor-for-vue';
import {Boot} from '@wangeditor/editor';
import mentionModule from '@wangeditor/plugin-mention';
import {getToken} from '@/utils/auth';

// 注册 mention 插件（Boot 为全局单例，registerModule 全局只需一次）
// 当前编辑器版本未提供 Boot.isModuleRegistered，用挂在 Boot 上的 flag 防止重复注册
const BootAny = Boot as any;
if (!BootAny.__mentionModuleRegistered) {
  try {
    Boot.registerModule(mentionModule);
  } catch (e) {
    // 重复注册等异常忽略，避免阻断编辑器渲染
  }
  BootAny.__mentionModuleRegistered = true;
}

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
  /** 高度随内容自动撑开（内部不出现滚动条），适合抽屉/表单内嵌场景 */
  autoHeight?: boolean;
  /** 只读预览态：隐藏工具栏、内容不可编辑、随内容撑开，用于详情展示 */
  readonly?: boolean;
  /** 启用 @提及功能 */
  mention?: boolean;
  /** @提及下拉数据 */
  mentionList?: { label: string; value: string }[];
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  (e: 'uploaded', fileId: string): void;
  (e: 'mention', value: string): void;
  (e: 'mention-trigger'): void;
}>();

const editorRef = shallowRef<any>(null);
const valueHtml = ref(props.modelValue || '');
const isEditorReady = ref(false);

// @提及下拉状态
const mentionVisible = ref(false);
const mentionEditor = shallowRef<any>(null);
const mentionPos = ref({ top: '0px', left: '0px' });

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
  // 用官方 toolbarKeys 精编为单行常用项（原生方案，替代 excludeKeys 全量展示导致的工具栏换行）
  return {
    toolbarKeys: [
      'headerSelect',
      'bold',
      'italic',
      'underline',
      'through',
      '|',
      'color',
      'bgColor',
      '|',
      'bulletedList',
      'numberedList',
      'todo',
      '|',
      'blockquote',
      'codeBlock',
      'insertLink',
      'insertImage',
      'insertTable',
      '|',
      'undo',
      'redo',
      'clearStyle',
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

/**
 * 插入 @提及节点
 * 下拉项使用 @mousedown.prevent 触发（点击时编辑器会失焦，prevent 阻止失焦，
 * 保持光标在 '@' 之后）；插入前删除触发用的 '@' 字符，避免出现 '@' + mention 重复
 */
const insertMention = (item: { label: string; value: string }) => {
  const editor = mentionEditor.value;
  if (!editor) return;
  // 还原编辑器选区（保险调用，mousedown.prevent 已尽量保持焦点）
  if (typeof editor.restoreSelection === 'function') editor.restoreSelection();
  // 删除触发用的 '@' 字符
  editor.deleteBackward('character');
  // 插入 mention 节点
  editor.insertNode({
    type: 'mention',
    value: item.value,
    info: {},
    children: [{ text: '' }],
  });
  // 光标后移一位，移出 void 节点，便于继续输入
  editor.move(1);
  mentionVisible.value = false;
  emit('mention', item.value);
};

const editorConfig = computed(() => ({
  placeholder: props.placeholder || '请输入内容...',
  // autoHeight/readonly 模式下关闭编辑器内部滚动，高度随内容自动撑开
  scroll: !(props.autoHeight || props.readonly),
  readOnly: !!props.readonly,
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
  },
  // @提及配置：mention 插件在每次 insertText 时都会链式访问
  // editor.getConfig().EXTEND_CONF.mentionConfig，若 EXTEND_CONF 为 undefined 会抛错
  // 导致无法输入。因此即便未启用 mention 也提供空对象（showModal/hideModal 为
  // undefined 时插件内部用 && 短路跳过，'@' 退化为普通字符），确保非 mention 模式
  // 不影响原有功能。
  EXTEND_CONF: {
    mentionConfig: props.mention ? {
      showModal: (editor: any) => {
        mentionEditor.value = editor;
        // 获取光标位置定位下拉（官方推荐：document.getSelection）
        try {
          const domSelection = document.getSelection();
          const domRange = domSelection?.getRangeAt(0);
          if (domRange) {
            const rect = domRange.getBoundingClientRect();
            mentionPos.value = {
              top: `${rect.top + 20}px`,
              left: `${rect.left + 5}px`,
            };
          }
        } catch (e) { /* fallback */ }
        mentionVisible.value = true;
        emit('mention-trigger');
      },
      hideModal: () => {
        mentionVisible.value = false;
      },
    } : {},
  },
}));

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
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  // 下拉浮层显示时放开外层裁剪，避免被 overflow:hidden 裁掉（仅在 mentionVisible 期间）
  &.mention-open {
    overflow: visible;
  }

  .editor-container {
    flex: 1 1 0;
    min-height: 0;
    overflow: hidden;
  }

  // @提及下拉浮层
  .mention-dropdown {
    position: fixed;
    max-height: 200px;
    overflow-y: auto;
    background: var(--color-bg-popup);
    border: 1px solid var(--color-border-2);
    border-radius: 4px;
    box-shadow: 0 4px 10px rgba(0, 0, 0, .1);
    z-index: 9999;
    min-width: 160px;

    .mention-item {
      padding: 6px 12px;
      font-size: 13px;
      cursor: pointer;

      &:hover {
        background: var(--color-fill-2);
      }
    }
  }

  :deep(.w-e-text-container) {
    width: 100%;
    background-color: var(--color-bg-2);
    text-align: left;
  }

  // wangEditor 默认不给滚动容器加 overflow，内容变长会撑破容器
  :deep(.w-e-scroll) {
    overflow-y: auto;
  }

  :deep(.w-e-bar) {
    background-color: var(--color-bg-2);
  }

  // autoHeight：编辑区不滚动、随内容撑开，给个最小高度避免空内容时只剩一行
  &.auto-height {
    overflow: visible;

    .editor-container {
      flex: none;
      overflow: visible;
    }

    :deep(.w-e-text-container) {
      min-height: 160px;
    }
  }

  // 只读预览态：不保留编辑用最小高度，去掉 wangEditor 只读时的灰底
  &.is-readonly {
    :deep(.w-e-text-container) {
      min-height: 0;
    }
  }
}
</style>
