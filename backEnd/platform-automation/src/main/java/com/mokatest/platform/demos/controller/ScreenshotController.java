package com.mokatest.platform.demos.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/screenshots")
public class ScreenshotController {

    @GetMapping("/{filename}")
    public ResponseEntity<InputStream> getScreenshot(@PathVariable String filename) {
        
        // 1. 构建资源路径。注意：路径以 "classpath:" 开头，且不需要 "src/main/resources"
        String resourcePath = "screenshots/" + filename; // 直接指向 resources/screenshots 目录下的文件
        
        // 2. 使用 ClassPathResource 加载资源
        Resource resource = new ClassPathResource(resourcePath);
        
        // 3. 检查资源是否存在
        if (!resource.exists()) {
            // 如果图片不存在，返回404状态码
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Screenshot not found: " + filename);
        }
        
        try {
            // 4. 获取资源的输入流
            InputStream inputStream = resource.getInputStream();
            
            // 5. 返回响应
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_PNG) // 设置为图片类型
                    .body(inputStream); // 直接返回输入流，Spring会负责处理流的关闭

        } catch (IOException e) {
            // 如果读取发生错误，返回500状态码
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read screenshot", e);
        }
    }
}