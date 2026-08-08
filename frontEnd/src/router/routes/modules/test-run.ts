import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const TestRun: AppRouteRecordRaw = {
    path: '/test-run',
    name: 'TestRun',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.testRun',
        icon: 'icon-calendar-clock',
        requiresAuth: false,
        order: 4,
        requiresProject: true,
        permission: [
            'auto:plan:view',
            'auto:plan:webhook:view',
            'report:view',
        ],
    },
    children: [
        {
            path: 'planList',
            name: 'PlanList',
            component: () => import('@/views/plan/index.vue'),
            meta: {
                locale: 'menu.testRun.planList',
                requiresAuth: false,
                permission: 'auto:plan:view',
            },
        },
        {
            path: 'planDetail/:planId',
            name: 'PlanDetail',
            component: () => import('@/views/plan/planDetail/index.vue'),
            meta: {
                locale: 'menu.testRun.planDetail',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'auto:plan:view',
            },
        },
        {
            path: 'webhook',
            name: 'PlanWebhook',
            component: () => import('@/views/plan/webhook/index.vue'),
            meta: {
                locale: 'menu.testRun.webhook',
                requiresAuth: false,
                permission: 'auto:plan:webhook:view',
            },
        },
        {
            path: 'reportList',
            name: 'ReportList',
            component: () => import('@/views/report/index.vue'),
            meta: {
                locale: 'menu.testRun.reportList',
                requiresAuth: false,
                permission: 'report:view',
            },
        },
        {
            path: 'reportDetail/:reportId',
            name: 'ReportDetail',
            component: () => import('@/views/report/report-detail/index.vue'),
            meta: {
                locale: 'menu.testRun.reportDetail',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'report:view',
            },
        },
    ],
};

export default TestRun;
