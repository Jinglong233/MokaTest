package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

/**
 * SQL 数据提取配置
 * 从 SQL 查询结果中提取指定行指定列的值，存入变量池供后续步骤使用。
 *
 * @author JingLong
 * @since 2026-07-28
 */
@Data
public class SqlExtraction {
    /** 变量名 */
    private String variableName;
    /** SQL 结果列名 */
    private String columnName;
    /**
     * JSON 路径（选填）：当列值为 JSON 字符串/JSON 列时，按 JSONPath 继续向下提取嵌套值。
     * 语法与 HTTP 接口提取一致，如 $.data.id。为空则取整列值。
     */
    private String jsonPath;
    /** 行下标，0 = 第一行 */
    private Integer rowIndex;
    /** 默认值 */
    private String defaultValue;
    /** 是否禁用 */
    private boolean disabled = false;
    /** 描述 */
    private String description;
}
