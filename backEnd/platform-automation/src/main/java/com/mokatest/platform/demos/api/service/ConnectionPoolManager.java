package com.mokatest.platform.demos.api.service;

import com.mokatest.platform.demos.api.domain.apiEnum.DataBaseType;
import com.mokatest.platform.demos.api.domain.requestModel.DataBaseParameter;
import com.mokatest.platform.demos.api.domain.vo.DbTestResult;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接池管理器
 * <p>
 * 按 projectId:connectionName 隔离连接池，符合平台数据边界约定。
 * 支持 9 种数据库类型的 JDBC URL 自动拼接。
 *
 * @author JingLong
 * @since 2026-07-28
 */
@Slf4j
@Component
public class ConnectionPoolManager {

    /**
     * key = "projectId:dbName" 的 HikariCP 连接池缓存
     */
    private final ConcurrentHashMap<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    /**
     * 获取或创建连接池
     *
     * @param param     数据库连接参数
     * @param projectId 项目 ID（用于隔离）
     * @return DataSource
     */
    public DataSource getOrCreate(DataBaseParameter param, Integer projectId) {
        String key = buildPoolKey(projectId, param.getName());
        return pools.computeIfAbsent(key, k -> createPool(param));
    }

    /**
     * 测试数据库连接（不走连接池，用完即关）
     *
     * @param param 数据库连接参数
     * @return 测试结果
     */
    public DbTestResult testConnection(DataBaseParameter param) {
        String url = buildJdbcUrl(param);
        // 加载驱动类
        loadDriver(param.getDataBaseType());

        long start = System.currentTimeMillis();
        try (Connection conn = DriverManager.getConnection(url, param.getUserName(), param.getPassword())) {
            if (!conn.isValid(5)) {
                return DbTestResult.fail("连接验证失败：isValid 返回 false");
            }
            String version = conn.getMetaData().getDatabaseProductVersion();
            long latency = System.currentTimeMillis() - start;
            log.info("数据库连接测试成功: {} -> {}, 延迟 {}ms", param.getName(), url, latency);
            return DbTestResult.success(version, latency);
        } catch (SQLException e) {
            long latency = System.currentTimeMillis() - start;
            log.warn("数据库连接测试失败: {} -> {}, 耗时 {}ms, 错误: {}", param.getName(), url, latency, e.getMessage());
            return DbTestResult.fail(e.getMessage());
        }
    }

    /**
     * 销毁指定连接池（配置变更时调用）
     */
    public void evict(Integer projectId, String connectionName) {
        String key = buildPoolKey(projectId, connectionName);
        HikariDataSource ds = pools.remove(key);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("已销毁连接池: {}", key);
        }
    }

    /**
     * 构建连接池 key
     */
    private String buildPoolKey(Integer projectId, String connectionName) {
        return projectId + ":" + connectionName;
    }

    /**
     * 创建 HikariCP 连接池
     */
    private HikariDataSource createPool(DataBaseParameter param) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(buildJdbcUrl(param));
        config.setUsername(param.getUserName());
        config.setPassword(param.getPassword());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);       // 10s
        config.setMaxLifetime(600000);            // 10min
        config.setIdleTimeout(300000);            // 5min
        config.setPoolName("HikariPool-" + param.getName());

        return new HikariDataSource(config);
    }

    /**
     * 加载 JDBC 驱动类
     */
    private void loadDriver(DataBaseType dataBaseType) {
        String driverClass = getDriverClass(dataBaseType);
        try {
            Class.forName(driverClass);
        } catch (ClassNotFoundException e) {
            log.warn("无法加载 JDBC 驱动: {}, 尝试依赖 DriverManager 自动发现", driverClass);
        }
    }

    /**
     * 根据 DataBaseType 获取驱动类名
     */
    private String getDriverClass(DataBaseType dataBaseType) {
        switch (dataBaseType) {
            case MYSQL:
            case MARIADB:
                return "com.mysql.cj.jdbc.Driver";
            case POSTGRESQL:
                return "org.postgresql.Driver";
            case SQLSERVER:
                return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            case ORACLE:
                return "oracle.jdbc.OracleDriver";
            case DB2:
                return "com.ibm.db2.jcc.DB2Driver";
            case H2:
                return "org.h2.Driver";
            case SQLITE:
                return "org.sqlite.JDBC";
            default:
                return "com.mysql.cj.jdbc.Driver";
        }
    }

    /**
     * 构建 JDBC URL
     */
    public String buildJdbcUrl(DataBaseParameter param) {
        DataBaseType type = param.getDataBaseType();
        if (type == null) {
            type = DataBaseType.MYSQL;
        }
        String host = param.getIp() != null ? param.getIp() : "localhost";
        String port = param.getPort() != null ? param.getPort() : getDefaultPort(type);
        String dbName = param.getDbName() != null ? param.getDbName() : "";

        switch (type) {
            case MYSQL:
                return String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf-8",
                        host, port, dbName);
            case MARIADB:
                return String.format("jdbc:mariadb://%s:%s/%s", host, port, dbName);
            case POSTGRESQL:
                return String.format("jdbc:postgresql://%s:%s/%s", host, port, dbName);
            case SQLSERVER:
                return String.format("jdbc:sqlserver://%s:%s;databaseName=%s;encrypt=false",
                        host, port, dbName);
            case ORACLE:
                // Oracle 使用 thin 模式，dbName 可能是 SID 或 Service Name
                return String.format("jdbc:oracle:thin:@%s:%s:%s", host, port, dbName);
            case DB2:
                return String.format("jdbc:db2://%s:%s/%s", host, port, dbName);
            case H2:
                return String.format("jdbc:h2:tcp://%s:%s/%s", host, port, dbName);
            case SQLITE:
                return String.format("jdbc:sqlite:%s", dbName);
            default:
                return String.format("jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                        host, port, dbName);
        }
    }

    /**
     * 获取默认端口
     */
    private String getDefaultPort(DataBaseType type) {
        switch (type) {
            case MYSQL:
            case MARIADB:
                return "3306";
            case POSTGRESQL:
                return "5432";
            case SQLSERVER:
                return "1433";
            case ORACLE:
                return "1521";
            case DB2:
                return "50000";
            case H2:
                return "9092";
            default:
                return "3306";
        }
    }

    /**
     * 合并数据库连接配置：步骤级覆盖环境级
     *
     * @param envConfig   环境级数据库配置（可为 null）
     * @param stepOverride 步骤级覆盖配置（可为 null）
     * @return 合并后的配置（新对象，不修改入参）
     */
    public DataBaseParameter mergeDbConfig(DataBaseParameter envConfig, DataBaseParameter stepOverride) {
        if (stepOverride == null && envConfig == null) {
            return null;
        }
        if (stepOverride == null) {
            return deepCopy(envConfig);
        }
        if (envConfig == null) {
            return deepCopy(stepOverride);
        }

        DataBaseParameter merged = deepCopy(envConfig);
        if (stepOverride.getDataBaseType() != null) merged.setDataBaseType(stepOverride.getDataBaseType());
        if (stepOverride.getIp() != null) merged.setIp(stepOverride.getIp());
        if (stepOverride.getPort() != null) merged.setPort(stepOverride.getPort());
        if (stepOverride.getDbName() != null) merged.setDbName(stepOverride.getDbName());
        if (stepOverride.getUserName() != null) merged.setUserName(stepOverride.getUserName());
        if (stepOverride.getPassword() != null) merged.setPassword(stepOverride.getPassword());
        if (stepOverride.getCharset() != null) merged.setCharset(stepOverride.getCharset());
        if (stepOverride.getName() != null) merged.setName(stepOverride.getName());
        if (stepOverride.getValue() != null) merged.setValue(stepOverride.getValue());
        return merged;
    }

    private DataBaseParameter deepCopy(DataBaseParameter source) {
        DataBaseParameter copy = new DataBaseParameter();
        copy.setDataBaseType(source.getDataBaseType());
        copy.setName(source.getName());
        copy.setDbName(source.getDbName());
        copy.setValue(source.getValue());
        copy.setIp(source.getIp());
        copy.setPort(source.getPort());
        copy.setUserName(source.getUserName());
        copy.setPassword(source.getPassword());
        copy.setCharset(source.getCharset());
        copy.setDescription(source.getDescription());
        return copy;
    }
}
