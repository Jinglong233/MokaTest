<template>
  <a-card class="api-list-card">
    <div class="api-list-search-wrap">
      <a-input-search
        placeholder="输入目录/目录名称筛选"
        class="api-list-search"
        v-model="searchKey"
      />
    </div>
    <a-button-group class="api-list-actions">
      <a-popover title="新建目录" style="flex: 1">
        <a-button v-permission="'auto:api:create'" @click="userAddOrUpdateFolder(undefined)" style="width: 100%">
          <template #icon>
            <icon-folder-add />
          </template>
        </a-button>
      </a-popover>
      <a-dropdown v-permission="'auto:api:create'" style="flex: 1" @select="userAddInterFace">
        <a-button style="width: 100%">
          <template #icon>
            <icon-file />
          </template>
        </a-button>
        <template #content>
          <a-doption value="HTTP">HTTP 接口</a-doption>
          <a-doption value="SQL">SQL 接口</a-doption>
        </template>
      </a-dropdown>
      <a-popover title="导入 Swagger" style="flex: 1">
        <a-button v-permission="'auto:api:create'" @click="importModalVisible = true" style="width: 100%">
          <template #icon>
            <icon-upload />
          </template>
        </a-button>
      </a-popover>
      <a-popover :title="isExpandAll ? '展开全部' : '折叠全部'" style="flex: 1">
        <a-button @click="handleApiTreeExpand" style="width: 100%">
          <template #icon>
            <icon-menu-fold v-if="isExpandAll" />
            <icon-menu-unfold v-if="!isExpandAll" />
          </template>
        </a-button>
      </a-popover>
    </a-button-group>
    <div class="api-list-scrollbar">
      <a-scrollbar :disable-horizontal="true" :outer-style="{ height: '100%' }" style="height: 100%; overflow-x: hidden; overflow-y: auto;">
        <a-tree
          :data="treeData"
          style="margin-top: 10px; padding-right: 0"
          :selected-keys="[selectedApi]"
          block-node
          show-line
          ref="apiTreeRef"
          :draggable="hasApiUpdatePermission"
          @drop="onDrop"
          @drag-start="isDragging = true"
          @drag-end="isDragging = false"
          @select="selectApi"
          :fieldNames="{
            key: 'id',
            title: 'apiName',
          }"
        >
        <template #drag-icon> </template>
        <template #title="node">
          <div class="tree-node-title">
            <!-- SQL 接口：显示 SQL + 数据库名 -->
            <span
              v-if="node.apiNode === 'INTERFACE' && node.apiType === ApiType.SQL"
              class="api-method-tag"
              style="color: #16C1F3"
            >
              SQL<template v-if="node.sqlConfig?.dbConnectionName">@{{ node.sqlConfig.dbConnectionName }}</template>
            </span>
            <!-- HTTP/TCP/WS 接口：显示请求方法 -->
            <span
              v-else-if="node.apiNode === 'INTERFACE'"
              class="api-method-tag"
              :style="{ color: getMethodColor(node.requestMethod) }"
            >
              {{ node.requestMethod || 'GET' }}
            </span>
            <a-tooltip
              :content="node.apiName"
              position="br"
              :disabled="isDragging"
              v-model:popup-visible="tooltipVisibleMap[node.id]"
            >
              <span class="tree-node-name">{{ node.apiName }}</span>
            </a-tooltip>
          </div>
        </template>
        <template #switcher-icon="node">
          <icon-folder
            v-if="node.apiNode == 'FOLDER'"
            style="font-size: 16px"
          />
        </template>
        <template #extra="node">
          <span class="node-extra-actions" v-if="node.id !== 0">
            <a-dropdown position="bottom">
              <a-button type="text" size="mini" class="node-extra-btn">
                <template #icon>
                  <icon-more class="node-extra-icon" />
                </template>
              </a-button>
              <template #content v-if="node.apiNode === ApiNodeType.FOLDER">
              <a-doption v-permission="'auto:api:create'" @click="userAddSubFolder(node.id)"
                >新建子目录</a-doption
              >
              <a-doption v-permission="'auto:api:update'" @click="userAddOrUpdateFolder(node)"
                >编辑目录</a-doption
              >
              <a-doption
                v-permission="'auto:api:delete'"
                v-if="node.id != null && node.id != 0"
                @click="handleInterfaceOrFolderDelete(node.id, node.apiNode)"
                >删除目录
              </a-doption>
            </template>
            <template #content v-if="node.apiNode === ApiNodeType.INTERFACE">
              <a-doption
                v-permission="'auto:api:delete'"
                @click="handleInterfaceOrFolderDelete(node.id, node.apiNode)"
                >删除接口</a-doption
              >
              <a-doption
                v-permission="'auto:api:create'"
                v-if="node.id != null && node.id != 0"
                @click="handleSceneCopy(node.id)"
                >复制接口
              </a-doption>
            </template>
          </a-dropdown>
          </span>
        </template>
      </a-tree>
      </a-scrollbar>
    </div>
  </a-card>

  <!--添加/编辑 目录对话框-->
  <a-modal
    v-model:visible="folderFormVisible"
    :title="addOrUpdateFolderForm?.id == null ? '新建目录' : '编辑目录'"
    @before-ok="handleFolderBeforeOk"
  >
    <a-form
      ref="addOrUpdateFolderFormRef"
      :model="addOrUpdateFolderForm"
      :rules="addFormRules"
    >
      <a-form-item field="parentId" label="父目录">
        <a-tree-select
          :data="folderList"
          v-model="addOrUpdateFolderForm.parentId"
          placeholder="选择父目录"
          :fieldNames="{
            key: 'id',
            title: 'apiName',
          }"
        >
          <template #tree-slot-icon>
            <icon-folder />
          </template>
        </a-tree-select>
      </a-form-item>
      <a-form-item field="apiName" label="目录名称">
        <a-input v-model="addOrUpdateFolderForm.apiName" />
      </a-form-item>
    </a-form>
  </a-modal>
  <!--导入 Swagger / OpenAPI 弹窗-->
  <SwaggerImportModal
    v-model:visible="importModalVisible"
    :folder-list="folderList"
    @success="reloadList"
  />
</template>
<script setup lang="ts">
  import { IconFolder, IconUpload } from '@arco-design/web-vue/es/icon';
  import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
  import { ApiRequest } from '@/types/domain/api/ApiRequest';
  import { ApiNodeType } from '@/types/domain/api/apiEnum/ApiNodeType';
import { ApiType } from '@/types/domain/api/apiEnum/ApiType';
  import { AddApiInterfaceDTO } from '@/types/domain/api/dto/AddApiInterfaceDTO';
  import { Message, Modal, TreeNodeData } from '@arco-design/web-vue';
  import {
    copyApi,
    deleteApi,
    getApiTreeList,
    getFolderList,
    saveApi,
    updateApiTreeList,
  } from '@/api/MyApi/apiInterface';
  import { useProjectStore } from '@/store';
  import { ApiFolderTreeVO } from '@/types/domain/api/vo/ApiFolderTreeVO';
  import useTeamStore from '@/store/modules/team';
  import usePermission from '@/hooks/permission';
  import SwaggerImportModal from './SwaggerImportModal.vue';

  const emit = defineEmits<{
    (e: 'add', apiType: string): void;
    (e: 'updateInterface', interfaceId: number): void;
    (e: 'deleteInterfaceTab', deleteInterfaceId: number): void;
  }>();
  const projectId = useProjectStore().getProjectId as number;
  const permission = usePermission();

  const hasApiCreatePermission = computed(() => permission.hasPermission('auto:api:create'));
  const hasApiUpdatePermission = computed(() => permission.hasPermission('auto:api:update'));
  const hasApiDeletePermission = computed(() => permission.hasPermission('auto:api:delete'));

  const getMethodColor = (method?: string) => {
    switch ((method || 'GET').toUpperCase()) {
      case 'GET': return '#0e8a16';
      case 'POST': return '#0969da';
      case 'PUT': return '#9a6700';
      case 'DELETE': return '#cf222e';
      case 'PATCH': return '#8957e5';
      default: return '#666';
    }
  };

  const searchKey = ref<number | null>(null);

  const currentEditFolder = ref<ApiRequest>({});
  // 目录树列表
  const folderList = ref<ApiFolderTreeVO[]>([]);
  // 当前选择的api（接口的id）
  const selectedApi = ref<number | null>(null);

  // 目录树引用
  const apiTreeRef = ref();

  // 目录表单开关
  const folderFormVisible = ref(false);
  //
  const interfaceTree = ref<ApiFolderTreeVO[]>([]);
  // 新增/编辑 目录表单
  const addOrUpdateFolderForm = ref<ApiRequest>({});
  const addOrUpdateFolderFormRef = ref('');

  // 当前是否全部展开
  // 初始为 false，与 a-tree 组件默认折叠状态保持一致
  const isExpandAll = ref(false);

  // Swagger 导入弹窗开关
  const importModalVisible = ref(false);
  // 拖拽期间禁用节点 title tooltip，避免遮挡落点
  const isDragging = ref(false);
  // 控制每个节点 tooltip 的显隐，拖拽开始时强制全部隐藏
  const tooltipVisibleMap = reactive<Record<string | number, boolean>>({});
  watch(isDragging, (dragging) => {
    if (dragging) {
      Object.keys(tooltipVisibleMap).forEach((key) => {
        tooltipVisibleMap[key] = false;
      });
    }
  });

  const reloadList = async () => {
    const { data } = await getFolderList(projectId);
    folderList.value = data;
    const res = await getApiTreeList(projectId);
    interfaceTree.value = res.data;
  };

  onMounted(async () => {
    await reloadList();
  });

  const treeData = computed(() => {
    if (!searchKey.value) return interfaceTree.value;
    return searchData(searchKey.value);
  });

  // 搜索的目录
  const searchData = (keyword: any) => {
    const loop = (data: any) => {
      const result: any = [];
      data.forEach((item: any) => {
        if (item.apiName.indexOf(keyword) > -1) {
          result.push({ ...item });
        } else if (item.children) {
          const filterData = loop(item.children);
          if (filterData.length) {
            result.push({
              ...item,
              children: filterData,
            });
          }
        }
      });
      return result;
    };
    return loop(interfaceTree.value);
  };

  // 添加/编辑 目录
  const userAddOrUpdateFolder = (folder: ApiRequest | undefined) => {
    folderFormVisible.value = true;
    if (folder) {
      currentEditFolder.value = folder;
      // 只复制必要的字段，避免携带 children 等树结构数据
      addOrUpdateFolderForm.value = {
        id: folder.id,
        parentId: folder.parentId,
        apiName: folder.apiName,
        apiNode: folder.apiNode,
        projectId: folder.projectId,
        teamId: folder.teamId,
        sort: folder.sort
      };
      selectedApi.value = folder?.id;
    } else {
      addOrUpdateFolderForm.value = new AddApiInterfaceDTO(ApiNodeType.FOLDER);
    }
  };

  // 新建接口
  const userAddInterFace = (apiType: string) => {
    emit('add', apiType);
  };

  // 目录树的展开
  const handleApiTreeExpand = () => {
    const newExpandState = !isExpandAll.value;
    isExpandAll.value = newExpandState;
    // 等待 DOM 更新后再执行
    nextTick(() => {
      if (apiTreeRef.value) {
        apiTreeRef.value.expandAll(newExpandState);
      }
    });
  };

  // 接口树列表拖拽
  const onDrop = async ({
    dragNode,
    dropNode,
    dropPosition,
  }: {
    dragNode: any;
    dropNode: any;
    dropPosition: number;
  }) => {
    // 使用 interfaceTree 的深拷贝，避免搜索状态下只提交部分数据
    const data: any[] = JSON.parse(JSON.stringify(interfaceTree.value));

    // 检查是否允许拖拽：如果都是 SCENE 类型且要作为子节点，不允许
    if (
      dropPosition === 0 &&
      dragNode.sceneType === 'SCENE' &&
      dropNode.sceneType === 'SCENE'
    ) {
      console.warn('SCENE 类型的节点不能作为其他 SCENE 节点的子节点');
      return;
    }

    // 如果拖拽节点id是0 or null 就直接返回
    if (!dragNode.id || dragNode.id === 0) {
      Message.warning('根节点无法拖拽');
      return;
    }

    // **问题1: loop函数返回值类型声明错误**
    const loop = (
      data: any[],
      key: number,
      callback: (item: any, index: number, arr: any[]) => void
    ): boolean => {
      // 这里需要返回boolean
      return data.some((item, index, arr) => {
        if (item.id === key) {
          callback(item, index, arr);
          return true;
        }
        if (item.children && item.children.length > 0) {
          return loop(item.children, key, callback);
        }
        return false;
      });
    };

    // 深拷贝拖拽节点，避免引用问题
    const dragNodeCopy = JSON.parse(JSON.stringify(dragNode));

    // 从原位置移除拖拽节点
    const removed = loop(data, dragNodeCopy.id, (_, index, arr) => {
      arr.splice(index, 1);
    });

    if (!removed) {
      console.error('未找到要拖拽的节点');
      return;
    }

    // **问题2: 需要先找到拖拽位置再执行操作**
    let inserted = false;

    // 根据拖拽位置插入到新位置
    if (dropPosition === 0) {
      // 作为子节点插入
      inserted = loop(data, dropNode.id, (item) => {
        // 检查目标节点是否允许有子节点
        if (item.apiNode !== 'FOLDER') {
          console.warn('只有 FOLDER 类型的节点才能有子节点');
          return;
        }

        item.children = item.children || [];

        // 更新拖拽节点的父节点ID
        dragNodeCopy.parentId = item.id;

        // 设置 sort 为子节点数量 + 1（从1开始）
        dragNodeCopy.sort = item.children.length + 1;
        item.children.push(dragNodeCopy);
      });
    } else {
      // 作为同级节点插入
      inserted = loop(data, dropNode.id, (_, index, arr) => {
        const insertIndex = dropPosition < 0 ? index : index + 1;

        // 更新拖拽节点的父节点ID
        // **问题3: 需要找到正确的parentId**
        // 如果是根节点的children，parentId应该是0
        dragNodeCopy.parentId = dropNode.parentId;

        // 插入节点
        arr.splice(insertIndex, 0, dragNodeCopy);

        // 重新排序所有节点，sort 从1开始递增
        arr.forEach((node, idx) => {
          node.sort = idx + 1;
        });
      });
    }

    if (!inserted) {
      console.error('插入节点失败');
      // 可以考虑恢复原状
      return;
    }

    const result = await updateApiTreeList(data[0].children);
    if (result.code === 200) {
      Message.success('更新成功');
    } else {
      Message.error({
        content: '更新失败',
        duration: 1000,
      });
    }
    await reloadList();
    return;
  };

  // 选择API接口
  const selectApi = async (
    data: any,
    { selectedNodes }: { selectedNodes: TreeNodeData }
  ) => {
    selectedApi.value = data[0];
    if (selectedNodes[0].apiNode == ApiNodeType.FOLDER) {
      currentEditFolder.value = selectedNodes[0];
    } else if (selectedNodes[0].apiNode === ApiNodeType.INTERFACE) {
      emit('updateInterface', selectedNodes[0].id);
    }
  };

  // 过滤之后的选择目录
  const selectableTreeNode = computed(() => {
    return filterNodeAndSubNode(currentEditFolder.value.id);
  });

  // 过滤目录节点及其子节点
  const filterNodeAndSubNode = (nodeId: string) => {
    const deepCloneAndFilter = (node: any): any | null => {
      // 如果是目标节点，返回null表示过滤掉
      if (node.id === nodeId) return null;

      // 创建新节点（深拷贝）
      const newNode = { ...node };

      // 递归处理子节点
      if (node.children?.length) {
        const filteredChildren = node.children
          .map(deepCloneAndFilter)
          .filter((child: any) => child !== null);

        newNode.children = filteredChildren.length
          ? filteredChildren
          : undefined;
      }

      return newNode;
    };

    // 处理根节点是目标的情况
    if (folderList.value[0]?.id === nodeId) return [];
    // 处理普通情况
    const filteredRoot = deepCloneAndFilter(folderList.value[0]);
    return filteredRoot ? [filteredRoot] : [];
  };

  const addFormRules = {
    apiName: [
      {
        required: true,
        message: '请输入目录名称',
      },
    ],
    parentId: [
      {
        required: true,
        message: '请选择父级目录',
      },
    ],
  };

  // 确认添加目录
  const handleFolderBeforeOk = async () => {
    const error = await addOrUpdateFolderFormRef.value?.validate();
    if (error) {
      return false;
    }

    addOrUpdateFolderForm.value.projectId = useProjectStore()
      .getProjectId as number;
    addOrUpdateFolderForm.value.teamId = useTeamStore().getTeamId as number;
    const res = await saveApi(addOrUpdateFolderForm.value);
    if (res.code === 200) {
      Message.success('添加成功');
      folderFormVisible.value = false;
      // 重新获取API列表
      await reloadList();
    }
  };

  // 添加子目录
  const userAddSubFolder = async (parentId: number) => {
    // 重置表单，避免保留上一次编辑的数据
    addOrUpdateFolderForm.value = {
      parentId: parentId,
      apiNode: ApiNodeType.FOLDER,
      apiName: ''
    };
    folderFormVisible.value = true;
    selectedApi.value = parentId;
  };

  // 删除目录的确认框
  const handleInterfaceOrFolderDelete = async (
    deleteId: number,
    type: ApiNodeType
  ) => {
    Modal.warning({
      title: '确认删除？',
      content: () =>
        `删除后，该${
          type === ApiNodeType.FOLDER
            ? '目录及子目录下的接口将不再可见。'
            : '接口将不再可见；接口下的用例将被清理。'
        }`,
      cancelText: '取消',
      okText: '确认',
      hideCancel: false,
      onOk: async () => {
        const res = await deleteApi(deleteId);
        // 重新加载列表
        if (res.code === 200) {
          await reloadList();
          if (type === ApiNodeType.INTERFACE) {
            // 判断树桩列表的selectKey是不是当前的这个接口
            if (selectedApi.value === deleteId) {
              // 重置selectKey
              selectedApi.value = null;
            }

            // 删除之后，判断删除的这个接口是不是当前正在编辑的节接口，如果是，则activeKey默认取第一个
            emit('deleteInterfaceTab', deleteId);
          }
        }
      },
    });
  };

  const getCurrentFolder = () =>
    currentEditFolder.value ? currentEditFolder.value : folderList.value[0];

  // 复制接口
  const handleSceneCopy = async (apiId: number) => {
    const res = await copyApi(apiId);
    if (res.code === 200) {
      Message.success('复制成功');
      // 刷新列表
      await reloadList();
    } else {
      Message.error('复制失败');
    }
  };

  const setSelectedApi = (key: number | null) => {
    selectedApi.value = key;
  };

  const openImportModal = () => {
    importModalVisible.value = true;
  };

  defineExpose({ getCurrentFolder, reloadList, setSelectedApi, openImportModal });
</script>

<style scoped lang="less">
  .api-list-card {
    height: 100%;
    display: flex;
    flex-direction: column;

    :deep(.arco-card-body) {
      padding: 16px 0 16px 16px; /* 右侧无内边距，滚动条贴边 */
      flex: 1;
      display: flex;
      flex-direction: column;
      overflow: hidden;
    }

    :deep(.arco-tree-node) {
      border-radius: 4px;
    }

    :deep(.arco-tree-node-selected) {
      background-color: var(--color-primary-light-1);
    }

    :deep(.arco-tree-node-selected .arco-tree-node-title) {
      background-color: transparent;
    }

    :deep(.arco-tree-node:hover:not(.arco-tree-node-selected)) {
      background-color: var(--color-fill-1);
    }

    /* 隐藏 Arco Tree 默认拖拽手柄，避免占位 */
    :deep(.arco-tree-node-drag-icon) {
      display: none;
    }
  }

  .api-list-search-wrap {
    padding: 0 16px;
    margin-bottom: 8px;
  }

  .api-list-search {
    width: 100%;
  }

  .api-list-actions {
    display: flex;
    width: 100%;
    padding: 0 16px;
    margin-bottom: 8px;
  }

  /* 更多操作默认隐藏，悬浮时绝对定位到节点标题右侧 */
  :deep(.arco-tree-node) .node-extra-actions {
    position: absolute;
    right: 4px;
    top: 50%;
    transform: translateY(-50%);
    visibility: hidden;
    z-index: 1;
  }

  :deep(.arco-tree-node:hover) .node-extra-actions {
    visibility: visible;
  }

  .node-extra-btn {
    padding: 0 4px;
  }

  .node-extra-icon {
    color: rgb(var(--primary-6));
    font-size: 12px;
  }

  .api-list-scrollbar {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }

  .api-list-scrollbar :deep(.arco-scrollbar-container) {
    overflow-x: hidden !important;
    overflow-y: auto !important;
    padding-right: 12px; /* 给滚动条和更多按钮之间留出间隙 */
  }

  /* 让标题文本区和更多操作在同一行自适应，避免标题过长把更多按钮挤出容器。
     这里保留 min-width:0，确保 flex 子项可以收缩；不设置 overflow:hidden，
     否则 Arco Tree 拖拽落点指示线（::before 在标题上下沿外侧）会被裁掉。 */
  :deep(.arco-tree-node-title) {
    min-width: 0;
    margin: 1px 0;
  }

  :deep(.arco-tree-node-title-text) {
    display: flex !important;
    align-items: center;
    flex: 1 !important;
    min-width: 0 !important;
  }

  .tree-node-title {
    display: flex;
    align-items: center;
    gap: 6px;
    flex: 1;
    min-width: 0;
    max-width: 100%;
    overflow: hidden;
    padding-right: 24px; /* 给悬浮的更多按钮预留位置 */
  }

  .tree-node-name {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    flex: 1;
    min-width: 0;
  }

  /* a-tooltip 内部会套一层 display:inline-block 的 .arco-trigger-wrapper，
     不处理会破坏 .tree-node-title 的 flex 布局，导致省略失效 / tooltip 定位异常。
     让该 wrapper 参与 flex 并占满剩余宽度。 */
  .tree-node-title :deep(.arco-trigger-wrapper) {
    flex: 1;
    min-width: 0;
    display: flex;
  }

  .api-method-tag {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    min-width: 28px;
    padding: 0 2px;
    height: 16px;
    font-size: 10px;
    font-weight: 600;
    line-height: 1;
    flex-shrink: 0;
  }
</style>
