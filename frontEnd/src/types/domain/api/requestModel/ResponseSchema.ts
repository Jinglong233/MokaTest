import {MockFieldRule} from "@/types/domain/api/requestModel/MockFieldRule";

/**
 * 响应定义（对标 Apifox 数据模型）
 *
 * mode = NONE：不定义响应结构
 * mode = TEMPLATE：绑定数据模板，可隐藏/覆盖部分字段
 * mode = INLINE：内联定义响应结构
 */
export class ResponseSchema {
    /** 定义模式：NONE-不定义，TEMPLATE-绑定数据模板，INLINE-内联定义 */
    mode?: 'NONE' | 'TEMPLATE' | 'INLINE';

    /** 绑定的数据模板ID（mode=TEMPLATE 时有效） */
    templateId?: number;

    /** 内联响应结构（mode=INLINE 时有效，根节点 fieldType=OBJECT） */
    schema?: MockFieldRule;

    /** 隐藏字段名列表（mode=TEMPLATE 时有效）：从模板中剔除的字段 */
    hiddenFields?: string[];

    /** 覆盖字段列表（mode=TEMPLATE 时有效）：覆盖模板中同名字段 */
    overrideFields?: MockFieldRule[];

    /** 执行时是否校验响应结构 */
    validateEnabled?: boolean;

    constructor() {
        this.mode = 'NONE';
        this.templateId = undefined;
        this.schema = undefined;
        this.hiddenFields = [];
        this.overrideFields = [];
        this.validateEnabled = true;
    }
}
