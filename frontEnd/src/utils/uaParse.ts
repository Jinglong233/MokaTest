/**
 * User-Agent 简易解析（登录日志列表展示用）
 * 无需第三方依赖，覆盖主流浏览器/操作系统即可，识别不出返回「其他」
 */

export function parseBrowser(ua?: string): string {
    if (!ua) return '-';
    // 顺序敏感：Edge/Opera 的 UA 里也含 Chrome/Safari 关键字，需先判
    const rules: Array<[RegExp, string]> = [
        [/Edg(?:e|A|iOS)?\/([\d.]+)/, 'Edge'],
        [/OPR\/([\d.]+)/, 'Opera'],
        [/Chrome\/([\d.]+)/, 'Chrome'],
        [/Firefox\/([\d.]+)/, 'Firefox'],
        [/Version\/([\d.]+).*Safari/, 'Safari'],
        [/MSIE ([\d.]+)/, 'IE'],
        [/Trident\/.*rv:([\d.]+)/, 'IE'],
        [/MicroMessenger\/([\d.]+)/, '微信'],
        [/PostmanRuntime\/([\d.]+)/, 'Postman'],
        [/curl\/([\d.]+)/, 'curl'],
        [/okhttp\/([\d.]+)/, 'OkHttp'],
    ];
    for (const [regex, name] of rules) {
        const m = ua.match(regex);
        if (m) {
            const major = m[1] ? m[1].split('.')[0] : '';
            return major ? `${name} ${major}` : name;
        }
    }
    return '其他';
}

export function parseOs(ua?: string): string {
    if (!ua) return '-';
    const rules: Array<[RegExp, string]> = [
        [/Windows NT 10\.0/, 'Windows 10/11'],
        [/Windows NT 6\.3/, 'Windows 8.1'],
        [/Windows NT 6\.1/, 'Windows 7'],
        [/Windows/, 'Windows'],
        [/iPhone|iPad/, 'iOS'],
        [/Android/, 'Android'],
        [/Mac OS X/, 'macOS'],
        [/Linux/, 'Linux'],
    ];
    for (const [regex, name] of rules) {
        if (regex.test(ua)) return name;
    }
    return '其他';
}
