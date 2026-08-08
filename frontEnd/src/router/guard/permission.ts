import type { Router, RouteRecordNormalized } from 'vue-router';
import NProgress from 'nprogress'; // progress bar

import usePermission from '@/hooks/permission';
import { useUserStore, useAppStore, useTeamStore, useProjectStore } from '@/store';
import { appRoutes } from '../routes';
import { WHITE_LIST, NOT_FOUND, NO_PERMISSION } from '../constants';
import { getTeamList } from '@/api/MyApi/team';

export default function setupPermissionGuard(router: Router) {
  router.beforeEach(async (to, from, next) => {
    const appStore = useAppStore();
    const userStore = useUserStore();
    const teamStore = useTeamStore();
    const projectStore = useProjectStore();
    const Permission = usePermission();

    // 超管进入 /team 下的平台管理页时，不需要绑定具体团队上下文
    const isAdminTeamRoute =
      to.path.startsWith('/team') && userStore.role === 'super_admin';

    // 若已选择团队且权限未加载，先加载权限列表
    if (teamStore.teamId && !Permission.isLoaded) {
      if (isAdminTeamRoute) {
        // 超管在 /team 平台管理页直接加载全平台权限，不绑定到某个团队
        await Permission.loadPermissions();
      } else {
        await Permission.loadPermissions(teamStore.teamId);
      }
    }

    // 首次登录或权限未加载时，尝试自动选择第一个团队并加载权限
    // （团队守卫会跳过 /team 路径，导致这些页面菜单权限无法及时加载）
    if (!Permission.isLoaded) {
      if (isAdminTeamRoute) {
        // 超管进入平台管理页直接加载全平台权限，不强制选择团队
        await Permission.loadPermissions();
      } else {
        try {
          const res: any = await getTeamList();
          const teamList = res?.data || [];
          if (teamList.length > 0 && teamList[0].id != null) {
            // setTeam 内部会触发权限加载，等待完成后再放行，避免菜单闪现/缺失
            await teamStore.setTeam(String(teamList[0].id), teamList[0].teamName || '个人团队');
          } else if (userStore.role === 'super_admin') {
            // 超级管理员没有团队时也能加载全部权限
            await Permission.loadPermissions();
          }
        } catch (e) {
          // 静默处理：无团队时不阻塞路由，后续按未加载权限逻辑走
        }
      }
    }

    // 超管访问平台总览直接放行（兜底，避免 roles 判断异常）
    if (to.path === '/team/overview' && userStore.role === 'super_admin') {
      next();
      NProgress.done();
      return;
    }

    // 进入业务页面时，按「当前团队 + 当前项目」精确加载权限，实现不同项目权限清晰区分。
    // 权限 store 以 team+project 为缓存键，未变化时自动跳过，仅在切换项目时重新加载。
    if (!isAdminTeamRoute && teamStore.teamId) {
      const pid = projectStore.projectId;
      await Permission.loadPermissions(
        teamStore.teamId,
        pid !== null && pid !== undefined ? pid : undefined
      );
    }

    // 1. 基于角色的旧权限判断
    const roleAllow = Permission.accessRouter(to);
    // 2. 基于权限编码的新权限判断（路由 meta.permission）
    const permissionAllow = Permission.accessRouterByPermission(to);

    if (!roleAllow || !permissionAllow) {
      next(NO_PERMISSION);
      NProgress.done();
      return;
    }

    if (appStore.menuFromServer) {
      // 针对来自服务端的菜单配置进行处理
      // Handle routing configuration from the server

      // 根据需要自行完善来源于服务端的菜单配置的permission逻辑
      // Refine the permission logic from the server's menu configuration as needed
      if (
        !appStore.appAsyncMenus.length &&
        !WHITE_LIST.find((el) => el.name === to.name)
      ) {
        await appStore.fetchServerMenuConfig();
      }
      const serverMenuConfig = [...appStore.appAsyncMenus, ...WHITE_LIST];

      let exist = false;
      while (serverMenuConfig.length && !exist) {
        const element = serverMenuConfig.shift();
        if (element?.name === to.name) exist = true;

        if (element?.children) {
          serverMenuConfig.push(
            ...(element.children as unknown as RouteRecordNormalized[])
          );
        }
      }
      if (exist) {
        next();
      } else next(NOT_FOUND);
    } else {
      next();
    }
    NProgress.done();
  });
}
