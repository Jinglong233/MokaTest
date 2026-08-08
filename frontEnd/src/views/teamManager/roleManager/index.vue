<template>
  <div class="role-manager" :class="{ embedded: embeddedInWorkspace }">
    <!-- 顶部标题 -->
    <div class="page-header">
      <a-space v-if="!embeddedInWorkspace">
        <span class="page-title">角色管理</span>
        <a-tag v-if="teamStore.teamName" color="arcoblue">{{ teamStore.teamName }}</a-tag>
      </a-space>
      <a-space v-else></a-space>
      <a-button v-permission="'team:role:manage'" v-if="isSuperAdmin" type="primary" shape="round" @click="openRoleForm()">
        <template #icon><icon-plus /></template>
        新建角色
      </a-button>
    </div>

    <!-- 角色列表 -->
    <a-spin :loading="loading" style="width: 100%">
      <LoadError v-if="loadError" @retry="loadRoles" />
      <a-empty v-else-if="roleList.length === 0" style="margin-top: 80px" description="暂无角色数据" />
      <template v-else>
        <!-- 系统预设角色 -->
        <div class="role-section">
          <div class="role-section-header">
            <span class="role-section-title">系统预设角色</span>
            <span class="role-section-desc">平台内置角色，权限由系统统一维护、随功能升级自动放行，无需也无法逐项配置</span>
          </div>
          <a-table :data="systemRoles" :pagination="false" row-key="id">
            <template #columns>
              <a-table-column title="角色" :width="340">
                <template #cell="{ record }">
                  <div class="role-cell">
                    <div class="role-icon" :style="getRoleIconStyle(record.code)">
                      <icon-user />
                    </div>
                    <div class="role-cell-main">
                      <div class="role-cell-name">
                        <span class="role-name-text" :title="record.name">{{ record.name }}</span>
                        <a-tag size="small" color="arcoblue">系统预设</a-tag>
                      </div>
                      <div class="role-cell-desc" :title="record.description">{{ record.description || '暂无说明' }}</div>
                    </div>
                  </div>
                </template>
              </a-table-column>
              <a-table-column title="权限范围">
                <template #cell>
                  <span>拥有对应范围的全部权限</span>
                  <a-tooltip content="由平台统一维护，权限随功能升级自动放行，无需也无法逐项配置，因此不支持编辑/删除。">
                    <icon-info-circle class="scope-info-icon" />
                  </a-tooltip>
                </template>
              </a-table-column>
              <a-table-column title="成员数" :width="90" align="center">
                <template #cell="{ record }">
                  <span class="member-count">{{ getMemberCount(record.id) }}</span>
                </template>
              </a-table-column>
              <a-table-column title="角色码" :width="160">
                <template #cell="{ record }">
                  <span class="role-code" :title="record.code">{{ record.code }}</span>
                </template>
              </a-table-column>
              <a-table-column title="操作" :width="130" align="center">
                <template #cell="{ record }">
                  <a-button
                    v-permission="'team:role:manage'"
                    v-if="isSuperAdmin"
                    type="text"
                    size="small"
                    @click="openPermissionModal(record)"
                  >
                    <template #icon><icon-eye /></template>
                    查看权限
                  </a-button>
                  <span v-else>-</span>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </div>

        <!-- 自定义角色 -->
        <div class="role-section">
          <div class="role-section-header">
            <span class="role-section-title">自定义角色</span>
            <span class="role-section-desc">按团队需要创建，可逐项配置权限、编辑信息或删除</span>
          </div>
          <a-table :data="customRoles" :pagination="false" row-key="id">
            <template #columns>
              <a-table-column title="角色" :width="340">
                <template #cell="{ record }">
                  <div class="role-cell">
                    <div class="role-icon" :style="getRoleIconStyle(record.code)">
                      <icon-user />
                    </div>
                    <div class="role-cell-main">
                      <div class="role-cell-name">
                        <span class="role-name-text" :title="record.name">{{ record.name }}</span>
                        <a-tag size="small" color="green">自定义</a-tag>
                      </div>
                      <div class="role-cell-desc" :title="record.description">{{ record.description || '暂无说明' }}</div>
                    </div>
                  </div>
                </template>
              </a-table-column>
              <a-table-column title="权限范围">
                <template #cell="{ record }">
                  <span class="scope-custom">已配置 {{ getCustomPermCount(record) }} / {{ customVisibleCount }} 项权限</span>
                </template>
              </a-table-column>
              <a-table-column title="成员数" :width="90" align="center">
                <template #cell="{ record }">
                  <span class="member-count">{{ getMemberCount(record.id) }}</span>
                </template>
              </a-table-column>
              <a-table-column title="角色码" :width="160">
                <template #cell="{ record }">
                  <span class="role-code" :title="record.code">{{ record.code }}</span>
                </template>
              </a-table-column>
              <a-table-column title="操作" :width="160" align="center">
                <template #cell="{ record }">
                  <a-space v-if="isSuperAdmin" :size="4">
                    <a-button
                      v-permission="'team:role:manage'"
                      type="text"
                      size="small"
                      @click="openPermissionModal(record)"
                    >
                      <template #icon><icon-lock /></template>
                      配置权限
                    </a-button>
                    <a-dropdown trigger="click">
                      <a-button v-permission="'team:role:manage'" type="text" size="small" shape="circle">
                        <template #icon><icon-more /></template>
                      </a-button>
                      <template #content>
                        <a-doption @click="openRoleForm(record)">
                          <template #icon><icon-edit /></template>
                          编辑信息
                        </a-doption>
                        <a-doption @click="confirmDeleteRole(record)">
                          <template #icon><icon-delete /></template>
                          删除角色
                        </a-doption>
                      </template>
                    </a-dropdown>
                  </a-space>
                  <span v-else>-</span>
                </template>
              </a-table-column>
            </template>
          </a-table>
        </div>
      </template>
    </a-spin>

    <!-- 角色表单弹窗 -->
    <a-modal
      v-model:visible="roleFormVisible"
      :title="editingRole.id ? '编辑角色' : '新建角色'"
      @before-ok="handleRoleFormSubmit"
    >
      <a-form :model="editingRole" ref="roleFormRef"
      >
        <a-form-item
          label="角色名称"
          field="name"
          :rules="[{ required: true, message: '请输入角色名称' }]"
        >
          <a-input v-model="editingRole.name" placeholder="如：测试负责人" />
        </a-form-item>
        <a-form-item
          label="角色编码"
          field="code"
          :rules="[{ required: true, message: '请输入角色编码' }]"
        >
          <a-input
            v-model="editingRole.code"
            placeholder="如：test_leader"
            :disabled="!!editingRole.id"
          />
        </a-form-item>
        <a-form-item label="角色说明" field="description"
        >
          <a-textarea v-model="editingRole.description" placeholder="请输入角色说明" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 权限分配抽屉 -->
    <a-drawer
      :visible="permissionModalVisible"
      :title="`${currentRole?.name} 的${isSystemRoleReadonly ? '权限查看' : '权限配置'}`"
      :width="900"
      :mask-closable="false"
      :footer="true"
      @cancel="handleDrawerCancel"
    >
      <div class="permission-drawer-body">
        <p v-if="!isSystemRoleReadonly" style="color: #86909c; margin-bottom: 12px">
          按业务模块归类后勾选对应权限，保存后即时生效
        </p>

        <!-- 系统预设角色：拥有全部权限，无需逐项配置，只展示说明 -->
        <div v-if="isSystemRoleReadonly" class="system-role-desc">
          <div class="system-role-desc-title">{{ systemRoleDescription.title }}</div>
          <p class="system-role-desc-text">{{ systemRoleDescription.desc }}</p>
          <p class="system-role-desc-note">
            说明：系统预设角色拥有对应范围的全部权限，由系统统一维护、随功能升级自动放行，无需也无法逐项配置。
          </p>
        </div>

        <!-- 自定义角色：可编辑权限配置 -->
        <template v-if="!isSystemRoleReadonly">
          <!-- 顶部工具栏：进度 + 搜索 + 全局全选/清空 -->
          <div class="permission-toolbar">
            <div class="toolbar-progress">
              <span class="toolbar-progress-text">已选：<span class="toolbar-progress-checked">{{ checkedVisibleCount }}</span>/{{ totalVisibleCount }}</span>
              <a-progress
                class="toolbar-progress-bar"
                :percent="progressPercent"
                color="#165dff"
                :stroke-width="8"
                :show-text="false"
              />
            </div>
            <div class="toolbar-actions">
              <a-input-search
                v-model="searchKeyword"
                placeholder="搜索权限名称"
                allow-clear
                style="width: 220px"
                @input="handleSearchInput"
                @clear="handleSearchClear"
              />
              <a-button type="outline" size="mini" @click="handleSelectAll">
                <template #icon><icon-select-all /></template>
                全选
              </a-button>
              <a-button type="outline" size="mini" @click="handleClearAll">
                <template #icon><icon-eraser /></template>
                清空
              </a-button>
            </div>
          </div>

          <div class="permission-layout">
            <!-- 左侧模块导航 -->
            <div class="permission-nav" :class="{ dimmed: isSearching }">
              <div class="permission-nav-list">
                <div
                  v-for="domain in visibleGroupedPermissions"
                  :key="domain.key"
                  class="permission-nav-item"
                  :class="{ active: selectedDomainKey === domain.key }"
                  role="button"
                  tabindex="0"
                  @click="selectedDomainKey = domain.key"
                  @keydown.enter="selectedDomainKey = domain.key"
                >
                  <div class="nav-item-main">
                    <span class="nav-title">{{ domain.title }}</span>
                  </div>
                  <span class="nav-count">
                    {{ getDomainCheckedCount(domain) }}/{{ getDomainTotalCount(domain) }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 右侧权限内容 -->
            <div class="permission-content">
              <div v-if="isSearching && !hasFilteredModules" class="permission-empty">
                <a-empty description="未找到匹配的权限" />
              </div>

              <div class="permission-domain">
                <template v-for="group in filteredGroups" :key="group.domainKey">
                  <div v-if="isSearching" class="permission-domain-title">
                    <span>{{ group.domainTitle }}</span>
                  </div>
                  <div
                    v-for="module in group.modules"
                    :key="module.key"
                    class="permission-module-card"
                  >
                  <div class="module-card-header">
                    <div class="module-card-title" @click="toggleModuleAll(module)">
                      <a-checkbox
                        :model-value="getModuleIndeterminateState(module)"
                        :indeterminate="getModuleIndeterminate(module)"
                        @click.stop
                        @change="toggleModuleAll(module)"
                      >
                        <span>{{ module.title }}</span>
                      </a-checkbox>
                      <span class="module-card-count" @click.stop>
                        {{ getModuleCheckedCount(module) }}/{{ module.permissions.length }}
                      </span>
                    </div>
                    <a-button
                      type="text"
                      size="mini"
                      @click="toggleModuleAll(module)"
                    >
                      {{ getModuleCheckedCount(module) === module.permissions.length ? '取消全选' : '全选' }}
                    </a-button>
                  </div>
                  <a-checkbox-group
                    class="permission-checkbox-group"
                    :options="module.permissions.map((p) => ({ label: p.name, value: p.id as number }))"
                    :model-value="getModuleCheckedKeys(module)"
                    @change="(vals) => handleModulePermissionChange(module, vals as number[])"
                  />
                  </div>
                </template>
              </div>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <a-space>
          <span v-if="!isSystemRoleReadonly && changeCount > 0" class="change-count">
            已改动 {{ changeCount }} 项
          </span>
          <a-button @click="handleDrawerCancel">{{ isSystemRoleReadonly ? '关闭' : '取消' }}</a-button>
          <a-button
            v-if="!isSystemRoleReadonly"
            type="primary"
            :loading="savingPermission"
            :disabled="changeCount === 0"
            @click="handlePermissionSave"
          >保存</a-button>
        </a-space>
      </template>
    </a-drawer>

    <!-- 未保存改动确认 -->
    <a-modal
      v-model:visible="unsavedConfirmVisible"
      title="未保存的改动"
      :mask="false"
      :align-center="false"
      :simple="true"
      @ok="confirmCloseDrawer"
      @cancel="cancelCloseDrawer"
    >
      <p>当前权限有未保存的改动，关闭后修改将丢失，是否继续关闭？</p>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { Message, Modal } from '@arco-design/web-vue';
import type { FormInstance } from '@arco-design/web-vue';
import useTeamStore from '@/store/modules/team';
import { usePermissionStore } from '@/store/modules/permission';
import { useUserStore } from '@/store';
import useLoadState from '@/hooks/useLoadState';
import LoadError from '@/components/load-error/index.vue';
import { Role } from '@/types/domain/Role';
import { Permission } from '@/types/domain/Permission';
import { TeamMemberVO } from '@/types/vo/TeamMemberVO';
import {
  getRoleList,
  createRole,
  updateRole,
  deleteRole,
  getPermissionTree,
  getRolePermissions,
  assignRolePermissions,
} from '@/api/MyApi/rbac';
import { getTeamMembers } from '@/api/MyApi/team';
import { getUserInfo } from '@/api/MyApi/user';

const teamStore = useTeamStore();
const permissionStore = usePermissionStore();
const userStore = useUserStore();

const props = defineProps<{ embeddedInWorkspace?: boolean }>();

const memberList = ref<TeamMemberVO[]>([]);

// 当前登录用户角色，直接从接口获取，避免 Pinia 缓存导致判断错误
const currentUserRole = ref<string>('');
const isSuperAdmin = computed(() => currentUserRole.value === 'super_admin');

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

interface GroupedPermissionModule {
  key: string;
  title: string;
  permissions: Permission[];
}

interface GroupedPermissionDomain {
  key: string;
  title: string;
  modules: GroupedPermissionModule[];
}

const domainNames: Record<string, string> = {
  team: '团队管理',
  project: '项目管理',
  qa: '质量管理',
  auto: '自动化测试',
  report: '测试报告',
};

const domainOrder = ['team', 'project', 'qa', 'auto', 'report'];

const moduleMapping: Record<string, { domain: string; module: string }> = {
  'team:member': { domain: 'team', module: '团队成员' },
  'team:role': { domain: 'team', module: '团队角色' },
  project: { domain: 'project', module: '项目管理' },
  'qa:requirement': { domain: 'qa', module: '需求管理' },
  'qa:bug:comment': { domain: 'qa', module: 'BUG评论' },
  'qa:bug:operationlog': { domain: 'qa', module: 'BUG操作日志' },
  'qa:bug': { domain: 'qa', module: 'BUG管理' },
  'qa:testcase': { domain: 'qa', module: '用例管理' },
  'qa:testplan': { domain: 'qa', module: '测试计划' },
  'qa:module': { domain: 'qa', module: '模块管理' },
  'qa:overview': { domain: 'qa', module: '质量概览' },
  'auto:scene': { domain: 'auto', module: 'UI场景' },
  'auto:api': { domain: 'auto', module: 'API接口' },
  'auto:plan:webhook': { domain: 'auto', module: 'Webhook' },
  'auto:plan': { domain: 'auto', module: '自动化任务' },
  'auto:element': { domain: 'auto', module: '元素库' },
  'auto:env': { domain: 'auto', module: '环境管理' },
  'auto:globalvar': { domain: 'auto', module: '全局变量' },
  'auto:step': { domain: 'auto', module: '测试步骤' },
  'auto:overview': { domain: 'auto', module: '自动化概览' },
  report: { domain: 'report', module: '测试报告' },
};

const findModuleMapping = (code?: string) => {
  if (!code) return null;
  const entries = Object.entries(moduleMapping).sort(
    (a, b) => b[0].length - a[0].length
  );
  for (const [prefix, value] of entries) {
    if (code === prefix || code.startsWith(`${prefix}:`)) {
      return {
        domain: value.domain,
        moduleKey: prefix,
        moduleTitle: value.module,
      };
    }
  }
  return null;
};

const groupedPermissions = computed(() => {
  const groups: Record<
    string,
    { title: string; modules: Record<string, { title: string; permissions: Permission[] }> }
  > = {};

  const traverse = (nodes: Permission[]) => {
    nodes.forEach((node) => {
      const mapping = findModuleMapping(node.code);
      // 只把 BUTTON 类型权限作为可勾选叶子节点，MENU/API 仅用于分组，不显示为勾选框
      if (mapping && node.type === 'BUTTON') {
        if (!groups[mapping.domain]) {
          groups[mapping.domain] = { title: domainNames[mapping.domain], modules: {} };
        }
        if (!groups[mapping.domain].modules[mapping.moduleKey]) {
          groups[mapping.domain].modules[mapping.moduleKey] = {
            title: mapping.moduleTitle,
            permissions: [],
          };
        }
        groups[mapping.domain].modules[mapping.moduleKey].permissions.push(node);
      }
      if (node.children?.length) {
        traverse(node.children);
      }
    });
  };

  traverse(permissionTree.value);

  return Object.entries(groups)
    .map(([key, group]) => ({
      key,
      title: group.title,
      modules: Object.entries(group.modules)
        .map(([mKey, m]) => ({
          key: mKey,
          title: m.title,
          permissions: m.permissions.sort((a, b) => (a.sort || 0) - (b.sort || 0)),
        }))
        .sort((a, b) => (a.permissions[0]?.sort || 0) - (b.permissions[0]?.sort || 0)),
    }))
    .sort((a, b) => domainOrder.indexOf(a.key) - domainOrder.indexOf(b.key));
});

// 系统预设角色权限由系统硬编码，权限抽屉只读、不可编辑保存
const isSystemRoleReadonly = computed(() => currentRole.value?.isSystem === 1);

// 系统预设角色的固定权限说明（拥有全部权限，只展示说明，不展示勾选网格）
const systemRoleDescription = computed(() => {
  const code = currentRole.value?.code;
  if (code === 'team_admin' || code === 'admin') {
    return {
      title: '团队管理员',
      desc: '拥有本团队的全部权限：团队成员与角色管理，以及团队内所有项目的全部操作。',
    };
  }
  if (code === 'project_admin') {
    return {
      title: '项目管理员',
      desc: '项目管理员身份由 project.owner_id 决定（项目设置中变更），拥有该项目全部权限。此角色仅用于成员列表展示。',
    };
  }
  if (code === 'project_member') {
    return {
      title: '项目成员',
      desc: '内置只读成员：全项目查看权限（需求/BUG/用例/场景/报告等只读）。邀请成员时的默认角色，可随时调整为其他权限模板。',
    };
  }
  if (code === 'team_member' || code === 'tester' || code === 'member') {
    return {
      title: '普通成员（团队门票）',
      desc: '本身不含任何业务权限，仅表示用户已加入团队。具体能在某个项目做什么，由该项目分配给他的「项目角色模板」决定。',
    };
  }
  return {
    title: currentRole.value?.name || '系统预设角色',
    desc: '该角色拥有对应范围的全部权限，由系统统一维护。',
  };
});

// 自定义模板可见的权限分组：过滤掉 platform 平台级、整个团队管理域、以及项目域中的成员管理权限
// 模板只含执行类权限：项目域仅保留「项目查看」，项目创建/编辑/删除、成员管理均属管理类，不进模板
const customVisibleGroups = computed(() =>
  groupedPermissions.value
    .filter((domain) => domain.key !== 'team')
    .map((domain) => ({
      ...domain,
      modules: domain.modules
        .map((module) => ({
          ...module,
          permissions: module.permissions.filter((p) => {
            if (p.code?.startsWith('platform:')) return false;
            if (p.code?.startsWith('project:') && p.code !== 'project:view') return false;
            return true;
          }),
        }))
        .filter((module) => module.permissions.length > 0),
    }))
    .filter((domain) => domain.modules.length > 0)
);

const visibleGroupedPermissions = computed(() =>
  // 系统角色展示全部权限；自定义模板只展示可配置的执行类权限
  currentRole.value?.isSystem === 1 ? groupedPermissions.value : customVisibleGroups.value
);

const selectedDomainKey = ref<string>('');

// ========== 权限范围列：各角色已配置权限数 ==========
// roleId -> 已分配的权限ID列表（列表加载后批量拉取）
const rolePermissionMap = ref<Record<number, number[]>>({});

// 自定义角色可配置的权限ID集合与总数（与抽屉可见范围口径一致）
const customVisibleIds = computed(() => {
  const ids = new Set<number>();
  customVisibleGroups.value.forEach((domain) => {
    domain.modules.forEach((m) => {
      m.permissions.forEach((p) => {
        if (p.id) ids.add(p.id as number);
      });
    });
  });
  return ids;
});
const customVisibleCount = computed(() => customVisibleIds.value.size);

const getCustomPermCount = (role: Role) => {
  const ids = rolePermissionMap.value[role.id as number];
  if (!ids) return '…';
  return ids.filter((id) => customVisibleIds.value.has(id)).length;
};

const loadAllRolePermissions = async () => {
  const entries = await Promise.all(
    roleList.value.map(async (r) => {
      try {
        const res: any = await getRolePermissions(r.id as number);
        const ids = res.code === 200 && res.data ? res.data.map((id: any) => Number(id)) : [];
        return [r.id as number, ids] as [number, number[]];
      } catch (e) {
        return [r.id as number, []] as [number, number[]];
      }
    })
  );
  rolePermissionMap.value = Object.fromEntries(entries);
};

const getDomainCheckedCount = (domain: GroupedPermissionDomain) =>
  domain.modules.reduce(
    (sum, m) => sum + m.permissions.filter((p) => checkedPermissionKeys.value.includes(p.id as number)).length,
    0
  );

const getDomainTotalCount = (domain: GroupedPermissionDomain) =>
  domain.modules.reduce((sum, m) => sum + m.permissions.length, 0);

const getModuleCheckedCount = (module: GroupedPermissionModule) => {
  return module.permissions.filter((p) =>
    checkedPermissionKeys.value.includes(p.id as number)
  ).length;
};

const getModuleCheckedKeys = (module: GroupedPermissionModule) => {
  return module.permissions
    .filter((p) => checkedPermissionKeys.value.includes(p.id as number))
    .map((p) => p.id as number);
};

const getModuleIndeterminateState = (module: GroupedPermissionModule) => {
  const checked = getModuleCheckedCount(module);
  return checked === module.permissions.length && checked > 0;
};

const getModuleIndeterminate = (module: GroupedPermissionModule) => {
  const checked = getModuleCheckedCount(module);
  return checked > 0 && checked < module.permissions.length;
};

// ========== 搜索 ==========
const searchKeyword = ref('');
const isSearching = computed(() => searchKeyword.value.trim().length > 0);

// 非搜索态：只展示当前选中域；搜索态：跨全部业务域按权限名过滤，结果按域分组
const filteredGroups = computed(() => {
  if (!isSearching.value) {
    const domain = visibleGroupedPermissions.value.find((d) => d.key === selectedDomainKey.value);
    return domain
      ? [{ domainKey: domain.key, domainTitle: domain.title, modules: domain.modules }]
      : [];
  }
  const kw = searchKeyword.value.trim().toLowerCase();
  return visibleGroupedPermissions.value
    .map((domain) => ({
      domainKey: domain.key,
      domainTitle: domain.title,
      modules: domain.modules
        .map((module) => ({
          ...module,
          permissions: module.permissions.filter((p) => p.name?.toLowerCase().includes(kw)),
        }))
        .filter((module) => module.permissions.length > 0),
    }))
    .filter((group) => group.modules.length > 0);
});

const hasFilteredModules = computed(() =>
  filteredGroups.value.some((group) => group.modules.length > 0)
);

const handleSearchInput = () => {
  // 搜索时保持当前选中域，若当前域无结果会自动显示空状态
};

const handleSearchClear = () => {
  searchKeyword.value = '';
};

// ========== 依赖联动：view-update / view-delete / view-create 关系 ==========
// 规则：取消 view 时，同资源的 update/delete/create 被连带取消；
// 反向勾选 update/delete/create 时，自动补齐 view。
const viewDependencyMap = computed(() => {
  const map: Record<string, string[]> = {};
  const collect = (nodes: Permission[]) => {
    nodes.forEach((node) => {
      if (node.code && node.type === 'BUTTON') {
        const parts = node.code.split(':');
        const action = parts[parts.length - 1];
        const resource = parts.slice(0, -1).join(':');
        if (action && resource) {
          const viewCode = `${resource}:view`;
          if (node.code !== viewCode) {
            if (!map[viewCode]) map[viewCode] = [];
            if (!map[viewCode].includes(node.code)) map[viewCode].push(node.code);
          }
        }
      }
      if (node.children?.length) collect(node.children);
    });
  };
  collect(permissionTree.value);
  return map;
});

const applyDependencyChanges = (nextIds: Set<number>, permissionMap: Map<number, Permission>) => {
  // 收集所有 nextIds 对应的 code
  const nextCodes = new Set<string>();
  nextIds.forEach((id) => {
    const p = permissionMap.get(id);
    if (p?.code) nextCodes.add(p.code);
  });

  // 反向补齐：如果勾选了 update/delete/create，但没有 view，则自动补齐 view
  Object.entries(viewDependencyMap.value).forEach(([viewCode, childCodes]) => {
    const hasChild = childCodes.some((c) => nextCodes.has(c));
    if (hasChild) {
      const viewPermission = Array.from(permissionMap.values()).find((p) => p.code === viewCode);
      if (viewPermission?.id) nextIds.add(viewPermission.id as number);
    }
  });

  // 连带取消：如果取消了 view，则取消所有关联的 update/delete/create
  Object.entries(viewDependencyMap.value).forEach(([viewCode, childCodes]) => {
    if (!nextCodes.has(viewCode)) {
      childCodes.forEach((childCode) => {
        const childPermission = Array.from(permissionMap.values()).find((p) => p.code === childCode);
        if (childPermission?.id) nextIds.delete(childPermission.id as number);
      });
    }
  });
};

const allPermissionMap = computed(() => {
  const map = new Map<number, Permission>();
  const traverse = (nodes: Permission[]) => {
    nodes.forEach((node) => {
      if (node.id) map.set(node.id, node);
      if (node.children?.length) traverse(node.children);
    });
  };
  traverse(permissionTree.value);
  return map;
});

const handleModulePermissionChange = (
  module: GroupedPermissionModule,
  checkedIds: number[]
) => {
  const moduleIds = new Set(module.permissions.map((p) => p.id as number));
  let nextIds = new Set([
    ...checkedPermissionKeys.value.filter((id) => !moduleIds.has(id)),
    ...checkedIds,
  ]);
  applyDependencyChanges(nextIds, allPermissionMap.value);
  checkedPermissionKeys.value = Array.from(nextIds);
};

const toggleModuleAll = (module: GroupedPermissionModule) => {
  const moduleIds = module.permissions.map((p) => p.id as number);
  const allChecked = moduleIds.every((id) => checkedPermissionKeys.value.includes(id));
  let nextIds: Set<number>;
  if (allChecked) {
    nextIds = new Set(checkedPermissionKeys.value.filter((id) => !moduleIds.includes(id)));
  } else {
    nextIds = new Set(checkedPermissionKeys.value);
    moduleIds.forEach((id) => nextIds.add(id));
  }
  applyDependencyChanges(nextIds, allPermissionMap.value);
  checkedPermissionKeys.value = Array.from(nextIds);
};

// ========== 全局全选/清空 + 勾选进度 ==========
// 可见权限 ID 集合：进度与全选只针对页面上实际展示的权限，
// 已选中的隐藏权限（如平台级、MENU/API 节点）不参与计数
const visiblePermissionIds = computed(() => {
  const ids = new Set<number>();
  visibleGroupedPermissions.value.forEach((domain) => {
    domain.modules.forEach((m) => {
      m.permissions.forEach((p) => {
        if (p.id) ids.add(p.id as number);
      });
    });
  });
  return ids;
});

const totalVisibleCount = computed(() => visiblePermissionIds.value.size);

const checkedVisibleCount = computed(
  () => checkedPermissionKeys.value.filter((id) => visiblePermissionIds.value.has(id)).length
);

const handleSelectAll = () => {
  // 只做全选：补齐所有可见权限，保留隐藏权限的既有选中状态
  const next = new Set(checkedPermissionKeys.value);
  visiblePermissionIds.value.forEach((id) => next.add(id));
  checkedPermissionKeys.value = Array.from(next);
};

const handleClearAll = () => {
  checkedPermissionKeys.value = [];
};

// Arco a-progress 的 percent 为 0~1 小数，不是 0~100
const progressPercent = computed(() => {
  if (totalVisibleCount.value === 0) return 0;
  return checkedVisibleCount.value / totalVisibleCount.value;
});

// ========== 变更计数 ==========
const originalPermissionKeys = ref<number[]>([]);
const changeCount = computed(() => {
  const original = new Set(originalPermissionKeys.value);
  const current = new Set(checkedPermissionKeys.value);
  let count = 0;
  current.forEach((id) => {
    if (!original.has(id)) count += 1;
  });
  original.forEach((id) => {
    if (!current.has(id)) count += 1;
  });
  return count;
});

// ========== 未保存改动关闭拦截 ==========
const unsavedConfirmVisible = ref(false);
const pendingClose = ref(false);

const handleDrawerCancel = () => {
  if (isSystemRoleReadonly.value || changeCount.value === 0) {
    permissionModalVisible.value = false;
  } else {
    pendingClose.value = true;
    unsavedConfirmVisible.value = true;
  }
};

const confirmCloseDrawer = () => {
  unsavedConfirmVisible.value = false;
  permissionModalVisible.value = false;
  pendingClose.value = false;
};

const cancelCloseDrawer = () => {
  unsavedConfirmVisible.value = false;
  pendingClose.value = false;
};

// ========== 角色列表 ==========
const { loading, loadError, track } = useLoadState();
const roleList = ref<Role[]>([]);

const systemRoles = computed(() => roleList.value.filter((r) => r.isSystem === 1));
const customRoles = computed(() => roleList.value.filter((r) => r.isSystem !== 1));

const loadRoles = async () => {
  // 权限模板是平台级功能，不依赖当前团队；直接拉取全部角色（系统角色 + 模板角色）
  const res: any = await track(getRoleList());
  if (res?.code === 200 && res.data) {
    roleList.value = res.data;
    loadAllRolePermissions();
  } else if (res) {
    loadError.value = true;
  }
};

const loadMembers = async () => {
  if (!teamStore.teamId) {
    memberList.value = [];
    return;
  }
  try {
    const res: any = await getTeamMembers(teamStore.teamId);
    if (res.code === 200 && res.data) {
      memberList.value = res.data;
    }
  } catch (e) {
    console.error(e);
  }
};

const getMemberCount = (roleId?: number) => {
  if (!roleId) return 0;
  return memberList.value.filter((m) => m.roleId === roleId).length;
};

const getRoleIconStyle = (code?: string) => {
  const colorMap: Record<string, { bg: string; color: string }> = {
    admin: { bg: '#ffece8', color: '#f53f3f' },
    test_leader: { bg: '#fff7e8', color: '#ff7d00' },
    tester: { bg: '#e8ffea', color: '#00b42a' },
    developer: { bg: '#e8f3ff', color: '#165dff' },
    readonly: { bg: '#f2f3f5', color: '#86909c' },
  };
  const style = colorMap[code || ''] || { bg: '#f2f3f5', color: '#86909c' };
  return {
    background: style.bg,
    color: style.color,
  };
};

// ========== 角色表单 ==========
const roleFormVisible = ref(false);
const roleFormRef = ref<FormInstance>();
const editingRole = reactive<Role>({});

const openRoleForm = (role?: Role) => {
  if (role?.id) {
    Object.assign(editingRole, role);
  } else {
    Object.assign(editingRole, { id: undefined, name: '', code: '', description: '' });
  }
  roleFormVisible.value = true;
};

const handleRoleFormSubmit = async (done: (closed: boolean) => void) => {
  const validateRes = await roleFormRef.value?.validate();
  if (validateRes) {
    done(false);
    return;
  }
  try {
    editingRole.teamId = teamStore.teamId || undefined;
    const res: any = editingRole.id
      ? await updateRole(editingRole)
      : await createRole(editingRole);
    if (res.code === 200) {
      Message.success(editingRole.id ? '更新成功' : '创建成功');
      await loadRoles();
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

const confirmDeleteRole = (role: Role) => {
  Modal.confirm({
    title: '删除角色',
    content: '删除角色后，已分配该角色的成员将失去对应权限，确定删除吗？',
    okText: '删除',
    okButtonProps: { status: 'danger' },
    onOk: () => handleDeleteRole(role.id),
  });
};

const handleDeleteRole = async (id?: number) => {
  if (!id) return;
  try {
    const res: any = await deleteRole(id);
    if (res.code === 200) {
      Message.success('删除成功');
      await loadRoles();
    } else {
      Message.error(res.msg || '删除失败');
    }
  } catch (e) {
    console.error(e);
    Message.error('删除失败');
  }
};

// ========== 权限分配 ==========
const permissionModalVisible = ref(false);
const currentRole = ref<Role | null>(null);
const permissionTree = ref<Permission[]>([]);
const checkedPermissionKeys = ref<number[]>([]);
const savingPermission = ref(false);

const loadPermissionTree = async () => {
  try {
    const res: any = await getPermissionTree();
    if (res.code === 200 && res.data) {
      permissionTree.value = res.data;
    }
  } catch (e) {
    console.error(e);
  }
};

const openPermissionModal = async (role: Role) => {
  currentRole.value = role;
  checkedPermissionKeys.value = [];
  originalPermissionKeys.value = [];
  searchKeyword.value = '';
  permissionModalVisible.value = true;
  try {
    const res: any = await getRolePermissions(role.id as number);
    if (res.code === 200 && res.data) {
      const keys = res.data.map((id: any) => Number(id));
      checkedPermissionKeys.value = keys;
      originalPermissionKeys.value = [...keys];
    }
  } catch (e) {
    console.error(e);
  }
  // 默认选中第一个业务域
  if (visibleGroupedPermissions.value.length > 0) {
    selectedDomainKey.value = visibleGroupedPermissions.value[0].key;
  }
};

const handlePermissionSave = async () => {
  if (!currentRole.value?.id) {
    return;
  }
  savingPermission.value = true;
  try {
    const res: any = await assignRolePermissions(
      currentRole.value.id,
      checkedPermissionKeys.value
    );
    if (res.code === 200) {
      Message.success('权限分配成功');
      // 同步列表页「权限范围」计数
      rolePermissionMap.value = {
        ...rolePermissionMap.value,
        [currentRole.value.id]: [...checkedPermissionKeys.value],
      };
      permissionStore.clearPermissions();
      if (teamStore.teamId) {
        await permissionStore.loadPermissions(teamStore.teamId);
      }
      originalPermissionKeys.value = [...checkedPermissionKeys.value];
      permissionModalVisible.value = false;
    } else {
      Message.error(res.msg || '权限分配失败');
    }
  } catch (e) {
    console.error(e);
    Message.error('权限分配失败');
  } finally {
    savingPermission.value = false;
  }
};

// ========== 监听团队变化 ==========
watch(
  () => teamStore.teamId,
  async () => {
    await loadCurrentUserRole();
    await loadPermissionTree();
    await loadMembers();
    await loadRoles();
  },
  { immediate: true }
);

onMounted(async () => {
  await loadCurrentUserRole();
  loadPermissionTree();
  loadMembers();
  loadRoles();
});
</script>

<style scoped lang="less">
.role-manager {
  padding: 0 20px 20px;

  &.embedded {
    padding: 0;
  }
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

.role-section {
  margin-bottom: 24px;
}

.role-section-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 12px;
}

.role-section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.role-section-desc {
  font-size: 12px;
  color: var(--color-text-3);
}

.role-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.role-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  flex-shrink: 0;
}

.role-cell-main {
  flex: 1;
  min-width: 0;
}

.role-cell-name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 2px;
}

.role-name-text {
  font-weight: 600;
  color: var(--color-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-cell-desc {
  font-size: 12px;
  color: var(--color-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scope-info-icon {
  color: var(--color-text-3);
  margin-left: 4px;
  cursor: help;
}

.scope-custom {
  color: var(--color-text-2);
}

.member-count {
  font-weight: 600;
  color: var(--color-text-1);
}

.role-code {
  font-size: 12px;
  color: var(--color-text-3);
  font-family: 'Menlo', 'Monaco', monospace;
  background: var(--color-fill-2);
  padding: 2px 8px;
  border-radius: 4px;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.system-role-desc {
  padding: 16px;
  background: var(--color-fill-1);
  border-radius: 8px;

  .system-role-desc-title {
    font-size: 15px;
    font-weight: 600;
    color: var(--color-text-1);
    margin-bottom: 8px;
  }

  .system-role-desc-text {
    color: var(--color-text-2);
    line-height: 1.6;
    margin: 0 0 8px;
  }

  .system-role-desc-note {
    color: var(--color-text-3);
    font-size: 12px;
    line-height: 1.6;
    margin: 0;
  }
}

.permission-drawer-body {
  padding: 0 4px;
  height: 100%;
}

.permission-layout {
  display: flex;
  gap: 16px;
  height: 100%;
  min-height: 400px;
}

.permission-nav {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--color-border-2);
  padding-right: 16px;
  transition: opacity 0.2s ease;

  &.dimmed {
    opacity: 0.45;
  }
}

.permission-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border-2);
}

.toolbar-progress {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
}

.toolbar-progress-text {
  font-size: 13px;
  color: var(--color-text-2);
  white-space: nowrap;
}

.toolbar-progress-checked {
  color: #165dff;
  font-weight: 600;
}

.toolbar-progress-bar {
  flex: 1;
  max-width: 240px;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-shrink: 0;
}

.permission-nav-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.permission-nav-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;

  &:focus-visible {
    outline: 2px solid rgb(var(--primary-6));
    outline-offset: -2px;
  }

  &:hover {
    background: var(--color-fill-2);
  }

  &.active {
    background: var(--color-primary-light-1);

    .nav-title {
      color: var(--color-primary-6);
      font-weight: 500;
    }
  }
}

.nav-item-main {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.nav-title {
  color: var(--color-text-1);
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav-count {
  flex-shrink: 0;
  background: var(--color-fill-2);
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 18px;
  padding: 1px 10px;
  border-radius: 10px;
}

.permission-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.permission-domain {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 8px 4px;
}

.permission-domain-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-1);
  margin-bottom: -4px;
}

.permission-module-card {
  border-radius: 8px;
  border: 1px solid var(--color-border-2);
  background: var(--color-bg-2);
  overflow: hidden;
}

.module-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--color-fill-1);
  border-bottom: 1px solid var(--color-border-2);
}

.module-card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
  font-size: 14px;
  cursor: pointer;

  :deep(.arco-checkbox) {
    font-weight: 500;
  }
}

.module-card-count {
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 400;
  cursor: default;
}

.permission-checkbox-group {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px 24px;
  padding: 12px 16px;

  :deep(.arco-checkbox) {
    margin-right: 0;
  }
}

.permission-empty {
  padding: 40px 0;
}

:deep(.arco-drawer-footer) {
  display: flex;
  justify-content: flex-end;
}

.change-count {
  color: var(--color-text-3);
  font-size: 13px;
  margin-right: 8px;
}
</style>
