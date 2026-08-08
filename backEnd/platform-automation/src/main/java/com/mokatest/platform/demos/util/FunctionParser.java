package com.mokatest.platform.demos.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 函数解析器类
 * 用于解析模板中的函数调用，如 {{__MD5(ABC)__}}
 * 备注：目前只支持【输入操作】使用内置函数
 */
public class FunctionParser {

    // 参数部分引号感知：允许引号内出现 )（如 @fixed('a)b')、{{__CUSTOM(5, 'x)y')}}）
    private static final String ARGS_PATTERN =
            "((?:[^()'\"]|\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*')*)";
    private static final Pattern FUNCTION_PATTERN =
            Pattern.compile("\\{\\{__([A-Za-z0-9]+)\\(" + ARGS_PATTERN + "\\)__\\}}");
    private static final Pattern AT_SYNTAX_PATTERN =
            Pattern.compile("@([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(" + ARGS_PATTERN + "\\)");
    private static final Random random = new Random();

    /**
     * 判断文本中是否包含公共函数调用
     *
     * @param text 原始文本
     * @return true 表示包含 {{__函数名(参数)__}} 格式
     */
    public static boolean containsFunction(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return FUNCTION_PATTERN.matcher(text).find()
                || AT_SYNTAX_PATTERN.matcher(text).find();
    }

    // 地区编码映射（简化的示例，实际需要完整的行政区划编码）
    private static final Map<String, String> AREA_CODES = new HashMap<>();

    static {
        // 初始化一些地区编码（这里只是示例，实际需要完整的行政区划编码表）
        AREA_CODES.put("北京市", "110000");
        AREA_CODES.put("台湾省", "710000");
        AREA_CODES.put("香港特别行政区", "810000");
        AREA_CODES.put("深圳市", "440300");
        AREA_CODES.put("黄浦区", "310101");
        // 可以添加更多地区
    }

    /**
     * 解析模板字符串中的函数调用
     *
     * @param template 包含函数调用的模板字符串
     * @return 解析后的字符串
     */
    public static String parse(String template) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        // 先解析 @ 语法
        String afterAt = parseAtSyntax(template);
        // 再解析旧版 {{__函数名(参数)__}} 语法
        return parseLegacyFunctions(afterAt);
    }

    private static String parseAtSyntax(String template) {
        Matcher matcher = AT_SYNTAX_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String functionName = matcher.group(1);
            String args = matcher.group(2);
            String replacement = AtSyntaxMockGenerator.generate(functionName, args);
            matcher.appendReplacement(result,
                    replacement != null ? Matcher.quoteReplacement(replacement) : "");
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String parseLegacyFunctions(String template) {
        Matcher matcher = FUNCTION_PATTERN.matcher(template);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String functionName = matcher.group(1);
            String params = matcher.group(2);
            String replacement = executeFunction(functionName, params);
            // quoteReplacement：函数结果含 $ 或 \ 时按字面量处理，避免 appendReplacement 抛异常/错位
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(replacement != null ? replacement : ""));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 公开入口：按函数名执行内置函数（供 @ 语法生成器代理 @md5() 等调用）
     */
    public static String executeBuiltin(String functionName, String params) {
        return executeFunction(functionName, params);
    }

    private static String executeFunction(String functionName, String params) {
        try {
            switch (functionName) {
                case "MD5":
                    return md5(params);
                case "SHA256":
                    return sha256(params);
                case "SHA512":
                    return sha512(params);
                case "IdCard":
                    return generateIdCard(params);
                case "RandomIdCard":
                    return randomIdCard();
                case "VerifyIdCard":
                    return verifyIdCard(params);
                case "ToStringLU":
                    return toStringLU(params);
                case "RandomInt":
                    return randomInt(params);
                case "RandomFloat0":
                    return randomFloat0();
                case "RandomString":
                    return randomString(params);
                case "GetUUid":
                case "Uuid":
                    return getUuid();
                case "MOCK":
                    return MockDataGenerator.generate(params);
                case "TEMPLATE":
                    return DataTemplateFunctionExecutor.generate(params);
                case "TEMPLATE_BATCH":
                    return DataTemplateFunctionExecutor.batchGenerate(params);
                case "CUSTOM":
                    return CustomFunctionExecutor.generate(params);
                case "ToTimeStamp":
                    return toTimeStamp(params);
                case "ToStandardTime":
                    return toStandardTime(params);
                default:
                    return "{{__" + functionName + "(" + params + ")__}}";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "{{__" + functionName + "(" + params + ")__}}";
        }
    }

    /**
     * MD5加密
     */
    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    /**
     * SHA256加密
     */
    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not found", e);
        }
    }

    /**
     * SHA512加密
     */
    private static String sha512(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(digest).toLowerCase();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-512 algorithm not found", e);
        }
    }

    /**
     * 生成身份证号
     * 参数格式: isEighteen, address, birthday, sex
     */
    private static String generateIdCard(String params) {
        String[] paramArray = params.split(",\\s*");
        if (paramArray.length < 4) {
            return "参数错误";
        }

        boolean isEighteen = Boolean.parseBoolean(paramArray[0].trim());
        String address = paramArray[1].trim();
        String birthday = paramArray[2].trim();
        int sex = Integer.parseInt(paramArray[3].trim());

        // 获取地区编码
        String areaCode = AREA_CODES.getOrDefault(address, "110000"); // 默认北京

        // 格式化生日
        String birthdayPart = formatBirthday(birthday);

        // 生成顺序码（3位，最后一位奇数为男，偶数为女）
        int sequenceCode = random.nextInt(999);
        if (sequenceCode < 100) {
            sequenceCode += 100;
        }
        if (sex == 1 && sequenceCode % 2 == 0) {
            sequenceCode++; // 确保男性为奇数
        } else if (sex == 0 && sequenceCode % 2 == 1) {
            sequenceCode++; // 确保女性为偶数
        }

        if (!isEighteen) {
            // 15位身份证：地区(6) + 生日(6) + 顺序(3)
            return areaCode.substring(0, 6) + birthdayPart.substring(2) +
                    String.format("%03d", sequenceCode);
        } else {
            // 18位身份证：地区(6) + 生日(8) + 顺序(3) + 校验码(1)
            String base = areaCode.substring(0, 6) + birthdayPart +
                    String.format("%03d", sequenceCode);
            return base + calculateCheckCode(base);
        }
    }

    /**
     * 随机生成身份证号
     */
    private static String randomIdCard() {
        // 随机选择参数
        boolean isEighteen = random.nextBoolean();
        String[] addresses = {"北京市", "上海市", "广州市", "深圳市", "杭州市"};
        String address = addresses[random.nextInt(addresses.length)];
        String birthday = String.format("%04d%02d%02d",
                1970 + random.nextInt(50),
                1 + random.nextInt(12),
                1 + random.nextInt(28));
        int sex = random.nextInt(2);

        return generateIdCard(isEighteen + ", " + address + ", " + birthday + ", " + sex);
    }

    /**
     * 验证身份证号
     * 参数格式: cardId, strict
     */
    private static String verifyIdCard(String params) {
        String[] paramArray = params.split(",\\s*");
        if (paramArray.length < 2) {
            return "false";
        }

        String cardId = paramArray[0].trim();
        boolean strict = Boolean.parseBoolean(paramArray[1].trim());

        // 简单的验证逻辑
        if (cardId.length() != 15 && cardId.length() != 18) {
            return "false";
        }

        if (cardId.length() == 18 && strict) {
            // 验证18位身份证的校验码
            String base = cardId.substring(0, 17);
            char checkCode = calculateCheckCode(base).charAt(0);
            if (checkCode != cardId.charAt(17)) {
                return "false";
            }
        }

        return "true";
    }

    /**
     * 计算18位身份证的校验码
     */
    private static String calculateCheckCode(String base) {
        if (base.length() != 17) {
            return "0";
        }

        int[] weight = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] validate = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (base.charAt(i) - '0') * weight[i];
        }

        int mod = sum % 11;
        return String.valueOf(validate[mod]);
    }

    /**
     * 格式化生日
     */
    private static String formatBirthday(String birthday) {
        if (birthday.length() == 4) {
            return birthday + "0101";
        } else if (birthday.length() == 6) {
            return birthday + "01";
        } else if (birthday.length() == 8) {
            return birthday;
        } else {
            // 默认2000年
            return "20000101";
        }
    }

    /**
     * 改变字符串大小写
     * 参数格式: input, caseType (L/U)
     */
    private static String toStringLU(String params) {
        String[] paramArray = params.split(",\\s*");
        if (paramArray.length < 2) {
            return params;
        }

        String input = paramArray[0].trim();
        String caseType = paramArray[1].trim().toUpperCase();

        if ("L".equals(caseType)) {
            return input.toLowerCase();
        } else if ("U".equals(caseType)) {
            return input.toUpperCase();
        } else {
            return input;
        }
    }

    /**
     * 生成随机整数
     * 参数格式: start, end
     */
    private static String randomInt(String params) {
        String[] paramArray = params.split(",\\s*");
        if (paramArray.length < 2) {
            return "0";
        }

        int start = Integer.parseInt(paramArray[0].trim());
        int end = Integer.parseInt(paramArray[1].trim());

        if (start > end) {
            int temp = start;
            start = end;
            end = temp;
        }

        return String.valueOf(start + random.nextInt(end - start + 1));
    }

    /**
     * 生成0-1之间的随机小数
     */
    private static String randomFloat0() {
        return String.format("%.6f", random.nextDouble());
    }

    /**
     * 生成随机字符串
     * 参数格式: length, type
     */
    private static String randomString(String params) {
        String[] paramArray = params.split(",\\s*");
        if (paramArray.length < 2) {
            return "";
        }

        int length = Integer.parseInt(paramArray[0].trim());
        int type = Integer.parseInt(paramArray[1].trim());

        StringBuilder sb = new StringBuilder();

        // 根据类型确定字符集
        String charset = getCharsetByType(type);

        for (int i = 0; i < length; i++) {
            int index = random.nextInt(charset.length());
            sb.append(charset.charAt(index));
        }

        return sb.toString();
    }

    /**
     * 根据类型获取字符集
     */
    private static String getCharsetByType(int type) {
        switch (type) {
            case 0: // a-z, 0-9, A-Z
                return "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            case 1: // a-z, A-Z
                return "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
            case 2: // a-z
                return "abcdefghijklmnopqrstuvwxyz";
            case 3: // A-Z
                return "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
            default:
                return "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        }
    }

    /**
     * 生成UUID
     */
    private static String getUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * 生成时间戳
     * 参数: s, ms, ns, ws
     */
    private static String toTimeStamp(String option) {
        long currentTime = System.currentTimeMillis();

        if (option == null || option.trim().isEmpty()) {
            option = "ms";
        }

        switch (option.trim()) {
            case "s":  // 秒
                return String.valueOf(currentTime / 1000);
            case "ms": // 毫秒
                return String.valueOf(currentTime);
            case "ns": // 纳秒
                return String.valueOf(System.nanoTime());
            case "ws": // 微秒（这里用纳秒除以1000模拟）
                return String.valueOf(System.nanoTime() / 1000);
            default:
                return String.valueOf(currentTime);
        }
    }

    /**
     * 生成标准时间格式
     * 参数: 0-10
     */
    private static String toStandardTime(String option) {
        int opt = 0;
        try {
            opt = Integer.parseInt(option.trim());
        } catch (NumberFormatException e) {
            opt = 0;
        }

        Date now = new Date();
        SimpleDateFormat sdf;

        switch (opt) {
            case 0: // yyyy-MM-dd HH:mm:ss
                sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                break;
            case 1: // yyyy/MM/dd HH:mm:ss
                sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                break;
            case 2: // yyyy年MM月dd日 HH时mm分ss秒
                sdf = new SimpleDateFormat("yyyy年MM月dd日 HH时mm分ss秒");
                break;
            case 3: // yyyyMMddHHmmss
                sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                break;
            case 4: // yyyy-MM-dd
                sdf = new SimpleDateFormat("yyyy-MM-dd");
                break;
            case 5: // HH:mm:ss
                sdf = new SimpleDateFormat("HH:mm:ss");
                break;
            case 6: // yyyyMMdd
                sdf = new SimpleDateFormat("yyyyMMdd");
                break;
            case 7: // HHmmss
                sdf = new SimpleDateFormat("HHmmss");
                break;
            case 8: // EEE, d MMM yyyy HH:mm:ss Z
                sdf = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
                break;
            case 9: // yyyy-MM-dd'T'HH:mm:ss'Z'
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                break;
            case 10: // yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
                break;
            default:
                sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                break;
        }

        return sdf.format(now);
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }


}