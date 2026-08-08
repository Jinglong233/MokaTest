<template>
  <div class="ai-config-page">
    <a-card :bordered="false">
      <template #title>AI 模型配置</template>
      <template #extra>
        <a-button type="primary" @click="openEditor()">
          <template #icon>
            <icon-plus />
          </template>
          新建配置
        </a-button>
      </template>

      <a-alert type="info" style="margin-bottom: 16px">
        可保存多套模型接入配置（OpenAI
        兼容端点），但全平台同一时间只有一套「生效中」。 API Key
        加密存储，页面仅显示打码值；生效配置开启后，各页面 AI 功能入口才会开放。
      </a-alert>

      <LoadError v-if="loadError" @retry="loadList" />
      <a-table v-else :data="list" :loading="loading" :pagination="false">
        <template #columns>
          <a-table-column title="生效" :width="80" align="center">
            <template #cell="{ record }">
              <a-switch
                v-model="record.enabled"
                :checked-value="1"
                :unchecked-value="0"
                :loading="activatingId === record.id"
                @change="(v: any) => handleToggleActive(record, v)"
              />
            </template>
          </a-table-column>
          <a-table-column title="配置名" :min-width="140">
            <template #cell="{ record }">
              <a-space>
                <span style="font-weight: 500">{{
                    record.configName || '-'
                  }}</span>
                <a-tag v-if="record.enabled === 1" color="green" size="small"
                >生效中
                </a-tag
                >
              </a-space>
            </template>
          </a-table-column>
          <a-table-column title="服务地址" :min-width="200">
            <template #cell="{ record }">
              <span class="mono">{{ record.baseUrl }}</span>
            </template>
          </a-table-column>
          <a-table-column title="对话模型" :width="150">
            <template #cell="{ record }">{{ record.chatModel }}</template>
          </a-table-column>
          <a-table-column title="向量模型" :width="170">
            <template #cell="{ record }">
              <span v-if="record.embeddingModel">{{
                  record.embeddingModel
                }}</span>
              <span v-else style="color: var(--color-text-3)"
              >未配置（关键词检索）</span
              >
            </template>
          </a-table-column>
          <a-table-column title="备注" :min-width="120">
            <template #cell="{ record }">
              <span style="color: var(--color-text-3)">{{
                  record.remark || '-'
                }}</span>
            </template>
          </a-table-column>
          <a-table-column title="操作" :width="180" align="center">
            <template #cell="{ record }">
              <a-space>
                <a-tooltip content="编辑">
                  <a-button size="mini" type="text" aria-label="编辑配置" @click="openEditor(record)">
                    <template #icon>
                      <icon-edit />
                    </template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="测试连接">
                  <a-button
                    size="mini"
                    type="text"
                    aria-label="测试连接"
                    :loading="testingId === record.id"
                    @click="handleTest(record)"
                  >
                    <template #icon>
                      <icon-thunderbolt />
                    </template>
                  </a-button>
                </a-tooltip>
                <a-popconfirm
                  v-if="record.enabled !== 1"
                  content="删除该配置？"
                  @ok="handleDelete(record)"
                >
                  <a-button size="mini" type="text" status="danger" aria-label="删除配置">
                    <template #icon>
                      <icon-delete />
                    </template>
                  </a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <a-empty
        v-if="!loading && !list.length"
        description="暂无配置，点击右上角「新建配置」接入大模型"
      />
    </a-card>

    <!-- 编辑抽屉 -->
    <a-drawer
      v-model:visible="editorVisible"
      :title="form.id ? '编辑配置' : '新建配置'"
      :width="560"
      :mask-closable="false"
      :esc-to-close="false"
      :closable="false"
      unmount-on-close
    >
      <a-form ref="formRef" :model="form" layout="vertical" @submit-success="handleSave">
        <a-form-item
          label="配置名"
          field="configName"
          extra="便于区分多套配置，如：生产-GPT4o / 测试-DeepSeek；留空取模型名"
        >
          <a-input v-model="form.configName" placeholder="生产-GPT4o" />
        </a-form-item>

        <a-form-item
          label="服务地址"
          field="baseUrl"
          required
          extra="OpenAI 兼容端点，如 https://api.openai.com/v1 或自建网关地址"
          :rules="[{ required: true, message: '请输入服务地址' }]"
        >
          <a-input
            v-model="form.baseUrl"
            placeholder="https://api.openai.com/v1"
          />
        </a-form-item>

        <a-form-item
          label="API Key"
          field="apiKey"
          :extra="form.id ? '留空或保持打码值表示不修改' : '新建配置必填'"
        >
          <a-input-password
            v-model="form.apiKey"
            placeholder="sk-..."
            allow-clear
          />
        </a-form-item>

        <a-form-item
          label="对话模型"
          field="chatModel"
          required
          :rules="[{ required: true, message: '请输入对话模型名' }]"
        >
          <a-input
            v-model="form.chatModel"
            placeholder="如 gpt-4o-mini / qwen-plus / deepseek-chat"
          />
        </a-form-item>

        <a-form-item
          label="向量模型"
          field="embeddingModel"
          extra="可选；用于知识库语义检索，不配置则降级为关键词检索"
        >
          <a-input
            v-model="form.embeddingModel"
            placeholder="如 text-embedding-3-small"
            allow-clear
          />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="最大输出 Tokens" field="maxTokens">
              <a-input-number
                v-model="form.maxTokens"
                :min="256"
                :max="32000"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="请求超时（毫秒）" field="timeoutMs">
              <a-input-number
                v-model="form.timeoutMs"
                :min="5000"
                :max="300000"
                :step="5000"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item label="采样温度" field="temperature">
          <a-slider
            v-model="temperatureValue"
            :min="0"
            :max="1"
            :step="0.1"
            show-input
            style="max-width: 420px"
          />
        </a-form-item>

        <a-form-item
          label="启用多模态（图片理解）"
          field="visionEnabled"
          extra="开启后，需求描述中的图片会一并发送给模型分析；需要模型本身支持视觉能力"
        >
          <a-switch
            :checked="form.visionEnabled === 1"
            @change="(v: any) => form.visionEnabled = v ? 1 : 0"
          />
        </a-form-item>

        <a-form-item label="备注" field="remark">
          <a-input
            v-model="form.remark"
            placeholder="配置用途说明，可空"
            :max-length="200"
          />
        </a-form-item>
      </a-form>
      <template #footer>
        <a-space>
          <a-button @click="handleCancelEditor">取消</a-button>
          <a-button :loading="testingInEditor" @click="handleTestInEditor"
          >测试连接
          </a-button
          >
          <a-button type="primary" :loading="saving" @click="handleSave"
          >保存
          </a-button
          >
        </a-space>
      </template>
    </a-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';
import {
  activateAiConfig,
  deactivateAiConfig,
  deleteAiConfig,
  listAiConfigs,
  saveAiConfig,
  testAiConfig
} from '@/api/MyApi/ai';

const list = ref<any[]>([]);
const { loading, loadError, track } = useLoadState();
const saving = ref(false);
const testingId = ref<number | null>(null);
const activatingId = ref<number | null>(null);
const testingInEditor = ref(false);
const editorVisible = ref(false);
const formRef = ref();
const formSnapshot = ref('');

const blankForm = () => ({
  id: null as number | null,
  configName: '',
  provider: 'openai',
  baseUrl: '',
  apiKey: '',
  chatModel: '',
  embeddingModel: '',
  maxTokens: 4096,
  temperature: 0.3,
  timeoutMs: 60000,
  visionEnabled: 0,
  remark: ''
});

const form = reactive<any>(blankForm());

const temperatureValue = computed<number>({
  get: () => Number(form.temperature ?? 0.3),
  set: (v: number) => {
    form.temperature = v;
  }
});

async function loadList() {
  const res: any = await track(listAiConfigs());
  list.value = res?.data || [];
}

function openEditor(record?: any) {
  Object.assign(
    form,
    blankForm(),
    record ? { ...record, apiKey: record.apiKey || '' } : {}
  );
  // 生效状态不进编辑表单（未传时后端保持原值），生效切换走列表行内开关
  delete form.enabled;
  formSnapshot.value = JSON.stringify(form);
  editorVisible.value = true;
}

// 有未保存修改时关闭需确认（与 projectConfig 的守卫纪律一致）
function handleCancelEditor() {
  if (JSON.stringify(form) !== formSnapshot.value) {
    Modal.confirm({
      title: '放弃修改',
      content: '配置已修改但未保存，确定放弃吗？',
      okText: '放弃',
      cancelText: '继续编辑',
      onOk: () => {
        editorVisible.value = false;
      },
    });
    return;
  }
  editorVisible.value = false;
}

async function handleSave() {
  // 走表单校验（规则在表单项上），不绕过
  const errors = await formRef.value?.validate();
  if (errors) return;
  if (!form.baseUrl?.trim() || !form.chatModel?.trim()) {
    Message.warning('请填写服务地址和对话模型');
    return;
  }
  saving.value = true;
  try {
    await saveAiConfig({ ...form });
    Message.success('保存成功');
    editorVisible.value = false;
    loadList();
  } finally {
    saving.value = false;
  }
}

/**
 * 行内 switch 切换：v 为组件 change 事件抛出的目标值（1=设为生效，0=停用）。
 * 注意必须用事件值判断方向：v-model 在 change 触发前已把 record.enabled 翻成新值，
 * 从 record 状态推断会把开启/关闭搞反。
 */
async function handleToggleActive(record: any, v: any) {
  const turningOn = v === 1 || v === true;
  // 当前行已被 v-model 乐观翻转；开启时同步把其他行置为未生效（唯一生效语义）
  if (turningOn) {
    list.value.forEach((r: any) => {
      if (r.id !== record.id) r.enabled = 0;
    });
  }
  activatingId.value = record.id;
  try {
    if (turningOn) {
      await activateAiConfig(record.id);
      Message.success(
        `已切换生效配置：${record.configName || record.chatModel}`
      );
    } else {
      await deactivateAiConfig(record.id);
      Message.success(
        `已停用 AI 功能：${record.configName || record.chatModel}`
      );
    }
    await loadList();
  } catch {
    await loadList(); // 失败回滚为服务端真实状态
  } finally {
    activatingId.value = null;
  }
}

async function handleDelete(record: any) {
  await deleteAiConfig(record.id);
  Message.success('已删除');
  loadList();
}

async function handleTest(record: any) {
  testingId.value = record.id;
  try {
    const res: any = await testAiConfig({ ...record });
    Message.success(res.msg || '连接成功');
  } catch (e: any) {
    Message.error(e?.response?.data?.msg || e?.message || '连接失败');
  } finally {
    testingId.value = null;
  }
}

async function handleTestInEditor() {
  testingInEditor.value = true;
  try {
    const res: any = await testAiConfig({ ...form });
    Message.success(res.msg || '连接成功');
  } catch (e: any) {
    Message.error(e?.response?.data?.msg || e?.message || '连接失败');
  } finally {
    testingInEditor.value = false;
  }
}

onMounted(loadList);
</script>

<style scoped>
.ai-config-page {
  padding: 12px 16px;
  height: var(--page-container-height, calc(100vh - 60px));
  box-sizing: border-box;
  overflow-y: auto;
}

.mono {
  font-family: monospace;
  font-size: 12px;
  color: var(--color-text-2);
}
</style>
