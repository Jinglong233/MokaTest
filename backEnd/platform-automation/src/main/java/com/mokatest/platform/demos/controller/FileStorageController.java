package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 通用文件存储接口。
 *
 * 根据当前配置的 FileStorageService 返回文件的预览 URL 或下载内容。
 * 前端无需感知底层是本地磁盘、OSS/COS/S3/MinIO 中的哪一种。
 */
@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileStorageController {

    private final FileStorageService fileStorageService;

    /**
     * 获取文件预览 URL。
     * 例如图片场景：local 返回后端代理地址，MinIO 返回直链/代理地址，OSS 返回签名 URL。
     */
    @GetMapping("/preview")
    public SaResult getPreviewUrl(@RequestParam String fileId) {
        String url = fileStorageService.getPreviewUrl(fileId);
        return SaResult.data(url);
    }

    /**
     * 下载指定文件。
     */
    @GetMapping("/download")
    public void download(@RequestParam String fileId, HttpServletResponse response) {
        fileStorageService.download(fileId, response);
    }

    /**
     * 下载 trace 文件（专供 trace.playwright.dev 跨域加载）。
     * 设置 CORS 头，使 trace.playwright.dev 可以直接通过 URL 加载，
     * 不管底层存储是本地/MinIO/OSS/COS 都统一走 FileStorageService 代理。
     */
    @GetMapping("/trace")
    public void downloadTrace(@RequestParam String fileId, HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET");
        response.setHeader("Content-Type", "application/zip");
        fileStorageService.download(fileId, response);
    }
}
