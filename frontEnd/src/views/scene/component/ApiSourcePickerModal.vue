<template>
  <a-modal
      :visible="visible"
      :title="apiType === 'SQL' ? '引入已有SQL接口' : '引入已有用例'"
      :width="480"
      unmount-on-close
      @cancel="handleCancel"
  >
    <a-spin :loading="treeLoading" style="width: 100%">
      <a-tree-select
          v-model="selectedId"
          :data="treeData"
          :field-names="{ key: 'id', title: 'apiName', children: 'children' }"
          :allow-clear="true"
          :allow-search="true"
          :tree-check-strictly="apiType !== 'SQL'"
          :placeholder="apiType === 'SQL' ? '仅可选择 SQL 接口' : '请选择用例（接口不可选）'"
          style="width: 100%"
      />
    </a-spin>
    <template #footer>
      <a-button @click="handleCancel">取消</a-button>
      <a-button type="primary" :disabled="!selectedId" :loading="confirmLoading" @click="handleConfirm">
        确定
      </a-button>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue';
import {Message} from '@arco-design/web-vue';
import {getApiById, getApiTreeList, getInterfaceCaseTree} from '@/api/MyApi/apiInterface';
import {useProjectStore} from '@/store';
import {ApiFolderTreeVO} from '@/types/domain/api/vo/ApiFolderTreeVO';
import {ApiType} from '@/types/domain/api/apiEnum/ApiType';

/**
 * 引入已有接口/用例选择器（HTTP 用接口-用例树，SQL 用 SQL 接口过滤树）。
 * 确认后通过 select 事件回传接口/用例完整详情，由调用方决定如何生成步骤副本。
 */
const props = withDefaults(defineProps<{
  visible: boolean;
  apiType: 'HTTP' | 'SQL';
}>(), {});

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'select', apiDetail: any): void;
}>();

const projectStore = useProjectStore();

const treeLoading = ref(false);
const confirmLoading = ref(false);
const treeData = ref<any[]>([]);
const selectedId = ref<number | undefined>(undefined);

// HTTP：接口-用例关系树，接口节点禁用（不可选），用例节点可选。
// 仅保留 HTTP 接口（apiType 为空视为历史 HTTP 数据）；SQL 接口无用例，不出现在此树中
const processInterfaceCaseTree = (nodes: ApiFolderTreeVO[]): any[] => {
  return nodes
      .filter(node => !node.apiType || node.apiType === ApiType.HTTP)
      .map(node => {
        // 后端数据中，接口节点有 children 字段（可能为空数组），用例节点没有 children
        // 使用 Array.isArray 判断，避免空数组的接口被误判为可选项
        const isInterface = Array.isArray(node.children);
        const processed: any = {
          ...node,
          disabled: isInterface,
          selectable: !isInterface,
        };
        if (isInterface && node.children) {
          processed.children = processInterfaceCaseTree(node.children);
        }
        return processed;
      });
};

// SQL：仅保留「含 SQL 接口的文件夹 + SQL 接口」。
// 注意：后端 buildTree 会给所有节点（含接口）塞 children: []，
// 必须用 apiNode 判断文件夹，用 apiType 判断 SQL 接口，不能靠 children 是否为数组
const filterSqlTree = (nodes: any[]): any[] => {
  const result: any[] = [];
  for (const node of nodes) {
    if (node.apiNode === 'FOLDER') {
      const children = filterSqlTree(node.children || []);
      if (children.length > 0) {
        // 文件夹仅作分组展示，不可选
        result.push({...node, children, disabled: true, selectable: false});
      }
    } else if (node.apiType === ApiType.SQL) {
      // 去掉空的 children，避免被 tree-select 当成可展开的父节点
      const {children, ...rest} = node;
      result.push({...rest});
    }
  }
  return result;
};

const loadTree = async () => {
  treeLoading.value = true;
  try {
    if (props.apiType === 'SQL') {
      const res = await getApiTreeList(projectStore.getProjectId as number);
      treeData.value = filterSqlTree(res.data || []);
    } else {
      const res = await getInterfaceCaseTree(projectStore.getProjectId as number);
      treeData.value = processInterfaceCaseTree(res.data || []);
    }
  } catch (e) {
    console.error('加载接口列表失败', e);
    Message.error({content: '加载接口列表失败', duration: 2000});
  } finally {
    treeLoading.value = false;
  }
};

watch(() => props.visible, (val) => {
  if (val) {
    selectedId.value = undefined;
    loadTree();
  }
});

const handleCancel = () => {
  emit('update:visible', false);
};

const handleConfirm = async () => {
  if (!selectedId.value) {
    Message.warning({content: props.apiType === 'SQL' ? '请先选择一个 SQL 接口' : '请先选择一个用例（接口不可选）', duration: 2000});
    return;
  }
  confirmLoading.value = true;
  try {
    const {data} = await getApiById(selectedId.value);
    if (data) {
      emit('select', data);
      emit('update:visible', false);
    } else {
      Message.error({content: '获取详情失败（可能已被删除）', duration: 2000});
    }
  } catch (e: any) {
    Message.error({content: '获取详情失败：' + (e.message || '未知错误'), duration: 2000});
  } finally {
    confirmLoading.value = false;
  }
};
</script>
