export interface StepExtractor {
    // 所属步骤ID
    stepId?: string,

    // 提取类型
    extractType?: string,

    // 引用的元素ID
    elementId?: string,

    // 自定义元素定位类型
    elementType?: string,

    // 自定义元素定位值
    elementValue?: string,

    // 源属性名（如header名、cookie名、JSON路径等）
    sourceProperty?: string,

    // 目标变量名
    targetVariable?: string,

    // cookie名称
    cookieName?: string;

    // 提取值类型
    extractValueType?: string,

    // 属性名（当提取值类型为attribute时使用）
    attributeName?: string,

    // 提取顺序
    orderIndex?: number,

    //
    createdAt?: number,

    // 更新人ID
    updateUserId?: string,

    // 提取描述
    description?: string,

}