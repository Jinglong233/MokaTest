import {DataBaseType} from "@/types/domain/api/apiEnum/DataBaseType";

export class DataBaseParameter {
      dataBaseType?: DataBaseType;
      name?: string;
      /** JDBC URL 中的数据库名 */
      dbName?: string;
      value?: string;
      ip?: string;
      port?: string;
      userName?: string;
      password?: string;
      // 编码集
      charset?: string;
      /** 连接描述/备注 */
      description?: string;
}
