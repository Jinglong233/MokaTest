<template>
  <div class="team-workspace">
    <!-- 顶部面包屑栏（吸顶半透明） -->
    <div class="breadcrumb-bar">
      <a-breadcrumb>
        <a-breadcrumb-item v-if="isSuperAdmin">
          <a class="crumb-link" @click="goOverview">平台总览</a>
        </a-breadcrumb-item>
        <a-breadcrumb-item>{{ teamStore.teamName || '-' }}</a-breadcrumb-item>
      </a-breadcrumb>
    </div>

    <a-layout class="workspace-layout">
      <!-- 左侧团队列表 -->
      <a-layout-sider class="team-sider" :width="260">
        <div class="sider-header">
          <span class="sider-title">{{ teamListTitle }}（{{ teamList.length }}）</span>
          <a-tooltip v-if="isSuperAdmin" content="新建团队">
            <a-button type="text" size="small" shape="circle" @click="openCreateTeam">
              <template #icon><icon-plus /></template>
            </a-button>
          </a-tooltip>
        </div>
        <a-input-search
          v-model="teamKeyword"
          class="team-search"
          placeholder="搜索团队"
          allow-clear
        />
        <div class="team-list">
          <div
            v-for="item in filteredTeamList"
            :key="String(item.id)"
            class="team-item"
            :class="{
              active: String(item.id) === String(teamStore.teamId),
              disabled: item.status === 0,
            }"
            role="button"
            :tabindex="item.status === 0 ? -1 : 0"
            :aria-label="`切换到团队 ${item.teamName}`"
            @click="switchTeam(item)"
            @keydown.enter.space.prevent="switchTeam(item)"
          >
            <div
              class="team-icon"
              :style="
                String(item.id) === String(teamStore.teamId)
                  ? { background: '#165dff', color: '#fff' }
                  : getTeamIconStyle(item.teamName)
              "
            >
              {{ (item.teamName || 'T').charAt(0) }}
            </div>
            <div class="team-item-info">
              <div class="team-item-name" :title="item.teamName">{{ item.teamName }}</div>
              <div class="team-item-meta">
                {{ item.teamNumber || 0 }} 成员 /
                {{ projectCountByTeam[String(item.id)] || 0 }} 项目
              </div>
            </div>
            <a-tag v-if="item.status === 0" size="small" color="red" class="team-role-tag">已禁用</a-tag>
            <a-tag v-else size="small" :color="getMyRoleTag(item).color" class="team-role-tag">
              {{ getMyRoleTag(item).text }}
            </a-tag>
          </div>
          <div v-if="filteredTeamList.length === 0" class="team-empty">
            <a-empty :description="teamKeyword ? '没有匹配的团队' : '暂无团队'" />
          </div>
        </div>
      </a-layout-sider>

      <!-- 中间主内容区 -->
      <a-layout-content class="workspace-content">
        <a-tabs v-model:activeKey="activeTab" class="workspace-tabs" type="line">
          <a-tab-pane key="projects" title="项目列表">
            <!-- 项目指标条（轻量卡） -->
            <a-grid
              :cols="{ xs: 2, sm: 2, md: 2, lg: 4 }"
              :colGap="16"
              :rowGap="16"
              class="stats-grid"
            >
              <a-grid-item v-for="s in projectStatItems" :key="s.key">
                <div class="stat-card">
                  <div class="stat-card-icon" :style="{ background: s.iconBg, color: s.color }">
                    <component :is="s.icon" />
                  </div>
                  <div class="stat-card-info">
                    <div class="stat-card-num">{{ s.value }}</div>
                    <div class="stat-card-label">{{ s.label }}</div>
                  </div>
                </div>
              </a-grid-item>
            </a-grid>

            <!-- 项目工具条 -->
            <div class="project-toolbar">
              <div class="toolbar-left">
                <a-radio-group v-model="projectStatusFilter" type="button" size="small">
                  <a-radio value="all">全部</a-radio>
                  <a-radio value="normal">正常</a-radio>
                  <a-radio value="disabled">已禁用</a-radio>
                </a-radio-group>
                <span class="toolbar-count">
                  <b>{{ displayProjects.length }}</b> 个项目
                </span>
              </div>
              <div class="toolbar-right">
                <a-input-search
                  v-model="projectKeyword"
                  placeholder="搜索项目"
                  allow-clear
                  style="width: 180px"
                />
                <a-select v-model="sortBy" size="small" style="width: 120px">
                  <a-option value="updated">最近更新</a-option>
                  <a-option value="name">名称</a-option>
                  <a-option value="cases">用例数量</a-option>
                </a-select>
                <a-radio-group v-model="viewMode" type="button" size="small">
                  <a-radio value="list"><icon-list /></a-radio>
                  <a-radio value="grid"><icon-apps /></a-radio>
                </a-radio-group>
                <a-button
                  v-if="permissionStore.hasPermission('project:create')"
                  type="primary"
                  size="small"
                  @click="openCreateProject"
                >
                  <template #icon><icon-plus /></template>
                  新建项目
                </a-button>
              </div>
            </div>

            <!-- 加载中 -->
            <a-spin
              v-if="loading"
              :loading="loading"
              style="width: 100%; padding-top: 120px"
            />
            <LoadError v-else-if="loadError" style="padding-top: 80px" @retry="loadProjects" />
            <template v-else>
              <!-- 列表视图 -->
              <a-list
                v-if="viewMode === 'list' && displayProjects.length > 0"
                :bordered="true"
                :max-height="projectListMaxHeight"
                :split="true"
                :scrollbar="true"
                :data="displayProjects"
                class="project-list"
              >
                <template #item="{ item }">
                  <a-list-item :class="{ disabled: isProjectDisabled(item) }">
                    <div class="project-item-body">
                      <div class="project-icon" :style="getProjectIconStyle(item.projectName)">
                        {{ item.projectName?.charAt(0) || 'P' }}
                      </div>
                      <div class="project-main">
                        <div class="project-title-row">
                          <span class="project-name-text" :title="item.projectName">
                            {{ item.projectName }}
                          </span>
                          <a-tag
                            class="status-pill"
                            :class="`status-${statusColor(item.status as string)}`"
                            size="small"
                          >
                            {{ statusText(item.status as string) }}
                          </a-tag>
                          <a-tag
                            v-if="item.myRoleName"
                            size="small"
                            :color="myRoleTagColor(item.myRoleCode)"
                          >{{ item.myRoleName }}</a-tag>
                          <span class="creator-line">
                            <icon-user class="creator-icon" />
                            <span>{{ item.createUserName || '-' }}</span>
                            <a-tag
                              v-if="superAdminIds.has(String(item.createUserId))"
                              size="small"
                              color="red"
                            >超管</a-tag>
                          </span>
                        </div>
                        <div class="project-desc-row" :title="item.description || ''">
                          {{ item.description || '暂无描述' }}
                        </div>
                        <div class="project-stats-row">
                          <span class="metric-chip" :class="{ active: (item.apiTotal || 0) > 0 }">
                            接口 {{ item.apiTotal || 0 }}
                          </span>
                          <span class="metric-chip" :class="{ active: (item.uiTotal || 0) > 0 }">
                            UI {{ item.uiTotal || 0 }}
                          </span>
                          <span class="metric-chip" :class="{ active: (item.planTotal || 0) > 0 }">
                            计划 {{ item.planTotal || 0 }}
                          </span>
                        </div>
                      </div>
                      <div class="project-action">
                        <a-tooltip content="编辑">
                          <a-button
                            v-permission="'project:update'"
                            type="text"
                            size="small"
                            shape="circle"
                            @click.stop="openEditProject(item)"
                          >
                            <template #icon><icon-edit /></template>
                          </a-button>
                        </a-tooltip>
                        <a-tooltip content="删除">
                          <a-button
                            v-permission="PROJECT_DELETE"
                            type="text"
                            size="small"
                            shape="circle"
                            class="danger-icon-btn"
                            @click.stop="handleDeleteProject(item)"
                          >
                            <template #icon><icon-delete /></template>
                          </a-button>
                        </a-tooltip>
                        <a-button
                          v-if="isProjectDisabled(item)"
                          type="text"
                          size="small"
                          disabled
                        >
                          不可进入
                        </a-button>
                        <a-button
                          v-else
                          type="primary"
                          size="small"
                          @click.stop="enterProject(item)"
                        >
                          进入
                        </a-button>
                      </div>
                    </div>
                  </a-list-item>
                </template>
              </a-list>

              <!-- 网格视图 -->
              <div v-else-if="viewMode === 'grid' && displayProjects.length > 0" class="project-grid-wrap">
                <a-grid :cols="{ xs: 1, sm: 1, md: 1, lg: 2 }" :colGap="16" :rowGap="16">
                  <a-grid-item v-for="item in displayProjects" :key="item.id">
                    <div class="project-grid-card" :class="{ disabled: isProjectDisabled(item) }">
                      <div class="grid-card-header">
                        <div class="project-icon" :style="getProjectIconStyle(item.projectName)">
                          {{ item.projectName?.charAt(0) || 'P' }}
                        </div>
                        <div class="grid-card-title">
                          <span class="project-name-text" :title="item.projectName">
                            {{ item.projectName }}
                          </span>
                          <a-tag
                            class="status-pill"
                            :class="`status-${statusColor(item.status as string)}`"
                            size="small"
                          >
                            {{ statusText(item.status as string) }}
                          </a-tag>
                          <a-tag
                            v-if="item.myRoleName"
                            size="small"
                            :color="myRoleTagColor(item.myRoleCode)"
                          >{{ item.myRoleName }}</a-tag>
                        </div>
                      </div>
                      <div class="project-desc-row" :title="item.description || ''">
                        {{ item.description || '暂无描述' }}
                      </div>
                      <div class="project-stats-row">
                        <span class="metric-chip" :class="{ active: (item.apiTotal || 0) > 0 }">
                          接口 {{ item.apiTotal || 0 }}
                        </span>
                        <span class="metric-chip" :class="{ active: (item.uiTotal || 0) > 0 }">
                          UI {{ item.uiTotal || 0 }}
                        </span>
                        <span class="metric-chip" :class="{ active: (item.planTotal || 0) > 0 }">
                          计划 {{ item.planTotal || 0 }}
                        </span>
                      </div>
                      <div class="grid-card-footer">
                        <span class="creator-line">
                          <icon-user class="creator-icon" />
                          <span>{{ item.createUserName || '-' }}</span>
                          <a-tag
                            v-if="superAdminIds.has(String(item.createUserId))"
                            size="small"
                            color="red"
                          >超管</a-tag>
                        </span>
                        <div class="project-action">
                          <a-tooltip content="编辑">
                            <a-button
                              v-permission="'project:update'"
                              type="text"
                              size="small"
                              shape="circle"
                              @click.stop="openEditProject(item)"
                            >
                              <template #icon><icon-edit /></template>
                            </a-button>
                          </a-tooltip>
                          <a-tooltip content="删除">
                            <a-button
                              v-permission="PROJECT_DELETE"
                              type="text"
                              size="small"
                              shape="circle"
                              class="danger-icon-btn"
                              @click.stop="handleDeleteProject(item)"
                            >
                              <template #icon><icon-delete /></template>
                            </a-button>
                          </a-tooltip>
                          <a-button
                            v-if="isProjectDisabled(item)"
                            type="text"
                            size="small"
                            disabled
                          >
                            不可进入
                          </a-button>
                          <a-button
                            v-else
                            type="primary"
                            size="small"
                            @click.stop="enterProject(item)"
                          >
                            进入
                          </a-button>
                        </div>
                      </div>
                    </div>
                  </a-grid-item>
                </a-grid>
              </div>

              <!-- 空状态：团队管理员 -->
              <a-empty
                v-if="displayProjects.length === 0 && isTeamAdmin"
                style="margin-top: 120px"
                :description="
                  projectKeyword || projectStatusFilter !== 'all'
                    ? '没有匹配的项目'
                    : '暂无项目，创建一个开始管理吧'
                "
              >
                <a-button
                  v-if="!projectKeyword && projectStatusFilter === 'all'"
                  v-permission="'project:create'"
                  type="primary"
                  @click="openCreateProject"
                >
                  <template #icon><icon-plus /></template>
                  创建第一个项目
                </a-button>
              </a-empty>

              <!-- 空状态：普通成员 -->
              <a-empty
                v-if="displayProjects.length === 0 && !isTeamAdmin"
                style="margin-top: 120px"
                :description="
                  projectKeyword || projectStatusFilter !== 'all'
                    ? '没有匹配的项目'
                    : '暂无项目权限，请联系团队管理员分配项目角色'
                "
              />
            </template>
          </a-tab-pane>

          <a-tab-pane key="members" title="团队成员">
            <UserManager :embedded-in-workspace="true" />
          </a-tab-pane>

          <a-tab-pane v-if="canViewAdminTabs" key="roles" title="角色权限">
            <RoleManager :embedded-in-workspace="true" />
          </a-tab-pane>

          <a-tab-pane v-if="canViewAdminTabs" key="settings" title="团队设置">
            <TeamSetting :embedded-in-workspace="true" />
          </a-tab-pane>
        </a-tabs>
      </a-layout-content>

      <!-- 右侧团队信息 -->
      <a-layout-sider class="overview-sider" :width="288">
        <!-- 团队卡 -->
        <a-card :bordered="false" class="overview-card">
          <div class="team-card-header">
            <div class="team-icon" :style="getTeamIconStyle(teamStore.teamName)">
              {{ (teamStore.teamName || 'T').charAt(0) }}
            </div>
            <div class="team-card-title">
              <div class="overview-team-name">{{ teamStore.teamName || '-' }}</div>
              <a-tag v-if="isSuperAdmin" size="small" color="red">超级管理员</a-tag>
              <a-tag v-else-if="isTeamManager" size="small" color="orange">团队管理员</a-tag>
              <a-tag v-else size="small" color="arcoblue">成员</a-tag>
            </div>
          </div>
          <a-descriptions class="overview-descriptions" :column="1" size="small">
            <a-descriptions-item label="成员数">
              {{ currentTeamInfo?.teamNumber ?? '-' }}
            </a-descriptions-item>
            <a-descriptions-item label="项目数">
              {{ currentTeamProjectCount }}
            </a-descriptions-item>
            <a-descriptions-item label="创建时间">
              {{ formatDate(currentTeamInfo?.createdAt) }}
            </a-descriptions-item>
          </a-descriptions>
        </a-card>

        <!-- 团队成员卡 -->
        <a-card :bordered="false" class="overview-card members-card">
          <div class="members-card-header">
            <span class="members-card-title">团队成员</span>
            <a-link @click="activeTab = 'members'">邀请</a-link>
          </div>
          <a-avatar-group v-if="teamMembers.length > 0" :max-count="8" :size="32">
            <a-avatar
              v-for="m in teamMembers"
              :key="m.userId"
              :style="{ background: getMemberColor(m.nickname || m.username) }"
            >
              {{ (m.nickname || m.username || 'U').charAt(0) }}
            </a-avatar>
          </a-avatar-group>
          <div class="members-count">共 {{ teamMembers.length }} 名成员</div>
        </a-card>

        <!-- 团队设置快捷入口 -->
        <a-button v-if="canViewAdminTabs" long @click="activeTab = 'settings'">
          <template #icon><icon-settings /></template>
          团队设置
        </a-button>
      </a-layout-sider>
    </a-layout>
    <!-- 创建/编辑项目弹窗 -->
    <a-modal
      v-model:visible="projectFormVisible"
      title="新建项目"
      :mask-closable="true"
      :esc-to-close="true"
      :unmount-on-close="true"
      @before-ok="handleProjectSubmit"
    >
      <a-form :model="editingProject" ref="projectFormRef">
        <a-form-item
          label="项目名称"
          field="projectName"
          :rules="[{ required: true, message: '请输入项目名称' }]"
        >
          <a-input
            v-model="editingProject.projectName"
            placeholder="请输入项目名称"
          />
        </a-form-item>
        <a-form-item label="项目描述" field="description">
          <a-textarea
            v-model="editingProject.description"
            placeholder="请输入项目描述"
          />
        </a-form-item>
        <a-form-item
          v-if="isSuperAdmin"
          label="项目管理员"
          field="ownerId"
        >
          <a-select
            v-model="editingProject.ownerId"
            placeholder="从团队成员中选择项目管理员"
          >
            <a-option
              v-for="m in teamMemberOptions"
              :key="m.userId"
              :value="m.userId"
              >{{ m.nickname || m.username }}</a-option
            >
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 创建团队弹窗（仅超管可见入口） -->
    <CreateTeamModal v-model:visible="teamFormVisible" @success="loadTeams" />

    <!-- 项目概览页同款编辑项目弹窗 -->
    <ProjectForm
      v-model:visible="projectEditFormVisible"
      :project="projectEditFormProject"
      :can-manage-owner="isSuperAdmin || isTeamManager"
      @success="handleProjectEditSuccess"
    />
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, ref, watch } from 'vue';
  import { useRouter } from 'vue-router';
  import { Message, Modal } from '@arco-design/web-vue';
  import type { FormInstance } from '@arco-design/web-vue';
  import dayjs from 'dayjs';
  import useTeamStore from '@/store/modules/team';
  import {
    useProjectStore,
    usePermissionStore,
    useWorkspaceStore,
    useUserStore,
  } from '@/store';
  import useDataStore from '@/store/modules/nav';
  import useLoadState from '@/hooks/useLoadState';
  import LoadError from '@/components/load-error/index.vue';
  import { Project } from '@/types/domain/Project';
  import { Team } from '@/types/domain/Team';
  import { UserVO } from '@/types/vo/UserVO';
  import {
    getProjectListByTeamId,
    addProject,
    deleteProject,
    allProject,
  } from '@/api/MyApi/project';
  import { getTeamMembers, getTeamList } from '@/api/MyApi/team';
  import { getUserInfo, getUserList } from '@/api/MyApi/user';
  import UserManager from '../userManager/index.vue';
  import RoleManager from '../roleManager/index.vue';
  import TeamSetting from '../teamSetting/index.vue';
  import ProjectForm from '@/components/project-form/index.vue';
  import CreateTeamModal from '../components/CreateTeamModal.vue';
  import { PROJECT_DELETE } from '@/constants/permissions';
  import {
    IconApps,
    IconCheckCircle,
    IconStop,
    IconUserGroup,
  } from '@arco-design/web-vue/es/icon';

  const router = useRouter();
  const teamStore = useTeamStore();
  const projectStore = useProjectStore();
  const workspaceStore = useWorkspaceStore();
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();
  const dataStore = useDataStore();

  const { loading, loadError, track } = useLoadState();
  const projectList = ref<Project[]>([]);
  const teamList = ref<Team[]>([]);
  const allProjectList = ref<Project[]>([]);
  const activeTab = ref('projects');

  // 是否是团队管理员（有创建项目权限）
  const isTeamAdmin = computed(() =>
    permissionStore.hasPermission('project:create')
  );

  // 项目列表最大高度自适应视口
  const projectListMaxHeight = 'calc(100vh - 430px)';

  // 是否拥有团队管理权限（成员/角色/设置入口）
  const isTeamManager = computed(() =>
    permissionStore.hasPermission('team:member:manage')
  );
  const canViewAdminTabs = computed(() => isTeamManager.value);

  const currentUserRole = ref('');
  const isSuperAdmin = computed(() => currentUserRole.value === 'super_admin');

  const goOverview = () => {
    router.push({ name: 'AdminOverview' });
  };

  const isManagedProject = (project: Project) => {
    if (isSuperAdmin.value) return true;
    return (
      project.createUserId &&
      String(project.createUserId) === String(userStore.id)
    );
  };

  const isProjectDisabled = (project: Project) => {
    return project.status === 'disabled' || project.status === 'SUSPENDED';
  };

  const projectStats = computed(() => {
    const total = projectList.value.length;
    const active = projectList.value.filter(
      (p) => p.status === 'normal' || p.status === 'ACTIVE'
    ).length;
    const suspended = projectList.value.filter(
      (p) => p.status === 'disabled' || p.status === 'SUSPENDED'
    ).length;
    const managed = projectList.value.filter((p) => isManagedProject(p)).length;
    return { total, active, suspended, managed };
  });

  const projectStatItems = computed(() => [
    {
      key: 'total',
      label: '项目总数',
      value: projectStats.value.total,
      color: '#165dff',
      iconBg: '#e8f3ff',
      icon: IconApps,
    },
    {
      key: 'active',
      label: '正常',
      value: projectStats.value.active,
      color: '#00b42a',
      iconBg: '#e8ffea',
      icon: IconCheckCircle,
    },
    {
      key: 'suspended',
      label: '禁用',
      value: projectStats.value.suspended,
      color: '#ff7d00',
      iconBg: '#fff7e8',
      icon: IconStop,
    },
    {
      key: 'managed',
      label: '我管理的',
      value: projectStats.value.managed,
      color: '#722ed1',
      iconBg: '#f5e8ff',
      icon: IconUserGroup,
    },
  ]);

  const projectCountByTeam = computed(() => {
    const map: Record<string, number> = {};
    allProjectList.value.forEach((p) => {
      const tid = String(p.teamId || '');
      if (!tid) return;
      map[tid] = (map[tid] || 0) + 1;
    });
    return map;
  });

  const teamListTitle = computed(() =>
    isSuperAdmin.value ? '所有团队' : '我加入的团队'
  );

  // 项目列表「我的角色」标签颜色
  const myRoleTagColor = (code?: string) => {
    switch (code) {
      case 'project_admin':
        return 'red';
      case 'team_admin':
        return 'purple';
      case 'super_admin':
        return 'red';
      case 'creator':
        return 'orange';
      default:
        return 'arcoblue';
    }
  };

  // 我在各团队的角色标签（超管全局超管；团队管理员 = team.owner_id）
  const getMyRoleTag = (team: Team) => {    if (isSuperAdmin.value) return { text: '超管', color: 'red' };
    if (
      team.manageable ||
      String(team.ownerId) === String(userStore.id)
    ) {
      return { text: '管理员', color: 'orange' };
    }
    return { text: '成员', color: 'arcoblue' };
  };

  // 团队搜索
  const teamKeyword = ref('');
  const filteredTeamList = computed(() => {
    const kw = teamKeyword.value.trim().toLowerCase();
    if (!kw) return teamList.value;
    return teamList.value.filter((t) =>
      (t.teamName || '').toLowerCase().includes(kw)
    );
  });

  // 项目筛选 + 搜索 + 排序
  const projectStatusFilter = ref<'all' | 'normal' | 'disabled'>('all');
  const projectKeyword = ref('');
  const sortBy = ref<'updated' | 'name' | 'cases'>('updated');
  const viewMode = ref<'list' | 'grid'>('list');

  const displayProjects = computed(() => {
    const kw = projectKeyword.value.trim().toLowerCase();
    const list = projectList.value.filter((p) => {
      if (projectStatusFilter.value === 'normal' && isProjectDisabled(p)) return false;
      if (projectStatusFilter.value === 'disabled' && !isProjectDisabled(p)) return false;
      if (kw && !(p.projectName || '').toLowerCase().includes(kw)) return false;
      return true;
    });
    const sorted = [...list];
    if (sortBy.value === 'name') {
      sorted.sort((a, b) => (a.projectName || '').localeCompare(b.projectName || ''));
    } else if (sortBy.value === 'cases') {
      sorted.sort(
        (a, b) =>
          (b.apiTotal || 0) + (b.uiTotal || 0) - ((a.apiTotal || 0) + (a.uiTotal || 0))
      );
    } else {
      sorted.sort(
        (a, b) =>
          (dayjs(b.updatedAt || b.createdAt || 0).valueOf() || 0) -
          (dayjs(a.updatedAt || a.createdAt || 0).valueOf() || 0)
      );
    }
    return sorted;
  });

  // 超管 ID 集合（创建人/成员超管标签）
  const superAdminIds = ref<Set<string>>(new Set());
  const loadSuperAdmins = async () => {
    try {
      const res: any = await getUserList();
      if (res.code === 200 && Array.isArray(res.data)) {
        superAdminIds.value = new Set(
          res.data
            .filter((u: UserVO) => u.role === 'super_admin')
            .map((u: UserVO) => String(u.id))
        );
      }
    } catch (e) {
      console.error(e);
    }
  };

  const currentTeamInfo = computed(
    () =>
      teamList.value.find((t) => String(t.id) === String(teamStore.teamId)) ||
      null
  );

  const currentTeamProjectCount = computed(() => {
    const tid = String(teamStore.teamId || '');
    return tid ? projectCountByTeam.value[tid] || 0 : 0;
  });

  const loadCurrentUserRole = async () => {
    try {
      const res: any = await getUserInfo();
      if (res.code === 200 && res.data) {
        currentUserRole.value = res.data.role || '';
        userStore.setInfo(res.data);
      }
    } catch (e) {
      console.error('加载当前用户信息失败', e);
    }
  };

  // 团队成员（右栏成员卡 + 项目负责人选项）
  const teamMembers = ref<any[]>([]);
  const teamMemberOptions = computed(() =>
    teamMembers.value.filter((m: any) => !m.superAdmin)
  );

  const loadTeamMembers = async () => {
    if (!teamStore.teamId) {
      teamMembers.value = [];
      return;
    }
    try {
      const res: any = await getTeamMembers(teamStore.teamId);
      teamMembers.value = res.code === 200 && Array.isArray(res.data) ? res.data : [];
    } catch (e) {
      console.error(e);
      teamMembers.value = [];
    }
  };

  // 加载团队列表
  const loadTeams = async () => {
    try {
      const res: any = await getTeamList();
      if (res.code === 200 && Array.isArray(res.data)) {
        teamList.value = res.data;
      } else {
        teamList.value = [];
      }
    } catch (e) {
      console.error(e);
      teamList.value = [];
    }
  };

  // 加载全部项目（用于右栏/左侧团队项目数统计）
  const loadAllProjects = async () => {
    try {
      const res: any = await allProject();
      if (res.code === 200 && Array.isArray(res.data)) {
        allProjectList.value = res.data;
      } else {
        allProjectList.value = [];
      }
    } catch (e) {
      console.error(e);
      allProjectList.value = [];
    }
  };

  // 加载项目列表
  const loadProjects = async () => {
    if (!teamStore.teamId) {
      projectList.value = [];
      return;
    }
    const res: any = await track(getProjectListByTeamId(String(teamStore.teamId)));
    if (res?.code === 200 && Array.isArray(res.data)) {
      projectList.value = res.data;
    } else {
      projectList.value = [];
      if (res) loadError.value = true;
    }
  };

  // 切换团队
  const switchTeam = async (team: Team) => {
    if (!team.id || String(team.id) === String(teamStore.teamId)) return;
    await teamStore.setTeam(String(team.id), team.teamName || '');
    await dataStore.fetchData();
    activeTab.value = 'projects';
  };

  // 进入项目：创建/激活工作区标签页，启用左侧菜单
  const enterProject = async (project: Project) => {
    if (!project.id || isProjectDisabled(project)) return;
    projectStore.setProject(project.id, project.projectName);
    const targetRoute = {
      name: 'ProjectInfo',
      query: { projectId: String(project.id) },
    };
    workspaceStore.addTab({
      teamId: Number(teamStore.teamId),
      teamName: teamStore.teamName || '',
      projectId: project.id,
      projectName: project.projectName,
      route: targetRoute,
    });
    router.push(targetRoute);
  };

  // 项目表单（工作台内新建/简单编辑）
  const projectFormVisible = ref(false);
  const projectFormRef = ref<FormInstance>();
  const editingProject = ref<Project>(new Project());

  // 项目概览页同款编辑弹窗
  const projectEditFormVisible = ref(false);
  const projectEditFormProject = ref<Project | null>(null);

  // 创建团队（弹窗逻辑在 CreateTeamModal 组件内）
  const teamFormVisible = ref(false);

  const openCreateTeam = () => {
    teamFormVisible.value = true;
  };

  const openCreateProject = () => {
    editingProject.value = new Project();
    if (isSuperAdmin.value) {
      loadTeamMembers();
    }
    projectFormVisible.value = true;
  };

  const openEditProject = (project: Project) => {
    projectEditFormProject.value = project;
    projectEditFormVisible.value = true;
  };

  const handleProjectEditSuccess = async () => {
    await loadProjects();
    await loadAllProjects();
  };

  const handleDeleteProject = (project: Project) => {
    if (!project.id) {
      Message.warning('项目信息异常，无法删除');
      return;
    }
    Modal.warning({
      title: '确认删除项目',
      content: `删除项目 "${project.projectName}" 后，项目下的需求、用例、BUG、自动化场景等数据将不再可见。是否确认删除？`,
      okText: '确认删除',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res: any = await deleteProject(project.id as number);
          if (res.code === 200) {
            Message.success({ content: '项目已删除', duration: 2000 });
            await loadProjects();
            await loadAllProjects();
            await dataStore.fetchData();
          }
        } catch (e) {
          console.error(e);
        }
      },
    });
  };

  // 新建项目（编辑走 ProjectForm 组件，openEditProject）
  const handleProjectSubmit = async (done: (closed: boolean) => void) => {
    const validateRes = await projectFormRef.value?.validate();
    if (validateRes) {
      done(false);
      return;
    }
    try {
      editingProject.value.teamId = String(teamStore.teamId || '');
      const res: any = await addProject(editingProject.value);
      if (res.code === 200 || res.data) {
        Message.success('创建成功');
        await loadProjects();
        await loadAllProjects();
        done(true);
      } else {
        Message.error(res.msg || '操作失败');
        done(false);
      }
    } catch (e) {
      console.error(e);
      Message.error('操作失败');
      done(false);
    }
  };

  // 状态显示
  const statusColor = (status?: string) => {
    const map: Record<string, string> = {
      normal: 'green',
      ACTIVE: 'green',
      disabled: 'red',
      SUSPENDED: 'orange',
      default: 'default',
    };
    return map[status || 'default'] || 'default';
  };

  const statusText = (status?: string) => {
    const map: Record<string, string> = {
      normal: '正常',
      ACTIVE: '正常',
      disabled: '禁用',
      SUSPENDED: '禁用',
    };
    return map[status || ''] || status || '-';
  };

  const formatDate = (date?: Date | string) => {
    if (!date) return '-';
    return dayjs(date).format('YYYY-MM-DD');
  };

  const iconColors = [
    { bg: '#e8f3ff', color: '#165dff' },
    { bg: '#e8ffea', color: '#00b42a' },
    { bg: '#fff7e8', color: '#ff7d00' },
    { bg: '#ffece8', color: '#f53f3f' },
    { bg: '#f5e8ff', color: '#722ed1' },
  ];

  const getProjectIconStyle = (name?: string) => {
    const style = iconColors[(name || '').length % iconColors.length];
    return { background: style.bg, color: style.color };
  };

  const getTeamIconStyle = (name?: string) => {
    const style = iconColors[((name || '').charCodeAt(0) || 0) % iconColors.length];
    return { background: style.bg, color: style.color };
  };

  const memberColors = ['#165dff', '#00b42a', '#ff7d00', '#722ed1', '#14c9c9', '#f53f3f'];
  const getMemberColor = (name?: string) => {
    if (!name) return memberColors[0];
    return memberColors[name.charCodeAt(0) % memberColors.length];
  };

  watch(
    () => teamStore.teamId,
    () => {
      loadProjects();
      loadTeamMembers();
    }
  );

  const recoverTeamContext = async () => {
    if (teamStore.teamId) return;
    const active = workspaceStore.activeTab;
    if (active?.teamId) {
      await teamStore.setTeam(
        String(active.teamId),
        active.teamName || ''
      );
      await dataStore.fetchData();
      return;
    }
    try {
      const { data } = await getTeamList();
      const first = (data || [])[0];
      if (first?.id) {
        await teamStore.setTeam(String(first.id), first.teamName || '');
        await dataStore.fetchData();
      }
    } catch (e) {
      console.error('恢复团队上下文失败', e);
    }
  };

  onMounted(async () => {
    // recoverTeamContext 若切换了 teamId，watch 会触发 loadProjects/loadTeamMembers；
    // 仅在 teamId 未变化时手动加载，避免双请求
    const hadTeam = !!teamStore.teamId;
    await recoverTeamContext();
    loadCurrentUserRole();
    loadSuperAdmins();
    await loadTeams();
    await loadAllProjects();
    if (hadTeam || !teamStore.teamId) {
      loadProjects();
      loadTeamMembers();
    }
  });
</script>

<style scoped lang="less">
  .team-workspace {
    display: flex;
    flex-direction: column;
    padding: 0 20px 20px;
    background: #f5f6f7;
    height: calc(100vh - 64px - 20px);
    overflow: hidden;
  }

  /* 吸顶半透明面包屑栏 */
  .breadcrumb-bar {
    position: sticky;
    top: 0;
    z-index: 10;
    flex-shrink: 0;
    padding: 12px 4px;
    background: rgba(245, 246, 247, 0.85);
    backdrop-filter: blur(8px);

    .crumb-link {
      color: var(--color-text-2);
      cursor: pointer;

      &:hover {
        color: var(--color-primary-6);
      }
    }
  }

  .workspace-layout {
    flex: 1;
    min-height: 0;
    background: #f5f6f7;
    gap: 12px;

    :deep(.arco-layout) {
      height: 100%;
    }
  }

  .team-sider,
  .workspace-content,
  .overview-sider {
    height: 100%;
    background: #fff;
    border: 1px solid var(--color-border-2);
    border-radius: 10px;
  }

  .team-sider {
    padding: 16px 12px 12px;

    :deep(.arco-layout-sider-children) {
      display: flex;
      flex-direction: column;
      height: 100%;
      overflow: hidden;
    }

    .sider-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 4px 12px;
      flex-shrink: 0;
    }

    .sider-title {
      font-size: 15px;
      font-weight: 600;
      color: var(--color-text-1);
    }

    .team-search {
      margin-bottom: 12px;
      flex-shrink: 0;
    }

    .team-list {
      flex: 1;
      min-height: 0;
      overflow-y: auto;
    }

    .team-item {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 8px;
      border-radius: 8px;
      border: 1px solid transparent;
      cursor: pointer;
      transition: all 0.2s ease;

      &:hover {
        background: var(--color-fill-2);
      }

      &:focus-visible {
        outline: 2px solid var(--color-primary-6);
        outline-offset: -2px;
        background: var(--color-fill-2);
      }

      &.active {
        background: var(--color-primary-light-1);
        border-color: var(--color-primary-6);

        .team-item-name {
          color: var(--color-primary-6);
        }
      }

      &.disabled {
        opacity: 0.6;
      }

      & + .team-item {
        margin-top: 4px;
      }
    }

    .team-role-tag {
      flex-shrink: 0;
    }

    .team-empty {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      min-height: 120px;
    }
  }

  .team-icon {
    width: 40px;
    height: 40px;
    border-radius: 10px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    font-weight: 600;
    flex-shrink: 0;
  }

  .team-item-info {
    flex: 1;
    min-width: 0;
  }

  .team-item-name {
    font-size: 14px;
    font-weight: 500;
    color: var(--color-text-1);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .team-item-meta {
    font-size: 12px;
    color: var(--color-text-3);
    margin-top: 2px;
  }

  .workspace-content {
    padding: 16px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }

  .project-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin: 16px 0 12px;
    flex-wrap: wrap;
    gap: 12px;

    .toolbar-left,
    .toolbar-right {
      display: flex;
      align-items: center;
      gap: 10px;
      flex-wrap: wrap;
    }

    .toolbar-count {
      font-size: 13px;
      color: var(--color-text-3);

      b {
        color: var(--color-primary-6);
        font-size: 15px;
      }
    }
  }

  .overview-sider {
    padding: 16px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }

  .overview-card {
    border-radius: 10px;
    border: 1px solid var(--color-border-2);

    :deep(.arco-card-body) {
      padding: 16px;
    }
  }

  .team-card-header {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }

  .team-card-title {
    min-width: 0;
  }

  .overview-team-name {
    font-size: 16px;
    font-weight: 600;
    color: var(--color-text-1);
    margin-bottom: 4px;
    word-break: break-all;
  }

  .overview-descriptions {
    :deep(.arco-descriptions-table) {
      width: 100%;
    }

    :deep(.arco-descriptions-item-label-block) {
      text-align: left;
      width: 1px;
      white-space: nowrap;
      color: var(--color-text-2);
    }

    :deep(.arco-descriptions-item-value-block) {
      text-align: right;
      width: 100%;
      color: var(--color-text-1);
    }
  }

  .members-card {
    .members-card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
    }

    .members-card-title {
      font-size: 14px;
      font-weight: 600;
      color: var(--color-text-1);
    }

    .members-count {
      font-size: 12px;
      color: var(--color-text-3);
      margin-top: 10px;
    }
  }

  .workspace-tabs {
    height: 100%;

    :deep(.arco-tabs) {
      height: 100%;
      display: flex;
      flex-direction: column;
    }

    :deep(.arco-tabs-content) {
      flex: 1;
      min-height: 0;
      padding-top: 4px;
    }

    :deep(.arco-tabs-content-list),
    :deep(.arco-tabs-content-item) {
      height: 100%;
    }

    :deep(.arco-tab-pane) {
      height: 100%;
    }
  }

  .team-sider,
  .workspace-content,
  .overview-sider {
    &::-webkit-scrollbar {
      width: 4px;
      height: 4px;
    }

    &::-webkit-scrollbar-thumb {
      background: var(--color-fill-3);
      border-radius: 2px;
    }

    &::-webkit-scrollbar-track {
      background: transparent;
    }
  }

  /* 项目指标条：与总览页一致的轻量卡 */
  .stats-grid {
    width: 100%;

    .stat-card {
      display: flex;
      align-items: center;
      gap: 14px;
      padding: 14px 18px;
      border-radius: 10px;
      background: var(--color-bg-2);
      border: 1px solid var(--color-border-2);
      transition: box-shadow 0.2s ease;

      &:hover {
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      }
    }

    .stat-card-icon {
      width: 40px;
      height: 40px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 20px;
      flex-shrink: 0;
    }

    .stat-card-info {
      min-width: 0;
    }

    .stat-card-num {
      font-size: 22px;
      font-weight: 700;
      line-height: 1.2;
      color: var(--color-text-1);
    }

    .stat-card-label {
      font-size: 12px;
      color: var(--color-text-3);
      margin-top: 2px;
    }
  }

  .project-list {
    border-radius: 10px;
    overflow: hidden;

    :deep(.arco-list-item) {
      padding: 0;
      transition: background 0.2s ease;

      &:hover {
        background: var(--color-fill-1);
      }

      &.disabled {
        opacity: 0.6;
        cursor: not-allowed;

        &:hover {
          background: transparent;
        }
      }
    }
  }

  /* 横向行布局：图标 | 主信息(自适应) | 操作 */
  .project-item-body {
    display: flex;
    align-items: center;
    gap: 12px;
    width: 100%;
    padding: 12px;
  }

  .project-icon {
    width: 40px;
    height: 40px;
    border-radius: 8px;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 16px;
    font-weight: 600;
    flex-shrink: 0;
    align-self: flex-start;
  }

  .project-main {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    gap: 6px;
  }

  .project-title-row {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    flex-wrap: wrap;
  }

  .project-name-text {
    font-size: 14px;
    font-weight: 600;
    color: var(--color-text-1);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 260px;
  }

  .status-pill {
    height: 20px;
    padding: 0 8px;
    border-radius: 10px;
    border: none;
    font-size: 12px;
    line-height: 20px;
    flex-shrink: 0;

    &.status-green {
      background: rgba(0, 180, 42, 0.12);
      color: #00b42a;
    }
    &.status-red {
      background: rgba(245, 63, 63, 0.12);
      color: #f53f3f;
    }
    &.status-orange {
      background: rgba(255, 125, 0, 0.12);
      color: #ff7d00;
    }
    &.status-default {
      background: var(--color-fill-2);
      color: var(--color-text-2);
    }
  }

  .creator-line {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--color-text-3);
    flex-shrink: 0;
  }

  .creator-icon {
    font-size: 12px;
  }

  .project-desc-row {
    font-size: 12px;
    color: var(--color-text-2);
    line-height: 1.4;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: 100%;
  }

  .project-stats-row {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  /* 指标 chip：有数值高亮，为 0 置灰 */
  .metric-chip {
    display: inline-flex;
    align-items: center;
    font-size: 12px;
    padding: 1px 8px;
    border-radius: 10px;
    background: var(--color-fill-2);
    color: var(--color-text-3);

    &.active {
      background: var(--color-primary-light-1);
      color: var(--color-primary-6);
      font-weight: 600;
    }
  }

  .project-action {
    flex-shrink: 0;
    align-self: center;
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 4px;
  }

  .danger-icon-btn:hover {
    color: #f53f3f;
    background: #ffece8;
  }

  /* 网格视图 */
  .project-grid-wrap {
    max-height: v-bind(projectListMaxHeight);
    overflow-y: auto;
    padding-right: 4px;
  }

  .project-grid-card {
    border: 1px solid var(--color-border-2);
    border-radius: 10px;
    padding: 16px;
    background: var(--color-bg-2);
    display: flex;
    flex-direction: column;
    gap: 10px;
    transition: box-shadow 0.2s ease, transform 0.2s ease;

    &:hover {
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
      transform: translateY(-2px);
    }

    &.disabled {
      opacity: 0.6;
    }
  }

  .grid-card-header {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .grid-card-title {
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
  }

  .grid-card-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    padding-top: 10px;
    border-top: 1px solid var(--color-border-2);
  }

  /* 响应式：xl 以下隐藏右栏，lg 以下隐藏左栏 */
  @media (max-width: 1199px) {
    .overview-sider {
      display: none;
    }
  }

  @media (max-width: 991px) {
    .team-sider {
      display: none;
    }
  }

  @media (max-width: 768px) {
    .project-item-body {
      flex-wrap: wrap;
      gap: 10px 12px;
    }

    .project-action {
      width: 100%;
      align-self: flex-start;
      justify-content: flex-end;
    }

    .project-name-text {
      max-width: 100%;
    }
  }
</style>
