<template>
  <div class="data-template-page" v-if="projectStore.getProjectId">
    <Breadcrumb :items="['menu.interfaceTest', 'menu.interfaceTest.dataTemplate']"/>
    <div class="main-row">
      <!-- 左侧模板分类树 -->
      <div class="side-col" :style="sideColStyle">
        <a-card class="tree-card" size="small">
          <template #title>
            <span>模板分类</span>
          </template>

          <div class="tree-search-wrap">
            <a-input-search
                v-model="searchKeyword"
                placeholder="搜索模板/文件夹"
                allow-clear
                class="tree-search"
            />
          </div>

          <a-button-group class="tree-actions">
            <a-tooltip content="新建文件夹" style="flex: 1">
              <a-button v-permission="'auto:template:folder:create'" @click="handleAddFolder(0)" style="width: 100%">
                <template #icon>
                  <icon-folder-add/>
                </template>
              </a-button>
            </a-tooltip>
            <a-tooltip content="新建模板" style="flex: 1">
              <a-button v-permission="'auto:template:create'" @click="handleAddTemplate(selectedFolderId)" style="width: 100%">
                <template #icon>
                  <icon-plus/>
                </template>
              </a-button>
            </a-tooltip>
            <a-tooltip :content="isExpandAll ? '折叠全部' : '展开全部'" style="flex: 1">
              <a-button @click="handleToggleExpand" style="width: 100%">
                <template #icon>
                  <icon-menu-fold v-if="isExpandAll"/>
                  <icon-menu-unfold v-else/>
                </template>
              </a-button>
            </a-tooltip>
          </a-button-group>

          <div class="tree-scroll-wrapper">
            <a-scrollbar
                :disable-horizontal="true"
                :outer-style="{ height: '100%' }"
                style="height: 100%; overflow-x: hidden; overflow-y: auto;"
            >
              <a-tree
                  ref="treeRef"
                  :data="treeDataForArco"
                  :fieldNames="{ key: 'id', title: 'name' }"
                  :selected-keys="selectedKeys"
                  draggable
                  default-expand-all
                  block-node
                  style="margin-top: 10px; padding-right: 0"
                  @select="handleTreeSelect"
                  @drop="handleTreeDrop"
                  @drag-start="isDragging = true"
                  @drag-end="isDragging = false"
              >
                <template #drag-icon></template>
                <template #title="nodeData">
                  <div class="tree-node-content">
                    <icon-folder v-if="nodeData.type === 'FOLDER'" style="font-size: 16px; flex-shrink: 0;"/>
                    <icon-file v-else style="color: rgb(var(--primary-6)); font-size: 16px; flex-shrink: 0;"/>
                    <a-tooltip
                      :content="nodeData.name"
                      position="br"
                      :disabled="isDragging"
                      v-model:popup-visible="tooltipVisibleMap[nodeData.id]"
                    >
                      <span class="node-name" v-html="highlightText(nodeData.name, searchKeyword)"/>
                    </a-tooltip>
                  </div>
                </template>

                <template #extra="nodeData">
                  <span class="node-extra-actions" v-if="nodeData.id !== 0">
                    <a-dropdown position="bottom">
                      <a-button type="text" size="mini" class="node-extra-btn" @click.stop>
                        <template #icon>
                          <icon-more class="node-extra-icon"/>
                        </template>
                      </a-button>
                      <template #content v-if="nodeData.type === 'FOLDER'">
                        <a-doption v-permission="'auto:template:folder:create'"
                                   @click.stop="handleAddFolder(nodeData.id)"
                        >{{ nodeData.id === 0 ? '新建文件夹' : '新建子文件夹' }}
                        </a-doption>
                        <a-doption v-permission="'auto:template:create'"
                                   @click.stop="handleAddTemplate(nodeData.id)"
                        >新建模板
                        </a-doption>
                        <a-doption v-permission="'auto:template:folder:update'"
                                   @click.stop="handleEditFolder(nodeData)"
                                   v-if="nodeData.id !== 0"
                        >编辑
                        </a-doption>
                        <a-doption v-permission="'auto:template:folder:delete'"
                                   @click.stop="handleDeleteFolder(nodeData.id)"
                                   v-if="nodeData.id !== 0"
                        >删除
                        </a-doption>
                      </template>
                      <template #content v-else>
                        <a-doption v-permission="'auto:template:update'"
                                   @click.stop="handleSelectTemplate(nodeData)"
                        >编辑
                        </a-doption>
                        <a-doption v-permission="'auto:template:create'"
                                   @click.stop="handleCopyTemplate(nodeData.id)"
                        >复制
                        </a-doption>
                        <a-doption v-permission="'auto:template:delete'"
                                   @click.stop="handleDeleteTemplate(nodeData.id)"
                        >删除
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
          class="main-resizer"
          :class="{ 'is-collapsed': isSidebarCollapsed }"
          @mousedown="startResizeSidebar"
      >
        <div v-if="isSidebarCollapsed" class="sidebar-expand-btn">
          <icon-right class="sidebar-expand-icon"/>
        </div>
        <span v-else class="resizer-line"/>
      </div>

      <!-- 右侧内联编辑区 -->
      <div class="content-col">
        <a-card class="editor-card" :title="editorTitle">
          <template #extra>
            <a-space v-if="selectedTemplate">
              <a-button type="primary" @click="handleSave">保存</a-button>
              <a-button @click="handleCancel">取消</a-button>
            </a-space>
          </template>

          <div v-if="!selectedTemplate" class="editor-empty">
            <a-empty :description="selectedFolder ? '已选择文件夹，可点击模板进行编辑' : '请选择或新建一个模板'"/>
          </div>

          <div v-else class="editor-form">
            <a-form :model="form" layout="vertical" class="editor-form-top">
              <a-row :gutter="24">
                <a-col :span="12">
                  <a-form-item label="模板名称" required>
                    <a-input
                        v-model="form.templateName"
                        placeholder="请输入模板名称"
                        :max-length="100"
                        show-word-limit
                    />
                  </a-form-item>
                </a-col>
                <a-col :span="12">
                  <a-form-item label="所属文件夹">
                    <a-tree-select
                        v-model="form.parentId"
                        :data="folderTreeSelectData"
                        placeholder="请选择所属文件夹"
                        :fieldNames="{ key: 'id', title: 'name' }"
                        allow-clear
                    />
                  </a-form-item>
                </a-col>
              </a-row>

              <a-row :gutter="24">
                <a-col :span="12">
                  <a-form-item label="继承自">
                    <a-select
                        v-model="form.extendsId"
                        placeholder="不继承（可选）"
                        allow-clear
                    >
                      <a-option
                          v-for="tmpl in extendableTemplateList"
                          :key="tmpl.id"
                          :value="tmpl.id"
                      >
                        {{ tmpl.templateName }}
                      </a-option>
                    </a-select>
                  </a-form-item>
                </a-col>
              </a-row>

              <a-form-item label="描述">
                <a-textarea
                    v-model="form.description"
                    placeholder="请输入描述"
                    :max-length="500"
                    show-word-limit
                />
              </a-form-item>
            </a-form>

            <a-split
                v-model:size="splitSize"
                direction="vertical"
                :min="0.35"
                :max="0.75"
                class="editor-split"
            >
              <template #first>
                <div class="field-rule-pane">
                  <div class="field-rule-label">
                    <span class="field-rule-label-text">字段规则</span>
                    <span class="field-rule-label-required">*</span>
                  </div>
                  <div class="field-rule-toolbar">
                    <a-space size="mini">
                      <a-button type="text" size="mini" @click="handleExportSchema">
                        <template #icon>
                          <icon-export/>
                        </template>
                        导出规则
                      </a-button>
                      <a-button type="text" size="mini" @click="handleImportSchemaClick">
                        <template #icon>
                          <icon-import/>
                        </template>
                        导入规则
                      </a-button>
                      <input
                          ref="schemaFileInput"
                          type="file"
                          accept=".json"
                          style="display: none;"
                          @change="handleSchemaFileChange"
                      />
                    </a-space>
                  </div>
                  <div class="field-rule-list">
                    <MockFieldTreeNode
                        v-model="form.templateSchema"
                        :template-list="templateList"
                        :exclude-template-id="form.id"
                        @change="onDataChange"
                    />
                  </div>
                </div>
              </template>

              <template #second>
                <div class="preview-section">
                  <div class="preview-section-title">
                    <span style="font-size: 14px; font-weight: 600; color: rgb(var(--primary-6));">生成预览</span>
                  </div>
                  <a-tabs v-if="form.id" type="card" size="small">
                    <a-tab-pane key="single" title="单条预览">
                      <div class="preview-tab-content">
                        <a-button
                            type="primary"
                            :loading="previewLoading"
                            @click="handleGeneratePreview"
                        >
                          <template #icon>
                            <icon-play-arrow/>
                          </template>
                          生成预览
                        </a-button>
                        <MonacoViewer
                            v-if="previewResult !== null"
                            class="preview-monaco"
                            :content="JSON.stringify(previewResult, null, 2)"
                            lang="json"
                        />
                        <div v-else class="preview-placeholder">点击「生成预览」查看数据</div>
                      </div>
                    </a-tab-pane>
                    <a-tab-pane key="batch" title="批量生成">
                      <div class="preview-tab-content">
                        <a-space>
                          <a-input-number v-model="batchCount" :min="1" :max="1000" style="width: 120px"/>
                          <a-button
                              type="primary"
                              :disabled="!form.id"
                              :loading="batchLoading"
                              @click="handleBatchGenerate"
                          >
                            批量生成
                          </a-button>
                          <a-button
                              :disabled="!batchResult || batchResult.length === 0"
                              @click="handleExportBatch"
                          >
                            <template #icon>
                              <icon-export/>
                            </template>
                            导出 JSON
                          </a-button>
                          <a-button
                              :disabled="!batchResult || batchResult.length === 0"
                              @click="handleExportBatchCSV"
                          >
                            <template #icon>
                              <icon-export/>
                            </template>
                            导出 CSV
                          </a-button>
                          <a-button
                              :disabled="!batchResult || batchResult.length === 0"
                              :loading="excelExportLoading"
                              @click="handleExportBatchExcel"
                          >
                            <template #icon>
                              <icon-export/>
                            </template>
                            导出 Excel
                          </a-button>
                        </a-space>
                        <MonacoViewer
                            v-if="batchResult !== null"
                            class="preview-monaco"
                            :content="JSON.stringify(batchResult, null, 2)"
                            lang="json"
                        />
                        <div v-else class="preview-placeholder">输入数量后点击批量生成</div>
                      </div>
                    </a-tab-pane>
                  </a-tabs>
                  <div v-else class="preview-placeholder">保存模板后可在此生成预览数据</div>
                </div>
              </template>
            </a-split>
          </div>
        </a-card>
      </div>
    </div>

    <!-- 文件夹编辑弹窗 -->
    <a-modal
        v-model:visible="folderModalVisible"
        :title="folderModalTitle"
        width="400px"
        :mask-closable="false"
        @ok="handleSaveFolder"
        @cancel="folderModalVisible = false"
    >
      <a-form :model="folderForm" layout="vertical">
        <a-form-item label="文件夹名称" required>
          <a-input
              v-model="folderForm.templateName"
              placeholder="请输入文件夹名称"
              :max-length="100"
              show-word-limit
          />
        </a-form-item>
        <a-form-item label="父文件夹">
          <a-tree-select
              v-model="folderForm.parentId"
              :data="folderTreeSelectData"
              placeholder="请选择父文件夹"
              :fieldNames="{ key: 'id', title: 'name' }"
              allow-clear
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
  <NoProjectPlaceholder v-else/>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'DataTemplate' };
</script>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch} from 'vue';
import {onBeforeRouteLeave} from 'vue-router';
import {Message, Modal} from '@arco-design/web-vue';
import {
  IconExport,
  IconFile,
  IconFolder,
  IconFolderAdd,
  IconImport,
  IconMenuFold,
  IconMenuUnfold,
  IconMore,
  IconPlayArrow,
  IconPlus,
  IconRight,
} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import MockFieldTreeNode from '@/views/apiManager/component/MockFieldTreeNode.vue';
import MonacoViewer from '@/views/apiManager/component/MonacoViewer.vue';
import {DataTemplate} from '@/types/domain/api/DataTemplate';
import {MockFieldRule} from '@/types/domain/api/requestModel/MockFieldRule';
import {DataTemplateTreeNode} from '@/types/vo/DataTemplateTreeVO';
import {
  batchGenerateData,
  copyDataTemplate,
  deleteDataTemplate,
  deleteDataTemplateFolder,
  exportBatchData,
  generateData,
  getDataTemplateDetail,
  getDataTemplateFolderList,
  getDataTemplateList,
  getDataTemplateTree,
  saveDataTemplate,
  saveDataTemplateFolder,
  updateDataTemplateSort,
} from '@/api/MyApi/dataTemplate';
import useProjectStore from '@/store/modules/project';
import useTeamStore from '@/store/modules/team';

const projectStore = useProjectStore();
const teamStore = useTeamStore();

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

const treeRef = ref<any>(null);
const treeData = ref<DataTemplateTreeNode[]>([{id: 0, name: '根目录', nodeType: 'FOLDER', parentId: 0, children: []}]);
const searchKeyword = ref('');
const selectedKeys = ref<number[]>([]);
const selectedTemplate = ref<DataTemplateTreeNode | null>(null);
const selectedFolder = ref<DataTemplateTreeNode | null>(null);
const loading = ref(false);
const isExpandAll = ref(true);
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

// 文件夹弹窗
const folderModalVisible = ref(false);
const folderModalTitle = ref('新建文件夹');
const folderForm = ref<Partial<DataTemplate>>({
  templateName: '',
  parentId: 0,
  nodeType: 'FOLDER',
  projectId: undefined,
});
const isEditFolder = ref(false);

// 内联预览
const previewResult = ref<any>(null);
const previewLoading = ref(false);
const batchCount = ref(10);
const batchResult = ref<any[] | null>(null);
const batchLoading = ref(false);
const excelExportLoading = ref(false);

// 可被其他模板引用的数据模板列表（用于 Mock 值选择"数据模板"时填充下拉）
const templateList = ref<DataTemplate[]>([]);

// 「继承自」下拉选项：排除当前正在编辑的模板自身，避免自引用
const extendableTemplateList = computed(() =>
  templateList.value.filter((t) => t.id !== form.id)
);

// 右侧表单
const form = reactive<DataTemplate>({
  id: undefined,
  nodeType: 'TEMPLATE',
  templateName: '',
  description: '',
  parentId: 0,
  templateSchema: new MockFieldRule(true),
  isShared: 1,
  sort: 0,
  extendsId: undefined,
});

// 未保存快照
const originalFormSnapshot = ref('');
const schemaFileInput = ref<HTMLInputElement | null>(null);
// 分隔栏大小记忆（持久化到 localStorage）
const splitSize = ref(Number(localStorage.getItem('dataTemplateSplitSize')) || 0.55);

const editorTitle = computed(() => {
  if (!selectedTemplate.value) return '模板详情';
  return form.id ? '编辑模板' : '新建模板';
});

const selectedFolderId = computed(() => {
  if (selectedFolder.value) return selectedFolder.value.id;
  if (selectedTemplate.value?.parentId) return selectedTemplate.value.parentId;
  return 0;
});

const hasUnsavedChanges = computed(() => {
  if (!selectedTemplate.value) return false;
  const current = JSON.stringify({
    templateName: form.templateName,
    description: form.description,
    parentId: form.parentId,
    templateSchema: form.templateSchema,
    extendsId: form.extendsId,
  });
  return current !== originalFormSnapshot.value;
});

const saveSnapshot = () => {
  originalFormSnapshot.value = JSON.stringify({
    templateName: form.templateName,
    description: form.description,
    parentId: form.parentId,
    templateSchema: form.templateSchema,
    extendsId: form.extendsId,
  });
};

const confirmDiscard = async (): Promise<boolean> => {
  if (!hasUnsavedChanges.value) return true;
  return new Promise((resolve) => {
    Modal.confirm({
      title: '未保存的更改',
      content: '当前模板有未保存的更改，是否放弃？',
      okText: '放弃',
      cancelText: '留在当前页',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
};

const beforeUnloadHandler = (e: BeforeUnloadEvent) => {
  if (hasUnsavedChanges.value) {
    e.preventDefault();
    e.returnValue = '';
  }
};

// SPA 内路由跳转守卫：有未保存更改时弹确认，确认则放行，取消则留在本页
onBeforeRouteLeave(async (to, from, next) => {
  if (!hasUnsavedChanges.value) {
    next();
    return;
  }
  const discard = await confirmDiscard();
  next(discard);
});

const escapeHtml = (text: string): string => {
  return text
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;');
};

const highlightText = (text: string, keyword: string): string => {
  if (!keyword || !text) return escapeHtml(text || '');
  const safeKeyword = escapeHtml(keyword.trim());
  const safeText = escapeHtml(text);
  if (!safeKeyword) return safeText;
  const regex = new RegExp(`(${safeKeyword.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')})`, 'gi');
  return safeText.replace(regex, '<span class="tree-highlight">$1</span>');
};

const filteredTreeData = computed(() => {
  if (!searchKeyword.value) return treeData.value;
  return filterTree(treeData.value, searchKeyword.value.trim());
});

// 给 a-tree 的数据：把 nodeType 重命名为 type，避免 Arco Tree 把 nodeType 作为 DOM 属性绑定到 div 上
const treeDataForArco = computed(() => transformNodeType(filteredTreeData.value));

const transformNodeType = (nodes: DataTemplateTreeNode[]): any[] => {
  return nodes.map(node => {
    // 排除 nodeType，避免 Arco Tree 把它作为 DOM 属性绑定到 div 上（nodeType 是 DOM 只读属性）
    const { nodeType, ...rest } = node;
    return {
      ...rest,
      type: nodeType,
      children: node.children ? transformNodeType(node.children) : undefined
    };
  });
};

// 兼容 a-tree 转换后的数据（type）和原始 treeData（nodeType）
const getNodeType = (node: any): string | undefined => node?.type || node?.nodeType;

const filterTree = (nodes: DataTemplateTreeNode[], keyword: string): DataTemplateTreeNode[] => {
  const result: DataTemplateTreeNode[] = [];
  for (const node of nodes) {
    const matchName = node.name.toLowerCase().includes(keyword.toLowerCase());
    let children: DataTemplateTreeNode[] | undefined;
    if (node.children && node.children.length > 0) {
      children = filterTree(node.children, keyword);
    }
    if (matchName || (children && children.length > 0)) {
      result.push({
        ...node,
        children: children && children.length > 0 ? children : undefined,
      });
    }
  }
  return result;
};

const folderTreeSelectData = computed(() => {
  const build = (nodes: DataTemplateTreeNode[]): any[] => {
    return nodes
        .filter((n) => n.nodeType === 'FOLDER')
        .map((n) => {
          const { nodeType, ...rest } = n;
          return {
            ...rest,
            type: nodeType,
            children: n.children ? build(n.children) : undefined,
          };
        });
  };
  return build(treeData.value);
});

const loadTree = async () => {
  const projectId = projectStore.getProjectId;
  if (!projectId) return;
  loading.value = true;
  try {
    const res: any = await getDataTemplateTree(projectId);
    treeData.value = res.data || [{id: 0, name: '全部模板', nodeType: 'FOLDER', parentId: 0, children: []}];
    // 数据加载后，如果当前是展开状态，主动展开全部节点
    // （default-expand-all 只在初始化时生效，异步加载的数据不会自动展开）
    await nextTick();
    if (isExpandAll.value && treeRef.value) {
      treeRef.value.expandAll(true);
    }
  } catch (e) {
    Message.error('加载模板树失败');
  } finally {
    loading.value = false;
  }
};

// 加载当前项目下所有数据模板，用于 Mock 值选择"数据模板"时的下拉选项
const loadTemplates = async () => {
  const projectId = projectStore.getProjectId;
  if (!projectId) return;
  try {
    const { data } = await getDataTemplateList(projectId);
    templateList.value = data || [];
  } catch (e) {
    // 静默失败，模板选择器显示为空
  }
};

const handleTreeSelect = async (keys: (string | number)[], {node}: any) => {
  const key = Number(keys[0]);
  if (hasUnsavedChanges.value) {
    const discard = await confirmDiscard();
    if (!discard) return;
  }
  selectedKeys.value = [key];
  if (node?.type === 'TEMPLATE') {
    selectedFolder.value = null;
    handleSelectTemplate(node as DataTemplateTreeNode);
  } else {
    selectedTemplate.value = null;
    selectedFolder.value = node as DataTemplateTreeNode;
    saveSnapshot();
  }
};

const handleSelectTemplate = async (node: DataTemplateTreeNode) => {
  selectedTemplate.value = node;
  const nodeType = (node as any).type || node.nodeType;
  if (!node || nodeType !== 'TEMPLATE') return;
  try {
    const res: any = await getDataTemplateDetail(node.id);
    const data: DataTemplate = res.data;
    resetForm(data);
  } catch (e) {
    Message.error('加载模板详情失败');
  }
};

const createDefaultField = (fieldName: string, fieldType: MockFieldRule['fieldType'], ruleType: string, extra: Partial<MockFieldRule> = {}): MockFieldRule => {
  const rule = new MockFieldRule();
  rule.fieldName = fieldName;
  rule.fieldType = fieldType;
  rule.ruleType = ruleType;
  Object.assign(rule, extra);
  return rule;
};

const handleAddTemplate = async (parentFolderId: number) => {
  if (hasUnsavedChanges.value) {
    const discard = await confirmDiscard();
    if (!discard) return;
  }
  selectedKeys.value = [];
  selectedFolder.value = null;
  const root = new MockFieldRule(true);
  root.children = [
    createDefaultField('id', 'STRING', 'uuid'),
    createDefaultField('name', 'STRING', 'cname', {locale: 'zh'}),
  ];
  const parentId = parentFolderId || 0;
  selectedTemplate.value = {
    id: 0,
    name: '',
    parentId,
    nodeType: 'TEMPLATE',
  };
  resetForm({
    nodeType: 'TEMPLATE',
    parentId,
    templateSchema: root,
  });
};

const resetForm = (data?: DataTemplate) => {
  form.id = data?.id;
  form.nodeType = data?.nodeType || 'TEMPLATE';
  form.templateName = data?.templateName || '';
  form.description = data?.description || '';
  form.parentId = data?.parentId ?? 0;
  // 新建模板默认共享（1）；编辑已有模板时保留后端返回的原值，避免覆盖用户设置
  form.isShared = data?.isShared ?? 1;
  form.sort = data?.sort ?? 0;
  form.extendsId = data?.extendsId ?? undefined;
  form.templateSchema = migrateSchema(data?.templateSchema);
  // 切换/新建模板时清空历史预览数据，避免上一个模板的结果残留
  previewResult.value = null;
  batchResult.value = null;
  nextTick(() => {
    saveSnapshot();
  });
};

function migrateSchema(schema: any): MockFieldRule {
  if (!schema) {
    return new MockFieldRule(true);
  }
  if (Array.isArray(schema)) {
    const root = new MockFieldRule(true);
    root.children = schema.map((r: any) => {
      const rule = new MockFieldRule();
      Object.assign(rule, r);
      if (r.type) {
        rule.ruleType = r.type;
        rule.fieldType = mapOldType(r.type);
      }
      if (!rule.fieldType) rule.fieldType = 'STRING';
      rule.nullable = !!rule.nullable;
      rule.required = rule.required !== false;
      return rule;
    });
    return root;
  }
  const root = new MockFieldRule(true);
  Object.assign(root, schema);
  if (!root.children) root.children = [];
  return root;
}

function mapOldType(type: string): MockFieldRule['fieldType'] {
  if (!type) return 'STRING';
  switch (type.trim().toLowerCase()) {
    case 'int':
      return 'INT';
    case 'float':
      return 'FLOAT';
    default:
      return 'STRING';
  }
}

const onDataChange = () => {
  // 空方法，用于触发响应式
};

const handleSave = async (showTip = true): Promise<boolean> => {
  const projectId = projectStore.getProjectId;
  const teamId = teamStore.getTeamId;
  if (!projectId || !teamId) {
    Message.warning('请先选择项目和团队');
    return false;
  }
  const payload: DataTemplate = {
    ...JSON.parse(JSON.stringify(form)),
    projectId,
    teamId,
    nodeType: 'TEMPLATE',
  };
  try {
    const res: any = await saveDataTemplate(payload);
    if (showTip) {
      Message.success('保存成功');
    }
    await loadTree();
    await loadTemplates();
    if (res.data?.id) {
      selectedKeys.value = [res.data.id];
      if (!form.id) {
        // 新建保存：重新拉详情，拿全后端字段（id/sort/审计字段等）
        await handleSelectTemplate({
          id: res.data.id,
          name: payload.templateName || '',
          parentId: payload.parentId || 0,
          nodeType: 'TEMPLATE',
        });
      } else {
        // 更新保存：不重新拉详情，避免 schema 重建导致字段规则树 _key 全部换新、展开状态丢失
        saveSnapshot();
      }
    }
    return true;
  } catch (e: any) {
    const msg = e?.response?.data?.msg || e?.message || '保存失败';
    Message.error(msg);
    return false;
  }
};

const handleCancel = async () => {
  if (form.id && selectedTemplate.value) {
    await handleSelectTemplate(selectedTemplate.value);
  } else {
    selectedTemplate.value = null;
    selectedKeys.value = [];
  }
};

const handleCopyTemplate = async (id: number) => {
  try {
    const res: any = await copyDataTemplate(id);
    Message.success('复制成功');
    await loadTree();
    await loadTemplates();
    if (res.data?.id) {
      selectedKeys.value = [res.data.id];
      await handleSelectTemplate({
        id: res.data.id,
        name: res.data.templateName || '',
        parentId: res.data.parentId || 0,
        nodeType: 'TEMPLATE',
      });
    }
  } catch (e: any) {
    const msg = e?.response?.data?.msg || e?.message || '复制失败';
    Message.error(msg);
  }
};

const handleDeleteTemplate = async (id: number) => {
  Modal.confirm({
    title: '确认删除',
    content: '确定删除该模板吗？删除后可在回收站恢复（如支持）。',
    onOk: async () => {
      try {
        await deleteDataTemplate(id);
        Message.success('删除成功');
        if (form.id === id) {
          selectedTemplate.value = null;
          selectedKeys.value = [];
        }
        await loadTree();
        await loadTemplates();
      } catch (e: any) {
        const msg = e?.response?.data?.msg || e?.message || '删除失败';
        Message.error(msg);
      }
    },
  });
};

const handleDeleteFolder = async (id: number) => {
  Modal.confirm({
    title: '确认删除文件夹',
    content: '删除文件夹将同时删除其下的所有子文件夹和模板，确定继续吗？',
    onOk: async () => {
      try {
        await deleteDataTemplateFolder(id);
        Message.success('删除成功');
        if (selectedFolder.value?.id === id) {
          selectedFolder.value = null;
          selectedKeys.value = [];
        }
        if (form.parentId === id) {
          form.parentId = 0;
        }
        await loadTree();
      } catch (e: any) {
        const msg = e?.response?.data?.msg || e?.message || '删除失败';
        Message.error(msg);
      }
    },
  });
};

const handleGeneratePreview = async () => {
  if (!form.id) return;
  // 预览前自动保存，确保后端生成的是最新 schema
  const saved = await handleSave(false);
  if (!saved) return;
  previewLoading.value = true;
  try {
    const {data} = await generateData(form.id);
    previewResult.value = data;
  } catch (e) {
    Message.error('生成预览失败');
  } finally {
    previewLoading.value = false;
  }
};

const handleBatchGenerate = async () => {
  if (!form.id) return;
  // 批量生成前自动保存，确保后端生成的是最新 schema
  const saved = await handleSave(false);
  if (!saved) return;
  batchLoading.value = true;
  try {
    const {data} = await batchGenerateData(form.id, batchCount.value);
    batchResult.value = data || [];
  } catch (e) {
    Message.error('批量生成失败');
  } finally {
    batchLoading.value = false;
  }
};

const handleExportBatch = () => {
  if (!batchResult.value) {
    Message.warning('请先生成数据');
    return;
  }
  const blob = new Blob([JSON.stringify(batchResult.value, null, 2)], {type: 'application/json'});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `data-template-${form.id}-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
};

const escapeCsvValue = (value: any): string => {
  if (value === null || value === undefined) return '';
  let text = typeof value === 'object' ? JSON.stringify(value) : String(value);
  if (text.includes(',') || text.includes('"') || text.includes('\n') || text.includes('\r')) {
    return '"' + text.replace(/"/g, '""') + '"';
  }
  return text;
};

const handleExportBatchCSV = () => {
  if (!batchResult.value || batchResult.value.length === 0) {
    Message.warning('请先生成数据');
    return;
  }
  const data = batchResult.value;
  const keys = Object.keys(data[0]);
  const header = keys.join(',');
  const rows = data.map((row) => keys.map((k) => escapeCsvValue(row[k])).join(','));
  const csv = [header, ...rows].join('\n');
  const blob = new Blob(['﻿' + csv], {type: 'text/csv;charset=utf-8'});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `data-template-${form.id}-${Date.now()}.csv`;
  a.click();
  URL.revokeObjectURL(url);
};

const handleExportBatchExcel = async () => {
  if (!form.id || !batchResult.value || batchResult.value.length === 0) {
    Message.warning('请先生成数据');
    return;
  }
  excelExportLoading.value = true;
  try {
    const res: any = await exportBatchData(form.id, batchResult.value.length, 'EXCEL');
    const blobData = res.data || res;
    const blob = blobData instanceof Blob ? blobData : new Blob([blobData], {type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'});
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `data-template-${form.id}-${Date.now()}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
    Message.success('导出成功');
  } catch (e: any) {
    const msg = e?.response?.data?.msg || e?.message || '导出失败';
    Message.error(msg);
  } finally {
    excelExportLoading.value = false;
  }
};

const handleExportSchema = () => {
  const schema = form.templateSchema;
  if (!schema || !schema.children || schema.children.length === 0) {
    Message.warning('当前没有字段规则可导出');
    return;
  }
  const blob = new Blob([JSON.stringify(schema, null, 2)], {type: 'application/json'});
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `data-template-schema-${form.id || 'new'}-${Date.now()}.json`;
  a.click();
  URL.revokeObjectURL(url);
};

const handleImportSchemaClick = () => {
  schemaFileInput.value?.click();
};

const handleSchemaFileChange = (e: Event) => {
  const target = e.target as HTMLInputElement;
  const file = target.files?.[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = (event) => {
    try {
      const text = String(event.target?.result || '');
      const parsed = JSON.parse(text);
      form.templateSchema = migrateSchema(parsed);
      Message.success('导入成功');
    } catch (err) {
      Message.error('JSON 解析失败，请检查文件格式');
    } finally {
      if (schemaFileInput.value) {
        schemaFileInput.value.value = '';
      }
    }
  };
  reader.onerror = () => {
    Message.error('读取文件失败');
    if (schemaFileInput.value) {
      schemaFileInput.value.value = '';
    }
  };
  reader.readAsText(file);
};

// 文件夹管理
const handleAddFolder = (parentId: number) => {
  isEditFolder.value = false;
  folderModalTitle.value = '新建文件夹';
  folderForm.value = {
    templateName: '',
    parentId: parentId || 0,
    nodeType: 'FOLDER',
    projectId: projectStore.getProjectId,
  };
  folderModalVisible.value = true;
};

const handleEditFolder = (node: DataTemplateTreeNode) => {
  isEditFolder.value = true;
  folderModalTitle.value = '编辑文件夹';
  folderForm.value = {
    id: node.id,
    templateName: node.name,
    parentId: node.parentId || 0,
    nodeType: 'FOLDER',
    projectId: projectStore.getProjectId,
  };
  folderModalVisible.value = true;
};

const handleSaveFolder = async () => {
  if (!folderForm.value.templateName || folderForm.value.templateName.trim() === '') {
    Message.warning('请输入文件夹名称');
    return;
  }
  const projectId = projectStore.getProjectId;
  const teamId = teamStore.getTeamId;
  if (!projectId || !teamId) {
    Message.warning('请先选择项目和团队');
    return;
  }
  const payload: DataTemplate = {
    ...JSON.parse(JSON.stringify(folderForm.value)),
    projectId,
    teamId,
    nodeType: 'FOLDER',
  };
  try {
    await saveDataTemplateFolder(payload);
    Message.success('保存成功');
    folderModalVisible.value = false;
    await loadTree();
  } catch (e: any) {
    const msg = e?.response?.data?.msg || e?.message || '保存失败';
    Message.error(msg);
  }
};

// 拖拽排序/移动
const handleTreeDrop = async ({dragNode, dropNode, dropPosition}: any) => {
  const dragId = Number(dragNode.id);
  const dropId = Number(dropNode.id);

  if (dragId === dropId || dragId === 0) return;

  // 构造新树副本
  const newTree: DataTemplateTreeNode[] = JSON.parse(JSON.stringify(treeData.value[0]?.children || []));

  // 从原位置移除
  let dragItem: DataTemplateTreeNode | null = null;
  const removeNode = (nodes: DataTemplateTreeNode[]): boolean => {
    for (let i = 0; i < nodes.length; i++) {
      if (nodes[i].id === dragId) {
        dragItem = nodes.splice(i, 1)[0];
        return true;
      }
      if (nodes[i].children && removeNode(nodes[i].children as DataTemplateTreeNode[])) {
        return true;
      }
    }
    return false;
  };
  removeNode(newTree);
  if (!dragItem) {
    await loadTree();
    return;
  }

  // 插入新位置
  if (dropPosition === 0) {
    // 拖入目标内部
    if (getNodeType(dropNode) !== 'FOLDER') {
      Message.warning('只能拖入文件夹内部');
      await loadTree();
      return;
    }
    const insertInto = (nodes: DataTemplateTreeNode[]): boolean => {
      for (const node of nodes) {
        if (node.id === dropId) {
          if (!node.children) node.children = [];
          dragItem!.parentId = dropId;
          node.children.push(dragItem!);
          return true;
        }
        if (node.children && insertInto(node.children)) {
          return true;
        }
      }
      return false;
    };
    insertInto(newTree);
  } else {
    // 同级排序/跨父移动
    const insertSibling = (nodes: DataTemplateTreeNode[]): boolean => {
      for (let i = 0; i < nodes.length; i++) {
        if (nodes[i].id === dropId) {
          dragItem!.parentId = nodes[i].parentId;
          const insertIndex = dropPosition < 0 ? i : i + 1;
          nodes.splice(insertIndex, 0, dragItem!);
          return true;
        }
        if (nodes[i].children && insertSibling(nodes[i].children as DataTemplateTreeNode[])) {
          return true;
        }
      }
      return false;
    };
    insertSibling(newTree);
  }

  // 防止文件夹拖入自身后代
  if (getNodeType(dragItem) === 'FOLDER' && isDescendantInTree(dragItem.id, dropId, newTree)) {
    Message.warning('不能将文件夹移动到自身或其子文件夹内');
    await loadTree();
    return;
  }

  // 重新计算 sort
  normalizeSort(newTree, 0);

  try {
    const res: any = await updateDataTemplateSort(newTree);
    if (res.code === 200) {
      Message.success('排序已保存');
      await loadTree();
    }
  } catch (e: any) {
    const msg = e?.response?.data?.msg || e?.message || '排序失败';
    Message.error(msg);
    // 服务端保存失败，恢复树视图，避免视觉位置与服务端数据不一致
    await loadTree();
  }
};

const normalizeSort = (nodes: DataTemplateTreeNode[], parentId: number) => {
  nodes.forEach((node, index) => {
    node.parentId = parentId;
    node.sort = index;
    if (node.children && node.children.length > 0) {
      normalizeSort(node.children, node.id);
    }
  });
};

const isDescendantInTree = (ancestorId: number, targetId: number, nodes: DataTemplateTreeNode[]): boolean => {
  if (ancestorId === targetId) return true;
  const find = (list: DataTemplateTreeNode[], id: number): DataTemplateTreeNode | null => {
    for (const node of list) {
      if (node.id === id) return node;
      if (node.children) {
        const found = find(node.children, id);
        if (found) return found;
      }
    }
    return null;
  };
  const target = find(nodes, targetId);
  if (!target || target.parentId === 0) return false;
  if (target.parentId === ancestorId) return true;
  return isDescendantInTree(ancestorId, target.parentId, nodes);
};

const handleToggleExpand = () => {
  isExpandAll.value = !isExpandAll.value;
  nextTick(() => {
    if (treeRef.value) {
      treeRef.value.expandAll(isExpandAll.value);
    }
  });
};

onMounted(() => {
  loadTree();
  loadTemplates();
  window.addEventListener('beforeunload', beforeUnloadHandler);
  window.addEventListener('keydown', handleKeydown);
});

onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnloadHandler);
  window.removeEventListener('keydown', handleKeydown);
});

watch(
    () => projectStore.getProjectId,
    async (newId, oldId) => {
      if (newId) {
        if (oldId !== undefined && oldId !== newId && hasUnsavedChanges.value) {
          const discard = await confirmDiscard();
          if (!discard) return;
        }
        selectedKeys.value = [];
        selectedTemplate.value = null;
        selectedFolder.value = null;
        await loadTree();
        await loadTemplates();
      }
    },
    {immediate: true}
);

// 持久化分隔栏大小
watch(splitSize, (v) => {
  localStorage.setItem('dataTemplateSplitSize', String(v));
});

// Ctrl/Cmd + S 保存
const handleKeydown = (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'S')) {
    if (selectedTemplate.value) {
      e.preventDefault();
      handleSave();
    }
  }
};
</script>

<style scoped lang="less">
.data-template-page {
  padding: 0 16px 12px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
}

.main-row {
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.side-col {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  flex-shrink: 0;
  transition: none;
}

.content-col {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  flex: 1;
}

.tree-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.tree-card :deep(.arco-card-body) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  padding: 12px 0 12px 12px;
}

.tree-search-wrap {
  padding: 0 12px;
  margin-bottom: 8px;
}

.tree-search {
  width: 100%;
}

.tree-actions {
  display: flex;
  width: 100%;
  padding: 0 12px;
  margin-bottom: 8px;
}

.tree-scroll-wrapper {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.tree-scroll-wrapper :deep(.arco-scrollbar-container) {
  overflow-x: hidden !important;
  overflow-y: auto !important;
  padding-right: 12px;
}

.tree-scroll-wrapper :deep(.arco-tree-node) {
  border-radius: 4px;
}

.tree-scroll-wrapper :deep(.arco-tree-node-selected) {
  background-color: var(--color-primary-light-1);
}

.tree-scroll-wrapper :deep(.arco-tree-node-selected .arco-tree-node-title) {
  background-color: transparent;
}

.tree-scroll-wrapper :deep(.arco-tree-node:hover:not(.arco-tree-node-selected)) {
  background-color: var(--color-fill-1);
}

/* 隐藏 Arco Tree 默认拖拽手柄（IconDragDotVertical），保留整行拖拽能力 */
.tree-scroll-wrapper :deep(.arco-tree-node-drag-icon) {
  display: none;
}

/* 让标题文本区和更多操作在同一行自适应。
   保留 min-width:0 使 flex 子项可收缩；不设置 overflow:hidden，
   否则 Arco Tree 拖拽落点指示线（::before 在标题上下沿外侧）会被裁掉。
   给标题加 1px 上下外边距，使相邻节点的上下指示线重合，避免切换 target 时闪烁。 */
.tree-scroll-wrapper :deep(.arco-tree-node-title) {
  min-width: 0;
  margin: 1px 0;
}

.tree-scroll-wrapper :deep(.arco-tree-node-title-text) {
  display: flex !important;
  align-items: center;
  flex: 1 !important;
  min-width: 0 !important;
}

.tree-node-content {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
  max-width: 100%;
  overflow: hidden;
  padding-right: 24px;
}

.node-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}

/* a-tooltip 内部会套一层 display:inline-block 的 .arco-trigger-wrapper，
   不处理会破坏 .tree-node-content 的 flex 布局，导致省略失效 / tooltip 定位异常。
   让该 wrapper 参与 flex 并占满剩余宽度。 */
.tree-node-content :deep(.arco-trigger-wrapper) {
  flex: 1;
  min-width: 0;
  display: flex;
}

/* 更多操作默认隐藏，悬浮时绝对定位到节点标题右侧 */
.tree-scroll-wrapper :deep(.arco-tree-node) .node-extra-actions {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  visibility: hidden;
  z-index: 1;
}

.tree-scroll-wrapper :deep(.arco-tree-node:hover) .node-extra-actions {
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
.main-resizer {
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

.main-resizer.is-collapsed {
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

.main-resizer:hover .resizer-line,
.main-resizer:active .resizer-line {
  background: rgb(var(--primary-6));
}

.resizer-line {
  width: 2px;
  height: 24px;
  border-radius: 1px;
  background: var(--color-border-2);
}

.tree-highlight {
  background-color: #fffb8f;
  color: #333;
  border-radius: 2px;
  padding: 0 2px;
}

.editor-card {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.editor-card :deep(.arco-card-body) {
  flex: 1;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.editor-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.editor-form {
  flex: 1;
  min-height: 0;
  padding-right: 8px;
  display: flex;
  flex-direction: column;
}

/* 上方表单区域：自然高度，不参与滚动 */
.editor-form-top {
  flex-shrink: 0;
}

.editor-split {
  flex: 1;
  min-height: 0;
  margin-top: 8px;
  border: none !important;
  background: transparent !important;
}

/* split 的 pane 必须限制高度 + 隐藏溢出，内部各自处理滚动 */
.editor-split :deep(.arco-split-pane) {
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.editor-split :deep(.arco-split-trigger) {
  background: transparent;
}

/* 字段规则面板：label + toolbar + list(滚动) */
.field-rule-pane {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.field-rule-label {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-bottom: 4px;
  font-size: 14px;
  color: var(--color-text-1);
  flex-shrink: 0;
}

.field-rule-label-required {
  color: var(--color-danger);
}

.field-rule-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
  padding: 4px 8px;
  border: 1px solid var(--color-border-2);
  border-bottom: none;
  border-radius: 4px 4px 0 0;
  background: var(--color-fill-1);
  flex-shrink: 0;
}

.field-rule-list {
  flex: 1;
  min-height: 0;
  width: 100%;
  box-sizing: border-box;
  overflow-y: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 0 0 4px 4px;
  padding: 0;
}

.preview-section {
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  .preview-section-title {
    padding: 8px 0;
    margin-bottom: 4px;
    flex-shrink: 0;
  }

  :deep(.arco-tabs) {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }

  :deep(.arco-tabs-nav) {
    flex-shrink: 0;
  }

  :deep(.arco-tabs-content) {
    flex: 1;
    min-height: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }

  :deep(.arco-tabs-content-list) {
    flex: 1;
    min-height: 0;
  }

  :deep(.arco-tabs-content-item) {
    height: 100%;
  }

  :deep(.arco-tabs-pane) {
    height: 100%;
  }

  .preview-tab-content {
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 8px;
    padding: 8px;
    box-sizing: border-box;
  }

  .preview-monaco {
    flex: 1;
    min-height: 200px;
    border: 1px solid var(--color-border-2);
    border-radius: 4px;
    overflow: hidden;
  }

  .preview-placeholder {
    flex: 1;
    min-height: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--color-text-3);
    padding: 24px;
    text-align: center;
    background: var(--color-fill-1);
    border-radius: 4px;
    border: 1px dashed var(--color-border-2);
  }
}
</style>
