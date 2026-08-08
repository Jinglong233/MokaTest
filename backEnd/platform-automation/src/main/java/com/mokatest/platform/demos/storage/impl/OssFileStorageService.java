package com.mokatest.platform.demos.storage.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.mokatest.platform.demos.storage.FileStorageService;
import com.mokatest.platform.demos.storage.StorageProperties;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

/**
 * 阿里云 OSS 文件存储实现。
 *
 * 使用存储桶对象键作为 fileId，支持生成临时签名 URL 用于下载/预览。
 */
@Slf4j
public class OssFileStorageService implements FileStorageService {

    private static final long DEFAULT_URL_EXPIRATION_MS = 3600 * 1000L;

    private final StorageProperties properties;
    private final OSS ossClient;
    private final String bucket;

    public OssFileStorageService(StorageProperties properties) {
        this.properties = properties;
        StorageProperties.OssProperties oss = properties.getOss();
        this.bucket = oss.getBucket();
        String endpoint = oss.isSecure() ? "https://" + oss.getEndpoint() : "http://" + oss.getEndpoint();
        this.ossClient = new OSSClientBuilder().build(endpoint, oss.getAccessKey(), oss.getSecretKey());
    }

    @Override
    public String upload(File file, String path) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("上传文件不能为空或不存在");
        }
        String key = resolveKey(path, file.getName());
        try {
            ossClient.putObject(bucket, key, file);
            log.info("OSS 文件上传成功：bucket={}, key={}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("OSS 文件上传失败：bucket={}, key={}", bucket, key, e);
            throw new RuntimeException("OSS 文件上传失败", e);
        }
    }

    @Override
    public String upload(byte[] data, String path) {
        if (data == null) {
            throw new IllegalArgumentException("上传数据不能为空");
        }
        String key = resolveKey(path, null);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(data.length);
        try (ByteArrayInputStream input = new ByteArrayInputStream(data)) {
            PutObjectRequest request = new PutObjectRequest(bucket, key, input, metadata);
            ossClient.putObject(request);
            log.info("OSS 字节上传成功：bucket={}, key={}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("OSS 字节上传失败：bucket={}, key={}", bucket, key, e);
            throw new RuntimeException("OSS 字节上传失败", e);
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
        try (InputStream input = ossClient.getObject(bucket, fileId).getObjectContent();
             ServletOutputStream output = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            output.flush();
        } catch (Exception e) {
            log.error("OSS 文件下载失败：bucket={}, key={}", bucket, fileId, e);
            throw new RuntimeException("OSS 文件下载失败", e);
        }
    }

    @Override
    public String getPreviewUrl(String fileId) {
        return generatePresignedUrl(fileId, "GET");
    }

    @Override
    public String getDownloadUrl(String fileId) {
        return generatePresignedUrl(fileId, "GET");
    }

    @Override
    public boolean delete(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        try {
            ossClient.deleteObject(bucket, fileId);
            log.info("OSS 文件删除成功：bucket={}, key={}", bucket, fileId);
            return true;
        } catch (Exception e) {
            log.error("OSS 文件删除失败：bucket={}, key={}", bucket, fileId, e);
            return false;
        }
    }

    @Override
    public InputStream openInputStream(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            return ossClient.getObject(bucket, fileId).getObjectContent();
        } catch (Exception e) {
            log.error("OSS 文件流打开失败：bucket={}, key={}", bucket, fileId, e);
            return null;
        }
    }

    @Override
    public boolean exists(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        try {
            return ossClient.doesObjectExist(bucket, fileId);
        } catch (Exception e) {
            log.warn("OSS 文件存在性检查失败：bucket={}, key={}", bucket, fileId, e);
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

    private String generatePresignedUrl(String fileId, String method) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            Date expiration = new Date(System.currentTimeMillis() + DEFAULT_URL_EXPIRATION_MS);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, fileId, com.aliyun.oss.HttpMethod.valueOf(method));
            request.setExpiration(expiration);
            URL url = ossClient.generatePresignedUrl(request);
            return url == null ? null : url.toString();
        } catch (Exception e) {
            log.error("OSS 生成签名 URL 失败：bucket={}, key={}", bucket, fileId, e);
            throw new RuntimeException("OSS 生成签名 URL 失败", e);
        }
    }
}
