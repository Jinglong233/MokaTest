import {AppRouteRecordRaw} from '../types';


const RESULT: AppRouteRecordRaw = {
    path: '/team',
    name: 'team',
    component: () => import('@/views/teamManager/index.vue'),
    redirect: '/team/workspace',
    meta: {
        requiresAuth: true,
        hideInMenu: true,
        // 团队列表是所有登录用户都能进入的公共页面，不在父路由上限制权限
        // 子页面（用户管理、角色权限）分别按需控制
        // 注意：角色权限页对团队管理员只读，实际管理操作仅超管可用
    },
    children: [
        {
            path: 'overview',
            name: 'AdminOverview',
            component: () => import('@/views/teamManager/overview/index.vue'),
            meta: {
                requiresAuth: true,
                roles: ['super_admin'],
            },
        },
        {
            // 团队管理员工作区：项目列表（任何团队成员可查看）
            path: 'workspace',
            name: 'TeamWorkspace',
            component: () => import('@/views/teamManager/workspace/index.vue'),
            meta: {
                requiresAuth: true,
            },
        },
        {
            // 团队管理：编辑/解散当前团队
            path: 'teamSetting',
            name: 'TeamSetting',
            component: () => import('@/views/teamManager/teamSetting/index.vue'),
            meta: {
                requiresAuth: true,
                permission: 'team:member:manage',
            },
        },
        {
            path: 'userManager',
            name: 'UserManager',
            component: () => import('@/views/teamManager/userManager/index.vue'),
            meta: {
                // 任何团队成员均可查看团队成员列表（只读）；
                // 邀请/移除/禁用/分配角色等管理操作由页面内按钮级权限控制
                requiresAuth: true,
            },
        },
        {
            path: 'roleManager',
            name: 'RoleManager',
            component: () => import('@/views/teamManager/roleManager/index.vue'),
            meta: {
                requiresAuth: true,
                roles: ['super_admin'],
                permission: 'team:role:manage',
            },
        },
        {
            // 操作日志：放到超管平台菜单下
            path: 'operationLog',
            name: 'OperationLog',
            component: () => import('@/views/system/operationLog/index.vue'),
            meta: {
                requiresAuth: true,
                roles: ['super_admin'],
            },
        },
        {
            // AI 模型配置：平台级配置，仅超管可见
            path: 'aiConfig',
            name: 'AiConfig',
            component: () => import('@/views/system/aiConfig/index.vue'),
            meta: {
                requiresAuth: true,
                roles: ['super_admin'],
            },
        },
        {
            // 登录日志：安全审计数据，仅超管可见
            path: 'loginLog',
            name: 'LoginLog',
            component: () => import('@/views/system/loginLog/index.vue'),
            meta: {
                requiresAuth: true,
                roles: ['super_admin'],
            },
        },
        {
            // 个人中心：在工作台布局内展示
            path: 'userSetting',
            name: 'TeamUserSetting',
            component: () => import('@/views/user/setting/index.vue'),
            meta: {
                requiresAuth: true,
            },
        },
    ]
};

export default RESULT;
