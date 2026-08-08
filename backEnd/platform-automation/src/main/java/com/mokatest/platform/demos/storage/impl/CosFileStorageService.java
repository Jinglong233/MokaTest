package com.mokatest.platform.demos.storage.impl;

import com.mokatest.platform.demos.storage.FileStorageService;
import com.mokatest.platform.demos.storage.StorageProperties;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
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
 * 腾讯云 COS 文件存储实现。
 *
 * 使用存储桶对象键作为 fileId，支持生成临时签名 URL 用于下载/预览。
 */
@Slf4j
public class CosFileStorageService implements FileStorageService {

    private static final long DEFAULT_URL_EXPIRATION_MS = 3600 * 1000L;

    private final StorageProperties properties;
    private final COSClient cosClient;
    private final String bucket;

    public CosFileStorageService(StorageProperties properties) {
        this.properties = properties;
        StorageProperties.CosProperties cos = properties.getCos();
        this.bucket = cos.getBucket();
        COSCredentials credentials = new BasicCOSCredentials(cos.getAccessKey(), cos.getSecretKey());
        ClientConfig clientConfig = new ClientConfig(new Region(cos.getRegion()));
        clientConfig.setHttpProtocol(cos.isSecure() ? HttpProtocol.https : HttpProtocol.http);
        this.cosClient = new COSClient(credentials, clientConfig);
    }

    @Override
    public String upload(File file, String path) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("上传文件不能为空或不存在");
        }
        String key = resolveKey(path, file.getName());
        try {
            cosClient.putObject(bucket, key, file);
            log.info("COS 文件上传成功：bucket={}, key={}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("COS 文件上传失败：bucket={}, key={}", bucket, key, e);
            throw new RuntimeException("COS 文件上传失败", e);
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
            cosClient.putObject(request);
            log.info("COS 字节上传成功：bucket={}, key={}", bucket, key);
            return key;
        } catch (Exception e) {
            log.error("COS 字节上传失败：bucket={}, key={}", bucket, key, e);
            throw new RuntimeException("COS 字节上传失败", e);
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
        try (InputStream input = cosClient.getObject(bucket, fileId).getObjectContent();
             ServletOutputStream output = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            output.flush();
        } catch (Exception e) {
            log.error("COS 文件下载失败：bucket={}, key={}", bucket, fileId, e);
            throw new RuntimeException("COS 文件下载失败", e);
        }
    }

    @Override
    public String getPreviewUrl(String fileId) {
        return generatePresignedUrl(fileId, HttpMethodName.GET);
    }

    @Override
    public String getDownloadUrl(String fileId) {
        return generatePresignedUrl(fileId, HttpMethodName.GET);
    }

    @Override
    public boolean delete(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        try {
            cosClient.deleteObject(bucket, fileId);
            log.info("COS 文件删除成功：bucket={}, key={}", bucket, fileId);
            return true;
        } catch (Exception e) {
            log.error("COS 文件删除失败：bucket={}, key={}", bucket, fileId, e);
            return false;
        }
    }

    @Override
    public InputStream openInputStream(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            return cosClient.getObject(bucket, fileId).getObjectContent();
        } catch (Exception e) {
            log.error("COS 文件流打开失败：bucket={}, key={}", bucket, fileId, e);
            return null;
        }
    }

    @Override
    public boolean exists(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        try {
            return cosClient.doesObjectExist(bucket, fileId);
        } catch (CosClientException e) {
            log.warn("COS 文件存在性检查失败：bucket={}, key={}", bucket, fileId, e);
            return false;
        }
    }

    private String resolveKey(String path, String fallbackName) {
        if (path == null || path.isBlank()) {
            String suffix = extractSuffix(fallbackName);
            return UUID.randomUUID() + suffix;
        }
        // 去除前导斜杠，保证对象键规范
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

    private String generatePresignedUrl(String fileId, HttpMethodName method) {
        if (fileId == null || fileId.isBlank()) {
            return null;
        }
        try {
            Date expiration = new Date(System.currentTimeMillis() + DEFAULT_URL_EXPIRATION_MS);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, fileId, method);
            request.setExpiration(expiration);
            URL url = cosClient.generatePresignedUrl(request);
            return url == null ? null : url.toString();
        } catch (Exception e) {
            log.error("COS 生成签名 URL 失败：bucket={}, key={}", bucket, fileId, e);
            throw new RuntimeException("COS 生成签名 URL 失败", e);
        }
    }
}
