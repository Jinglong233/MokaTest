package com.mokatest.platform.demos.operationlog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 操作日志配置属性
 */
@Component
@ConfigurationProperties(prefix = "operation.log")
public class OperationLogProperties {

    /**
     * 是否启用操作日志记录（默认开启）
     */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
