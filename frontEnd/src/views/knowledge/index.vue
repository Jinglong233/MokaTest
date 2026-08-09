<template>
  <div class="knowledge-page" v-if="projectStore.getProjectId">
    <Breadcrumb :items="['menu.knowledge']"/>

    <a-card class="general-card" :bordered="false">
      <!-- 顶部操作区 -->
      <div class="toolbar" style="margin-top: 15px">
        <a-input-search
            v-model="keyword"
            placeholder="搜索文档标题"
            style="width: 260px"
            allow-clear
            @search="loadData"
            @clear="loadData"
        />
        <a-space>
          <a-button v-permission="'knowledge:create'" type="primary" @click="openEditor()">
            <template #icon><icon-plus/></template>
            新建文档
          </a-button>
          <a-upload
              v-permission="'knowledge:create'"
              :auto-upload="false"
              :show-file-list="false"
              accept=".md,.markdown,.txt"
              @change="handleUpload"
          >
            <template #upload-button>
              <a-button>
                <template #icon><icon-upload/></template>
                上传文档
              </a-button>
            </template>
          </a-upload>
        </a-space>
      </div>

      <!-- 文档表格 -->
      <a-table
          :data="list"
          :loading="loading"
          :pagination="false"
          style="margin-top: 12px"
      >
        <template #columns>
          <a-table-column title="标题" :min-width="220">
            <template #cell="{ record }">
              <a-link @click="openEditor(record)">{{ record.title }}</a-link>
            </template>
          </a-table-column>
          <a-table-column title="类型" :width="80">
            <template #cell="{ record }">
              <a-tag size="small">{{ record.docType }}</a-tag>
            </template>
          </a-table-column>
          <a-table-column title="索引状态" :width="110">
            <template #cell="{ record }">
              <a-tag size="small" :color="statusColor(record.indexStatus)">
                <icon-loading v-if="record.indexStatus === 'INDEXING' || record.indexStatus === 'PENDING'"/>
                {{ statusText(record.indexStatus) }}
              </a-tag>
            </template>
          </a-table-column>
          <a-table-column title="分块数" :width="80" align="center">
            <template #cell="{ record }">{{ record.chunkCount }}</template>
          </a-table-column>
          <a-table-column title="AI 引用" :width="90" align="center">
            <template #cell="{ record }">
              <a-tag v-if="record.citeCount > 0" size="small" color="arcoblue">{{ record.citeCount }} 次</a-tag>
              <span v-else style="color: var(--color-text-3)">-</span>
            </template>
          </a-table-column>
          <a-table-column title="更新时间" :width="160">
            <template #cell="{ record }">{{ formatTime(record.updateTime) }}</template>
          </a-table-column>
          <a-table-column title="操作" :width="200" align="center">
            <template #cell="{ record }">
              <a-space>
                <a-tooltip content="分块预览">
                  <a-button size="mini" type="text" @click="openChunks(record)">
                    <template #icon><icon-list/></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip v-if="record.indexStatus === 'FAILED'" content="重建索引">
                  <a-button v-permission="'knowledge:update'" size="mini" type="text" status="warning"
                            @click="handleRebuild(record)">
                    <template #icon><icon-refresh/></template>
                  </a-button>
                </a-tooltip>
                <a-tooltip content="编辑">
                  <a-button v-permission="'knowledge:update'" size="mini" type="text" @click="openEditor(record)">
                    <template #icon><icon-edit/></template>
                  </a-button>
                </a-tooltip>
                <a-popconfirm content="删除该文档及其全部分块？（不影响已生成的用例）" @ok="handleDelete(record)">
                  <a-tooltip content="删除">
                    <a-button v-permission="'knowledge:delete'" size="mini" type="text" status="danger">
                      <template #icon><icon-delete/></template>
                    </a-button>
                  </a-tooltip>
                </a-popconfirm>
              </a-space>
            </template>
          </a-table-column>
        </template>
      </a-table>
      <a-empty v-if="!loading && !list.length" description="暂无文档，新建或上传后 AI 生成用例时将自动检索参考"/>
    </a-card>

    <!-- 分块预览抽屉 -->
    <a-drawer
        v-model:visible="chunksVisible"
        :title="`分块预览 - ${chunksDoc?.title || ''}`"
        :width="640"
        unmount-on-close
    >
      <a-spin :loading="chunksLoading" style="width: 100%">
        <div v-for="chunk in chunks" :key="chunk.id" class="chunk-card">
          <div class="chunk-meta">
            <a-tag size="small">#{{ chunk.chunkIndex + 1 }}</a-tag>
            <span>约 {{ chunk.tokenCount }} tokens</span>
            <a-tag size="small" :color="chunk.hasEmbedding ? 'green' : 'gray'">
              {{ chunk.hasEmbedding ? '已向量化' : '关键词检索' }}
            </a-tag>
          </div>
          <pre class="chunk-text">{{ chunk.chunkText }}</pre>
        </div>
        <a-empty v-if="!chunksLoading && !chunks.length" description="暂无分块（索引可能未完成）"/>
      </a-spin>
    </a-drawer>
  </div>

  <a-result v-else status="warning" title="请先选择项目"/>
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'KnowledgeIndex' };
</script>

<script setup lang="ts">
import {onBeforeUnmount, onMounted, ref} from 'vue';
import {useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import useProjectStore from '@/store/modules/project';
import {
  deleteKnowledgeDoc,
  listKnowledgeChunks,
  listKnowledgeDocs,
  rebuildKnowledgeIndex,
  uploadKnowledgeDoc,
} from '@/api/MyApi/knowledge';

const projectStore = useProjectStore();
const router = useRouter();

const list = ref<any[]>([]);
const loading = ref(false);
const keyword = ref('');
let pollTimer: ReturnType<typeof setInterval> | null = null;

const chunksVisible = ref(false);
const chunksLoading = ref(false);
const chunks = ref<any[]>([]);
const chunksDoc = ref<any>(null);

async function loadData() {
    loading.value = true;
    try {
        const res: any = await listKnowledgeDocs(keyword.value || undefined);
        list.value = res.data || [];
        // 有索引中的文档时轮询刷新状态
        const indexing = list.value.some((d: any) => d.indexStatus === 'INDEXING' || d.indexStatus === 'PENDING');
        if (indexing && !pollTimer) {
            pollTimer = setInterval(loadData, 5000);
        } else if (!indexing && pollTimer) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
    } finally {
        loading.value = false;
    }
}

function statusColor(status: string) {
    return {PENDING: 'gray', INDEXING: 'arcoblue', READY: 'green', FAILED: 'red'}[status] || 'gray';
}

function statusText(status: string) {
    return {PENDING: '待索引', INDEXING: '索引中', READY: '已就绪', FAILED: '失败'}[status] || status;
}

function formatTime(t: any) {
    if (!t) return '-';
    const d = new Date(t);
    const pad = (n: number) => String(n).padStart(2, '0');
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function openEditor(record?: any) {
    if (record?.id) {
        router.push({name: 'KnowledgeEdit', query: {id: String(record.id)}});
    } else {
        router.push({name: 'KnowledgeEdit'});
    }
}

async function handleUpload(_fileList: any, fileItem: any) {
    const raw = fileItem?.file;
    if (!raw) return;
    const formData = new FormData();
    formData.append('file', raw);
    try {
        await uploadKnowledgeDoc(formData);
        Message.success('上传成功，索引构建中');
        loadData();
    } catch { /* 拦截器统一提示 */ }
}

async function handleDelete(record: any) {
    await deleteKnowledgeDoc(record.id);
    Message.success('已删除');
    loadData();
}

async function handleRebuild(record: any) {
    await rebuildKnowledgeIndex(record.id);
    Message.success('已触发重建');
    loadData();
}

async function openChunks(record: any) {
    chunksDoc.value = record;
    chunksVisible.value = true;
    chunksLoading.value = true;
    try {
        const res: any = await listKnowledgeChunks(record.id);
        chunks.value = res.data || [];
    } finally {
        chunksLoading.value = false;
    }
}

onMounted(loadData);
onBeforeUnmount(() => {
    if (pollTimer) clearInterval(pollTimer);
});
</script>

<style scoped>
.knowledge-page {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
  box-sizing: border-box;
  overflow-y: auto;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.chunk-card {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  padding: 8px 10px;
  margin-bottom: 10px;
}

.chunk-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
  margin-bottom: 6px;
}

.chunk-text {
  margin: 0;
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 200px;
  overflow-y: auto;
  color: var(--color-text-2);
}
</style>
