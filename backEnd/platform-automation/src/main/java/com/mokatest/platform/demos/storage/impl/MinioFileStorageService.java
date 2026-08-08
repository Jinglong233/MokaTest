package com.mokatest.platform.demos.storage.impl;

import com.mokatest.platform.demos.storage.FileStorageService;
import com.mokatest.platform.demos.storage.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.http.Method;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 文件存储实现。
 *
 * 兼容 S3 协议，适合私有化部署、多机共享文件场景。
 * fileId 对应 MinIO 对象键（object key）。
 */
@Slf4j
public class MinioFileStorageService implements FileStorageService {

    private static final int DEFAULT_URL_EXPIRATION_SECONDS = 3600;

    private final StorageProperties properties;
    private final MinioClient minioClient;
    private final String bucket;

    public MinioFileStorageService(StorageProperties properties) {
        this.properties = properties;
        StorageProperties.MinioProperties minio = properties.getMinio();
        this.bucket = minio.getBucket();
        this.minioClient = MinioClient.builder()
                .endpoint(minio.getEndpoint())
                .credentials(minio.getAccessKey(), minio.getSecretKey())
                .build();
    }

    @Override
    public String upload(File file, String path) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("上传文件不能为空或不存在");
        }
        String key = resolveKey(path, file.getName());
        try (InputStream input = new java.io.FileInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(input, file.length(), -1)
                    .contentType(detectContentType(file.getName()))
                    .build());
            log.info("MinIO 文件上传成功：bucket={}, key={}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("MinIO 文件上传失败：bucket={}, key={}", bucket, key, e);
            throw new RuntimeException("MinIO 文件上传失败", e);
        }
    }

    @Override
    public String upload(byte[] data, String path) {
        if (data == null) {
            throw new IllegalArgumentException("上传数据不能为空");
        }
        String key = resolveKey(path, null);
        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(input, data.length, -1)
                    .build());
            log.info("MinIO 字节上传成功：bucket={}, key={}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("MinIO 字节上传失败：bucket={}, key={}", bucket, key, e);
            throw new RuntimeException("MinIO 字节上传失败", e);
        }
    }

    @Override
    public void download(String fileId, HttpServletResponse response) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        response.setContentType("application/octet-stream");
        String fileName = fileId.substring(fileId.lastIndexOf('/') + 1);
        response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(fileId)
                        .build());
             ServletOutputStream output = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            output.flush();
        } catch (Exception e) {
            log.error("MinIO 文件下载失败：bucket={}, key={}", bucket, fileId, e);
            throw new RuntimeException("MinIO 文件下载失败", e);
        }
    }

    @Override
    public String getPreviewUrl(String fileId) {
        if (properties.getMinio().isUseProxy()) {
            return "/api/file/download?fileId=" + encodeFileId(fileId);
        }
        return generatePresignedUrl(fileId, Method.GET);
    }

    @Override
    public String getDownloadUrl(String fileId) {
        if (properties.getMinio().isUseProxy()) {
            return "/api/file/download?fileId=" + encodeFileId(fileId);
        }
        return generatePresignedUrl(fileId, Method.GET);
    }

    @Override
    public boolean delete(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        try {
            minioClient.removeObject(io.minio.RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileId)
                    .build());
            log.info("MinIO 文件删除成功：bucket={}, key={}", bucket, fileId);
            return true;
        } catch (Exception e) {
            log.error("MinIO 文件删除失败：bucket={}, key={}", bucket, fileId, e);
            return false;
        }
    }

    @Override
    public InputStream openInputStream(String fileId) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileId)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 文件打开失败：bucket={}, key={}", bucket, fileId, e);
            return null;
        }
    }

    @Override
    public boolean exists(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(fileId)
                    .build());
            return true;
        } catch (Exception e) {
            log.warn("MinIO 文件存在性检查失败：bucket={}, key={}", bucket, fileId, e);
            return false;
        }
    }

    private String resolveKey(String path, String fallbackName) {
        if (path == null || path.isBlank()) {
            String suffix = extractSuffix(fallbackName);
            return UUID.randomUUID() + suffix;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String extractSuffix(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fileName.substring(lastDot);
        }
        return "";
    }

    private String detectContentType(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "application/octet-stream";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        return "application/octet-stream";
    }

    private String generatePresignedUrl(String fileId, Method method) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            return minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .bucket(bucket)
                    .object(fileId)
                    .method(method)
                    .expiry(DEFAULT_URL_EXPIRATION_SECONDS, TimeUnit.SECONDS)
                    .build());
        } catch (Exception e) {
            log.error("MinIO 生成签名 URL 失败：bucket={}, key={}", bucket, fileId, e);
            throw new RuntimeException("MinIO 生成签名 URL 失败", e);
        }
    }

    private String encodeFileId(String fileId) {
        try {
            return java.net.URLEncoder.encode(fileId, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return fileId;
        }
    }
}
