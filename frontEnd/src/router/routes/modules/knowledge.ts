import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const Knowledge: AppRouteRecordRaw = {
    path: '/knowledge',
    name: 'Knowledge',
    redirect: '/knowledge/index',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.knowledge',
        icon: 'icon-book',
        requiresAuth: false,
        order: 92,
        requiresProject: true,
        hideChildrenInMenu: true,
        permission: [
            'knowledge:view',
        ],
    },
    children: [
        {
            path: 'index',
            name: 'KnowledgeIndex',
            component: () => import('@/views/knowledge/index.vue'),
            meta: {
                locale: 'menu.knowledge',
                requiresAuth: false,
                requiresProject: true,
                activeMenu: 'Knowledge',
                ignoreCache: true,
            },
        },
        {
            path: 'edit',
            name: 'KnowledgeEdit',
            component: () => import('@/views/knowledge/edit.vue'),
            meta: {
                locale: 'menu.knowledge',
                requiresAuth: false,
                requiresProject: true,
                activeMenu: 'Knowledge',
                hideInMenu: true,
                ignoreCache: true,
            },
        },
    ],
};

export default Knowledge;
