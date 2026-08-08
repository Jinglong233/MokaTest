package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

/**
 * SQL 参数绑定项
 *
 * @author JingLong
 * @since 2026-07-28
 */
@Data
public class SqlParam {
    /** 参数名 */
    private String name;
    /** 参数值，支持 ${var} 变量替换 */
    private String value;
    /** 参数类型：STRING / INT / FLOAT / DATE */
    private String type;
}
