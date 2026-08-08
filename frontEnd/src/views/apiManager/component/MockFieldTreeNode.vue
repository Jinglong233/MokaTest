<template>
  <div class="mock-field-tree-node"
    >
    <div class="mock-rule-header"
    >
      <div class="col-drag"></div>
      <div class="col-name">字段名</div>
      <div class="col-type">类型</div>
      <div class="col-bool required-col" title="必填">*</div>
      <div class="col-bool nullable-col" title="允许 Null">N</div>
      <div class="col-rule">Mock 值</div>
      <div class="col-desc">描述</div>
      <div class="col-advanced">高级</div>
      <div class="col-ops">操作</div>
    </div>
    <div class="mock-rule-body"
    >
      <a-tree
        ref="treeRef"
        v-model:expanded-keys="expandedKeys"
        :data="treeData"
        :draggable="!disabled"
        block-node
        :field-names="{ key: 'key', title: 'title' }"
        :allow-drop="(allowDrop as any)"
        @drag-start="onDragStart"
        @drop="onDrop"
      >
        <template #drag-icon>
          <span></span>
        </template>
        <template #title="node"
        >
          <MockFieldRuleItem
            :rule="node.rule"
            :depth="node.depth"
            :template-list="templateList"
            :disabled="disabled"
            :is-root="node.isRoot"
            :root-label="rootLabel"
            :exclude-template-id="excludeTemplateId"
            @update-field="(field, value) => updateNodeField(node.key, field, value)"
            @field-type-change="(type) => onNodeFieldTypeChange(node.key, type)"
            @rule-type-change="(type) => onNodeRuleTypeChange(node.key, type)"
            @add-child="addChild(node.key)"
            @add-sibling="addSibling(node.key)"
            @delete="deleteNode(node.key)"
          />
        </template>
      </a-tree>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, watch, computed } from 'vue';
  import type { TreeNodeData } from '@arco-design/web-vue/es/tree/interface';
  import { MockFieldRule } from '@/types/domain/api/requestModel/MockFieldRule';
  import { DataTemplate } from '@/types/domain/api/DataTemplate';
  import MockFieldRuleItem from './MockFieldRuleItem.vue';

  interface TreeNode extends TreeNodeData {
    key: string;
    title: string;
    rule: MockFieldRule;
    children?: TreeNode[];
    isRoot?: boolean;
    depth?: number;
  }

  interface Props {
    modelValue?: MockFieldRule;
    disabled?: boolean;
    templateList?: DataTemplate[];
    rootLabel?: string;
    excludeTemplateId?: number;
  }

  const props = withDefaults(defineProps<Props>(), {
    modelValue: () => new MockFieldRule(true),
    disabled: false,
    templateList: () => [],
    rootLabel: '响应体',
  });

  const emit = defineEmits<{
    (e: 'update:modelValue', value: MockFieldRule): void;
    (e: 'change'): void;
  }>();

  const treeRef = ref();
  const treeData = ref<TreeNode[]>([]);
  const expandedKeys = ref<string[]>([]);
  // 是否已初始化过（首次加载时展开全部容器节点，后续重建保留用户折叠状态）
  let initialized = false;

  const rootRule = computed(() => props.modelValue || new MockFieldRule(true));

  watch(
    () => props.modelValue,
    (newVal) => {
      const root = JSON.parse(
        JSON.stringify(newVal || new MockFieldRule(true))
      ) as MockFieldRule;
      treeData.value = [buildTreeNode(root, true, 0)];
      if (!initialized) {
        // 首次加载：展开全部容器节点
        expandedKeys.value = collectContainerKeys(treeData.value);
        initialized = true;
      } else {
        // 后续重建：仅保留当前 expandedKeys 中仍存在的 key，避免已折叠的节点被重新展开
        const allKeys = new Set(collectAllKeys(treeData.value));
        expandedKeys.value = expandedKeys.value.filter((k) => allKeys.has(k));
      }
    },
    { immediate: true, deep: true }
  );

  function collectAllKeys(nodes: TreeNode[]): string[] {
    const keys: string[] = [];
    for (const node of nodes) {
      keys.push(node.key);
      if (node.children) {
        keys.push(...collectAllKeys(node.children));
      }
    }
    return keys;
  }

  function collectContainerKeys(nodes: TreeNode[]): string[] {
    const keys: string[] = [];
    for (const node of nodes) {
      const isContainer =
        node.rule.fieldType === 'OBJECT' ||
        node.rule.fieldType === 'ARRAY' ||
        // TEMPLATE 节点的 children 是覆盖字段，也需要默认展开
        node.rule.fieldType === 'TEMPLATE';
      if (isContainer) {
        keys.push(node.key);
      }
      if (node.children) {
        keys.push(...collectContainerKeys(node.children));
      }
    }
    return keys;
  }

  function buildTreeNode(
    rule: MockFieldRule,
    isRoot: boolean,
    depth: number
  ): TreeNode {
    if (!rule._key) {
      rule._key = MockFieldRule.generateKey();
    }
    const node: TreeNode = {
      key: rule._key,
      title: rule.fieldName || 'root',
      rule,
      isRoot,
      depth,
    };
    if (rule.children && rule.children.length > 0) {
      node.children = rule.children.map((child) =>
        buildTreeNode(child, false, depth + 1)
      );
    }
    return node;
  }

  function treeNodeToRule(node: TreeNode): MockFieldRule {
    const rule = { ...node.rule };
    if (node.children && node.children.length > 0) {
      rule.children = node.children.map(treeNodeToRule);
    } else {
      rule.children = [];
    }
    return rule;
  }

  function emitUpdate() {
    if (!treeData.value || treeData.value.length === 0) {
      emit('update:modelValue', new MockFieldRule(true));
      emit('change');
      return;
    }
    const root = treeNodeToRule(treeData.value[0]);
    emit('update:modelValue', root);
    emit('change');
  }

  // 查找节点：返回 [parentChildren, index, node]
  function findNode(
    data: TreeNode[],
    key: string,
    parent?: TreeNode[]
  ): [TreeNode[] | undefined, number, TreeNode | undefined] {
    for (let i = 0; i < data.length; i++) {
      const item = data[i];
      if (item.key === key) {
        return [parent || data, i, item];
      }
      if (item.children && item.children.length > 0) {
        const found = findNode(item.children, key, item.children);
        if (found[2]) {
          return found;
        }
      }
    }
    return [undefined, -1, undefined];
  }

  function updateNodeField(
    key: string,
    field: keyof MockFieldRule,
    value: any
  ) {
    const [, , node] = findNode(treeData.value, key);
    if (!node) return;
    (node.rule as any)[field] = value;
    emitUpdate();
  }

  function resetRuleFields(rule: MockFieldRule, isRoot: boolean) {
    rule.ruleType = isRoot ? undefined : 'name';
    rule.fixedValue = undefined;
    rule.templateId = undefined;
    rule.minItems = 0;
    rule.maxItems = 10;
    rule.uniqueItems = false;
    rule.children = [];
    rule.nullable = false;
    rule.required = true;
    rule.isConstant = false;
    rule.format = 'yyyy-MM-dd HH:mm:ss';
    rule.min = 0;
    rule.max = 100;
    rule.scale = 2;
    rule.length = 10;
    rule.minLength = 0;
    rule.maxLength = 1000;
    rule.choices = 'A,B,C';
    rule.caseType = 'lower';
    rule.charset = 'abcdefghijklmnopqrstuvwxyz0123456789';
    rule.locale = 'zh';
  }

  function onNodeFieldTypeChange(key: string, type: string) {
    const [, , node] = findNode(treeData.value, key);
    if (!node) return;
    const r = node.rule;
    r.fieldType = type as MockFieldRule['fieldType'];
    resetRuleFields(r, node.isRoot || false);

    switch (type) {
      case 'STRING':
        r.ruleType = 'name';
        break;
      case 'INT':
        r.ruleType = 'int';
        r.min = 0;
        r.max = 100;
        break;
      case 'LONG':
        r.ruleType = 'long';
        r.min = 0;
        r.max = 100000;
        break;
      case 'FLOAT':
      case 'DOUBLE':
        r.ruleType = 'float';
        r.min = 0;
        r.max = 100;
        r.scale = 2;
        break;
      case 'BOOLEAN':
        r.ruleType = 'boolean';
        r.fixedValue = undefined;
        break;
      case 'OBJECT':
        r.ruleType = undefined;
        break;
      case 'ARRAY':
        r.ruleType = undefined;
        r.minItems = 0;
        r.maxItems = 10;
        r.uniqueItems = false;
        break;
      case 'TEMPLATE':
        // 数据模板引用：ruleType 留空，模板在类型列内联选择
        r.ruleType = undefined;
        r.templateId = props.templateList[0]?.id;
        break;
    }

    // 清空子节点
    if (node.children) {
      node.children = [];
    }
    emitUpdate();
  }

  function onNodeRuleTypeChange(key: string, type: string) {
    const [, , node] = findNode(treeData.value, key);
    if (!node) return;
    const r = node.rule;
    r.ruleType = type;
    r.fixedValue = undefined;
    r.templateId = undefined;

    if (type === 'fixed' && r.fieldType !== 'BOOLEAN') {
      r.fixedValue = '';
    } else if (type === 'fixed' && r.fieldType === 'BOOLEAN') {
      r.fixedValue = 'true';
    }
    emitUpdate();
  }

  function addChild(key: string) {
    const [, , node] = findNode(treeData.value, key);
    if (!node) return;

    // 只有 Object/Array 才能添加子节点；TEMPLATE 节点允许添加子节点作为覆盖字段（不转换类型）
    if (
      node.rule.fieldType !== 'OBJECT' &&
      node.rule.fieldType !== 'ARRAY' &&
      node.rule.fieldType !== 'TEMPLATE'
    ) {
      node.rule.fieldType = 'OBJECT';
      node.rule.ruleType = undefined;
    }

    if (!node.children) {
      node.children = [];
    }
    node.children.push(buildTreeNode(new MockFieldRule(), false, (node.depth || 0) + 1));
    emitUpdate();
  }

  function addSibling(key: string) {
    const [parentChildren, index, node] = findNode(treeData.value, key);
    if (!parentChildren || !node || index < 0) return;
    const depth = node.depth || 0;
    parentChildren.splice(index + 1, 0, buildTreeNode(new MockFieldRule(), false, depth));
    emitUpdate();
  }

  function deleteNode(key: string) {
    const [parentChildren, index] = findNode(treeData.value, key);
    if (!parentChildren || index < 0) return;
    parentChildren.splice(index, 1);
    emitUpdate();
  }

  function addTopLevelSibling() {
    const root = treeData.value[0];
    if (!root) return;
    if (root.rule.fieldType !== 'OBJECT' && root.rule.fieldType !== 'ARRAY') {
      root.rule.fieldType = 'OBJECT';
      root.rule.ruleType = undefined;
    }
    if (!root.children) {
      root.children = [];
    }
    root.children.push(buildTreeNode(new MockFieldRule(), false, 1));
    emitUpdate();
  }

  // 展开全部容器节点
  function expandAll() {
    expandedKeys.value = collectContainerKeys(treeData.value);
  }

  // 折叠全部（保留根节点展开，避免界面空白）
  function collapseAll() {
    const root = treeData.value[0];
    expandedKeys.value = root ? [root.key] : [];
  }

  // 拖拽排序/移动：支持同级排序、跨父节点平移、拖入 Object/Array 容器
  function onDrop({
    e,
    dragNode,
    dropNode,
    dropPosition,
  }: {
    e: DragEvent;
    dragNode: TreeNodeData;
    dropNode: TreeNodeData;
    dropPosition: number;
  }) {
    const dragTreeNode = dragNode as TreeNode;
    const dropTreeNode = dropNode as TreeNode;

    // 根节点不允许拖拽，也不能拖到自己上
    if (dragTreeNode.isRoot || dragTreeNode.key === dropTreeNode.key) {
      return;
    }

    const dragParent = findParent(treeData.value, dragTreeNode.key);
    if (!dragParent) return;

    // 拖入 dropNode 内部：仅 Object / Array 容器可接收
    if (dropPosition === 0) {
      if (dropTreeNode.isRoot || !isContainerNode(dropTreeNode)) {
        return;
      }
      // 禁止把祖先拖到后代里造成循环
      if (isDescendant(dragTreeNode, dropTreeNode)) {
        return;
      }

      const dragIndex = dragParent.findIndex((n) => n.key === dragTreeNode.key);
      if (dragIndex < 0) return;
      const [removed] = dragParent.splice(dragIndex, 1);

      // 确保目标节点有 children
      if (!dropTreeNode.children) {
        dropTreeNode.children = [];
      }

      dropTreeNode.children.push(removed);
      updateDepth(removed, (dropTreeNode.depth || 0) + 1);
      expandedKeys.value = Array.from(
        new Set([...expandedKeys.value, dropTreeNode.key])
      );
      emitUpdate();
      return;
    }

    // 平级排序 / 跨父节点平移
    if (dropTreeNode.isRoot) return;

    const dropParent = findParent(treeData.value, dropTreeNode.key);
    if (!dropParent) return;

    const dragIndex = dragParent.findIndex((n) => n.key === dragTreeNode.key);
    const dropIndex = dropParent.findIndex((n) => n.key === dropTreeNode.key);
    if (dragIndex < 0 || dropIndex < 0) return;

    const [removed] = dragParent.splice(dragIndex, 1);

    let insertIndex = dropPosition < 0 ? dropIndex : dropIndex + 1;
    if (dragParent === dropParent && dragIndex < dropIndex) {
      insertIndex--;
    }
    insertIndex = Math.max(0, Math.min(insertIndex, dropParent.length));

    dropParent.splice(insertIndex, 0, removed);
    emitUpdate();
  }

  function allowDrop({
    dropNode,
    dropPosition,
  }: {
    dropNode: TreeNodeData;
    dropPosition: -1 | 0 | 1;
  }): boolean {
    const dropTreeNode = dropNode as TreeNode;
    // Arco Tree 自身会拦截拖到自己/拖到后代，allowDrop 只需判断目标节点是否可放入
    if (dropPosition === 0) {
      // 只允许拖入 Object / Array 容器，且不能拖入根节点
      return !dropTreeNode.isRoot && isContainerNode(dropTreeNode);
    }
    // 平级：不能排到根节点前后
    return !dropTreeNode.isRoot;
  }

  function isContainerNode(node: TreeNode): boolean {
    const type = node.rule.fieldType;
    if (type === 'OBJECT') return true;
    if (type === 'ARRAY') {
      // ARRAY 总是可以作为容器；拖入时自动切为 object 元素模式
      return true;
    }
    return false;
  }

  function isDescendant(ancestor: TreeNode, descendant: TreeNode): boolean {
    if (!ancestor.children || ancestor.children.length === 0) {
      return false;
    }
    for (const child of ancestor.children) {
      if (child.key === descendant.key) return true;
      if (isDescendant(child, descendant)) return true;
    }
    return false;
  }

  function updateDepth(node: TreeNode, depth: number) {
    node.depth = depth;
    if (node.children) {
      node.children.forEach((child) => updateDepth(child, depth + 1));
    }
  }

  function onDragStart(e: DragEvent, node: TreeNodeData) {
    const treeNode = node as TreeNode;
    if (treeNode.isRoot) {
      e.preventDefault();
    }
  }

  function findParent(data: TreeNode[], key: string): TreeNode[] | undefined {
    for (let i = 0; i < data.length; i++) {
      const item = data[i];
      if (item.children) {
        if (item.children.some((child) => child.key === key)) {
          return item.children;
        }
        const found = findParent(item.children, key);
        if (found) return found;
      }
    }
    return undefined;
  }

  defineExpose({
    addTopLevelSibling,
    expandAll,
    collapseAll,
  });
</script>

<style scoped>
  .mock-field-tree-node {
    width: 100%;
    display: flex;
    flex-direction: column;
  }

  .mock-rule-header {
    display: flex;
    align-items: center;
    width: 100%;
    box-sizing: border-box;
    padding: 8px 12px;
    background: var(--color-fill-2);
    border-bottom: 1px solid var(--color-border-2);
    font-weight: 500;
    font-size: 13px;
    color: var(--color-text-1);
    flex-shrink: 0;
    /* 相对于外层滚动容器 .field-rule-list 固定表头 */
    position: sticky;
    top: 0;
    z-index: 2;
  }

  .mock-rule-body {
    width: 100%;
  }

  /* 让 a-tree 容器撑满宽度 */
  .mock-rule-body :deep(.arco-tree) {
    width: 100% !important;
    box-sizing: border-box;
    display: block;
  }

  .mock-rule-body :deep(.arco-tree-list) {
    width: 100%;
    box-sizing: border-box;
    padding: 0;
    margin: 0;
  }

  .mock-rule-body :deep(.arco-tree-node) {
    padding: 0;
    line-height: inherit;
    width: 100%;
    box-sizing: border-box;
    border-bottom: 1px solid var(--color-border-2);
  }

  /* 隐藏缩进块，让所有节点不论层级都对齐到同一位置 */
  .mock-rule-body :deep(.arco-tree-node-indent) {
    display: none;
  }

  /* switcher 固定 24px（icon 12 + margin 12）*/
  .mock-rule-body :deep(.arco-tree-node-switcher) {
    width: 12px;
    margin-right: 12px;
    flex-shrink: 0;
  }

  /* title 撑满剩余空间（Arco 的 title-block 用 content-box 会导致宽度异常，强制改 border-box）*/
  .mock-rule-body :deep(.arco-tree-node-title) {
    margin-left: 0;
    padding: 0;
    flex: 1 1 0;
    min-width: 0;
    width: auto;
    box-sizing: border-box;
  }

  /* title-text 是 span，默认 inline，强制 block 撑满 title */
  .mock-rule-body :deep(.arco-tree-node-title-text) {
    display: block;
    width: 100%;
    box-sizing: border-box;
  }

  .mock-rule-header .col-drag {
    /* 宽度 = 拖拽手柄宽度(20px) + tree switcher 宽度(24px)，让表头与根节点行对齐 */
    width: 44px;
    flex-shrink: 0;
  }

  .mock-rule-header .col-name {
    width: 200px;
    flex-shrink: 0;
    margin-left: 4px;
  }

  .mock-rule-header .col-type {
    width: 220px;
    flex-shrink: 0;
    margin-left: 8px;
  }

  .mock-rule-header .col-bool {
    width: 28px;
    flex-shrink: 0;
    margin-left: 8px;
    text-align: center;
    font-weight: bold;
  }

  .mock-rule-header .required-col {
    color: #f53f3f;
  }

  .mock-rule-header .nullable-col {
    color: #86909c;
  }

  .mock-rule-header .col-rule {
    flex: 1;
    min-width: 180px;
    margin-left: 8px;
  }

  .mock-rule-header .col-desc {
    width: 180px;
    flex-shrink: 0;
    margin-left: 8px;
  }

  .mock-rule-header .col-advanced {
    width: 50px;
    flex-shrink: 0;
    margin-left: 8px;
    text-align: center;
  }

  .mock-rule-header .col-ops {
    width: 110px;
    flex-shrink: 0;
    margin-left: 8px;
    text-align: right;
  }

  /* a-tree 样式调整 */
  .mock-rule-body :deep(.arco-tree-node-title:hover) {
    background: transparent;
  }

  .mock-rule-body :deep(.arco-tree-node-selected .arco-tree-node-title) {
    background: transparent;
  }

  .mock-rule-body :deep(.arco-tree-node-drag-over) {
    background: #e8f3ff;
  }
</style>
