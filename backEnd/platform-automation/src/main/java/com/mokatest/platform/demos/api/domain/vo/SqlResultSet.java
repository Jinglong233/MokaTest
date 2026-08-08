package com.mokatest.platform.demos.api.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SQL 查询结果集
 *
 * @author JingLong
 * @since 2026-07-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlResultSet {
    /** 列名列表（保持查询顺序） */
    private List<String> columns;
    /** 数据行，每行是列名→值的 Map */
    private List<Map<String, Object>> rows;
    /** 实际返回行数 */
    private int rowCount;
    /** 执行耗时（毫秒） */
    private long elapsedMs;
    /** 是否超过 maxRows 被截断 */
    private boolean truncated;
}
