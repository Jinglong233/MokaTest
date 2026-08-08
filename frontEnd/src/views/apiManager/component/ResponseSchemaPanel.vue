<template>
  <div class="response-schema-panel">
    <div class="form-item">
      <div class="form-label">响应定义模式</div>
      <a-radio-group
          :model-value="local.mode"
          :disabled="disabled"
          @change="(v) => updateField('mode', v as ResponseSchema['mode'])"
      >
        <a-radio value="NONE">不定义</a-radio>
        <a-radio value="TEMPLATE">绑定数据模板</a-radio>
        <a-radio value="INLINE">内联定义</a-radio>
      </a-radio-group>
    </div>

    <!-- 绑定数据模板 -->
    <template v-if="local.mode === 'TEMPLATE'">
      <div class="form-item">
        <div class="form-label">数据模板<span class="required-mark">*</span></div>
        <a-select
            :model-value="local.templateId"
            placeholder="请选择数据模板"
            :disabled="disabled"
            allow-clear
            @change="(v) => updateField('templateId', v as number)"
        >
          <a-option v-for="tmpl in templateList" :key="tmpl.id" :value="tmpl.id">
            {{ tmpl.templateName }}
          </a-option>
        </a-select>
      </div>

      <div class="form-item">
        <div class="form-label">隐藏字段</div>
        <a-input-tag
            :model-value="local.hiddenFields"
            placeholder="输入要从模板中剔除的字段名，回车添加"
            :disabled="disabled"
            allow-clear
            @update:model-value="(v) => updateField('hiddenFields', v as string[])"
        />
      </div>

      <div class="form-item">
        <div class="form-label">
          覆盖字段
          <span class="override-tip">（与模板中同名字段以这里定义的为准）</span>
        </div>
        <MockFieldTreeNode
            v-model="overrideRoot"
            :template-list="templateList"
            :disabled="disabled"
            root-label="覆盖字段"
            @change="emitUpdate"
        />
      </div>
    </template>

    <!-- 内联定义 -->
    <template v-else-if="local.mode === 'INLINE'">
      <div class="form-item">
        <div class="form-label">响应体结构</div>
        <MockFieldTreeNode
            :model-value="inlineSchema"
            :template-list="templateList"
            :disabled="disabled"
            root-label="响应体"
            @update:model-value="(v) => updateField('schema', v)"
        />
      </div>
    </template>

    <div class="form-item" v-if="!hideValidate">
      <div class="form-label">执行时校验响应结构</div>
      <div class="validate-row">
        <a-switch
            :model-value="local.validateEnabled"
            :disabled="disabled"
            @change="(v) => updateField('validateEnabled', v as boolean)"
        />
        <span class="validate-tip">开启后，调试/场景执行会校验响应是否符合上述结构定义，结果以「结构校验」断言展示</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { ref, reactive, computed, watch, onMounted } from 'vue';
  import { Message } from '@arco-design/web-vue';
  import { ResponseSchema } from '@/types/domain/api/requestModel/ResponseSchema';
  import { MockFieldRule } from '@/types/domain/api/requestModel/MockFieldRule';
  import { DataTemplate } from '@/types/domain/api/DataTemplate';
  import { getDataTemplateList } from '@/api/MyApi/dataTemplate';
  import useProjectStore from '@/store/modules/project';
  import MockFieldTreeNode from './MockFieldTreeNode.vue';

  interface Props {
    modelValue?: ResponseSchema;
    disabled?: boolean;
    templateList?: DataTemplate[];
    /** 隐藏「执行时校验响应结构」开关（请求 Body 绑定场景用，校验只对响应有意义） */
    hideValidate?: boolean;
  }

  const props = withDefaults(defineProps<Props>(), {
    disabled: false,
  });

  const emit = defineEmits<{
    (e: 'update:modelValue', value: ResponseSchema): void;
    (e: 'change'): void;
  }>();

  const projectStore = useProjectStore();

  // 父组件未传模板列表时自行加载（与 TemplateDataSelector 一致）
  const innerTemplateList = ref<DataTemplate[]>([]);
  const templateList = computed(() => props.templateList ?? innerTemplateList.value);

  const loadTemplates = async () => {
    const projectId = projectStore.getProjectId;
    if (!projectId) return;
    try {
      const {data} = await getDataTemplateList(projectId);
      innerTemplateList.value = data || [];
    } catch (e) {
      Message.error('加载模板列表失败');
    }
  };

  onMounted(() => {
    if (!props.templateList) {
      loadTemplates();
    }
  });

  const local = reactive<ResponseSchema>(new ResponseSchema());

  watch(
    () => props.modelValue,
    (newVal) => {
      const merged = new ResponseSchema();
      Object.assign(merged, newVal || {});
      if (!merged.hiddenFields) merged.hiddenFields = [];
      if (!merged.overrideFields) merged.overrideFields = [];
      Object.assign(local, merged);
    },
    { immediate: true, deep: true }
  );

  function updateField(field: keyof ResponseSchema, value: any) {
    (local as any)[field] = value;
    emitUpdate();
  }

  function emitUpdate() {
    emit('update:modelValue', JSON.parse(JSON.stringify(local)) as ResponseSchema);
    emit('change');
  }

  // 覆盖字段：包装成一个 OBJECT 根节点，children 即 overrideFields，双向同步
  const overrideRoot = computed<MockFieldRule>({
    get: () => {
      const root = new MockFieldRule(true);
      root.children = (local.overrideFields || []).map((r) =>
        JSON.parse(JSON.stringify(r))
      );
      return root;
    },
    set: (root: MockFieldRule) => {
      local.overrideFields = (root?.children || []).map((r) =>
        JSON.parse(JSON.stringify(r))
      );
      emitUpdate();
    },
  });

  // 内联定义：schema 为空时给一个 OBJECT 根节点
  const inlineSchema = computed<MockFieldRule>(() => {
    if (local.schema) return local.schema;
    return new MockFieldRule(true);
  });
</script>

<style scoped>
  .response-schema-panel {
    width: 100%;
  }

  .form-item {
    margin-bottom: 16px;
  }

  .form-label {
    font-size: 13px;
    color: var(--color-text-1);
    margin-bottom: 6px;
  }

  .required-mark {
    color: #f53f3f;
    margin-left: 2px;
  }

  .override-tip {
    font-size: 12px;
    color: var(--color-text-3);
    font-weight: normal;
  }

  .validate-row {
    display: flex;
    align-items: center;
  }

  .validate-tip {
    margin-left: 8px;
    font-size: 12px;
    color: var(--color-text-3);
  }
</style>
