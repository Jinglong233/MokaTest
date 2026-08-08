package com.mokatest.platform.demos.storage;

import com.aliyun.oss.OSS;
import com.mokatest.platform.demos.storage.impl.CosFileStorageService;
import com.mokatest.platform.demos.storage.impl.LocalFileStorageService;
import com.mokatest.platform.demos.storage.impl.MinioFileStorageService;
import com.mokatest.platform.demos.storage.impl.OssFileStorageService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文件存储自动配置。
 *
 * 根据 storage.type 选择对应实现。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "local", matchIfMissing = true)
    public FileStorageService localFileStorageService(StorageProperties properties) {
        return new LocalFileStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "cos")
    public FileStorageService cosFileStorageService(StorageProperties properties) {
        return new CosFileStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "oss")
    @ConditionalOnClass(OSS.class)
    public FileStorageService ossFileStorageService(StorageProperties properties) {
        return new OssFileStorageService(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "storage.type", havingValue = "minio")
    public FileStorageService minioFileStorageService(StorageProperties properties) {
        return new MinioFileStorageService(properties);
    }
}
