import {DataBaseParameter} from "@/types/domain/api/requestModel/DataBaseParameter";
import {SqlExtraction} from "@/types/domain/api/requestModel/SqlExtraction";
import {SqlAssertion} from "@/types/domain/api/requestModel/SqlAssertion";

/** SQL 参数绑定项 */
export class SqlParam {
    /** 参数名 */
    name?: string;
    /** 参数值，支持 ${var} 变量替换 */
    value?: string;
    /** 参数类型：STRING / INT / FLOAT / DATE */
    type?: string;
}

/**
 * SQL 调试配置
 * 存储于 api_request.sql_config 或场景步骤 step_detail 中
 */
export class SqlConfig {
    /** SQL 语句，支持 ${var} / {{var}} 变量替换 */
    sql?: string;
    /** 引用环境级数据库连接名（environment.dbs[].name） */
    dbConnectionName?: string;
    /** 步骤级数据库连接覆盖（逐字段 merge，优先级高于环境级） */
    dbConfig?: DataBaseParameter;
    /** 查询超时秒数，默认 30 */
    timeout?: number;
    /** 最大返回行数，默认 1000，超限截断 */
    maxRows?: number;
    /** 可选参数绑定（预留） */
    params?: SqlParam[];
    /** SQL 结果提取规则 */
    sqlExtractions?: SqlExtraction[];
    /** SQL 断言规则 */
    sqlAssertions?: SqlAssertion[];
}
