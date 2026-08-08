package com.mokatest.platform.demos.api.domain.requestModel;

import com.mokatest.platform.demos.api.domain.apiEnum.DataBaseType;
import lombok.Data;

/**
 * @Description: 数据库参数
 * @Author: JingLong
 * @DateTime: 2026/4/3 14:36
 */
@Data
public class DataBaseParameter {
    private DataBaseType dataBaseType;
    private String name;
    /** JDBC URL 中的数据库名 */
    private String dbName;
    private String value;
    private String ip;
    private String port;
    private String userName;
    private String password;
    // 编码集
    private String charset;
    /** 连接描述/备注 */
    private String description;
}
