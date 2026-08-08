<template>
  <!--    导入步骤对话框-->
  <a-drawer
    v-if="visible"
    :visible="visible"
    title="导入已有步骤"
    :width="520"
    :hide-cancel="true"
    @cancel="handleCancel"
    @ok="handleOk"
  >
    <a-input-search
      placeholder="输入场景名称筛选"
      style="margin-bottom: 8px"
      v-model="searchKey"
    />
    <a-alert type="info" style="margin-bottom: 8px">
      勾选具体步骤进行导入；勾选子步骤时会自动连带导入其直属父步骤，以保持层级关系。
    </a-alert>
    <a-scrollbar style="height: calc(100vh - 260px); overflow: auto">
      <a-tree
        :data="displayTreeData"
        style="margin-top: 10px"
        v-model:checked-keys="checkedImportStepKeys"
        ref="importSceneStepRef"
        checkable
        block-node
        :selectable="false"
        checked-strategy="all"
        :fieldNames="{
          key: 'key',
          title: 'title',
          children: 'children',
        }"
        @check="checkedStep"
      >
        <template #title="node">
          <span class="import-tree-node-title">
            <icon-folder
              v-if="node.type === 'FOLDER'"
              style="font-size: 16px; flex-shrink: 0"
            />
            <icon-drive-file
              v-else-if="node.type === 'SCENE'"
              style="font-size: 16px; flex-shrink: 0"
            />
            <icon-code
              v-else-if="node.stepType === 'API_REQUEST'"
              style="
                font-size: 16px;
                color: rgb(var(--green-6));
                flex-shrink: 0;
              "
            />
            <icon-storage
              v-else-if="node.stepType === 'SQL'"
              style="
                font-size: 16px;
                color: #16c1f3;
                flex-shrink: 0;
              "
            />
            <icon-desktop
              v-else
              style="
                font-size: 16px;
                color: rgb(var(--arcoblue-6));
                flex-shrink: 0;
              "
            />
            <span class="import-tree-node-text">{{ node.title }}</span>
          </span>
        </template>
      </a-tree>
    </a-scrollbar>
  </a-drawer>
</template>

<script setup lang="ts">
  import { computed, ref, watch } from 'vue';
  import { importExistSceneStep, getStepBySceneId } from '@/api/MyApi/step';
  import { ImportExistSceneStepDTO } from '@/types/dto/other/ImportExistSceneStepDTO';
  import { Message } from '@arco-design/web-vue';
  import { TestStep } from '@/types/domain/TestStep';
  import {
    IconFolder,
    IconDriveFile,
    IconCode,
    IconDesktop,
    IconStorage,
  } from '@arco-design/web-vue/es/icon';

  interface TreeNode {
    key: string;
    title: string;
    type?: 'FOLDER' | 'SCENE' | 'STEP';
    stepType?: string;
    originId?: number;
    scenarioId?: number;
    children?: TreeNode[];
    disabled?: boolean;
  }

  const props = defineProps<{
    visible: boolean;
    treeData: any[];
    currentEditSceneId: any;
  }>();

  const emit = defineEmits(['update:visible', 'reloadStepList']);

  // 已选择导入的步骤key（带 step- 前缀）
  const checkedImportStepKeys = ref<string[]>([]);
  // 实际传后端的步骤ID
  const importStepIds = ref<number[]>([]);
  // 导入已存在场景步骤树列表 引用
  const importSceneStepRef = ref();
  // 搜索的场景
  const searchKey = ref('');
  // 场景ID -> 步骤树的缓存
  const sceneStepMap = ref<Map<number, TreeNode[]>>(new Map());
  // 是否正在加载步骤
  const loadingSceneIds = ref<Set<number>>(new Set());

  // 构建展示用的混合树（场景 + 已加载的步骤）
  const buildDisplayTree = (nodes: any[]): TreeNode[] => {
    return nodes.map((node) => {
      const isCurrentScene = node.id === props.currentEditSceneId;
      const base: TreeNode = {
        key: `scene-${node.id}`,
        title: node.name,
        type: node.sceneType === 'FOLDER' ? 'FOLDER' : 'SCENE',
        originId: node.id,
        disabled: isCurrentScene,
      };
      if (node.children && node.children.length > 0) {
        base.children = buildDisplayTree(node.children);
      }
      // 场景节点：如果已加载步骤，则挂载步骤子树
      if (node.sceneType === 'SCENE' && !isCurrentScene) {
        const stepChildren = sceneStepMap.value.get(node.id);
        if (stepChildren && stepChildren.length > 0) {
          base.children = stepChildren;
        }
      }
      return base;
    });
  };

  // 递归过滤搜索
  const filterTree = (nodes: TreeNode[], keyword: string): TreeNode[] => {
    const lower = keyword.toLowerCase();
    const result: TreeNode[] = [];
    for (const node of nodes) {
      const matchTitle = node.title.toLowerCase().includes(lower);
      let children: TreeNode[] | undefined;
      if (node.children) {
        children = filterTree(node.children, keyword);
      }
      if (matchTitle || (children && children.length > 0)) {
        result.push({
          ...node,
          children: children && children.length > 0 ? children : undefined,
        });
      }
    }
    return result;
  };

  const displayTreeData = computed(() => {
    const tree = buildDisplayTree(props.treeData || []);
    if (!searchKey.value) return tree;
    return filterTree(tree, searchKey.value.trim());
  });

  // 将步骤列表按 parentId 组织成树
  const buildStepTree = (steps: TestStep[]): TreeNode[] => {
    const stepMap = new Map<number, TreeNode>();
    const roots: TreeNode[] = [];

    // 先创建所有节点
    for (const step of steps) {
      const node: TreeNode = {
        key: `step-${step.id}`,
        title: step.stepName || '未命名步骤',
        type: 'STEP',
        stepType: step.stepType,
        originId: step.id,
        scenarioId: Number(step.scenarioId),
      };
      stepMap.set(step.id as number, node);
    }

    // 再构建父子关系
    for (const step of steps) {
      const node = stepMap.get(step.id as number);
      if (!node) continue;
      const parentId = step.parentId ? Number(step.parentId) : 0;
      if (parentId === 0) {
        roots.push(node);
      } else {
        const parent = stepMap.get(parentId);
        if (parent) {
          if (!parent.children) parent.children = [];
          parent.children.push(node);
        } else {
          // 父节点不在列表中，挂到根
          roots.push(node);
        }
      }
    }

    // 按 orderIndex 排序
    roots.sort((a, b) => (a.originId || 0) - (b.originId || 0));
    stepMap.forEach((n) => {
      if (n.children) {
        n.children.sort((a, b) => (a.originId || 0) - (b.originId || 0));
      }
    });

    return roots;
  };

  // 懒加载场景下的步骤
  const loadSceneSteps = async (sceneId: number) => {
    if (sceneStepMap.value.has(sceneId) || loadingSceneIds.value.has(sceneId))
      return;
    loadingSceneIds.value.add(sceneId);
    try {
      const res: any = await getStepBySceneId(String(sceneId));
      const steps: TestStep[] = res.data || [];
      sceneStepMap.value.set(sceneId, buildStepTree(steps));
    } catch (e) {
      Message.error('加载场景步骤失败');
    } finally {
      loadingSceneIds.value.delete(sceneId);
    }
  };

  // 初始化时加载所有场景（除当前场景外）的步骤
  const initSceneSteps = async () => {
    const collectScenes = (nodes: any[]): number[] => {
      const ids: number[] = [];
      for (const node of nodes) {
        if (
          node.sceneType === 'SCENE' &&
          node.id !== props.currentEditSceneId
        ) {
          ids.push(node.id);
        }
        if (node.children && node.children.length > 0) {
          ids.push(...collectScenes(node.children));
        }
      }
      return ids;
    };
    const sceneIds = collectScenes(props.treeData || []);
    // 并发加载所有场景步骤
    await Promise.all(sceneIds.map((id) => loadSceneSteps(id)));
  };

  // 确认添加
  const handleOk = async () => {
    if (importStepIds.value.length === 0) {
      Message.warning('请选择要导入的步骤');
      return;
    }
    const importExistSceneStepDTO = new ImportExistSceneStepDTO();
    importExistSceneStepDTO.targetSceneId = props.currentEditSceneId;
    importExistSceneStepDTO.sourceStepIds = importStepIds.value;
    importExistSceneStepDTO.sourceSceneIds = [];
    const result = await importExistSceneStep(importExistSceneStepDTO);
    if (result.data == true) {
      Message.success('导入成功');
    } else {
      Message.error('导入失败');
    }
    // 关闭弹窗
    handleCancel();
    // 触发刷新
    emit('reloadStepList');
  };

  const handleCancel = () => {
    checkedImportStepKeys.value = [];
    importStepIds.value = [];
    emit('update:visible', false);
  };

  const checkedStep = (checkedKeys: string[]) => {
    checkedImportStepKeys.value = checkedKeys;
    importStepIds.value = checkedKeys
      .filter((k) => k.startsWith('step-'))
      .map((k) => Number(k.replace('step-', '')));
  };

  // 监听visible，打开时加载步骤
  watch(
    () => props.visible,
    (newVal) => {
      if (newVal) {
        checkedImportStepKeys.value = [];
        importStepIds.value = [];
        sceneStepMap.value.clear();
        initSceneSteps();
      }
    }
  );
</script>

<style scoped>
  .import-tree-node-title {
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }

  .import-tree-node-text {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--color-text-1);
  }

  .import-tree-node-title
    :deep(.arco-tree-node-disabled)
    .import-tree-node-text {
    color: var(--color-text-4);
  }
</style>
