<template>
  <div class="ai-config-page">
    <Breadcrumb :items="['menu.ai', 'menu.aiConfig']" />
    <a-card :bordered="false" class="config-card">
      <div class="page-header">
        <h3 style="margin: 0">AI 配置</h3>
        <a-button type="primary" size="small" @click="editConfig()">
          <template #icon><IconPlus /></template>
          新建配置
        </a-button>
      </div>
      <div class="page-desc">
        为本项目独立配置 AI 模型接入（endpoint/key/模型/thinking）。未配置时该项目的 AI 功能不可用。
      </div>

      <a-spin :loading="loading" style="width: 100%">
        <div v-if="configs.length" class="config-list">
          <div v-for="cfg in configs" :key="cfg.id" class="config-item" :class="{ active: cfg.enabled === 1 }">
            <div class="config-info">
              <span class="config-name">{{ cfg.configName || cfg.chatModel }}</span>
              <a-tag v-if="cfg.enabled === 1" color="green" size="small">生效中</a-tag>
              <span class="config-sub">{{ cfg.chatModel }} · {{ cfg.baseUrl }}</span>
              <span v-if="cfg.embeddingModel" class="config-sub">向量模型：{{ cfg.embeddingModel }}</span>
              <span v-if="cfg.remark" class="config-sub">{{ cfg.remark }}</span>
            </div>
            <a-space size="small">
              <a-button size="mini" @click="editConfig(cfg)">编辑</a-button>
              <a-button v-if="cfg.enabled !== 1" size="mini" type="primary" @click="activateItem(cfg.id)">激活</a-button>
              <a-button v-else size="mini" status="warning" @click="deactivateItem(cfg.id)">停用</a-button>
              <a-popconfirm content="确认删除？" @ok="deleteItem(cfg.id)">
                <a-button size="mini" status="danger" :disabled="cfg.enabled === 1">删除</a-button>
              </a-popconfirm>
            </a-space>
          </div>
        </div>
        <a-empty v-else description="本项目暂无 AI 配置，AI 功能不可用。请新建配置。" :image-size="60" />
      </a-spin>
    </a-card>

    <!-- 编辑抽屉 -->
    <a-drawer :visible="editorVisible" @cancel="editorVisible = false" :width="520" :footer="false" unmount-on-close>
      <template #title>{{ editing?.id ? '编辑 AI 配置' : '新建 AI 配置' }}</template>
      <a-form :model="editing" layout="vertical">
        <a-form-item field="configName" label="配置名称" required>
          <a-input v-model="editing.configName" placeholder="如：项目专用-DeepSeek" />
        </a-form-item>
        <a-form-item field="baseUrl" label="API 地址" required>
          <a-input v-model="editing.baseUrl" placeholder="https://api.openai.com/v1" />
        </a-form-item>
        <a-form-item field="apiKey" label="API Key" :required="!editing?.id">
          <a-input-password v-model="editing.apiKey" :placeholder="editing?.id ? '留空不修改' : '填写 API Key'" />
        </a-form-item>
        <a-form-item field="chatModel" label="对话模型" required>
          <a-input v-model="editing.chatModel" placeholder="如：deepseek-chat" />
        </a-form-item>
        <a-form-item field="embeddingModel" label="向量模型" required>
          <a-input v-model="editing.embeddingModel" placeholder="如：text-embedding-3-small" />
        </a-form-item>
        <a-form-item field="thinkingMode" label="Thinking 模式">
          <a-select v-model="editing.thinkingMode">
            <a-option value="AUTO">自动（按厂商适配）</a-option>
            <a-option value="DISABLED">强制关闭</a-option>
            <a-option value="ENABLED">强制开启</a-option>
          </a-select>
        </a-form-item>
        <a-form-item field="remark" label="备注">
          <a-input v-model="editing.remark" placeholder="配置用途说明（可选）" />
        </a-form-item>
        <a-form-item>
          <a-checkbox v-model="editing.enabled" :true-value="1" :false-value="0">保存后立即激活</a-checkbox>
        </a-form-item>
      </a-form>
      <div style="display: flex; gap: 8px; margin-top: 16px">
        <a-button type="primary" :loading="saving" @click="saveItem">保存</a-button>
        <a-button @click="testItem" :loading="testing">连通性测试</a-button>
      </div>
    </a-drawer>
  </div>
</template>

<script lang="ts">
export default { name: 'AiConfigIndex' };
</script>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { Message } from '@arco-design/web-vue';
import { IconPlus } from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import { useProjectStore } from '@/store';
import { listAiConfigs, saveAiConfig, activateAiConfig, deactivateAiConfig, deleteAiConfig, testAiConfig } from '@/api/MyApi/ai';

const projectStore = useProjectStore();
const configs = ref<any[]>([]);
const loading = ref(false);
const editorVisible = ref(false);
const editing = ref<any>({});
const saving = ref(false);
const testing = ref(false);

async function loadConfigs() {
  loading.value = true;
  try {
    const res: any = await listAiConfigs();
    if (res.code === 200) configs.value = res.data || [];
  } catch { /* ignore */ } finally { loading.value = false; }
}

function editConfig(cfg?: any) {
  if (cfg) {
    editing.value = { ...cfg, apiKey: '' };
  } else {
    editing.value = { thinkingMode: 'AUTO', enabled: 0, projectId: projectStore.projectId };
  }
  editorVisible.value = true;
}

async function saveItem() {
  const e = { ...editing.value, enabled: editing.value.enabled ? 1 : 0 };
  if (!e.configName || !e.baseUrl || !e.chatModel || !e.embeddingModel) { Message.warning('请填写名称、API 地址、对话模型和向量模型'); return; }
  if (!e.id && !e.apiKey) { Message.warning('新建配置请填写 API Key'); return; }
  saving.value = true;
  try {
    await saveAiConfig(e);
    Message.success('保存成功');
    editorVisible.value = false;
    loadConfigs();
  } catch (err: any) { Message.error(err.response?.data?.msg || '保存失败'); }
  finally { saving.value = false; }
}

async function testItem() {
  const e = { ...editing.value, enabled: editing.value.enabled ? 1 : 0 };
  if (!e.baseUrl || !e.chatModel) { Message.warning('请填写 API 地址和对话模型'); return; }
  testing.value = true;
  try {
    const res: any = await testAiConfig(e);
    Message.success(res.data?.msg || '连接成功');
  } catch (err: any) { Message.error(err.response?.data?.msg || '连接失败'); }
  finally { testing.value = false; }
}

async function activateItem(id: number) {
  try { await activateAiConfig(id); Message.success('已激活'); loadConfigs(); }
  catch (err: any) { Message.error(err.response?.data?.msg || '激活失败'); }
}

async function deactivateItem(id: number) {
  try { await deactivateAiConfig(id); Message.success('已停用'); loadConfigs(); }
  catch (err: any) { Message.error(err.response?.data?.msg || '停用失败'); }
}

async function deleteItem(id: number) {
  try { await deleteAiConfig(id); Message.success('已删除'); loadConfigs(); }
  catch (err: any) { Message.error(err.response?.data?.msg || '删除失败'); }
}

onMounted(() => { loadConfigs(); });
</script>

<style scoped>
.ai-config-page { height: var(--page-container-height, calc(100vh - 60px)); display: flex; flex-direction: column; overflow: hidden; }
.config-card { flex: 1; min-height: 0; overflow-y: auto; margin: 15px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 4px; }
.page-desc { font-size: 13px; color: var(--color-text-3); margin-bottom: 16px; }
.config-list { display: flex; flex-direction: column; gap: 8px; }
.config-item { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); }
.config-item.active { border-color: rgb(var(--success-3)); background: rgb(var(--success-1)); }
.config-info { flex: 1; min-width: 0; }
.config-name { font-size: 14px; font-weight: 600; margin-right: 8px; }
.config-sub { font-size: 12px; color: var(--color-text-3); display: block; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
