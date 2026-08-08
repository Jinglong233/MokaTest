/**
 * 工作区标签页模型
 */
export interface WorkspaceTab {
    /**
     * 标签唯一标识
     */
    id: string;

    /**
     * 所属团队ID
     */
    teamId: number;

    /**
     * 当前选中的项目ID
     */
    activeProjectId: number;

    /**
     * 标签标题，例如：团队名·项目名
     */
    title: string;

    /**
     * 团队名称（缓存，避免标题变化时频繁查询）
     */
    teamName?: string;

    /**
     * 项目名称（缓存）
     */
    projectName?: string;

    /**
     * 内部路由快照，用于恢复工作区状态
     */
    savedRoute?: {
        name?: string;
        path?: string;
        query?: Record<string, any>;
    };
}

/**
 * 打开工作区时所需的来源信息
 */
export interface WorkspaceSource {
    teamId: number;
    teamName?: string;
    projectId: number;
    projectName?: string;
    route?: WorkspaceTab['savedRoute'];
}
