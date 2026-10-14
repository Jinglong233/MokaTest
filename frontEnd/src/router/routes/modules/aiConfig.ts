import {AppRouteRecordRaw} from '../types';

// AI 配置已迁入「AI 中心」菜单(/ai/config)，保留旧路径重定向避免旧链接失效
const AiConfig: AppRouteRecordRaw = {
    path: '/aiConfig',
    name: 'AiConfig',
    redirect: to => ({path: '/ai/config', query: to.query}),
    meta: {
        requiresAuth: false,
        requiresProject: true,
        hideInMenu: true,
    },
    children: [
        {
            path: 'index',
            redirect: to => ({path: '/ai/config', query: to.query}),
            meta: {requiresAuth: false, hideInMenu: true},
        },
    ],
};

export default AiConfig;
