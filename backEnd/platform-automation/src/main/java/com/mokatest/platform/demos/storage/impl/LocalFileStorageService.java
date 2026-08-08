package com.mokatest.platform.demos.storage.impl;

import com.mokatest.platform.demos.storage.FileStorageService;
import com.mokatest.platform.demos.storage.StorageProperties;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地磁盘文件存储实现。
 *
 * 适合单机部署或临时调试文件，不推荐多实例共享场景。
 */
@Slf4j
public class LocalFileStorageService implements FileStorageService {

    private final StorageProperties properties;
    private final Path basePath;

    public LocalFileStorageService(StorageProperties properties) {
        this.properties = properties;
        this.basePath = resolveBasePath(properties.getLocal().getBasePath());
    }

    private Path resolveBasePath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return Paths.get(System.getProperty("user.dir"), "uploads");
        }
        String resolved = configuredPath.replace("${user.dir}", System.getProperty("user.dir"));
        return Paths.get(resolved).toAbsolutePath().normalize();
    }

    @Override
    public String upload(File file, String path) {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("上传文件不能为空或不存在");
        }
        try {
            Path target = resolveTargetPath(path);
            Files.createDirectories(target.getParent());
            Files.copy(file.toPath(), target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            log.info("本地文件上传成功：{}", target);
            return relativize(target);
        } catch (Exception e) {
            log.error("本地文件上传失败：{}", path, e);
            throw new RuntimeException("本地文件上传失败", e);
        }
    }

    @Override
    public String upload(byte[] data, String path) {
        if (data == null) {
            throw new IllegalArgumentException("上传数据不能为空");
        }
        try {
            Path target = resolveTargetPath(path);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            log.info("本地字节上传成功：{}", target);
            return relativize(target);
        } catch (Exception e) {
            log.error("本地字节上传失败：{}", path, e);
            throw new RuntimeException("本地字节上传失败", e);
        }
    }

    @Override
    public void download(String fileId, HttpServletResponse response) {
        Path file = resolveFileId(fileId);
        if (!Files.exists(file)) {
            log.warn("本地文件不存在：{}", file);
            throw new RuntimeException("文件不存在");
        }
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "inline; filename=\"" + file.getFileName() + "\"");
        try (InputStream input = Files.newInputStream(file);
             ServletOutputStream output = response.getOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = input.read(buffer)) != -1) {
                output.write(buffer, 0, len);
            }
            output.flush();
        } catch (Exception e) {
            log.error("本地文件下载失败：{}", fileId, e);
            throw new RuntimeException("本地文件下载失败", e);
        }
    }

    @Override
    public String getPreviewUrl(String fileId) {
        // 本地存储没有公共直链，返回后端代理地址
        return "/api/file/download?fileId=" + encodeFileId(fileId);
    }

    @Override
    public String getDownloadUrl(String fileId) {
        return "/api/file/download?fileId=" + encodeFileId(fileId);
    }

    @Override
    public boolean delete(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            return false;
        }
        try {
            Path file = resolveFileId(fileId);
            return Files.deleteIfExists(file);
        } catch (Exception e) {
            log.error("本地文件删除失败：{}", fileId, e);
            return false;
        }
    }

    @Override
    public InputStream openInputStream(String fileId) {
        try {
            return Files.newInputStream(resolveFileId(fileId));
        } catch (IOException e) {
            log.error("本地文件打开失败：{}", fileId, e);
            return null;
        }
    }

    @Override
    public boolean exists(String fileId) {
        return Files.exists(resolveFileId(fileId));
    }

    private Path resolveTargetPath(String path) {
        // 如果 path 不含目录分隔符，生成 UUID 作为文件名避免冲突
        if (path == null || path.isBlank()) {
            path = UUID.randomUUID().toString();
        }
        return basePath.resolve(path).normalize();
    }

    private Path resolveFileId(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId 不能为空");
        }
        // 防止路径穿越
        Path resolved = basePath.resolve(fileId).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new SecurityException("非法 fileId：" + fileId);
        }
        return resolved;
    }

    private String relativize(Path target) {
        return basePath.relativize(target).toString().replace("\\", "/");
    }

    private String encodeFileId(String fileId) {
        try {
            return java.net.URLEncoder.encode(fileId, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return fileId;
        }
    }
}
