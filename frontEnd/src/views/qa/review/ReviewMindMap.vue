<template>
  <div class="rm-mindmap-wrapper">
    <div ref="mapRef" class="rm-mindmap-canvas"></div>
    <!-- 「更多」下拉（放组件层渲染，避免 foreignObject 裁剪） -->
    <div
      v-if="moreMenu.visible"
      class="clm-add-menu"
      :style="{ left: moreMenu.x + 'px', top: moreMenu.y + 'px' }"
      @mousedown.stop
    >
      <div
        v-for="item in moreMenu.items"
        :key="item.key"
        class="clm-add-item"
        :class="{ 'clm-add-item-danger': item.danger }"
        @click="handleMoreAction(item.key)"
      >
        {{ item.text }}
      </div>
    </div>
    <div class="rm-mindmap-toolbar">
      <span class="hint">{{ $t('qa.review.mapStats', { modules: moduleCount, cases: caseCount }) }}</span>
      <span class="hint">{{ $t('qa.review.mapHint') }}</span>
      <span v-if="loadingDetail" class="hint loading">{{ $t('qa.review.loadingDetail') }}</span>
    </div>

    <!-- 我的评审浮动面板（点击用例节点「评审」按钮后出现） -->
    <div
      v-if="reviewPanel.visible"
      class="rm-review-panel"
      :style="{ left: reviewPanel.x + 'px', top: reviewPanel.y + 'px' }"
      @mousedown.stop
    >
      <div class="rm-review-title">
        {{ $t('qa.review.myReview') }}
        <span class="rm-review-case">{{ reviewPanel.record?.caseName }}</span>
        <a-tag v-if="reviewPanel.existing && reviewPanel.existing !== 'PENDING'" size="small" :color="reviewPanel.existing === 'PASS' ? 'green' : 'red'">
          {{ $t('qa.review.reviewedTag', { conclusion: reviewPanel.existing === 'PASS' ? $t('qa.review.resultPass') : $t('qa.review.resultReject') }) }}
        </a-tag>
        <span class="rm-review-close" @click="reviewPanel.visible = false">×</span>
      </div>
      <a-radio-group v-model="reviewPanel.conclusion">
        <a-radio value="PASS">{{ $t('qa.review.resultPass') }}</a-radio>
        <a-radio value="REJECT">{{ $t('qa.review.resultReject') }}</a-radio>
      </a-radio-group>
      <a-textarea
        v-model="reviewPanel.comment"
        :auto-size="{ minRows: 2, maxRows: 4 }"
        :placeholder="reviewPanel.conclusion === 'REJECT' ? $t('qa.review.commentRequired') : $t('qa.review.commentOptional')"
      />
      <div class="rm-review-actions">
        <a-button type="primary" size="small" :loading="reviewPanel.submitting" @click="handleSubmitReview">{{ $t('qa.review.submit') }}</a-button>
      </div>
    </div>

    <!-- 用例详情抽屉：基本信息 + 评审历史 -->
    <a-drawer v-model:visible="detailDrawer.visible" :width="620" :footer="false">
      <template #title>
        <span>{{ detailDrawer.detail?.caseName || $t('qa.review.caseDetailTitle') }}</span>
        <a-tag v-if="detailDrawer.record" :color="finalResultColor(detailDrawer.record.finalResult)" size="small" style="margin-left: 8px">
          {{ finalResultText(detailDrawer.record.finalResult) }}
        </a-tag>
      </template>
      <a-tabs v-model:active-key="detailDrawer.tab">
        <a-tab-pane key="basic" :title="$t('qa.review.tabBasic')">
          <a-descriptions :column="1" size="small">
            <a-descriptions-item :label="$t('qa.testCase.code')">{{ detailDrawer.detail?.caseCode || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('qa.review.module')">{{ detailDrawer.detail?.moduleName || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('qa.review.caseLevel')">
              <a-tag v-if="detailDrawer.detail?.priority" :color="priorityColor(detailDrawer.detail.priority)" size="small">{{ detailDrawer.detail.priority }}</a-tag>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('qa.review.tags')">
              <template v-if="detailDrawer.detail?.tags">
                <a-tag v-for="tag in String(detailDrawer.detail.tags).split(',').filter(Boolean)" :key="tag" size="small">{{ tag }}</a-tag>
              </template>
              <span v-else>-</span>
            </a-descriptions-item>
            <a-descriptions-item :label="$t('qa.review.creator')">{{ detailDrawer.detail?.createUserName || '-' }}</a-descriptions-item>
            <a-descriptions-item :label="$t('qa.review.createTime')">{{ detailDrawer.detail?.createTime || '-' }}</a-descriptions-item>
          </a-descriptions>
          <div class="drawer-section-title">{{ $t('qa.testCase.preCondition') }}</div>
          <div v-if="detailDrawer.detail?.preCondition" class="pre-condition-view" v-html="detailDrawer.detail.preCondition"></div>
          <div v-else class="pre-condition-empty">{{ $t('qa.review.none') }}</div>
          <div class="drawer-section-title">{{ $t('qa.testCase.steps') }}</div>
          <a-table v-if="detailDrawer.steps.length" :data="detailDrawer.steps" :pagination="false" size="small" row-key="index" :bordered="{ cell: true }">
            <template #columns>
              <a-table-column title="#" :width="50" align="center">
                <template #cell="{ rowIndex }">{{ rowIndex + 1 }}</template>
              </a-table-column>
              <a-table-column :title="$t('qa.testCase.step')">
                <template #cell="{ record }"><div v-html="record.step || '-'"></div></template>
              </a-table-column>
              <a-table-column :title="$t('qa.testCase.expected')">
                <template #cell="{ record }"><div v-html="record.expected || '-'"></div></template>
              </a-table-column>
            </template>
          </a-table>
          <a-empty v-else :description="$t('qa.review.noSteps')" />
        </a-tab-pane>
        <a-tab-pane key="history" :title="$t('qa.review.tabHistory')">
          <template v-if="detailDrawer.history.length">
            <div v-for="(h, idx) in detailDrawer.history" :key="idx" class="vote-item">
              <a-avatar :size="32" :image-url="h.reviewerAvatar || undefined" class="vote-avatar">
                {{ (h.reviewerName || '#').slice(0, 1) }}
              </a-avatar>
              <div class="vote-main">
                <div class="vote-name-line">
                  <span class="vote-name">{{ h.reviewerName || `#${h.reviewerId}` }}</span>
                  <span v-if="h.reviewCode" class="vote-review">{{ h.reviewCode }} · {{ h.reviewTitle }}</span>
                </div>
                <div v-if="h.comment" class="vote-comment">{{ h.comment }}</div>
                <div class="vote-time">{{ h.reviewTime || '' }}</div>
              </div>
              <a-tag class="vote-result" size="small" :color="h.conclusion === 'PASS' ? 'green' : h.conclusion === 'REJECT' ? 'red' : 'gray'">
                {{ h.conclusion === 'PASS' ? $t('qa.review.resultPass') : h.conclusion === 'REJECT' ? $t('qa.review.resultReject') : $t('qa.review.resultPending') }}
              </a-tag>
            </div>
          </template>
          <a-empty v-else :description="$t('qa.review.noHistory')" style="padding: 40px 0" />
        </a-tab-pane>
      </a-tabs>
    </a-drawer>

    <!-- 修改评审人弹窗 -->
    <a-modal
      v-model:visible="reviewerModal.visible"
      :title="$t('qa.review.editReviewers')"
      :ok-loading="reviewerModal.saving"
      @ok="handleSaveReviewers"
    >
      <a-select
        v-model="reviewerModal.ids"
        multiple
        :placeholder="$t('qa.review.reviewerPlaceholder')"
        style="width: 100%"
      >
        <a-option v-for="r in reviewers" :key="r.reviewerId" :value="r.reviewerId">
          {{ r.reviewerName || `#${r.reviewerId}` }}
        </a-option>
      </a-select>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, onBeforeUnmount, nextTick, watch} from 'vue';
import {useI18n} from 'vue-i18n';
import {Message, Modal} from '@arco-design/web-vue';
import MindMap from 'simple-mind-map/dist/simpleMindMap.esm.min.js';
import 'simple-mind-map/dist/simpleMindMap.esm.min.css';
import {getTestCaseDetail, getCaseReviewHistory, updateReviewCaseReviewers, resetReviewCase, submitTestCaseReview} from '@/api/MyApi/qa';

const props = defineProps<{
  cases: any[];          // 当前评审单关联的用例（含 finalResult/priority/moduleId/caseName/items）
  moduleTree: any[];     // 用例模块树（CASE 模块）
  selectedModuleId?: number; // 左侧选中的模块：选中则根节点为该模块，否则为「全部用例」
  reviewStatus: string;  // 评审单状态 REVIEWING/COMPLETED/CANCELLED
  isCreator: boolean;
  reviewId: number;      // 评审单 ID（修改评审人/重新评审/提交评审用）
  reviewers: any[];      // 评审单评审人列表（修改评审人下拉）
  myId: number;          // 当前登录用户 ID（判断是否该用例的在册评审人）
}>();

const emit = defineEmits<{
  (e: 'review', record: any): void;   // 评审（跳工作台）
  (e: 'remove', record: any): void;   // 取消关联
  (e: 'refresh'): void;               // 需要父页刷新评审单数据
}>();

const {t} = useI18n();

const mapRef = ref<HTMLElement>();
let mind: any = null;
let resizeObserver: ResizeObserver | null = null;
const loadingDetail = ref(false);
const moduleCount = ref(0);
const caseCount = ref(0);
const detailCache = new Map<string, any[]>();

function stripHtml(text: string): string {
  return (text || '').replace(/<[^>]*>/g, '').replace(/&nbsp;/g, ' ').trim();
}

function escapeHtml(text: string): string {
  return (text || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

// 自绘优先级圆形徽标注册进内置图标库
function badgeSvg(text: string, color: string): string {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="44" height="44" viewBox="0 0 44 44"><circle cx="22" cy="22" r="20" fill="${color}"/><text x="22" y="29" text-anchor="middle" font-size="19" font-weight="bold" fill="#ffffff" font-family="Arial, sans-serif">${text}</text></svg>`;
}
if (Array.isArray((MindMap as any).iconList) && !(MindMap as any).iconList.some((g: any) => g.type === 'casePriority')) {
  (MindMap as any).iconList.push({
    type: 'casePriority',
    list: [
      {name: 'P0', icon: badgeSvg('P0', '#f53f3f')},
      {name: 'P1', icon: badgeSvg('P1', '#ff7d00')},
      {name: 'P2', icon: badgeSvg('P2', '#165dff')},
    ],
  });
}

const PRIORITY_ICON: Record<string, string> = {P0: 'casePriority_P0', P1: 'casePriority_P1', P2: 'casePriority_P2'};

// 节点形态兼容：渲染树里既有节点实例（nodeData.data），也可能是纯数据（data）
const uidOf = (n: any): string => n?.nodeData?.data?.uid ?? n?.data?.uid ?? '';
const childrenOf = (n: any): any[] => n?.children || n?.nodeData?.children || [];

// 节点类型视觉规范：tag（类型标识，SVG 原生渲染在文本后）+ 底色/文字色
const mkTag = (text: string, fill: string) => ({text, style: {fill, radius: 3, fontSize: 12, height: 20, paddingX: 8}});
const TYPE_TAG = {
  mod: (count: number) => mkTag(t('qa.review.tagModule', {count}), '#165dff'),
  pre: () => mkTag(t('qa.review.tagPre'), '#86909c'),
  step: (index?: number) => mkTag(index ? t('qa.review.tagStep', {index}) : t('qa.testCase.step'), '#722ed1'),
  expect: () => mkTag(t('qa.review.tagExpect'), '#00b42a'),
};
// 各类型节点底色/文字色（data 里的 fillColor 等渲染为节点形状）
const NODE_TYPE_STYLE = {
  case: {fillColor: '#ffffff', borderColor: '#c9cdd4', borderWidth: 1, borderRadius: 4},
  pre: {fillColor: '#f2f3f5', color: '#4e5969'},
  step: {fillColor: '#f5e8ff', color: '#722ed1'},
  expect: {fillColor: '#e8ffea', color: '#00b42a'},
};
// 评审结果 tag 颜色（文案走 finalResultText）
const RESULT_COLOR: Record<string, string> = {
  PASS: '#00b42a',
  REJECT: '#f53f3f',
  PENDING: '#86909c',
};

function buildCaseNode(c: any) {
  const uid = `case-${c.testCaseId}`;
  const icon = PRIORITY_ICON[c.priority];
  const resultColor = RESULT_COLOR[c.finalResult] || RESULT_COLOR.PENDING;
  return {
    data: {
      text: escapeHtml(c.caseName || t('qa.review.caseFallback', {id: c.testCaseId})),
      uid,
      expand: true,
      tag: [mkTag(finalResultText(c.finalResult), resultColor)],
      ...(icon ? {icon: [icon]} : {}),
      ...NODE_TYPE_STYLE.case,
    },
    children: [],
  };
}

function buildModuleNodes(modules: any[], casesByModule: Map<number, any[]>): any[] {
  return modules.map((m: any) => ({
    data: {
      text: escapeHtml(m.moduleName),
      uid: `mod-${m.id}`,
      expand: true,
      fillColor: '#e8f3ff',
      color: '#165dff',
      tag: [TYPE_TAG.mod(m.totalCaseCount ?? 0)],
    },
    children: [
      ...buildModuleNodes(m.children || [], casesByModule),
      ...(casesByModule.get(m.id) || []).map(buildCaseNode),
    ],
  }));
}

function buildMindData(rawModules: any[], cases: any[]) {
  detailCache.clear();
  // 深拷贝模块树：totalCaseCount 聚合只写在拷贝上，不污染 props
  const copyModules = (list: any[]): any[] => list.map((m) => ({...m, children: copyModules(m.children || [])}));
  const allModules = copyModules(rawModules);
  const casesByModule = new Map<number, any[]>();
  const noModule: any[] = [];
  cases.forEach((c: any) => {
    if (c.moduleId) {
      if (!casesByModule.has(c.moduleId)) casesByModule.set(c.moduleId, []);
      casesByModule.get(c.moduleId)!.push(c);
    } else {
      noModule.push(c);
    }
  });
  const aggregate = (m: any): number => {
    const own = (casesByModule.get(m.id) || []).length;
    const sub = (m.children || []).reduce((s: number, c: any) => s + aggregate(c), 0);
    m.totalCaseCount = own + sub;
    return m.totalCaseCount;
  };
  allModules.forEach(aggregate);
  // 隐藏没有评审用例的模块分支（含子模块聚合为 0 的）
  const prune = (list: any[]): any[] => list
    .filter((m) => (m.totalCaseCount ?? 0) > 0)
    .map((m) => ({...m, children: prune(m.children || [])}));
  const modules = prune(allModules);

  // 选中模块：根节点 = 该模块，子节点 = 其子模块 + 本模块直接用例
  if (props.selectedModuleId !== undefined) {
    const findModule = (list: any[]): any => {
      for (const m of list) {
        if (Number(m.id) === Number(props.selectedModuleId)) return m;
        const hit = findModule(m.children || []);
        if (hit) return hit;
      }
      return null;
    };
    // 在剪枝前的树上找（选中模块本身即使用例数为 0 也正常显示为根）
    const targetRaw = findModule(allModules);
    if (targetRaw) {
      const prunedTarget = findModule(modules);
      moduleCount.value = (prunedTarget?.children || []).length;
      caseCount.value = targetRaw.totalCaseCount ?? 0;
      return {
        data: {
          text: escapeHtml(targetRaw.moduleName),
          uid: 'root',
          expand: true,
          fillColor: '#e8f3ff',
          color: '#165dff',
          tag: [TYPE_TAG.mod(targetRaw.totalCaseCount ?? 0)],
        },
        children: [
          ...buildModuleNodes(prunedTarget?.children || [], casesByModule),
          ...(casesByModule.get(Number(targetRaw.id)) || []).map(buildCaseNode),
        ],
      };
    }
  }

  // 未选模块（全部用例）：根节点 = 全部用例，子节点 = 模块树 + 未分组
  const root: any = {
    data: {text: t('qa.review.allCases'), uid: 'root', expand: true},
    children: buildModuleNodes(modules, casesByModule),
  };
  if (noModule.length > 0) {
    root.children.push({
      data: {
        text: t('qa.review.ungrouped'),
        uid: 'mod-0',
        expand: true,
        fillColor: '#f7f8fa',
        color: '#4e5969',
        tag: [TYPE_TAG.mod(noModule.length)],
      },
      children: noModule.map(buildCaseNode),
    });
  }
  moduleCount.value = modules.length;
  caseCount.value = cases.length;
  return root;
}

// 根据详情生成 前置条件 + 步骤 子节点（文本为纯内容，类型标识走 tag），只读展示
function buildDetailNodes(detail: any): any[] {
  const nodes: any[] = [];
  const pre = stripHtml(detail.preCondition);
  nodes.push({
    data: {text: escapeHtml(pre), uid: `pre-${detail.id}`, tag: [TYPE_TAG.pre()], ...NODE_TYPE_STYLE.pre},
    children: [],
  });
  normalizeSteps(detail).forEach((s: any, i: number) => {
    const stepNode: any = {
      data: {
        text: escapeHtml(stripHtml(s.step)),
        uid: `step-${detail.id}-${i}`,
        expand: true,
        tag: [TYPE_TAG.step(i + 1)],
        ...NODE_TYPE_STYLE.step,
      },
      children: [],
    };
    stepNode.children.push({
      data: {
        text: escapeHtml(stripHtml(s.expected)),
        uid: `expect-${detail.id}-${i}`,
        expand: true,
        tag: [TYPE_TAG.expect()],
        ...NODE_TYPE_STYLE.expect,
      },
      children: [],
    });
    nodes.push(stepNode);
  });
  return nodes;
}

function normalizeSteps(detail: any): any[] {
  const steps = detail?.testSteps;
  if (Array.isArray(steps)) return steps;
  if (typeof steps === 'string' && steps) {
    try {
      const parsed = JSON.parse(steps);
      if (Array.isArray(parsed)) return parsed;
    } catch { /* 忽略脏数据 */ }
  }
  return [];
}

// 加载用例详情节点（带缓存）
async function loadCaseDetailNodes(uid: string, caseId: number): Promise<any[]> {
  let detailNodes = detailCache.get(uid);
  if (detailNodes) return detailNodes;
  loadingDetail.value = true;
  try {
    const res: any = await getTestCaseDetail(caseId);
    detailNodes = res.code === 200 && res.data ? buildDetailNodes(res.data) : [];
    detailCache.set(uid, detailNodes);
  } catch (e) {
    console.error('[ReviewMindMap] 加载用例详情失败:', e);
    detailNodes = [];
  } finally {
    loadingDetail.value = false;
  }
  return detailNodes;
}

// ===== 节点选中态（只读模式无内置高亮，用官方 setNodeStyles 实现） =====
const CARD_STYLE = {fillColor: '#ffffff', borderColor: '#c9cdd4', borderWidth: 1, borderRadius: 4};
const SELECT_STYLE = {fillColor: '#e8f3ff', borderColor: '#165dff', borderWidth: 2, borderRadius: 4};
let selectedNode: any = null;

// 各类型的默认样式（取消选中时还原用）
function baseStyleOf(uid: string): any {
  if (uid.startsWith('case-')) return CARD_STYLE;
  if (uid === 'mod-0') return {fillColor: '#f7f8fa', borderColor: '', borderWidth: 0};
  if (uid.startsWith('mod-')) return {fillColor: '#e8f3ff', borderColor: '', borderWidth: 0};
  if (uid.startsWith('pre-')) return {fillColor: '#f2f3f5', borderColor: '', borderWidth: 0};
  if (uid.startsWith('step-')) return {fillColor: '#f5e8ff', borderColor: '', borderWidth: 0};
  if (uid.startsWith('expect-')) return {fillColor: '#e8ffea', borderColor: '', borderWidth: 0};
  return null;
}

function applySelection(node: any) {
  if (selectedNode && selectedNode !== node) {
    const restore = baseStyleOf(uidOf(selectedNode));
    if (restore) mind?.renderer?.setNodeStyles?.(selectedNode, restore);
  }
  selectedNode = node || null;
  if (selectedNode) {
    mind?.renderer?.setNodeStyles?.(selectedNode, SELECT_STYLE);
  }
}

function clearSelection() {
  if (selectedNode) {
    const restore = baseStyleOf(uidOf(selectedNode));
    if (restore) mind?.renderer?.setNodeStyles?.(selectedNode, restore);
    selectedNode = null;
  }
}

// ===== 单击节点：选中态 + 操作栏；用例只负责展开详情（折叠走节点 +/- 按钮） =====
async function onNodeClick(node: any) {
  showNodeActions(node);
  const uid = uidOf(node);
  if (!uid || uid === 'root') return;
  applySelection(node);
  if (!uid.startsWith('case-')) return;
  const caseId = Number(uid.replace('case-', ''));

  // 已加载过子节点：不折叠，交给节点上的 +/- 按钮控制
  if ((node.nodeData.children || []).length > 0) return;
  const detailNodes = await loadCaseDetailNodes(uid, caseId);
  if (!detailNodes.length && !detailCache.has(uid)) return;
  node.nodeData.children = detailNodes.map(n => ({...n}));
  node.nodeData.data.expand = true;
  mind?.render();
}

// ===== 节点操作按钮（addCustomContentToNode 叠加层，点击节点显示在节点下方） =====
// 固定三个按钮：评审 / 详情 / 更多（修改评审人、重新评审、取消关联进下拉）
const ACTION_BTN_W = 20;
const ACTION_BTN_H = 20;
const ACTION_GAP = 4;
const actionElMap = new Map<string, HTMLElement>();
let shownActionUid = '';

// 操作图标（内联 SVG，跟随 currentColor）
const ICON_REVIEW = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 8s2.5-4.5 6-4.5S14 8 14 8s-2.5 4.5-6 4.5S2 8 2 8z"/><circle cx="8" cy="8" r="1.8"/></svg>';
const ICON_INFO = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><circle cx="8" cy="8" r="6.5"/><path d="M8 7.2v3.3M8 4.8v.2"/></svg>';
const ICON_MORE = '<svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor"><circle cx="3.5" cy="8" r="1.5"/><circle cx="8" cy="8" r="1.5"/><circle cx="12.5" cy="8" r="1.5"/></svg>';

// 「更多」下拉项（按权限/状态动态计算）
function getMoreMenuItems(record: any): {key: string; text: string; danger?: boolean}[] {
  const items: {key: string; text: string; danger?: boolean}[] = [];
  if (props.isCreator && props.reviewStatus === 'REVIEWING') {
    items.push({key: 'reviewers', text: t('qa.review.editReviewers')});
  }
  if (props.isCreator && props.reviewStatus !== 'CANCELLED' && record?.finalResult && record.finalResult !== 'PENDING') {
    items.push({key: 'reset', text: t('qa.review.reReview')});
  }
  if (props.isCreator && ['REVIEWING', 'COMPLETED'].includes(props.reviewStatus)) {
    items.push({key: 'remove', text: t('qa.review.unlink'), danger: true});
  }
  return items;
}

function getNodeActions(uid: string): {key: string; icon: string; title: string; more?: boolean}[] {
  if (!uid.startsWith('case-')) return [];
  const record = props.cases.find((c) => `case-${c.testCaseId}` === uid);
  const actions: {key: string; icon: string; title: string; more?: boolean}[] = [
    {key: 'review', icon: ICON_REVIEW, title: props.reviewStatus !== 'CANCELLED' ? t('qa.review.op.review') : t('qa.review.op.view')},
    {key: 'detail', icon: ICON_INFO, title: t('qa.review.op.detail')},
  ];
  if (record && getMoreMenuItems(record).length) {
    actions.push({key: 'more', icon: ICON_MORE, title: t('qa.review.more'), more: true});
  }
  return actions;
}

// addCustomContentToNode.create：用例节点生成操作图标按钮 DOM
function createNodeActions(node: any) {
  const uid = uidOf(node);
  const actions = getNodeActions(uid);
  if (!actions.length) return null;

  const el = document.createElement('div');
  el.className = 'clm-actions';
  // 阻止冒泡，避免触发画布拖拽/节点点击（文档：自定义内容内交互需阻止 mousedown 冒泡）
  el.addEventListener('mousedown', (e) => e.stopPropagation());
  actions.forEach((a) => {
    const btn = document.createElement('span');
    btn.className = 'clm-icon-btn';
    btn.innerHTML = a.icon;
    btn.title = a.title;
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const record = props.cases.find((c) => `case-${c.testCaseId}` === uid);
      if (!record) return;
      if (a.more) openMoreMenu(node, record);
      else if (a.key === 'review') handleReviewClick(node, record);
      else if (a.key === 'detail') openDetailDrawer(record);
    });
    el.appendChild(btn);
  });
  actionElMap.set(uid, el);
  // 重渲染后恢复点击展开状态
  if (uid === shownActionUid) el.classList.add('clm-show');
  return {
    el,
    width: actions.length * ACTION_BTN_W + (actions.length - 1) * ACTION_GAP,
    height: ACTION_BTN_H,
  };
}

function hideNodeActions() {
  shownActionUid = '';
  actionElMap.forEach(el => el.classList.remove('clm-show'));
}

function showNodeActions(node: any) {
  const uid = uidOf(node);
  hideNodeActions();
  const el = actionElMap.get(uid);
  if (el) {
    shownActionUid = uid;
    el.classList.add('clm-show');
  }
}

// ===== 我的评审浮动面板 =====
const reviewPanel = ref<{visible: boolean; x: number; y: number; record: any; conclusion: string; comment: string; existing: string; submitting: boolean}>({
  visible: false, x: 0, y: 0, record: null, conclusion: 'PASS', comment: '', existing: '', submitting: false,
});

function hideReviewPanel() {
  reviewPanel.value.visible = false;
}

// 评审按钮：在册评审人 → 节点旁弹出评审面板；非评审人 → 跳转工作台查看
function handleReviewClick(node: any, record: any) {
  const myItem = (record.items || []).find((i: any) => Number(i.reviewerId) === Number(props.myId));
  if (!myItem || props.reviewStatus === 'CANCELLED') {
    emit('review', record);
    return;
  }
  const existing = myItem.conclusion && myItem.conclusion !== 'PENDING' ? myItem.conclusion : '';
  hideNodeActions();
  hideMoreMenu();
  const p = nodeCanvasPos(node);
  // 吸附到节点正下方（覆盖操作栏位置，面板出现后操作栏无需再交互）
  reviewPanel.value = {
    visible: true,
    x: Math.max(8, p.left + p.width / 2 - 160),
    y: p.top + p.height + 2,
    record,
    conclusion: existing || 'PASS',
    comment: existing ? (myItem.comment || '') : '',
    existing,
    submitting: false,
  };
}

async function handleSubmitReview() {
  const panel = reviewPanel.value;
  if (!panel.record) return;
  if (panel.conclusion === 'REJECT' && !panel.comment.trim()) {
    Message.warning(t('qa.review.rejectCommentRequired'));
    return;
  }
  panel.submitting = true;
  try {
    const res: any = await submitTestCaseReview({
      reviewId: props.reviewId,
      testCaseId: Number(panel.record.testCaseId),
      conclusion: panel.conclusion,
      comment: panel.comment,
    });
    if (res.code === 200) {
      Message.success(t('qa.review.submitSuccess'));
      hideReviewPanel();
      emit('refresh');
    } else {
      Message.error(res.msg || t('qa.review.submitFailed'));
    }
  } catch (e) {
    console.error(e);
  } finally {
    panel.submitting = false;
  }
}

// ===== 「更多」下拉 =====
const moreMenu = ref<{visible: boolean; x: number; y: number; items: {key: string; text: string; danger?: boolean}[]; record: any}>({
  visible: false, x: 0, y: 0, items: [], record: null,
});

function hideMoreMenu() {
  moreMenu.value.visible = false;
}

// 官方坐标换算：节点逻辑坐标 × 缩放 + 平移
function nodeCanvasPos(node: any) {
  const {state} = mind.view.getTransformData();
  return {
    left: node.left * state.scale + state.x,
    top: node.top * state.scale + state.y,
    width: node.width * state.scale,
    height: node.height * state.scale,
  };
}

function openMoreMenu(node: any, record: any) {
  const p = nodeCanvasPos(node);
  // 节点下方图标栏之下，水平居中（菜单宽约 140px）
  moreMenu.value = {visible: true, x: p.left + p.width / 2 - 70, y: p.top + p.height + 28, items: getMoreMenuItems(record), record};
}

function handleMoreAction(key: string) {
  const record = moreMenu.value.record;
  hideMoreMenu();
  if (!record) return;
  if (key === 'reviewers') openReviewerModal(record);
  else if (key === 'reset') confirmResetCase(record);
  else if (key === 'remove') emit('remove', record);
}

// ===== 详情抽屉（基本信息 + 评审历史） =====
const detailDrawer = ref<{visible: boolean; tab: string; record: any; detail: any; steps: any[]; history: any[]}>({
  visible: false, tab: 'basic', record: null, detail: null, steps: [], history: [],
});

async function openDetailDrawer(record: any) {
  detailDrawer.value = {visible: true, tab: 'basic', record, detail: null, steps: [], history: []};
  const caseId = Number(record.testCaseId);
  try {
    const res: any = await getTestCaseDetail(caseId);
    if (res.code === 200 && res.data) {
      detailDrawer.value.detail = res.data;
      detailDrawer.value.steps = normalizeSteps(res.data);
    }
  } catch (e) {
    console.error(e);
  }
  try {
    const histRes: any = await getCaseReviewHistory(caseId);
    if (histRes.code === 200) detailDrawer.value.history = histRes.data || [];
  } catch (e) {
    console.error(e);
  }
}

// ===== 修改评审人 =====
const reviewerModal = ref<{visible: boolean; saving: boolean; record: any; ids: number[]}>({
  visible: false, saving: false, record: null, ids: [],
});

function openReviewerModal(record: any) {
  reviewerModal.value = {
    visible: true,
    saving: false,
    record,
    ids: (record.items || []).map((i: any) => Number(i.reviewerId)),
  };
}

async function handleSaveReviewers() {
  const record = reviewerModal.value.record;
  if (!record) return;
  reviewerModal.value.saving = true;
  try {
    const res: any = await updateReviewCaseReviewers(props.reviewId, Number(record.testCaseId), reviewerModal.value.ids);
    if (res.code === 200) {
      Message.success(t('qa.review.reviewersAdjusted'));
      reviewerModal.value.visible = false;
      emit('refresh');
    } else {
      Message.error(res.msg || t('qa.review.adjustFailed'));
    }
  } catch (e) {
    console.error(e);
  } finally {
    reviewerModal.value.saving = false;
  }
}

// ===== 重新评审 =====
function confirmResetCase(record: any) {
  Modal.confirm({
    title: t('qa.review.reReview'),
    content: t('qa.review.resetContent', {name: record.caseName}),
    onOk: async () => {
      try {
        const res: any = await resetReviewCase(props.reviewId, Number(record.testCaseId));
        if (res.code === 200) {
          Message.success(t('qa.review.resetSuccess'));
          emit('refresh');
        } else {
          Message.error(res.msg || t('qa.review.operationFailed'));
        }
      } catch (e) {
        console.error(e);
      }
    },
  });
}

const priorityColor = (p: string) => ({P0: 'red', P1: 'orange', P2: 'blue'} as Record<string, string>)[p] || 'gray';
const finalResultColor = (r: string) => ({PASS: 'green', REJECT: 'red', PENDING: 'gray'} as Record<string, string>)[r] || 'gray';
const finalResultText = (r: string) => ({PASS: t('qa.review.resultPass'), REJECT: t('qa.review.resultReject'), PENDING: t('qa.review.resultPending')} as Record<string, string>)[r] || t('qa.review.resultPending');

// ===== 数据重建（保持展开/折叠状态） =====
async function rebuild() {
  if (!mind) return;
  // 重建前记录：已展开详情的用例 + 被折叠的节点
  const expandedCaseUids: string[] = [];
  const collapsedUids = new Set<string>();
  const collect = (n: any) => {
    if (!n) return;
    const uid = uidOf(n);
    if (uid.startsWith('case-') && childrenOf(n).length > 0) expandedCaseUids.push(uid);
    const expand = n?.nodeData?.data?.expand ?? n?.data?.expand;
    if (uid && expand === false) collapsedUids.add(uid);
    childrenOf(n).forEach(collect);
  };
  collect(mind.renderer?.root || mind.renderer?.renderTree);

  hideNodeActions();
  hideMoreMenu();
  hideReviewPanel();
  selectedNode = null;
  const data = buildMindData(props.moduleTree, props.cases);
  // 恢复折叠状态
  const applyCollapse = (n: any) => {
    if (!n) return;
    if (collapsedUids.has(n.data?.uid)) n.data.expand = false;
    (n.children || []).forEach(applyCollapse);
  };
  applyCollapse(data);
  // 恢复用例详情展开：直接在新数据树上挂子节点（重新拉详情保证最新），不依赖节点实例
  if (expandedCaseUids.length) {
    const findDataNode = (n: any, uid: string): any => {
      if (!n) return null;
      if (n.data?.uid === uid) return n;
      for (const c of (n.children || [])) {
        const hit = findDataNode(c, uid);
        if (hit) return hit;
      }
      return null;
    };
    for (const uid of expandedCaseUids) {
      const target = findDataNode(data, uid);
      if (!target) continue;
      const caseId = Number(uid.replace('case-', ''));
      const detailNodes = await loadCaseDetailNodes(uid, caseId);
      target.children = detailNodes.map(n => ({...n}));
      target.data.expand = !collapsedUids.has(uid);
    }
  }
  mind.setData(data);
}

// ===== 初始化（与用例库脑图同款：等容器尺寸就绪再创建） =====
async function initMind(retries = 0) {
  if (!mapRef.value) return;
  const {width, height} = mapRef.value.getBoundingClientRect();
  if (width === 0 || height === 0) {
    if (retries < 60) requestAnimationFrame(() => initMind(retries + 1));
    else console.error('[ReviewMindMap] 容器尺寸为 0，放弃初始化');
    return;
  }
  if (mind) return;

  mapRef.value.innerHTML = '';
  try {
    mind = new MindMap({
      el: mapRef.value,
      data: buildMindData(props.moduleTree, props.cases),
      readonly: true, // 评审脑图只读：无编辑/拖拽/增删
      layout: 'logicalStructure',
      theme: 'default',
      expandBtnSize: 20,
      nodeTextUseRichText: true,
      // 节点叠加操作按钮（官方 addCustomContentToNode，v0.13.1+）
      addCustomContentToNode: {
        create: (node: any) => createNodeActions(node),
        // 定位到节点下方水平居中（节点间距 marginY 已留足空间，不遮挡下一个节点）
        handle: ({content, element, node}: any) => {
          element.x(node.width / 2 - content.width / 2).y(node.height + 4);
        },
      },
    });

    mind.on('node_click', (node: any) => onNodeClick(node));
    mind.on('svg_mousedown', () => { hideNodeActions(); clearSelection(); hideMoreMenu(); hideReviewPanel(); });
    // 加大同级节点垂直间距，容纳节点下方的操作栏（默认 node.marginY=0 会遮挡下一个节点）
    mind.setThemeConfig({node: {marginY: 34}});
    resizeObserver = new ResizeObserver(() => {
      if (!mapRef.value || !mind) return;
      const {width: w, height: h} = mapRef.value.getBoundingClientRect();
      if (w === 0 || h === 0) return;
      try { mind.resize?.(); } catch (e) { console.warn('[ReviewMindMap] resize 异常:', e); }
    });
    resizeObserver.observe(mapRef.value);
  } catch (e) {
    console.error('[ReviewMindMap] 初始化失败:', e);
    mind = null;
    mapRef.value.innerHTML = '';
  }
}

onMounted(() => {
  nextTick(() => initMind());
  // 点击菜单外任意处关闭「更多」下拉（菜单自身已 @mousedown.stop）
  document.addEventListener('mousedown', hideMoreMenu);
});

// 评审数据/模块树/选中模块变化 → 重建（保持展开状态）
watch(() => [props.cases, props.moduleTree, props.selectedModuleId], () => rebuild(), {deep: true});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  document.removeEventListener('mousedown', hideMoreMenu);
  if (mind) {
    try {
      mind.destroy?.();
    } catch (e) {
      console.error('[ReviewMindMap] destroy 异常:', e);
      if (mapRef.value) mapRef.value.innerHTML = '';
    }
    mind = null;
  }
});
</script>

<style>
.rm-mindmap-canvas * {
  margin: 0;
  padding: 0;
}

/* 节点叠加操作图标（真实 DOM 渲染在 foreignObject 里，CSS 直接生效；点击节点时显示在节点下方） */
.clm-actions {
  display: none;
  gap: 4px;
  align-items: center;
}
.clm-actions.clm-show {
  display: flex;
}
.clm-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  color: #165dff;
  background: #ffffff;
  border: 1px solid #bed0ff;
  border-radius: 4px;
  cursor: pointer;
  user-select: none;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
}
.clm-icon-btn:hover {
  background: #e8f3ff;
}
.clm-icon-danger {
  color: #f53f3f;
  border-color: #fbaca3;
}
.clm-icon-danger:hover {
  background: #ffece8;
}
/* 「更多」下拉菜单 */
.clm-add-menu {
  position: absolute;
  z-index: 100;
  min-width: 130px;
  background: var(--color-bg-2, #ffffff);
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
  padding: 4px;
}
.clm-add-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 7px 12px;
  font-size: 13px;
  color: var(--color-text-1, #1d2129);
  border-radius: 4px;
  cursor: pointer;
  white-space: nowrap;
}
.clm-add-item:hover {
  background: var(--color-fill-2, #f2f3f5);
}
.clm-add-item-danger {
  color: #f53f3f;
}
</style>

<style scoped>
.rm-mindmap-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
}

/* 我的评审浮动面板 */
.rm-review-panel {
  position: absolute;
  z-index: 120;
  width: 320px;
  background: var(--color-bg-2, #fff);
  border: 1px solid #e5e6eb;
  border-radius: 8px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.rm-review-title {
  font-weight: 600;
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.rm-review-case {
  font-weight: 400;
  color: var(--color-text-3);
  font-size: 12px;
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rm-review-close {
  margin-left: auto;
  cursor: pointer;
  color: var(--color-text-3);
  font-size: 16px;
  line-height: 1;
  padding: 0 2px;
}
.rm-review-close:hover {
  color: var(--color-text-1);
}
.rm-review-actions {
  display: flex;
  justify-content: flex-end;
}

.rm-mindmap-canvas {
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}
.rm-mindmap-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  border-top: 1px solid #e5e6eb;
  flex-shrink: 0;
}
.rm-mindmap-toolbar .hint {
  font-size: 13px;
  color: #86909c;
}
.rm-mindmap-toolbar .loading {
  color: rgb(var(--primary-6));
}

/* 详情抽屉 */
.drawer-section-title {
  font-weight: 600;
  margin: 14px 0 10px;
  padding-left: 8px;
  border-left: 3px solid rgb(var(--primary-6));
  line-height: 1.2;
}
.pre-condition-view { margin: 0 0 8px; }
.pre-condition-empty { margin: 0 0 8px; color: var(--color-text-3); font-size: 13px; }
.vote-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--color-border-2);
}
.vote-item:last-child { border-bottom: none; }
.vote-avatar { flex-shrink: 0; }
.vote-main { flex: 1; min-width: 0; }
.vote-name-line { display: flex; align-items: baseline; gap: 8px; }
.vote-name { font-size: 13px; font-weight: 600; }
.vote-review { font-size: 12px; color: var(--color-text-3); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.vote-comment { font-size: 13px; color: var(--color-text-2); white-space: pre-wrap; margin-top: 2px; }
.vote-time { font-size: 12px; color: var(--color-text-3); margin-top: 4px; }
.vote-result { flex-shrink: 0; }
</style>
