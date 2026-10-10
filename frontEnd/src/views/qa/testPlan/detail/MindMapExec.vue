<template>
  <div class="mindmap-wrapper">
    <div ref="mapRef" class="mindmap-canvas"></div>
    <!-- 画布右上角悬浮工具栏（与用例库脑图一致） -->
    <div class="mindmap-toolbar-top">
      <span class="hint">{{ $t('qa.testPlan.mindmap.tpCount', { count: testPoints.length }) }}</span>
      <a-button size="mini" type="primary" :loading="saving" @click="handleSave">{{ $t('qa.common.save') }}</a-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref, onMounted, onBeforeUnmount, nextTick} from 'vue';
import {useI18n} from 'vue-i18n';
import {Message} from '@arco-design/web-vue';
import MindMap from 'simple-mind-map/dist/simpleMindMap.esm.min.js';
import 'simple-mind-map/dist/simpleMindMap.esm.min.css';
import {getTestPoints, saveTestPoints} from '@/api/MyApi/qa';

const props = defineProps<{
  planId: number;
  planName: string;
  caseList: any[];
}>();

const emit = defineEmits<{
  openDrawer: [testPointId: string, testPointName: string];
}>();

const {t} = useI18n();

const mapRef = ref<HTMLElement>();
let mind: any = null;
let origExec: any = null;
let isInternalOp = false;
let resizeObserver: ResizeObserver | null = null;
const saving = ref(false);
const dirty = ref(false); // 未保存修改标记（新增/删除测试点、改名）
const testPoints = ref<any[]>([]); // 后端返回的测试点数据

const uidOf = (node: any): string => node?.nodeData?.data?.uid || '';
// 测试点节点：已有 tp-<id>、临时 tp-new-<ts>；「未分配」不算可编辑测试点
const isTpNode = (node: any): boolean => {
  const uid = uidOf(node);
  return uid.startsWith('tp-') && uid !== 'tp-unassigned';
};

// ===== 节点操作按钮（addCustomContentToNode 叠加层，与用例库脑图同款交互） =====
const ACTION_BTN_W = 20;
const ACTION_BTN_H = 20;
const ACTION_GAP = 4;
const actionElMap = new Map<string, HTMLElement>();
let shownActionUid = '';

const ICON_PLUS = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round"><path d="M8 3v10M3 8h10"/></svg>';
const ICON_TRASH = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M2 4h12M5.5 4V2.5h5V4M3.5 4l.8 10h7.4l.8-10"/></svg>';
const ICON_SIBLING = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"><path d="M3 3.5h10M3 8h10M8 11v3.5M6 13.5l2 2 2-2"/></svg>';
const ICON_LINK = '<svg width="12" height="12" viewBox="0 0 16 16" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"><path d="M6.5 9.5l3-3M5 7l-1.8 1.8a2.6 2.6 0 0 0 3.7 3.7L8.7 10.7M11 9l1.8-1.8a2.6 2.6 0 0 0-3.7-3.7L7.3 5.3"/></svg>';

function getNodeActions(node: any): { key: string; icon: string; danger?: boolean; title?: string }[] {
  const uid = uidOf(node);
  // 「功能用例」固定节点：只能添加测试点
  if (uid === 'cat-functional') {
    return [{key: 'addTp', icon: ICON_PLUS, title: t('qa.testPlan.tp.add')}];
  }
  // 测试点节点：添加同级测试点 / 关联用例 / 删除
  if (isTpNode(node)) {
    return [
      {key: 'siblingTp', icon: ICON_SIBLING, title: t('qa.testPlan.tp.addSibling')},
      {key: 'linkCase', icon: ICON_LINK, title: t('qa.testPlan.tp.linkCase')},
      {key: 'del', icon: ICON_TRASH, danger: true, title: t('qa.common.delete')},
    ];
  }
  return [];
}

function createNodeActions(node: any) {
  const uid = uidOf(node);
  const actions = getNodeActions(node);
  if (!actions.length) return null;

  const el = document.createElement('div');
  el.className = 'tpm-actions';
  // 阻止冒泡，避免触发画布拖拽/节点点击（文档：自定义内容内交互需阻止 mousedown 冒泡）
  el.addEventListener('mousedown', (e) => e.stopPropagation());
  actions.forEach((a) => {
    const btn = document.createElement('span');
    btn.className = `tpm-icon-btn${a.danger ? ' tpm-icon-danger' : ''}`;
    btn.innerHTML = a.icon;
    if (a.title) btn.title = a.title;
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      onNodeAction(a.key, node);
    });
    el.appendChild(btn);
  });
  actionElMap.set(uid, el);
  // 重渲染后恢复点击展开状态
  if (uid === shownActionUid) el.classList.add('tpm-show');
  return {
    el,
    width: actions.length * ACTION_BTN_W + (actions.length - 1) * ACTION_GAP,
    height: ACTION_BTN_H,
  };
}

function hideNodeActions() {
  shownActionUid = '';
  actionElMap.forEach(el => el.classList.remove('tpm-show'));
}

function showNodeActions(node: any) {
  const uid = uidOf(node);
  hideNodeActions();
  const el = actionElMap.get(uid);
  if (el) {
    shownActionUid = uid;
    el.classList.add('tpm-show');
  }
}

function onNodeAction(key: string, node: any) {
  if (key === 'addTp') insertTestPoint(node, false);
  else if (key === 'siblingTp') insertTestPoint(node, true);
  else if (key === 'del') {
    isInternalOp = true;
    try {
      origExec('REMOVE_NODE', [node]);
      dirty.value = true;
    } finally {
      isInternalOp = false;
    }
  } else if (key === 'linkCase') {
    const uid = uidOf(node);
    emit('openDrawer', uid.replace(/^tp-/, ''), node?.nodeData?.data?.text || '');
  }
}

// 用例集合节点：文本「N条」+ 末尾「用例数」tag（官方 data.tag 通道）
const CASESET_STYLE = {fillColor: '#f7f8fa', color: '#4e5969'};
const casesetTag = () => [{text: t('qa.testPlan.caseCount'), style: {backgroundColor: '#165dff', color: '#ffffff', borderRadius: 3, fontSize: 12}}];
const casesetNode = (uid: string, n: number) => ({
  data: {text: t('qa.testPlan.itemsCount', { n }), uid, expand: true, tag: casesetTag(), ...CASESET_STYLE},
  children: [] as any[],
});

// 插入测试点节点：默认文本，一次性携带用例集合子节点（appointChildren 官方参数），插入后自动进入编辑
function insertTestPoint(targetNode: any, asSibling: boolean) {
  if (!mind || !origExec || !targetNode) return;
  const uid = `tp-new-${Date.now()}`;
  const children = [casesetNode(`caseset-${uid}`, 0)];
  isInternalOp = true;
  try {
    origExec(asSibling ? 'INSERT_NODE' : 'INSERT_CHILD_NODE', true, [targetNode], {text: t('qa.testPlan.defaultTestPoint'), uid}, children);
  } finally {
    isInternalOp = false;
  }
  dirty.value = true;
}

// 从后端数据构建脑图树
function buildMindData(tpData: any) {
  const tps = tpData?.testPoints || [];
  const unassigned = tpData?.unassignedCases || [];
  testPoints.value = tps;

  const root: any = {
    // 根节点必须给稳定 uid：渲染器按 uid 复用/清理节点，无 uid 时重复渲染旧根节点 DOM 不会被销毁，导致脑图重叠
    data: { text: props.planName || t('qa.testPlan.title'), expand: true, uid: 'root' },
    children: [
      { data: { text: t('qa.testPlan.tab.cases'), expand: true, uid: 'cat-functional' }, children: [] },
      { data: { text: t('qa.testPlan.cat.api'), expand: true, uid: 'cat-api' }, children: [] },
      { data: { text: t('qa.testPlan.cat.scenario'), expand: true, uid: 'cat-scenario' }, children: [] },
    ],
  };

  const funcNode = root.children[0];
  tps.forEach((tp: any) => {
    const cases = tp.cases || [];
    funcNode.children.push({
      data: { text: tp.name, expand: true, uid: `tp-${tp.id}` },
      children: [casesetNode(`caseset-${tp.id}`, cases.length)],
    });
  });

  // 未分配用例作为独立测试点
  if (unassigned.length > 0) {
    funcNode.children.push({
      data: { text: t('qa.testPlan.unassigned'), expand: true, uid: 'tp-unassigned' },
      children: [casesetNode('caseset-unassigned', unassigned.length)],
    });
  }

  return root;
}

function onNodeDblClick(node: any) {
  const uid = uidOf(node);

  if (uid.startsWith('caseset-')) {
    // 已有节点 caseset-<id>、新节点 caseset-tp-new-<ts>，统一剥成 idStr 与缓存查找口径一致
    const tpId = uid.replace('caseset-', '').replace(/^tp-/, '');
    const tpName = node.parent?.nodeData?.data?.text || '';
    emit('openDrawer', tpId, tpName);
    mind.renderer?.textEdit?.hideEditTextBox?.();
    return;
  }

  // 只有测试点节点可双击编辑文本
  if (!isTpNode(node)) {
    mind.renderer?.textEdit?.hideEditTextBox?.();
  }
}

// 从脑图树提取测试点数据 → 调后端保存
function extractTestPoints(): any[] {
  const root = mind.getData();
  const funcNode = root?.children?.find((c: any) => c.data?.uid === 'cat-functional');
  if (!funcNode?.children) return [];

  return funcNode.children
    .filter((tp: any) => {
      const uid = tp.data?.uid || '';
      return uid.startsWith('tp-') && uid !== 'tp-unassigned';
    })
    .map((tp: any, index: number) => {
      const uid = tp.data?.uid || '';
      const idStr = uid.replace('tp-', '');
      const id = /^\d+$/.test(idStr) ? parseInt(idStr) : null;
      // ponytail: caseIds 从 testPoints 缓存取，脑图节点不存 case 详情
      const cached = testPoints.value.find(t => String(t.id) === idStr);
      return {
        id,
        name: (tp.data?.text || t('qa.testPlan.defaultTestPoint')).replace(/<[^>]*>/g, ''),
        sortOrder: index,
        caseIds: cached?.cases?.map((c: any) => c.testCaseId) || [],
      };
    });
}

async function handleSave() {
  if (!mind || !props.planId) return;
  saving.value = true;
  try {
    const tps = extractTestPoints();
    const res: any = await saveTestPoints(props.planId, tps);
    if (res.code === 200) {
      Message.success(t('qa.testPlan.saveSuccess'));
      dirty.value = false;
      await loadTestPoints();
    }
  } catch (e) {
    console.error(e);
    Message.error(t('qa.testPlan.saveFail'));
  } finally {
    saving.value = false;
  }
}

async function loadTestPoints() {
  if (!props.planId) return;
  try {
    const res: any = await getTestPoints(props.planId);
    if (res.code === 200) {
      const data = buildMindData(res.data);
      if (mind) {
        // setData 内部已调 reRender，再手动 render 会重复绘制导致脑图重叠
        mind.setData(data);
      }
    }
  } catch (e) {
    console.error(e);
  }
}

async function initMind(retries = 0) {
  if (!mapRef.value) return;

  // simple-mind-map 构造函数在容器宽高为 0 时会直接 throw，等布局完成再初始化
  const {width, height} = mapRef.value.getBoundingClientRect();
  if (width === 0 || height === 0) {
    if (retries < 60) requestAnimationFrame(() => initMind(retries + 1));
    else console.error('[MindMap] 容器尺寸为 0，放弃初始化');
    return;
  }

  if (!mind) {
    // 先取到真实数据再构造。构造器会立即渲染一次 opt.data，若先渲染空结构、接口回来再 setData，
    // 两次渲染间隔跨 tick 时旧节点清理不可靠，会出现多份脑图重叠
    let data;
    try {
      const res: any = await getTestPoints(props.planId);
      data = buildMindData(res.code === 200 ? res.data : null);
    } catch (e) {
      console.error(e);
      data = buildMindData(null);
    }
    if (!mapRef.value || mind) return; // await 期间组件可能已卸载或已初始化

    // 防御：构造前清空容器，避免异常重试/重复挂载导致多个 SVG 重叠
    mapRef.value.innerHTML = '';
    try {
      mind = new MindMap({
        el: mapRef.value,
        data,
        readonly: false,
        layout: 'logicalStructure',
        theme: 'default',
        nodeTextEditZIndex: 1000,
        expandBtnSize: 20,
        // 节点叠加操作按钮（官方 addCustomContentToNode，v0.13.1+）
        addCustomContentToNode: {
          create: (node: any) => createNodeActions(node),
          // 定位到节点下方水平居中（节点间距 marginY 已留足空间，不遮挡下一个节点）
          handle: ({content, element, node}: any) => {
            element.x(node.width / 2 - content.width / 2).y(node.height + 4);
          },
        },
      });

      // 结构命令受控拦截：增删/重排仅内部操作（isInternalOp）放行；
      // 键盘 Tab/Enter/Delete 走业务规则——功能用例下才能加测试点、测试点才能加同级/删除
      origExec = mind.command.exec.bind(mind.command);
      mind.command.exec = (name: string, ...args: any[]) => {
        if (isInternalOp) return origExec(name, ...args);

        const node = (mind.renderer?.activeNodeList || [])[0];

        if (name === 'INSERT_CHILD_NODE') {
          if (uidOf(node) === 'cat-functional') insertTestPoint(node, false);
          return;
        }
        if (name === 'INSERT_NODE') {
          if (isTpNode(node)) insertTestPoint(node, true);
          return;
        }
        if (name === 'REMOVE_NODE' || name === 'REMOVE_CURRENT_NODE') {
          if (!isTpNode(node)) return;
          dirty.value = true;
        }

        return origExec(name, ...args);
      };

      mind.on('node_click', (node: any) => {
        // node_click 先于原生 active() 触发，延迟到原生处理后再断言激活：
        // 节点被多次点击始终保持选中，取消选中只能靠点击空白画布
        showNodeActions(node);
        setTimeout(() => {
          if (node && !node.nodeData?.data?.isActive) {
            mind.renderer.setNodeActive(node, true);
          }
        }, 0);
      });
      mind.on('node_dblclick', (node: any) => onNodeDblClick(node));
      // 点空白画布才隐藏工具栏；点在节点上（含根节点左键冒泡）不清除
      mind.on('svg_mousedown', (e: any) => {
        if (e?.target?.closest?.('.smm-node')) return;
        hideNodeActions();
      });
      // 双击编辑结束：测试点改名记为未保存（空文本兜底回默认名，避免提取出空名称）
      mind.on('hide_text_edit', (_el: any, _activeList: any, node: any) => {
        if (!isTpNode(node)) return;
        const d = node?.nodeData?.data;
        if (!d) return;
        if (!(d.text || '').trim()) {
          d.text = t('qa.testPlan.defaultTestPoint');
          mind.render();
        }
        dirty.value = true;
      });
      // 加大同级节点垂直间距，容纳节点下方的操作栏（默认 node.marginY=0 会遮挡下一个节点）
      mind.setThemeConfig({node: {marginY: 34}});

      // 容器尺寸变化（tab 切换/布局调整）时同步画布，否则可拖动区域停留在初始化时的小尺寸
      // 注意：tab 隐藏时容器宽高等 0，此时调用 resize 会直接抛异常，需跳过
      resizeObserver = new ResizeObserver(() => {
        if (!mapRef.value || !mind) return;
        const {width: w, height: h} = mapRef.value.getBoundingClientRect();
        if (w === 0 || h === 0) return;
        try {
          mind.resize?.();
        } catch (e) {
          console.warn('[MindMap] resize 异常:', e);
        }
      });
      resizeObserver.observe(mapRef.value);
    } catch (e) {
      console.error('[MindMap] 初始化失败:', e);
      mind = null;
      mapRef.value.innerHTML = '';
    }
  } else {
    loadTestPoints();
  }
}

onMounted(() => {
  nextTick(() => initMind());
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
  if (mind) {
    try {
      mind.destroy?.();
    } catch (e) {
      console.error('[MindMap] destroy 异常:', e);
      if (mapRef.value) mapRef.value.innerHTML = '';
    }
    mind = null;
  }
});

defineExpose({
  isDirty: () => dirty.value,
  getTestPointCases: (tpId: string) => {
    const tp = testPoints.value.find(t => String(t.id) === tpId);
    return tp?.cases || [];
  },
  // 替换某测试点关联的用例（供抽屉确认后调用）：更新缓存 → saveTestPoints → loadTestPoints 重建
  replaceTestPointCases: async (tpId: string, testCaseIds: number[]) => {
    let tp = testPoints.value.find(t => String(t.id) === tpId);
    if (!tp) {
      // 新建未保存的测试点不在后端缓存里，先补一条缓存（id 为时间戳 uid），否则关联后不会触发保存
      tp = { id: tpId, cases: [] };
      testPoints.value.push(tp);
    }
    tp.cases = testCaseIds.map((id: number) => ({ testCaseId: id }));
    await handleSave();
  },
});
</script>

<style>
.mindmap-canvas * {
  margin: 0;
  padding: 0;
}

/* 节点叠加操作图标（真实 DOM 渲染在 foreignObject 里，CSS 直接生效；点击节点时显示在节点下方） */
.tpm-actions {
  display: none;
  gap: 4px;
  align-items: center;
}
.tpm-actions.tpm-show {
  display: flex;
}
.tpm-icon-btn {
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
.tpm-icon-btn:hover {
  background: #e8f3ff;
}
.tpm-icon-danger {
  color: #f53f3f;
  border-color: #fbaca3;
}
.tpm-icon-danger:hover {
  background: #ffece8;
}
</style>

<style scoped>
.mindmap-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  overflow: hidden;
  position: relative;
}
.mindmap-canvas {
  flex: 1;
  width: 100%;
  position: relative;
  overflow: hidden;
}
/* 画布右上角悬浮工具栏 */
.mindmap-toolbar-top {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 10px;
  background: var(--color-bg-2, #ffffff);
  border: 1px solid #e5e6eb;
  border-radius: 6px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.mindmap-toolbar-top .hint {
  font-size: 13px;
  color: #86909c;
}
</style>
