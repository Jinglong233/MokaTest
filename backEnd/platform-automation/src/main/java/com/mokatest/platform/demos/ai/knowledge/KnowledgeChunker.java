package com.mokatest.platform.demos.ai.knowledge;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库分块器：纯文本/markdown → 分块（目标 ~500 tokens/块）
 *
 * 规则：
 * 1. 先按 Markdown 标题行（#{1,6} 开头）切段，标题与正文保持同段
 * 2. 段内按空行分段落
 * 3. 段落贪心合并到目标长度；单段超长则按字符硬切
 * token 粗算与 ContextBlock.estimateTokens 一致（2 字符 ≈ 1 token）
 */
public final class KnowledgeChunker {

    /** 目标块长（字符，≈500 tokens） */
    public static final int TARGET_CHARS = 1000;

    private KnowledgeChunker() {
    }

    public static List<String> chunk(String content) {
        List<String> result = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return result;
        }
        // 1. 切基础块：标题行起新块，空行分块
        List<String> blocks = splitBlocks(content);
        // 2. 贪心合并 + 超长硬切
        StringBuilder current = new StringBuilder();
        for (String block : blocks) {
            if (block.length() > TARGET_CHARS) {
                // 先Flush当前累积，再硬切长块
                flush(current, result);
                for (int i = 0; i < block.length(); i += TARGET_CHARS) {
                    result.add(block.substring(i, Math.min(i + TARGET_CHARS, block.length())).trim());
                }
                continue;
            }
            if (current.length() + block.length() + 2 > TARGET_CHARS && current.length() > 0) {
                flush(current, result);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(block);
        }
        flush(current, result);
        return result;
    }

    private static void flush(StringBuilder current, List<String> result) {
        String text = current.toString().trim();
        if (!text.isEmpty()) {
            result.add(text);
        }
        current.setLength(0);
    }

    /** 按标题行与空行切基础块 */
    private static List<String> splitBlocks(String content) {
        List<String> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean previousBlank = false;
        for (String line : content.split("\n", -1)) {
            String trimmed = line.trim();
            boolean blank = trimmed.isEmpty();
            boolean heading = trimmed.matches("^#{1,6}\\s+.*");
            if (heading && current.length() > 0) {
                blocks.add(current.toString().trim());
                current.setLength(0);
            } else if (blank && previousBlank && current.length() > 0) {
                // 连续空行 = 段落分隔
                blocks.add(current.toString().trim());
                current.setLength(0);
                previousBlank = true;
                continue;
            }
            if (blank) {
                previousBlank = true;
                continue;
            }
            previousBlank = false;
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(line);
        }
        if (current.length() > 0 && !current.toString().trim().isEmpty()) {
            blocks.add(current.toString().trim());
        }
        return blocks;
    }

    /** 估算 token 数（与 ContextBlock.estimateTokens 同规则） */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, text.length() / 2);
    }

    /**
     * 富文本 HTML → 纯文本（块级标签转换行、去标签、解常见实体）
     * 编辑器产出 HTML，索引/分块/检索统一在纯文本上进行
     */
    public static String toPlainText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String text = html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li|tr|table|ul|ol|blockquote|pre)>", "\n")
                .replaceAll("(?i)<li[^>]*>", "- ")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&amp;", "&");
        // 收敛多余空行
        return text.replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
