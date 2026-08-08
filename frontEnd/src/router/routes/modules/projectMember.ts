import {DEFAULT_LAYOUT} from '../base';
import {AppRouteRecordRaw} from '../types';

const ProjectMember: AppRouteRecordRaw = {
    path: '/projectMember',
    name: 'ProjectMember',
    redirect: '/projectMember/index',
    component: DEFAULT_LAYOUT,
    meta: {
        locale: 'menu.projectMember',
        icon: 'icon-user-group',
        requiresAuth: false,
        // 放到左侧菜单最后
        order: 90,
        requiresProject: true,
        // 单个菜单项，不显示为下拉子菜单
        hideChildrenInMenu: true,
        // 项目成员列表对本项目所有成员可见（只读），菜单不做权限码过滤；
        // 非本项目成员直接访问由后端成员身份校验拦截；邀请/分配/移除由页面内按钮级权限控制
    },
    children: [
        {
            path: 'index',
            name: 'ProjectMemberIndex',
            component: () => import('@/views/project/member/index.vue'),
            meta: {
                locale: 'menu.projectMember',
                requiresAuth: false,
                requiresProject: true,
                activeMenu: 'ProjectMember',
                // 项目成员页 URL 固定、projectId 在 store 中，不能走 keep-alive 缓存，
                // 否则切换项目后会复用上一个项目的缓存实例导致成员"串项目"
                ignoreCache: true,
            },
        },
    ],
};

export default ProjectMember;
