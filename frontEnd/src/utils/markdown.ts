import {marked} from 'marked';
import {sanitizeHtml} from './sanitize';

/**
 * Markdown 渲染（AI 对话内容用）
 *
 * marked 解析 → sanitizeHtml 白名单消毒，防存储型 XSS。
 * breaks: true 让单个换行也渲染为 <br>，贴合聊天消息习惯。
 */
marked.setOptions({gfm: true, breaks: true});

export function renderMarkdown(text: string): string {
    if (!text) {
        return '';
    }
    try {
        const html = marked.parse(text, {async: false}) as string;
        return sanitizeHtml(html);
    } catch {
        // 解析失败退化为转义纯文本（保留换行）
        const escaped = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
        return `<p>${escaped.replace(/\n/g, '<br>')}</p>`;
    }
}
