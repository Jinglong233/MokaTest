/**
 * 数据库类型枚举
 * 使用字符串值以匹配后端 Jackson 序列化（enum name → "MYSQL"）
 */
export enum DataBaseType {
    MYSQL = "MYSQL",
    ORACLE = "ORACLE",
    SQLSERVER = "SQLSERVER",
    POSTGRESQL = "POSTGRESQL",
    DB2 = "DB2",
    H2 = "H2",
    MARIADB = "MARIADB",
    SQLITE = "SQLITE",
    OTHER = "OTHER"
}

/**
 * 前端下拉实际可选的数据库类型：仅保留后端 pom 中已有 JDBC 驱动的类型
 * （MARIADB 驱动与 URL 协议不匹配、DB2/H2/SQLITE 无驱动依赖，暂不开放；
 * 后端枚举保留全部类型，历史数据不受影响）
 */
export const SUPPORTED_DATA_BASE_TYPES: DataBaseType[] = [
    DataBaseType.MYSQL,
    DataBaseType.POSTGRESQL,
    DataBaseType.SQLSERVER,
    DataBaseType.ORACLE,
    DataBaseType.OTHER,
];
