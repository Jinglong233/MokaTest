import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const USER: AppRouteRecordRaw = {
    path: '/user',
    name: 'user',
    component: DEFAULT_LAYOUT,
    redirect: '/user/setting',
    meta: {
        locale: 'menu.user',
        icon: 'icon-user',
        order: 7,
    },
    children: [
        {
            path: 'setting',
            name: 'Setting',
            component: () => import('@/views/user/setting/index.vue'),
            meta: {
                locale: 'menu.user.setting',
                roles: ['*'],
            },
        },
        {
            path: 'message',
            name: 'Message',
            component: () => import('@/views/user/message/index.vue'),
            meta: {
                locale: 'menu.user.message',
                requiresAuth: false,
                roles: ['*'],
                hideInMenu: true,
            },
        },
    ],
};

export default USER;
