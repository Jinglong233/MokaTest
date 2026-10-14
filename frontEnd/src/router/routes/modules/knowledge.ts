import {AppRouteRecordRaw} from '../types';

// 知识库已迁入「AI 中心」菜单(/ai/knowledge)，保留旧路径重定向避免旧链接失效
const Knowledge: AppRouteRecordRaw = {
    path: '/knowledge',
    name: 'Knowledge',
    redirect: to => ({path: '/ai/knowledge', query: to.query}),
    meta: {
        requiresAuth: false,
        requiresProject: true,
        hideInMenu: true,
    },
    children: [
        {
            path: 'index',
            redirect: to => ({path: '/ai/knowledge', query: to.query}),
            meta: {requiresAuth: false, hideInMenu: true},
        },
        {
            path: 'edit',
            redirect: to => ({path: '/ai/knowledge/edit', query: to.query}),
            meta: {requiresAuth: false, hideInMenu: true},
        },
    ],
};

export default Knowledge;
