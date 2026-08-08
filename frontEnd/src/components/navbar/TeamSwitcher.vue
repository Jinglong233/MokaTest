<template>
  <a-dropdown
    v-if="visible"
    trigger="click"
    position="bl"
    @select="handleSelect"
  >
    <a-button type="text" size="small" class="team-switcher-trigger">
      <template #icon>
        <icon-apps />
      </template>
      <span class="team-name">{{ currentTeamName }}</span>
      <icon-down class="team-arrow" />
    </a-button>
    <template #content>
      <template v-if="managedTeams.length">
        <div class="team-group-title">我管理的团队</div>
        <a-doption
          v-for="team in managedTeams"
          :key="team.id"
          :value="team.id"
          :class="{ 'team-option-active': team.id === teamStore.teamId }"
        >
          <template #icon>
            <icon-check v-if="team.id === teamStore.teamId" />
          </template>
          <a-space>
            <span class="team-option-name">{{ team.teamName }}</span>
            <a-tag size="mini" color="arcoblue">管理</a-tag>
          </a-space>
        </a-doption>
      </template>
      <template v-if="joinedTeams.length">
        <div class="team-group-title">我加入的团队</div>
        <a-doption
          v-for="team in joinedTeams"
          :key="team.id"
          :value="team.id"
          :class="{ 'team-option-active': team.id === teamStore.teamId }"
        >
          <template #icon>
            <icon-check v-if="team.id === teamStore.teamId" />
          </template>
          <span class="team-option-name">{{ team.teamName }}</span>
        </a-doption>
      </template>
      <a-empty
        v-if="!teamList.length && !loading"
        style="padding: 12px 0"
        description="暂无团队"
      />
    </template>
  </a-dropdown>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { Message } from '@arco-design/web-vue';
import { useUserStore, useTeamStore } from '@/store';
import useDataStore from '@/store/modules/nav';
import { getTeamList } from '@/api/MyApi/team';
import { Team } from '@/types/domain/Team';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();
const teamStore = useTeamStore();
const dataStore = useDataStore();

const visible = computed(() => route.path.startsWith('/team') && userStore.role !== 'super_admin');

const teamList = ref<Team[]>([]);
const loading = ref(false);

const currentTeamName = computed(() => {
  if (teamStore.teamName) return teamStore.teamName;
  const current = teamList.value.find((t) => t.id === teamStore.teamId);
  return current?.teamName || '选择团队';
});

const isManagedTeam = (team: Team) => {
  if (userStore.role === 'super_admin') return true;
  return team.manageable === true;
};

const managedTeams = computed(() => teamList.value.filter((t) => isManagedTeam(t)));
const joinedTeams = computed(() => teamList.value.filter((t) => !isManagedTeam(t)));

const loadTeams = async () => {
  if (!visible.value) return;
  loading.value = true;
  try {
    const { data } = await getTeamList();
    teamList.value = data || [];
  } catch (e) {
    console.error('加载团队列表失败', e);
  } finally {
    loading.value = false;
  }
};

const handleSelect = async (value: string | number | Record<string, any> | undefined) => {
  const teamId = Number(value);
  const team = teamList.value.find((t) => t.id === teamId);
  if (!team) return;
  if (team.status === 0) {
    Message.warning('该团队已禁用，无法切换');
    return;
  }
  await teamStore.setTeam(String(teamId), team.teamName || '');
  await dataStore.fetchData();
  // 保持在当前路由，由页面自身的 watcher 或重新加载来刷新数据
  router.replace({ path: route.path, query: { ...route.query } });
};

onMounted(() => {
  loadTeams();
});
</script>

<style scoped lang="less">
.team-switcher-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-text-1);

  .team-name {
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .team-arrow {
    font-size: 12px;
    color: var(--color-text-3);
  }
}

.team-group-title {
  padding: 6px 12px;
  font-size: 12px;
  color: var(--color-text-3);
}

.team-option-active {
  color: rgb(var(--primary-6));
  font-weight: 500;
}

.team-option-name {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
