package com.mokatest.platform.demos.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.service.FileUploadService;
import com.mokatest.platform.demos.storage.FileStorageService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件上传接口。
 *
 * 提供临时文件上传、富文本图片上传/回显/删除能力。
 * 底层统一使用 {@link FileStorageService}，支持本地磁盘、MinIO、OSS、COS 等存储后端。
 */
@RestController
@RequestMapping("/file")
public class FileUploadController {

    @Resource
    private FileUploadService fileUploadService;

    @Resource
    private FileStorageService fileStorageService;

    /**
     * 上传临时文件
     *
     * @param file 文件
     * @return fileId 与原始文件名
     */
    @SaCheckPermission(value = {"auto:api:update", "auto:scene:update", "auto:step:update"}, mode = SaMode.OR)
    @PostMapping("/upload")
    public SaResult upload(@RequestParam("file") MultipartFile file) {
        try {
            // 文件大小校验：最大 10MB
            if (file.getSize() > 10 * 1024 * 1024) {
                return SaResult.error("文件大小不能超过 10MB");
            }
            String fileId = fileUploadService.upload(file);
            Map<String, String> data = new HashMap<>();
            data.put("fileId", fileId);
            data.put("fileName", fileUploadService.getOriginalFilename(fileId));
            return SaResult.ok("上传成功").setData(data);
        } catch (IOException e) {
            return SaResult.error("文件上传失败：" + e.getMessage());
        } catch (IllegalArgumentException e) {
            return SaResult.error(e.getMessage());
        }
    }

    /**
     * 删除富文本图片。
     *
     * @param fileIds 需要删除的 fileId 列表
     * @return 删除结果
     */
    @PostMapping("/deleteImage")
    public SaResult deleteImage(@RequestBody List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return SaResult.ok("无文件需要删除");
        }
        int successCount = 0;
        int failCount = 0;
        for (String fileId : fileIds) {
            if (fileStorageService.delete(fileId)) {
                successCount++;
            } else {
                failCount++;
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("successCount", successCount);
        data.put("failCount", failCount);
        return SaResult.ok("删除完成").setData(data);
    }

    /**
     * 上传富文本图片。
     *
     * @param file 图片文件
     * @return wangEditor 要求的 { errno, data: { url, alt, href } }
     */
    @PostMapping("/uploadImage")
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            String fileId = fileUploadService.upload(file);
            String fileName = fileUploadService.getOriginalFilename(fileId);
            Map<String, Object> data = new HashMap<>();
            // 统一返回后端代理下载地址，前端无跨域，且兼容所有存储后端
            data.put("url", "/api/file/download?fileId=" + encodeFileId(fileId));
            data.put("alt", fileName);
            data.put("href", "");
            result.put("errno", 0);
            result.put("data", data);
            return result;
        } catch (IOException e) {
            result.put("errno", 1);
            result.put("message", "图片上传失败：" + e.getMessage());
            return result;
        } catch (IllegalArgumentException e) {
            result.put("errno", 1);
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * 获取上传的图片（用于富文本回显）。
     * 已废弃旧接口 /image/{fileId}，统一使用 /fileStorage/download?fileId=...
     *
     * @param fileId 文件ID
     * @param response HTTP 响应
     */
    @GetMapping("/image")
    public void image(@RequestParam String fileId, HttpServletResponse response) {
        fileStorageService.download(fileId, response);
    }

    private String encodeFileId(String fileId) {
        try {
            return java.net.URLEncoder.encode(fileId, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return fileId;
        }
    }
}
