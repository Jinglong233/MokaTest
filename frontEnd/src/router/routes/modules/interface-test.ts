import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const InterfaceTest: AppRouteRecordRaw = {
    path: '/interface-test',
    name: 'InterfaceTest',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.interfaceTest',
        icon: 'icon-code',
        requiresAuth: false,
        order: 1,
        requiresProject: true,
        permission: [
            'auto:api:view',
            'auto:env:view',
            'auto:globalvar:view',
            'auto:function:view',
        ],
    },
    children: [
        {
            path: 'apiList',
            name: 'ApiList',
            component: () => import('@/views/apiManager/index.vue'),
            meta: {
                locale: 'menu.interfaceTest.apiList',
                requiresAuth: false,
                permission: 'auto:api:view',
            },
        },
        {
            path: 'sceneList',
            name: 'ApiSceneList',
            component: () => import('@/views/scene/api/index.vue'),
            meta: {
                locale: 'menu.interfaceTest.sceneList',
                requiresAuth: false,
                permission: 'auto:scene:view',
            },
        },
        {
            path: 'envConfig',
            name: 'InterfaceEnvConfig',
            component: () => import('@/views/env/index.vue'),
            meta: {
                locale: 'menu.interfaceTest.envConfig',
                requiresAuth: false,
                permission: 'auto:env:view',
            },
        },
        {
            path: 'dataTemplate',
            name: 'DataTemplate',
            component: () => import('@/views/dataTemplate/index.vue'),
            meta: {
                locale: 'menu.interfaceTest.dataTemplate',
                requiresAuth: false,
                permission: 'auto:template:view',
            },
        },
        {
            path: 'customFunction',
            name: 'CustomFunction',
            component: () => import('@/views/customFunction/index.vue'),
            meta: {
                locale: 'menu.interfaceTest.customFunction',
                requiresAuth: false,
                permission: 'auto:function:view',
            },
        },
    ],
};

export default InterfaceTest;
