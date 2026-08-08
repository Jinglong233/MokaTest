import { RouteLocationNormalized, RouteRecordRaw } from 'vue-router';
import { useUserStore, usePermissionStore } from '@/store';

export default function usePermission() {
  const userStore = useUserStore();
  const permissionStore = usePermissionStore();

  return {
    /**
     * 权限列表是否已加载
     */
    get isLoaded() {
      return permissionStore.loaded;
    },

    /**
     * 手动加载指定团队/项目下的权限列表
     */
    async loadPermissions(teamId?: number | string, projectId?: number | string) {
      await permissionStore.loadPermissions(teamId, projectId);
    },

    /**
     * 基于角色判断路由是否可访问（旧逻辑兼容）
     */
    accessRouter(route: RouteLocationNormalized | RouteRecordRaw) {
      return (
        !route.meta?.requiresAuth ||
        !route.meta?.roles ||
        route.meta?.roles?.includes('*') ||
        route.meta?.roles?.includes(userStore.role as string)
      );
    },

    /**
     * 基于权限编码判断路由是否可访问
     * 路由 meta.permission 可配置单个权限编码或权限编码数组
     */
    accessRouterByPermission(route: RouteLocationNormalized | RouteRecordRaw) {
      const permission = route.meta?.permission;
      if (!permission) return true;
      if (Array.isArray(permission)) {
        return permissionStore.hasAnyPermission(permission as string[]);
      }
      if (typeof permission === 'string') {
        return permissionStore.hasPermission(permission);
      }
      return true;
    },

    /**
     * 判断当前用户是否拥有指定权限
     */
    hasPermission(permissionCode: string) {
      return permissionStore.hasPermission(permissionCode);
    },

    /**
     * 判断当前用户是否拥有任意一个指定权限
     */
    hasAnyPermission(permissionCodes: string[]) {
      return permissionStore.hasAnyPermission(permissionCodes);
    },

    findFirstPermissionRoute(_routers: any, role = 'admin') {
      const cloneRouters = [..._routers];
      while (cloneRouters.length) {
        const firstElement = cloneRouters.shift();
        if (
          firstElement?.meta?.roles?.find((el: string[]) => {
            return el.includes('*') || el.includes(role);
          })
        )
          return { name: firstElement.name };
        if (firstElement?.children) {
          cloneRouters.push(...firstElement.children);
        }
      }
      return null;
    },
    // You can add any rules you want
  };
}
