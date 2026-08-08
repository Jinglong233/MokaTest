package com.mokatest.platform.demos.api.script;

import cn.hutool.crypto.digest.DigestUtil;
import com.mokatest.platform.demos.util.DataTemplateFunctionExecutor;
import com.mokatest.platform.demos.util.MockDataGenerator;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 脚本内置工具函数（单例模式）
 *
 * 在脚本中通过 context.utils.xxx() 调用：
 *   context.utils.md5("hello")           // → "5d41402abc4b2a76b9719d911017c592"
 *   context.utils.base64Encode("hello")  // → "aGVsbG8="
 *   context.utils.uuid()                 // → "550e8400-e29b-41d4-a716-446655440000"
 *   context.utils.randomString(8)        // → "aB3dE7fG"
 *   context.utils.timestamp()            // → 1716789012345
 */
public class ScriptUtils {

    private static final ScriptUtils INSTANCE = new ScriptUtils();

    private ScriptUtils() {}

    public static ScriptUtils getInstance() {
        return INSTANCE;
    }

    /**
     * MD5 加密
     */
    public String md5(String input) {
        if (input == null) return null;
        return DigestUtil.md5Hex(input);
    }

    /**
     * SHA1 加密
     */
    public String sha1(String input) {
        if (input == null) return null;
        return DigestUtil.sha1Hex(input);
    }

    /**
     * SHA256 加密
     */
    public String sha256(String input) {
        if (input == null) return null;
        return DigestUtil.sha256Hex(input);
    }

    /**
     * Base64 编码
     */
    public String base64Encode(String input) {
        if (input == null) return null;
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Base64 解码
     */
    public String base64Decode(String input) {
        if (input == null) return null;
        return new String(Base64.getDecoder().decode(input), StandardCharsets.UTF_8);
    }

    /**
     * URL 编码
     */
    public String urlEncode(String input) {
        if (input == null) return null;
        return java.net.URLEncoder.encode(input, StandardCharsets.UTF_8);
    }

    /**
     * URL 解码
     */
    public String urlDecode(String input) {
        if (input == null) return null;
        return java.net.URLDecoder.decode(input, StandardCharsets.UTF_8);
    }

    /**
     * 生成 UUID
     */
    public String uuid() {
        return UUID.randomUUID().toString();

    }

    /**
     * 生成随机字符串（字母+数字）
     * @param length 字符串长度
     */
    public String randomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * 生成时间戳（毫秒）
     */
    public long timestamp() {
        return System.currentTimeMillis();
    }

    /**
     * 当前日期时间格式化
     * @param pattern 格式，如 "yyyy-MM-dd HH:mm:ss"
     */
    public String now(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            pattern = "yyyy-MM-dd HH:mm:ss";
        }
        return new java.text.SimpleDateFormat(pattern).format(new java.util.Date());
    }

    /**
     * 生成 Mock 数据
     * 用法：context.utils.mock("phone") / context.utils.mock("int", 1, 100)
     */
    public String mock(String type, Object... args) {
        StringBuilder params = new StringBuilder(type);
        for (Object arg : args) {
            params.append(", ").append(arg);
        }
        return MockDataGenerator.generate(params.toString());
    }

    /**
     * 根据数据模板生成单条数据（JSON 字符串）
     */
    public String template(int templateId) {
        return DataTemplateFunctionExecutor.generate(String.valueOf(templateId));
    }

    /**
     * 根据数据模板批量生成数据（JSON 字符串）
     */
    public String templateBatch(int templateId, int count) {
        return DataTemplateFunctionExecutor.batchGenerate(templateId + ", " + count);
    }

    /**
     * 调用用户自定义公共函数
     * 用法：context.utils.custom(123, "a", 1)  // → 函数执行结果的字符串形式
     * 执行失败时返回空字符串并向控制台输出错误（可通过 context.log 观察）
     */
    public String custom(int functionId, Object... args) {
        com.mokatest.platform.demos.util.CustomFunctionExecutor.RunResult result =
                com.mokatest.platform.demos.util.CustomFunctionExecutor.executeById(
                        functionId,
                        args != null ? java.util.Arrays.asList(args) : java.util.List.of());
        return result.isSuccess() ? result.getValue() : "";
    }

    /**
     * JSON 字符串转对象（在 JS 中可用 JSON.parse，此方法作为备用）
     */
    public Object parseJson(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return com.alibaba.fastjson.JSON.parse(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 对象转 JSON 字符串（在 JS 中可用 JSON.stringify，此方法作为备用）
     */
    public String toJson(Object obj) {
        if (obj == null) return "null";
        return com.alibaba.fastjson.JSON.toJSONString(obj);
    }
}
