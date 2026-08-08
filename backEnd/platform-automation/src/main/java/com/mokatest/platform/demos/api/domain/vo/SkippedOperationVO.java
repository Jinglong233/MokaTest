package com.mokatest.platform.demos.api.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Swagger / OpenAPI 导入跳过的操作记录
 *
 * @author JingLong
 * @since 2026-07-03
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkippedOperationVO {

    /**
     * API 路径
     */
    private String path;

    /**
     * HTTP 方法
     */
    private String method;

    /**
     * 跳过原因
     */
    private String reason;
}
