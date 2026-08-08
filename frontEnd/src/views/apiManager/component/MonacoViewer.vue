<template>
  <div class="monaco-viewer">
    <!-- 工具栏 -->
    <div class="toolbar">
      <a-button
        size="mini"
        :disabled="!['json', 'html', 'xml', 'css', 'javascript'].includes(lang || '')"
        @click="formatDocument"
      >
        <template #icon><icon-brush /></template>
        格式化
      </a-button>
    </div>
    <!-- Monaco 容器 -->
    <div ref="editorContainerRef" class="editor-container">
      <div v-if="loading" class="editor-loading">
        <a-spin />
        <span class="editor-loading-text">编辑器加载中…</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import {ref, onMounted, onBeforeUnmount, watch, computed} from 'vue';
  import {Message} from '@arco-design/web-vue';
  import {IconBrush} from '@arco-design/web-vue/es/icon';
  import beautify from 'js-beautify';
  import {loadMonaco} from '@/utils/monacoLoader';

  interface Props {
    content: string;
    lang?: string;
  }

  const props = defineProps<Props>();

  let monaco: any = null;
  let editor: any = null;
  const editorContainerRef = ref<HTMLDivElement>();
  const loading = ref(true);

  const formatDocument = async () => {
    if (!editor) return;
    const raw = editor.getValue().trim();
    if (!raw) return;

    const lang = props.lang || 'text';
    try {
      let formatted = raw;
      if (lang === 'json') {
        formatted = JSON.stringify(JSON.parse(raw), null, 2);
      } else if (lang === 'xml' || lang === 'html') {
        formatted = beautify.html(raw, {
          indent_size: 2,
          indent_char: ' ',
          max_preserve_newlines: 2,
          preserve_newlines: true,
          wrap_line_length: 0,
          extra_liners: [],
        });
      } else if (lang === 'javascript') {
        formatted = beautify.js(raw, {
          indent_size: 2,
          indent_char: ' ',
          max_preserve_newlines: 2,
          preserve_newlines: true,
        });
      } else if (lang === 'css') {
        formatted = beautify.css(raw, {indent_size: 2});
      } else {
        // 其他语言尝试 Monaco 内置格式化
        await editor.getAction('editor.action.formatDocument')?.run();
        return;
      }
      editor.setValue(formatted);
      Message.success({content: '格式化成功', duration: 1500});
    } catch (e) {
      Message.warning({content: '当前内容无法格式化', duration: 2000});
    }
  };

  const monacoLang = computed(() => {
    switch (props.lang) {
      case 'json':
        return 'json';
      case 'html':
        return 'html';
      case 'xml':
        return 'xml';
      case 'css':
        return 'css';
      case 'javascript':
        return 'javascript';
      default:
        return 'plaintext';
    }
  });

  const syncContent = () => {
    if (!editor) return;
    const next = props.content || '';
    if (editor.getValue() !== next) {
      editor.setValue(next);
    }
  };

  const initEditor = async () => {
    try {
      monaco = await loadMonaco();
    } catch (e: any) {
      loading.value = false;
      Message.error({content: e?.message || '编辑器加载失败', duration: 3000});
      return;
    }
    if (!editorContainerRef.value) return;

    editor = monaco.editor.create(editorContainerRef.value, {
      value: props.content || '',
      language: monacoLang.value,
      theme: 'vs',
      readOnly: true,
      domReadOnly: true,
      automaticLayout: true,
      fontFamily: "'SF Mono', 'Monaco', 'Cascadia Code', 'Fira Code', monospace",
      fontSize: 13,
      lineHeight: 20,
      minimap: {enabled: false},
      scrollBeyondLastLine: false,
      wordWrap: 'on',
      renderLineHighlight: 'none',
      fixedOverflowWidgets: true,
      contextmenu: true,
      scrollbar: {
        verticalScrollbarSize: 10,
        horizontalScrollbarSize: 10,
      },
    });

    loading.value = false;
  };

  // 内容变化时刷新
  watch(() => props.content, () => {
    syncContent();
  });

  // 语言变化时切换高亮
  watch(monacoLang, (newLang) => {
    if (!editor || !monaco) return;
    const model = editor.getModel();
    if (model) monaco.editor.setModelLanguage(model, newLang);
  });

  onMounted(() => {
    initEditor();
  });

  onBeforeUnmount(() => {
    if (editor) {
      const model = editor.getModel();
      editor.dispose();
      if (model) model.dispose();
      editor = null;
    }
  });
</script>

<style scoped>
  .monaco-viewer {
    height: 100%;
    display: flex;
    flex-direction: column;
  }

  .toolbar {
    flex-shrink: 0;
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    background: var(--color-fill-2);
    border-bottom: 1px solid var(--color-border-2);
  }

  .editor-container {
    flex: 1;
    min-height: 0;
    overflow: hidden;
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
