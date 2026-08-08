export class Element {
    id?: number;      // 元素ID
    parentId?: number;      // 父id
    elementName?: string = '';      // 元素名称
    elementType?: string = '';      // 类型
    locatorType?: string = undefined;      // 定位类型
    locatorValue?: string = '';      // 定位值
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

