/**
 * 自定义公共函数
 *
 * 用户用 JS 编写项目级公共函数，在参数值/Mock/脚本中通过
 * {{__CUSTOM(id, args)__}} / @custom(id, args) / context.utils.custom(id, ...) 调用。
 */
export interface CustomFunction {
    id?: number;
    teamId?: number | string;
    projectId?: number | string;
    /** 函数名称（展示用） */
    funcName?: string;
    /** 参数名定义（逗号分隔，如 text,key） */
    funcParams?: string;
    /** JS 函数体（return 出结果） */
    funcCode?: string;
    /** 描述 */
    description?: string;
    /** 排序 */
    sort?: number;
    createUserId?: number;
    updateUserId?: number;
    createTime?: string;
    updateTime?: string;
}
