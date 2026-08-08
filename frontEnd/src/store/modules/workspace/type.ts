import {WorkspaceTab} from '@/types/domain/workspace';

export interface WorkspaceState {
    /**
     * 工作区标签页列表
     */
    tabs: WorkspaceTab[];

    /**
     * 当前激活的标签页ID
     */
    activeTabId: string | null;
}
