<template>
  <div class="expr-suggest-input">
    <a-input
        ref="inputRef"
        :model-value="displayText"
        :placeholder="placeholder"
        :disabled="disabled"
        :allow-clear="allowClear"
        @input="onInput"
        @blur="onBlur"
        @keydown="onKeydown"
    >
      <template #suffix>
        <a-tooltip :content="showTemplate ? '插入函数 / 数据模板' : '插入函数'" :disabled="disabled">
          <icon-thunderbolt
              v-if="!disabled"
              class="trigger-icon"
              :class="{ 'is-active': suggestVisible }"
              @mousedown.prevent="toggleSuggest"
          />
        </a-tooltip>
        <slot name="suffix"/>
      </template>
    </a-input>

    <!-- 函数辅助下拉：输入 @ 或 fn. 触发（Teleport 到 body，避免被表格/抽屉 overflow 裁剪） -->
    <Teleport to="body">
      <div
          v-if="suggestVisible"
          class="suggest-dropdown"
          :style="dropdownStyle"
          @mousedown.prevent
      >
        <template v-if="filteredSuggestions.length">
          <div
              v-for="(item, idx) in filteredSuggestions"
              :key="item.insert + item.label"
              class="suggest-item"
              :class="{ 'is-active': idx === activeIndex, 'is-custom': item.kind === 'custom', 'is-template': item.kind === 'template' }"
              @mousedown.prevent="applySuggestion(item)"
              @mouseenter="activeIndex = idx"
          >
            <span class="suggest-label">{{ item.label }}</span>
            <span class="suggest-desc">{{ item.desc }}</span>
          </div>
        </template>
        <div v-else class="suggest-empty">无匹配函数</div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue';
import {Message} from '@arco-design/web-vue';
import {IconThunderbolt} from '@arco-design/web-vue/es/icon';
import {toDisplayText, toStorageText} from './customFunctionExpr';
import {getCustomFunctionList} from '@/api/MyApi/customFunction';
import {getDataTemplateList} from '@/api/MyApi/dataTemplate';
import {CustomFunction} from '@/types/domain/api/CustomFunction';
import {DataTemplate} from '@/types/domain/api/DataTemplate';
import useProjectStore from '@/store/modules/project';

interface SuggestItem {
  kind: 'builtin' | 'custom' | 'template';
  /** 下拉里显示的文本 */
  label: string;
  /** 插入到输入框的文本 */
  insert: string;
  desc: string;
}

const props = defineProps<{
  /** 存储层文本（可能含 {{__CUSTOM(id,...)}}） */
  modelValue?: string;
  placeholder?: string;
  disabled?: boolean;
  allowClear?: boolean;
  /**
   * 是否在建议下拉中提供数据模板项。
   * 模板生成的是 JSON 结构数据，仅适合 JSON 语境（Body JSON/Mock 响应体等），
   * 标量输入（Query/Header/断言预期值、UI 场景步骤输入）不应开启。
   * 后端 VariableReplacer 对 {{__TEMPLATE()__}} 的解析始终保留，此处只是入口开关。
   */
  showTemplate?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void;
  /** 失焦并完成表达式转换后触发（携带存储层文本） */
  (e: 'commit', value: string): void;
}>();

const projectStore = useProjectStore();
const inputRef = ref();

/** 输入框显示文本（fn.名称(...) 形式） */
const displayText = ref('');
/** 当前是否处于表达式编辑（含 fn. 引用时失焦才提交存储） */
const editing = ref(false);

// ==================== 函数来源 ====================

const customFunctions = ref<CustomFunction[]>([]);
const dataTemplates = ref<DataTemplate[]>([]);

const loadCustomFunctions = async () => {
  const projectId = projectStore.getProjectId;
  if (!projectId) {
    customFunctions.value = [];
    dataTemplates.value = [];
    return;
  }
  try {
    const res = await getCustomFunctionList(Number(projectId));
    customFunctions.value = res.data || [];
  } catch (e) {
    customFunctions.value = [];
  }
  try {
    const {data} = await getDataTemplateList(Number(projectId));
    dataTemplates.value = data || [];
  } catch (e) {
    dataTemplates.value = [];
  }
};

onMounted(loadCustomFunctions);

const resolveFnName = (id: number) => customFunctions.value.find(f => f.id === id)?.funcName;
const resolveFnId = (name: string) => customFunctions.value.find(f => f.funcName === name)?.id;

// 系统内置函数（@ 语法，后端 FunctionParser/AtSyntaxMockGenerator 直接解析）
const builtinSuggestions: SuggestItem[] = [
  {kind: 'builtin', label: '@phone()', insert: '@phone()', desc: '随机手机号'},
  {kind: 'builtin', label: '@email()', insert: '@email()', desc: '随机邮箱'},
  {kind: 'builtin', label: '@uuid()', insert: '@uuid()', desc: '随机 UUID'},
  {kind: 'builtin', label: '@cname()', insert: '@cname()', desc: '随机中文姓名'},
  {kind: 'builtin', label: '@ename()', insert: '@ename()', desc: '随机英文姓名'},
  {kind: 'builtin', label: '@idcard()', insert: '@idcard()', desc: '随机身份证号'},
  {kind: 'builtin', label: '@bankcard()', insert: '@bankcard()', desc: '随机银行卡号'},
  {kind: 'builtin', label: '@integer(min, max)', insert: "@integer(0, 100)", desc: '范围整数'},
  {kind: 'builtin', label: "@character('lower', 8)", insert: "@character('lower', 8)", desc: '指定字符集随机字符'},
  {kind: 'builtin', label: '@text(长度)', insert: '@text(10)', desc: '随机字符串'},
  {kind: 'builtin', label: '@boolean()', insert: '@boolean()', desc: '随机布尔值'},
  {kind: 'builtin', label: '@timestamp()', insert: '@timestamp()', desc: '当前时间戳'},
  {kind: 'builtin', label: "@date('yyyy-MM-dd')", insert: "@date('yyyy-MM-dd')", desc: '随机日期'},
  {kind: 'builtin', label: '@datetime()', insert: "@datetime('yyyy-MM-dd HH:mm:ss')", desc: '随机日期时间'},
  {kind: 'builtin', label: "@choice('a', 'b')", insert: "@choice('a', 'b')", desc: '枚举随机选择'},
  {kind: 'builtin', label: "@fixed('值')", insert: "@fixed('值')", desc: '固定值'},
  {kind: 'builtin', label: '@md5(文本)', insert: '@md5()', desc: 'MD5 加密（内置函数）'},
];

// 数据模板：每个模板一条具体建议（带真实 id，直接可搜模板名），无模板时回退到通用占位项。
// 仅 showTemplate=true（JSON 语境）时才会出现在下拉里。
const templateSuggestions = computed<SuggestItem[]>(() => {
  if (!props.showTemplate) return [];
  if (!dataTemplates.value.length) {
    return [{kind: 'template', label: '@template(模板id)', insert: '@template()', desc: '按数据模板生成单条数据（当前项目暂无模板）'}];
  }
  return dataTemplates.value.map(t => ({
    kind: 'template' as const,
    label: `@template(${t.templateName})`,
    insert: `@template(${t.id})`,
    desc: `数据模板·生成「${t.templateName}」单条数据`,
  }));
});

const customSuggestions = computed<SuggestItem[]>(() =>
    customFunctions.value.map(fn => ({
      kind: 'custom',
      label: `@fn.${fn.funcName}(${fn.funcParams || ''})`,
      insert: `@fn.${fn.funcName}(${fn.funcParams || ''})`,
      desc: fn.description || '项目自定义函数',
    })));

// ==================== 下拉触发与选择 ====================

const suggestVisible = ref(false);
const activeIndex = ref(0);
/** 触发片段在文本中的起始下标（@ 或 fn. 的起点） */
const triggerStart = ref(-1);
/** 触发片段后的过滤关键词 */
const keyword = ref('');
/** 下拉 fixed 定位（跟随输入框，Teleport 到 body 后不受父级 overflow 裁剪） */
const dropdownStyle = ref<Record<string, string>>({});

const updateDropdownPosition = () => {
  const el = getInputEl();
  if (!el) return;
  const rect = el.getBoundingClientRect();
  dropdownStyle.value = {
    top: `${rect.bottom + 4}px`,
    left: `${rect.left}px`,
    width: `${Math.max(rect.width, 260)}px`,
  };
};

const onWindowScroll = () => {
  if (suggestVisible.value) updateDropdownPosition();
};

watch(suggestVisible, (val) => {
  if (val) {
    updateDropdownPosition();
    window.addEventListener('scroll', onWindowScroll, true);
    window.addEventListener('resize', onWindowScroll);
  } else {
    window.removeEventListener('scroll', onWindowScroll, true);
    window.removeEventListener('resize', onWindowScroll);
  }
});

const filteredSuggestions = computed(() => {
  const kw = keyword.value.toLowerCase();
  const all = [...builtinSuggestions, ...templateSuggestions.value, ...customSuggestions.value];
  if (!kw) return all;
  return all.filter(item => item.label.toLowerCase().includes(kw));
});

// 点击闪电图标：手动开合建议下拉（不输入 @ 也能发现可用函数/模板）
const toggleSuggest = () => {
  if (props.disabled) return;
  if (suggestVisible.value) {
    suggestVisible.value = false;
    return;
  }
  const el = getInputEl();
  const cursor = el?.selectionStart ?? displayText.value.length;
  triggerStart.value = cursor; // 纯插入模式：不删除任何已输入字符
  keyword.value = '';
  activeIndex.value = 0;
  suggestVisible.value = true;
  requestAnimationFrame(() => getInputEl()?.focus());
};

const getInputEl = (): HTMLInputElement | null =>
    (inputRef.value?.$el || inputRef.value)?.querySelector?.('input') || null;

const onInput = (val: string, ev?: Event) => {
  displayText.value = val;
  const el = getInputEl();
  const cursor = el?.selectionStart ?? val.length;
  const before = val.slice(0, cursor);

  // 触发：@xxx 或 @fn.xxx（前面不能紧跟标识符字符）；@fn. 前缀时按 fn. 后的关键词过滤
  const atMatch = before.match(/(^|[^\w$.])@((?:fn\.)?[\w一-龥$]*)$/);

  if (atMatch) {
    const typed = atMatch[2];
    triggerStart.value = cursor - typed.length - 1;
    keyword.value = typed.startsWith('fn.') ? typed.slice(3) : typed;
    suggestVisible.value = true;
    activeIndex.value = 0;
  } else {
    suggestVisible.value = false;
  }

  // 非表达式文本：实时同步（与原生输入行为一致）
  if (!val.includes('fn.') && !val.includes('{{__CUSTOM(')) {
    emit('update:modelValue', val);
  } else {
    editing.value = true;
  }
};

const applySuggestion = (item: SuggestItem) => {
  const el = getInputEl();
  const cursor = el?.selectionStart ?? displayText.value.length;
  const before = displayText.value.slice(0, triggerStart.value);
  const after = displayText.value.slice(cursor);
  const next = before + item.insert + after;
  displayText.value = next;
  suggestVisible.value = false;
  editing.value = true;

  // 光标定位：自定义函数/空模板停在括号内（便于直接改参数占位），其余停在末尾
  const caretOffset = (item.kind === 'custom' || item.kind === 'template') && item.insert.endsWith('()')
      ? item.insert.length - 1
      : item.insert.length;
  const caret = before.length + caretOffset;
  requestAnimationFrame(() => {
    const input = getInputEl();
    if (input) {
      input.focus();
      input.setSelectionRange(caret, caret);
    }
  });
  // 选择后立即提交一次（表达式已完整）
  commitToStorage(next);
};

const onKeydown = (ev: KeyboardEvent) => {
  if (!suggestVisible.value) return;
  if (ev.key === 'ArrowDown') {
    ev.preventDefault();
    activeIndex.value = (activeIndex.value + 1) % Math.max(filteredSuggestions.value.length, 1);
  } else if (ev.key === 'ArrowUp') {
    ev.preventDefault();
    activeIndex.value = (activeIndex.value - 1 + filteredSuggestions.value.length) % Math.max(filteredSuggestions.value.length, 1);
  } else if (ev.key === 'Enter') {
    if (filteredSuggestions.value.length) {
      ev.preventDefault();
      applySuggestion(filteredSuggestions.value[activeIndex.value]);
    }
  } else if (ev.key === 'Escape') {
    suggestVisible.value = false;
  }
};

// ==================== 失焦提交（fn.名称 → 底层表达式） ====================

const commitToStorage = (text: string) => {
  const {text: storageText, unknownNames} = toStorageText(text, resolveFnId);
  editing.value = false;
  emit('update:modelValue', storageText);
  emit('commit', storageText);
  if (unknownNames.length) {
    Message.warning(`未找到自定义函数：${unknownNames.join('、')}（已按原文保留，不会生效）`);
  }
};

const onBlur = () => {
  // 延迟关闭，让下拉项的 mousedown 先触发
  setTimeout(() => {
    suggestVisible.value = false;
  }, 150);
  if (editing.value || displayText.value.includes('fn.')) {
    commitToStorage(displayText.value);
  } else {
    emit('commit', displayText.value);
  }
};

// 外部值变化（回显/重置）→ 转显示文本
watch(() => props.modelValue, (val) => {
  const storage = val ?? '';
  if (!storage.includes('{{__CUSTOM(')) {
    displayText.value = storage;
    editing.value = false;
    return;
  }
  const display = toDisplayText(storage, resolveFnName);
  displayText.value = display;
  editing.value = display !== storage || display.includes('fn.');
}, {immediate: true});

// 函数列表加载完成后刷新一次显示（名称解析依赖列表）
watch(customFunctions, () => {
  const storage = props.modelValue ?? '';
  if (storage.includes('{{__CUSTOM(')) {
    displayText.value = toDisplayText(storage, resolveFnName);
  }
});
</script>

<style scoped>
.expr-suggest-input {
  position: relative;
  width: 100%;
}

.suggest-dropdown {
  position: fixed;
  max-height: 260px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  z-index: 3000;
  padding: 4px;
}

.suggest-item {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}

.suggest-item.is-active {
  background: var(--color-fill-2);
}

.suggest-label {
  font-family: monospace;
  font-weight: 600;
  color: rgb(var(--primary-6));
  flex-shrink: 0;
}

.suggest-item.is-custom .suggest-label {
  color: #d46b08;
}

.suggest-item.is-template .suggest-label {
  color: #7b61ff;
}

.trigger-icon {
  cursor: pointer;
  color: var(--color-text-3);
  font-size: 14px;
  transition: color 0.15s;
}

.trigger-icon:hover,
.trigger-icon.is-active {
  color: rgb(var(--primary-6));
}

.suggest-desc {
  color: var(--color-text-3);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.suggest-empty {
  padding: 12px;
  text-align: center;
  color: var(--color-text-3);
  font-size: 13px;
}
</style>
