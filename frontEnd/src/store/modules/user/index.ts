import {defineStore} from 'pinia';
import {
    login as userLogin,
    getUserInfo,
    LoginData,
} from '@/api/MyApi/user';
import {setToken, clearToken} from '@/utils/auth';
import {removeRouteListener} from '@/utils/route-listener';
import {UserState} from './types';
import useAppStore from '../app';
import useWorkspaceStore from '../workspace';
import useProjectStore from '../project';
import useTeamStore from '../team';
import usePermissionStore from '../permission';
import useTabBarStore from '../tab-bar';
import useDataStore from '../nav';

const useUserStore = defineStore('user', {
    state: (): UserState => ({
        /**
         * 主键ID
         */
        id: undefined,

        /**
         * 用户名，唯一
         */
        username: undefined,

        /**
         * 昵称
         */
        nickname: undefined,

        /**
         * 头像URL
         */
        avatar: undefined,

        /**
         * 手机号
         */
        phone: undefined,

        /**
         * 邮箱
         */
        email: undefined,

        /**
         * 状态：0-禁用，1-正常
         */
        status: undefined,
        /**
         * 全局角色：super_admin-超级管理员，user-普通用户
         */
        role: undefined,
    }),

    getters: {
        userInfo(state: UserState): UserState {
            return {...state};
        },
    },

    actions: {
        // Set user's information
        setInfo(partial: Partial<UserState>) {
            this.$patch(partial);
        },

        // Reset user's information
        resetInfo() {
            this.$reset();
        },

        // Get user's information
        async info() {
            const res = await getUserInfo();
            const userInfo = res.data;
            console.log('[UserStore.info] received userInfo:', userInfo);

            // 用户切换时清空权限缓存，避免上一个账号（如 super_admin）的权限残留
            // 导致 teamManager 侧边栏或按钮级权限显示错乱
            if (this.id !== userInfo.id) {
                const permissionStore = usePermissionStore();
                permissionStore.clearPermissions();
            }

            this.setInfo(userInfo);
            console.log('[UserStore.info] after setInfo, store role:', this.role);
        },

        // Login
        async login(loginForm: LoginData) {
            try {
                const res = await userLogin(loginForm);
                setToken(res.data);
            } catch (err) {
                clearToken();
                throw err;
            }
        },
        logoutCallBack() {
            const appStore = useAppStore();
            const workspaceStore = useWorkspaceStore();
            const projectStore = useProjectStore();
            const teamStore = useTeamStore();
            const permissionStore = usePermissionStore();
            const tabBarStore = useTabBarStore();
            const dataStore = useDataStore();

            this.resetInfo();
            clearToken();
            removeRouteListener();

            // 清理工作区、项目、团队等业务状态，避免重新登录后残留旧账号数据
            workspaceStore.clearTabs();
            projectStore.clearProject();
            teamStore.clearTeam();
            permissionStore.clearPermissions();
            tabBarStore.resetTabList();
            dataStore.$reset();

            appStore.clearServerMenu();
        },
        // Logout
        async logout() {
            // 主动登出：只做本地清理，不调后端接口
            // 避免 token 已过期时后端返回 11011 导致拦截器弹"登录状态失效"
            this.logoutCallBack();
        },
    },
    persist: true

});

export default useUserStore;
