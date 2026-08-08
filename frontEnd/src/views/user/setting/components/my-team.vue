<template>
  <LoadError v-if="loadError" @retry="loadTeams" />
  <a-list v-else :bordered="false" :loading="loading">
    <a-list-item
        v-for="team in teamList"
        :key="team.id"
        action-layout="horizontal"
        class="team-item"
        @click="enterTeam(team)"
    >
      <a-list-item-meta>
        <template #avatar>
          <a-avatar :style="{ backgroundColor: 'rgb(var(--arcoblue-6))' }">
            {{ (team.teamName || '').slice(0, 1) }}
          </a-avatar>
        </template>
        <template #title>
          <a-space>
            {{ team.teamName }}
            <a-tag v-if="userStore.userInfo.id == team.createUserId" color="green">
              拥有者
            </a-tag>
          </a-space>
        </template>
        <template #description>共 {{ team.teamNumber }} 人</template>
      </a-list-item-meta>
      <template #actions>
        <icon-right />
      </template>
    </a-list-item>
    <template #empty>
      <a-empty description="暂无团队"/>
    </template>
  </a-list>
</template>

<script lang="ts" setup>

import {onMounted, ref} from "vue";
import {useRouter} from "vue-router";
import {getTeamList} from "@/api/MyApi/team";
import {useUserStore} from "@/store";
import useTeamStore from "@/store/modules/team";
import useDataStore from "@/store/modules/nav";
import useLoadState from "@/hooks/useLoadState";
import LoadError from "@/components/load-error/index.vue";

const router = useRouter();
const teamList = ref<any[]>([]);
const {loading, loadError, track} = useLoadState();
const userStore = useUserStore();
const teamStore = useTeamStore();
const dataStore = useDataStore();

const loadTeams = async () => {
  const res: any = await track(getTeamList());
  teamList.value = res?.data || [];
};

// 点击团队：切换上下文并进入团队工作台
const enterTeam = async (team: any) => {
  if (!team?.id) return;
  await teamStore.setTeam(String(team.id), team.teamName || '');
  await dataStore.fetchData();
  router.push('/team/workspace');
};

onMounted(loadTeams);
</script>

<style scoped lang="less">
.team-item {
  cursor: pointer;
  border-radius: 8px;
  padding: 12px;
  transition: background 0.2s;

  &:hover,
  &:focus-within {
    background: var(--color-fill-2);
  }
}
</style>
