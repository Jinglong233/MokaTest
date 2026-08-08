import {defineStore} from 'pinia';
import {getUserPermissions} from '@/api/MyApi/rbac';
import useUserStore from '@/store/modules/user';

/**
 * 权限状态管理
 *
 * <p>存储当前用户在当前团队下的权限编码列表，
 * 供按钮级权限控制（v-permission / hasPermission）和页面级权限判断使用。
 *
 * <p>切换团队时会自动清空并重新加载。
 */
export const usePermissionStore = defineStore('permission', {
    state: () => ({
        /**
         * 当前用户权限编码集合，使用 Set 便于 O(1) 查找
         */
        permissionCodes: new Set<string>(),
        /**
         * 是否已完成权限加载
         */
        loaded: false,
        /**
         * 当前缓存对应的团队ID
         */
        currentTeamId: null as number | string | null,
        /**
         * 当前缓存对应的项目ID（用于按项目精确区分权限）
         */
        currentProjectId: null as number | string | null,
    }),

    getters: {
        /**
         * 判断用户是否拥有指定权限
         */
        hasPermission: (state) => {
            return (permissionCode: string) => {
                // 超管前端兜底：无论权限是否加载成功、localStorage 是否残留脏数据，超管始终放行
                if (useUserStore().role === 'super_admin') return true;
                if (!permissionCode) return false;
                return state.permissionCodes.has(permissionCode);
            };
        },

        /**
         * 判断用户是否拥有任意一个指定权限
         */
        hasAnyPermission: (state) => {
            return (permissionCodes: string[]) => {
                if (useUserStore().role === 'super_admin') return true;
                if (!permissionCodes || permissionCodes.length === 0) return true;
                return permissionCodes.some((code) => state.permissionCodes.has(code));
            };
        },

        /**
         * 判断用户是否拥有所有指定权限
         */
        hasAllPermissions: (state) => {
            return (permissionCodes: string[]) => {
                if (useUserStore().role === 'super_admin') return true;
                if (!permissionCodes || permissionCodes.length === 0) return true;
                return permissionCodes.every((code) => state.permissionCodes.has(code));
            };
        },
    },

    actions: {
        /**
         * 加载当前用户在「指定团队 + 指定项目」下的权限列表
         * @param teamId 团队ID（可选，超级管理员不传时返回全部权限）
         * @param projectId 项目ID（可选，传入时按该项目精确解析项目级权限）
         */
        async loadPermissions(teamId?: number | string, projectId?: number | string) {
            const teamIdStr = teamId !== undefined && teamId !== null ? String(teamId) : null;
            const projectIdStr =
                projectId !== undefined && projectId !== null ? String(projectId) : null;
            // 团队、项目都未变化且已加载则跳过
            if (
                this.loaded &&
                this.currentTeamId === teamIdStr &&
                this.currentProjectId === projectIdStr
            ) {
                return;
            }
            this.clearPermissions();
            try {
                const res: any = await getUserPermissions(teamId, projectId);
                if (res.code === 200 && Array.isArray(res.data)) {
                    res.data.forEach((code: string) => {
                        if (code) this.permissionCodes.add(code);
                    });
                }
                this.currentTeamId = teamIdStr;
                this.currentProjectId = projectIdStr;
                this.loaded = true;
            } catch (e) {
                // 静默处理加载失败，避免控制台频繁打印权限相关错误
                this.clearPermissions();
            }
        },

        /**
         * 清空权限缓存
         */
        clearPermissions() {
            this.permissionCodes.clear();
            this.loaded = false;
            this.currentTeamId = null;
            this.currentProjectId = null;
        },
    },
});

export default usePermissionStore;
