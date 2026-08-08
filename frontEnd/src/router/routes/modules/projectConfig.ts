import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const ProjectConfig: AppRouteRecordRaw = {
    path: '/projectConfig',
    name: 'ProjectConfig',
    redirect: '/projectConfig/index',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.projectConfig',
        icon: 'icon-settings',
        requiresAuth: false,
        order: 91,
        requiresProject: true,
        hideChildrenInMenu: true,
        // 仅项目管理类角色（超管/团队管理员/项目管理员）可见；
        // 普通成员的项目配置读取走 /list（project:view），供字段显隐使用，无需看到本页面
        permission: [
            'project:config:update',
        ],
    },
    children: [
        {
            path: 'index',
            name: 'ProjectConfigIndex',
            component: () => import('@/views/projectConfig/index.vue'),
            meta: {
                locale: 'menu.projectConfig',
                requiresAuth: false,
                requiresProject: true,
                activeMenu: 'ProjectConfig',
                ignoreCache: true,
            },
        },
    ],
};

export default ProjectConfig;
