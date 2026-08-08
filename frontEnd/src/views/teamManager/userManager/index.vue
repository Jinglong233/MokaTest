<template>
  <div class="user-manager" :class="{ embedded: embeddedInWorkspace }">
    <!-- 顶部标题 -->
    <div class="page-header">
      <a-space v-if="!embeddedInWorkspace">
        <span class="page-title">{{ viewMode === 'all' ? '全部用户' : '团队用户管理' }}</span>
      </a-space>
      <a-space v-else></a-space>
      <a-button
        v-permission="'team:member:manage'"
        v-if="viewMode === 'team' && !isPersonalTeam"
        type="primary"
        shape="round"
        @click="openInviteModal"
      >
        <template #icon><icon-plus /></template>
        邀请成员
      </a-button>
    </div>

    <!-- 个人团队提示 -->
    <a-alert
      v-if="viewMode === 'team' && isPersonalTeam"
      type="warning"
      :show-icon="true"
      style="margin-bottom: 16px"
    >
      当前为个人团队（个人工作空间），不支持邀请其他成员。如需多人协作，请创建正式团队。
    </a-alert>

    <!-- 视图由角色决定：超管=全部用户(平台级)，团队管理员=团队成员；无需手动切换 -->

    <!-- 团队成员视图 -->
    <div v-if="viewMode === 'team'">
      <!-- 搜索栏 -->
      <a-card class="search-card" :bordered="false">
        <a-form :model="queryForm" layout="inline">
          <a-form-item field="keyword" label="关键词">
            <a-input
              v-model="queryForm.keyword"
              placeholder="用户名/昵称"
              allow-clear
              @press-enter="handleSearch"
            />
          </a-form-item>
          <a-form-item field="roleId" label="角色">
            <a-select
              v-model="queryForm.roleId"
              placeholder="全部角色"
              allow-clear
              style="width: 160px"
              @change="handleSearch"
            >
              <a-option
                v-for="role in roleList"
                :key="role.id"
                :value="role.id"
                >{{ role.name }}</a-option
              >
            </a-select>
          </a-form-item>
          <a-form-item field="status" label="状态">
            <a-select
              v-model="queryForm.status"
              placeholder="全部状态"
              allow-clear
              style="width: 140px"
              @change="handleSearch"
            >
              <a-option :value="1">正常</a-option>
              <a-option :value="0">禁用</a-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" @click="handleSearch">
              <template #icon><icon-search /></template>
              搜索
            </a-button>
            <a-button style="margin-left: 8px" @click="handleReset"
              >重置</a-button
            >
          </a-form-item>
        </a-form>
      </a-card>

      <!-- 成员表格 -->
      <a-card class="table-card" :bordered="false">
        <LoadError v-if="loadError" @retry="loadData" />
        <a-table
          v-else
          :columns="columns"
          :data="filteredList"
          :loading="loading"
          :pagination="pagination"
          @page-change="handlePageChange"
        >
          <template #role="{ record }">
            <a-space>
              <a-tag :color="record.roleCode === 'admin' || record.roleCode === 'team_admin' ? 'red' : 'blue'">
                {{ record.roleName || '-' }}
              </a-tag>
              <a-tag v-if="record.superAdmin" size="small" color="red"
                >超管</a-tag
              >
            </a-space>
          </template>
          <template #status="{ record }">
            <a-tooltip
              v-if="isSuperAdminMember(record)"
              content="超级管理员不可被禁用"
            >
              <a-switch :model-value="record.status === 1" :disabled="true" />
            </a-tooltip>
            <a-tooltip
              v-else-if="isSelf(record.userId)"
              content="不能操作自己的状态"
            >
              <a-switch :model-value="record.status === 1" :disabled="true" />
            </a-tooltip>
            <a-tooltip
              v-else-if="record.teamOwner"
              content="团队管理员不可被禁用，请先在团队设置中变更团队管理员"
            >
              <a-switch :model-value="record.status === 1" :disabled="true" />
            </a-tooltip>
            <a-switch
              v-else
              :model-value="record.status === 1"
              :disabled="!isAdmin"
              @change="(val) => handleStatusChange(record, val as boolean)"
            />
          </template>
          <template #joinTime="{ record }">
            {{ formatDate(record.joinTime) }}
          </template>
          <template #optional="{ record }">
            <a-space>
              <a-button type="text" size="small" @click="viewUserInfo(record)">
                <template #icon><icon-eye /></template>
                查看
              </a-button>
              <template v-if="canManageMember">
                <a-tooltip
                  v-if="isSuperAdminMember(record)"
                  content="超级管理员角色不可修改"
                >
                  <a-button type="text" size="small" :disabled="true">
                    <template #icon><icon-user /></template>
                    分配角色
                  </a-button>
                </a-tooltip>
                <a-tooltip
                  v-else-if="record.teamOwner"
                  content="团队管理员的角色不可修改（其管理员身份来自团队设置）"
                >
                  <a-button type="text" size="small" :disabled="true">
                    <template #icon><icon-user /></template>
                    分配角色
                  </a-button>
                </a-tooltip>
                <a-button
                  v-else
                  v-permission="'team:role:manage'"
                  v-if="!isSelf(record.userId)"
                  type="text"
                  size="small"
                  @click="openRoleModal(record)"
                >
                  <template #icon><icon-user /></template>
                  分配角色
                </a-button>
                <a-tooltip
                  v-if="isSuperAdminMember(record)"
                  content="超级管理员不可被移除"
                >
                  <a-button
                    type="text"
                    status="danger"
                    size="small"
                    :disabled="true"
                  >
                    <template #icon><icon-delete /></template>
                    移除
                  </a-button>
                </a-tooltip>
                <a-tooltip
                  v-else-if="record.teamOwner"
                  content="团队管理员不可被移除，请先在团队设置中变更团队管理员"
                >
                  <a-button
                    type="text"
                    status="danger"
                    size="small"
                    :disabled="true"
                  >
                    <template #icon><icon-delete /></template>
                    移除
                  </a-button>
                </a-tooltip>
                <a-tooltip
                  v-else-if="isSelf(record.userId)"
                  content="不能移除自己"
                >
                  <a-button
                    type="text"
                    status="danger"
                    size="small"
                    :disabled="true"
                  >
                    <template #icon><icon-delete /></template>
                    移除
                  </a-button>
                </a-tooltip>
                <a-popconfirm
                  v-else
                  v-permission="'team:member:manage'"
                  :content="`确定将成员「${record.nickname || record.username}」从团队移除吗？其在团队内各项目的角色将一并移除。`"
                  type="warning"
                  @ok="handleRemove(record)"
                >
                  <a-button type="text" status="danger" size="small">
                    <template #icon><icon-delete /></template>
                    移除
                  </a-button>
                </a-popconfirm>
              </template>
            </a-space>
          </template>
        </a-table>
      </a-card>
    </div>

    <!-- 全部用户视图（仅超管可见） -->
    <div v-if="viewMode === 'all' && isSuperAdmin">
      <a-card class="table-card" :bordered="false">
        <template #title>
          <a-button type="primary" @click="openCreateUserModal">
            <template #icon><icon-plus /></template>
            新建用户
          </a-button>
        </template>
        <LoadError v-if="allUserLoadError" @retry="loadAllUsers" />
        <a-table
          v-if="!allUserLoadError"
          :columns="allUserColumns"
          :data="allUserList"
          :loading="allUserLoading"
          :pagination="allUserPagination"
          @page-change="handleAllUserPageChange"
        >
          <template #role="{ record }">
            <a-space wrap>
              <a-tag v-if="record.role === 'super_admin'" color="red"
                >超级管理员</a-tag
              >
              <a-button
                v-if="record.teams && record.teams.length > 0"
                type="text"
                size="mini"
                @click="openTeamRoleDrawer(record)"
              >
                {{ record.teams.length }} 个团队
              </a-button>
              <span
                v-else-if="record.role !== 'super_admin'"
                style="color: var(--color-text-3)"
                >-</span
              >
            </a-space>
          </template>
          <template #status="{ record }">
            <a-tag
              v-if="record.status === '1' || record.status === 1"
              color="green"
              >正常</a-tag
            >
            <a-tag v-else color="red">禁用</a-tag>
          </template>
          <template #optional="{ record }">
            <a-space>
              <a-tooltip
                v-if="record.role === 'super_admin'"
                content="超级管理员密码不可被重置"
              >
                <a-button type="text" size="small" :disabled="true">
                  <template #icon><icon-lock /></template>
                  重置密码
                </a-button>
              </a-tooltip>
              <a-button
                v-else
                type="text"
                size="small"
                @click="openResetPwdModal(record)"
              >
                <template #icon><icon-lock /></template>
                重置密码
              </a-button>
              <a-tooltip
                v-if="record.role === 'super_admin'"
                content="超级管理员不可被禁用"
              >
                <a-button type="text" size="small" :disabled="true">
                  <template #icon><icon-stop /></template>
                  禁用
                </a-button>
              </a-tooltip>
              <a-tooltip
                v-else-if="String(record.id) === String(loginUserId)"
                content="不能禁用/启用自己"
              >
                <a-button type="text" size="small" :disabled="true">
                  <template #icon><icon-stop /></template>
                  {{ record.status === 1 || record.status === '1' ? '禁用' : '启用' }}
                </a-button>
              </a-tooltip>
              <a-popconfirm
                v-else
                :content="
                  record.status === 1 || record.status === '1'
                    ? '禁用后该用户将无法登录，现有会话立即失效，确定禁用吗？'
                    : '确定启用该用户吗？'
                "
                :type="record.status === 1 || record.status === '1' ? 'warning' : 'info'"
                @ok="handleToggleUserStatus(record)"
              >
                <a-button
                  type="text"
                  size="small"
                  :status="record.status === 1 || record.status === '1' ? 'danger' : 'success'"
                >
                  <template #icon><icon-stop /></template>
                  {{ record.status === 1 || record.status === '1' ? '禁用' : '启用' }}
                </a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </a-table>
      </a-card>
    </div>

    <!-- 用户团队角色抽屉（全部用户视图） -->
    <a-drawer
      :visible="teamRoleDrawerVisible"
      :width="480"
      :footer="false"
      @cancel="closeTeamRoleDrawer"
    >
      <template #title>
        <span v-if="selectedUserForTeams">
          {{
            selectedUserForTeams.nickname || selectedUserForTeams.username
          }}
          的团队角色
        </span>
      </template>
      <a-descriptions
        v-if="selectedUserForTeams"
        :column="1"
        style="margin-bottom: 16px"
      >
        <a-descriptions-item label="用户名">{{
          selectedUserForTeams.username
        }}</a-descriptions-item>
        <a-descriptions-item label="昵称">{{
          selectedUserForTeams.nickname || '-'
        }}</a-descriptions-item>
        <a-descriptions-item label="全局角色">
          <a-tag v-if="selectedUserForTeams.role === 'super_admin'" color="red"
            >超级管理员</a-tag
          >
          <a-tag v-else color="blue">普通用户</a-tag>
        </a-descriptions-item>
      </a-descriptions>
      <a-table
        v-if="selectedUserForTeams"
        :columns="teamRoleColumns"
        :data="selectedUserForTeams.teams || []"
        :pagination="false"
        size="small"
      >
        <template #roleName="{ record }">
          <a-tag :color="getRoleTagColor(record.roleCode)">
            {{ record.roleName || record.roleCode || '-' }}
          </a-tag>
        </template>
        <template #status="{ record }">
          <a-tag v-if="record.status === 1" color="green">正常</a-tag>
          <a-tag v-else color="red">禁用</a-tag>
        </template>
      </a-table>
    </a-drawer>

    <!-- 查看详情弹窗 -->
    <a-modal
      :visible="currentShowUser != null"
      :footer="false"
      @cancel="currentShowUser = null"
    >
      <template #title>用户详情</template>
      <a-descriptions
        v-if="currentShowUser"
        style="margin-top: 20px"
        :column="1"
      >
        <a-descriptions-item label="用户名">{{
          currentShowUser.username
        }}</a-descriptions-item>
        <a-descriptions-item label="昵称">{{
          currentShowUser.nickname
        }}</a-descriptions-item>
        <a-descriptions-item label="角色">
          <a-tag :color="currentShowUser.roleCode === 'admin' ? 'red' : 'blue'">
            {{ currentShowUser.roleName || '-' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag v-if="currentShowUser.status === 1" color="green">正常</a-tag>
          <a-tag v-else color="red">禁用</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="手机">{{
          currentShowUser.phone
        }}</a-descriptions-item>
        <a-descriptions-item label="邮箱">{{
          currentShowUser.email
        }}</a-descriptions-item>
        <a-descriptions-item label="加入时间">{{
          formatDate(currentShowUser.joinTime)
        }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>

    <!-- 分配角色弹窗 -->
    <a-modal
      v-model:visible="roleModalVisible"
      title="分配角色"
      @before-ok="handleRoleSubmit"
    >
      <a-form :model="roleForm">
        <a-form-item label="用户名">
          <span>{{ roleForm.username }}</span>
        </a-form-item>
        <a-form-item
          label="角色"
          field="roleId"
          :rules="[{ required: true, message: '请选择角色' }]"
        >
          <a-select
            v-model="roleForm.roleId"
            placeholder="请选择角色"
            style="width: 100%"
          >
            <a-option
              v-for="role in roleList"
              :key="role.id"
              :value="role.id"
              >{{ role.name }}</a-option
            >
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 邀请成员弹窗 -->
    <a-modal
      v-model:visible="inviteModalVisible"
      title="邀请成员"
      :footer="false"
      :width="700"
    >
      <p style="color: #86909c; margin-bottom: 16px"
        >选择用户加入当前团队（作为团队成员），可同时指定加入的项目和项目角色</p
      >
      <a-form-item label="选择用户" required>
        <a-select
          v-model="selectedUserIds"
          multiple
          allow-search
          allow-clear
          :loading="userLoading"
          :max-tag-count="5"
          placeholder="请选择要邀请的用户（可多选）"
        >
          <a-option
            v-for="u in userOptions"
            :key="u.id"
            :value="String(u.id)"
            :label="`${u.nickname || u.username}（${u.username}）`"
          />
          <template #header>
            <div style="padding: 6px 12px">
              <a-checkbox
                :model-value="isAllUsersSelected"
                :indeterminate="isUserSelectionPartial"
                @change="toggleSelectAllUsers"
                >全选（{{ selectedUserIds.length }}/{{ userOptions.length }}）</a-checkbox
              >
            </div>
          </template>
        </a-select>
      </a-form-item>
      <a-divider style="margin: 16px 0" />
      <a-form :model="inviteForm" layout="vertical">
        <a-form-item>
          <a-checkbox v-model="inviteForm.assignProjectRole">同时加入项目并分配项目角色（可选）</a-checkbox>
        </a-form-item>
        <template v-if="inviteForm.assignProjectRole">
          <a-form-item label="选择项目" field="projectId" :rules="[{ required: true, message: '请选择项目' }]">
            <a-select
              v-model="inviteForm.projectId"
              placeholder="请选择项目（仅当前团队）"
              @change="(v) => onInviteProjectChange(v as number)"
            >
              <a-option
                v-for="project in projectList"
                :key="project.id"
                :value="project.id"
                >{{ project.projectName }}</a-option
              >
            </a-select>
          </a-form-item>
          <a-form-item label="选择角色" field="projectRoleId">
            <a-select
              v-model="inviteForm.projectRoleId"
              placeholder="不选默认为「项目成员」"
              :disabled="!inviteForm.projectId"
              allow-clear
            >
              <a-option
                :value="PROJECT_ADMIN_OPTION_VALUE"
                :disabled="selectedUserIds.length > 1"
              >
                <span style="color: #f53f3f">项目管理员</span>
                <span style="color: #86909c; font-size: 12px">（将替换现任项目管理员，多选时不可选）</span>
              </a-option>
              <a-option
                v-for="role in projectRoleList"
                :key="role.id"
                :value="role.id"
                >{{ role.name }}</a-option
              >
            </a-select>
          </a-form-item>
        </template>
      </a-form>
      <div style="margin-top: 16px; text-align: right">
        <a-button @click="inviteModalVisible = false">取消</a-button>
        <a-button
          type="primary"
          style="margin-left: 8px"
          :loading="inviteLoading"
          @click="handleInvite"
        >
          确认邀请
        </a-button>
      </div>
    </a-modal>

    <!-- 新建用户弹窗（仅超管） -->
    <a-modal
      v-model:visible="createUserModalVisible"
      title="新建用户"
      :width="520"
      :footer="false"
      @cancel="createUserResult = null"
    >
      <!-- 表单模式 -->
      <template v-if="!createUserResult">
        <a-form :model="createUserForm" layout="vertical">
          <a-form-item
            field="username"
            label="用户名"
            :rules="[
              { required: true, message: '请输入用户名' },
              { minLength: 5, message: '用户名至少5个字符' },
              { maxLength: 20, message: '用户名最多20个字符' },
              {
                match: /^[a-zA-Z0-9_]+$/,
                message: '用户名只能包含字母、数字和下划线',
              },
            ]"
          >
            <a-input
              v-model="createUserForm.username"
              placeholder="5-20位，字母、数字、下划线"
            />
          </a-form-item>
          <a-form-item field="nickname" label="昵称">
            <a-input v-model="createUserForm.nickname" placeholder="选填" />
          </a-form-item>
          <a-form-item field="email" label="邮箱">
            <a-input v-model="createUserForm.email" placeholder="选填" />
          </a-form-item>
          <a-form-item field="phone" label="手机号">
            <a-input v-model="createUserForm.phone" placeholder="选填" />
          </a-form-item>
        </a-form>
        <div style="text-align: right; margin-top: 16px">
          <a-button @click="createUserModalVisible = false">取消</a-button>
          <a-button
            type="primary"
            style="margin-left: 8px"
            :loading="createUserLoading"
            @click="handleCreateUser"
          >
            确认创建
          </a-button>
        </div>
      </template>
      <!-- 成功结果 -->
      <template v-else>
        <a-result status="success">
          <template #subtitle>
            <p>用户名：<strong>{{ createUserResult.username }}</strong></p>
            <p style="margin-top: 8px">
              初始密码：<a-typography-text copyable code>{{
                createUserResult.password
              }}</a-typography-text>
            </p>
            <p style="margin-top: 8px; color: var(--color-text-3); font-size: 12px">
              请提醒用户首次登录后修改密码
            </p>
          </template>
        </a-result>
        <div style="text-align: right; margin-top: 16px">
          <a-button type="primary" @click="createUserModalVisible = false"
            >关闭</a-button
          >
        </div>
      </template>
    </a-modal>

    <!-- 重置密码弹窗（仅超管） -->
    <a-modal
      v-model:visible="resetPwdModalVisible"
      title="重置密码"
      :width="440"
      :footer="false"
      @cancel="resetPwdResult = null"
    >
      <!-- 确认模式 -->
      <template v-if="!resetPwdResult">
        <p>
          确认重置用户 <strong>{{ resetPwdTarget?.username || '-' }}</strong
          > 的密码？
        </p>
        <p style="color: var(--color-text-3); margin-top: 8px">
          系统将生成新的随机密码，重置后原密码将失效
        </p>
        <div style="text-align: right; margin-top: 16px">
          <a-button @click="resetPwdModalVisible = false">取消</a-button>
          <a-button
            type="primary"
            status="warning"
            style="margin-left: 8px"
            :loading="resetPwdLoading"
            @click="handleResetPwd"
          >
            确认重置
          </a-button>
        </div>
      </template>
      <!-- 成功结果 -->
      <template v-else>
        <a-result status="success">
          <template #subtitle>
            <p>用户名：<strong>{{ resetPwdResult.username }}</strong></p>
            <p style="margin-top: 8px">
              新密码：<a-typography-text copyable code>{{
                resetPwdResult.password
              }}</a-typography-text>
            </p>
            <p style="margin-top: 8px; color: var(--color-text-3); font-size: 12px">
              请提醒用户使用新密码登录
            </p>
          </template>
        </a-result>
        <div style="text-align: right; margin-top: 16px">
          <a-button type="primary" @click="resetPwdModalVisible = false"
            >关闭</a-button
          >
        </div>
      </template>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
  import { computed, onMounted, reactive, ref, watch } from 'vue';
  import { Message, Modal } from '@arco-design/web-vue';
  import dayjs from 'dayjs';
  import useTeamStore from '@/store/modules/team';
  import { useUserStore } from '@/store';
  import { usePermissionStore } from '@/store/modules/permission';
  import useLoadState from '@/hooks/useLoadState';
  import LoadError from '@/components/load-error/index.vue';
  import { TeamMemberVO } from '@/types/vo/TeamMemberVO';
  import { UserVO } from '@/types/vo/UserVO';
  import { UserTeamVO } from '@/types/vo/UserTeamVO';
  import { Role } from '@/types/domain/Role';
  import { Team } from '@/types/domain/Team';
  import {
    getTeamMembers,
    updateTeamMemberRole,
    removeTeamMember,
    updateTeamMemberStatus,
    addTeamMember,
    getTeamList,
  } from '@/api/MyApi/team';
  import { getRoleList } from '@/api/MyApi/rbac';
  import { getProjectListByTeamId, getProjectRoleOptions } from '@/api/MyApi/project';
  import { Project } from '@/types/domain/Project';
  import {
    getInviteUserList,
    getUserList,
    getUserInfo,
    adminCreateUser,
    adminResetPwd,
    updateUserStatus,
  } from '@/api/MyApi/user';

  const teamStore = useTeamStore();
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  const props = defineProps<{ embeddedInWorkspace?: boolean }>();

  // 当前团队下是否拥有成员管理能力（团队管理员/超管），用于控制管理类按钮与角色列表加载
  const canManageMember = computed(() =>
    permissionStore.hasPermission('team:member:manage')
  );

  // 团队选择器
  const teamOptions = ref<Team[]>([]);
  const currentTeamId = ref<number | undefined>(teamStore.teamId || undefined);

  // 当前团队是否为个人团队（个人工作空间，不支持邀请成员）
  const isPersonalTeam = computed(() => {
    const tid = teamStore.teamId;
    const t = teamOptions.value.find((it) => String(it.id) === String(tid));
    return t?.isPersonal === 1;
  });

  const loadTeams = async () => {
    try {
      const res: any = await getTeamList();
      if (res.code === 200 && res.data) {
        teamOptions.value = res.data;
        // 如果当前没有选中团队，且有团队数据，默认选中第一个
        if (!currentTeamId.value && teamOptions.value.length > 0) {
          currentTeamId.value = teamOptions.value[0].id;
          teamStore.setTeam(
            String(currentTeamId.value),
            teamOptions.value[0].teamName || ''
          );
        }
      }
    } catch (e) {
      console.error(e);
    }
  };

  const loadCurrentUserRole = async () => {
    try {
      const res: any = await getUserInfo();
      if (res.code === 200 && res.data) {
        currentUserRole.value = res.data.role || '';
        // 同步更新 store，方便其他组件使用
        userStore.setInfo(res.data);
      }
    } catch (e) {
      console.error('加载当前用户信息失败', e);
    }
  };

  const handleTeamChange = (teamId: number) => {
    const team = teamOptions.value.find((t) => t.id === teamId);
    if (team) {
      teamStore.setTeam(String(teamId), team.teamName || '');
    }
  };

  // 当前登录用户ID
  const loginUserId = computed(() => Number(userStore.id));

  // 判断是否为当前用户自己
  const isSelf = (userId?: number) => {
    return userId != null && userId === loginUserId.value;
  };

  // 判断目标成员是否为超级管理员（超管不可被移除、禁用、修改角色）
  const isSuperAdminMember = (record?: TeamMemberVO) => {
    return record?.superAdmin === true;
  };

  // 当前登录用户是否为超级管理员
  const currentUserRole = ref<string>('');
  const isSuperAdmin = computed(() => currentUserRole.value === 'super_admin');

  // 视图模式：team-团队成员，all-全部用户
  // 嵌入工作台时保持「团队成员」视图；独立页面时由角色决定默认视图
  const viewMode = ref<'team' | 'all'>(
    props.embeddedInWorkspace ? 'team' : (userStore.role === 'super_admin' ? 'all' : 'team')
  );

  // 角色列表
  const roleList = ref<Role[]>([]);

  // 判断当前用户是否为管理员
  const isAdmin = computed(() => {
    const currentUser = memberList.value.find(
      (m) => m.userId === loginUserId.value
    );
    return currentUser?.roleCode === 'admin' || currentUser?.roleCode === 'team_admin';
  });

  // ========== 搜索表单 ==========
  const queryForm = reactive({
    keyword: '',
    roleId: undefined as number | undefined,
    status: undefined as number | undefined,
  });

  const handleSearch = () => {
    pagination.current = 1;
  };

  const handleReset = () => {
    queryForm.keyword = '';
    queryForm.roleId = undefined;
    queryForm.status = undefined;
    handleSearch();
  };

  // ========== 表格数据 ==========
  const { loading, loadError, track } = useLoadState();
  const memberList = ref<TeamMemberVO[]>([]);
  const filteredList = computed(() => {
    let list = memberList.value;
    if (queryForm.keyword) {
      const key = queryForm.keyword.toLowerCase();
      list = list.filter(
        (m) =>
          m.username?.toLowerCase().includes(key) ||
          m.nickname?.toLowerCase().includes(key)
      );
    }
    if (queryForm.roleId !== undefined) {
      list = list.filter((m) => m.roleId === queryForm.roleId);
    }
    if (queryForm.status !== undefined) {
      list = list.filter((m) => m.status === queryForm.status);
    }
    return list;
  });

  const pagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: true,
    showJumper: true,
  });

  const handlePageChange = (page: number) => {
    pagination.current = page;
  };

  // ========== 全部用户（仅超管） ==========
  const {
    loading: allUserLoading,
    loadError: allUserLoadError,
    track: trackAllUsers,
  } = useLoadState();
  const allUserList = ref<UserVO[]>([]);
  const allUserPagination = reactive({
    current: 1,
    pageSize: 10,
    total: 0,
    showTotal: true,
    showJumper: true,
  });

  const allUserColumns = [
    { title: '用户名', dataIndex: 'username' },
    { title: '昵称', dataIndex: 'nickname' },
    { title: '邮箱', dataIndex: 'email' },
    { title: '手机', dataIndex: 'phone' },
    { title: '角色', slotName: 'role' },
    { title: '状态', slotName: 'status' },
    { title: '操作', slotName: 'optional', width: 140 },
  ];

  // 用户团队角色抽屉
  const teamRoleDrawerVisible = ref(false);
  const selectedUserForTeams = ref<UserVO | null>(null);

  const openTeamRoleDrawer = (record: UserVO) => {
    selectedUserForTeams.value = record;
    teamRoleDrawerVisible.value = true;
  };

  const closeTeamRoleDrawer = () => {
    teamRoleDrawerVisible.value = false;
    selectedUserForTeams.value = null;
  };

  const teamRoleColumns = [
    { title: '团队名称', dataIndex: 'teamName' },
    { title: '角色', slotName: 'roleName' },
    { title: '状态', slotName: 'status', width: 80 },
  ];

  const getRoleTagColor = (roleCode?: string) => {
    switch (roleCode) {
      case 'admin':
      case 'team_admin':
        return 'red';
      case 'test_leader':
        return 'orange';
      case 'tester':
        return 'blue';
      case 'developer':
        return 'cyan';
      case 'readonly':
        return 'gray';
      default:
        return 'arcoblue';
    }
  };

  const handleAllUserPageChange = (page: number) => {
    allUserPagination.current = page;
  };

  const loadAllUsers = async () => {
    if (!isSuperAdmin.value) return;
    const res: any = await trackAllUsers(getUserList());
    if (res?.code === 200 && res.data) {
      allUserList.value = res.data;
      allUserPagination.total = res.data.length;
    } else {
      allUserList.value = [];
      allUserPagination.total = 0;
      if (res) allUserLoadError.value = true;
    }
  };

  // 切换视图模式时加载对应数据
  watch(
    () => viewMode.value,
    (mode) => {
      if (mode === 'all') {
        loadAllUsers();
      }
    }
  );

  const columns = [
    { title: '用户名', dataIndex: 'username' },
    { title: '昵称', dataIndex: 'nickname' },
    { title: '角色', slotName: 'role' },
    { title: '状态', slotName: 'status' },
    { title: '加入时间', slotName: 'joinTime' },
    { title: '操作', slotName: 'optional', width: 240 },
  ];

  // ========== 加载数据 ==========
  const loadRoles = async () => {
    // 普通成员无成员管理权限，跳过角色列表加载，避免触发 403 提示
    if (!canManageMember.value) {
      roleList.value = [];
      return;
    }
    try {
      const res: any = await getRoleList(teamStore.teamId || undefined);
      if (res.code === 200 && res.data) {
        // 用户管理只分配「团队级」身份：仅 普通成员。
        // 团队管理员唯一来源是 team.owner_id（团队设置中变更）；
        // 项目管理员、各类自定义模板都是项目级，需在「项目成员」入口按项目分配。
        const teamLevelCodes = ['team_member', 'member'];
        roleList.value = (res.data as Role[]).filter(
          (r) => r.code != null && teamLevelCodes.includes(r.code)
        );
      }
    } catch (e) {
      console.error(e);
    }
  };

  const loadData = async () => {
    if (!teamStore.teamId) {
      memberList.value = [];
      return;
    }
    const res: any = await track(getTeamMembers(teamStore.teamId));
    if (res?.code === 200 && res.data) {
      memberList.value = res.data;
      pagination.total = res.data.length;
    } else {
      memberList.value = [];
      pagination.total = 0;
      if (res) loadError.value = true;
    }
  };

  // ========== 查看详情 ==========
  const currentShowUser = ref<TeamMemberVO | null>(null);
  const viewUserInfo = (record: TeamMemberVO) => {
    currentShowUser.value = record;
  };

  // ========== 分配角色 ==========
  const roleModalVisible = ref(false);
  const roleForm = reactive({
    userId: undefined as number | undefined,
    username: '',
    roleId: undefined as number | undefined,
  });

  const openRoleModal = (record: TeamMemberVO) => {
    roleForm.userId = record.userId;
    roleForm.username = record.username || '';
    roleForm.roleId = record.roleId;
    roleModalVisible.value = true;
  };

  const handleRoleSubmit = async (done: (closed: boolean) => void) => {
    if (!roleForm.userId || !teamStore.teamId || !roleForm.roleId) {
      Message.warning('缺少参数');
      done(false);
      return;
    }
    try {
      const res: any = await updateTeamMemberRole({
        teamId: teamStore.teamId,
        userId: roleForm.userId,
        roleId: roleForm.roleId,
      });
      if (res.code === 200) {
        Message.success('角色分配成功');
        await loadData();
        done(true);
      } else {
        Message.error(res.msg || '角色分配失败');
        done(false);
      }
    } catch (e) {
      console.error(e);
      Message.error('角色分配失败');
      done(false);
    }
  };

  // ========== 移除成员 ==========
  const handleRemove = async (record: TeamMemberVO) => {
    if (!record.userId || !teamStore.teamId) return;
    try {
      const res: any = await removeTeamMember({
        teamId: teamStore.teamId,
        userId: record.userId,
      });
      if (res.code === 200) {
        Message.success('移除成功');
        await loadData();
      } else {
        Message.error(res.msg || '移除失败');
      }
    } catch (e) {
      console.error(e);
      Message.error('移除失败');
    }
  };

  // ========== 状态切换 ==========
  const doUpdateStatus = async (record: TeamMemberVO, val: boolean) => {
    try {
      const res: any = await updateTeamMemberStatus({
        teamId: teamStore.teamId,
        userId: record.userId,
        status: val ? 1 : 0,
      });
      if (res.code === 200) {
        Message.success('状态更新成功');
        await loadData();
      } else {
        Message.error(res.msg || '状态更新失败');
      }
    } catch (e) {
      console.error(e);
      Message.error('状态更新失败');
    }
  };

  const handleStatusChange = async (record: TeamMemberVO, val: boolean) => {
    if (!record.userId || !teamStore.teamId) return;
    // 禁用是高危操作，二次确认；启用低风险直接执行（switch 用 model-value，取消不会残留态）
    if (!val) {
      Modal.confirm({
        title: '禁用成员',
        content: `确定禁用成员「${record.nickname || record.username}」吗？禁用后其将无法在该团队下进行任何操作。`,
        okText: '禁用',
        cancelText: '取消',
        okButtonProps: { status: 'danger' },
        onOk: () => doUpdateStatus(record, val),
      });
      return;
    }
    await doUpdateStatus(record, val);
  };

  // ========== 邀请成员 ==========
  const inviteModalVisible = ref(false);
  const inviteLoading = ref(false);
  const userLoading = ref(false);
  const userOptions = ref<UserVO[]>([]);
  const selectedUserIds = ref<string[]>([]);

  // 用户下拉全选
  const isAllUsersSelected = computed(
    () => userOptions.value.length > 0 && selectedUserIds.value.length === userOptions.value.length
  );
  const isUserSelectionPartial = computed(
    () => selectedUserIds.value.length > 0 && !isAllUsersSelected.value
  );
  const toggleSelectAllUsers = (checked: unknown) => {
    selectedUserIds.value = checked === true
      ? userOptions.value.map((u) => String(u.id))
      : [];
  };

  // 角色下拉中的特殊选项：项目管理员（值用负数占位，提交时转换为 assignAsProjectAdmin）
  const PROJECT_ADMIN_OPTION_VALUE = -1;
  const projectList = ref<Project[]>([]);
  const projectRoleList = ref<Role[]>([]);
  const inviteForm = reactive({
    assignProjectRole: false,
    projectId: undefined as number | undefined,
    projectRoleId: undefined as number | undefined,
  });

  // 多选用户时不支持指定项目管理员（owner_id 单人），自动清掉该选择
  watch(
    () => selectedUserIds.value.length,
    (len) => {
      if (len > 1 && inviteForm.projectRoleId === PROJECT_ADMIN_OPTION_VALUE) {
        inviteForm.projectRoleId = undefined;
        Message.info('多选用户时不支持指定项目管理员，已切换为默认角色');
      }
    }
  );

  const loadInviteProjectList = async () => {
    if (!teamStore.teamId) {
      projectList.value = [];
      return;
    }
    try {
      const res: any = await getProjectListByTeamId(String(teamStore.teamId));
      projectList.value = res.code === 200 && res.data ? res.data : [];
    } catch (e) {
      console.error(e);
      projectList.value = [];
    }
  };

  // 切换项目时加载该项目可分配的模板角色（全局模板 + 该项目模板）
  const onInviteProjectChange = async (projectId?: number) => {
    inviteForm.projectRoleId = undefined;
    projectRoleList.value = [];
    if (!projectId) return;
    try {
      const res: any = await getProjectRoleOptions(projectId);
      const all: Role[] = res.code === 200 && res.data ? res.data : [];
      // 项目级可分配角色 = 自定义模板(TEMPLATE)；项目管理员走 owner_id，用特殊选项表示
      projectRoleList.value = all.filter((r) => r.scopeType === 'TEMPLATE');
    } catch (e) {
      console.error(e);
    }
  };

  const openInviteModal = async () => {
    selectedUserIds.value = [];
    inviteForm.assignProjectRole = false;
    inviteForm.projectId = undefined;
    inviteForm.projectRoleId = undefined;
    projectRoleList.value = [];
    inviteModalVisible.value = true;
    if (!teamStore.teamId) return;
    userLoading.value = true;
    loadInviteProjectList();
    try {
      const res: any = await getInviteUserList(teamStore.teamId);
      if (res.code === 200 && res.data) {
        userOptions.value = res.data;
      }
    } catch (e) {
      console.error(e);
    } finally {
      userLoading.value = false;
    }
  };

  const handleInvite = async () => {
    if (!teamStore.teamId || selectedUserIds.value.length === 0) {
      Message.warning('请选择要邀请的用户');
      return;
    }
    if (inviteForm.assignProjectRole) {
      if (!inviteForm.projectId) {
        Message.warning('请选择项目');
        return;
      }
      if (
        inviteForm.projectRoleId === PROJECT_ADMIN_OPTION_VALUE &&
        selectedUserIds.value.length > 1
      ) {
        Message.warning('指定项目管理员时一次只能邀请一个用户');
        return;
      }
    }
    const assignAsProjectAdmin =
      inviteForm.assignProjectRole &&
      inviteForm.projectRoleId === PROJECT_ADMIN_OPTION_VALUE;
    inviteLoading.value = true;
    try {
      const res: any = await addTeamMember({
        teamId: teamStore.teamId,
        userList: selectedUserIds.value,
        assignProjectRole: inviteForm.assignProjectRole,
        projectId: inviteForm.projectId,
        projectRoleId:
          inviteForm.assignProjectRole && !assignAsProjectAdmin
            ? inviteForm.projectRoleId
            : undefined,
        assignAsProjectAdmin,
      });
      if (res.code === 200) {
        Message.success('邀请成功');
        inviteModalVisible.value = false;
        await loadData();
      } else {
        Message.error(res.msg || '邀请失败');
      }
    } catch (e) {
      console.error(e);
      Message.error('邀请失败');
    } finally {
      inviteLoading.value = false;
    }
  };

  // ========== 新建用户（仅超管） ==========
  const createUserModalVisible = ref(false);
  const createUserLoading = ref(false);
  const createUserResult = ref<{ username: string; password: string } | null>(null);
  const createUserForm = reactive({
    username: '',
    nickname: '',
    email: '',
    phone: '',
  });

  const openCreateUserModal = () => {
    createUserForm.username = '';
    createUserForm.nickname = '';
    createUserForm.email = '';
    createUserForm.phone = '';
    createUserResult.value = null;
    createUserModalVisible.value = true;
  };

  const handleCreateUser = async () => {
    if (!createUserForm.username) {
      Message.warning('用户名为必填项');
      return;
    }
    if (createUserForm.username.length < 5 || createUserForm.username.length > 20) {
      Message.warning('用户名长度需要5-20位');
      return;
    }
    if (!/^[a-zA-Z0-9_]+$/.test(createUserForm.username)) {
      Message.warning('用户名只能包含字母、数字和下划线');
      return;
    }
    createUserLoading.value = true;
    try {
      const res: any = await adminCreateUser({
        username: createUserForm.username,
        nickname: createUserForm.nickname || undefined,
        email: createUserForm.email || undefined,
        phone: createUserForm.phone || undefined,
      });
      if (res.code === 200 && res.data) {
        createUserResult.value = res.data;
        await loadAllUsers();
      } else {
        Message.error(res.msg || '新建用户失败');
      }
    } catch (e) {
      console.error(e);
      Message.error('新建用户失败');
    } finally {
      createUserLoading.value = false;
    }
  };

  // ========== 重置密码（仅超管） ==========
  const resetPwdModalVisible = ref(false);
  const resetPwdLoading = ref(false);
  const resetPwdTarget = ref<UserVO | null>(null);
  const resetPwdResult = ref<{ username: string; password: string } | null>(null);

  const openResetPwdModal = (record: UserVO) => {
    resetPwdTarget.value = record;
    resetPwdResult.value = null;
    resetPwdModalVisible.value = true;
  };

  const handleResetPwd = async () => {
    if (!resetPwdTarget.value) return;
    resetPwdLoading.value = true;
    try {
      const res: any = await adminResetPwd({
        userId: resetPwdTarget.value.id,
      });
      if (res.code === 200 && res.data) {
        resetPwdResult.value = res.data;
      } else {
        Message.error(res.msg || '重置密码失败');
      }
    } catch (e) {
      console.error(e);
      Message.error('重置密码失败');
    } finally {
      resetPwdLoading.value = false;
    }
  };

  // ========== 启用/禁用用户（仅超管，全部用户视图） ==========
  const handleToggleUserStatus = async (record: UserVO) => {
    if (record.id == null) return;
    const enabling = !(record.status === 1 || record.status === ('1' as any));
    try {
      const res: any = await updateUserStatus(record.id, enabling ? 1 : 0);
      if (res.code === 200) {
        Message.success(enabling ? '已启用' : '已禁用');
        await loadAllUsers();
      } else {
        Message.error(res.msg || '操作失败');
      }
    } catch (e) {
      console.error(e);
      Message.error('操作失败');
    }
  };

  // ========== 工具函数 ==========
  const formatDate = (date?: string) => {
    if (!date) return '-';
    return dayjs(date).format('YYYY-MM-DD HH:mm');
  };

  // 监听团队变化
  watch(
    () => teamStore.teamId,
    async () => {
      currentTeamId.value = teamStore.teamId || undefined;
      await loadCurrentUserRole();
      await loadTeams();
      await loadRoles();
      await loadData();
    },
    { immediate: true }
  );

  onMounted(async () => {
    await loadCurrentUserRole();
    if (props.embeddedInWorkspace) {
      viewMode.value = 'team';
      await loadTeams();
      await loadRoles();
      await loadData();
      return;
    }
    // 超管：用户管理只展示「全部用户」（平台级），团队成员管理已并入「团队管理」
    if (isSuperAdmin.value) {
      viewMode.value = 'all';
      await loadAllUsers();
      return;
    }
    await loadTeams();
    await loadRoles();
    await loadData();
  });
</script>

<style scoped lang="less">
  .user-manager {
    padding: 0 20px 20px;
  }

  .user-manager.embedded {
    padding: 0;
  }

  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
  }

  .page-title {
    font-size: 18px;
    font-weight: 600;
    color: var(--color-text-1);
  }

  .search-card {
    margin-bottom: 16px;
    border-radius: 12px;
  }

  .table-card {
    border-radius: 12px;
  }
</style>
