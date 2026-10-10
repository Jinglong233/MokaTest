<template>
  <a-card size="small" :bordered="false" class="rv-card source-card">
    <template #title>{{ $t('qa.ai.review.source.title') }}</template>
    <template #extra>
      <a-space :size="4">
        <a-button
          v-if="!editing"
          v-permission="'qa:requirement:update'"
          size="mini"
          type="text"
          @click="enterEdit"
        >
          {{ $t('qa.ai.common.edit') }}
        </a-button>
        <template v-else>
          <a-button size="mini" type="primary" :loading="saving" @click="onSave">{{ $t('qa.common.save') }}</a-button>
          <a-button size="mini" @click="cancelEdit">{{ $t('qa.common.cancel') }}</a-button>
        </template>
      </a-space>
    </template>
    <div class="source-hint">
      {{ editing ? $t('qa.ai.review.source.hintEditing') : $t('qa.ai.review.source.hint') }}
    </div>
    <!-- RichEditor：只读预览 / 编辑双态，与需求池同一组件保证渲染一致 -->
    <div ref="wrapRef" class="source-wrap">
      <RichEditor
        v-model="editHtml"
        :readonly="!editing"
        :auto-height="true"
        :placeholder="$t('qa.ai.review.source.empty')"
      />
    </div>
  </a-card>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import RichEditor from '@/components/rich-editor/index.vue';

const { t } = useI18n();

const props = defineProps<{
  title?: string;
  html?: string;
  /** 保存中（父组件控制） */
  saving?: boolean;
}>();

const emit = defineEmits<{
  save: [html: string];
}>();

const wrapRef = ref<HTMLElement>();
const editing = ref(false);
const editHtml = ref(props.html || '');

watch(() => props.html, (v) => {
  if (!editing.value) editHtml.value = v || '';
});

function enterEdit() {
  editHtml.value = props.html || '';
  editing.value = true;
}

function cancelEdit() {
  editing.value = false;
  editHtml.value = props.html || '';
}

function onSave() {
  emit('save', editHtml.value);
}

/** 保存成功后由父组件调用，退出编辑态 */
function exitEdit() {
  editing.value = false;
}

/** 规范化（去所有空白，与后端 quote 校验的 normalize 对齐） */
function normalize(s: string) {
  return s.replace(/\s+/g, '');
}

/** 清除上一次高亮 */
function clearMarks() {
  const root = wrapRef.value;
  if (!root) return;
  root.querySelectorAll('mark.quote-hit').forEach((mark) => {
    const parent = mark.parentNode;
    if (!parent) return;
    while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
    parent.removeChild(mark);
    parent.normalize();
  });
}

interface TextNodeInfo { node: Text; start: number; end: number }

/**
 * quote 定位：TreeWalker 遍历文本节点 → 规范化文本匹配 → 偏移映射回 DOM → mark 高亮
 * 编辑态下不可用（避免污染编辑器文档模型）
 */
function locateQuote(quote: string) {
  if (editing.value) {
    Message.info(t('qa.ai.review.source.locateEditing'));
    return;
  }
  const root = wrapRef.value;
  if (!root || !quote) {
    Message.warning(t('qa.ai.review.source.quoteNotFound'));
    return;
  }
  clearMarks();

  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes: TextNodeInfo[] = [];
  let rawText = '';
  let n = walker.nextNode();
  while (n) {
    const text = n.textContent || '';
    nodes.push({ node: n as Text, start: rawText.length, end: rawText.length + text.length });
    rawText += text;
    n = walker.nextNode();
  }

  const normToRaw: number[] = [];
  let normText = '';
  for (let i = 0; i < rawText.length; i++) {
    if (!/\s/.test(rawText[i])) {
      normToRaw.push(i);
      normText += rawText[i];
    }
  }
  const nq = normalize(quote);
  const hit = normText.indexOf(nq);
  if (hit < 0 || nq.length === 0) {
    Message.warning(t('qa.ai.review.source.quoteNotPrecise'));
    return;
  }
  const rawStart = normToRaw[hit];
  const rawEnd = normToRaw[hit + nq.length - 1] + 1;

  let firstMark: HTMLElement | null = null;
  for (const info of nodes) {
    const s = Math.max(rawStart, info.start);
    const e = Math.min(rawEnd, info.end);
    if (s >= e) continue;
    const textNode = info.node;
    const mark = document.createElement('mark');
    mark.className = 'quote-hit';
    const mid = textNode.splitText(s - info.start);
    mid.splitText(e - s);
    mark.appendChild(mid.cloneNode(true));
    textNode.parentNode?.replaceChild(mark, mid);
    if (!firstMark) firstMark = mark;
  }

  if (firstMark) {
    firstMark.scrollIntoView({ behavior: 'smooth', block: 'center' });
    setTimeout(() => clearMarks(), 3000);
  }
}

defineExpose({ locateQuote, enterEdit, exitEdit });
</script>

<style scoped>
.source-card { height: 100%; overflow-y: auto; }
.source-hint { font-size: 11px; color: var(--color-text-3); margin-bottom: 8px; }
.source-wrap { font-size: 13px; }
.source-wrap :deep(mark.quote-hit) {
  background: rgb(var(--gold-2));
  padding: 1px 2px;
  border-radius: 2px;
  transition: background 0.5s;
}
</style>
