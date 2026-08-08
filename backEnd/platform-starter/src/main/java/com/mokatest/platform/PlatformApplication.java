package com.mokatest.platform;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.mokatest.platform.demos")
@MapperScan({"com.mokatest.platform.demos.mapper", "com.mokatest.platform.demos.api.mapper", "com.mokatest.platform.demos.qa.mapper", "com.mokatest.platform.demos.qa.message.mapper", "com.mokatest.platform.demos.qa.config.mapper", "com.mokatest.platform.demos.ai.mapper"}) // 确保包路径正确
public class PlatformApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(PlatformApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }



}
