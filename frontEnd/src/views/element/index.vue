<template>
  <div class="element-page">
    <Breadcrumb :items="['menu.uiAutomation', 'menu.uiAutomation.elementList']" />
    <div class="element-main-row">
      <!--    左侧目录树-->
      <div class="element-col element-side-col" :style="sideColStyle">
        <a-card class="element-card element-folder-card">
          <div class="element-tree-search-wrap">
            <a-input-search
                class="element-tree-search"
                v-model="searchKey"
            />
          </div>
          <a-button-group class="element-tree-actions">
            <a-popover title="新建目录" style="flex: 1;">
              <a-button v-permission="'auto:element:create'" @click="userAddOrUpdateFolder('新建')" style="width: 100%;">
                <template #icon>
                  <icon-folder-add/>
                </template>
              </a-button>
            </a-popover>
            <a-popover title="新建元素" style="flex: 1;">
              <a-button v-permission="'auto:element:create'" @click="userAddOrUpdateElement('新建')" style="width: 100%;">
                <template #icon>
                  <icon-file/>
                </template>
              </a-button>
            </a-popover>
            <a-popover title="显示全部元素" style="flex: 1;">
              <a-button @click="allElement()" style="width: 100%;">
                <template #icon>
                  <icon-select-all/>
                </template>
              </a-button>
            </a-popover>
            <a-popover :title="isExpandAll ? '展开全部目录' : '折叠全部目录'" style="flex: 1;">
              <a-button @click="handleFolderTreeExpand" style="width: 100%;">
                <template #icon>
                  <icon-menu-fold v-if="isExpandAll"/>
                  <icon-menu-unfold v-if="!isExpandAll"/>
                </template>
              </a-button>
            </a-popover>
          </a-button-group>
          <div class="element-tree-scroll-wrapper">
            <a-scrollbar
                :disable-horizontal="true"
                :outer-style="{ height: '100%' }"
                style="height: 100%; overflow-x: hidden; overflow-y: auto;"
            >
              <a-tree :data="treeData"
                      style="margin-top: 10px; padding-right: 0"
                      :selected-keys="[selectedFolder]"
                      block-node
                      show-line
                      ref="folderTreeRef"
                      draggable
                      @drop="onDrop"
                      @select="selectFolder"
                      :fieldNames="{
                key: 'id',
                title: 'elementName',
                }"
              >
                <template #drag-icon></template>
                <template #switcher-icon="node, { isLeaf }">
                  <icon-folder style="font-size: 16px;"/>
                </template>
                <template #title="node">
                  <a-tooltip :content="node.elementName" position="top">
                    <div class="element-node-title-wrap">
                      <span class="element-node-title">{{ node.elementName }}</span>
                    </div>
                  </a-tooltip>
                </template>
                <template #extra="node">
                  <span class="node-extra-actions" v-if="node.id !== '0'">
                    <a-dropdown position="bottom">
                      <a-button type="text" size="mini" class="node-extra-btn" @click.stop>
                        <template #icon>
                          <icon-more class="node-extra-icon"/>
                        </template>
                      </a-button>
                      <template #content>
                        <a-doption v-permission="'auto:element:create'" @click="userAddSubFolder(node.id)">新建子目录</a-doption>
                        <a-doption v-permission="'auto:element:create'" @click="userAddSubElement(node.id)">新建元素</a-doption>
                        <a-doption v-permission="'auto:element:update'" @click="userAddOrUpdateFolder('编辑',node)"
                        >编辑目录</a-doption>
                        <a-doption v-permission="'auto:element:delete'" v-if="node.id !=null && node.id!=0"
                                    @click="handleFolderDelete(node.id)">删除目录
                        </a-doption>
                      </template>
                    </a-dropdown>
                  </span>
                </template>
              </a-tree>
            </a-scrollbar>
          </div>
        </a-card>
      </div>
      <div
          class="element-main-resizer"
          :class="{ 'is-collapsed': isSidebarCollapsed }"
          @mousedown="startResizeSidebar"
      >
        <div v-if="isSidebarCollapsed" class="sidebar-expand-btn">
          <icon-right class="sidebar-expand-icon"/>
        </div>
        <span v-else class="resizer-line"/>
      </div>
      <!--    中间元素列表-->
      <div class="element-col element-right-col">
        <a-card class="element-card element-list-card">
          <!--        顶部元素操作-->
          <a-row>
            <a-col :span="14">
              <a-form :model="searchElementForm" layout="inline">
                <a-form-item field="elementName">
                  <a-input v-model="searchElementForm.elementName" placeholder="输入元素名称"/>
                </a-form-item>
                <a-form-item field="locatorType">
                  <a-select v-model="searchElementForm.locatorType" placeholder="输入定位类型" allow-clear>
                    <a-option value="XPATH">xpath</a-option>
                    <a-option value="TEXT">text</a-option>
                    <a-option value="PLACEHOLDER">placeholder</a-option>
                    <a-option value="ROLE">role</a-option>
                    <a-option value="TEST_ID">test_id</a-option>
                    <a-option value="LABEL">label</a-option>
                    <a-option value="TITLE">title</a-option>
                    <a-option value="ALT">alt</a-option>
                    <a-option value="CSS">css</a-option>
                  </a-select>
                </a-form-item>
                <a-form-item>
                  <a-button type="primary" @click="searchElementList">搜索</a-button>
                </a-form-item>
                <a-form-item>
                  <a-button type="dashed" @click="resetElementList">重置</a-button>
                </a-form-item>
              </a-form>
            </a-col>

            <a-col :offset="6" :span="2">
              <a-button v-permission="'auto:element:create'" type="primary" @click="userAddOrUpdateElement('新建')">
                <template #icon>
                  <icon-plus/>
                </template>
                新建元素
              </a-button>
            </a-col>
            <a-col :span="2">
              <a-dropdown>
                <a-button v-permission="'auto:element:delete'" type="primary"> 批量操作</a-button>
                <template #content>
                  <a-doption @click="handleBatchDelete()">批量删除</a-doption>
                  <a-doption disabled>批量移动(待做...)</a-doption>
                </template>
              </a-dropdown>
            </a-col>
          </a-row>
          <a-table row-key="id" :columns="elementColumns" :data="elementList"
                   :row-selection="{
                    type: 'checkbox',
                    showCheckedAll: true,
                    onlyCurrent: false,
                  }"
                   :bordered="{cell:true}"
                   :showCheckedAll="true"
                   v-model:selectedKeys="selectRowSelection"
                   :pagination="false">
            <template #operation="{ record }">
              <a-button v-permission="'auto:element:update'" status="success" @click="userAddOrUpdateElement('编辑',record)">
                <template #icon>
                  <IconEdit/>
                </template>
              </a-button>
              <a-divider direction="vertical"/>
              <a-button v-permission="'auto:element:delete'" status="danger" @click="handleElementDelete(record.id)">
                <template #icon>
                  <IconDelete/>
                </template>
              </a-button>

            </template>
          </a-table>

          <a-pagination class="element-pagination" :total="pagination.total"
                        size="small"
                        show-total
                        show-page-size
                        @page-size-change="pageSizeChange"
                        @change="pageNumberChange"/>
        </a-card>
      </div>
    </div>

    <!--添加/编辑 目录对话框-->
    <a-modal v-model:visible="addFolderVisible" :title="`${modalMode}目录`" @cancel="handleCancel"
             @before-ok="handleFolderBeforeOk">
      <a-form ref="addFormRef" :model="addOrUpdateFolderForm" :rules="addFormRules">
        <a-form-item field="parentId" label="父目录" required>
          <a-tree-select
              :data="selectableTreeNode"
              v-model="addOrUpdateFolderForm.parentId"
              placeholder="选择父目录"
              :fieldNames="{
                key: 'id',
                title: 'elementName',
            }"
          >
            <template #tree-slot-icon>
              <icon-folder/>
            </template>
          </a-tree-select>
        </a-form-item>
        <a-form-item field="elementName" label="目录名称" required>
          <a-input v-model="addOrUpdateFolderForm.elementName"/>
        </a-form-item>
      </a-form>
    </a-modal>

    <!--添加元素对话框-->
    <a-modal v-model:visible="addOrUpdateElementVisible" title="新建元素" @cancel="handleElementCancel"
             @before-ok="handleElementBeforeOk">
      <a-form ref="addElementFormRef" :model="addOrUpdateElementForm" :rules="addElementFormRules">
        <a-form-item field="parentId" label="所属目录" required>
          <a-tree-select
              :data="folderTree"
              placeholder="元素所属目录"
              :fieldNames="{
                key: 'id',
                title: 'elementName',
            }"
              v-model="addOrUpdateElementForm.parentId"
          >
            <template #tree-slot-icon>
              <icon-folder/>
            </template>
          </a-tree-select>
        </a-form-item>
        <a-form-item field="elementName" label="元素名称" required>
          <a-input v-model="addOrUpdateElementForm.elementName"/>
        </a-form-item>
        <a-form-item field="locatorType" label="定位方式" required>
          <a-select v-model="addOrUpdateElementForm.locatorType">
            <a-option value="XPATH">xpath</a-option>
            <a-option value="TEXT">text</a-option>
            <a-option value="PLACEHOLDER">placeholder</a-option>
            <a-option value="ROLE">role</a-option>
            <a-option value="TEST_ID">test_id</a-option>
            <a-option value="LABEL">label</a-option>
            <a-option value="TITLE">title</a-option>
            <a-option value="ALT">alt</a-option>
            <a-option value="CSS">css</a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="locatorValue" label="定位值" required>
          <a-input v-model="addOrUpdateElementForm.locatorValue"/>
        </a-form-item>
        <a-form-item field="description" label="元素描述">
          <a-textarea v-model="addOrUpdateElementForm.description" allow-clear :max-length="50" show-word-limit
                      :auto-size="true"/>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import {IconFolder, IconRight} from '@arco-design/web-vue/es/icon';
import {computed, onMounted, ref, watch} from 'vue';
import {Message, Modal, TreeNodeData} from "@arco-design/web-vue";
import Breadcrumb from '@/components/breadcrumb/index.vue';
import {ElementVO} from "@/types/vo/ElementVO";
import {Element} from "@/types/domain/Element";
import {
  add,
  deleteElementBatch,
  deleteElementOrFolder,
  getFolderList,
  pageElementList,
  update, updateElementSort
} from "@/api/MyApi/element";
import {ElementQueryDTO} from "@/types/dto/queryDTO/ElementQueryDTO";
import {BasePageQueryDTO} from "@/types/dto/queryDTO/BasePageQueryDTO";
import {Pagination} from "@/types/global";
import {useProjectStore} from "@/store";
import {updateSort} from "@/api/MyApi/scene";

// 当前是否全部展开
const isExpandAll = ref(true);

// 目录树引用
const folderTreeRef = ref();

// 弹窗模式
const modalMode = ref('新建');

// 搜索的元素
const searchKey = ref('');

// 当前选择的目录
const selectedFolder = ref<number | null>(0);

// 当前步骤详情
const currentStepDetail = ref(false);

// 添加目录对话框开关
const addFolderVisible = ref(false);


// 添加目录表单
const addOrUpdateFolderForm = ref<Element>(new Element());

// 添加元素对话框开关
const addOrUpdateElementVisible = ref(false);

// 添加元素表单引用
const addElementFormRef = ref();

// 添加元素表单
const addOrUpdateElementForm = ref<Element>(new Element());

// 目录树列表
const folderTree = ref<ElementVO[]>([]);

const projectStore = useProjectStore();

// ===== 左侧目录树：可拖拽伸缩 + 拖拽隐藏 =====
const SIDEBAR_DEFAULT_WIDTH = 240;
const SIDEBAR_MIN_WIDTH = 200;
const SIDEBAR_MAX_WIDTH = 480;
const SIDEBAR_COLLAPSE_THRESHOLD = 20;

const sidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);
const isSidebarCollapsed = ref(false);
const lastSidebarWidth = ref(SIDEBAR_DEFAULT_WIDTH);

const sideColStyle = computed(() => ({
  width: isSidebarCollapsed.value ? '0px' : `${sidebarWidth.value}px`,
  overflow: 'hidden',
}));

const collapseSidebar = () => {
  if (!isSidebarCollapsed.value) {
    lastSidebarWidth.value = Math.max(sidebarWidth.value, SIDEBAR_MIN_WIDTH);
  }
  isSidebarCollapsed.value = true;
  sidebarWidth.value = 0;
};

const expandSidebar = () => {
  isSidebarCollapsed.value = false;
  const expandedWidth = Math.round((lastSidebarWidth.value || SIDEBAR_DEFAULT_WIDTH) * 1.25);
  sidebarWidth.value = Math.min(expandedWidth, SIDEBAR_MAX_WIDTH);
};

const startResizeSidebar = (e: MouseEvent) => {
  const startX = e.clientX;
  const wasCollapsed = isSidebarCollapsed.value;
  const startWidth = wasCollapsed ? 0 : sidebarWidth.value;
  let moved = false;

  const onMove = (ev: MouseEvent) => {
    if (Math.abs(ev.clientX - startX) > 3) moved = true;
    if (wasCollapsed) {
      isSidebarCollapsed.value = false;
    }
    const newWidth = startWidth + ev.clientX - startX;
    sidebarWidth.value = Math.min(Math.max(newWidth, 0), SIDEBAR_MAX_WIDTH);
  };

  const onUp = () => {
    document.removeEventListener('mousemove', onMove);
    document.removeEventListener('mouseup', onUp);
    document.body.style.userSelect = '';

    if (!moved && wasCollapsed) {
      expandSidebar();
      return;
    }

    if (sidebarWidth.value <= SIDEBAR_COLLAPSE_THRESHOLD) {
      collapseSidebar();
    } else if (sidebarWidth.value < SIDEBAR_MIN_WIDTH) {
      sidebarWidth.value = SIDEBAR_MIN_WIDTH;
    }
  };

  document.addEventListener('mousemove', onMove);
  document.addEventListener('mouseup', onUp);
  document.body.style.userSelect = 'none';
};

// 加载目录列表
const reloadFolderTree = async () => {
  const folderList = await getFolderList(projectStore.getProjectId);
  folderTree.value = folderList.data;
}

const pagination = ref<Pagination>({current: 0, total: 0, ...new BasePageQueryDTO()});

// 加载元素列表
const reloadElementList = async () => {
  let elList;
  searchElementForm.value.projectId = projectStore.getProjectId;
  elList = await pageElementList(searchElementForm.value);
  elementList.value = elList.data.records;
  pagination.value.current = elList.data.current;
  pagination.value.pageSize = elList.data.size;
  pagination.value.total = elList.data.total;
}
onMounted(async () => {
  // 获取目录列表
  await reloadFolderTree();

  // 获取元素列表
  await reloadElementList();

  // 初始化的时候，默认展开目录树
  handleFolderTreeExpand();
})

// 搜索元素表单
const searchElementForm = ref<ElementQueryDTO>(new ElementQueryDTO());

// 元素table列定义
const elementColumns = [
  {
    title: '元素名称',
    dataIndex: 'elementName',
  },
  {
    title: '元素描述',
    dataIndex: 'description',
  },
  {
    title: '定位类型',
    dataIndex: 'locatorType',
  },
  {
    title: '定位值',
    dataIndex: 'locatorValue',
  },
  {
    title: '更新时间',
    dataIndex: 'updatedAt',
  },
  {
    title: '操作',
    width: 150,
    slotName: 'operation',
  },
]


// 元素列表
const elementList = ref<Element[]>([]);


// 当前勾选的元素行
const selectRowSelection = ref<string[]>([]);

// 元素列表拖拽
const onDrop = async ({
                        dragNode,
                        dropNode,
                        dropPosition
                      }: { dragNode: TreeNodeData, dropNode: TreeNodeData, dropPosition: number }) => {

  const data: any[] = treeData.value;

  // 如果拖拽节点id是0 or null 就直接返回
  if (!dragNode.id || dragNode.id === 0) {
    Message.warning('根节点无法拖拽');
    return;
  }

  const loop = async (
      data: any[],
      key: number,
      callback: (item: any, index: number, arr: any[]) => void
  ): boolean => {
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

  // 从原位置移除拖拽节点
  loop(data, dragNode.id, (_, index, arr) => {
    arr.splice(index, 1);
  });

  // 根据拖拽位置插入到新位置
  if (dropPosition === 0) {
    // 作为子节点插入
    loop(data, dropNode.id, (item) => {
      item.children = item.children || [];

      // 更新拖拽节点的父节点ID
      dragNode.parentId = item.id;

      // 设置 sort 为子节点数量 + 1（从1开始）
      dragNode.sort = item.children.length + 1;

      item.children.push(dragNode);
    });
  } else {
    // 作为同级节点插入
    loop(data, dropNode.id, (_, index, arr) => {
      const insertIndex = dropPosition < 0 ? index : index + 1;

      // 更新拖拽节点的父节点ID
      dragNode.parentId = dropNode.parentId;

      // 重新计算所有同级节点的 sort（从1开始）
      arr.splice(insertIndex, 0, dragNode);

      // 重新排序所有节点，sort 从1开始递增
      arr.forEach((node, idx) => {
        node.sort = idx + 1;
      });
    });
  }

  const result = await updateElementSort(data[0].children);
  if (result.data === true) {
    Message.success("更新成功");
  } else {
    Message.error({
      content: "更新失败",
      duration: 1000
    })
  }
  await reloadFolderTree(projectStore.getProjectId);
  return;
}

const addFormRef = ref('');

// 添加目录表单校验规则
const addFormRules = {
  parentId: [
    {
      required: true,
      message: '请选择父目录',
    },
  ],
  elementName: [
    {
      required: true,
      message: '请输入目录名称',
    },
  ]
};

// 当前编辑的目录
const currentEditFolder = ref<string>('');

// 添加/编辑目录
const userAddOrUpdateFolder = (mode: string, folder: Element | null) => {
  currentEditFolder.value = folder?.id;
  modalMode.value = mode
  addFolderVisible.value = true;
  if (folder != null) {
    addOrUpdateFolderForm.value = folder;
  } else {
    addOrUpdateFolderForm.value.elementType = 'FOLDER';
  }
}

// 添加子目录
const userAddSubFolder = (parentId: string) => {
  modalMode.value = '新建';
  addFolderVisible.value = true;
  addOrUpdateFolderForm.value.elementType = 'FOLDER';
  addOrUpdateFolderForm.value.parentId = parentId;
}

// 在目录下添加元素
const userAddSubElement = (parentId: string) => {
  addOrUpdateElementVisible.value = true;
  addOrUpdateElementForm.value.elementType = 'ELEMENT';
  addOrUpdateElementForm.value.parentId = parentId;
}


// 取消添加目录
const handleCancel = () => {
  addFolderVisible.value = false;
  // 清除校验状态
  addFormRef.value.clearValidate();
  addOrUpdateFolderForm.value = {};
}

// 确认添加目录
const handleFolderBeforeOk = async () => {
  const error = await addFormRef.value?.validate();
  if (error) {
    return false;
  }

  let result;
  // 判断是否有id，如果有就是更新
  addOrUpdateElementForm.value.projectId = projectStore.getProjectId;
  if (addOrUpdateFolderForm.value.id != null) {
    result = await update(addOrUpdateFolderForm.value);
  } else {
    addOrUpdateFolderForm.value.projectId = projectStore.getProjectId;
    result = await add(addOrUpdateFolderForm.value);
  }

  if (result.data === true) {
    Message.success({
      content: `'${modalMode.value}成功'`,
      duration: 1000
    })
    // 清空添加表单
    addOrUpdateFolderForm.value = {};
    await reloadFolderTree();
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
  return true;
};


// 取消添加元素
const handleElementCancel = () => {
  addOrUpdateElementVisible.value = false;
  // 清除校验状态
  addElementFormRef.value.clearValidate();
  addOrUpdateElementForm.value = {};

}

// 点击添加/编辑元素
const userAddOrUpdateElement = (mode: string, element: Element | undefined) => {
  modalMode.value = mode;
  addOrUpdateElementVisible.value = true;

  if (element != null) {
    addOrUpdateElementForm.value = {...new Element(), ...element}
  } else {
    addOrUpdateElementForm.value = {...new Element(), elementType: 'ELEMENT'}
  }
  addOrUpdateElementForm.value.parentId = selectedFolder.value ? selectedFolder.value : 0;
}

// 添加元素表单校验
const addElementFormRules = {
  parentId: [
    {
      required: true,
      message: '请选择所属目录',
    },
  ],
  elementName: [
    {
      required: true,
      message: '请输入元素名称',
    },
  ],
  locatorType: [
    {
      required: true,
      message: '请选择定位方式',
    },
  ],
  locatorValue: [
    {
      required: true,
      message: '请输入定位值',
    },
  ]
}

// 确认添加元素
const handleElementBeforeOk = async () => {
  const error = await addElementFormRef.value?.validate();
  if (error) {
    return false;
  }

  let result;
  if (addOrUpdateElementForm.value.id != null) {
    // id不为空代表是更新
    result = await update(addOrUpdateElementForm.value);
  } else { // 添加
    addOrUpdateElementForm.value.projectId = projectStore.getProjectId;
    result = await add(addOrUpdateElementForm.value);
  }
  if (result.data === true) {
    Message.success({
      content: `${modalMode.value}成功`,
      duration: 1000
    })
    // 判断当前是否选择了目录，如果有就加载当前目录下的元素，没有就加载根目录
    if (selectedFolder.value != null) {
      await reloadElementList(selectedFolder.value);
    }
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
  return true;
}

// 点击选择目录
const selectFolder = async (data: any) => {
  selectedFolder.value = data[0];
  searchElementForm.value = new ElementQueryDTO();
  searchElementForm.value.parentId = data[0];
  await reloadElementList();
}

// 获取全部元素
const allElement = async () => {
  // 清空当前选择的目录
  selectedFolder.value = null;
  searchElementForm.value = new ElementQueryDTO();
  searchElementForm.value.parentId = null;
  await reloadElementList();
}

// 搜索目录的结果树
const treeData = computed(() => {
  if (!searchKey.value) return folderTree.value;
  return searchData(searchKey.value);
})

// 搜索的目录
const searchData = (keyword: any) => {
  const loop = (data: any) => {
    const result: any = [];
    data.forEach((item: any) => {
      if (item.elementName.indexOf(keyword) > -1) {
        result.push({...item});
      } else if (item.children) {
        const filterData = loop(item.children);
        if (filterData.length) {
          result.push({
            ...item,
            children: filterData
          })
        }
      }
    })
    return result;
  }
  return loop(folderTree.value);
}

// 过滤之后的选择目录
const selectableTreeNode = computed(() => {
  if (modalMode.value == '新建') return folderTree.value;
  if (!currentEditFolder.value) return folderTree.value;
  return filterNodeAndSubNode(currentEditFolder.value);
});

// 过滤节点及其子节点
const filterNodeAndSubNode = (nodeId: string) => {
  const deepCloneAndFilter = (node: any): any | null => {
    // 如果是目标节点，返回null表示过滤掉
    if (node.id === nodeId) return null;

    // 创建新节点（深拷贝）
    const newNode = {...node};

    // 递归处理子节点
    if (node.children?.length) {
      const filteredChildren = node.children
          .map(deepCloneAndFilter)
          .filter((child: any) => child !== null);

      newNode.children = filteredChildren.length ? filteredChildren : undefined;
    }

    return newNode;
  };

  // 处理根节点是目标的情况
  if (folderTree.value[0]?.id === nodeId) return [];

  // 处理普通情况
  const filteredRoot = deepCloneAndFilter(folderTree.value[0]);
  return filteredRoot ? [filteredRoot] : [];
};


// 删除目录 or 元素
const deleteFolderOrElement = async (id: string) => {
  const result = await deleteElementOrFolder(id);
  if (result.data === true) {
    Message.success({
      content: '删除成功',
      duration: 1000
    })
    // 重新加载目录和元素
    await reloadFolderTree();
    await reloadElementList(selectedFolder.value[0]);
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }
}

// 删除目录的确认框
const handleFolderDelete = async (deleteId: string) => {
  Modal.warning({
    title: '确认删除？',
    content: () => '删除后，该目录及子目录、元素将不再可见。',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      await deleteFolderOrElement(deleteId);
    }
  });
};


// 删除元素的确认框
const handleElementDelete = async (deleteId: string) => {
  Modal.warning({
    title: '确认删除？',
    content: () => '确认删除该元素吗？',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      await deleteFolderOrElement(deleteId);
    }
  });
};

// 搜索元素列表
const searchElementList = async () => {
  searchElementForm.value.projectId = projectStore.getProjectId;
  searchElementForm.value.parentId = selectedFolder.value;
  await reloadElementList();
}

// 重置搜索列表
const resetElementList = async () => {
  searchElementForm.value.elementName = '';
  searchElementForm.value.locatorType = '';
  searchElementForm.value.projectId = projectStore.getProjectId;
  searchElementForm.value.parentId = selectedFolder.value;
  await reloadElementList();
}


// 确认批量删除弹窗
const handleBatchDelete = async () => {
  if (selectRowSelection.value.length <= 0) {
    Message.error({
      content: '请选择要删除的元素',
      duration: 1000
    });
    return;
  }
  Modal.warning({
    title: '确认删除？',
    content: () => '确认删除选中的元素吗？',
    cancelText: '取消',
    okText: '确认',
    hideCancel: false,
    onOk: async () => {
      await deleteBatchElement();
    }
  });
}

// 批量删除元素
const deleteBatchElement = async () => {
  // 判断是否是全选
  // 全选，则根据查询条件删除

  const result = await deleteElementBatch(selectRowSelection.value);

  if (result.data === true) {
    Message.success({
      content: '删除成功',
      duration: 1000
    })
    // 清空勾选
    selectRowSelection.value = [];
    // 重新加载目录和元素
    await reloadFolderTree();
    await reloadElementList(selectedFolder.value[0]);
  } else {
    Message.error({
      content: result.msg,
      duration: 1000
    })
  }

}

// 分页数量变化
const pageSizeChange = async (pageSize: number) => {
  searchElementForm.value.pageSize = pageSize;
  await reloadElementList();
}

// 页码发生变化
const pageNumberChange = async (pageNum: number) => {
  searchElementForm.value.pageNum = pageNum;
  await reloadElementList();
}


// 目录树的展开
const handleFolderTreeExpand = () => {
  isExpandAll.value = !isExpandAll.value
  folderTreeRef.value.expandAll(!isExpandAll.value);
}

watch(() => selectedFolder.value, (newVal) => {
  // 切换目录的时候清除已选元素
  selectRowSelection.value = []
}, {deep: true});


watch(
    () => projectStore.getProjectId,
    async (newProjectId, oldProjectId) => {
      if (newProjectId) {
        // 获取目录列表
        await reloadFolderTree();

        // 获取元素列表
        await reloadElementList();

        // 初始化的时候，默认展开目录树
        handleFolderTreeExpand();
      }
    },
    {immediate: true} // 立即执行一次
);

</script>

<style scoped>
.element-page {
  padding: 0 16px 12px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.element-main-row {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.element-col {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.element-side-col {
  flex-shrink: 0;
  transition: none;
}

.element-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.element-card :deep(.arco-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.element-folder-card :deep(.arco-card-body) {
  padding: 12px 0 12px 12px;
}

.element-tree-search-wrap {
  padding: 0 12px;
  margin-bottom: 8px;
}

.element-tree-search {
  width: 100%;
}

.element-tree-actions {
  display: flex;
  width: 100%;
  padding: 0 12px;
}

.element-tree-scroll-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  margin-top: 8px;
}

.element-tree-scroll-wrapper :deep(.arco-scrollbar-container) {
  overflow-x: hidden !important;
  overflow-y: auto !important;
  padding-right: 12px;
}

.element-tree-scroll-wrapper :deep(.arco-tree-node) {
  border-radius: 4px;
}

.element-tree-scroll-wrapper :deep(.arco-tree-node-selected) {
  background-color: var(--color-primary-light-1);
}

.element-tree-scroll-wrapper :deep(.arco-tree-node-selected .arco-tree-node-title) {
  background-color: transparent;
}

.element-tree-scroll-wrapper :deep(.arco-tree-node:hover:not(.arco-tree-node-selected)) {
  background-color: var(--color-fill-1);
}

/* 隐藏 Arco Tree 默认拖拽手柄 */
.element-tree-scroll-wrapper :deep(.arco-tree-node-drag-icon) {
  display: none;
}

.element-tree-scroll-wrapper :deep(.arco-tree-node-title) {
  overflow: hidden !important;
}

.element-tree-scroll-wrapper :deep(.arco-tree-node-title-text) {
  display: flex !important;
  align-items: center;
  flex: 1 !important;
  min-width: 0 !important;
}

.element-node-title-wrap {
  display: flex;
  align-items: center;
  flex: 1;
  max-width: 100%;
  min-width: 0;
  overflow: hidden;
  padding-right: 24px;
}

.element-node-title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 更多操作默认隐藏，悬浮时绝对定位到节点标题右侧 */
.element-tree-scroll-wrapper :deep(.arco-tree-node) .node-extra-actions {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  visibility: hidden;
  z-index: 1;
}

.element-tree-scroll-wrapper :deep(.arco-tree-node:hover) .node-extra-actions {
  visibility: visible;
}

.node-extra-btn {
  padding: 0 4px;
}

.node-extra-icon {
  color: rgb(var(--primary-6));
  font-size: 12px;
}

/* 左侧伸缩分隔条 */
.element-main-resizer {
  flex-shrink: 0;
  width: 6px;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: col-resize;
  background: transparent;
  user-select: none;
}

.element-main-resizer.is-collapsed {
  width: 4px;
  cursor: col-resize;
  background: transparent;
  border-right: 1px solid var(--color-border-2);
  position: relative;
}

.sidebar-expand-btn {
  position: absolute;
  left: 4px;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-left: none;
  border-radius: 0 4px 4px 0;
  cursor: pointer;
  z-index: 10;
}

.sidebar-expand-btn:hover {
  background: var(--color-fill-2);
}

.sidebar-expand-icon {
  color: rgb(var(--primary-6));
  font-size: 14px;
}

.element-main-resizer:hover .resizer-line,
.element-main-resizer:active .resizer-line {
  background: rgb(var(--primary-6));
}

.resizer-line {
  width: 2px;
  height: 24px;
  border-radius: 1px;
  background: var(--color-border-2);
}

.element-right-col {
  display: flex;
  flex-direction: column;
  height: 100%;
  flex: 1;
}

.element-list-card :deep(.arco-card-body) {
  padding: 12px;
}

.element-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}
</style>
