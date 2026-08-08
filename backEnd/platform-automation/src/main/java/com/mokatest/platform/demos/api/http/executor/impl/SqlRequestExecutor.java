package com.mokatest.platform.demos.api.http.executor.impl;

import com.alibaba.fastjson.JSON;
import com.jayway.jsonpath.JsonPath;
import com.mokatest.platform.demos.api.domain.ApiRequest;
import com.mokatest.platform.demos.api.domain.Environment;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiType;
import com.mokatest.platform.demos.api.domain.requestModel.DataBaseParameter;
import com.mokatest.platform.demos.api.domain.requestModel.SqlConfig;
import com.mokatest.platform.demos.api.domain.requestModel.SqlAssertion;
import com.mokatest.platform.demos.api.domain.requestModel.SqlExtraction;
import com.mokatest.platform.demos.result.AssertResult;
import com.mokatest.platform.demos.api.http.executor.RequestExecutor;
import com.mokatest.platform.demos.api.http.model.TestHttpResponse;
import com.mokatest.platform.demos.api.http.model.VariableTrack;
import com.mokatest.platform.demos.api.script.ScriptExecutor;
import com.mokatest.platform.demos.api.script.ScriptRequest;
import com.mokatest.platform.demos.api.script.ScriptResponse;
import com.mokatest.platform.demos.api.service.ConnectionPoolManager;
import com.mokatest.platform.demos.api.service.EnvironmentService;
import com.mokatest.platform.demos.util.VariableReplacer;
import com.mokatest.platform.demos.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * SQL 请求执行器
 * <p>
 * 实现 RequestExecutor 接口，通过 JDBC 执行 SQL 查询并返回结构化结果。
 * 复用平台的变量替换、脚本执行、提取、断言等基础设施。
 * <p>
 * 安全约束：
 * - 查询：SELECT / SHOW / DESCRIBE / DESC / EXPLAIN / WITH / ANALYZE
 * - DML：INSERT / UPDATE / DELETE
 * - DDL：CREATE / ALTER / DROP / TRUNCATE（高危操作，前端执行按钮做警告样式）
 * - 拦截：GRANT / REVOKE / EXECUTE / CALL / LOCK / UNLOCK / KILL / SHUTDOWN 等
 *
 * @author JingLong
 * @since 2026-07-28
 */
@Slf4j
@Component
public class SqlRequestExecutor implements RequestExecutor {

    @Resource
    private ConnectionPoolManager connectionPoolManager;

    @Resource
    private EnvironmentService environmentService;

    /** 允许的 SQL 语句类型（查询 + DML + DDL） */
    private static final Pattern QUERY_SQL_PATTERN = Pattern.compile(
            "^(SELECT|SHOW|DESCRIBE|DESC|EXPLAIN|WITH|ANALYZE)\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DML_SQL_PATTERN = Pattern.compile(
            "^(INSERT|UPDATE|DELETE)\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DDL_SQL_PATTERN = Pattern.compile(
            "^(CREATE|ALTER|DROP|TRUNCATE)\\b.*",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 危险 SQL 关键字（额外校验层—DDL 已单独放行，此处仅拦截高危操作） */
    private static final Pattern DANGEROUS_SQL_PATTERN = Pattern.compile(
            "\\b(GRANT|REVOKE|REPLACE|MERGE|EXEC|EXECUTE|CALL|LOCK|UNLOCK|KILL|SHUTDOWN)\\b",
            Pattern.CASE_INSENSITIVE);

    /** 单次执行最大返回行数（超出部分截断） */
    private static final int MAX_TOTAL_ROWS = 5000;

    @Override
    public TestHttpResponse execute(ApiRequest request) {
        long startTime = System.currentTimeMillis();
        String uuid = UUID.randomUUID().toString();
        List<String> consoleLogs = new ArrayList<>();
        List<com.mokatest.platform.demos.api.script.ScriptContext.ScriptAssertion> scriptAssertions = new ArrayList<>();

        // ===== 1. 前置脚本 =====
        Map<String, Object> variables = buildVariableContext(request);
        if (request.getPreScript() != null && !request.getPreScript().isEmpty()) {
            try {
                ScriptRequest scriptRequest = ScriptRequest.builder().build();
                ScriptExecutor.ScriptResult preResult = ScriptExecutor.executePreScripts(
                        request.getPreScript(), variables, scriptRequest, request.getProjectId());
                consoleLogs.addAll(preResult.getConsoleLogs());
                scriptAssertions.addAll(preResult.getScriptAssertions());
                if (preResult.isSuccess()) {
                    variables.putAll(preResult.getVariables());
                }
            } catch (Exception e) {
                log.warn("[SQL执行] 前置脚本执行异常: {}", e.getMessage());
                consoleLogs.add("[ERROR] 前置脚本执行异常: " + e.getMessage());
            }
        }

        // ===== 2. 解析数据库连接 =====
        SqlConfig sqlConfig = request.getSqlConfig();
        if (sqlConfig == null) {
            return buildErrorResponse(request, uuid, startTime, "SQL 配置为空", consoleLogs, scriptAssertions);
        }

        String sql = sqlConfig.getSql();
        if (sql == null || sql.trim().isEmpty()) {
            return buildErrorResponse(request, uuid, startTime, "SQL 语句为空", consoleLogs, scriptAssertions);
        }

        // ===== 3. SQL 安全检查 =====
        String trimmedSql = sql.trim();
        boolean isQuery = QUERY_SQL_PATTERN.matcher(trimmedSql).matches();
        boolean isDml = DML_SQL_PATTERN.matcher(trimmedSql).matches();
        boolean isDdl = DDL_SQL_PATTERN.matcher(trimmedSql).matches();
        if (!isQuery && !isDml && !isDdl) {
            return buildErrorResponse(request, uuid, startTime,
                    "仅允许执行 SELECT / SHOW / DESCRIBE / EXPLAIN / INSERT / UPDATE / DELETE / CREATE / ALTER / DROP / TRUNCATE 语句",
                    consoleLogs, scriptAssertions);
        }
        if (DANGEROUS_SQL_PATTERN.matcher(trimmedSql).find()) {
            return buildErrorResponse(request, uuid, startTime,
                    "SQL 包含危险关键字 (GRANT/REVOKE/EXECUTE/LOCK/KILL/SHUTDOWN 等)，已拦截",
                    consoleLogs, scriptAssertions);
        }

        // ===== 4. 变量替换 =====
        // 无条件调用：变量为空时 replace 内部仍会解析 @mock/{{__函数__}} 表达式
        VariableTrack variableTrack = VariableTrack.builder().build();
        sql = VariableReplacer.replace(sql, variables);

        // ===== 4.5 单语句约束 + 变量替换后复检 =====
        // 一个 SQL 接口只允许执行一条语句（保证原子性），拆分器能识别引号与注释中的分号
        List<String> stmtList = splitSqlStatements(sql);
        if (stmtList.size() > 1) {
            return buildErrorResponse(request, uuid, startTime,
                    "SQL 接口仅支持单条语句执行，当前检测到 " + stmtList.size() + " 条，请拆分后逐个调试",
                    consoleLogs, scriptAssertions);
        }
        // 变量替换后重新校验语句类型与危险关键字，防止注入非法语句
        String finalSql = stmtList.isEmpty() ? sql.trim() : stmtList.get(0);
        if (!QUERY_SQL_PATTERN.matcher(finalSql).matches()
                && !DML_SQL_PATTERN.matcher(finalSql).matches()
                && !DDL_SQL_PATTERN.matcher(finalSql).matches()) {
            return buildErrorResponse(request, uuid, startTime,
                    "变量替换后 SQL 语句类型非法: " + truncate(finalSql, 60), consoleLogs, scriptAssertions);
        }
        if (DANGEROUS_SQL_PATTERN.matcher(finalSql).find()) {
            return buildErrorResponse(request, uuid, startTime,
                    "变量替换后 SQL 包含危险关键字: " + truncate(finalSql, 60), consoleLogs, scriptAssertions);
        }
        sql = finalSql;

        // ===== 5. 解析数据库连接参数 =====
        DataBaseParameter dbParam;
        try {
            dbParam = resolveDbConnection(request, sqlConfig);
        } catch (BusinessException e) {
            return buildErrorResponse(request, uuid, startTime, e.getMessage(), consoleLogs, scriptAssertions, null, sql);
        }

        // ===== 6. 执行 SQL（单语句，保证原子性） =====
        String jdbcUrl = "jdbc://" + dbParam.getIp() + ":" + dbParam.getPort() + "/" + dbParam.getDbName();

        // 连接池创建/获取失败单独兜底（地址不通、驱动缺失、认证失败等运行期异常）
        DataSource ds;
        try {
            ds = connectionPoolManager.getOrCreate(dbParam, request.getProjectId());
        } catch (Exception e) {
            log.warn("[SQL执行] 创建数据库连接失败 {}: {}", jdbcUrl, e.getMessage());
            return buildErrorResponse(request, uuid, startTime,
                    "无法建立数据库连接（" + jdbcUrl + "）：" + rootCauseMessage(e)
                            + "，请检查数据库地址、端口、账号密码及网络连通性",
                    consoleLogs, scriptAssertions, jdbcUrl, sql);
        }

        String responseBody;
        long sqlElapsedMs;
        Map<String, String> responseHeaders = new LinkedHashMap<>();
        responseHeaders.put("Content-Type", "application/json; charset=utf-8");

        int timeout = sqlConfig.getTimeout() != null ? sqlConfig.getTimeout() : 30;
        int maxRows = sqlConfig.getMaxRows() != null ? sqlConfig.getMaxRows() : 1000;

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(timeout);
            // 多取一行用于判断是否截断
            stmt.setMaxRows(maxRows + 1);

            long execStart = System.currentTimeMillis();
            boolean hasResultSet = stmt.execute(sql);

            if (hasResultSet) {
                List<String> columns;
                List<Map<String, Object>> rows = new ArrayList<>();
                boolean truncated = false;
                try (ResultSet rs = stmt.getResultSet()) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    columns = new ArrayList<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }
                    while (rs.next()) {
                        if (rows.size() >= maxRows || rows.size() >= MAX_TOTAL_ROWS) {
                            truncated = true;
                            break;
                        }
                        Map<String, Object> row = new LinkedHashMap<>(colCount);
                        for (int i = 1; i <= colCount; i++) {
                            row.put(columns.get(i - 1), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
                sqlElapsedMs = System.currentTimeMillis() - execStart;
                responseBody = JSON.toJSONString(rows);
                responseHeaders.put("X-Sql-Row-Count", String.valueOf(rows.size()));
                if (truncated) {
                    responseHeaders.put("X-Sql-Truncated", "true");
                }
            } else {
                int affectedRows = Math.max(stmt.getUpdateCount(), 0);
                sqlElapsedMs = System.currentTimeMillis() - execStart;
                responseBody = JSON.toJSONString(Map.of("affectedRows", affectedRows));
                responseHeaders.put("X-Sql-Affected-Rows", String.valueOf(affectedRows));
            }
            responseHeaders.put("X-Sql-Elapsed-Ms", String.valueOf(sqlElapsedMs));

            log.info("[SQL执行] 单语句执行完成, 耗时 {}ms", sqlElapsedMs);

        } catch (SQLException e) {
            String friendly = friendlySqlError(e, timeout);
            log.warn("[SQL执行] 失败 [{}]: {}", friendly, e.getMessage());
            return buildErrorResponse(request, uuid, startTime, friendly,
                    consoleLogs, scriptAssertions, jdbcUrl, sql);
        } catch (Exception e) {
            log.error("[SQL执行] 未预期异常", e);
            return buildErrorResponse(request, uuid, startTime,
                    "SQL 执行异常：" + rootCauseMessage(e),
                    consoleLogs, scriptAssertions, jdbcUrl, sql);
        }

        // ===== 6.5 SQL 结果提取 =====
        Map<String, Object> extractedVars = processSqlExtractions(sqlConfig, responseBody, variables);
        if (!extractedVars.isEmpty()) {
            variableTrack.setExtractedVariables(new LinkedHashMap<>(extractedVars));
        }

        // ===== 6.6 SQL 断言 =====
        List<AssertResult> sqlAssertionResults = processSqlAssertions(sqlConfig, responseBody, variables);

        // ===== 7. 后置脚本 =====
        if (request.getPostScript() != null && !request.getPostScript().isEmpty()) {
            try {
                ScriptResponse scriptResponse = ScriptResponse.builder()
                        .statusCode(200)
                        .body(responseBody)
                        .responseTimeMs(sqlElapsedMs)
                        .build();
                ScriptExecutor.ScriptResult postResult = ScriptExecutor.executePostScripts(
                        request.getPostScript(), variables, scriptResponse, request.getProjectId());
                consoleLogs.addAll(postResult.getConsoleLogs());
                scriptAssertions.addAll(postResult.getScriptAssertions());
            } catch (Exception e) {
                log.warn("[SQL执行] 后置脚本执行异常: {}", e.getMessage());
                consoleLogs.add("[ERROR] 后置脚本执行异常: " + e.getMessage());
            }
        }

        // ===== 8. 构建 TestHttpResponse =====
        long endTime = System.currentTimeMillis();

        return TestHttpResponse.builder()
                .uuid(uuid)
                .status("success")
                .statusCode(200)
                .responseStatusMsg("OK")
                .requestUrl("jdbc://" + dbParam.getIp() + ":" + dbParam.getPort() + "/" + dbParam.getDbName())
                .requestMethod("SQL")
                .requestHeaders(Map.of("X-Sql-Statement", sql))
                .requestBody(sql.getBytes(StandardCharsets.UTF_8))
                .responseHeaders(responseHeaders)
                .rawBody(responseBody.getBytes(StandardCharsets.UTF_8))
                .responseTimeMs(endTime - startTime)
                .responseBytes(responseBody.getBytes(StandardCharsets.UTF_8).length)
                .requestBytes(sql.getBytes(StandardCharsets.UTF_8).length)
                .requestStartTime(startTime)
                .requestEndTime(endTime)
                .scriptConsoleLog(consoleLogs)
                .scriptAssertions(scriptAssertions)
                .variableTrack(variableTrack)
                .extractedVariables(extractedVars)
                .assertionResults(sqlAssertionResults)
                .build();
    }

    /**
     * 解析数据库连接：步骤内联覆盖环境级
     */
    private DataBaseParameter resolveDbConnection(ApiRequest request, SqlConfig sqlConfig) {
        // 1. 查找环境级配置
        DataBaseParameter envConfig = null;
        if (request.getEnvInfo() != null && request.getEnvInfo().getEnvId() != null) {
            Environment env = environmentService.getById(request.getEnvInfo().getEnvId());
            if (env != null && env.getDbs() != null && sqlConfig.getDbConnectionName() != null) {
                envConfig = env.getDbs().stream()
                        .filter(db -> sqlConfig.getDbConnectionName().equals(db.getName()))
                        .findFirst()
                        .orElse(null);
            }
        }

        // 2. 如果环境级找不到且没有步骤覆盖，尝试用 SqlConfig.dbConfig 作为独立配置
        if (envConfig == null && sqlConfig.getDbConfig() == null) {
            throw new BusinessException("未找到数据库连接配置: " + sqlConfig.getDbConnectionName()
                    + "，请检查环境配置或步骤内联配置");
        }

        // 3. 合并：步骤内联覆盖环境级
        DataBaseParameter resolved = connectionPoolManager.mergeDbConfig(envConfig, sqlConfig.getDbConfig());
        if (resolved == null || resolved.getIp() == null) {
            throw new BusinessException("数据库连接信息不完整（缺少 IP 地址）");
        }
        return resolved;
    }

    /**
     * 按分号拆分 SQL 语句（识别单/双引号、反引号、行注释与块注释，字符串内的分号不计）。
     * 仅用于"是否超过一条语句"的判定与取首条语句，非完整 SQL 解析。
     */
    private List<String> splitSqlStatements(String sql) {
        List<String> list = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) return list;

        StringBuilder current = new StringBuilder();
        int len = sql.length();
        char quote = 0;          // 当前引号：' " `，0 表示不在引号内
        boolean inLineComment = false;
        boolean inBlockComment = false;

        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            char next = i + 1 < len ? sql.charAt(i + 1) : '\0';

            if (inLineComment) {
                current.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                current.append(c);
                if (c == '*' && next == '/') { current.append(next); i++; inBlockComment = false; }
                continue;
            }
            if (quote != 0) {
                current.append(c);
                if (c == '\\' && quote != '`' && next != '\0') { current.append(next); i++; continue; }
                if (c == quote) quote = 0;
                continue;
            }
            // 注释起始
            if ((c == '-' && next == '-' ) || c == '#') { inLineComment = true; current.append(c); continue; }
            if (c == '/' && next == '*') { inBlockComment = true; current.append(c).append(next); i++; continue; }
            // 引号起始
            if (c == '\'' || c == '"' || c == '`') { quote = c; current.append(c); continue; }
            // 语句分隔
            if (c == ';') {
                String trimmed = current.toString().trim();
                if (!trimmed.isEmpty()) list.add(trimmed);
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        String trimmed = current.toString().trim();
        if (!trimmed.isEmpty()) list.add(trimmed);
        return list;
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    /**
     * 从单语句查询结果（行数组 JSON）解析行数据
     */
    private List<Map<String, Object>> normalizeToRows(String responseBody) {
        if (responseBody == null) return null;
        String trimmed = responseBody.trim();
        if (!trimmed.startsWith("[")) return null;
        try {
            return JSON.parseObject(trimmed, new com.alibaba.fastjson.TypeReference<List<Map<String, Object>>>(){});
        } catch (Exception e) {
            return null;
        }
    }

    /** 从单语句 DML/DDL 结果（{"affectedRows": n}）解析影响行数 */
    private int normalizeToAffectedRows(String responseBody) {
        if (responseBody == null) return -1;
        String trimmed = responseBody.trim();
        if (!trimmed.startsWith("{")) return -1;
        try {
            com.alibaba.fastjson.JSONObject obj = JSON.parseObject(trimmed);
            return obj.containsKey("affectedRows") ? obj.getIntValue("affectedRows") : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 解析单元格值：jsonPath 为空时返回整列值；
     * 否则把列值按 JSON 解析并按 JSONPath 向下提取嵌套值（与 HTTP 提取语法一致）。
     * 列值非合法 JSON 或路径不存在时返回 null（按未命中处理）。
     */
    private String resolveCellValue(Object cellValue, String jsonPath) {
        if (cellValue == null) return null;
        String str = cellValue.toString();
        if (jsonPath == null || jsonPath.trim().isEmpty()) return str;
        try {
            Object v = JsonPath.read(str, jsonPath.trim());
            if (v == null) return null;
            return (v instanceof String) ? (String) v : JSON.toJSONString(v);
        } catch (Exception e) {
            log.warn("[SQL结果] JSON路径解析失败: path={}, error={}", jsonPath, e.getMessage());
            return null;
        }
    }

    /** 断言提示中的列定位描述：列名 + 可选 JSON 路径 */
    private String columnLabel(String columnName, String jsonPath) {
        return (jsonPath != null && !jsonPath.trim().isEmpty())
                ? columnName + jsonPath.trim()
                : columnName;
    }

    /**
     * 处理 SQL 结果提取规则
     * 从查询/DML 结果中按列名和行下标提取值，存入变量上下文
     */
    private Map<String, Object> processSqlExtractions(SqlConfig sqlConfig, String responseBody, Map<String, Object> variables) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (sqlConfig.getSqlExtractions() == null || sqlConfig.getSqlExtractions().isEmpty()) {
            return result;
        }
        List<SqlExtraction> extractions = sqlConfig.getSqlExtractions();
        List<Map<String, Object>> rows = null;
        Integer affectedRows = null;

        for (SqlExtraction ext : extractions) {
            if (ext.isDisabled() || ext.getVariableName() == null || ext.getColumnName() == null) {
                continue;
            }
            String value = null;
            try {
                if ("affectedRows".equals(ext.getColumnName())) {
                    if (affectedRows == null) affectedRows = normalizeToAffectedRows(responseBody);
                    if (affectedRows >= 0) value = String.valueOf(affectedRows);
                } else {
                    if (rows == null) rows = normalizeToRows(responseBody);
                    if (rows != null) {
                        int ri = ext.getRowIndex() != null ? ext.getRowIndex() : 0;
                        if (ri >= 0 && ri < rows.size()) {
                            value = resolveCellValue(rows.get(ri).get(ext.getColumnName()), ext.getJsonPath());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[SQL提取] 提取失败: variable={}, column={}, row={}, error={}",
                        ext.getVariableName(), ext.getColumnName(), ext.getRowIndex(), e.getMessage());
            }
            if (value == null && ext.getDefaultValue() != null) {
                // 默认值支持 ${var} / {{var}} 引用（可引用已提取变量与上下文变量）
                value = VariableReplacer.replace(ext.getDefaultValue(), variables);
            }
            if (value != null) {
                variables.put(ext.getVariableName(), value);
                result.put(ext.getVariableName(), value);
                log.debug("[SQL提取] {} = {} (column={}, row={})",
                        ext.getVariableName(), value, ext.getColumnName(), ext.getRowIndex());
            }
        }
        return result;
    }

    /**
     * 处理 SQL 断言规则
     */
    private List<AssertResult> processSqlAssertions(SqlConfig sqlConfig, String responseBody, Map<String, Object> variables) {
        List<AssertResult> results = new ArrayList<>();
        if (sqlConfig.getSqlAssertions() == null || sqlConfig.getSqlAssertions().isEmpty()) {
            return results;
        }
        List<Map<String, Object>> rows = null;
        Integer affectedRows = null;

        for (SqlAssertion a : sqlConfig.getSqlAssertions()) {
            if (a.isDisabled() || a.getColumnName() == null || a.getCondition() == null) {
                continue;
            }
            String actualValue = null;
            // 期望值支持 ${var} / {{var}} 变量引用（与 HTTP 断言行为一致），提示文案展示替换后的值
            String expectedValue = a.getExpectedValue() != null
                    ? VariableReplacer.replace(a.getExpectedValue(), variables) : null;
            String tip = columnLabel(a.getColumnName(), a.getJsonPath())
                    + " " + getConditionLabel(a.getCondition()) + " " + expectedValue;
            try {
                if ("affectedRows".equals(a.getColumnName())) {
                    if (affectedRows == null) affectedRows = normalizeToAffectedRows(responseBody);
                    if (affectedRows >= 0) actualValue = String.valueOf(affectedRows);
                } else {
                    if (rows == null) rows = normalizeToRows(responseBody);
                    if (rows != null) {
                        int ri = a.getRowIndex() != null ? a.getRowIndex() : 0;
                        if (ri >= 0 && ri < rows.size()) {
                            actualValue = resolveCellValue(rows.get(ri).get(a.getColumnName()), a.getJsonPath());
                        }
                    }
                }
                boolean pass = evaluateAssertion(a.getCondition(), actualValue, expectedValue);
                results.add(new AssertResult(pass, tip, "SQL_ASSERT", a.getCondition(), actualValue));
            } catch (Exception e) {
                log.warn("[SQL断言] 断言异常: {}", e.getMessage());
                results.add(new AssertResult(false, tip + " (异常: " + e.getMessage() + ")", "SQL_ASSERT", a.getCondition(), actualValue));
            }
        }
        return results;
    }

    private String getConditionLabel(String condition) {
        switch (condition != null ? condition.toUpperCase() : "") {
            case "EQUALS": return "等于";
            case "NOT_EQUALS": return "不等于";
            case "CONTAINS": return "包含";
            case "NOT_CONTAINS": return "不包含";
            case "GT": return "大于";
            case "LT": return "小于";
            case "GE": return "大于等于";
            case "LE": return "小于等于";
            case "REGULAR": return "正则匹配";
            default: return condition;
        }
    }

    private boolean evaluateAssertion(String condition, String actual, String expected) {
        if (actual == null) return false;
        switch (condition != null ? condition.toUpperCase() : "") {
            case "EQUALS": return actual.equals(expected);
            case "NOT_EQUALS": return !actual.equals(expected);
            case "CONTAINS": return actual.contains(expected);
            case "NOT_CONTAINS": return !actual.contains(expected);
            case "GT":
                try { return Double.parseDouble(actual) > Double.parseDouble(expected); }
                catch (NumberFormatException e) { return false; }
            case "LT":
                try { return Double.parseDouble(actual) < Double.parseDouble(expected); }
                catch (NumberFormatException e) { return false; }
            case "GE":
                try { return Double.parseDouble(actual) >= Double.parseDouble(expected); }
                catch (NumberFormatException e) { return false; }
            case "LE":
                try { return Double.parseDouble(actual) <= Double.parseDouble(expected); }
                catch (NumberFormatException e) { return false; }
            case "REGULAR": return actual.matches(expected);
            default: return false;
        }
    }

    /**
     * 构建变量上下文
     */
    private Map<String, Object> buildVariableContext(ApiRequest request) {
        Map<String, Object> variables = new HashMap<>();
        if (request.getEnvInfo() != null && request.getEnvInfo().getEnvVariables() != null) {
            variables.putAll(request.getEnvInfo().getEnvVariables());
        }
        return variables;
    }

    /**
     * 将 SQLException 翻译成对用户友好的中文提示。
     * 优先按数据库错误码精确匹配（MySQL/MariaDB 通用），其次按 SQLState 大类归类，最后回退原始信息。
     */
    private String friendlySqlError(SQLException e, int timeoutSeconds) {
        String raw = e.getMessage() != null ? e.getMessage().trim() : "未知错误";
        String state = e.getSQLState();
        int code = e.getErrorCode();
        String lowerRaw = raw.toLowerCase();

        // 查询超时（setQueryTimeout 触发，MySQL 为 error 1317 / SQLState 70100，消息含 timeout）
        if ("70100".equals(state) || lowerRaw.contains("timed out") || lowerRaw.contains("timeout")) {
            return "查询超时（超过 " + timeoutSeconds + " 秒），请优化 SQL 或调大超时时间后重试";
        }
        // 连接类错误（SQLState 08 开头）
        if (state != null && state.startsWith("08")) {
            return "数据库连接中断，请检查网络与数据库服务状态：" + raw;
        }
        // MySQL/MariaDB 常见错误码
        switch (code) {
            case 1045: return "数据库认证失败：用户名或密码错误";
            case 1044: return "当前账号无权访问目标数据库：" + raw;
            case 1049: return "数据库不存在：" + raw;
            case 1051: return "要删除的表不存在：" + raw;
            case 1054: return "SQL 引用了不存在的列：" + raw;
            case 1062: return "主键/唯一索引冲突，数据已存在：" + raw;
            case 1064: return "SQL 语法错误，请检查语句书写：" + raw;
            case 1146: return "数据表不存在：" + raw;
            case 1213: return "发生死锁，请稍后重试：" + raw;
            case 1205: return "锁等待超时，目标数据正被其他事务占用，请稍后重试";
            case 1366: return "字段值类型不匹配：" + raw;
            case 1406: return "数据长度超出字段限制：" + raw;
            case 1452: return "外键约束失败：关联的数据不存在：" + raw;
            case 1451: return "外键约束失败：该数据正被其他表引用，无法删除/修改：" + raw;
            default: break;
        }
        // SQLState 大类兜底
        if (state != null) {
            if (state.startsWith("42")) return "SQL 语法错误或引用的表/列不存在：" + raw;
            if (state.startsWith("23")) return "违反数据完整性约束（主键/唯一/外键/非空）：" + raw;
            if (state.startsWith("22")) return "数据值非法（类型/长度/范围不符）：" + raw;
            if (state.startsWith("40")) return "事务回滚（可能发生死锁），请重试：" + raw;
            if (state.startsWith("28")) return "数据库认证或授权失败：" + raw;
        }
        return "SQL 执行失败：" + raw;
    }

    /** 取异常链最底层的 message，避免包装层信息过于笼统 */
    private String rootCauseMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg != null && !msg.isBlank() ? msg : cur.getClass().getSimpleName();
    }

    /**
     * 构建错误响应（无请求上下文时的简版）
     */
    private TestHttpResponse buildErrorResponse(ApiRequest request, String uuid, long startTime,
                                                 String errorMessage, List<String> consoleLogs,
                                                 List<com.mokatest.platform.demos.api.script.ScriptContext.ScriptAssertion> scriptAssertions) {
        return buildErrorResponse(request, uuid, startTime, errorMessage, consoleLogs, scriptAssertions, null, null);
    }

    /**
     * 构建错误响应：携带 JDBC 地址与执行的 SQL，方便前端展示排查信息
     */
    private TestHttpResponse buildErrorResponse(ApiRequest request, String uuid, long startTime,
                                                 String errorMessage, List<String> consoleLogs,
                                                 List<com.mokatest.platform.demos.api.script.ScriptContext.ScriptAssertion> scriptAssertions,
                                                 String jdbcUrl, String executedSql) {
        long endTime = System.currentTimeMillis();
        return TestHttpResponse.builder()
                .uuid(uuid)
                .status("error")
                .statusCode(500)
                .responseStatusMsg("SQL Error")
                .requestUrl(jdbcUrl)
                .requestMethod("SQL")
                .requestHeaders(executedSql != null ? Map.of("X-Sql-Statement", executedSql) : null)
                .responseTimeMs(endTime - startTime)
                .requestStartTime(startTime)
                .requestEndTime(endTime)
                .errorMessage(errorMessage)
                .scriptConsoleLog(consoleLogs)
                .scriptAssertions(scriptAssertions)
                .build();
    }
}
