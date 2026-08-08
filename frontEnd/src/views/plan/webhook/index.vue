<template>
  <div class="container" style="height: 100%">
    <Breadcrumb :items="['menu.testRun', 'menu.testRun.webhook']" />
    <a-space direction="vertical" :size="12" fill>
      <a-card title="Webhook 通知配置">
        <template #extra>
          <a-button v-permission="'auto:plan:webhook:create'" type="primary" @click="openEdit()">
            <template #icon>
              <icon-plus />
            </template>
            新增配置
          </a-button>
        </template>

        <a-table
            row-key="id"
            :columns="columns"
            :data="webhookList"
            :loading="loading"
            :pagination="false"
        >
          <template #enabled="{ record }">
            <a-tag v-if="record.enabled" color="green">启用</a-tag>
            <a-tag v-else color="gray">禁用</a-tag>
          </template>
          <template #type="{ record }">
            <a-tag :color="typeColorMap[record.type]">{{ typeLabelMap[record.type] }}</a-tag>
          </template>
          <template #url="{ record }">
            <span
                :title="record.url"
                style="max-width: 300px; display: inline-block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;"
            >
              {{ record.url }}
            </span>
          </template>
          <template #operations="{ record }">
            <a-space>
              <a-button v-permission="'auto:plan:webhook:update'" type="primary" size="small" @click="handleTest(record)">
                测试
              </a-button>
              <a-button v-permission="'auto:plan:webhook:update'" type="primary" size="small" @click="openEdit(record)">
                编辑
              </a-button>
              <a-popconfirm
                  content="确认删除该Webhook配置吗？"
                  type="warning"
                  @ok="handleDelete(record.id)"
              >
                <a-button v-permission="'auto:plan:webhook:delete'" type="primary" status="danger" size="small">
                  删除
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table>
      </a-card>
    </a-space>

    <!-- 新增/编辑弹窗 -->
    <a-modal
        v-model:visible="editVisible"
        :title="editForm.id ? '编辑 Webhook 配置' : '新增 Webhook 配置'"
        @before-ok="handleEditBeforeOk"
        @cancel="editVisible = false"
    >
      <a-form
          ref="editFormRef"
          :model="editForm"
          layout="vertical"
          :rules="editRules"
      >
        <a-form-item field="name" label="配置名称" required>
          <a-input
              v-model="editForm.name"
              placeholder="如：钉钉-测试群"
          />
        </a-form-item>

        <a-form-item field="type" label="平台类型" required>
          <a-select v-model="editForm.type" placeholder="请选择平台类型">
            <a-option value="DINGTALK">钉钉</a-option>
            <a-option value="WECHAT">企业微信</a-option>
            <a-option value="FEISHU">飞书</a-option>
            <a-option value="CUSTOM">自定义 URL</a-option>
          </a-select>
        </a-form-item>

        <a-form-item field="url" label="Webhook URL" required>
          <a-input
              v-model="editForm.url"
              placeholder="请输入 Webhook 地址"
          />
        </a-form-item>

        <a-form-item field="secret" label="签名密钥">
          <a-input-password
              v-model="editForm.secret"
              placeholder="开启加签安全设置时填写（选填）"
          />
        </a-form-item>

        <a-form-item field="notifyOnArray" label="触发时机" required>
          <a-checkbox-group v-model="editForm.notifyOnArray">
            <a-checkbox value="SUCCESS">执行成功</a-checkbox>
            <a-checkbox value="FAILURE">执行失败</a-checkbox>
          </a-checkbox-group>
        </a-form-item>

        <a-form-item
            v-if="editForm.type === 'DINGTALK' || editForm.type === 'WECHAT'"
            field="atMobiles"
            label="@指定人手机号"
        >
          <a-input
              v-model="editForm.atMobiles"
              placeholder="多个手机号用逗号分隔，如：13800138000,13900139000"
          />
        </a-form-item>

        <a-form-item field="enabled" label="是否启用">
          <a-switch
              v-model="editForm.enabled"
              checked-text="启用"
              unchecked-text="禁用"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, reactive, watch } from 'vue';
import { useProjectStore } from '@/store';
import { Message } from '@arco-design/web-vue';
import {
  listPlanWebhooks,
  savePlanWebhook,
  updatePlanWebhook,
  deletePlanWebhook,
  testPlanWebhook,
} from '@/api/MyApi/planWebhook';
import { PlanWebhook } from '@/types/domain/PlanWebhook';

const projectStore = useProjectStore();
const loading = ref(false);
const webhookList = ref<PlanWebhook[]>([]);

const editVisible = ref(false);
const editFormRef = ref();

/**
 * 编辑表单数据
 * notifyOnArray 为前端展示用的数组，提交时转为逗号分隔字符串
 */
const editForm = reactive<{
  id?: number;
  projectId?: number;
  name: string;
  enabled: boolean;
  type: string;
  url: string;
  secret: string;
  notifyOnArray: string[];
  atMobiles: string;
}>({
  name: '',
  enabled: true,
  type: 'DINGTALK',
  url: '',
  secret: '',
  notifyOnArray: ['SUCCESS', 'FAILURE'],
  atMobiles: '',
});

/**
 * 平台类型颜色映射
 */
const typeColorMap: Record<string, string> = {
  DINGTALK: 'arcoblue',
  WECHAT: 'green',
  FEISHU: 'orange',
  CUSTOM: 'gray',
};

/**
 * 平台类型中文映射
 */
const typeLabelMap: Record<string, string> = {
  DINGTALK: '钉钉',
  WECHAT: '企业微信',
  FEISHU: '飞书',
  CUSTOM: '自定义',
};

/**
 * 表格列定义
 */
const columns = [
  { title: '配置名称', dataIndex: 'name' },
  { title: '平台类型', slotName: 'type' },
  { title: 'Webhook URL', slotName: 'url' },
  { title: '启用状态', slotName: 'enabled', width: 100 },
  { title: '操作', slotName: 'operations', width: 240 },
];

/**
 * 表单校验规则
 */
const editRules = {
  name: [{ required: true, message: '配置名称不能为空' }],
  type: [{ required: true, message: '请选择平台类型' }],
  url: [
    { required: true, message: 'Webhook URL 不能为空' },
    {
      validator: (value: string, callback: any) => {
        if (value && !value.startsWith('http')) {
          callback('URL 必须以 http 或 https 开头');
        } else {
          callback();
        }
      },
    },
  ],
  notifyOnArray: [{ required: true, message: '请至少选择一种触发时机' }],
};

/**
 * 加载列表数据
 */
const loadList = async () => {
  loading.value = true;
  try {
    const res = await listPlanWebhooks(projectStore.getProjectId);
    webhookList.value = res.data || [];
  } catch (e) {
    console.error('加载 Webhook 配置失败', e);
    Message.error('加载失败');
  } finally {
    loading.value = false;
  }
};

/**
 * 打开编辑弹窗
 * @param record 编辑时的记录，为空表示新增
 */
const openEdit = (record?: PlanWebhook) => {
  editVisible.value = true;
  // 重置表单
  Object.assign(editForm, {
    id: undefined,
    projectId: projectStore.getProjectId,
    name: '',
    enabled: true,
    type: 'DINGTALK',
    url: '',
    secret: '',
    notifyOnArray: ['SUCCESS', 'FAILURE'],
    atMobiles: '',
  });
  // 如果是编辑，回填数据
  if (record) {
    Object.assign(editForm, {
      id: record.id,
      projectId: record.projectId,
      name: record.name,
      enabled: record.enabled,
      type: record.type,
      url: record.url,
      secret: record.secret,
      atMobiles: record.atMobiles || '',
      // notifyOn 是逗号分隔字符串，转为数组
      notifyOnArray: record.notifyOn ? record.notifyOn.split(',') : ['SUCCESS', 'FAILURE'],
    });
  }
  // 清除校验状态
  editFormRef.value?.clearValidate?.();
};

/**
 * 弹窗确认前的校验与提交
 */
const handleEditBeforeOk = async () => {
  const error = await editFormRef.value?.validate?.();
  if (error) {
    return false;
  }

  // 构造提交对象
  const payload: PlanWebhook = {
    id: editForm.id,
    projectId: editForm.projectId || projectStore.getProjectId,
    name: editForm.name,
    enabled: editForm.enabled,
    type: editForm.type,
    url: editForm.url.trim(),
    secret: editForm.secret?.trim() || undefined,
    // notifyOnArray 转为逗号分隔字符串
    notifyOn: editForm.notifyOnArray.join(','),
    atMobiles: editForm.atMobiles?.trim() || undefined,
  };

  try {
    const isEdit = !!editForm.id;
    const res = isEdit
        ? await updatePlanWebhook(payload)
        : await savePlanWebhook(payload);

    if (res.data && (res.data === true || res.data.data === true)) {
      Message.success(isEdit ? '更新成功' : '新增成功');
      await loadList();
      return true;
    } else {
      Message.error(res.data?.msg || '操作失败');
      return false;
    }
  } catch (e: any) {
    Message.error(e?.response?.data?.msg || '操作失败');
    return false;
  }
};

/**
 * 删除配置
 */
const handleDelete = async (id: number) => {
  try {
    const res = await deletePlanWebhook(id);
    if (res.data && (res.data === true || res.data.data === true)) {
      Message.success('删除成功');
      await loadList();
    } else {
      Message.error('删除失败');
    }
  } catch (e: any) {
    Message.error(e?.response?.data?.msg || '删除失败');
  }
};

/**
 * 测试发送
 */
const handleTest = async (record: PlanWebhook) => {
  const loadingMsg = Message.loading({ content: '发送中...', duration: 0 });
  try {
    const res = await testPlanWebhook(record);
    loadingMsg.close();
    if (res.data && (res.data === true || res.data.data === true)) {
      Message.success('测试消息发送成功，请检查对应群消息');
    } else {
      Message.error(res.data?.msg || '发送失败，请检查 URL 和密钥');
    }
  } catch (e: any) {
    loadingMsg.close();
    Message.error(e?.response?.data?.msg || '发送失败，请检查 URL 和密钥');
  }
};

/**
 * 监听 notifyOnArray 变化，确保至少保留一个选项
 * 若全部取消，自动重置为默认值，避免提交空数组
 */
watch(
    () => editForm.notifyOnArray,
    (val) => {
      if (!val || val.length === 0) {
        editForm.notifyOnArray = ['SUCCESS', 'FAILURE'];
      }
    }
);

onMounted(() => {
  loadList();
});
</script>

<style scoped>
.container {
  padding: 16px;
}
</style>
