package com.mokatest.platform.demos.storage;

import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.InputStream;

/**
 * 统一文件存储接口。
 *
 * 支持本地磁盘、超星云盘、阿里云 OSS、腾讯云 COS、AWS S3、MinIO 等多种后端。
 * 所有实现对外返回统一的 fileId，调用方不感知底层存储差异。
 */
public interface FileStorageService {

    /**
     * 上传文件。
     *
     * @param file 待上传文件
     * @param path 建议的存储路径/前缀（具体含义由实现决定）
     * @return 存储标识 fileId，可用于后续下载/预览
     */
    String upload(File file, String path);

    /**
     * 上传字节数组。
     *
     * @param data 文件字节
     * @param path 建议的存储路径/前缀
     * @return 存储标识 fileId
     */
    String upload(byte[] data, String path);

    /**
     * 将指定 fileId 的文件下载到 response。
     *
     * @param fileId   存储标识
     * @param response HTTP 响应
     */
    void download(String fileId, HttpServletResponse response);

    /**
     * 获取预览 URL（如图片 CDN 地址）。
     * 不保证所有实现都支持，本地存储可返回后端代理地址。
     *
     * @param fileId 存储标识
     * @return 可直接访问的 URL，不支持时返回 null
     */
    String getPreviewUrl(String fileId);

    /**
     * 获取直链下载 URL。
     * 私有 bucket 的实现需要生成临时签名 URL。
     *
     * @param fileId 存储标识
     * @return 下载 URL，不支持时返回 null
     */
    String getDownloadUrl(String fileId);

    /**
     * 删除文件。
     *
     * @param fileId 存储标识
     * @return 是否删除成功
     */
    boolean delete(String fileId);

    /**
     * 打开文件输入流，用于需要读取文件内容的场景。
     *
     * @param fileId 存储标识
     * @return 文件输入流；不存在或失败时返回 null
     */
    InputStream openInputStream(String fileId);

    /**
     * 判断文件是否存在。
     *
     * @param fileId 存储标识
     * @return true / false
     */
    boolean exists(String fileId);
}
