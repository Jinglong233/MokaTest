<template>
  <div class="chat-panel">
    <div ref="chatBodyRef" class="chat-body" @scroll="onScroll">
      <template v-if="messages.length">
        <div v-for="(msg, idx) in messages" :key="msg._uid ?? idx" class="msg" :class="msg.role">
          <!-- 用户消息 -->
          <template v-if="msg.role === 'user'">
            <div class="bubble user-bubble">
              <div>{{ msg.content }}</div>
            </div>
            <a-avatar :size="28" class="msg-avatar user-avatar" shape="circle"><IconUser /></a-avatar>
          </template>
          <!-- AI 消息 -->
          <template v-else-if="msg.role === 'assistant'">
            <a-avatar v-if="idx === 0 || messages[idx - 1].role === 'user'" :size="28" class="msg-avatar ai-avatar" shape="circle"><IconRobot /></a-avatar>
            <div v-else class="avatar-placeholder"></div>
            <div class="msg-content">
              <!-- markstream-vue：专为流式 Markdown 设计，增量 AST 解析 + 内置打字光标 -->
              <div class="bubble ai-bubble">
                <MarkdownRender :content="msg.content || ''" :final="msg._done" mode="chat"
                  custom-id="ai-chat"
                  :custom-html-tags="['tool-status']"
                  :typewriter="!msg._done"
                  :code-block-props="{ showHeader: true, showTooltips: true }"
                  @copy-code="handleCopyCode" />
              </div>
              <div v-if="msg.skillScene && props.skills?.length" class="skill-chip" :class="{ 'skill-chip-disabled': skillDisabled(msg.skillScene) }">
                <a-tooltip :content="skillDisabled(msg.skillScene) ? $t('qa.ai.chat.skillDisabled') : $t('qa.ai.chat.skillDriven', { name: skillName(msg.skillScene) })">
                  <span>⚡ {{ skillName(msg.skillScene) }}</span>
                </a-tooltip>
              </div>
              <div v-if="msg.citations?.length" class="msg-citations">
                <IconBook /> {{ $t('qa.ai.chat.basis') }} <template v-for="(t, i) in msg.citations" :key="i">《{{ t }}》{{ i < msg.citations.length - 1 ? ' ' : '' }}</template>
              </div>
              <div v-if="msg._error" class="msg-error">
                <IconExclamationCircle /> {{ msg._errorMsg }}
              </div>
              <div v-if="!generating && idx === messages.length - 1 && msg._done" class="msg-actions">
                <a-button v-if="msg._stopped" size="mini" type="outline" @click="$emit('continue')">{{ $t('qa.ai.chat.continue') }}</a-button>
                <a-button v-if="msg._error" size="mini" status="danger" @click="$emit('retry')">
                  <template #icon><IconRefresh /></template>
                  {{ $t('qa.ai.common.retry') }}
                </a-button>
                <a-button v-else size="mini" @click="$emit('regenerate')">
                  <template #icon><IconRefresh /></template>
                  {{ $t('qa.ai.common.regenerate') }}
                </a-button>
              </div>
            </div>
          </template>
          <!-- 卡片消息：fallback 渲染工作台内置卡片，调用方可用 #card 插槽完全接管 -->
          <template v-else-if="msg.role === 'card'">
            <a-avatar :size="28" class="msg-avatar ai-avatar" shape="circle"><IconRobot /></a-avatar>
            <div class="msg-content">
              <slot name="card" :msg="msg" :on-card-event="onCardEvent">
                <CasegenEntryCard
                  v-if="msg.cardType === 'casegen-workflow' || msg.cardType === 'req-confirm' || msg.cardType === 'test-points' || msg.cardType === 'gen-progress' || msg.cardType === 'adopt-workspace' || msg.cardType === 'adopt-result'"
                  :data="msg.data" :disabled="msg.done || props.generating" />
                <ReviewEntryCard
                  v-else-if="msg.cardType === 'review-entry'"
                  :data="msg.data" :disabled="msg.done || props.generating" />
                <CaseTypeCard
                  v-else-if="msg.cardType === 'case-type-select'"
                  :data="msg.data" :disabled="msg.done || props.generating"
                  @select="(type) => onCardEvent({ cardType: 'case-type-select', type })" />
                <ApiCandidateCard
                  v-else-if="msg.cardType === 'api-candidates'"
                  :data="msg.data" :disabled="msg.done || props.generating"
                  @select="(api) => onCardEvent({ cardType: 'api-candidates', api })" />
                <CaseDraftConfirmCard
                  v-else-if="msg.cardType === 'case-draft'"
                  :data="msg.data" :disabled="msg.done || props.generating"
                  @saved="(data) => onCardEvent({ cardType: 'case-draft', action: 'saved', data, msg })" />
              </slot>
            </div>
          </template>
        </div>
        <!-- 等待AI响应的loading气泡（用户发送后、第一个token到达前） -->
        <div v-if="generating && !hasActiveAiMessage" class="msg assistant">
          <a-avatar :size="28" class="msg-avatar ai-avatar" shape="circle"><IconRobot /></a-avatar>
          <div class="msg-content">
            <div class="bubble ai-bubble">
              <span class="thinking-dots">
                <span class="dot"></span>
                <span class="dot"></span>
                <span class="dot"></span>
              </span>
            </div>
          </div>
        </div>
      </template>
      <div v-else class="empty-suggestions">
        <slot name="empty">
          <p class="suggestion-hint">{{ $t('qa.ai.chat.suggestHint') }}</p>
          <div class="suggestion-chips">
            <span v-for="s in suggestions" :key="s" class="suggestion-chip" @click="$emit('suggestion', s)">{{ s }}</span>
          </div>
        </slot>
      </div>
      <!-- 回到底部按钮 -->
      <div v-if="!isAtBottom && messages.length" class="scroll-bottom-btn" @click="forceScrollToBottom">
        <IconArrowDown />
      </div>
    </div>
  </div>
</template>

<script lang="ts">
export default { name: 'AiChatPanel' };
</script>

<script setup lang="ts">
import { ref, nextTick, computed } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import { IconBook, IconCheckCircle, IconCloseCircle, IconLoading, IconCaretDown, IconArrowDown, IconRefresh, IconExclamationCircle, IconRobot, IconUser } from '@arco-design/web-vue/es/icon';
import MarkdownRender, { setCustomComponents } from 'markstream-vue';
import 'markstream-vue/index.css';
import CasegenEntryCard from './cards/CasegenEntryCard.vue';
import ReviewEntryCard from './cards/ReviewEntryCard.vue';
import CaseTypeCard from './CaseTypeCard.vue';
import ApiCandidateCard from './ApiCandidateCard.vue';
import CaseDraftConfirmCard from './CaseDraftConfirmCard.vue';
import ToolStatusNode from './ToolStatusNode.vue';

setCustomComponents('ai-chat', {
  'tool-status': ToolStatusNode,
});

const props = withDefaults(defineProps<{
  messages: any[];
  generating: boolean;
  isAtBottom: boolean;
  suggestions?: string[];
  skills?: any[];
  toolLabels?: Record<string, string>;
}>(), {
  // 默认值即 qa 工作台工具名映射；编译器要求默认值内联，不能引用局部常量
  toolLabels: () => ({
    search_requirements: 'qa.ai.tool.searchRequirements',
    search_cases: 'qa.ai.tool.searchCases',
    search_bugs: 'qa.ai.tool.searchBugs',
    search_knowledge: 'qa.ai.tool.searchKnowledge',
    get_requirement_trace: 'qa.ai.tool.getRequirementTrace',
    create_test_case: 'qa.ai.tool.createTestCase',
    start_case_generation: 'qa.ai.tool.startCaseGeneration',
    ask_case_gen_type: 'qa.ai.tool.askCaseGenType',
    search_apis: 'qa.ai.tool.searchApis',
    start_api_case_generation: 'qa.ai.tool.startApiCaseGeneration',
  }),
});

/** 从 skills prop 查找指令名 */
function skillName(scene: string): string {
  return props.skills?.find((s: any) => s.scene === scene)?.name || scene;
}

/** 指令是否已停用/删除（归因胶囊变灰） */
function skillDisabled(scene: string): boolean {
  const s = props.skills?.find((sk: any) => sk.scene === scene);
  return !s || s.enabled !== 1;
}

/** 判断最后一条消息是否是 AI 侧消息（assistant/tool/card），用于决定是否显示等待loading */
const hasActiveAiMessage = computed(() => {
  const last = props.messages[props.messages.length - 1];
  return last && ['assistant', 'tool', 'card'].includes(last.role);
});

const emit = defineEmits<{
  regenerate: [];  continue: [];  retry: [];
  cardEvent: [data: any];
  suggestion: [text: string];
  scroll: [atBottom: boolean];
}>();

/** #card 插槽回传卡片事件（工作台卡片事件总线，插槽实现可自由选择是否使用） */
function onCardEvent(data: any) {
  emit('cardEvent', data);
}

/** 工具名 → 动作标签（主流交互：不暴露底层函数名和查询参数，只告诉用户 AI 在做什么） */
const { t } = useI18n();

function toolActionLabel(toolName: string, pending: boolean): string {
  const key = props.toolLabels[toolName];
  if (key) {
    const name = t(key);
    return pending ? t('qa.ai.tool.pending', { name }) : t('qa.ai.tool.done', { name });
  }
  return pending
    ? t('qa.ai.tool.genericPending', { name: toolName })
    : t('qa.ai.tool.genericDone', { name: toolName });
}

const chatBodyRef = ref<HTMLElement>();

function onScroll() {
  if (!chatBodyRef.value) return;
  const el = chatBodyRef.value;
  emit('scroll', el.scrollHeight - el.scrollTop - el.clientHeight < 50);
}

function forceScrollToBottom() {
  emit('scroll', true);
  nextTick(() => {
    if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight;
  });
}

function getChatBody() { return chatBodyRef.value; }

/** markstream-vue copy-code 事件：用户点击代码块复制按钮时触发 */
function handleCopyCode(text: string) {
  navigator.clipboard.writeText(text).then(() => {
    Message.success(t('qa.ai.common.copied'));
  });
}

defineExpose({ getChatBody, chatBodyRef });
</script>

<style scoped>
.chat-panel { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.chat-body {
  flex: 1; overflow-y: auto; padding: 16px; position: relative;
}
.chat-body > div {
  max-width: 860px; margin: 0 auto;
}
.msg { margin-bottom: 12px; display: flex; gap: 8px; align-items: flex-start; }
.msg.user { justify-content: flex-end; }
.msg-avatar { flex-shrink: 0; }
.avatar-placeholder { width: 28px; flex-shrink: 0; }
.user-avatar { background: rgb(var(--primary-6)); color: #fff; }
.ai-avatar { background: var(--color-fill-3); color: var(--color-text-2); }
.msg-content { flex: 1; min-width: 0; }
.msg-actions { margin-top: 4px; }
.msg-citations { margin-top: 4px; font-size: 12px; color: var(--color-text-3); display: flex; align-items: center; gap: 4px; flex-wrap: wrap; }
.skill-chip { margin-top: 4px; }
.skill-chip span {
  display: inline-flex; align-items: center; gap: 3px;
  font-size: 12px; padding: 1px 10px; border-radius: 10px;
  background: rgb(var(--primary-1)); color: rgb(var(--primary-6)); cursor: default;
}
.skill-chip-disabled span { background: var(--color-fill-2); color: var(--color-text-3); }
.msg-error { margin-top: 4px; font-size: 12px; color: rgb(var(--danger-6)); display: flex; align-items: center; gap: 4px; }
.thinking-dots { display: inline-flex; gap: 4px; padding: 4px 0; }
.thinking-dots .dot { width: 6px; height: 6px; border-radius: 50%; background: var(--color-text-3); animation: thinking-bounce 1.4s infinite ease-in-out; }
.thinking-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes thinking-bounce { 0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; } 40% { transform: scale(1); opacity: 1; } }
.bubble {
  display: inline-block; max-width: 80%; padding: 8px 12px; border-radius: 8px;
  font-size: 13px; line-height: 1.6; text-align: left; word-break: break-word;
}
.user-bubble { background: rgb(var(--primary-6)); color: #fff; }
.ai-bubble { background: var(--color-fill-2); color: var(--color-text-1); }
.ai-bubble :deep(h1), .ai-bubble :deep(h2), .ai-bubble :deep(h3),
.ai-bubble :deep(h4), .ai-bubble :deep(h5), .ai-bubble :deep(h6) { margin: 12px 0 6px; font-weight: 600; line-height: 1.3; }
.ai-bubble :deep(h1) { font-size: 18px; }
.ai-bubble :deep(h2) { font-size: 16px; }
.ai-bubble :deep(h3) { font-size: 14px; }
.ai-bubble :deep(h4), .ai-bubble :deep(h5), .ai-bubble :deep(h6) { font-size: 13px; }
.ai-bubble :deep(p) { margin: 6px 0; }
.ai-bubble :deep(ul), .ai-bubble :deep(ol) { margin: 6px 0; padding-left: 20px; }
.ai-bubble :deep(li) { margin: 2px 0; }
.ai-bubble :deep(blockquote) { margin: 6px 0; padding: 4px 12px; border-left: 3px solid var(--color-border-3); color: var(--color-text-3); }
.ai-bubble :deep(a) { color: rgb(var(--primary-6)); text-decoration: none; }
.ai-bubble :deep(a:hover) { text-decoration: underline; }
.ai-bubble :deep(table) { border-collapse: collapse; margin: 8px 0; font-size: 12px; width: 100%; }
.ai-bubble :deep(th), .ai-bubble :deep(td) { border: 1px solid var(--color-border-2); padding: 4px 8px; text-align: left; }
.ai-bubble :deep(th) { background: var(--color-fill-3); font-weight: 600; }
.ai-bubble :deep(hr) { border: none; border-top: 1px solid var(--color-border-2); margin: 10px 0; }
.ai-bubble :deep(pre) { background: var(--color-fill-3); padding: 10px; border-radius: 4px; overflow-x: auto; margin: 6px 0; font-size: 12px; line-height: 1.5; position: relative; }
.ai-bubble :deep(pre code) { background: none; padding: 0; font-size: 12px; }
.ai-bubble :deep(code) { background: var(--color-fill-3); padding: 2px 4px; border-radius: 2px; font-size: 12px; }
.ai-bubble :deep(img) { max-width: 100%; border-radius: 4px; }
.tool-card { border: 1px solid var(--color-border-2); border-radius: 4px; max-width: 80%; overflow: hidden; }
.tool-card.tool-error { border-color: rgb(var(--danger-3)); }
.tool-header { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--color-text-2); padding: 6px 12px; cursor: pointer; user-select: none; }
.tool-header:hover { background: var(--color-fill-2); }
.tool-icon { font-size: 14px; flex-shrink: 0; }
.tool-icon-pending { color: var(--color-text-3); animation: spin 1s linear infinite; }
.tool-icon-success { color: rgb(var(--success-6)); }
.tool-icon-error { color: rgb(var(--danger-6)); }
@keyframes spin { from { transform: rotate(0); } to { transform: rotate(360deg); } }
.tool-name { font-weight: 600; flex-shrink: 0; }
.tool-args { color: var(--color-text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; min-width: 0; }
.tool-duration { color: var(--color-text-3); flex-shrink: 0; }
.tool-caret { font-size: 10px; color: var(--color-text-3); transition: transform 0.2s; flex-shrink: 0; }
.tool-caret-open { transform: rotate(180deg); }
.tool-result { font-size: 12px; color: var(--color-text-2); padding: 8px 12px; border-top: 1px solid var(--color-fill-2); white-space: pre-wrap; word-break: break-word; max-height: 240px; overflow-y: auto; }
.scroll-bottom-btn { position: absolute; bottom: 12px; left: 50%; transform: translateX(-50%); width: 32px; height: 32px; border-radius: 50%; background: var(--color-bg-1); border: 1px solid var(--color-border-2); box-shadow: 0 2px 8px rgba(0,0,0,0.12); display: flex; align-items: center; justify-content: center; cursor: pointer; z-index: 10; transition: all 0.2s; }
.scroll-bottom-btn:hover { border-color: rgb(var(--primary-5)); color: rgb(var(--primary-6)); }
.empty-suggestions { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; gap: 12px; }
.suggestion-hint { font-size: 13px; color: var(--color-text-3); }
.suggestion-chips { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; max-width: 480px; }
.suggestion-chip { padding: 6px 14px; font-size: 13px; border: 1px solid var(--color-border-2); border-radius: 16px; background: var(--color-bg-1); color: var(--color-text-2); cursor: pointer; transition: all 0.2s; white-space: nowrap; }
.suggestion-chip:hover { border-color: rgb(var(--primary-5)); color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); }
</style>
