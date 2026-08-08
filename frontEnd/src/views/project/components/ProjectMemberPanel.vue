<template>
  <div class="project-member-panel">
    <div class="panel-header">
      <div class="panel-title">
        项目成员
        <span class="panel-sub">管理本项目的成员与项目管理员</span>
      </div>
      <a-button v-if="canManage" type="primary" @click="openInvite()">
        <template #icon><icon-user-add /></template>
        邀请项目成员
      </a-button>
    </div>

    <LoadError v-if="loadError" @retry="loadMembers" />
    <a-table
      v-else
      :columns="columns"
      :data="sortedMembers"
      :loading="loading"
      :pagination="false"
      row-key="userId"
    >
      <template #user="{ record }">
        <a-space>
          <span>{{ record.nickname || record.username }}</span>
          <span v-if="record.username" class="username">({{ record.username }})</span>
          <a-tag v-if="String(record.userId) === String(loginUserId)" size="small" color="arcoblue">我</a-tag>
        </a-space>
      </template>
      <template #role="{ record }">
        <a-tag :color="record.roleCode === 'project_admin' ? 'red' : 'blue'">
          {{ record.roleName || record.roleCode || '-' }}
        </a-tag>
        <a-tag v-if="record.projectCreator" size="small" color="orange" style="margin-left: 4px">
          项目创建者
        </a-tag>
        <a-tag v-else-if="record.teamAdmin" size="small" color="purple" style="margin-left: 4px">
          团队管理员
        </a-tag>
      </template>
      <template #operations="{ record }">
        <a-space>
          <a-button
            v-if="canManage && canAssignRow(record)"
            type="text"
            size="small"
            @click="openAssign(record)"
          >
            <template #icon><icon-user /></template>
            分配角色
          </a-button>
          <a-popconfirm
            v-if="canManage && canRemove(record)"
            :content="`确定将成员「${record.nickname || record.username}」从本项目移除吗？移除后其将无法访问本项目。`"
            type="warning"
            @ok="handleRemove(record)"
          >
            <a-button type="text" status="danger" size="small">
              <template #icon><icon-delete /></template>
              移除
            </a-button>
          </a-popconfirm>
          <a-tooltip v-else-if="canManage" content="项目管理员/团队管理员不可移除">
            <a-button type="text" status="danger" size="small" disabled>移除</a-button>
          </a-tooltip>
          <span v-if="!canManage">-</span>
        </a-space>
      </template>
    </a-table>

    <!-- 分配项目角色弹窗 -->
    <a-modal
      v-model:visible="assignVisible"
      title="分配项目角色"
      :mask-closable="false"
      :unmount-on-close="true"
      @before-ok="handleAssignSubmit"
    >
      <a-form ref="formRef" :model="form" layout="vertical">
        <a-form-item label="成员">
          <span class="assign-target">{{ assignTargetName || '-' }}</span>
        </a-form-item>
        <a-form-item
          label="项目角色"
          field="roleId"
          :rules="[{ required: true, message: '请选择项目角色' }]"
        >
          <a-select v-model="form.roleId" placeholder="选择自定义模板角色">
            <a-option
              v-for="r in projectRoleOptions"
              :key="r.id"
              :value="r.id"
            >
              {{ r.name }}
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 邀请项目成员弹窗 -->
    <a-modal
      v-model:visible="inviteVisible"
      title="邀请项目成员"
      :mask-closable="false"
      :unmount-on-close="true"
      @before-ok="handleInviteSubmit"
    >
      <a-form ref="inviteFormRef" :model="inviteForm" layout="vertical">
        <a-form-item
          label="选择成员"
          field="userIds"
          :rules="[{ required: true, message: '请选择要邀请的成员' }]"
        >
          <a-select
            v-model="inviteForm.userIds"
            multiple
            allow-search
            placeholder="从团队成员中选择（已排除超管/团队管理员/已加入成员）"
            :max-tag-count="3"
          >
            <a-option
              v-for="m in inviteCandidates"
              :key="m.userId"
              :value="m.userId"
            >
              {{ m.nickname || m.username }}
              <span v-if="m.username" class="username">({{ m.username }})</span>
            </a-option>
          </a-select>
          <template #extra>
            <span v-if="!inviteCandidates.length" style="color: var(--color-text-3)">
              当前团队暂无可邀请的成员
            </span>
          </template>
        </a-form-item>
        <a-form-item
          label="项目角色"
          field="roleId"
          :rules="[{ required: true, message: '请选择项目角色' }]"
        >
          <a-select v-model="inviteForm.roleId" placeholder="为被邀请成员指定项目角色">
            <a-option
              v-for="r in inviteRoleOptions"
              :key="r.id"
              :value="r.id"
            >
              {{ r.name }}
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import { Message } from '@arco-design/web-vue';
import type { FormInstance } from '@arco-design/web-vue';
import { useUserStore } from '@/store';
import { usePermissionStore } from '@/store/modules/permission';
import {
  getProjectMembers,
  assignProjectRole,
  removeProjectMember,
  isProjectAdmin,
  getProjectRoleOptions,
} from '@/api/MyApi/project';
import { getTeamMembers } from '@/api/MyApi/team';
import { Role } from '@/types/domain/Role';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';

const props = defineProps<{
  projectId: number;
  teamId: string | number;
}>();

const userStore = useUserStore();
const permissionStore = usePermissionStore();

const { loading, loadError, track } = useLoadState();
const members = ref<any[]>([]);
const isAdminBackend = ref(false);

// 超管 / 项目管理员 / 团队管理员 可管理
const canManage = computed(
  () =>
    userStore.role === 'super_admin' ||
    isAdminBackend.value ||
    permissionStore.hasPermission('team:member:manage')
);

const loginUserId = computed(() => Number(userStore.id));
const isSuperAdmin = computed(() => userStore.role === 'super_admin');

// 当前登录用户固定显示在列表第一位
const sortedMembers = computed(() => {
  const list = [...members.value];
  const meIndex = list.findIndex(
    (m) => String(m.userId) === String(loginUserId.value)
  );
  if (meIndex > 0) {
    const [me] = list.splice(meIndex, 1);
    list.unshift(me);
  }
  return list;
});

// 非超管不能给自己分配/调整项目角色（防止自我提权）；
// 项目管理员（project.owner_id）的身份来自项目设置，分配角色不会改变其权限，无需展示入口
const canAssignRow = (record: any) => {
  if (record.projectOwner) return false;
  return isSuperAdmin.value || String(record.userId) !== String(loginUserId.value);
};

const columns = [
  { title: '成员', slotName: 'user' },
  { title: '项目角色', slotName: 'role' },
  { title: '操作', slotName: 'operations', width: 200 },
];

// 项目管理员 / 团队管理员 / 团队创建者 / 自己 不可移除（后端也会拦截）；
// 项目创建者不持有特殊身份，可正常移除
const canRemove = (record: any) => {
  if (record.userId === loginUserId.value) return false;
  if (record.projectOwner || record.teamAdmin || record.teamCreator) return false;
  return true;
};

const loadMembers = async () => {
  if (!props.projectId) {
    members.value = [];
    return;
  }
  const res: any = await track(getProjectMembers(props.projectId));
  if (res && res.code !== 200) loadError.value = true;
  members.value = res?.code === 200 && Array.isArray(res.data) ? res.data : [];
};

const loadCanManage = async () => {
  if (!props.projectId) return;
  try {
    const res: any = await isProjectAdmin(props.projectId);
    isAdminBackend.value = res.code === 200 ? !!res.data : false;
  } catch (e) {
    isAdminBackend.value = false;
  }
};

// ===== 分配弹窗 =====
const assignVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({
  userId: undefined as number | undefined,
  roleId: undefined as number | undefined,
});

const teamMemberOptions = ref<any[]>([]);
const projectRoleOptions = ref<Role[]>([]);
// 项目级角色只有自定义模板；项目管理员唯一来源是 project.owner_id，在项目设置中变更
const inviteRoleOptions = computed(() =>
  projectRoleOptions.value.filter((r) => r.scopeType === 'TEMPLATE')
);
const assignTargetName = ref('');

const loadAssignOptions = async () => {
  if (!props.teamId && props.teamId !== 0) {
    teamMemberOptions.value = [];
    projectRoleOptions.value = [];
    Message.warning('未获取到团队信息，请稍后重试');
    return;
  }
  // 候选成员：本团队成员（排除超管）
  try {
    const res: any = await getTeamMembers(props.teamId);
    teamMemberOptions.value =
      res.code === 200 && Array.isArray(res.data)
        ? res.data.filter((m: any) => !m.superAdmin && m.status === 1)
        : [];
  } catch (e) {
    teamMemberOptions.value = [];
  }
  // 项目级角色：仅自定义模板（项目管理员走 project.owner_id，不在此分配）；
  // 走项目级接口，项目管理员（非团队管理员）也可用，避免团队级 /roles 403
  try {
    const res: any = await getProjectRoleOptions(props.projectId);
    const all: Role[] = res.code === 200 && res.data ? res.data : [];
    projectRoleOptions.value = all.filter((r) => r.scopeType === 'TEMPLATE');
  } catch (e) {
    projectRoleOptions.value = [];
  }
};

const openAssign = async (record: any) => {
  form.userId = record.userId;
  form.roleId = record.roleId;
  assignTargetName.value = record.nickname || record.username || '';
  await loadAssignOptions();
  assignVisible.value = true;
};

const handleAssignSubmit = async (done: (closed: boolean) => void) => {
  const err = await formRef.value?.validate();
  if (err) {
    done(false);
    return;
  }
  try {
    const res: any = await assignProjectRole({
      projectId: props.projectId,
      userId: form.userId as number,
      roleId: form.roleId as number,
    });
    if (res.code === 200) {
      Message.success('分配成功');
      await loadMembers();
      done(true);
    } else {
      Message.error(res.msg || '分配失败');
      done(false);
    }
  } catch (e) {
    console.error(e);
    Message.error('分配失败');
    done(false);
  }
};

// ===== 邀请项目成员 =====
const inviteVisible = ref(false);
const inviteFormRef = ref<FormInstance>();
const inviteForm = reactive({
  userIds: [] as number[],
  roleId: undefined as number | undefined,
});

// 已加入本项目的用户ID集合（含项目管理员/创建者）
const projectMemberIds = computed(
  () => new Set(members.value.map((m) => String(m.userId)))
);

// 可邀请候选：本团队成员，排除 超管 / 团队管理员 / 已加入本项目的人
const inviteCandidates = computed(() =>
  teamMemberOptions.value.filter(
    (m: any) =>
      !m.superAdmin &&
      m.status === 1 &&
      m.roleCode !== 'admin' &&
      m.roleCode !== 'team_admin' &&
      String(m.userId) !== String(loginUserId.value) &&
      !projectMemberIds.value.has(String(m.userId))
  )
);

const openInvite = async () => {
  inviteForm.userIds = [];
  inviteForm.roleId = undefined;
  await loadAssignOptions();
  // 默认选中内置只读模板「项目成员」，新成员至少有全项目查看权限
  const defaultRole = inviteRoleOptions.value.find(
    (r) => r.code === 'project_member'
  );
  inviteForm.roleId = (defaultRole?.id ?? inviteRoleOptions.value[0]?.id) as
    | number
    | undefined;
  inviteVisible.value = true;
};

const handleInviteSubmit = async (done: (closed: boolean) => void) => {
  const err = await inviteFormRef.value?.validate();
  if (err) {
    done(false);
    return;
  }
  try {
    for (const uid of inviteForm.userIds) {
      // eslint-disable-next-line no-await-in-loop
      await assignProjectRole({
        projectId: props.projectId,
        userId: Number(uid),
        roleId: inviteForm.roleId as number,
      });
    }
    Message.success('邀请成功');
    await loadMembers();
    done(true);
  } catch (e) {
    console.error(e);
    Message.error('邀请失败');
    done(false);
  }
};

const handleRemove = async (record: any) => {
  try {
    const res: any = await removeProjectMember({
      projectId: props.projectId,
      userId: record.userId,
    });
    if (res.code === 200) {
      Message.success('已移除');
      await loadMembers();
    } else {
      Message.error(res.msg || '移除失败');
    }
  } catch (e) {
    console.error(e);
    Message.error('移除失败');
  }
};

watch(
  () => props.projectId,
  () => {
    loadMembers();
    loadCanManage();
  },
  { immediate: true }
);
</script>

<style scoped lang="less">
.project-member-panel {
  padding: 4px 0;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.panel-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-1);
}

.panel-sub {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-3);
}

.username {
  color: var(--color-text-3);
  font-size: 12px;
}

.assign-target {
  font-weight: 600;
  color: var(--color-text-1);
}
</style>
