<template>
  <div class="lib-mindmap-wrapper">
    <div ref="mapRef" class="lib-mindmap-canvas"></div>
    <!-- 节点操作图标的二级文字下拉（放组件层渲染，避免 foreignObject 裁剪） -->
    <div
      v-if="addMenu.visible"
      class="clm-add-menu"
      :style="{ left: addMenu.x + 'px', top: addMenu.y + 'px' }"
      @mousedown.stop
    >
      <div v-for="item in addMenu.items" :key="item.key" class="clm-add-item" @click="handleAddMenuAction(item.key)">{{ item.text }}</div>
    </div>
    <!-- 画布右上角浮动工具栏 -->
    <div class="lib-mindmap-toolbar">
      <a-button type="primary" size="small" :disabled="!dirty" :loading="saving" @click="handleSave">保存</a-button>
      <a-button size="small" :disabled="!dirty" @click="handleReset">放弃修改</a-button>
      <span v-if="dirty" class="hint dirty-hint">● 有未保存的修改</span>
      <span v-if="loadingDetail" class="hint loading">用例详情加载中…</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, onBeforeUnmount, nextTick, watch} from 'vue';
import {Message, Modal} from '@arco-design/web-vue';
import MindMap from 'simple-mind-map/dist/simpleMindMap.esm.js';
import 'simple-mind-map/dist/simpleMindMap.esm.css';
import {
  getQaModuleList, getTestCaseList, getTestCaseDetail,
  updateTestCase, updateQaModule, saveQaModule, saveTestCase,
  deleteQaModule, deleteTestCase, batchMoveTestCase, sortTestCase,
} from '@/api/MyApi/qa';

const props = defineProps<{
  projectId: number;
  selectedModuleId?: number; // 左侧选中的模块：选中则根节点为该模块，否则为「全部用例」
}>();

const emit = defineEmits<{
  openDetail: [caseId: number];
}>();

const mapRef = ref<HTMLElement>();
let mind: any = null;
let origExec: any = null; // 原始 command.exec（受控放行结构命令）
let isInternalOp = false; // 内部插入/删除节点期间放行结构命令
let resizeObserver: ResizeObserver | null = null;
const loadingDetail = ref(false);
const moduleCount = ref(0);
const caseCount = ref(0);
const detailCache = new Map<string, any[]>();
// uid → 保存基线的纯文本（仅重建时重置，展开时只增不改）
const textSnapshot = new Map<string, string>();
const caseDetailMap = new Map<number, any>();

// ===== 未保存的变更（手动保存时统一提交） =====
const dirty = ref(false);
const saving = ref(false);
const touchedCases = new Set<number>();          // 详情/名称有改动的用例
const modRenames = new Map<number, string>();    // 模块改名
const caseMoves = new Map<number, string>();     // 用例移动：caseId → 目标模块 uid（mod-<id>/mod-0）
const modMoves = new Map<number, string>();      // 模块移动：modId → 目标父级 uid（mod-<id>/root/new-mod-*）
const modDeletes = new Set<number>();
const caseDeletes = new Set<number>();
const casePriorityChanges = new Map<number, string>(); // 用例等级调整
const tempCasePriorities = new Map<string, string>();  // 未保存临时用例的等级：new-case uid → priority
const casePreMap = new Map<number, boolean>();         // 用例是否有前置条件（未展开时判断「子级」菜单显隐用）

function clearPending() {
  touchedCases.clear();
  modRenames.clear();
  caseMoves.clear();
  modMoves.clear();
  modDeletes.clear();
  caseDeletes.clear();
  casePriorityChanges.clear();
  tempCasePriorities.clear();
  dirty.value = false;
}

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
const textOf = (n: any): string => n?.nodeData?.data?.text ?? n?.data?.text ?? '';
const childrenOf = (n: any): any[] => n?.children || n?.nodeData?.children || [];

// 节点类型视觉规范：tag（类型标识，SVG 原生渲染在文本后）+ 底色/文字色
const mkTag = (text: string, fill: string) => ({text, style: {fill, radius: 3, fontSize: 12, height: 20, paddingX: 8}});
const TYPE_TAG = {
  mod: (count: number) => mkTag(`模块 ${count}`, '#165dff'),
  case: () => mkTag('用例', '#0fc6c2'),
  pre: () => mkTag('前置', '#86909c'),
  step: (index?: number) => mkTag(index ? `步骤 ${index}` : '步骤', '#722ed1'),
  expect: () => mkTag('预期', '#00b42a'),
};
// 各类型节点底色/文字色（data 里的 fillColor 等渲染为节点形状）
const NODE_TYPE_STYLE = {
  case: {fillColor: '#ffffff', borderColor: '#c9cdd4', borderWidth: 1, borderRadius: 4},
  pre: {fillColor: '#f2f3f5', color: '#4e5969'},
  step: {fillColor: '#f5e8ff', color: '#722ed1'},
  expect: {fillColor: '#e8ffea', color: '#00b42a'},
};

function buildCaseNode(c: any) {
  const uid = `case-${c.id}`;
  const icon = PRIORITY_ICON[c.priority];
  return {
    data: {
      text: escapeHtml(c.caseName || `用例 ${c.id}`),
      uid,
      expand: true,
      tag: [TYPE_TAG.case()],
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

function buildMindData(modules: any[], cases: any[]) {
  detailCache.clear();
  caseDetailMap.clear();
  casePreMap.clear();
  cases.forEach((c: any) => casePreMap.set(c.id, !!stripHtml(c.preCondition || '')));
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
  modules.forEach(aggregate);

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
    const target = findModule(modules);
    if (target) {
      moduleCount.value = (target.children || []).length;
      caseCount.value = target.totalCaseCount ?? 0;
      return {
        data: {
          text: escapeHtml(target.moduleName),
          uid: 'root',
          expand: true,
          fillColor: '#e8f3ff',
          color: '#165dff',
          tag: [TYPE_TAG.mod(target.totalCaseCount ?? 0)],
        },
        children: [
          ...buildModuleNodes(target.children || [], casesByModule),
          ...(casesByModule.get(Number(target.id)) || []).map(buildCaseNode),
        ],
      };
    }
  }

  const root: any = {
    data: {text: '全部用例', uid: 'root', expand: true},
    children: buildModuleNodes(modules, casesByModule),
  };
  if (noModule.length > 0) {
    root.children.push({
      data: {
        text: '未分组',
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

// 根据详情生成 前置条件 + 步骤 子节点（文本为纯内容，类型标识走 tag）
// 前置条件节点无条件渲染（内容为空也显示，避免"空内容节点保存后消失"）
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
    // 步骤节点默认都带一个预期结果子节点（无内容时只显示「预期」标签，双击可补内容）
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

// ===== 树工具 =====
function findNodeByUid(uid: string): any {
  let found: any = null;
  const walk = (n: any) => {
    if (!n || found) return;
    if (uidOf(n) === uid) { found = n; return; }
    childrenOf(n).forEach(walk);
  };
  walk(mind?.renderer?.root || mind?.renderer?.renderTree);
  return found;
}

// 快照只增不改（rebuild 时才整体重置），保证重命名 diff 的基线不被后续操作冲掉
function ensureSnapshot() {
  const walk = (n: any) => {
    if (!n) return;
    const uid = uidOf(n);
    if (uid && !textSnapshot.has(uid)) textSnapshot.set(uid, stripHtml(textOf(n)));
    childrenOf(n).forEach(walk);
  };
  walk(mind?.renderer?.root || mind?.renderer?.renderTree);
}

function resetSnapshot() {
  textSnapshot.clear();
  const walk = (n: any) => {
    if (!n) return;
    const uid = uidOf(n);
    if (uid) textSnapshot.set(uid, stripHtml(textOf(n)));
    childrenOf(n).forEach(walk);
  };
  walk(mind?.renderer?.root || mind?.renderer?.renderTree);
}

// 加载用例详情节点（带缓存）
async function loadCaseDetailNodes(uid: string, caseId: number): Promise<any[]> {
  let detailNodes = detailCache.get(uid);
  if (detailNodes) return detailNodes;
  loadingDetail.value = true;
  try {
    const res: any = await getTestCaseDetail(caseId);
    if (res.code === 200 && res.data) {
      caseDetailMap.set(caseId, res.data);
      detailNodes = buildDetailNodes(res.data);
      detailCache.set(uid, detailNodes);
    } else {
      detailNodes = [];
    }
  } catch (e) {
    console.error('[CaseLibMindMap] 加载用例详情失败:', e);
    detailNodes = [];
  } finally {
    loadingDetail.value = false;
  }
  return detailNodes;
}

// ===== 单击节点：用例只负责展开详情（已展开则不动）；折叠只能通过节点上的展开/收起按钮 =====
async function onNodeClick(node: any) {
  showNodeActions(node);
  const uid: string = node?.nodeData?.data?.uid || '';
  if (!uid.startsWith('case-')) return;
  const caseId = Number(uid.replace('case-', ''));

  // 已加载过子节点：不折叠，交给节点上的 +/- 按钮控制
  if ((node.nodeData.children || []).length > 0) return;
  const detailNodes = await loadCaseDetailNodes(uid, caseId);
  if (!detailNodes.length && !detailCache.has(uid)) return;
  node.nodeData.children = detailNodes.map(n => ({...n}));
  node.nodeData.data.expand = true;
  mind?.render();
  ensureSnapshot();
}

// ===== 节点操作按钮（addCustomContentToNode 叠加层，点击节点显示在节点下方） =====
// 图标按钮固定 20x20，gap 4
const ACTION_BTN_W = 20;
const ACTION_BTN_H = 20;
const ACTION_GAP = 4;
// uid → 操作栏 DOM（create 时登记，点击切换显隐）
const actionElMap = new Map<string, HTMLElement>();
let shownActionUid = '';

// 操作图标（内联 SVG，跟随 currentColor）
const ICON_PLUS = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M8 3v10M3 8h10"/></svg>';
const ICON_TRASH = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 4h12M5.5 4V2.5h5V4M3.5 4l.8 10h7.4l.8-10"/></svg>';
const ICON_SIBLING = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M3 3.5h10M3 8h10M8 11v3.5M6 13.5l2 2 2-2"/></svg>';
const ICON_CHILD = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M3 3.5h7M7 8h6M7 12.5h6M3 3.5v9h2.5"/></svg>';
const ICON_FLAG = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M3.5 14V2.5M3.5 3h9l-2 3 2 3h-9"/></svg>';
const ICON_DETAIL = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 8s2.5-4.5 6-4.5S14 8 14 8s-2.5 4.5-6 4.5S2 8 2 8z"/><circle cx="8" cy="8" r="1.8"/></svg>';

// 「+」类图标的二级下拉菜单（文字项）
const addMenu = ref<{ visible: boolean; x: number; y: number; items: { key: string; text: string }[]; node: any }>({visible: false, x: 0, y: 0, items: [], node: null});

function hideAddMenu() {
  addMenu.value.visible = false;
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

function openAddMenu(node: any, items: { key: string; text: string }[]) {
  const p = nodeCanvasPos(node);
  // 节点下方图标栏之下，水平居中（菜单宽约 140px）
  addMenu.value = {visible: true, x: p.left + p.width / 2 - 70, y: p.top + p.height + 28, items, node};
}

function handleAddMenuAction(key: string) {
  const node = addMenu.value.node;
  hideAddMenu();
  if (!node) return;
  switch (key) {
    case 'addMod': insertEditableChild(node, `new-mod-${Date.now()}`); break;
    case 'addCase': insertCaseTempNode(node, false); break;
    case 'addSiblingMod': insertEditableSibling(node, `new-mod-${Date.now()}`); break;
    case 'addSiblingCase': insertCaseTempNode(node, true); break;
    case 'addPre': handleAddPrecondition(node); break;
    case 'addStep': handleAddStep(node); break;
    case 'P0': case 'P1': case 'P2': setCasePriority(node, key); break;
  }
}

// 用例等级下拉项
const PRIORITY_MENU_ITEMS = [
  {key: 'P0', text: 'P0（最高）'},
  {key: 'P1', text: 'P1（高）'},
  {key: 'P2', text: 'P2（普通）'},
];

function getNodeActions(uid: string, node?: any): { key: string; icon: string; danger?: boolean; menuItems?: { key: string; text: string }[] }[] {
  // 根节点（全部用例/选中的模块）：支持新建子模块/用例，不可删除
  if (uid === 'root') {
    return [
      {key: 'add', icon: ICON_PLUS, menuItems: [
        {key: 'addMod', text: '新建子模块'},
        {key: 'addCase', text: '新建用例'},
      ]},
    ];
  }
  // 用例子节点中是否已有前置条件：
  // 已展开（children 已加载）看树上；未展开看列表数据里的 preCondition
  const hasPreChild = (n: any) => {
    const children = n?.nodeData?.children || [];
    if (children.length > 0) {
      return children.some((c: any) => String(c.data?.uid || '').startsWith('pre-') || String(c.data?.uid || '').startsWith('new-pre-'));
    }
    const uid: string = n?.nodeData?.data?.uid || '';
    if (uid.startsWith('case-')) return casePreMap.get(Number(uid.replace('case-', ''))) === true;
    return false;
  };
  // 子级菜单：前置条件已存在时隐藏该入口
  const childMenuItems = (n: any) => [
    {key: 'addStep', text: '步骤描述'},
    ...(hasPreChild(n) ? [] : [{key: 'addPre', text: '前置条件'}]),
  ];
  if ((uid.startsWith('mod-') && uid !== 'mod-0') || uid.startsWith('new-mod-')) {
    return [
      {key: 'add', icon: ICON_PLUS, menuItems: [
        {key: 'addMod', text: '新建子模块'},
        {key: 'addCase', text: '新建用例'},
      ]},
      {key: 'del', icon: ICON_TRASH, danger: true},
    ];
  }
  if (uid.startsWith('case-')) {
    return [
      {key: 'sibling', icon: ICON_SIBLING, menuItems: [
        {key: 'addSiblingMod', text: '同级模块'},
        {key: 'addSiblingCase', text: '同级用例'},
      ]},
      {key: 'child', icon: ICON_CHILD, menuItems: childMenuItems(node)},
      {key: 'priority', icon: ICON_FLAG, menuItems: PRIORITY_MENU_ITEMS},
      {key: 'detail', icon: ICON_DETAIL},
      {key: 'del', icon: ICON_TRASH, danger: true},
    ];
  }
  // 未保存的临时用例：无详情入口（还没有真实 id），等级设置走临时记账
  if (uid.startsWith('new-case-')) {
    return [
      {key: 'sibling', icon: ICON_SIBLING, menuItems: [
        {key: 'addSiblingMod', text: '同级模块'},
        {key: 'addSiblingCase', text: '同级用例'},
      ]},
      {key: 'child', icon: ICON_CHILD, menuItems: childMenuItems(node)},
      {key: 'priority', icon: ICON_FLAG, menuItems: PRIORITY_MENU_ITEMS},
      {key: 'del', icon: ICON_TRASH, danger: true},
    ];
  }
  if (uid.startsWith('step-') || uid.startsWith('new-step-')) {
    return [
      {key: 'siblingStep', icon: ICON_SIBLING},
      {key: 'del', icon: ICON_TRASH, danger: true},
    ];
  }
  if (uid.startsWith('pre-') || uid.startsWith('new-')) {
    return [{key: 'del', icon: ICON_TRASH, danger: true}];
  }
  return [];
}

// addCustomContentToNode.create：按节点类型生成操作图标按钮 DOM
function createNodeActions(node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  const actions = getNodeActions(uid, node);
  if (!actions.length) {
    // simple-mind-map 要求 create 始终返回有效对象，null 会导致内部解构崩溃
    const empty = document.createElement('span');
    return {el: empty, width: 0, height: 0};
  }

  const el = document.createElement('div');
  el.className = 'clm-actions';
  // 阻止冒泡，避免触发画布拖拽/节点点击（文档：自定义内容内交互需阻止 mousedown 冒泡）
  el.addEventListener('mousedown', (e) => e.stopPropagation());
  actions.forEach((a) => {
    const btn = document.createElement('span');
    btn.className = `clm-icon-btn${a.danger ? ' clm-icon-danger' : ''}`;
    btn.innerHTML = a.icon;
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (a.menuItems) openAddMenu(node, a.menuItems);
      else onNodeAction(a.key, node);
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
  const uid: string = node?.nodeData?.data?.uid || '';
  hideNodeActions();
  const el = actionElMap.get(uid);
  if (el) {
    shownActionUid = uid;
    el.classList.add('clm-show');
  }
}

function onNodeAction(key: string, node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  if (key === 'del') handleDeleteNode(node, uid);
  else if (key === 'siblingStep') handleAddSiblingStep(node);
  else if (key === 'detail') {
    const caseId = Number(uid.replace('case-', ''));
    if (caseId) emit('openDetail', caseId);
  }
}

// 添加同级步骤：插入到当前步骤之后，自动带预期结果子节点并进入编辑
function handleAddSiblingStep(node: any) {
  const parentUid: string = node?.parent?.nodeData?.data?.uid || '';
  insertEditableSibling(node, `new-step-${Date.now()}`, true);
  // 已保存用例：标记改动，保存时按树顺序重建 test_steps
  if (parentUid.startsWith('case-')) {
    touchedCases.add(Number(parentUid.replace('case-', '')));
  }
}

// 设置用例等级：更新节点图标 + 记账，保存时随用例补丁提交
function setCasePriority(node: any, priority: string) {
  const uid: string = node?.nodeData?.data?.uid || '';
  // 未保存的临时用例：记入临时等级表，创建时随 saveTestCase 提交
  if (uid.startsWith('new-case-')) {
    tempCasePriorities.set(uid, priority);
    dirty.value = true;
    mind?.renderer?.setNodeIcon?.(node, [PRIORITY_ICON[priority]]);
    return;
  }
  const caseId = Number(uid.replace('case-', ''));
  if (!caseId) return;
  casePriorityChanges.set(caseId, priority);
  touchedCases.add(caseId);
  dirty.value = true;
  // 官方 API 更新节点图标
  mind?.renderer?.setNodeIcon?.(node, [PRIORITY_ICON[priority]]);
}

// 插入同级临时节点并进入编辑（INSERT_NODE = 插入到当前节点之后；openEdit=true 官方自动进入编辑态）
async function insertEditableSibling(node: any, uid: string, withExpectChild = false) {
  if (!mind || !origExec || !node) return;
  let typeData: any = {};
  if (uid.startsWith('new-mod-')) typeData = {tag: [TYPE_TAG.mod(0)], fillColor: '#e8f3ff', color: '#165dff'};
  else if (uid.startsWith('new-step-')) typeData = {tag: [TYPE_TAG.step()], ...NODE_TYPE_STYLE.step};
  const children = withExpectChild
    ? [{data: {text: '', uid: `new-expect-${Date.now()}`, tag: [TYPE_TAG.expect()], ...NODE_TYPE_STYLE.expect}, children: []}]
    : [];
  isInternalOp = true;
  try {
    origExec('INSERT_NODE', true, [node], {text: '', uid, ...typeData}, children);
  } finally {
    isInternalOp = false;
  }
  dirty.value = true;
}

// 新建用例临时节点：默认文本「用例」，并一次性携带 前置条件 + 步骤（带预期结果）子节点
// 官方 appointChildren 参数随插入命令一次创建整棵子树，不自动进入编辑态（双击可编辑）
function insertCaseTempNode(targetNode: any, asSibling: boolean) {
  if (!mind || !origExec || !targetNode) return;
  const ts = Date.now();
  const children = [
    {data: {text: '前置条件', uid: `new-pre-${ts}p`, tag: [TYPE_TAG.pre()], ...NODE_TYPE_STYLE.pre}, children: []},
    {
      data: {text: '步骤描述', uid: `new-step-${ts}s`, tag: [TYPE_TAG.step()], ...NODE_TYPE_STYLE.step},
      children: [{data: {text: '预期结果', uid: `new-expect-${ts}e`, tag: [TYPE_TAG.expect()], ...NODE_TYPE_STYLE.expect}, children: []}],
    },
  ];
  isInternalOp = true;
  try {
    if (asSibling) {
      origExec('INSERT_NODE', false, [targetNode], {text: '用例', uid: `new-case-${ts}`, tag: [TYPE_TAG.case()], ...NODE_TYPE_STYLE.case}, children);
    } else {
      origExec('INSERT_CHILD_NODE', false, [targetNode], {text: '用例', uid: `new-case-${ts}`, tag: [TYPE_TAG.case()], ...NODE_TYPE_STYLE.case}, children);
    }
  } finally {
    isInternalOp = false;
  }
  dirty.value = true;
}

// ===== 新建：插入临时节点并由官方 openEdit 进入编辑（保存时才真正调接口） =====
function insertEditableChild(parentNode: any, uid: string, withExpectChild = false) {
  if (!mind || !origExec || !parentNode) return;
  // 按节点类型挂对应 tag + 底色；步骤节点同时带一个「预期」子节点
  let typeData: any = {};
  if (uid.startsWith('new-pre-')) typeData = {tag: [TYPE_TAG.pre()], ...NODE_TYPE_STYLE.pre};
  else if (uid.startsWith('new-step-')) typeData = {tag: [TYPE_TAG.step()], ...NODE_TYPE_STYLE.step};
  else if (uid.startsWith('new-mod-')) typeData = {tag: [TYPE_TAG.mod(0)], fillColor: '#e8f3ff', color: '#165dff'};
  const children = withExpectChild
    ? [{data: {text: '', uid: `new-expect-${Date.now()}`, tag: [TYPE_TAG.expect()], ...NODE_TYPE_STYLE.expect}, children: []}]
    : [];
  isInternalOp = true;
  try {
    origExec('INSERT_CHILD_NODE', true, [parentNode], {text: '', uid, ...typeData}, children);
  } finally {
    isInternalOp = false;
  }
  dirty.value = true;
}

async function ensureCaseChildrenLoaded(node: any) {
  if (!node) return;
  if (!(node.nodeData.children || []).length) {
    await onNodeClick(node);
  }
}

// 添加前置条件：唯一性校验
async function handleAddPrecondition(node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  // 未保存的临时用例：只看树上有没有，直接插临时节点
  if (uid.startsWith('new-case-')) {
    const hasPre = (node.nodeData.children || []).some((c: any) => String(c.data?.uid || '').startsWith('new-pre-'));
    if (hasPre) {
      Message.warning('该用例已有前置条件，双击节点可直接编辑');
      return;
    }
    insertEditableChild(node, `new-pre-${Date.now()}`);
    return;
  }
  const caseId = Number(uid.replace('case-', ''));
  if (!caseId) return;
  // 已展开时直接看树上有没有前置条件节点；未展开时查详情
  const children = node.nodeData.children || [];
  const hasPreOnTree = children.some((c: any) => String(c.data?.uid || '').startsWith('pre-') || String(c.data?.uid || '').startsWith('new-pre-'));
  if (hasPreOnTree) {
    Message.warning('该用例已有前置条件，双击节点可直接编辑');
    return;
  }
  if (!children.length) {
    try {
      const res: any = await getTestCaseDetail(caseId);
      if (res.code === 200 && res.data) {
        caseDetailMap.set(caseId, res.data);
        if (stripHtml(res.data.preCondition)) {
          Message.warning('该用例已有前置条件，双击节点可直接编辑');
          return;
        }
      }
    } catch (e) {
      console.error(e);
    }
  }
  touchedCases.add(caseId);
  casePreMap.set(caseId, true);
  await ensureCaseChildrenLoaded(node);
  await insertEditableChild(node, `new-pre-${caseId}`);
}

// 添加步骤：自动带预期结果子节点
async function handleAddStep(node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  // 未保存的临时用例：直接插临时节点
  if (uid.startsWith('new-case-')) {
    insertEditableChild(node, `new-step-${Date.now()}`, true);
    return;
  }
  const caseId = Number(uid.replace('case-', ''));
  if (!caseId) return;
  touchedCases.add(caseId);
  await ensureCaseChildrenLoaded(node);
  await insertEditableChild(node, `new-step-${caseId}`, true);
}

// ===== 删除（本地移除，保存时统一提交） =====
function removeNodeLocal(node: any) {
  if (!mind || !origExec || !node) return;
  isInternalOp = true;
  try {
    origExec('REMOVE_NODE', [node]);
  } finally {
    isInternalOp = false;
  }
  dirty.value = true;
}

function handleDeleteNode(node: any, uid: string) {
  if (uid.startsWith('mod-')) {
    // 仅空模块可删除（含未保存的临时子节点也算非空）
    if ((node.nodeData.children || []).length > 0) {
      Message.warning('模块下存在子模块或用例，不能删除');
      return;
    }
    Modal.confirm({
      title: '删除模块',
      content: '删除后需点击「保存」生效，确认删除该模块？',
      onOk: () => {
        const id = Number(uid.replace('mod-', ''));
        if (id) modDeletes.add(id);
        removeNodeLocal(node);
      },
    });
    return;
  }
  if (uid.startsWith('case-')) {
    Modal.confirm({
      title: '删除用例',
      content: '删除后需点击「保存」生效，确认删除该用例？',
      onOk: () => {
        const id = Number(uid.replace('case-', ''));
        if (id) caseDeletes.add(id);
        touchedCases.delete(id);
        removeNodeLocal(node);
      },
    });
    return;
  }
  if (uid.startsWith('pre-') || uid.startsWith('step-')) {
    const caseId = Number(uid.split('-')[1]);
    if (caseId) {
      touchedCases.add(caseId);
      // 删除前置条件后，「子级」菜单的「前置条件」入口恢复显示
      if (uid.startsWith('pre-')) casePreMap.set(caseId, false);
    }
    removeNodeLocal(node);
    return;
  }
  // 未保存的临时节点：直接移除
  if (uid.startsWith('new-')) {
    // 已保存用例上新增的临时前置条件被删除：恢复菜单入口
    if (uid.startsWith('new-pre-')) {
      const caseId = Number(uid.split('-')[2] || uid.split('-')[1]);
      if (casePreMap.has(caseId)) casePreMap.set(caseId, false);
    }
    removeNodeLocal(node);
  }
}

// ===== 文本编辑结束（hide_text_edit）：文本改动记账的唯一入口 =====
// 注意：富文本编辑结束不一定触发 data_change，已保存节点的改动必须在这里记账
function onTextEditEnd(node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  if (!uid) return;
  // 未保存的临时节点：空内容清理（新建流程的取消语义）
  if (uid.startsWith('new-')) {
    const text = stripHtml(node.nodeData.data.text);
    if (!text) {
      removeNodeLocal(node);
      dirty.value = true;
    }
    return;
  }
  // 已保存节点：与基线比对，变了才记账
  const text = stripHtml(node.nodeData.data.text);
  if (textSnapshot.get(uid) === text) return;
  if (uid.startsWith('mod-') && uid !== 'mod-0') {
    if (!text) {
      // 模块名不允许为空：还原为基线文本
      mind?.renderer?.setNodeText?.(node, textSnapshot.get(uid) || '');
      Message.warning('模块名称不能为空');
      return;
    }
    modRenames.set(Number(uid.replace('mod-', '')), text);
  } else if (uid.startsWith('case-')) {
    touchedCases.add(Number(uid.replace('case-', '')));
  } else if (uid.startsWith('pre-') || uid.startsWith('step-') || uid.startsWith('expect-')) {
    touchedCases.add(Number(uid.split('-')[1]));
  } else {
    return;
  }
  dirty.value = true;
}

// ===== data_change：只做 diff 记账（重命名/详情改动标记 touched），不实时保存 =====
function onDataChange() {
  if (!mind) return;
  const walk = (n: any) => {
    if (!n) return;
    const uid = uidOf(n);
    if (uid && uid !== 'root' && textSnapshot.has(uid)) {
      const text = stripHtml(textOf(n));
      if (textSnapshot.get(uid) !== text) {
        if (uid.startsWith('mod-')) {
          const value = text;
          if (!value) {
            // 模块名不允许为空：还原为基线文本
            mind.renderer?.setNodeText?.(n, textSnapshot.get(uid));
            Message.warning('模块名称不能为空');
          } else {
            modRenames.set(Number(uid.replace('mod-', '')), value);
          }
        } else if (uid.startsWith('case-')) {
          touchedCases.add(Number(uid.replace('case-', '')));
        } else if (uid.startsWith('pre-') || uid.startsWith('step-') || uid.startsWith('expect-')) {
          touchedCases.add(Number(uid.split('-')[1]));
        }
      }
    }
    childrenOf(n).forEach(walk);
  };
  walk(mind.renderer?.root || mind.renderer?.renderTree);
  dirty.value = true;
}

// ===== 拖拽：beforeDragEnd 业务校验（返回 true = 取消拖拽，节点回弹） =====
let lastDragUid = '';

// 目标节点是否是被拖节点的后代（禁止拖进自己的子树）
function isDescendant(targetUid: string, dragUid: string): boolean {
  let cur = findNodeByUid(targetUid);
  while (cur) {
    if (uidOf(cur) === dragUid) return true;
    cur = cur.parent;
  }
  return false;
}

function validateDrag(dragUid: string, overlapNodeUid: string, prevNodeUid: string, nextNodeUid: string): boolean {
  // 根节点/占位节点/前置条件/预期结果/未保存临时节点：禁止拖拽
  if (!dragUid || dragUid === 'root' || dragUid.startsWith('empty-')) return false;
  if (dragUid.startsWith('pre-') || dragUid.startsWith('expect-') || dragUid.startsWith('new-')) return false;

  // 用例节点：只能拖到模块节点上（含「未分组」），成为其子节点
  if (dragUid.startsWith('case-')) {
    return !!overlapNodeUid && overlapNodeUid.startsWith('mod-');
  }
  // 模块节点：只能拖到其他模块节点或根节点下，不能拖进自己的子树、不能拖到「未分组」
  if (dragUid.startsWith('mod-')) {
    if (!overlapNodeUid) return false;
    if (overlapNodeUid === 'mod-0') return false;
    if (overlapNodeUid !== 'root' && !overlapNodeUid.startsWith('mod-')) return false;
    if (overlapNodeUid === dragUid) return false;
    if (isDescendant(overlapNodeUid, dragUid)) return false;
    return true;
  }
  // 步骤节点：仅允许同用例内排序（落在同用例步骤兄弟之间），禁止拖成子节点或跨用例
  if (dragUid.startsWith('step-')) {
    if (overlapNodeUid) return false;
    const caseId = dragUid.split('-')[1];
    const sibling = prevNodeUid || nextNodeUid;
    return !!sibling && sibling.startsWith(`step-${caseId}-`);
  }
  return false;
}

function onBeforeDragEnd({overlapNodeUid, prevNodeUid, nextNodeUid, beingDragNodeList}: any): boolean {
  // 只支持单节点拖拽
  if (!beingDragNodeList || beingDragNodeList.length !== 1) return true;
  const dragUid = beingDragNodeList[0]?.nodeData?.data?.uid || '';
  lastDragUid = dragUid;
  const allowed = validateDrag(dragUid, overlapNodeUid || '', prevNodeUid || '', nextNodeUid || '');
  if (!allowed) {
    Message.warning('该节点不允许拖拽到此处');
  }
  return !allowed; // 返回 true 取消拖拽
}

function onNodeDragEnd({overlapNodeUid}: any) {
  const dragUid = lastDragUid;
  lastDragUid = '';
  if (!dragUid) return;
  if (dragUid.startsWith('case-')) {
    // 用例换模块：落点必须是模块节点
    if (!overlapNodeUid) return;
    caseMoves.set(Number(dragUid.replace('case-', '')), overlapNodeUid);
    dirty.value = true;
  } else if (dragUid.startsWith('mod-')) {
    // 模块换父级：落点必须是模块/根节点
    if (!overlapNodeUid) return;
    modMoves.set(Number(dragUid.replace('mod-', '')), overlapNodeUid);
    dirty.value = true;
  } else if (dragUid.startsWith('step-')) {
    // 步骤同用例内排序：落点是间隙（无 overlap），只需标记用例已改动，保存时按树顺序重建 test_steps
    touchedCases.add(Number(dragUid.split('-')[1]));
    dirty.value = true;
  }
}

// ===== 双击编辑 =====
const BLOCKED_COMMANDS = new Set([
  'INSERT_CHILD_NODE', 'INSERT_NODE', 'INSERT_PARENT_NODE', 'REMOVE_NODE', 'REMOVE_CURRENT_NODE',
  'UP_NODE', 'DOWN_NODE', 'COPY_NODE', 'CUT_NODE', 'PASTE_NODE', 'SET_NODE_IMAGE',
  'SET_NODE_CUSTOM_POSITION', // 自由拖拽不开，位置必须由布局计算
]);

function onNodeDblClick(node: any) {
  const uid: string = node?.nodeData?.data?.uid || '';
  // 根节点/未分组/占位节点不允许编辑
  if (!node || node.isRoot || uid === 'root' || uid === 'mod-0' || uid.startsWith('empty-')) {
    mind?.renderer?.textEdit?.hideEditTextBox?.();
    mind?.richText?.hideEditText?.();
  }
}

// ===== 保存 =====
async function rebuild() {
  // 重建前记录展开状态：已展开详情的用例 + 被用户折叠的节点
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
  if (mind) collect(mind.renderer?.root || mind.renderer?.renderTree);
  try {
    const res: any = await getTestCaseList(props.projectId, undefined, undefined, undefined, undefined, undefined, 1, 9999);
    const modRes: any = await getQaModuleList(props.projectId);
    const cases = res.code === 200 ? (res.data?.records || []) : [];
    const modules = buildModuleTree(modRes.code === 200 ? (modRes.data || []) : []);
    const data = buildMindData(modules, cases);
    // 恢复折叠状态（默认都是展开的，只把之前折叠的节点折回去）
    const applyCollapse = (n: any) => {
      if (!n) return;
      if (collapsedUids.has(n.data?.uid)) n.data.expand = false;
      (n.children || []).forEach(applyCollapse);
    };
    applyCollapse(data);
    // 恢复用例详情展开：直接在新数据树上挂子节点（重新拉详情，保证是保存后的最新数据），
    // 不依赖渲染后的节点实例，避免实例/数据形态差异导致恢复失败
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
        // 已被用户折叠的用例：子节点数据保留，但保持折叠状态
        target.data.expand = !collapsedUids.has(uid);
      }
    }
    if (mind) mind.setData(data);
    clearPending();
    resetSnapshot();
    ensureSnapshot();
  } catch (e) {
    console.error(e);
  }
}

function buildModuleTree(list: any[], parentId = 0): any[] {
  return list
    .filter((m: any) => (m.parentId || 0) === parentId)
    .sort((a: any, b: any) => (a.sort || 0) - (b.sort || 0))
    .map((m: any) => ({...m, children: buildModuleTree(list, m.id)}));
}

// 从用例节点的子节点树重建 前置条件 + 步骤（步骤含预期结果子节点）
function buildDetailPayload(children: any[]): { preCondition: string; testSteps: any[] } {
  let pre = '';
  const steps: any[] = [];
  children.forEach((ch: any) => {
    const cuid = uidOf(ch);
    if (cuid.startsWith('pre-') || cuid.startsWith('new-pre-')) {
      pre = stripHtml(textOf(ch));
    } else if (cuid.startsWith('step-') || cuid.startsWith('new-step-')) {
      const stepText = stripHtml(textOf(ch));
      let expected = '';
      childrenOf(ch).forEach((g: any) => {
        const guid = uidOf(g);
        if (guid.startsWith('expect-') || guid.startsWith('new-expect-')) expected = stripHtml(textOf(g));
      });
      steps.push({step: stepText, expected});
    }
  });
  return {preCondition: pre, testSteps: steps};
}

// 保存单个被改动的用例：展开过的按树重建 前置条件+步骤+等级，未展开的只补名称/等级
async function saveTouchedCase(caseId: number): Promise<boolean> {
  const node = findNodeByUid(`case-${caseId}`);
  try {
    const res: any = await getTestCaseDetail(caseId);
    if (res.code !== 200 || !res.data) return false;
    const detail = res.data;
    const patch: any = {};
    const priority = casePriorityChanges.get(caseId);
    if (priority) patch.priority = priority;
    if (node) {
      patch.caseName = stripHtml(textOf(node));
      const children = childrenOf(node).filter((c: any) => !uidOf(c).startsWith('empty-'));
      if (children.length) {
        const detailPayload = buildDetailPayload(children);
        patch.preCondition = detailPayload.preCondition;
        patch.testSteps = detailPayload.testSteps;
      }
    }
    const payload = {...detail, testSteps: normalizeSteps(detail), ...patch};
    const updateRes: any = await updateTestCase(payload);
    return updateRes.code === 200;
  } catch (e) {
    console.error('[CaseLibMindMap] 保存用例失败:', e);
    return false;
  }
}

async function handleSave() {
  if (!mind || saving.value) return;
  saving.value = true;
  try {
    // 1. 收集新建节点（DFS 前序保证父模块先于子模块处理）
    const creates: { uid: string; type: 'mod' | 'case'; name: string; parentUid: string }[] = [];
    const walk = (n: any, parentUid: string) => {
      if (!n) return;
      const uid = uidOf(n);
      if (uid.startsWith('new-mod-')) {
        creates.push({uid, type: 'mod', name: stripHtml(textOf(n)), parentUid});
      } else if (uid.startsWith('new-case-')) {
        creates.push({uid, type: 'case', name: stripHtml(textOf(n)), parentUid});
      }
      childrenOf(n).forEach((c: any) => walk(c, uid));
    };
    walk(mind.renderer?.root || mind.renderer?.renderTree, '');

    const emptyCreate = creates.find(c => !c.name);
    if (emptyCreate) {
      Message.warning('存在未填写名称的新建节点，请先完善或删除');
      return;
    }

    // 临时模块 uid → 真实 id
    const modIdMap = new Map<string, number>();
    // 临时用例 uid → 真实 id（排序持久化用）
    const caseIdMap = new Map<string, number>();
    const resolveModId = (uid: string): number | undefined => {
      if (uid === 'mod-0') return 0;
      // 根节点：未选模块时 = 顶级（0）；选中模块时 = 该模块
      if (uid === 'root') return props.selectedModuleId ?? 0;
      if (uid.startsWith('new-mod-')) return modIdMap.get(uid);
      if (uid.startsWith('mod-')) return Number(uid.replace('mod-', ''));
      return undefined;
    };

    // 2. 新建模块（先父后子）
    for (const c of creates.filter(x => x.type === 'mod')) {
      const parentId = resolveModId(c.parentUid);
      if (parentId === undefined) {
        Message.error(`模块「${c.name}」的父模块尚未保存，请分次保存`);
        return;
      }
      const res: any = await saveQaModule({projectId: props.projectId, parentId, moduleType: 'CASE', moduleName: c.name});
      if (res.code !== 200 || !res.data) {
        Message.error(`模块「${c.name}」创建失败`);
        return;
      }
      modIdMap.set(c.uid, res.data);
    }

    // 3. 模块改名
    for (const [id, name] of modRenames) {
      if (modDeletes.has(id)) continue;
      await updateQaModule({id, projectId: props.projectId, moduleName: name});
    }

    // 4. 模块移动
    for (const [id, targetUid] of modMoves) {
      if (modDeletes.has(id)) continue;
      const parentId = resolveModId(targetUid);
      if (parentId === undefined) continue;
      await updateQaModule({id, projectId: props.projectId, parentId});
    }

    // 5. 删除模块
    for (const id of modDeletes) {
      await deleteQaModule(id);
    }

    // 6. 新建用例（保存后按默认子节点树补充 前置条件/步骤 详情）
    for (const c of creates.filter(x => x.type === 'case')) {
      const moduleId = resolveModId(c.parentUid) ?? 0;
      const res: any = await saveTestCase({
        projectId: props.projectId,
        moduleId,
        caseName: c.name,
        caseType: 'FUNCTION',
        priority: tempCasePriorities.get(c.uid) || 'P2',
        status: 'DRAFT',
      });
      if (res.code !== 200 || !res.data) {
        Message.error(`用例「${c.name}」创建失败`);
        return;
      }
      // 默认子节点（前置条件/步骤/预期结果）有实际内容时补充保存
      const newId = res.data;
      caseIdMap.set(c.uid, newId);
      const tempNode = findNodeByUid(c.uid);
      const children = childrenOf(tempNode).filter((x: any) => !uidOf(x).startsWith('empty-'));
      if (children.length) {
        const detailPayload = buildDetailPayload(children);
        if (detailPayload.preCondition || detailPayload.testSteps.some((s: any) => s.step || s.expected)) {
          const detailRes: any = await getTestCaseDetail(newId);
          if (detailRes.code === 200 && detailRes.data) {
            await updateTestCase({...detailRes.data, ...detailPayload});
          }
        }
      }
    }

    // 7. 用例移动模块
    for (const [caseId, targetUid] of caseMoves) {
      if (caseDeletes.has(caseId)) continue;
      const moduleId = resolveModId(targetUid);
      if (moduleId === undefined) continue;
      await batchMoveTestCase([caseId], moduleId);
    }

    // 8. 用例详情/名称改动
    for (const caseId of touchedCases) {
      if (caseDeletes.has(caseId)) continue;
      const ok = await saveTouchedCase(caseId);
      if (!ok) {
        Message.error(`用例 #${caseId} 保存失败`);
        return;
      }
    }

    // 9. 删除用例
    for (const id of caseDeletes) {
      await deleteTestCase(id);
    }

    // 10. 用例顺序持久化：按脑图兄弟顺序重排每个模块下的 sort 值
    const sortGroups: number[][] = [];
    const walkModuleOrder = (n: any) => {
      if (!n) return;
      const uid = uidOf(n);
      // 根节点在选中模块时也视为该模块的分组容器
      const isGroup = uid === 'mod-0' || uid.startsWith('mod-') || uid.startsWith('new-mod-')
        || (uid === 'root' && props.selectedModuleId !== undefined);
      if (isGroup) {
        const ids: number[] = [];
        childrenOf(n).forEach((ch: any) => {
          const cuid = uidOf(ch);
          if (cuid.startsWith('case-')) {
            const id = Number(cuid.replace('case-', ''));
            if (!caseDeletes.has(id)) ids.push(id);
          } else if (cuid.startsWith('new-case-')) {
            const id = caseIdMap.get(cuid);
            if (id) ids.push(id);
          }
        });
        if (ids.length > 0) sortGroups.push(ids);
      }
      childrenOf(n).forEach(walkModuleOrder);
    };
    walkModuleOrder(mind.renderer?.root || mind.renderer?.renderTree);
    for (const ids of sortGroups) {
      await sortTestCase(ids);
    }

    Message.success('保存成功');
    await rebuild();
  } catch (e) {
    console.error(e);
    Message.error('保存失败，请重试');
  } finally {
    saving.value = false;
  }
}

function handleReset() {
  Modal.confirm({
    title: '放弃修改',
    content: '将放弃所有未保存的修改并恢复为已保存状态，确认？',
    onOk: () => rebuild(),
  });
}

// ===== 初始化 =====
async function initMind(retries = 0) {
  if (!mapRef.value) return;
  const {width, height} = mapRef.value.getBoundingClientRect();
  if (width === 0 || height === 0) {
    if (retries < 60) requestAnimationFrame(() => initMind(retries + 1));
    else console.error('[CaseLibMindMap] 容器尺寸为 0，放弃初始化');
    return;
  }

  let data;
  try {
    const [caseRes, modRes]: any[] = await Promise.all([
      getTestCaseList(props.projectId, undefined, undefined, undefined, undefined, undefined, 1, 9999),
      getQaModuleList(props.projectId),
    ]);
    const cases = caseRes.code === 200 ? (caseRes.data?.records || []) : [];
    data = buildMindData(buildModuleTree(modRes.code === 200 ? (modRes.data || []) : []), cases);
  } catch (e) {
    console.error(e);
    data = buildMindData([], []);
  }
  if (!mapRef.value || mind) return;

  mapRef.value.innerHTML = '';
  try {
    mind = new MindMap({
      el: mapRef.value,
      data,
      readonly: false, // 允许双击编辑/拖拽；结构命令下方受控拦截
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
      // 拖拽落点业务校验（官方钩子：返回 true 取消，节点回弹）
      beforeDragEnd: (params: any) => onBeforeDragEnd(params),
    });

    // 结构命令受控拦截：拖拽移动（MOVE_NODE_TO/INSERT_AFTER/INSERT_BEFORE）放行，
    // 增删仅内部操作（isInternalOp）放行，其余（复制/粘贴/自由拖拽定位等）一律拦截
    origExec = mind.command.exec.bind(mind.command);
    mind.command.exec = (name: string, ...args: any[]) => {
      if (BLOCKED_COMMANDS.has(name) && !isInternalOp) return;
      return origExec(name, ...args);
    };

    mind.on('node_click', (node: any) => onNodeClick(node));
    mind.on('node_dblclick', (node: any) => onNodeDblClick(node));
    mind.on('svg_mousedown', () => { hideNodeActions(); hideAddMenu(); });
    mind.on('data_change', () => onDataChange());
    mind.on('hide_text_edit', (_el: any, _activeList: any, node: any) => onTextEditEnd(node));
    mind.on('node_dragend', (params: any) => onNodeDragEnd(params));
    // 加大同级节点垂直间距，容纳节点下方的操作栏（默认 node.marginY=0 会遮挡下一个节点）
    mind.setThemeConfig({node: {marginY: 34}});
    resizeObserver = new ResizeObserver(() => {
      if (!mapRef.value || !mind) return;
      const {width: w, height: h} = mapRef.value.getBoundingClientRect();
      if (w === 0 || h === 0) return;
      try { mind.resize?.(); } catch (e) { console.warn('[CaseLibMindMap] resize 异常:', e); }
    });
    resizeObserver.observe(mapRef.value);
    resetSnapshot();
  } catch (e) {
    console.error('[CaseLibMindMap] 初始化失败:', e);
    mind = null;
    mapRef.value.innerHTML = '';
  }
}

onMounted(() => {
  nextTick(() => initMind());
  // 点击菜单外任意处关闭「+」下拉（菜单自身已 @mousedown.stop）
  document.addEventListener('mousedown', hideAddMenu);
});

// 暴露未保存状态给父页面（切换视图/离开页面前的拦截提示用）
defineExpose({
  isDirty: () => dirty.value,
});

// 左侧选中模块变化 → 根节点跟随（重新拉数据重建，保持展开状态）
watch(() => props.selectedModuleId, () => rebuild());

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  document.removeEventListener('mousedown', hideAddMenu);
  if (mind) {
    try {
      mind.destroy?.();
    } catch (e) {
      console.error('[CaseLibMindMap] destroy 异常:', e);
      if (mapRef.value) mapRef.value.innerHTML = '';
    }
    mind = null;
  }
});
</script>

<style>
.lib-mindmap-canvas * {
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
/* 「+」下拉菜单 */
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
</style>

<style scoped>
.lib-mindmap-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
}
.lib-mindmap-canvas {
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}
/* 画布右上角浮动工具栏（top 留出页面上「列表/脑图」切换按钮的位置） */
.lib-mindmap-toolbar {
  position: absolute;
  top: 44px;
  right: 8px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: var(--color-bg-2, #fff);
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.lib-mindmap-toolbar .hint {
  font-size: 12px;
  color: #86909c;
}
.lib-mindmap-toolbar .dirty-hint {
  color: #ff7d00;
}
.lib-mindmap-toolbar .loading {
  color: rgb(var(--primary-6));
}
</style>
