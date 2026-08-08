/**
 * SQL 数据提取配置
 * 从 SQL 查询结果中提取指定行指定列的值，存入变量池供后续步骤使用。
 *
 * 示例：提取 SQL 结果第 1 行 id 列的值
 *   { variableName: "userId", columnName: "id", rowIndex: 0 }
 */
export class SqlExtraction {
    /** 变量名，提取成功后存入变量池的 key */
    variableName?: string;
    /** SQL 结果列名 */
    columnName?: string;
    /** JSON 路径（选填）：列值为 JSON 时按 JSONPath 继续向下提取，如 $.data.id；为空取整列值 */
    jsonPath?: string;
    /** 行下标，0 = 第一行 */
    rowIndex?: number;
    /** 默认值，提取失败时使用 */
    defaultValue?: string;
    /** 是否禁用 */
    disabled?: boolean;
    /** 描述 */
    description?: string;
}
