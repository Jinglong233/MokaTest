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
            path: 'testPlan/execute/:planId',
            name: 'TestPlanExecute',
            component: () => import('@/views/qa/testPlan/execute.vue'),
            meta: {
                locale: 'menu.qa.testPlanExecute',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'qa:testplan:execute',
            },
        },
        {
            path: 'testPlan/report/:id',
            name: 'TestPlanReportDetail',
            component: () => import('@/views/qa/testPlan/reportDetail.vue'),
            meta: {
                locale: 'menu.qa.testPlanReportDetail',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'qa:testplan:view',
            },
        },
        {
            path: 'review',
            name: 'qaReview',
            component: () => import('@/views/qa/review/index.vue'),
            meta: {
                locale: 'menu.qa.review',
                requiresAuth: false,
                // 评审已并入「用例管理」tab，菜单不再展示，保留路由避免旧链接失效
                hideInMenu: true,
                activeMenu: 'TestCase',
            },
        },
        {
            path: 'review/detail/:id',
            name: 'qaReviewDetail',
            component: () => import('@/views/qa/review/detail.vue'),
            meta: {
                locale: 'menu.qa.reviewDetail',
                requiresAuth: false,
                hideInMenu: true,
                activeMenu: 'TestCase',
            },
        },
        {
            path: 'review/workbench',
            name: 'qaReviewWorkbench',
            component: () => import('@/views/qa/review/workbench.vue'),
            meta: {
                locale: 'menu.qa.reviewWorkbench',
                requiresAuth: false,
                hideInMenu: true,
                activeMenu: 'TestCase',
            },
        },
        {
            path: 'review/create',
            name: 'qaReviewCreate',
            component: () => import('@/views/qa/review/create.vue'),
            meta: {
                locale: 'menu.qa.reviewCreate',
                requiresAuth: false,
                hideInMenu: true,
                activeMenu: 'TestCase',
            },
        },
        {
            path: 'review/edit/:id',
            name: 'qaReviewEdit',
            component: () => import('@/views/qa/review/create.vue'),
            meta: {
                locale: 'menu.qa.reviewEdit',
                requiresAuth: false,
                hideInMenu: true,
                activeMenu: 'TestCase',
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
        // AI 相关菜单已迁至「AI 中心」(/ai)，保留旧路径重定向避免旧链接失效
        {
            path: 'aiWorkbench',
            redirect: to => ({path: '/ai/workbench', query: to.query}),
            meta: {requiresAuth: false, hideInMenu: true},
        },
        {
            path: 'aiSkills',
            redirect: to => ({path: '/ai/skills', query: to.query}),
            meta: {requiresAuth: false, hideInMenu: true},
        },
        {
            path: 'aiReview',
            redirect: to => ({path: '/ai/review', query: to.query}),
            meta: {requiresAuth: false, hideInMenu: true},
        },
        {
            path: 'aiCaseGen',
            redirect: to => ({path: '/ai/caseGen', query: to.query}),
            meta: {requiresAuth: false, hideInMenu: true},
        },
    ],
};

export default QA;
