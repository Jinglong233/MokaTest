import {defineStore} from 'pinia';
import {WorkspaceState} from './type';
import {WorkspaceTab, WorkspaceSource} from '@/types/domain/workspace';
import {computed, ref} from 'vue';

export const WORKSPACE_STORE_ID = 'workspace';

/**
 * 生成唯一标签ID
 */
function generateTabId(): string {
    return `ws_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
}

/**
 * 构建标签标题
 */
function buildTabTitle(source: WorkspaceSource): string {
    const teamName = source.teamName || '团队';
    const projectName = source.projectName || '项目';
    return `${teamName} · ${projectName}`;
}

/**
 * 工作区状态管理
 *
 * 管理多团队/项目的工作区标签页，每个标签页绑定一个团队(teamId)和当前项目(activeProjectId)。
 * 与 projectStore/teamStore 不同，workspaceStore 支持同时维护多个上下文。
 */
export const useWorkspaceStore = defineStore(WORKSPACE_STORE_ID, () => {
    const tabs = ref<WorkspaceTab[]>([]);
    const activeTabId = ref<string | null>(null);

    const activeTab = computed(() => tabs.value.find((t) => t.id === activeTabId.value) ?? null);
    const activeTeamId = computed(() => activeTab.value?.teamId ?? null);
    const activeProjectId = computed(() => activeTab.value?.activeProjectId ?? null);
    const hasTabs = computed(() => tabs.value.length > 0);

    /**
     * 添加或切换工作区标签页
     * @param source 工作区来源信息
     * @param forceNew 是否强制新建标签（同一团队多项目并行时使用）
     * @returns 激活的标签页
     */
    function addTab(source: WorkspaceSource, forceNew = false): WorkspaceTab {
        if (!forceNew) {
            const existing = tabs.value.find((t) => t.teamId === source.teamId);
            if (existing) {
                existing.activeProjectId = source.projectId;
                existing.projectName = source.projectName || existing.projectName;
                existing.teamName = source.teamName || existing.teamName;
                existing.title = buildTabTitle({
                    teamId: existing.teamId,
                    teamName: existing.teamName,
                    projectId: existing.activeProjectId,
                    projectName: existing.projectName,
                });
                if (source.route) {
                    existing.savedRoute = source.route;
                }
                activeTabId.value = existing.id;
                return existing;
            }
        }

        const tab: WorkspaceTab = {
            id: generateTabId(),
            teamId: source.teamId,
            activeProjectId: source.projectId,
            teamName: source.teamName,
            projectName: source.projectName,
            title: buildTabTitle(source),
            savedRoute: source.route,
        };
        tabs.value.push(tab);
        activeTabId.value = tab.id;
        return tab;
    }

    /**
     * 切换到指定标签页
     */
    function switchTab(tabId: string): WorkspaceTab | null {
        const exists = tabs.value.some((t) => t.id === tabId);
        if (exists) {
            activeTabId.value = tabId;
            return activeTab.value;
        }
        return null;
    }

    /**
     * 关闭指定标签页
     * @returns 关闭后激活的标签页ID，若全部关闭则返回 null
     */
    function closeTab(tabId: string): string | null {
        const index = tabs.value.findIndex((t) => t.id === tabId);
        if (index === -1) return activeTabId.value;

        tabs.value.splice(index, 1);

        if (activeTabId.value === tabId) {
            const nextTab = tabs.value[Math.min(index, tabs.value.length - 1)];
            activeTabId.value = nextTab?.id ?? null;
        }
        return activeTabId.value;
    }

    /**
     * 更新当前激活标签页的项目
     */
    function updateActiveProject(projectId: number, projectName?: string): void {
        const tab = activeTab.value;
        if (!tab) return;
        tab.activeProjectId = projectId;
        if (projectName) {
            tab.projectName = projectName;
        }
        tab.title = buildTabTitle({
            teamId: tab.teamId,
            teamName: tab.teamName,
            projectId: tab.activeProjectId,
            projectName: tab.projectName,
        });
    }

    /**
     * 更新当前激活标签页的路由快照
     */
    function updateActiveRoute(route: WorkspaceTab['savedRoute']): void {
        const tab = activeTab.value;
        if (!tab) return;
        tab.savedRoute = route;
    }

    /**
     * 移除指定团队的所有标签页
     */
    function removeTabsByTeam(teamId: number): void {
        tabs.value = tabs.value.filter((t) => t.teamId !== teamId);
        if (activeTabId.value && !tabs.value.some((t) => t.id === activeTabId.value)) {
            activeTabId.value = tabs.value[0]?.id ?? null;
        }
    }

    /**
     * 清空所有工作区
     */
    function clearTabs(): void {
        tabs.value = [];
        activeTabId.value = null;
    }

    /**
     * 查找指定团队的标签页
     */
    function findTabByTeam(teamId: number): WorkspaceTab | undefined {
        return tabs.value.find((t) => t.teamId === teamId);
    }

    return {
        tabs,
        activeTabId,
        activeTab,
        activeTeamId,
        activeProjectId,
        hasTabs,
        addTab,
        switchTab,
        closeTab,
        updateActiveProject,
        updateActiveRoute,
        removeTabsByTeam,
        clearTabs,
        findTabByTeam,
    };
}, {
    persist: true,
});

export default useWorkspaceStore;
