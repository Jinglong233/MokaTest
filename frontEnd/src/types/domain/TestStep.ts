export class TestStep {
    // 步骤ID
    id?: number;
    // 步骤类型
    stepType?: string;
    // 步骤名称
    stepName: string = '';
    // 步骤描述
    description?: string;
    // 父步骤ID
    parentId?: string;
    // 执行顺序
    orderIndex?: number;
    // 所属项目ID
    projectId: string = '';
    // 所属场景ID
    scenarioId: string = '';


    // 是否禁用
    isDisable?: number = 0;


    // 步骤详情
    stepDetail: Object = {};
    //
    createdAt?: number;
    //
    updatedAt?: number;
    // 创建人ID
    createUserId?: string;

    // 更新人ID
    updateUserId?: string;
}