/**
 * SQL 断言配置
 * 对 SQL 执行结果进行校验，确认指定行指定列的值满足预期条件。
 *
 * 示例：断言第 1 行 id 列的值等于 100
 *   { columnName: "id", condition: "EQUALS", expectedValue: "100", rowIndex: 0 }
 */
export class SqlAssertion {
    /** SQL 结果列名 */
    columnName?: string;
    /** JSON 路径（选填）：列值为 JSON 时按 JSONPath 取嵌套值后再比较，如 $.data.id；为空取整列值 */
    jsonPath?: string;
    /** 断言条件：EQUALS / NOT_EQUALS / CONTAINS / NOT_CONTAINS / GT / LT / GE / LE / REGULAR */
    condition?: string;
    /** 期望值 */
    expectedValue?: string;
    /** 行下标，0 = 第一行 */
    rowIndex?: number;
    /** 是否禁用 */
    disabled?: boolean;
    /** 描述 */
    description?: string;
}
