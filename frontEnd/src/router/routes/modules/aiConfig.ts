import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const AiConfig: AppRouteRecordRaw = {
    path: '/aiConfig',
    name: 'AiConfig',
    redirect: '/aiConfig/index',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.aiConfig',
        icon: 'icon-robot',
        requiresAuth: false,
        order: 92,
        requiresProject: true,
        hideChildrenInMenu: true,
        permission: ['project:aiconfig:update'],
    },
    children: [
        {
            path: 'index',
            name: 'AiConfigIndex',
            component: () => import('@/views/aiConfig/index.vue'),
            meta: {
                locale: 'menu.aiConfig',
                requiresAuth: false,
                requiresProject: true,
                activeMenu: 'AiConfig',
                ignoreCache: true,
            },
        },
    ],
};

export default AiConfig;
