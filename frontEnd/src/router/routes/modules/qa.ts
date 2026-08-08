import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const QA: AppRouteRecordRaw = {
    path: '/qa',
    name: 'Qa',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.qa',
        icon: 'icon-bug',
        requiresAuth: false,
        order: 3,
        requiresProject: true,
        permission: [
            'qa:requirement:view',
            'qa:bug:view',
            'qa:testcase:view',
            'qa:module:view',
            'qa:testplan:view',
            'qa:overview:view',
        ],
    },
    children: [
        {
            path: 'requirement',
            name: 'Requirement',
            component: () => import('@/views/qa/requirement/index.vue'),
            meta: {
                locale: 'menu.qa.requirement',
                requiresAuth: false,
                permission: 'qa:requirement:view',
            },
        },
        {
            path: 'requirement/edit/:id?',
            name: 'RequirementEdit',
            component: () => import('@/views/qa/requirement/edit/index.vue'),
            meta: {
                locale: 'menu.qa.requirementEdit',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'qa:requirement:view',
            },
        },
        {
            path: 'bug',
            name: 'Bug',
            component: () => import('@/views/qa/bug/index.vue'),
            meta: {
                locale: 'menu.qa.bug',
                requiresAuth: false,
                permission: 'qa:bug:view',
            },
        },
        {
            path: 'bug/edit/:id?',
            name: 'BugEdit',
            component: () => import('@/views/qa/bug/edit/index.vue'),
            meta: {
                locale: 'menu.qa.bugEdit',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'qa:bug:view',
            },
        },
        {
            path: 'testCase',
            name: 'TestCase',
            component: () => import('@/views/qa/testCase/index.vue'),
            meta: {
                locale: 'menu.qa.testCase',
                requiresAuth: false,
                permission: 'qa:testcase:view',
            },
        },
        {
            path: 'testCase/edit/:id?',
            name: 'TestCaseEdit',
            component: () => import('@/views/qa/testCase/edit/index.vue'),
            meta: {
                locale: 'menu.qa.testCaseEdit',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'qa:testcase:view',
            },
        },
        {
            path: 'testPlan',
            name: 'TestPlan',
            component: () => import('@/views/qa/testPlan/index.vue'),
            meta: {
                locale: 'menu.qa.testPlan',
                requiresAuth: false,
                permission: 'qa:testplan:view',
            },
        },
        {
            path: 'testPlan/detail/:planId',
            name: 'TestPlanDetail',
            component: () => import('@/views/qa/testPlan/detail/index.vue'),
            meta: {
                locale: 'menu.qa.testPlanDetail',
                requiresAuth: false,
                hideInMenu: true,
            },
        },
        {
            path: 'traceability',
            name: 'Traceability',
            component: () => import('@/views/qa/traceability/index.vue'),
            meta: {
                locale: 'menu.qa.traceability',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'qa:requirement:view',
            },
        },
    ],
};

export default QA;
