import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const Project = {
    path: '/project',
    name: 'Project',
    redirect: '/project/projectInfo',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.project',
        requiresAuth: false,
        icon: 'icon-list',
        order: 0,
        hideChildrenInMenu: true,
        permission: 'project:view',
    },
    children: [
        {
            path: 'projectInfo',
            name: 'ProjectInfo',
            component: () => import('@/views/project/index.vue'),
            meta: {
                locale: 'menu.project',
                requiresAuth: false,
                roles: ['*'],
                permission: 'project:view',
                activeMenu: 'Project',
            },
        },
    ],
};

export default Project;
