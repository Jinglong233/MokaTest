<template>
  <div class="knowledge-edit-page">
    <Breadcrumb :items="['menu.knowledge', docId ? '编辑文档' : '新建文档']"/>

    <a-card class="general-card" :bordered="false" :loading="loading">
      <!-- 标题 + 操作 -->
      <div class="header-row" style="margin-top: 15px">
        <a-input
            v-model="form.title"
            placeholder="文档标题，如：支付系统设计说明"
            :max-length="200"
            show-word-limit
            class="title-input"
        />
        <a-space>
          <a-button @click="goBack">返回</a-button>
          <a-button type="primary" :loading="saving" :disabled="!form.title.trim()" @click="handleSave">
            保存并索引
          </a-button>
        </a-space>
      </div>

      <a-alert v-if="!docId" type="info" style="margin-bottom: 10px">
        保存后自动分块并建立索引；AI 生成用例时将检索最相关的片段作为参考依据。
      </a-alert>

      <RichEditor
          v-model="form.content"
          placeholder="编写文档内容（设计说明、业务规则、接口约定等）"
          height="calc(100vh - 320px)"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import RichEditor from '@/components/rich-editor/index.vue';
import {getKnowledgeDoc, saveKnowledgeDoc} from '@/api/MyApi/knowledge';

const route = useRoute();
const router = useRouter();

const docId = ref<number | null>(route.query.id ? Number(route.query.id) : null);
const loading = ref(false);
const saving = ref(false);
const form = ref<any>({title: '', docType: 'MD', content: ''});

async function loadDetail() {
    if (!docId.value) return;
    loading.value = true;
    try {
        const res: any = await getKnowledgeDoc(docId.value);
        const doc = res.data || {};
        form.value = {title: doc.title || '', docType: doc.docType || 'MD', content: doc.content || ''};
    } finally {
        loading.value = false;
    }
}

async function handleSave() {
    saving.value = true;
    try {
        const res: any = await saveKnowledgeDoc({
            id: docId.value || undefined,
            title: form.value.title,
            docType: 'MD',
            content: form.value.content,
        });
        Message.success('已保存，索引构建中');
        if (!docId.value && res.data?.id) {
            docId.value = res.data.id;
            router.replace({query: {id: String(docId.value)}});
        }
        goBack();
    } finally {
        saving.value = false;
    }
}

function goBack() {
    router.push({name: 'KnowledgeIndex'});
}

onMounted(loadDetail);
</script>

<style scoped>
.knowledge-edit-page {
  padding: 12px 16px;
  display: flex;
  flex-direction: column;
  height: var(--page-container-height, calc(100vh - 60px));
  box-sizing: border-box;
}

.knowledge-edit-page :deep(.general-card) {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
}

.knowledge-edit-page :deep(.general-card .arco-card-body) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}

.header-row {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
}

.title-input {
  flex: 1;
  font-size: 16px;
}
</style>
