// 定位候选项：有序数组，第一项为主定位（与 locatorType/locatorValue 同步）
export interface LocatorChainItem {
    type: string;
    value: string;
    score?: number;
}

export class Element {
    id?: number;      // 元素ID
    parentId?: number;      // 父id
    elementName?: string = '';      // 元素名称
    elementType?: string = '';      // 类型
    locatorType?: string = undefined;      // 定位类型（主定位 = 定位链第一项）
    locatorValue?: string = '';      // 定位值（主定位 = 定位链第一项）
    locatorChain?: LocatorChainItem[];      // 定位候选链（有序），为空时由主定位合成
    sort?: number = 0;      // 排序
    description?: string = '';      // 元素描述
    projectId?: string = '';      // 所属项目ID
    createdAt?: Date;      //
    updatedAt?: Date;      //
    createUserId?: string = '';      // 创建人ID
    updateUserId?: string = '';      // 更新人ID
    isShared?: number = 0;      // 是否共享元素(1-共享，0-私有)
    isDeleted?: number = 0;      // 是否已删除：0-未删除，1-已删除
    deletedAt?: Date;      // 删除时间
}

