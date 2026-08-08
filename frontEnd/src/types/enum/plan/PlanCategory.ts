export enum PlanCategory {
    UI = 'UI',
    API = 'API',
    MIXED = 'MIXED'
}

export const PlanCategoryDesc: Record<string, string> = {
    [PlanCategory.UI]: 'UI 计划',
    [PlanCategory.API]: 'API 计划',
    [PlanCategory.MIXED]: '混合计划'
};
