package com.mokatest.platform.demos.qa.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import org.springframework.scheduling.annotation.EnableAsync;

/**
 * QA 模块自动配置入口
 *
 * 可插拔控制：通过 application.yml 中的 module.qa.enabled 开关控制是否加载 QA 模块
 *
 * 用法：
 *   module.qa.enabled=true  → 加载所有 QA Bean、Controller、Service
 *   module.qa.enabled=false → 不加载 QA 模块，系统正常运行
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "module.qa.enabled", havingValue = "true")
@ComponentScan(basePackages = "com.mokatest.platform.demos.qa")
@EnableAsync
public class QaModuleAutoConfiguration {

    public QaModuleAutoConfiguration() {
        log.info("[QA模块] 已启用，加载需求池/BUG池/用例列表功能");
    }
}
