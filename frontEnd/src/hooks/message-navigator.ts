import {useRouter} from 'vue-router';
import {Message} from '@arco-design/web-vue';
import {useWorkspaceStore, useProjectStore, useTeamStore} from '@/store';
import usePermission from '@/hooks/permission';
import useDataStore from '@/store/modules/nav';
import {MessageRecord} from '@/api/message';
import {getBugDetail, getRequirementDetail} from '@/api/MyApi/qa';

/**
 * 业务类型到路由名称的映射
 */
const BIZ_ROUTE_MAP: Record<string, string> = {
  bug: 'Bug',
  requirement: 'Requirement',
};

/**
 * 业务类型到所需权限的映射
 */
const BIZ_PERMISSION_MAP: Record<string, string> = {
  bug: 'qa:bug:view',
  requirement: 'qa:requirement:view',
};

/**
 * 消息跳转导航器
 *
 * 封装消息点击后的跳转决策树：
 * 1. 资源存在性校验；
 * 2. 目标模块权限预检；
 * 3. 同团队切换项目 / 不同团队新建工作区；
 * 4. 跳转并高亮目标资源。
 */
export function useMessageNavigator() {
  const router = useRouter();
  const workspaceStore = useWorkspaceStore();
  const projectStore = useProjectStore();
  const teamStore = useTeamStore();
  const dataStore = useDataStore();
  const permission = usePermission();

  /**
   * 校验消息来源资源是否仍然存在
   */
  async function validateSourceExists(message: MessageRecord): Promise<boolean> {
    const {bizType, bizId} = message;
    if (!bizType || !bizId) return false;
    try {
      const detailRes: any = bizType === 'bug'
        ? await getBugDetail(bizId)
        : await getRequirementDetail(bizId);
      return detailRes.code === 200 && detailRes.data;
    } catch (e) {
      return false;
    }
  }

  /**
   * 执行跳转
   * @param message 消息记录
   * @param options 可选配置
   * @param.options.closePopover 跳转后是否关闭消息面板
   */
  async function navigate(message: MessageRecord, options?: { closePopover?: boolean }) {
    const {bizType, bizId, teamId, projectId} = message;
    const requiredPermission = BIZ_PERMISSION_MAP[bizType];

    // 1. 权限预检
    if (requiredPermission && !permission.hasPermission(requiredPermission)) {
      Message.warning('您暂无权限查看该消息来源');
      return;
    }

    // 2. 资源存在性校验
    const exists = await validateSourceExists(message);
    if (!exists) {
      Message.warning('该来源已被删除或不存在，仅展示消息快照');
      return;
    }

    // 3. 构造目标路由
    const routeName = BIZ_ROUTE_MAP[bizType];
    if (!routeName) {
      Message.warning('暂不支持该类型消息的跳转');
      return;
    }
    const targetRoute = {
      name: routeName,
      query: {highlight: String(bizId)},
    };

    // 4. 工作区上下文处理
    if (!teamId || !projectId) {
      Message.warning('消息缺少团队或项目信息，无法跳转');
      return;
    }

    // 确保项目列表数据可用
    if (dataStore.data.length === 0 || teamStore.getTeamId !== teamId) {
      await dataStore.fetchData();
    }
    const project = dataStore.data.find((p: any) => p.id === projectId);

    // 4.1 同团队：复用/激活已有 tab
    if (teamStore.getTeamId === teamId) {
      const existingTab = workspaceStore.findTabByTeam(teamId);
      if (existingTab) {
        workspaceStore.switchTab(existingTab.id);
        workspaceStore.updateActiveProject(projectId, project?.projectName);
      } else {
        workspaceStore.addTab({
          teamId,
          projectId,
          projectName: project?.projectName,
          route: targetRoute,
        });
      }
    } else {
      // 4.2 不同团队：切换团队并新建/激活 tab
      teamStore.setTeam(String(teamId), message.snapshot?.teamName || '');
      await dataStore.fetchData();
      workspaceStore.addTab({
        teamId,
        teamName: message.snapshot?.teamName,
        projectId,
        projectName: project?.projectName || message.snapshot?.projectName,
        route: targetRoute,
      });
    }

    // 同步 projectStore 保持兼容
    projectStore.setProject(projectId, project?.projectName || '');

    // 5. 关闭消息面板并跳转
    if (options?.closePopover !== false) {
      window.dispatchEvent(new CustomEvent('close-message-popover'));
    }
    await router.push(targetRoute);
  }

  return {
    navigate,
    validateSourceExists,
  };
}
