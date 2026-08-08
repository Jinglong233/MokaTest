<template>
  <div class="platform-overview">
    <!-- 顶部页头 -->
    <div class="overview-header">
      <div class="header-left">
        <h2 class="overview-title">平台总览</h2>
        <p class="overview-subtitle">全平台运行状态与核心指标监控</p>
      </div>
      <div class="header-right">
        <a-radio-group v-model="timeRange" type="button" size="small">
          <a-radio :value="7">近 7 天</a-radio>
          <a-radio :value="30">近 30 天</a-radio>
          <a-radio :value="90">近 90 天</a-radio>
        </a-radio-group>
        <a-button size="small" @click="loadData">
          <template #icon><icon-refresh :spin="loading" /></template>
          刷新
        </a-button>
        <a-button size="small" @click="handleExport">
          <template #icon><icon-download /></template>
          导出
        </a-button>
      </div>
    </div>

    <LoadError v-if="loadError" @retry="loadData" />

    <!-- 核心指标区 -->
    <a-grid v-show="!loadError" :cols="{ xs: 2, sm: 2, md: 4 }" :col-gap="16" :row-gap="16" class="kpi-grid">
      <a-grid-item v-for="item in kpiItems" :key="item.key">
        <a-card class="kpi-card" :class="{ alert: item.key === 'disabled' && item.value > 0 }" :bordered="false">
          <div class="kpi-top">
            <span class="kpi-label">{{ item.label }}</span>
            <span class="kpi-icon" :style="{ background: item.iconBg, color: item.color }">
              <component :is="item.icon" />
            </span>
          </div>
          <div class="kpi-bottom">
            <span class="kpi-value" :style="{ color: item.key === 'disabled' && item.value > 0 ? '#f53f3f' : 'var(--color-text-1)' }">
              {{ item.value }}
            </span>
            <!-- 禁用团队：风险哨兵 -->
            <span v-if="item.key === 'disabled'" class="kpi-badge" :class="item.value > 0 ? 'down' : 'up'">
              <icon-exclamation-circle v-if="item.value > 0" />
              <icon-check-circle v-else />
              {{ item.value > 0 ? '需关注' : '无异常' }}
            </span>
            <!-- 环比徽标 -->
            <span v-else class="kpi-badge" :class="item.mom?.direction">
              <icon-arrow-rise v-if="item.mom?.direction === 'up'" />
              <icon-arrow-fall v-else-if="item.mom?.direction === 'down'" />
              <icon-minus v-else />
              {{ item.mom?.direction === 'flat' ? '持平' : `${Math.abs(item.mom?.delta || 0)}` }}
            </span>
          </div>
        </a-card>
      </a-grid-item>
    </a-grid>

    <!-- 图文双栏 -->
    <a-row v-show="!loadError" :gutter="16" class="trend-row">
      <a-col :xs="24" :md="16">
        <a-card class="block-card" :bordered="false" title="业务增长趋势（近 6 个月）">
          <template #extra>
            <a-radio-group v-model="trendMetric" type="button" size="mini">
              <a-radio value="project">项目</a-radio>
              <a-radio value="user">用户</a-radio>
              <a-radio value="team">团队</a-radio>
            </a-radio-group>
          </template>
          <Chart :options="trendOption" height="300px" />
        </a-card>
      </a-col>
      <a-col :xs="24" :md="8">
        <a-card class="block-card activity-card" :bordered="false" title="全平台最新动态">
          <template #extra>
            <a-link @click="goOperationLog">查看全部</a-link>
          </template>
          <a-timeline v-if="activities.length > 0" class="activity-timeline">
            <a-timeline-item v-for="(act, i) in activities" :key="i" :dot-color="act.color">
              <div class="activity-item">
                <span class="activity-icon" :style="{ background: act.iconBg, color: act.color }">
                  <component :is="act.icon" />
                </span>
                <div class="activity-content">
                  <div class="activity-text">
                    <strong>{{ act.subject }}</strong>
                    {{ act.action }}
                    <strong v-if="act.target">{{ act.target }}</strong>
                    <a-tag v-if="act.isSuper" size="small" color="red">超管</a-tag>
                  </div>
                  <div class="activity-time">{{ formatDate(act.time) }}</div>
                </div>
              </div>
            </a-timeline-item>
          </a-timeline>
          <a-empty v-else description="所选时间范围内暂无动态" />
        </a-card>
      </a-col>
    </a-row>

    <!-- 团队数据概览表 -->
    <a-card v-show="!loadError" class="block-card" :bordered="false">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="table-title">团队数据概览</span>
          <a-radio-group v-model="statusFilter" type="button" size="small">
            <a-radio value="all">全部</a-radio>
            <a-radio value="normal">正常</a-radio>
            <a-radio value="disabled">已禁用</a-radio>
          </a-radio-group>
        </div>
        <a-space>
          <a-input-search
            v-model="teamKeyword"
            placeholder="搜索团队名称"
            allow-clear
            style="width: 240px"
          />
          <a-button v-if="isSuperAdmin" type="primary" size="small" @click="createTeamVisible = true">
            <template #icon><icon-plus /></template>
            新建团队
          </a-button>
        </a-space>
      </div>
      <a-table
        :columns="teamColumns"
        :data="filteredTeams"
        :loading="loading"
        :pagination="{ pageSize: 8, showTotal: true }"
        row-key="id"
      >
        <template #teamName="{ record }">
          <div class="team-cell">
            <span class="team-avatar" :style="{ background: getTeamColor(record.teamName) }">
              <icon-user-group />
            </span>
            <span class="team-name-text" :title="record.teamName">{{ record.teamName }}</span>
            <a-tag v-if="record.isPersonal === 1" size="small" color="purple">个人</a-tag>
          </div>
        </template>
        <template #projectCount="{ record }">
          {{ projectCountByTeam[String(record.id)] || 0 }}
        </template>
        <template #status="{ record }">
          <a-badge
            :status="record.status === 1 ? 'success' : 'danger'"
            :text="record.status === 1 ? '正常' : '已禁用'"
          />
        </template>
        <template #createUser="{ record }">
          <a-space :size="6">
            <span>{{ record.createUserName || record.createUserId || '-' }}</span>
            <a-tag v-if="superAdminIds.has(String(record.createUserId))" size="small" color="red">超管</a-tag>
          </a-space>
        </template>
        <template #createTime="{ record }">
          {{ formatDate(record.createdAt) }}
        </template>
        <template #operations="{ record }">
          <a-button
            type="text"
            size="small"
            :disabled="record.status === 0"
            @click="enterTeam(record)"
          >
            <template #icon><icon-to-right /></template>
            进入
          </a-button>
        </template>
        <template #empty>
          <a-empty description="没有匹配的团队数据" />
        </template>
      </a-table>
    </a-card>

    <!-- 新建团队弹窗（仅超管入口可见） -->
    <CreateTeamModal v-model:visible="createTeamVisible" @success="loadData" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import { Message } from '@arco-design/web-vue';
import type { TableColumnData } from '@arco-design/web-vue';
import * as echarts from 'echarts/core';
import Chart from '@/components/chart/index.vue';
import { getTeamList } from '@/api/MyApi/team';
import { getUserList, getUserInfo } from '@/api/MyApi/user';
import { allProject } from '@/api/MyApi/project';
import useTeamStore from '@/store/modules/team';
import useDataStore from '@/store/modules/nav';
import { Team } from '@/types/domain/Team';
import { UserVO } from '@/types/vo/UserVO';
import { Project } from '@/types/domain/Project';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';
import CreateTeamModal from '../components/CreateTeamModal.vue';

const router = useRouter();
const teamStore = useTeamStore();
const dataStore = useDataStore();

const { loading, loadError, track } = useLoadState();
const teamList = ref<Team[]>([]);
const userList = ref<UserVO[]>([]);
const projectList = ref<Project[]>([]);

// 仅超管可新建团队
const currentUserRole = ref('');
const isSuperAdmin = computed(() => currentUserRole.value === 'super_admin');
const createTeamVisible = ref(false);

const loadCurrentUserRole = async () => {
  try {
    const res: any = await getUserInfo();
    if (res.code === 200 && res.data) {
      currentUserRole.value = res.data.role || '';
    }
  } catch (e) {
    console.error(e);
  }
};

// 时间范围（天），影响环比窗口与动态过滤
const timeRange = ref<7 | 30 | 90>(30);

const formatDate = (d?: Date | string) => (d ? dayjs(d).format('YYYY-MM-DD HH:mm') : '-');

// 每个团队的项目数
const projectCountByTeam = computed(() => {
  const map: Record<string, number> = {};
  projectList.value.forEach((p) => {
    const tid = String(p.teamId ?? '');
    if (!tid) return;
    map[tid] = (map[tid] || 0) + 1;
  });
  return map;
});

const superAdminIds = computed(
  () => new Set(userList.value.filter((u) => u.role === 'super_admin').map((u) => String(u.id)))
);

// 环比：当前时间窗新增 vs 上一等长窗口新增
const momCompare = (list: any[], dateField: string) => {
  const days = timeRange.value;
  const now = dayjs();
  const curStart = now.subtract(days, 'day');
  const prevStart = now.subtract(days * 2, 'day');
  let cur = 0;
  let prev = 0;
  list.forEach((it) => {
    const t = it[dateField];
    if (!t) return;
    const d = dayjs(t);
    if (d.isAfter(curStart)) cur += 1;
    else if (d.isAfter(prevStart)) prev += 1;
  });
  const delta = cur - prev;
  return { cur, prev, delta, direction: delta > 0 ? 'up' : delta < 0 ? 'down' : 'flat' };
};

const kpiItems = computed(() => [
  {
    key: 'team',
    label: '团队总数',
    value: teamList.value.length,
    color: '#165dff',
    iconBg: '#e8f3ff',
    icon: 'icon-user-group',
    mom: momCompare(teamList.value, 'createdAt'),
  },
  {
    key: 'project',
    label: '项目总数',
    value: projectList.value.length,
    color: '#00b42a',
    iconBg: '#e8ffea',
    icon: 'icon-apps',
    mom: momCompare(projectList.value, 'createdAt'),
  },
  {
    key: 'user',
    label: '用户总数',
    value: userList.value.length,
    color: '#ff7d00',
    iconBg: '#fff7e8',
    icon: 'icon-user',
    mom: momCompare(userList.value, 'createTime'),
  },
  {
    key: 'disabled',
    label: '禁用团队',
    value: teamList.value.filter((t) => t.status === 0).length,
    color: '#f53f3f',
    iconBg: '#ffece8',
    icon: 'icon-exclamation-circle',
  },
]);

// ========== 业务增长趋势（近 6 个月累计规模） ==========
const trendMetric = ref<'project' | 'user' | 'team'>('project');

const metricMeta = {
  project: { name: '项目', color: '#00b42a', list: () => projectList.value, field: 'createdAt' },
  user: { name: '用户', color: '#ff7d00', list: () => userList.value, field: 'createTime' },
  team: { name: '团队', color: '#165dff', list: () => teamList.value, field: 'createdAt' },
};

const monthBuckets = computed(() => {
  const now = dayjs();
  return Array.from({ length: 6 }, (_, i) => now.subtract(5 - i, 'month'));
});

const trendOption = computed(() => {
  const meta = metricMeta[trendMetric.value];
  const labels = monthBuckets.value.map((d) => d.format('M月'));
  const data = monthBuckets.value.map((d) => {
    const end = d.endOf('month').valueOf();
    return meta.list().filter((it: any) => it[meta.field] && dayjs(it[meta.field]).valueOf() <= end).length;
  });
  return {
    grid: { top: 24, left: 44, right: 20, bottom: 32 },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line', lineStyle: { color: '#c9cdd4', type: 'dashed' } },
      formatter: (params: any) => {
        const p = params[0];
        return `<div style="display:flex;align-items:center;gap:6px">
          <span style="display:inline-block;width:8px;height:8px;border-radius:50%;background:${meta.color}"></span>
          <span>${p.axisValue} ${meta.name}：<strong>${p.value}</strong></span>
        </div>`;
      },
    },
    xAxis: {
      type: 'category',
      data: labels,
      boundaryGap: false,
      axisLine: { lineStyle: { color: '#e5e6eb' } },
      axisLabel: { color: '#86909c' },
    },
    yAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#f2f3f5' } },
      axisLabel: { color: '#86909c' },
    },
    series: [
      {
        name: meta.name,
        type: 'line',
        smooth: true,
        data,
        symbol: 'circle',
        symbolSize: 8,
        showSymbol: true,
        lineStyle: { color: meta.color, width: 3 },
        itemStyle: { color: meta.color, borderColor: '#fff', borderWidth: 2 },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: `${meta.color}40` },
            { offset: 1, color: `${meta.color}05` },
          ]),
        },
      },
    ],
  };
});

// ========== 全平台最新动态 ==========
const activities = computed(() => {
  const rangeStart = dayjs().subtract(timeRange.value, 'day');
  const userActs = userList.value.map((u) => ({
    type: 'user',
    icon: 'icon-user-add',
    color: '#165dff',
    iconBg: '#e8f3ff',
    subject: u.nickname || u.username || '新用户',
    action: '注册了平台账号',
    target: '',
    time: u.createTime,
    isSuper: u.role === 'super_admin',
  }));
  const teamActs = teamList.value.map((t) => ({
    type: 'team',
    icon: 'icon-user-group',
    color: '#00b42a',
    iconBg: '#e8ffea',
    subject: t.createUserName || t.createUserId || '用户',
    action: '创建了团队',
    target: t.teamName,
    time: t.createdAt,
    isSuper: superAdminIds.value.has(String(t.createUserId)),
  }));
  return [...userActs, ...teamActs]
    .filter((a) => a.time && dayjs(a.time).isAfter(rangeStart))
    .sort((a, b) => dayjs(b.time).valueOf() - dayjs(a.time).valueOf())
    .slice(0, 8);
});

const goOperationLog = () => {
  router.push({ name: 'OperationLog' });
};

// ========== 团队数据概览表 ==========
const statusFilter = ref<'all' | 'normal' | 'disabled'>('all');
const teamKeyword = ref('');

const filteredTeams = computed(() =>
  teamList.value.filter((t) => {
    if (statusFilter.value === 'normal' && t.status !== 1) return false;
    if (statusFilter.value === 'disabled' && t.status !== 0) return false;
    const kw = teamKeyword.value.trim().toLowerCase();
    if (kw && !(t.teamName || '').toLowerCase().includes(kw)) return false;
    return true;
  })
);

const teamColumns: TableColumnData[] = [
  { title: '团队名称', slotName: 'teamName', ellipsis: true, tooltip: true },
  { title: '成员数', dataIndex: 'teamNumber', width: 90, align: 'center' },
  { title: '项目数', slotName: 'projectCount', width: 90, align: 'center' },
  { title: '状态', slotName: 'status', width: 110, align: 'center' },
  { title: '创建人', slotName: 'createUser', width: 180, ellipsis: true, tooltip: true },
  { title: '创建时间', slotName: 'createTime', width: 160 },
  { title: '操作', slotName: 'operations', width: 100, align: 'center' },
];

const teamColors = ['#165dff', '#00b42a', '#ff7d00', '#722ed1', '#14c9c9', '#f53f3f'];
const getTeamColor = (name?: string) => {
  if (!name) return teamColors[0];
  let hash = 0;
  for (let i = 0; i < name.length; i += 1) hash += name.charCodeAt(i);
  return teamColors[hash % teamColors.length];
};

// 导出当前筛选结果为 CSV
const handleExport = () => {
  const header = ['团队名称', '成员数', '项目数', '状态', '创建人', '创建时间'];
  const rows = filteredTeams.value.map((t) => [
    t.teamName,
    t.teamNumber ?? 0,
    projectCountByTeam.value[String(t.id)] || 0,
    t.status === 1 ? '正常' : '已禁用',
    t.createUserName || t.createUserId || '-',
    formatDate(t.createdAt),
  ]);
  const csv = [header, ...rows]
    .map((r) => r.map((c) => `"${String(c ?? '').replace(/"/g, '""')}"`).join(','))
    .join('\n');
  const blob = new Blob([`﻿${csv}`], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `团队数据概览_${dayjs().format('YYYYMMDD')}.csv`;
  a.click();
  URL.revokeObjectURL(url);
  Message.success(`已导出 ${filteredTeams.value.length} 条团队数据`);
};

// 进入团队 → 跳转全屏工作台（面包屑可返回总览，形成闭环）
const enterTeam = async (team: Team) => {
  if (team.status === 0) return;
  await teamStore.setTeam(String(team.id), team.teamName as string);
  await dataStore.fetchData();
  router.push('/team/workspace');
};

const loadData = async () => {
  const results = await track(Promise.all([
    getTeamList(),
    getUserList(),
    allProject(),
  ])) as any[] | null;
  if (!results) return;
  const [teamRes, userRes, projectRes] = results;
  teamList.value = teamRes?.code === 200 && Array.isArray(teamRes.data) ? teamRes.data : [];
  userList.value = userRes?.code === 200 && Array.isArray(userRes.data) ? userRes.data : [];
  // allProject 走 ResponseVO（success），数据在 data 上
  projectList.value = Array.isArray(projectRes?.data) ? projectRes.data : [];
};

onMounted(() => {
  loadCurrentUserRole();
  loadData();
});
</script>

<style scoped lang="less">
.platform-overview {
  padding: 0 20px 20px;
}

.overview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
  flex-wrap: wrap;
  gap: 12px;

  .overview-title {
    font-size: 20px;
    font-weight: 600;
    color: var(--color-text-1);
    margin: 0 0 4px;
  }

  .overview-subtitle {
    font-size: 13px;
    color: var(--color-text-3);
    margin: 0;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.kpi-grid {
  margin-bottom: 16px;
}

.kpi-card {
  border-radius: 10px;
  border: 1px solid var(--color-border-2);
  transition: box-shadow 0.2s ease;

  &:hover {
    box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  }

  &.alert {
    border-color: #f53f3f55;
    background: linear-gradient(180deg, #fff5f4 0%, #ffffff 60%);
  }

  :deep(.arco-card-body) {
    padding: 16px 20px;
  }
}

.kpi-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.kpi-label {
  font-size: 13px;
  color: var(--color-text-3);
}

.kpi-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.kpi-bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
}

.kpi-value {
  font-size: 28px;
  font-weight: 700;
  line-height: 1.2;
}

.kpi-badge {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 10px;

  &.up {
    color: #00b42a;
    background: #e8ffea;
  }

  &.down {
    color: #f53f3f;
    background: #ffece8;
  }

  &.flat {
    color: #86909c;
    background: #f2f3f5;
  }
}

.trend-row {
  margin-bottom: 16px;
}

.block-card {
  border-radius: 10px;
  border: 1px solid var(--color-border-2);
  height: 100%;
}

.activity-card {
  :deep(.arco-card-body) {
    max-height: 344px;
    overflow-y: auto;
  }
}

.activity-timeline {
  padding-left: 4px;
  margin-top: 4px;
}

.activity-item {
  display: flex;
  gap: 10px;
  padding-bottom: 4px;
}

.activity-icon {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
}

.activity-content {
  flex: 1;
  min-width: 0;
}

.activity-text {
  font-size: 13px;
  color: var(--color-text-2);
  line-height: 1.6;

  strong {
    color: var(--color-text-1);
    font-weight: 600;
  }

  .arco-tag {
    margin-left: 4px;
  }
}

.activity-time {
  font-size: 12px;
  color: var(--color-text-3);
  margin-top: 2px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
  gap: 12px;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 16px;
  }

  .table-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text-1);
  }
}

.team-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.team-avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 15px;
  flex-shrink: 0;
}

.team-name-text {
  font-weight: 500;
  color: var(--color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
