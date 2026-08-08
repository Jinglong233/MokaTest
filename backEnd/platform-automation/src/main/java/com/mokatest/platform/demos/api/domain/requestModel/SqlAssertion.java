package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

/**
 * SQL 断言配置
 * 对 SQL 执行结果进行校验，确认指定行指定列的值满足预期条件。
 *
 * @author JingLong
 * @since 2026-07-28
 */
@Data
public class SqlAssertion {
    /** SQL 结果列名 */
    private String columnName;
    /**
     * JSON 路径（选填）：当列值为 JSON 字符串/JSON 列时，按 JSONPath 取嵌套值后再比较。
     * 语法与 HTTP 接口提取一致，如 $.data.id。为空则取整列值。
     */
    private String jsonPath;
    /** 断言条件：EQUALS / NOT_EQUALS / CONTAINS / NOT_CONTAINS / GT / LT / GE / LE / REGULAR */
    private String condition;
    /** 期望值 */
    private String expectedValue;
    /** 行下标，0 = 第一行 */
    private Integer rowIndex;
    /** 是否禁用 */
    private boolean disabled = false;
    /** 描述 */
    private String description;
}
