<template>
  <!-- 新建团队弹窗（仅超管入口可见） -->
  <a-modal
    v-model:visible="visible"
    title="新建团队"
    :mask-closable="false"
    :esc-to-close="false"
    :closable="false"
    :unmount-on-close="true"
  >
    <template #footer>
      <a-button @click="handleCancel">取消</a-button>
      <a-button type="primary" :loading="submitting" @click="handleOkClick">确定</a-button>
    </template>
    <a-form :model="editingTeam" ref="teamFormRef">
      <a-form-item label="团队名称" field="teamName" :rules="teamNameRules">
        <a-input
          v-model="editingTeam.teamName"
          placeholder="请输入团队名称"
        />
      </a-form-item>
      <a-form-item label="团队描述" field="description">
        <a-textarea
          v-model="editingTeam.description"
          placeholder="请输入团队描述"
        />
      </a-form-item>
      <a-form-item
        label="团队管理员"
        field="ownerId"
      >
        <a-select
          v-model="editingTeam.ownerId"
          placeholder="不指定时由您担任团队管理员"
          allow-search
          allow-clear
        >
          <a-option
            v-for="user in platformUserOptions"
            :key="user.id"
            :value="user.id"
          >
            {{ user.nickname || user.username }}
            <span
              v-if="user.username"
              style="color: var(--color-text-3); margin-left: 4px"
            >({{ user.username }})</span>
          </a-option>
        </a-select>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import type { FormInstance } from '@arco-design/web-vue';
import { Team } from '@/types/domain/Team';
import { UserVO } from '@/types/vo/UserVO';
import { createTeam } from '@/api/MyApi/team';
import { getUserList } from '@/api/MyApi/user';

const props = defineProps<{
  visible: boolean;
}>();

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void;
  (e: 'success'): void;
}>();

const visible = ref(props.visible);
watch(
  () => props.visible,
  (val) => {
    visible.value = val;
    if (val) {
      editingTeam.value = new Team();
      loadPlatformUsers();
    }
  }
);
watch(visible, (val) => emit('update:visible', val));

const teamFormRef = ref<FormInstance>();
const editingTeam = ref<Team>(new Team());
const platformUserOptions = ref<UserVO[]>([]);
const submitting = ref(false);

const teamNameRules = [
  { required: true, message: '请填写团队名称' },
  { maxLength: 10, message: '团队名称长度最大为10' },
  { minLength: 5, message: '团队名称长度最小为5' },
  {
    match: /^[a-zA-Z0-9_一-龥]+$/,
    message: '团队名称只能包含汉字、字母、数字和下划线',
  },
];

const loadPlatformUsers = async () => {
  try {
    const res: any = await getUserList();
    if (res.code === 200 && res.data) {
      // 超管账号不作为团队负责人候选
      platformUserOptions.value = (res.data || []).filter(
        (u: UserVO) => u.role !== 'super_admin'
      );
    } else {
      platformUserOptions.value = [];
    }
  } catch (e) {
    console.error(e);
    platformUserOptions.value = [];
  }
};

const handleCancel = () => {
  // 已填写内容时关闭需确认，避免误点丢失
  const dirty =
    !!editingTeam.value.teamName ||
    !!editingTeam.value.description ||
    editingTeam.value.ownerId != null;
  if (dirty) {
    Modal.confirm({
      title: '放弃创建',
      content: '已填写的内容尚未保存，确定放弃吗？',
      okText: '放弃',
      cancelText: '继续编辑',
      onOk: () => {
        visible.value = false;
      },
    });
    return;
  }
  visible.value = false;
};

const handleOkClick = async () => {
  const validateRes = await teamFormRef.value?.validate();
  if (validateRes) return;
  submitting.value = true;
  try {
    const res: any = await createTeam(editingTeam.value);
    if (res.code === 200) {
      Message.success('创建团队成功');
      visible.value = false;
      emit('success');
    } else {
      Message.error(res.msg || '创建团队失败');
    }
  } catch (e) {
    console.error(e);
    Message.error('创建团队失败');
  } finally {
    submitting.value = false;
  }
};
</script>
