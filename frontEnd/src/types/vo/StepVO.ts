export interface StepVO {
// 步骤ID
    id?: number,
    // 步骤类型
    stepType?: string,
    // 步骤名称
    stepName?: string,
    // 步骤描述
    description?: string,
    // 父步骤ID
    parentId?: number,
    // 执行顺序
    orderIndex: number,
    // 所属项目ID
    projectId?: string,
    // 所属场景ID
    scenarioId?: string,
    // 关联元素ID
    elementId?: string,
    // 自定义元素类型
    customElementType?: string,
    // 自定义元素值
    customElementValue?: string,
    // 子节点
    children?: StepVO[]
}
