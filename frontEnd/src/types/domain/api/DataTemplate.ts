import {MockFieldRule} from "@/types/domain/api/requestModel/MockFieldRule";

/**
 * 数据模板（单表化：FOLDER / TEMPLATE 共用）
 */
export class DataTemplate {
    id?: number;
    teamId?: number;
    projectId?: number;
    /** 父节点ID，0=根节点 */
    parentId?: number;
    /** 节点类型：FOLDER-文件夹，TEMPLATE-模板 */
    nodeType?: 'FOLDER' | 'TEMPLATE';
    /** 名称（模板名/文件夹名） */
    templateName?: string;
    description?: string;
    /** 字段规则根节点（仅 TEMPLATE） */
    templateSchema?: MockFieldRule;
    /** 是否共享：1-共享，0-私有（仅 TEMPLATE） */
    isShared?: number;
    /** 继承的父模板ID（仅 TEMPLATE，可选） */
    extendsId?: number;
    /** 同级排序 */
    sort?: number;
    createUserId?: number;
    updateUserId?: number;
    createTime?: Date;
    updateTime?: Date;
}
