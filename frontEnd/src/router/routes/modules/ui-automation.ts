import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const UiAutomation: AppRouteRecordRaw = {
    path: '/ui-automation',
    name: 'UiAutomation',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.uiAutomation',
        icon: 'icon-check-circle',
        requiresAuth: false,
        order: 2,
        requiresProject: true,
        permission: [
            'auto:scene:view',
            'auto:element:view',
        ],
    },
    children: [
        {
            path: 'sceneList',
            name: 'UiSceneList',
            component: () => import('@/views/scene/ui/index.vue'),
            meta: {
                locale: 'menu.uiAutomation.sceneList',
                requiresAuth: false,
                permission: 'auto:scene:view',
            },
        },
        {
            path: 'elementList',
            name: 'ElementList',
            component: () => import('@/views/element/index.vue'),
            meta: {
                locale: 'menu.uiAutomation.elementList',
                requiresAuth: false,
                permission: 'auto:element:view',
            },
        },
    ],
};

export default UiAutomation;
