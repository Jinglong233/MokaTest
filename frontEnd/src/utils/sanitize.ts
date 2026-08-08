/**
 * 轻量 HTML 消毒（白名单方式，无第三方依赖）
 * 用于渲染操作日志等用户输入的富文本，防存储型 XSS
 */

const ALLOWED_TAGS = new Set([
    'p', 'br', 'span', 'div', 'b', 'strong', 'i', 'em', 'u', 's', 'del',
    'ul', 'ol', 'li', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
    'blockquote', 'pre', 'code', 'table', 'thead', 'tbody', 'tr', 'td', 'th',
    'img', 'a', 'hr', 'font', 'sub', 'sup',
]);

const ALLOWED_ATTRS = new Set([
    'href', 'src', 'alt', 'title', 'colspan', 'rowspan', 'style', 'color',
]);

const URI_ATTRS = new Set(['href', 'src']);

function sanitizeNode(node: Element): void {
    // 先处理子节点（倒序，避免移除时索引错乱）
    Array.from(node.children).forEach((child) => {
        const tag = child.tagName.toLowerCase();
        if (!ALLOWED_TAGS.has(tag)) {
            // 脚本/样式/事件载体等直接移除（含其内容）
            child.remove();
            return;
        }
        // 清理属性
        Array.from(child.attributes).forEach((attr) => {
            const name = attr.name.toLowerCase();
            if (name.startsWith('on') || !ALLOWED_ATTRS.has(name)) {
                child.removeAttribute(attr.name);
                return;
            }
            if (URI_ATTRS.has(name) && /^\s*javascript:/i.test(attr.value)) {
                child.removeAttribute(attr.name);
            }
        });
        // a 标签强制 noopener
        if (tag === 'a') {
            child.setAttribute('rel', 'noopener noreferrer');
            child.setAttribute('target', '_blank');
        }
        sanitizeNode(child);
    });
}

export function sanitizeHtml(html: string): string {
    if (!html) return '';
    const doc = new DOMParser().parseFromString(html, 'text/html');
    sanitizeNode(doc.body);
    return doc.body.innerHTML;
}
