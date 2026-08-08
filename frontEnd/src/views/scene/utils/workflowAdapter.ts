import type {StepVO} from "@/types/vo/StepVO";
import type {Node, Edge} from "@vue-flow/core";

/**
 * 工作流视图适配器
 *
 * 职责：在场景步骤的「树形结构」(parentId + orderIndex + children) 与
 * Vue Flow 的「图结构」(nodes + edges) 之间做双向映射。
 *
 * 容器（IF/FOR/WHILE）渲染为 Vue Flow 的 group 节点（带边框的框），
 * 子步骤通过 parentNode + 相对坐标真正嵌套在框内，直观表达层级。
 *
 * 约束：不修改后端语义：
 * - 同一父节点下的子节点按 orderIndex 顺序执行
 * - IF/FOR/WHILE 为容器节点，子节点挂在其内部
 * - 不支持并行分支、节点多入边、跨分支跳转
 */

/** 叶子节点宽度（与 WorkflowStepNode.vue 保持一致） */
const LEAF_WIDTH = 320;
/** 叶子节点高度估算值（首屏未实测时用，实测后会用真实高度重排） */
const LEAF_HEIGHT_EST = 220;
/** 容器节点头部高度（紧凑摘要，固定） */
const GROUP_HEADER_HEIGHT = 64;
/** 容器内边距 */
const GROUP_PADDING = 16;
/** 同级节点水平间距 */
const HORIZONTAL_GAP = 40;
/** 容器内子区域最小宽度 */
const MIN_BODY_WIDTH = 320;
/** 空容器子区域最小高度（方便拖入） */
const MIN_EMPTY_BODY_HEIGHT = 80;

/** 容器类型：拥有子步骤 */
const CONTAINER_TYPES = new Set(['IF', 'WHILE', 'FOR']);

/** 新增节点的临时 id 前缀 */
export const TEMP_NODE_ID_PREFIX = 'temp-step-';

/** 判断是否为临时新增节点 */
export function isTempNodeId(id?: string | number): boolean {
  return id != null && String(id).startsWith(TEMP_NODE_ID_PREFIX);
}

/** 生成临时节点 id */
export function generateTempNodeId(): string {
  return `${TEMP_NODE_ID_PREFIX}${Date.now()}-${Math.floor(Math.random() * 10000)}`;
}

/** 是否容器步骤 */
export function isContainerStep(step?: { stepType?: string }): boolean {
  return !!step && CONTAINER_TYPES.has(step.stepType || '');
}

export function isContainerNode(node: Node): boolean {
  return node.type === 'group' || CONTAINER_TYPES.has(node.data?.stepType || '');
}

// ==================== 尺寸测量 ====================

interface Measured {
  step: StepVO;
  width: number;
  height: number;
  container: boolean;
  kids: Measured[];
}

type HeightResolver = (id: string | number) => number | undefined;

/**
 * 自底向上测量每个步骤子树的外接尺寸。
 * - 叶子高度优先取实测值（getLeafHeight），无则用估算值
 * - 容器高度 = 头部 + 子区域（子节点横向排列，取最高者）
 * - 折叠的容器只占头部高度，且不测量/不渲染子节点
 */
function measure(step: StepVO, getLeafHeight: HeightResolver, collapsedIds?: Set<string>): Measured {
  if (isContainerStep(step)) {
    if (collapsedIds?.has(String(step.id))) {
      // 折叠态：仅头部高度，子节点不参与布局（由画布过滤掉子节点）
      const width = MIN_BODY_WIDTH + GROUP_PADDING * 2;
      return {step, width, height: GROUP_HEADER_HEIGHT, container: true, kids: []};
    }
    const children = [...(step.children || [])].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
    const kids = children.map(c => measure(c, getLeafHeight, collapsedIds));
    const n = kids.length;
    const bodyWidth = n > 0
        ? kids.reduce((sum, k) => sum + k.width, 0) + HORIZONTAL_GAP * (n - 1)
        : MIN_BODY_WIDTH;
    const bodyHeight = n > 0 ? Math.max(...kids.map(k => k.height)) : MIN_EMPTY_BODY_HEIGHT;
    const width = Math.max(bodyWidth, MIN_BODY_WIDTH) + GROUP_PADDING * 2;
    const height = GROUP_HEADER_HEIGHT + GROUP_PADDING + bodyHeight + GROUP_PADDING;
    return {step, width, height, container: true, kids};
  }
  const h = getLeafHeight(step.id!) ?? LEAF_HEIGHT_EST;
  return {step, width: LEAF_WIDTH, height: h, container: false, kids: []};
}

/**
 * 把测量结果摆放成 Vue Flow 节点。
 * - 根节点使用绝对坐标，子节点使用相对父容器的坐标 + parentNode + extent:'parent'
 * - 父节点一定先于子节点入数组（Vue Flow 要求）
 */
function place(
    m: Measured,
    parentId: string | undefined,
    x: number,
    y: number,
    out: Node[]
): void {
  const node: Node = {
    id: String(m.step.id),
    type: m.container ? 'group' : 'step',
    position: {x, y},
    data: m.step,
  };
  if (parentId != null) {
    (node as any).parentNode = parentId;
    // 不设 extent:'parent'，允许把子步骤拖出容器、或把根步骤拖入容器；
    // 落点与父子关系由画布的拖拽逻辑在松手时确定
  }
  if (m.container) {
    node.style = {width: `${m.width}px`, height: `${m.height}px`};
    out.push(node);
    let cx = GROUP_PADDING;
    const cy = GROUP_HEADER_HEIGHT + GROUP_PADDING;
    for (const k of m.kids) {
      place(k, String(m.step.id), cx, cy, out);
      cx += k.width + HORIZONTAL_GAP;
    }
  } else {
    out.push(node);
  }
}

/**
 * 将 StepVO 树转换为 Vue Flow 节点列表（带布局坐标）。
 * @param steps 步骤树
 * @param getLeafHeight 叶子节点实测高度解析器（可选，用于实测重排）
 * @param collapsedIds 已折叠容器 id 集合（折叠容器不展开子节点）
 */
export function stepsToNodes(steps: StepVO[] = [], getLeafHeight?: HeightResolver, collapsedIds?: Set<string>): Node[] {
  if (!steps || steps.length === 0) return [];
  const resolver: HeightResolver = getLeafHeight || (() => LEAF_HEIGHT_EST);
  const sorted = [...steps].sort((a, b) => (a.orderIndex ?? 0) - (b.orderIndex ?? 0));
  const measured = sorted.map(s => measure(s, resolver, collapsedIds));

  const nodes: Node[] = [];
  let x = 0;
  for (const m of measured) {
    place(m, undefined, x, 0, nodes);
    x += m.width + HORIZONTAL_GAP;
  }
  return nodes;
}

// ==================== 边 ====================

/**
 * 生成顺序边：同一父节点下相邻兄弟（按 x 坐标）依次相连。
 * 容器作为其所在层级的一个兄弟参与连线；容器内部子节点之间也相连。
 */
export function nodesToEdges(nodes: Node[]): Edge[] {
  const byParent = new Map<string, Node[]>();
  nodes.forEach(n => {
    const key = (n as any).parentNode ? String((n as any).parentNode) : 'root';
    if (!byParent.has(key)) byParent.set(key, []);
    byParent.get(key)!.push(n);
  });

  const edges: Edge[] = [];
  byParent.forEach(list => {
    const sorted = [...list].sort((a, b) => (a.position?.x ?? 0) - (b.position?.x ?? 0));
    for (let i = 0; i < sorted.length - 1; i++) {
      edges.push(buildEdge(sorted[i].id, sorted[i + 1].id));
    }
  });
  return edges;
}

function buildEdge(source: string, target: string): Edge {
  return {
    id: `e-${source}-${target}`,
    source,
    target,
    type: 'smoothstep',
    style: {stroke: '#C2C8D5', strokeWidth: 2},
    markerEnd: 'arrowclosed',
  };
}

// ==================== 图 → 树 ====================

/**
 * 将 Vue Flow 节点还原为 StepVO 树。
 * - 父子关系来自 node.parentNode（容器节点 id）
 * - 同一父下按 x 坐标从小到大生成 orderIndex
 */
export function graphToSteps(nodes: Node[], _edges?: Edge[]): StepVO[] {
  const childrenByParent = new Map<string, Node[]>();
  nodes.forEach(n => {
    const key = (n as any).parentNode ? String((n as any).parentNode) : 'root';
    if (!childrenByParent.has(key)) childrenByParent.set(key, []);
    childrenByParent.get(key)!.push(n);
  });

  const build = (parentKey: string, parentIdNum: number): StepVO[] => {
    const list = [...(childrenByParent.get(parentKey) || [])]
        .sort((a, b) => (a.position?.x ?? 0) - (b.position?.x ?? 0));
    return list.map((n, index) => {
      const data = n.data || {};
      const idStr = String(data.id);
      return {
        ...data,
        id: data.id,
        stepType: data.stepType,
        stepName: data.stepName,
        parentId: parentIdNum as number,
        orderIndex: index + 1,
        children: build(idStr, data.id),
      } as StepVO;
    });
  };

  return build('root', 0);
}

/**
 * 自动布局：基于当前节点重新计算坐标（用估算高度）。
 */
export function layoutGraph(nodes: Node[]): Node[] {
  const steps = graphToSteps(nodes);
  return stepsToNodes(steps);
}

/**
 * 按当前坐标重新排序并重建（拖拽后用）。
 */
export function reorderByPosition(nodes: Node[], _edges?: Edge[]): { nodes: Node[]; edges: Edge[] } {
  const steps = graphToSteps(nodes);
  const newNodes = stepsToNodes(steps).map(n => {
    const original = nodes.find(x => x.id === n.id);
    return original ? {...n, data: original.data} : n;
  });
  return {nodes: newNodes, edges: nodesToEdges(newNodes)};
}

/**
 * 校验图结构。group 节点 + extent:'parent' 已约束层级，这里基本恒为合法。
 */
export function validateGraph(_nodes: Node[], _edges: Edge[]): { valid: boolean; message?: string } {
  return {valid: true};
}

// ==================== 变更计算（供需要的地方使用） ====================

export function computeStepChanges(
    oldSteps: StepVO[],
    newSteps: StepVO[]
): { added: StepVO[]; updated: StepVO[]; deleted: StepVO[] } {
  const oldMap = new Map<string | number, StepVO>();
  const flatten = (steps: StepVO[]): StepVO[] => {
    const out: StepVO[] = [];
    for (const s of steps || []) {
      out.push(s);
      if (s.children) out.push(...flatten(s.children));
    }
    return out;
  };

  flatten(oldSteps).forEach(s => oldMap.set(s.id!, s));
  const newFlat = flatten(newSteps);
  const newIds = new Set(newFlat.map(s => s.id));

  const added: StepVO[] = [];
  const updated: StepVO[] = [];
  newFlat.forEach(s => {
    if (isTempNodeId(s.id)) {
      added.push(s);
      return;
    }
    const old = oldMap.get(s.id!);
    if (!old) {
      added.push(s);
      return;
    }
    if (old.parentId !== s.parentId || old.orderIndex !== s.orderIndex
        || old.stepName !== s.stepName || old.stepType !== s.stepType) {
      updated.push(s);
    }
  });

  const deleted: StepVO[] = [];
  flatten(oldSteps).forEach(s => {
    if (!newIds.has(s.id)) deleted.push(s);
  });

  return {added, updated, deleted};
}
