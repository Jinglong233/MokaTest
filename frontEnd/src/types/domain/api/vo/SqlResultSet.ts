/**
 * SQL 查询结果集
 */
export class SqlResultSet {
    /** 列名列表（保持查询顺序） */
    columns?: string[];
    /** 数据行，每行是列名→值的 Map */
    rows?: Record<string, any>[];
    /** 实际返回行数 */
    rowCount?: number;
    /** 执行耗时（毫秒） */
    elapsedMs?: number;
    /** 是否超过 maxRows 被截断 */
    truncated?: boolean;
}
