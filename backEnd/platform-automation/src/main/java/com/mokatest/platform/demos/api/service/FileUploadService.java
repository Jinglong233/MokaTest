package com.mokatest.platform.demos.api.service;

import com.mokatest.platform.demos.storage.FileStorageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 临时文件上传服务。
 *
 * 为 API 测试参数中的 FILE 类型、以及富文本编辑器图片提供上传和读取能力。
 * 底层实际委托给统一的 {@link FileStorageService}，支持本地磁盘、MinIO、OSS、COS 等存储后端。
 */
@Slf4j
@Service
public class FileUploadService {

    private final String uploadDir;
    private final ConcurrentHashMap<String, File> fileStore = new ConcurrentHashMap<>();

    @Resource
    private FileStorageService fileStorageService;

    public FileUploadService() {
        this.uploadDir = System.getProperty("user.dir") + File.separator + "uploads";
        initUploadDir();
    }

    private void initUploadDir() {
        File dir = new File(uploadDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * 上传文件并返回 fileId。
     *
     * 优先使用统一存储服务；未配置时回退到本地磁盘。
     */
    public String upload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            originalFilename = "unknown";
        }
        // 只替换文件系统非法字符，保留中文、空格等原文件名信息
        String safeFilename = originalFilename.replaceAll("[\\\\/:*?\"<>|]", "_");
        String fileId = UUID.randomUUID().toString() + "_" + safeFilename;

        File tempFile = new File(uploadDir, fileId);
        file.transferTo(tempFile);
        try {
            String storageFileId = fileStorageService.upload(tempFile, "editor/" + fileId);
            if (!fileStorageService.exists(storageFileId)) {
                throw new RuntimeException("文件上传后存储中不存在：" + storageFileId);
            }
            // 上传成功后删除本地临时文件，减轻磁盘压力
            Files.deleteIfExists(tempFile.toPath());
            log.info("文件上传成功：storageFileId={}", storageFileId);
            return storageFileId;
        } catch (Exception e) {
            log.error("统一存储上传失败，回退到本地磁盘：fileId={}", fileId, e);
            // 回退：保留本地文件，fileId 即本地文件名
            fileStore.put(fileId, tempFile);
            return fileId;
        }
    }

    /**
     * 根据 fileId 获取文件。
     *
     * 如果 fileId 是历史遗留的本地文件名，直接从本地读取；
     * 否则从统一存储下载到临时文件返回。
     */
    public File getFile(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return null;
        }
        // 历史本地文件回退
        File localFile = Paths.get(uploadDir, fileId).toFile();
        if (localFile.exists()) {
            return localFile;
        }
        // 缓存命中
        File cached = fileStore.get(fileId);
        if (cached != null && cached.exists()) {
            return cached;
        }
        // 从统一存储下载到临时文件
        try (InputStream input = fileStorageService.openInputStream(fileId)) {
            if (input == null) {
                return null;
            }
            Path tempPath = Files.createTempFile("mokaTest-download-", "_" + extractSuffix(fileId));
            Files.copy(input, tempPath, StandardCopyOption.REPLACE_EXISTING);
            File tempFile = tempPath.toFile();
            fileStore.put(fileId, tempFile);
            return tempFile;
        } catch (Exception e) {
            log.error("从统一存储获取文件失败：fileId={}", fileId, e);
            return null;
        }
    }

    /**
     * 根据 fileId 删除文件。
     */
    public boolean delete(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return false;
        }
        try {
            boolean deleted = fileStorageService.delete(fileId);
            File cached = fileStore.remove(fileId);
            if (cached != null && cached.exists()) {
                cached.delete();
            }
            return deleted;
        } catch (Exception e) {
            log.error("删除文件失败：fileId={}", fileId, e);
            return false;
        }
    }

    /**
     * 从 fileId 中提取原始文件名。
     * fileId 格式为 [path/]UUID_原文件名，去掉 UUID_ 前缀即可还原。
     */
    public String getOriginalFilename(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return fileId;
        }
        int lastSlash = Math.max(fileId.lastIndexOf('/'), fileId.lastIndexOf('\\'));
        String baseName = lastSlash >= 0 ? fileId.substring(lastSlash + 1) : fileId;
        // 去掉标准的 UUID 前缀（xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx_）
        return baseName.replaceFirst(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}_", "");
    }

    private String extractSuffix(String fileId) {
        if (fileId == null || fileId.isEmpty()) {
            return "";
        }
        int lastDot = fileId.lastIndexOf('.');
        if (lastDot >= 0) {
            return fileId.substring(lastDot);
        }
        return "";
    }
}
