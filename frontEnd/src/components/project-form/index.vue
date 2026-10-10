<template>
  <a-modal
    v-model:visible="visible"
    :title="form.id ? $t('components.projectForm.editTitle') : $t('components.projectForm.createTitle')"
    @cancel="handleCancel"
    @before-ok="handleBeforeOk"
  >
    <a-form ref="formRef" :model="form" :rules="formRules">
      <a-form-item field="projectName" :label="$t('components.projectForm.nameLabel')" required>
        <a-input v-model="form.projectName" :placeholder="$t('components.projectForm.namePlaceholder')"/>
      </a-form-item>
      <a-form-item field="description" :label="$t('components.projectForm.descLabel')">
        <a-textarea v-model="form.description" :placeholder="$t('components.projectForm.descPlaceholder')"/>
      </a-form-item>
      <a-form-item field="status" :label="$t('components.projectForm.statusLabel')" required>
        <a-select v-model="form.status" :placeholder="$t('components.projectForm.statusPlaceholder')">
          <a-option value="ACTIVE">{{ $t('components.projectForm.statusActive') }}</a-option>
          <a-option value="COMPLETED">{{ $t('components.projectForm.statusCompleted') }}</a-option>
          <a-option value="SUSPENDED">{{ $t('components.projectForm.statusSuspended') }}</a-option>
        </a-select>
      </a-form-item>
      <a-form-item
        v-if="canManageOwner"
        field="ownerId"
        :label="$t('components.projectForm.ownerLabel')"
      >
        <a-select
          v-model="form.ownerId"
          :placeholder="$t('components.projectForm.ownerPlaceholder')"
          allow-search
          allow-clear
        >
          <a-option
            v-for="m in teamMemberOptions"
            :key="m.userId"
            :value="m.userId"
          >
            {{ m.nickname || m.username }}
            <span
              v-if="m.username"
              style="color: var(--color-text-3); margin-left: 4px"
            >
              ({{ m.username }})
            </span>
          </a-option>
        </a-select>
      </a-form-item>
      <a-form-item field="tagClassify" :label="$t('components.projectForm.tagLabel')">
        <a-input-tag
          v-model="form.tagClassify"
          :style="{ width: '100%' }"
          :placeholder="$t('components.projectForm.tagPlaceholder')"
          :max-tag-count="3"
          allow-clear
          @press-enter="validateTagNum"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import { Message } from '@arco-design/web-vue';
import { useI18n } from 'vue-i18n';
import { Project } from '@/types/domain/Project';
import { addProject, updateProject } from '@/api/MyApi/project';
import { getTeamMembers } from '@/api/MyApi/team';
import useTeamStore from '@/store/modules/team';
import useDataStore from '@/store/modules/nav';

const { t } = useI18n();

const props = defineProps<{
  visible: boolean;
  project?: Project | null;
  canManageOwner?: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:visible', visible: boolean): void;
  (e: 'success', project?: Project): void;
}>();

const visible = ref(props.visible);

watch(
  () => props.visible,
  (val) => {
    visible.value = val;
  }
);

watch(
  () => visible.value,
  (val) => {
    emit('update:visible', val);
  }
);

const formRef = ref<any>(null);
const form = ref<Project>(new Project());
const teamMemberOptions = ref<any[]>([]);

const loadTeamMemberOptions = async () => {
  const teamId = useTeamStore().teamId;
  if (!teamId) {
    teamMemberOptions.value = [];
    return;
  }
  try {
    const res: any = await getTeamMembers(teamId);
    if (res.code === 200 && Array.isArray(res.data)) {
      // 超管不可被指定为项目管理员；但历史数据的 owner 可能是超管，需保留在选项中避免裸显示 id
      teamMemberOptions.value = res.data.filter(
        (m: any) =>
          !m.superAdmin || String(m.userId) === String(form.value.ownerId)
      );
    } else {
      teamMemberOptions.value = [];
    }
  } catch (e) {
    console.error(e);
    teamMemberOptions.value = [];
  }
};

const formRules = computed(() => ({
  projectName: [{ required: true, message: t('components.projectForm.nameRequired') }],
  status: [{ required: true, message: t('components.projectForm.statusRequired') }]
}));

const resetForm = () => {
  formRef.value?.clearValidate?.();
  if (props.project) {
    form.value = { ...props.project };
    const tags = props.project.tagClassify;
    if (tags) {
      if (typeof tags === 'string') {
        try { form.value.tagClassify = JSON.parse(tags); }
        catch { form.value.tagClassify = []; }
      } else {
        form.value.tagClassify = tags;
      }
    }
  } else {
    form.value = new Project();
  }
};

watch(
  () => props.visible,
  (isVisible) => {
    if (isVisible) {
      resetForm();
      if (props.canManageOwner) {
        loadTeamMemberOptions();
      }
    }
  }
);

watch(
  () => props.project,
  () => {
    if (props.visible) {
      resetForm();
    }
  },
  { deep: true }
);

const handleCancel = () => {
  formRef.value?.clearValidate?.();
  visible.value = false;
};

const handleBeforeOk = async () => {
  const error = await formRef.value?.validate?.();
  if (error) return false;

  if (form.value.id != null) {
    const result = await updateProject(form.value);
    if (result.data) {
      Message.success({ content: t('components.common.updateSuccess'), duration: 1000 });
      await useDataStore().fetchData();
      visible.value = false;
      emit('success', form.value);
    } else {
      Message.error({ content: t('components.common.updateFailed'), duration: 1000 });
      return false;
    }
  } else {
    form.value.teamId = String(useTeamStore().teamId ?? '');
    const result = await addProject(form.value);
    if (result.data) {
      Message.success({ content: t('components.common.addSuccess'), duration: 1000 });
      await useDataStore().fetchData();
      visible.value = false;
      emit('success');
    } else {
      Message.error({ content: t('components.common.addFailed'), duration: 1000 });
      return false;
    }
  }
};

const validateTagNum = () => {
  const tags = form.value.tagClassify;
  if (Array.isArray(tags) && tags.length > 3) {
    form.value.tagClassify = tags.slice(0, 3);
    Message.warning({ content: t('components.projectForm.tagLimit'), duration: 1000 });
  }
};
</script>
