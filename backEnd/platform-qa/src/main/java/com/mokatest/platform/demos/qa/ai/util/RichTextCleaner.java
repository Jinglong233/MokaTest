package com.mokatest.platform.demos.qa.ai.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 富文本清洗：需求描述（HTML）→ 模型可读文本
 *
 * 规则（对齐设计文档三档处理）：
 * - 默认：HTML 转纯文本，<img> 替换为 [图片] 占位
 * - 多模态开启：由调用方用 extractImageUrls 取图片地址走视觉模型
 */
public class RichTextCleaner {

    private static final Pattern IMG_PATTERN = Pattern.compile("<img[^>]*src=[\"']([^\"']+)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");

    /**
     * HTML → 纯文本（img 替换为 [图片] 占位）
     */
    public static String toPlainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = IMG_PATTERN.matcher(html).replaceAll("[图片]");
        // 块级标签转换行，保留基本结构
        text = text.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("(?i)</h[1-6]>", "\n");
        text = TAG_PATTERN.matcher(text).replaceAll("");
        // 常见实体
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"");
        // 折叠多余空行
        return text.replaceAll("\\n{3,}", "\n\n").trim();
    }

    /**
     * 提取富文本中的图片地址（多模态用）
     */
    public static List<String> extractImageUrls(String html) {
        List<String> urls = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return urls;
        }
        Matcher matcher = IMG_PATTERN.matcher(html);
        while (matcher.find()) {
            urls.add(matcher.group(1));
        }
        return urls;
    }
}
