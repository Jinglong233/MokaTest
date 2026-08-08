/**
 * 数据模板树节点（文件夹 + 模板混合树）
 */
export interface DataTemplateTreeNode {
    id: number;
    name: string;
    parentId: number;
    nodeType: 'FOLDER' | 'TEMPLATE';
    description?: string;
    isShared?: number;
    updateTime?: Date;
    sort?: number;
    /** 文件夹下包含的模板数量（含子文件夹） */
    templateCount?: number;
    children?: DataTemplateTreeNode[];
}
