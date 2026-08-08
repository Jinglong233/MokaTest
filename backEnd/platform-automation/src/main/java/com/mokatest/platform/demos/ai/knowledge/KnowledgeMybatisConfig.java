package com.mokatest.platform.demos.ai.knowledge;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * 知识库包 Mapper 扫描配置
 *
 * 注意：必须加 annotationClass 过滤——本包内还有 EmbeddingStore 等普通服务接口，
 * 全包扫描会把它们也注册成 MyBatis Mapper 代理，注入后调用即
 * "Invalid bound statement (not found)"。只注册带 @Mapper 注解的接口。
 */
@Configuration
@MapperScan(basePackages = "com.mokatest.platform.demos.ai.knowledge", annotationClass = Mapper.class)
public class KnowledgeMybatisConfig {
}
