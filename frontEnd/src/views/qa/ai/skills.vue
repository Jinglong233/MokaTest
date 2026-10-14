<template>
  <div class="ai-skills-page">
    <Breadcrumb :items="['menu.ai', 'menu.qa.aiSkillsMgmt']" />

    <a-card :bordered="false">
      <div class="toolbar">
        <h3 style="margin: 0">{{ $t('qa.ai.skills.title') }}</h3>
        <a-button type="primary" @click="openEditor()">
          <template #icon><icon-plus /></template>
          {{ $t('qa.ai.skills.add') }}
        </a-button>
      </div>

      <a-spin :loading="defLoading" style="width: 100%">
        <div v-if="definitions.length" class="skill-grid">
          <div v-for="item in definitions" :key="item.id" class="skill-card" :class="{ disabled: item.enabled !== 1 }">
            <div class="skill-card-header">
              <div class="skill-name" @click="openEditor(item)">
                {{ item.name }}
              </div>
              <a-switch :model-value="item.enabled === 1" size="small" @change="handleToggle(item.id)" />
            </div>
            <div class="skill-desc">{{ item.description || $t('qa.ai.skills.noDesc') }}</div>
            <div class="skill-card-footer">
              <span class="skill-time">{{ item.updateTime?.slice(0, 10) }}</span>
              <a-space size="small">
                <a-button size="mini" @click="openEditor(item)">{{ $t('qa.ai.common.edit') }}</a-button>
                <a-popconfirm :content="$t('qa.ai.skills.deleteConfirm')" @ok="handleDelete(item.id)" position="tr">
                  <a-button size="mini" status="danger">{{ $t('qa.common.delete') }}</a-button>
                </a-popconfirm>
              </a-space>
            </div>
          </div>
        </div>
        <a-empty v-else :description="$t('qa.ai.skills.empty')" style="padding: 60px 0" />
      </a-spin>
    </a-card>

    <!-- 编辑弹窗 -->
    <a-modal v-model:visible="editorVisible" :title="editing?.id ? $t('qa.ai.skills.edit') : $t('qa.ai.skills.add')" :width="600" @ok="handleSave">
      <a-form :model="editing" layout="vertical">
        <a-form-item field="name" :label="$t('qa.ai.skills.name')" required>
          <a-input v-model="editing.name" :placeholder="$t('qa.ai.skills.namePlaceholder')" />
        </a-form-item>
        <a-form-item field="description" :label="$t('qa.ai.skills.desc')">
          <a-textarea v-model="editing.description" :placeholder="$t('qa.ai.skills.descPlaceholder')" :auto-size="{ minRows: 1, maxRows: 3 }" />
        </a-form-item>
        <a-form-item field="systemPrompt" :label="$t('qa.ai.skills.content')" required>
          <a-textarea v-model="editing.systemPrompt" :placeholder="$t('qa.ai.skills.contentPlaceholder')" :auto-size="{ minRows: 6, maxRows: 15 }" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { useI18n } from 'vue-i18n';
import { Message } from '@arco-design/web-vue';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import {
  listSkillDefinitions, createSkillDefinition, updateSkillDefinition, deleteSkillDefinition, toggleSkillDefinition,
} from '@/api/MyApi/ai';

const { t } = useI18n();

const definitions = ref<any[]>([]);
const defLoading = ref(false);
const editorVisible = ref(false);
const editing = ref<any>({});

async function loadDefinitions() {
  defLoading.value = true;
  try {
    const res: any = await listSkillDefinitions();
    if (res.code === 200) definitions.value = res.data || [];
  } catch { /* */ } finally { defLoading.value = false; }
}

function openEditor(record?: any) {
  if (record) {
    editing.value = { ...record };
  } else {
    editing.value = {};
  }
  editorVisible.value = true;
}

async function handleSave() {
  const e = editing.value;
  if (!e.name || !e.systemPrompt) {
    Message.warning(t('qa.ai.skills.fillRequired'));
    return;
  }
  const payload = {
    name: e.name,
    description: e.description || '',
    systemPrompt: e.systemPrompt,
  };
  try {
    if (e.id) {
      await updateSkillDefinition(e.id, payload);
    } else {
      await createSkillDefinition(payload);
    }
    Message.success(t('qa.ai.skills.saveSuccess'));
    editorVisible.value = false;
    loadDefinitions();
  } catch (err: any) {
    Message.error(err.response?.data?.msg || t('qa.ai.skills.saveFailed'));
  }
}

async function handleToggle(id: number) {
  await toggleSkillDefinition(id);
  Message.success(t('qa.ai.skills.toggleSuccess'));
  loadDefinitions();
}

async function handleDelete(id: number) {
  await deleteSkillDefinition(id);
  Message.success(t('qa.ai.common.deleted'));
  loadDefinitions();
}

onMounted(() => {
  loadDefinitions();
});
</script>

<style scoped>
.ai-skills-page {
  padding: 12px 20px 16px;
  display: flex;
  flex-direction: column;
  min-height: calc(100vh - 120px);
}
.ai-skills-page :deep(.arco-card) {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.ai-skills-page :deep(.arco-card-body) {
  flex: 1;
  display: flex;
  flex-direction: column;
}
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }

.skill-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
  flex: 1;
}

.skill-card {
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.skill-card:hover {
  border-color: rgb(var(--primary-6));
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
.skill-card.disabled {
  opacity: 0.6;
}

.skill-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.skill-name {
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  color: var(--color-text-1);
}
.skill-name:hover {
  color: rgb(var(--primary-6));
}

.skill-desc {
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.5;
  min-height: 20px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.skill-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.skill-card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: auto;
  padding-top: 8px;
  border-top: 1px solid var(--color-fill-2);
}
.skill-time {
  color: var(--color-text-4);
  font-size: 12px;
}
</style>
