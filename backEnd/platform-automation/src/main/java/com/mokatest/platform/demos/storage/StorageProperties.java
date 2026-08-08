package com.mokatest.platform.demos.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 文件存储配置。
 *
 * 通过 storage.type 切换后端实现：local / minio / oss / cos / s3
 */
@Data
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    /**
     * 存储类型：local / minio / oss / cos / s3
     */
    private String type = "local";

    /**
     * 本地存储配置
     */
    private LocalProperties local = new LocalProperties();

    /**
     * 阿里云 OSS 配置
     */
    private OssProperties oss = new OssProperties();

    /**
     * 腾讯云 COS 配置
     */
    private CosProperties cos = new CosProperties();

    /**
     * AWS S3 配置
     */
    private S3Properties s3 = new S3Properties();

    /**
     * MinIO 配置
     */
    private MinioProperties minio = new MinioProperties();

    @Data
    public static class LocalProperties {
        /**
         * 本地存储根目录，支持 ${user.dir} 占位符
         */
        private String basePath = "${user.dir}/uploads";
    }

    @Data
    public static class OssProperties {
        private String endpoint;
        private String bucket;
        private String accessKey;
        private String secretKey;
        /**
         * 是否使用 HTTPS
         */
        private boolean secure = true;
    }

    @Data
    public static class CosProperties {
        /**
         * 地域，如 ap-guangzhou / ap-shanghai / ap-beijing
         */
        private String region;
        /**
         * 存储桶名称，如 mybucket-1250000000
         */
        private String bucket;
        private String accessKey;
        private String secretKey;
        /**
         * 是否使用 HTTPS
         */
        private boolean secure = true;
    }

    @Data
    public static class S3Properties {
        private String region;
        private String bucket;
        private String accessKey;
        private String secretKey;
    }

    @Data
    public static class MinioProperties {
        private String endpoint;
        private String bucket;
        private String accessKey;
        private String secretKey;
        /**
         * 是否通过后端代理访问文件。
         * true：getPreviewUrl/getDownloadUrl 返回 /api/file/{fileId}，避免前端跨域。
         * false：返回 MinIO 直链/签名 URL。
         */
        private boolean useProxy = true;
    }
}
