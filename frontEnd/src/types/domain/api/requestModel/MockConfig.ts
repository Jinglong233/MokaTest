/**
 * 参数级 Mock 结构化配置
 */
export class MockConfig {
    /** 规则类型：name/phone/email/uuid/idCard/company/address/int/float/date/text/choice/fixed/template/character/cname/ename/datetime/time/boolean/timestamp/bankcard */
    type?: string;
    /** 语言/地区：zh / en */
    locale?: string;
    /** 字符类型：lower / upper / number / mixed，用于 character */
    caseType?: string;
    /** 最小值 */
    min?: number;
    /** 最大值 */
    max?: number;
    /** 小数位数 */
    scale?: number;
    /** 字符串长度 */
    length?: number;
    /** 日期格式 */
    format?: string;
    /** 枚举选项，英文逗号分隔 */
    choices?: string;
    /** 固定值 */
    fixedValue?: string;
    /** 数据模板 ID */
    templateId?: number;
}
