<template>
  <a-modal
      :visible="visible"
      :title="$t('scene.derive.title')"
      :width="860"
      :mask-closable="false"
      :esc-to-close="false"
      @cancel="handleCancel"
  >
    <!-- ==================== 表单阶段 ==================== -->
    <template v-if="phase === 'form'">
      <a-form layout="vertical" class="derive-form">
        <!-- 源场景只读展示 -->
        <a-form-item :label="$t('scene.derive.sourceScene')">
          <div class="derive-source-scene">
            <span class="derive-source-name">{{ sourceScene?.name || '-' }}</span>
            <span v-if="sourceScene?.description" class="derive-source-desc">{{ sourceScene.description }}</span>
          </div>
        </a-form-item>

        <!-- 派生指令 -->
        <a-form-item :label="$t('scene.derive.instruction')">
          <a-textarea
              v-model="instruction"
              :placeholder="$t('scene.derive.instructionPlaceholder')"
              :auto-size="{minRows: 3, maxRows: 5}"
              allow-clear
          />
        </a-form-item>

        <!-- 发给 AI 的元素范围：目录树多选 -->
        <a-form-item :label="$t('scene.derive.elementScope')" required>
          <div class="derive-scope-tree">
            <a-scrollbar style="height: 220px; overflow: auto;">
              <a-tree
                  v-if="elementFolderTree.length > 0"
                  :data="elementFolderTree"
                  checkable
                  v-model:checked-keys="folderCheckedKeys"
                  v-model:expanded-keys="folderExpandedKeys"
                  :fieldNames="{key: 'id', title: 'elementName'}"
              >
                <template #switcher-icon>
                  <icon-folder style="font-size: 16px;"/>
                </template>
              </a-tree>
              <a-empty v-else :description="$t('scene.derive.noElementFolder')"/>
            </a-scrollbar>
          </div>
          <div class="derive-scope-count" :class="{ 'is-over': overLimit }">
            {{ $t('scene.derive.elementCount', { count: selectedElementCount }) }}
          </div>
          <div v-if="overLimit" class="derive-scope-over-tip">
            {{ $t('scene.derive.elementOverLimit', { limit: elementLimit }) }}
          </div>
        </a-form-item>

        <!-- 覆盖方向 -->
        <a-form-item :label="$t('scene.derive.coverage')">
          <a-checkbox-group v-model="coverageDirs">
            <a-checkbox :value="coverageValues.NEGATIVE">{{ $t('scene.derive.coverageNegative') }}</a-checkbox>
            <a-checkbox :value="coverageValues.BOUNDARY">{{ $t('scene.derive.coverageBoundary') }}</a-checkbox>
            <a-checkbox :value="coverageValues.EXCEPTION">{{ $t('scene.derive.coverageException') }}</a-checkbox>
            <a-checkbox :value="coverageValues.PERMISSION">{{ $t('scene.derive.coveragePermission') }}</a-checkbox>
          </a-checkbox-group>
        </a-form-item>

        <!-- 业务规则 / 提示文案 -->
        <a-form-item :label="$t('scene.derive.bizRules')">
          <a-textarea
              v-model="bizRules"
              :placeholder="$t('scene.derive.bizRulesPlaceholder')"
              :max-length="bizRulesMaxLength"
              show-word-limit
              :auto-size="{minRows: 3, maxRows: 6}"
          />
        </a-form-item>

        <!-- 使用说明折叠区 -->
        <a-collapse :bordered="false" class="derive-help">
          <a-collapse-item :header="$t('scene.derive.help')" key="help">
            <div class="derive-help-content">{{ $t('scene.derive.helpContent') }}</div>
          </a-collapse-item>
        </a-collapse>
      </a-form>
    </template>

    <!-- ==================== 生成中 ==================== -->
    <template v-else-if="phase === 'generating'">
      <div class="derive-generating">
        <a-space>
          <icon-loading/>
          <span>{{ $t('scene.derive.generating') }}</span>
        </a-space>
        <div ref="deltaScrollRef" class="derive-delta-log">
          <pre>{{ deltaText }}</pre>
        </div>
        <a-alert v-if="warnings.length > 0" type="warning" style="margin-top: 8px;">
          <div v-for="(w, i) in warnings" :key="i">{{ w }}</div>
        </a-alert>
      </div>
    </template>

    <!-- ==================== 结果预览 ==================== -->
    <template v-else>
      <a-form layout="vertical" class="derive-form">
        <a-alert v-if="warnings.length > 0" type="warning" style="margin-bottom: 12px;">
          <div v-for="(w, i) in warnings" :key="i">{{ w }}</div>
        </a-alert>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item :label="$t('scene.derive.sceneName')" required>
              <a-input v-model="draft.name" :max-length="50" show-word-limit/>
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item :label="$t('scene.derive.sceneDesc')">
              <a-input v-model="draft.description" :max-length="200"/>
            </a-form-item>
          </a-col>
        </a-row>
        <RecordStepList v-model:steps="draft.steps"/>
      </a-form>
    </template>

    <!-- ==================== 底部按钮 ==================== -->
    <template #footer>
      <template v-if="phase === 'form'">
        <a-button @click="handleCancel">{{ $t('qa.common.cancel') }}</a-button>
        <a-button type="primary" :disabled="!canGenerate" @click="startGenerate">
          {{ $t('scene.derive.generate') }}
        </a-button>
      </template>
      <template v-else-if="phase === 'generating'">
        <a-button status="danger" @click="stopGenerate">{{ $t('scene.derive.stop') }}</a-button>
      </template>
      <template v-else>
        <a-button @click="phase = 'form'">{{ $t('scene.derive.back') }}</a-button>
        <a-button type="primary" :loading="saving" :disabled="!draft.name?.trim() || draft.steps.length === 0"
                  @click="handleSave">
          {{ $t('scene.derive.save') }}
        </a-button>
      </template>
    </template>
  </a-modal>
</template>

<script lang="ts" setup>
import {computed, h, nextTick, ref, watch} from 'vue';
import {useI18n} from 'vue-i18n';
import {Message, Modal} from '@arco-design/web-vue';
import {IconFolder, IconLoading} from '@arco-design/web-vue/es/icon';
import RecordStepList from '@/views/scene/component/RecordStepList.vue';
import {getFolderList as getElementFolderList, getAllElementByProjectId} from '@/api/MyApi/element';
import {
  DERIVE_BIZ_RULES_MAX_LENGTH,
  DERIVE_COVERAGE,
  DERIVE_ELEMENT_LIMIT,
  deriveSceneStream,
  saveDerivedScene,
} from '@/api/MyApi/aiSceneDerive';
import type {RecordStepDraft} from '@/api/MyApi/record';
import {Scene} from '@/types/domain/Scene';
import {useProjectStore} from '@/store';

const props = defineProps<{
  visible: boolean;
  /** 源场景（只读展示 + 派生来源） */
  sourceScene: Scene | null;
  /** 源场景所属目录名：用于默认勾选元素库同名目录 */
  sourceFolderName?: string;
}>();

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'saved'): void;
}>();

const {t} = useI18n();
const projectStore = useProjectStore();

const elementLimit = DERIVE_ELEMENT_LIMIT;
const bizRulesMaxLength = DERIVE_BIZ_RULES_MAX_LENGTH;
const coverageValues = DERIVE_COVERAGE;

// 阶段：form=填写参数，generating=SSE 生成中，result=草稿预览
const phase = ref<'form' | 'generating' | 'result'>('form');

// 表单字段
const instruction = ref('');
const folderCheckedKeys = ref<(number | string)[]>([]);
const folderExpandedKeys = ref<(number | string)[]>([]);
const coverageDirs = ref<string[]>([]);
const bizRules = ref('');

// 元素库数据
const elementFolderTree = ref<any[]>([]);
const elementList = ref<any[]>([]);

// 生成过程
const deltaText = ref('');
const warnings = ref<string[]>([]);
const deltaScrollRef = ref<HTMLElement | null>(null);
let abortController: AbortController | null = null;

// 结果草稿
const draft = ref<{ name: string; description?: string; steps: RecordStepDraft[] }>({name: '', description: '', steps: []});
const saving = ref(false);

// 扁平化目录列表（含 parentId），用于计算每个目录的后代集合
const flatFolders = computed<any[]>(() => {
  const result: any[] = [];
  const walk = (nodes: any[], parentId: number) => {
    (nodes || []).forEach((n) => {
      result.push({id: Number(n.id), parentId, elementName: n.elementName});
      if (n.children?.length) walk(n.children, Number(n.id));
    });
  };
  walk(elementFolderTree.value, 0);
  return result;
});

// 每个目录 id → 自身 + 后代目录 id 集合
const folderScopeMap = computed(() => {
  const childrenMap = new Map<number, number[]>();
  flatFolders.value.forEach((f) => {
    if (!childrenMap.has(f.parentId)) childrenMap.set(f.parentId, []);
    childrenMap.get(f.parentId)!.push(f.id);
  });
  const map = new Map<number, Set<number>>();
  const collect = (id: number, acc: Set<number>) => {
    if (acc.has(id)) return;
    acc.add(id);
    (childrenMap.get(id) || []).forEach((c) => collect(c, acc));
  };
  flatFolders.value.forEach((f) => {
    const scope = new Set<number>();
    collect(f.id, scope);
    map.set(f.id, scope);
  });
  return map;
});

// 已选元素总数：勾选目录（含后代目录）下的元素去重计数
const selectedElementCount = computed(() => {
  const scope = new Set<number>();
  folderCheckedKeys.value.forEach((key) => {
    const s = folderScopeMap.value.get(Number(key));
    if (s) s.forEach((id) => scope.add(id));
  });
  return elementList.value.filter((el) => el.parentId != null && scope.has(Number(el.parentId))).length;
});

const overLimit = computed(() => selectedElementCount.value > elementLimit);

// 提交给后端的目录 id：虚拟根目录（id=0）展开为全部真实目录
const submitFolderIds = computed<number[]>(() => {
  const ids = new Set<number>();
  folderCheckedKeys.value.forEach((key) => {
    const id = Number(key);
    if (id > 0) {
      ids.add(id);
    } else {
      flatFolders.value.forEach((f) => {
        if (f.id > 0) ids.add(f.id);
      });
    }
  });
  return [...ids];
});

const canGenerate = computed(() =>
    submitFolderIds.value.length > 0 && !overLimit.value && phase.value === 'form');

// 打开弹窗：重置并加载元素库目录/元素，默认勾选与源场景同名的元素目录
watch(() => props.visible, async (visible) => {
  if (!visible) return;
  phase.value = 'form';
  instruction.value = '';
  coverageDirs.value = [];
  bizRules.value = '';
  deltaText.value = '';
  warnings.value = [];
  draft.value = {name: '', description: '', steps: []};
  folderCheckedKeys.value = [];

  const projectId = String(projectStore.getProjectId ?? '');
  const [folderRes, elementRes] = await Promise.all([
    getElementFolderList(projectId),
    getAllElementByProjectId(projectId),
  ]);
  elementFolderTree.value = folderRes.data || [];
  elementList.value = elementRes.data || [];
  folderExpandedKeys.value = flatFolders.value.map((f) => f.id);

  if (props.sourceFolderName) {
    const matched = flatFolders.value.find((f) => f.elementName === props.sourceFolderName);
    if (matched) folderCheckedKeys.value = [matched.id];
  }
});

// 生成文本滚动到底部
watch(deltaText, async () => {
  await nextTick();
  if (deltaScrollRef.value) {
    deltaScrollRef.value.scrollTop = deltaScrollRef.value.scrollHeight;
  }
});

// AI 返回的步骤 JSON → 预览草稿步骤
const toDraftStep = (s: any): RecordStepDraft => ({
  stepType: s?.stepType || s?.stepDetail?.stepType || '',
  stepName: s?.stepName || s?.stepDetail?.stepName || '',
  stepDetail: s?.stepDetail ?? s,
});

const startGenerate = () => {
  if (!canGenerate.value) {
    if (folderCheckedKeys.value.length === 0) {
      Message.warning(t('scene.derive.selectFolderFirst'));
    }
    return;
  }
  phase.value = 'generating';
  deltaText.value = '';
  warnings.value = [];
  abortController = new AbortController();
  deriveSceneStream({
    sceneId: props.sourceScene!.id!,
    instruction: instruction.value?.trim() || undefined,
    folderIds: submitFolderIds.value,
    coverageDirs: coverageDirs.value,
    bizRules: bizRules.value?.trim() || undefined,
  }, {
    onDelta: (chunk) => {
      deltaText.value += chunk;
    },
    onWarning: (msg) => {
      warnings.value.push(msg);
    },
    onResult: (data) => {
      const steps = (data.steps || []).map(toDraftStep);
      if (steps.length === 0) {
        Message.error(t('scene.derive.resultEmpty'));
        phase.value = 'form';
        return;
      }
      draft.value = {
        name: data.name || `${props.sourceScene?.name || ''}-AI`,
        description: data.description || '',
        steps,
      };
      phase.value = 'result';
    },
    onError: (msg) => {
      Message.error(msg || t('scene.derive.generateFailed'));
      phase.value = 'form';
    },
  }, abortController.signal);
};

const stopGenerate = () => {
  abortController?.abort();
  abortController = null;
  phase.value = 'form';
};

const handleCancel = () => {
  if (phase.value === 'generating') {
    stopGenerate();
  }
  emit('update:visible', false);
};

const handleSave = async () => {
  if (!props.sourceScene?.id) return;
  saving.value = true;
  try {
    // 最终契约：{deriveFrom, name, description?, steps:[{stepType, stepName, stepDetail}]}
    // 分组目录/项目归属由后端处理；stepDetail 中元素用 elementId 引用，后端自行解析快照
    const steps = draft.value.steps.map((s) => ({
      stepType: s.stepType,
      stepName: s.stepName,
      stepDetail: s.stepDetail,
    }));
    // axios 拦截器对 code !== 200 已统一报错并 reject，resolve 即成功
    const result = await saveDerivedScene({
      deriveFrom: Number(props.sourceScene.id),
      name: draft.value.name.trim(),
      description: draft.value.description || undefined,
      steps,
    });
    const data = result.data;
    if (data && data.sceneId != null) {
      Message.success({content: t('scene.derive.saveSuccess'), duration: 1500});
      // 部分步骤被跳过或后端有告警时，给用户明确提示
      const saveWarnings: string[] = data.warnings || [];
      if ((data.skippedSteps ?? 0) > 0 || saveWarnings.length > 0) {
        Modal.warning({
          title: t('scene.derive.saveSkippedTitle'),
          content: () => [
            (data.skippedSteps ?? 0) > 0
                ? h('div', t('scene.derive.saveSkippedContent', {saved: data.savedSteps ?? 0, skipped: data.skippedSteps}))
                : null,
            ...saveWarnings.map((w) => h('div', w)),
          ],
        });
      }
      emit('update:visible', false);
      emit('saved');
    } else {
      Message.error({content: t('scene.derive.saveFailed'), duration: 2000});
    }
  } catch {
    // 拦截器已弹出错误提示，这里不再重复
  } finally {
    saving.value = false;
  }
};
</script>

<style scoped>
.derive-form :deep(.arco-form-item) {
  margin-bottom: 14px;
}

.derive-source-scene {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 12px;
  background: var(--color-fill-1);
  border-radius: 4px;
  width: 100%;
}

.derive-source-name {
  font-weight: 600;
  color: var(--color-text-1);
}

.derive-source-desc {
  font-size: 12px;
  color: var(--color-text-3);
}

.derive-scope-tree {
  border: 1px solid var(--color-neutral-3);
  border-radius: 4px;
  padding: 8px;
  width: 100%;
}

.derive-scope-count {
  margin-top: 6px;
  font-size: 13px;
  color: rgb(var(--arcoblue-6));
  font-weight: 600;
}

.derive-scope-count.is-over {
  color: rgb(var(--red-6));
}

.derive-scope-over-tip {
  font-size: 12px;
  color: rgb(var(--red-6));
}

.derive-help {
  margin-top: -4px;
}

.derive-help-content {
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.8;
  white-space: pre-wrap;
}

.derive-generating {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.derive-delta-log {
  height: 320px;
  overflow-y: auto;
  border: 1px solid var(--color-neutral-3);
  border-radius: 4px;
  padding: 12px;
  background: var(--color-fill-1);
}

.derive-delta-log pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  color: var(--color-text-2);
}
</style>
