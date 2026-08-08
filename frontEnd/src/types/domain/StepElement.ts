export interface StepElement {

    // 关联ID
    id?: string,
    // 步骤ID
    stepId?: string,
    // 引用的元素ID(可为空)
    elementId?: string,
    // 自定义定位类型(当element_id为空时使用)
    elementType?: string,
    // 自定义定位值(当element_id为空时使用)
    elementValue?: string,
    // 是否自定义(1-自定义，0-引用)
    isCustom?: number,
    // 元素顺序
    orderIndex?: number,
    //
    createdAt?: number,
}