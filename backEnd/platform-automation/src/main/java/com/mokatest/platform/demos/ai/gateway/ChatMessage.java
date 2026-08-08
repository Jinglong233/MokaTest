package com.mokatest.platform.demos.ai.gateway;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话消息（OpenAI 兼容）
 */
@Data
public class ChatMessage {

    /** 角色：system / user / assistant */
    private String role;

    /** 文本内容（纯文本场景） */
    private String content;

    /** 多模态内容（含图片时）：[{type:text,...},{type:image_url,...}]，与 content 二选一 */
    private List<ContentPart> parts;

    public ChatMessage() {
    }

    public ChatMessage(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }

    /** 构造多模态用户消息：文本 + 图片 url 列表 */
    public static ChatMessage userWithImages(String text, List<String> imageUrls) {
        ChatMessage msg = new ChatMessage();
        msg.setRole("user");
        List<ContentPart> parts = new ArrayList<>();
        ContentPart textPart = new ContentPart();
        textPart.setType("text");
        textPart.setText(text);
        parts.add(textPart);
        if (imageUrls != null) {
            for (String url : imageUrls) {
                ContentPart img = new ContentPart();
                img.setType("image_url");
                ImageUrl imageUrl = new ImageUrl();
                imageUrl.setUrl(url);
                img.setImageUrl(imageUrl);
                parts.add(img);
            }
        }
        msg.setParts(parts);
        return msg;
    }

    @Data
    public static class ContentPart {
        private String type;
        private String text;
        private ImageUrl imageUrl;
    }

    @Data
    public static class ImageUrl {
        private String url;
    }
}
