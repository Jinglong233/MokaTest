import {MockFieldRuleParams} from "@/types/domain/api/requestModel/MockFieldRuleParams";

/**
 * Mock 字段规则
 *
 * 支持树状结构：根节点 fieldType = OBJECT，子节点支持嵌套对象/数组。
 */
export class MockFieldRule {
    fieldName?: string;
    description?: string;
    fieldType?: 'STRING' | 'INT' | 'LONG' | 'FLOAT' | 'DOUBLE' | 'BOOLEAN' | 'OBJECT' | 'ARRAY' | 'TEMPLATE';

    // schema 标记
    nullable?: boolean;
    required?: boolean;
    /** @deprecated 后端不再读取，保留仅用于兼容旧数据；枚举请使用 ruleType='choice' */
    isEnum?: boolean;
    isConstant?: boolean;

    // 生成规则
    ruleType?: string;
    /** @deprecated 参数已上移到顶层字段，保留仅用于兼容旧数据 */
    params?: MockFieldRuleParams;
    fixedValue?: string;
    /** @deprecated 已停写死，保留仅用于兼容旧数据；数组长度请使用 minItems/maxItems */
    arrayLength?: number;
    /** @deprecated 已停写死，保留仅用于兼容旧数据 */
    arrayElementType?: 'STRING' | 'INT' | 'LONG' | 'FLOAT' | 'DOUBLE' | 'BOOLEAN';
    /** 数组最小元素个数 */
    minItems?: number;
    /** 数组最大元素个数 */
    maxItems?: number;
    /** 数组元素是否唯一 */
    uniqueItems?: boolean;
    templateId?: number;
    /** 隐藏字段名列表（仅 fieldType=TEMPLATE 时有效）：从模板中剔除的字段名 */
    excludedFields?: string[];

    // 高级约束
    format?: string;
    minLength?: number;
    maxLength?: number;
    min?: number;
    max?: number;
    scale?: number;
    pattern?: string;
    defaultValue?: string;
    choices?: string;
    charset?: string;
    length?: number;
    locale?: string;
    /** 字符类型：lower / upper / number / mixed（character 规则用） */
    caseType?: string;

    // 树结构
    children?: MockFieldRule[];

    /** 前端稳定 key，用于列表渲染 */
    _key?: string;

    constructor(isRoot = false) {
        this.fieldType = isRoot ? 'OBJECT' : 'STRING';
        this.nullable = false;
        this.required = true;
        this.isEnum = false;
        this.isConstant = false;
        this.ruleType = isRoot ? undefined : 'name';
        this.locale = 'zh';
        this.charset = 'abcdefghijklmnopqrstuvwxyz0123456789';
        this.min = 0;
        this.max = 100;
        this.scale = 2;
        this.length = 10;
        this.minLength = 0;
        this.maxLength = 1000;
        this.format = 'yyyy-MM-dd HH:mm:ss';
        this.choices = 'A,B,C';
        this.caseType = 'lower';
        this.children = [];
        this._key = MockFieldRule.generateKey();
    }

    static generateKey(): string {
        return `rule_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`;
    }
}
