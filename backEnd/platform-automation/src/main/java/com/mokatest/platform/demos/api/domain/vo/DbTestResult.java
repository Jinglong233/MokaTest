package com.mokatest.platform.demos.api.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库连接测试结果
 *
 * @author JingLong
 * @since 2026-07-28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DbTestResult {
    /** 是否成功 */
    private boolean success;
    /** 结果消息 */
    private String message;
    /** 数据库版本（成功时返回） */
    private String dbVersion;
    /** 连接延迟（毫秒） */
    private long latencyMs;

    public static DbTestResult success(String version, long latencyMs) {
        return DbTestResult.builder()
                .success(true)
                .message("连接成功")
                .dbVersion(version)
                .latencyMs(latencyMs)
                .build();
    }

    public static DbTestResult fail(String message) {
        return DbTestResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
