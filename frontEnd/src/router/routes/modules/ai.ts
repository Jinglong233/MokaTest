import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const AI: AppRouteRecordRaw = {
    path: '/ai',
    name: 'Ai',
    redirect: '/ai/workbench',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.ai',
        icon: 'icon-robot',
        requiresAuth: false,
        order: 5,
        requiresProject: true,
        permission: [
            'qa:ai:use',
            'knowledge:view',
            'project:aiconfig:update',
        ],
    },
    children: [
        {
            path: 'workbench',
            name: 'AiWorkbench',
            component: () => import('@/views/qa/ai/workbench.vue'),
            meta: {
                locale: 'menu.qa.aiWorkbench',
                requiresAuth: false,
                permission: 'qa:ai:use',
            },
        },
        {
            path: 'skills',
            name: 'AiSkills',
            component: () => import('@/views/qa/ai/skills.vue'),
            meta: {
                locale: 'menu.qa.aiSkills',
                requiresAuth: false,
                permission: 'qa:ai:use',
            },
        },
        {
            path: 'review',
            name: 'AiReview',
            component: () => import('@/views/qa/ai/review.vue'),
            meta: {
                locale: 'menu.qa.aiReview',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'qa:ai:use',
            },
        },
        {
            path: 'caseGen',
            name: 'AiCaseGen',
            component: () => import('@/views/qa/ai/caseGen.vue'),
            meta: {
                locale: 'menu.qa.aiCaseGen',
                requiresAuth: false,
                hideInMenu: true,
                permission: 'qa:ai:use',
            },
        },
        {
            path: 'knowledge',
            name: 'KnowledgeIndex',
            component: () => import('@/views/knowledge/index.vue'),
            meta: {
                locale: 'menu.knowledge',
                requiresAuth: false,
                requiresProject: true,
                ignoreCache: true,
                permission: 'knowledge:view',
            },
        },
        {
            path: 'knowledge/edit',
            name: 'KnowledgeEdit',
            component: () => import('@/views/knowledge/edit.vue'),
            meta: {
                locale: 'menu.knowledge',
                requiresAuth: false,
                requiresProject: true,
                hideInMenu: true,
                activeMenu: 'KnowledgeIndex',
                ignoreCache: true,
                permission: 'knowledge:view',
            },
        },
        {
            path: 'config',
            name: 'AiConfigIndex',
            component: () => import('@/views/aiConfig/index.vue'),
            meta: {
                locale: 'menu.aiConfig',
                requiresAuth: false,
                requiresProject: true,
                ignoreCache: true,
                permission: 'project:aiconfig:update',
            },
        },
    ],
};

export default AI;
