<template>
  <div class="traceability-page" v-if="projectStore.hasProjectSelected">
    <Breadcrumb :items="['menu.qa', 'menu.qa.traceability']"/>

    <a-card class="search-card" size="small">
      <a-space>
        <a-select
            v-model="selectedRequirementId"
            :placeholder="$t('qa.traceability.selectPlaceholder')"
            allow-search
            :filter-option="false"
            style="width: 400px"
            @search="handleSearchRequirement"
            @dropdown-visible-change="handleDropdownVisibleChange"
        >
          <a-option v-for="req in requirementOptions" :key="req.id" :value="req.id">
            {{ req.reqCode }} - {{ req.title }}
          </a-option>
        </a-select>
        <a-button type="primary" @click="handleLoadTrace" :loading="loading">
          <template #icon><icon-search /></template>
          {{ $t('qa.traceability.viewTrace') }}
        </a-button>
      </a-space>
    </a-card>

    <a-card v-if="traceData.requirement" class="trace-card" :bordered="false">
      <a-descriptions :data="[
        { label: $t('qa.requirement.code'), value: traceData.requirement.reqCode },
        { label: $t('qa.requirement.name'), value: traceData.requirement.title },
        { label: $t('qa.requirement.status'), value: statusText(traceData.requirement.status) },
        { label: $t('qa.requirement.priority'), value: traceData.requirement.priority }
      ]" layout="inline-horizontal" :column="4" />

      <a-divider style="margin: 16px 0;" />

      <a-row :gutter="16" style="margin-bottom: 16px;">
        <a-col :span="8">
          <a-statistic :title="$t('qa.traceability.relatedCases')" :value="traceData.caseCount || 0" show-group-separator>
            <template #prefix><icon-file style="color: rgb(var(--arcoblue-6));" /></template>
          </a-statistic>
        </a-col>
        <a-col :span="8">
          <a-statistic :title="$t('qa.traceability.relatedBugCount')" :value="traceData.bugCount || 0" show-group-separator>
            <template #prefix><icon-bug style="color: rgb(var(--red-6));" /></template>
          </a-statistic>
        </a-col>
        <a-col :span="8">
          <a-statistic :title="$t('qa.traceability.openBugCount')" :value="traceData.openBugCount || 0" show-group-separator>
            <template #prefix><icon-exclamation-circle style="color: rgb(var(--orange-6));" /></template>
          </a-statistic>
        </a-col>
      </a-row>

      <a-divider style="margin: 16px 0;" />

      <h3 style="margin-bottom: 12px;">{{ $t('qa.traceability.traceGraph') }}</h3>
      <a-empty v-if="!traceData.testCases || traceData.testCases.length === 0" :description="$t('qa.traceability.noRelatedCase')" />
      <div v-else class="trace-tree">
        <div class="tree-node root-node">
          <div class="node-icon req-icon">{{ $t('qa.traceability.iconRequirement') }}</div>
          <div class="node-content">
            <div class="node-title">{{ traceData.requirement.reqCode }} {{ traceData.requirement.title }}</div>
            <a-tag size="small">{{ statusText(traceData.requirement.status) }}</a-tag>
          </div>
        </div>
        <div class="tree-children">
          <div v-for="tc in traceData.testCases" :key="tc.id" class="tree-branch">
            <div class="tree-node case-node">
              <div class="node-icon case-icon">{{ $t('qa.traceability.iconCase') }}</div>
              <div class="node-content">
                <div class="node-title">{{ tc.caseCode }} {{ tc.caseName }}</div>
                <a-space size="mini">
                  <a-tag size="small" :color="priorityColor(tc.priority)">{{ tc.priority }}</a-tag>
                  <a-tag size="small" :color="lastResultColor(tc.lastResult)" v-if="tc.lastResult">{{ lastResultText(tc.lastResult) }}</a-tag>
                  <span v-if="tc.bugs && tc.bugs.length > 0" style="color: var(--color-text-3); font-size: 12px;">{{ $t('qa.traceability.bugCountLabel', { count: tc.bugs.length }) }}</span>
                </a-space>
              </div>
            </div>
            <div v-if="tc.bugs && tc.bugs.length > 0" class="tree-children">
              <div v-for="bug in tc.bugs" :key="bug.id" class="tree-branch">
                <div class="tree-node bug-node">
                  <div class="node-icon bug-icon">{{ $t('qa.traceability.iconBug') }}</div>
                  <div class="node-content">
                    <div class="node-title">{{ bug.bugCode }} {{ bug.title }}</div>
                    <a-space size="mini">
                      <a-tag size="small" :color="bugStatusColor(bug.status)">{{ bugStatusText(bug.status) }}</a-tag>
                      <a-tag size="small" :color="severityColor(bug.severity)">{{ bug.severity }}</a-tag>
                    </a-space>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </a-card>

    <a-empty v-else :description="$t('qa.traceability.selectRequirementHint')" style="margin-top: 40px;" />
  </div>
  <NoProjectPlaceholder v-else />
</template>

<script lang="ts">
  // 组件名需与路由 name 一致，供页签 keep-alive :include 匹配缓存
  export default { name: 'Traceability' };
</script>

<script setup lang="ts">
import {ref, onMounted} from 'vue';
import {useI18n} from 'vue-i18n';
import {IconSearch, IconFile, IconBug, IconExclamationCircle} from '@arco-design/web-vue/es/icon';
import Breadcrumb from '@/components/breadcrumb/index.vue';
import NoProjectPlaceholder from '@/components/no-project-placeholder/index.vue';
import {useProjectStore} from '@/store';
import {getRequirementList, getRequirementTraceability} from '@/api/MyApi/qa';

const projectStore = useProjectStore();
const { t } = useI18n();
const loading = ref(false);
const selectedRequirementId = ref<number | undefined>(undefined);
const requirementOptions = ref<any[]>([]);
const reqPage = ref({ current: 1, pageSize: 20, hasMore: true, keyword: '', loading: false });

const traceData = ref<any>({});

const statusText = (status: string) => {
  const map: Record<string, string> = {
    DRAFT: t('qa.traceability.status.draft'), REVIEWING: t('qa.traceability.status.reviewing'),
    CONFIRMED: t('qa.traceability.status.confirmed'), DEVELOPING: t('qa.traceability.status.developing'),
    TESTING: t('qa.traceability.status.testing'), RELEASED: t('qa.traceability.status.released'),
    CLOSED: t('qa.traceability.status.closed')
  };
  return map[status] || status;
};

const priorityColor = (priority: string) => {
  const map: Record<string, string> = { P0: 'red', P1: 'orange', P2: 'blue', P3: 'green' };
  return map[priority] || '';
};

const lastResultColor = (result: string) => {
  const map: Record<string, string> = { PASS: 'green', FAIL: 'red', BLOCK: 'orange', NA: 'gray' };
  return map[result] || '';
};

const lastResultText = (result: string) => {
  const map: Record<string, string> = {
    PASS: t('qa.traceability.lastResult.pass'), FAIL: t('qa.testPlan.result.fail'),
    BLOCK: t('qa.testPlan.result.block'), NA: t('qa.testPlan.result.na')
  };
  return map[result] || result;
};

const bugStatusColor = (status: string) => {
  const map: Record<string, string> = {
    NEW: 'red', CONFIRMED: 'orange', FIXING: 'blue',
    FIXED: 'green', VERIFIED: 'cyan', CLOSED: 'gray', REJECTED: 'gray'
  };
  return map[status] || '';
};

const bugStatusText = (status: string) => {
  const map: Record<string, string> = {
    NEW: t('qa.traceability.bugStatus.new'), CONFIRMED: t('qa.traceability.bugStatus.confirmed'),
    FIXING: t('qa.traceability.bugStatus.fixing'), FIXED: t('qa.traceability.bugStatus.fixed'),
    VERIFIED: t('qa.traceability.bugStatus.verified'), CLOSED: t('qa.traceability.bugStatus.closed'),
    REJECTED: t('qa.traceability.bugStatus.rejected')
  };
  return map[status] || status;
};

const severityColor = (severity: string) => {
  const map: Record<string, string> = { FATAL: 'red', SERIOUS: 'orange', NORMAL: 'blue', TIPS: 'gray' };
  return map[severity] || '';
};

const loadRequirements = async (isSearch: boolean = false) => {
  if (!projectStore.getProjectId) return;
  if (reqPage.value.loading) return;
  reqPage.value.loading = true;
  try {
    if (isSearch) {
      reqPage.value.current = 1;
      reqPage.value.hasMore = true;
      requirementOptions.value = [];
    }
    if (!reqPage.value.hasMore) return;
    const res: any = await getRequirementList(
        projectStore.getProjectId,
        reqPage.value.keyword || undefined,
        undefined, undefined, undefined, undefined,
        reqPage.value.current, reqPage.value.pageSize
    );
    if (res.code === 200 && res.data) {
      const list = res.data.records || [];
      if (isSearch) {
        requirementOptions.value = list;
      } else {
        requirementOptions.value.push(...list);
      }
      reqPage.value.hasMore = list.length === reqPage.value.pageSize;
      if (list.length === reqPage.value.pageSize) {
        reqPage.value.current++;
      }
    }
  } catch (e) {
    console.error(e);
  } finally {
    reqPage.value.loading = false;
  }
};

const handleSearchRequirement = (keyword: string) => {
  reqPage.value.keyword = keyword;
  loadRequirements(true);
};

const handleDropdownVisibleChange = (visible: boolean) => {
  if (visible && requirementOptions.value.length === 0) {
    loadRequirements(true);
  }
};

const handleLoadTrace = async () => {
  if (!selectedRequirementId.value) {
    traceData.value = {};
    return;
  }
  loading.value = true;
  try {
    const res: any = await getRequirementTraceability(selectedRequirementId.value);
    if (res.code === 200 && res.data) {
      traceData.value = res.data;
    }
  } catch (e) {
    console.error(e);
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  loadRequirements(true);
});
</script>

<style scoped>
.traceability-page {
  padding: 16px;
}
.search-card {
  margin-bottom: 16px;
}
.trace-card {
  min-height: 400px;
}
.trace-tree {
  padding: 8px;
}
.tree-node {
  display: flex;
  align-items: flex-start;
  padding: 12px 16px;
  border-radius: 8px;
  margin-bottom: 8px;
  background: var(--color-fill-2);
}
.root-node {
  background: rgb(var(--arcoblue-1));
}
.case-node {
  background: rgb(var(--green-1));
}
.bug-node {
  background: rgb(var(--red-1));
}
.node-icon {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
  margin-right: 12px;
  flex-shrink: 0;
}
.req-icon {
  background: rgb(var(--arcoblue-6));
  color: white;
}
.case-icon {
  background: rgb(var(--green-6));
  color: white;
}
.bug-icon {
  background: rgb(var(--red-6));
  color: white;
}
.node-content {
  flex: 1;
  min-width: 0;
}
.node-title {
  font-weight: 500;
  margin-bottom: 4px;
  word-break: break-all;
}
.tree-children {
  margin-left: 32px;
  padding-left: 16px;
  border-left: 2px solid var(--color-border-2);
}
.tree-branch {
  margin-bottom: 4px;
}
</style>
