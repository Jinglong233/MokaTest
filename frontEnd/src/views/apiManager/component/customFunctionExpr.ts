/**
 * 自定义函数调用表达式工具
 *
 * 存储层：{{__CUSTOM(5, 'a', 'b')}}（id 引用，改名不失效）
 * 显示层：@fn.请求签名('a', 'b')（名称仅用于展示，不参与解析；脚本里写 fn.请求签名(...)）
 */

export interface CustomExpr {
    id: number;
    args: string[];
}

/** 整个值恰好是一个 CUSTOM 表达式时才匹配（嵌入在长文本里的不做标签化渲染） */
const CUSTOM_EXPR_FULL_RE = /^\s*\{\{__CUSTOM\(\s*(\d+)\s*(?:,([\s\S]*?))?\)\s*__\}\}\s*$/;

/**
 * 解析参数值：整个值是一个 {{__CUSTOM(id, ...)}} 表达式时返回 {id, args}，否则返回 null
 */
export const parseCustomExpr = (value: unknown): CustomExpr | null => {
    if (typeof value !== 'string') return null;
    const m = value.match(CUSTOM_EXPR_FULL_RE);
    if (!m) return null;
    const id = Number(m[1]);
    if (!Number.isFinite(id) || id <= 0) return null;
    const rawArgs = (m[2] || '').trim();
    const args = rawArgs ? splitTopLevelArgs(rawArgs).map(stripQuotes) : [];
    return {id, args};
};

/**
 * 生成底层调用表达式：{{__CUSTOM(5, 'a', 'b')}}
 */
export const buildCustomExpr = (id: number, args: string[]): string => {
    const parts = (args || []).map(a => `'${String(a ?? '').replace(/'/g, "\\'")}'`);
    return `{{__CUSTOM(${id}${parts.length ? ', ' + parts.join(', ') : ''})__}}`;
};

/**
 * 生成显示文本：@fn.请求签名('a')（文本字段统一 @ 前缀；脚本里用 fn.请求签名('a')）
 */
export const buildFnDisplay = (funcName: string | undefined, id: number, args: string[]): string => {
    const name = funcName || `已失效:${id}`;
    const parts = (args || []).map(a => `'${a}'`);
    return `@fn.${name}(${parts.join(', ')})`;
};

/** 按顶层逗号切分（尊重单/双引号内的逗号），与后端 CustomFunctionExecutor.splitArgs 行为一致 */
export const splitTopLevelArgs = (raw: string): string[] => {
    const tokens: string[] = [];
    let current = '';
    let quote: string | null = null;
    for (const c of raw) {
        if (quote === null && (c === "'" || c === '"')) {
            quote = c;
            current += c;
        } else if (quote !== null && c === quote) {
            quote = null;
            current += c;
        } else if (quote === null && c === ',') {
            tokens.push(current.trim());
            current = '';
        } else {
            current += c;
        }
    }
    if (current.trim() !== '') tokens.push(current.trim());
    return tokens;
};

const stripQuotes = (token: string): string => {
    if (token.length >= 2) {
        const first = token[0];
        const last = token[token.length - 1];
        if ((first === "'" && last === "'") || (first === '"' && last === '"')) {
            return token.slice(1, -1);
        }
    }
    return token;
};

// ==================== 混合文本双向转换 ====================
// 存储层：...{{__CUSTOM(5, 'a')}}...（可嵌在任意文本中、可多个）
// 显示层：...@fn.请求签名('a')...（输入框里看到/编辑的形式；兼容旧的 fn. 写法）

const STORAGE_PREFIX = '{{__CUSTOM(';
const STORAGE_SUFFIX = ')__}}';
const DISPLAY_PREFIX = '@fn.';
/** 旧显示前缀（脚本语法同款），toStorageText 兼容识别 */
const LEGACY_DISPLAY_PREFIX = 'fn.';

/**
 * 存储文本 → 显示文本：把 {{__CUSTOM(id, args)}} 逐段替换为 fn.名称(args)。
 * id 解析不到名称（函数被删/跨项目）时保留原文，避免往返转换丢数据。
 */
export const toDisplayText = (text: string, resolveName: (id: number) => string | undefined): string => {
    if (!text || !text.includes(STORAGE_PREFIX)) return text;
    let result = '';
    let cursor = 0;
    while (cursor < text.length) {
        const start = text.indexOf(STORAGE_PREFIX, cursor);
        if (start < 0) {
            result += text.slice(cursor);
            break;
        }
        const contentStart = start + STORAGE_PREFIX.length;
        const end = findStorageEnd(text, contentStart);
        if (end < 0) {
            // 没有闭合，原样保留剩余文本
            result += text.slice(cursor);
            break;
        }
        const inner = text.slice(contentStart, end);
        const expr = parseCustomExprInner(inner);
        const name = expr ? resolveName(expr.id) : undefined;
        result += text.slice(cursor, start);
        if (expr && name) {
            const argsText = expr.args.map(a => `'${a}'`).join(', ');
            result += `${DISPLAY_PREFIX}${name}(${argsText})`;
        } else {
            result += text.slice(start, end + STORAGE_SUFFIX.length);
        }
        cursor = end + STORAGE_SUFFIX.length;
    }
    return result;
};

export interface StorageConvertResult {
    text: string;
    /** 解析不到的函数名（原文保留未转换） */
    unknownNames: string[];
}

/**
 * 显示文本 → 存储文本：把 @fn.名称(args)（兼容 fn.名称(args)）逐段解析回 {{__CUSTOM(id, args)}}。
 * 名称解析不到时保留原文并收集到 unknownNames（调用方提示用户）。
 * 边界要求：@fn./fn. 前面不能紧跟标识符字符（避免误伤 "xfn.xxx()" 这类普通文本）。
 */
export const toStorageText = (text: string, resolveId: (name: string) => number | undefined): StorageConvertResult => {
    const unknownNames: string[] = [];
    if (!text || !text.includes(LEGACY_DISPLAY_PREFIX)) return {text, unknownNames};
    let result = '';
    let cursor = 0;
    while (cursor < text.length) {
        // 找下一个 'fn.'；若前一个字符是 '@' 则按 '@fn.' 整体处理
        let start = text.indexOf(LEGACY_DISPLAY_PREFIX, cursor);
        if (start < 0) {
            result += text.slice(cursor);
            break;
        }
        let prefix = LEGACY_DISPLAY_PREFIX;
        if (start > 0 && text[start - 1] === '@') {
            start -= 1;
            prefix = DISPLAY_PREFIX;
        }
        // 边界检查：前一个字符是字母/数字/_/$/. 时视为普通文本（如 xfn.foo()）
        if (start > 0 && /[\w$.]/.test(text[start - 1])) {
            result += text.slice(cursor, start + prefix.length);
            cursor = start + prefix.length;
            continue;
        }
        const contentStart = start + prefix.length;
        // 函数名：到第一个 ( 为止
        const parenStart = findNextParenOpen(text, contentStart);
        if (parenStart < 0) {
            result += text.slice(cursor);
            break;
        }
        const name = text.slice(contentStart, parenStart).trim();
        const end = findMatchingParen(text, parenStart + 1);
        if (!name || end < 0) {
            result += text.slice(cursor, contentStart);
            cursor = contentStart;
            continue;
        }
        const inner = text.slice(parenStart + 1, end);
        const id = resolveId(name);
        result += text.slice(cursor, start);
        if (id != null) {
            const args = inner.trim() ? splitTopLevelArgs(inner).map(stripQuotes) : [];
            result += buildCustomExpr(id, args);
        } else {
            if (!unknownNames.includes(name)) unknownNames.push(name);
            result += text.slice(start, end + 1);
        }
        cursor = end + 1;
    }
    return {text: result, unknownNames};
};

/** 找下一个左括号位置（函数名不允许含括号/引号，直接查找即可） */
const findNextParenOpen = (text: string, from: number): number => {
    const idx = text.indexOf('(', from);
    return idx;
};

/** 在 {{__CUSTOM( 内容里找闭合的 )__}}（引号感知） */
const findStorageEnd = (text: string, from: number): number => {
    let quote: string | null = null;
    for (let i = from; i < text.length; i++) {
        const c = text[i];
        if (quote === null && (c === "'" || c === '"')) {
            quote = c;
        } else if (quote !== null && c === quote) {
            quote = null;
        } else if (quote === null && c === ')' && text.startsWith('__}}', i + 1)) {
            return i;
        }
    }
    return -1;
};

/** 在 fn.名称( 内容里找匹配的右括号（引号感知 + 嵌套括号计数） */
const findMatchingParen = (text: string, from: number): number => {
    let quote: string | null = null;
    let depth = 1;
    for (let i = from; i < text.length; i++) {
        const c = text[i];
        if (quote === null && (c === "'" || c === '"')) {
            quote = c;
        } else if (quote !== null && c === quote) {
            quote = null;
        } else if (quote === null) {
            if (c === '(') depth++;
            else if (c === ')') {
                depth--;
                if (depth === 0) return i;
            }
        }
    }
    return -1;
};

/** 解析 {{__CUSTOM( 内部内容："5, 'a', 'b'" → {id: 5, args: ['a','b']} */
const parseCustomExprInner = (inner: string): CustomExpr | null => {
    const segments = splitTopLevelArgs(inner);
    if (!segments.length) return null;
    const id = Number(segments[0]);
    if (!Number.isFinite(id) || id <= 0) return null;
    return {id, args: segments.slice(1).map(stripQuotes)};
};
