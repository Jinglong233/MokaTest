package com.mokatest.platform.demos.util;

import com.microsoft.playwright.Page;

import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScreenshotUtils {

    /**
     * 将页面截图自动保存到 resources/screenshots 目录
     *
     * @param page   Playwright Page 对象
     * @param prefix 文件名前缀
     * @return 保存后的文件完整路径
     */
    public static String saveToResourcesScreenshots(Page page, String prefix) {
        try {
            // 获取 resources 目录路径
            String resourcesPath = ScreenshotUtils.class.getClassLoader().getResource("").getPath();
            if (resourcesPath.startsWith("/") && System.getProperty("os.name").toLowerCase().contains("win")) {
                resourcesPath = resourcesPath.substring(1);
            }

            String screenshotDir = Paths.get(resourcesPath, "screenshots").toString();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String fileName = (prefix != null ? prefix + "-" : "") + timestamp + ".png";

            String fullPath = Paths.get(screenshotDir, fileName).toString();
            page.screenshot(new Page.ScreenshotOptions().setPath(Paths.get(fullPath)));

            return fullPath;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}