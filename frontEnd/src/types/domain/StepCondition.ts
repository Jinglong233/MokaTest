// IStepCondition
export interface StepCondition {
    // 断言ID
    id?: string,
    // 父ID
    parentId?: string,
    // 关联步骤ID
    stepId?: string,
    // 断言名称
    name?: string,
    // 条件关系
    conditionRelationship?: string,
    // 断言描述
    description?: string,
    //
    createdAt?: number,
    //
    updatedAt?: number,
}