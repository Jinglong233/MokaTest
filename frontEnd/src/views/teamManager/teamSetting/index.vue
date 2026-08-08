<template>
  <div class="team-setting" :class="{ embedded: embeddedInWorkspace }">
    <div v-if="!embeddedInWorkspace" class="page-header">
      <span class="page-title">团队管理</span>
      <a-space>
        <a-button
          v-permission="'team:member:manage'"
          type="primary"
          @click="openEdit"
        >
          <template #icon><icon-edit /></template>
          编辑团队
        </a-button>
        <a-button
          v-if="canDissolve"
          status="danger"
          @click="openDissolve"
        >
          <template #icon><icon-delete /></template>
          解散团队
        </a-button>
      </a-space>
    </div>

    <a-spin v-if="loading" :loading="loading" style="width: 100%; padding-top: 120px" />

    <LoadError v-else-if="loadError" style="margin-top: 80px" @retry="loadCurrentTeam" />

    <template v-else>
      <a-empty v-if="!currentTeam" description="请先在顶部选择一个团队" style="margin-top: 120px" />

      <a-card v-else class="info-card" :bordered="!embeddedInWorkspace">
        <a-descriptions :column="1" size="large" title="团队信息">
          <a-descriptions-item label="团队名称">
            {{ currentTeam.teamName || '-' }}
            <a-tag v-if="currentTeam.isPersonal === 1" size="small" color="purple" style="margin-left: 8px">
              个人团队
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="状态">
            <a-tag :color="currentTeam.status === 1 ? 'green' : 'red'">
              {{ currentTeam.status === 1 ? '正常' : '已禁用' }}
            </a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="团队人数">
            {{ currentTeam.teamNumber ?? '-' }}
          </a-descriptions-item>
          <a-descriptions-item label="描述">
            {{ currentTeam.description || '暂无描述' }}
          </a-descriptions-item>
          <a-descriptions-item label="创建时间">
            {{ formatDate(currentTeam.createdAt) }}
          </a-descriptions-item>
        </a-descriptions>

        <!-- 嵌入模式：在卡片内展示操作按钮 -->
        <a-space v-if="embeddedInWorkspace" style="margin-top: 20px">
          <a-button
            v-permission="'team:member:manage'"
            type="primary"
            @click="openEdit"
          >
            <template #icon><icon-edit /></template>
            编辑团队
          </a-button>
          <a-button
            v-if="canDissolve"
            status="danger"
            @click="openDissolve"
          >
            <template #icon><icon-delete /></template>
            解散团队
          </a-button>
        </a-space>
      </a-card>
    </template>

    <!-- 解散团队确认弹窗：高危操作，需输入完整团队名称确认 -->
    <a-modal
      v-model:visible="dissolveVisible"
      title="解散团队"
      :mask-closable="false"
      :ok-loading="dissolving"
      :ok-button-props="{
        status: 'danger',
        disabled: dissolveConfirmText !== currentTeam?.teamName,
      }"
      ok-text="确认解散"
      cancel-text="取消"
      @ok="handleDissolve"
      @cancel="dissolveVisible = false"
    >
      <a-alert type="error" style="margin-bottom: 16px">
        此操作不可恢复。解散后，团队成员关系将被移除，团队下的项目及其需求、用例、BUG
        等数据将无法访问。
      </a-alert>
      <p style="margin-bottom: 8px; color: var(--color-text-2)">
        请输入团队名称
        <span style="font-weight: 600; color: var(--color-text-1)">{{ currentTeam?.teamName }}</span>
        以确认解散：
      </p>
      <a-input
        v-model="dissolveConfirmText"
        placeholder="请输入完整团队名称"
        allow-clear
      />
    </a-modal>

    <!-- 编辑团队弹窗 -->
    <a-modal
      v-model:visible="formVisible"
      title="编辑团队"
      :mask-closable="false"
      :esc-to-close="true"
      :unmount-on-close="true"
      @before-ok="handleBeforeOk"
    >
      <a-form ref="formRef" :model="form" layout="vertical">
        <a-form-item field="teamName" label="团队名称" :rules="rules.teamName">
          <a-input v-model="form.teamName" placeholder="请输入团队名称" />
        </a-form-item>
        <a-form-item field="description" label="描述">
          <a-textarea
            v-model="form.description"
            placeholder="请输入团队描述"
            :auto-size="{ minRows: 3, maxRows: 5 }"
          />
        </a-form-item>
        <a-form-item field="status" label="状态">
          <a-switch
            :model-value="form.status === 1"
            type="round"
            @change="(v) => (form.status = v ? 1 : 0)"
          />
        </a-form-item>
        <a-form-item field="ownerId" label="团队管理员">
          <a-select
            v-model="form.ownerId"
            placeholder="请选择团队管理员"
            allow-search
            allow-clear
          >
            <a-option
              v-for="member in adminMemberOptions"
              :key="member.userId"
              :value="member.userId"
            >
              {{ member.nickname || member.username }}
              <span
                v-if="member.username"
                style="color: var(--color-text-3); margin-left: 4px"
                >({{ member.username }})</span
              >
            </a-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import type { FormInstance } from '@arco-design/web-vue';
import dayjs from 'dayjs';
import useTeamStore from '@/store/modules/team';
import useDataStore from '@/store/modules/nav';
import usePermission from '@/hooks/permission';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';
import { Team } from '@/types/domain/Team';
import type { TeamMemberVO } from '@/types/vo/TeamMemberVO';
import {
  getTeamList,
  updateTeam,
  deleteTeam,
  getTeamMembers,
} from '@/api/MyApi/team';

const router = useRouter();
const teamStore = useTeamStore();
const dataStore = useDataStore();
const permission = usePermission();

const props = defineProps<{ embeddedInWorkspace?: boolean }>();

const { loading, loadError, track } = useLoadState();
const teamList = ref<Team[]>([]);
const currentTeam = ref<Team | null>(null);

// 解散团队确认
const dissolveVisible = ref(false);
const dissolveConfirmText = ref('');
const dissolving = ref(false);

const formVisible = ref(false);
const formRef = ref<FormInstance>();
const form = ref<Team>(new Team());
const memberList = ref<TeamMemberVO[]>([]);
const adminMemberOptions = ref<TeamMemberVO[]>([]);

const rules = {
  teamName: [
    { required: true, message: '请填写团队名称' },
    { maxLength: 10, message: '团队名称长度最大为10' },
    { minLength: 5, message: '团队名称长度最小为5' },
    {
      match: /^[a-zA-Z0-9_一-龥]+$/,
      message: '团队名称只能包含汉字、字母、数字和下划线',
    },
  ],
};

// 个人团队不可解散；其余有 team:member:manage 即可解散（后端再次校验）
const canDissolve = computed(() => {
  if (!currentTeam.value) return false;
  if (currentTeam.value.isPersonal === 1) return false;
  return permission.hasPermission('team:member:manage');
});

const formatDate = (date?: Date | string) => {
  if (!date) return '-';
  return dayjs(date).format('YYYY-MM-DD HH:mm');
};

const loadMembers = async (teamId: number) => {
  try {
    const res: any = await getTeamMembers(teamId);
    if (res.code === 200 && res.data) {
      memberList.value = res.data || [];
    } else {
      memberList.value = [];
    }
  } catch (e) {
    console.error(e);
    memberList.value = [];
  }
};

const loadCurrentTeam = async () => {
  if (!teamStore.teamId) {
    currentTeam.value = null;
    return;
  }
  const res: any = await track(getTeamList());
  if (!res) {
    currentTeam.value = null;
    return;
  }
  teamList.value = res.data || [];
  currentTeam.value =
    teamList.value.find((t) => String(t.id) === String(teamStore.teamId)) || null;
};

const openEdit = async () => {
  if (!currentTeam.value) {
    Message.warning('请先在顶部选择一个团队');
    return;
  }
  form.value = {
    ...currentTeam.value,
    status: currentTeam.value.status == null ? 1 : currentTeam.value.status,
  } as Team;

  if (currentTeam.value.id) {
    await loadMembers(currentTeam.value.id);
    // 团队管理员唯一来源是 team.owner_id；历史数据未迁移时兜底取成员中的 admin
    if (currentTeam.value.ownerId != null) {
      form.value.ownerId = Number(currentTeam.value.ownerId);
    } else {
      const admin = memberList.value.find(
        (m) => m.roleCode === 'admin' || m.roleCode === 'team_admin'
      );
      form.value.ownerId = admin?.userId ? Number(admin.userId) : undefined;
    }
    adminMemberOptions.value = memberList.value.filter((m) => m.status === 1);
  }

  formVisible.value = true;
};

const handleBeforeOk = async (done: (closed: boolean) => void) => {
  const err = await formRef.value?.validate();
  if (err) {
    done(false);
    return;
  }
  // 负责人 ID 统一转为 number，避免 select 返回值类型不一致
  if (form.value.ownerId != null) {
    form.value.ownerId = Number(form.value.ownerId);
  }
  try {
    // 团队管理员随 team.owner_id 一并保存（后端校验新管理员必须是本团队成员）
    const res: any = await updateTeam(form.value);
    if (res.code !== 200) {
      Message.error(res.msg || '更新失败');
      done(false);
      return;
    }

    Message.success('更新成功');
    // 同步团队名到全局 store
    teamStore.setTeamName(form.value.teamName || '');
    await loadCurrentTeam();
    done(true);
  } catch (e) {
    console.error(e);
    Message.error('更新失败');
    done(false);
  }
};

const openDissolve = () => {
  dissolveConfirmText.value = '';
  dissolveVisible.value = true;
};

const handleDissolve = async () => {
  if (!currentTeam.value?.id) return;
  if (dissolveConfirmText.value !== currentTeam.value.teamName) return;
  dissolving.value = true;
  try {
    const res: any = await deleteTeam(currentTeam.value.id);
    if (res.code === 200) {
      Message.success('团队已解散');
      dissolveVisible.value = false;
      // 切换到下一个可管理团队，没有则清空团队上下文
      const next = teamList.value.find(
        (t) => t.manageable && String(t.id) !== String(currentTeam.value?.id)
      );
      if (next?.id) {
        await teamStore.setTeam(String(next.id), next.teamName || '');
        await dataStore.fetchData();
      } else {
        teamStore.clearTeam();
      }
      router.replace({ name: 'TeamWorkspace' });
    } else {
      Message.error(res.msg || '解散失败');
    }
  } catch (e) {
    console.error(e);
    Message.error('解散失败');
  } finally {
    dissolving.value = false;
  }
};

watch(
  () => teamStore.teamId,
  () => {
    loadCurrentTeam();
  }
);

onMounted(() => {
  loadCurrentTeam();
});
</script>

<style scoped lang="less">
.team-setting {
  padding: 0 20px 20px;

  &.embedded {
    padding: 0;
  }
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-1);
}

.info-card {
  border-radius: 10px;
  max-width: 720px;
}
</style>
