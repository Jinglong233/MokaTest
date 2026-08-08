package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

import java.util.List;

/**
 * SQL 调试配置
 * 存储于 api_request.sql_config 或场景步骤 step_detail 中
 *
 * @author JingLong
 * @since 2026-07-28
 */
@Data
public class SqlConfig {
    /** SQL 语句，支持 ${var} / {{var}} 变量替换 */
    private String sql;
    /** 引用环境级数据库连接名（environment.dbs[].name） */
    private String dbConnectionName;
    /** 步骤级数据库连接覆盖（逐字段 merge，优先级高于环境级） */
    private DataBaseParameter dbConfig;
    /** 查询超时秒数，默认 30 */
    private Integer timeout = 30;
    /** 最大返回行数，默认 1000，超限截断 */
    private Integer maxRows = 1000;
    /** 可选参数绑定（预留） */
    private List<SqlParam> params;
    /** SQL 结果提取规则 */
    private List<SqlExtraction> sqlExtractions;
    /** SQL 断言规则 */
    private List<SqlAssertion> sqlAssertions;
}
