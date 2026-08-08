/**
 * Mock 字段规则参数
 *
 * @deprecated 参数已上移到 MockFieldRule 顶层字段，保留仅用于兼容旧数据。
 */
export class MockFieldRuleParams {
    locale?: string;
    min?: number;
    max?: number;
    scale?: number;
    length?: number;
    format?: string;
    choices?: string;

    constructor() {
        this.locale = 'zh';
        this.min = 0;
        this.max = 100;
        this.scale = 2;
        this.length = 10;
        this.format = 'yyyy-MM-dd HH:mm:ss';
        this.choices = 'A,B,C';
    }
}
