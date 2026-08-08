package com.mokatest.platform.demos.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

/**
 * Mock 数据生成器
 *
 * 为 {@link FunctionParser} 的 {@code {{__MOCK(type, args)__}}} 函数提供数据生成能力。
 *
 * 支持的类型：
 *   name [zh|en] / cname / ename：姓名
 *   phone：手机号
 *   email：邮箱
 *   int min max：指定范围整数
 *   long min max：指定范围长整数
 *   float/double min max [scale]：指定范围浮点数
 *   date [format] / datetime [format] / time [format]：日期时间
 *   uuid：UUID
 *   text length [charset]：随机字符串
 *   character [caseType] [length]：指定字符集随机字符
 *   choice "a,b,c"：枚举选择
 *   idCard：身份证号
 *   bankcard：银行卡号
 *   company [zh|en]：公司名
 *   address [zh|en]：地址
 *   boolean：布尔值
 *   timestamp [unit]：时间戳
 *
 * @author JingLong
 * @since 2026-06-17
 */
public class MockDataGenerator {

    private static final Random RANDOM = new Random();

    // 中文姓名库
    private static final String[] FAMILY_NAMES = {
            "赵", "钱", "孙", "李", "周", "吴", "郑", "王", "冯", "陈",
            "褚", "卫", "蒋", "沈", "韩", "杨", "朱", "秦", "尤", "许"
    };
    private static final String[] GIVEN_NAMES = {
            "伟", "芳", "娜", "敏", "静", "丽", "强", "磊", "军", "洋",
            "勇", "艳", "杰", "娟", "涛", "明", "超", "秀", "霞", "平",
            "刚", "桂英", "秀英", "梅", "强", "磊", "洋", "勇", "艳", "杰"
    };

    // 英文姓名库
    private static final String[] EN_FIRST_NAMES = {
            "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
            "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica"
    };
    private static final String[] EN_LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas"
    };

    // 中文公司名
    private static final String[] COMPANY_PREFIXES = {
            "腾讯", "阿里", "字节", "华为", "京东", "美团", "小米", "百度", "滴滴", "网易"
    };
    private static final String[] COMPANY_SUFFIXES = {
            "科技", "网络", "信息", "软件", "智能", "云服务", "数字", "互联", "创新", "数据"
    };

    // 英文公司名
    private static final String[] EN_COMPANY_PREFIXES = {
            "Tech", "Cloud", "Data", "Smart", "Global", "Future", "Digital", "Cyber", "Net", "Open"
    };
    private static final String[] EN_COMPANY_SUFFIXES = {
            "Systems", "Solutions", "Technologies", "Services", "Networks", "Software", "Labs", "Corp", "Inc", "Group"
    };

    // 城市/地址
    private static final String[] CITIES = {
            "北京", "上海", "广州", "深圳", "杭州", "成都", "南京", "武汉", "西安", "重庆"
    };
    private static final String[] EN_CITIES = {
            "New York", "Los Angeles", "Chicago", "Houston", "Phoenix", "London", "Paris", "Tokyo", "Sydney", "Berlin"
    };
    private static final String[] STREETS = {
            "中山路", "解放大道", "建设路", "人民路", "和平街", "新华路", "胜利街", "光明路", "前进路", "文化路"
    };
    private static final String[] EN_STREETS = {
            "Main St", "Broadway", "Park Ave", "5th Ave", "Oak St", "Maple Ave", "Washington St", "Lake St", "Hill Rd", "River Rd"
    };

    /**
     * 根据参数字符串生成 Mock 数据
     *
     * @param params 格式：type[, arg1, arg2, ...]
     * @return 生成的字符串
     */
    public static String generate(String params) {
        if (params == null || params.trim().isEmpty()) {
            return "";
        }
        String[] args = params.split(",\\s*");
        if (args.length == 0) {
            return "";
        }
        String type = args[0].trim().toLowerCase();
        try {
            switch (type) {
                case "name":
                    return generateName(args);
                case "cname":
                    return generateName(new String[]{"name", "zh"});
                case "ename":
                    return generateName(new String[]{"name", "en"});
                case "phone":
                    return generatePhone();
                case "email":
                    return generateEmail();
                case "int":
                    return generateInt(args);
                case "long":
                    return generateLong(args);
                case "float":
                case "double":
                    return generateFloat(args);
                case "date":
                    return generateDate(args);
                case "datetime":
                    return generateDateTime(args);
                case "time":
                    return generateTime(args);
                case "uuid":
                    return UUID.randomUUID().toString();
                case "text":
                    return generateText(args);
                case "character":
                    return generateCharacter(args);
                case "choice":
                    return generateChoice(args);
                case "idcard":
                case "id_card":
                    return generateIdCard();
                case "bankcard":
                    return generateBankCard();
                case "company":
                    return generateCompany(args);
                case "address":
                    return generateAddress(args);
                case "boolean":
                    return String.valueOf(RANDOM.nextBoolean());
                case "timestamp":
                    return generateTimestamp(args);
                default:
                    return "{{__MOCK(" + params + ")__}}";
            }
        } catch (Exception e) {
            return "{{__MOCK(" + params + ")__}}";
        }
    }

    private static String generateName(String[] args) {
        String locale = args.length > 1 ? args[1].trim().toLowerCase() : "zh";
        if ("en".equals(locale)) {
            return EN_FIRST_NAMES[RANDOM.nextInt(EN_FIRST_NAMES.length)] + " "
                    + EN_LAST_NAMES[RANDOM.nextInt(EN_LAST_NAMES.length)];
        }
        return FAMILY_NAMES[RANDOM.nextInt(FAMILY_NAMES.length)]
                + GIVEN_NAMES[RANDOM.nextInt(GIVEN_NAMES.length)];
    }

    private static String generatePhone() {
        String[] prefixes = {"130", "131", "132", "133", "134", "135", "136", "137", "138", "139",
                "150", "151", "152", "153", "155", "156", "157", "158", "159", "180", "181", "182", "183", "185", "186", "187", "188", "189"};
        String prefix = prefixes[RANDOM.nextInt(prefixes.length)];
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 8; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String generateEmail() {
        String local = generateRandomString(8, "abcdefghijklmnopqrstuvwxyz0123456789");
        String[] domains = {"qq.com", "163.com", "gmail.com", "outlook.com", "example.com", "mokaTest.com"};
        return local + "@" + domains[RANDOM.nextInt(domains.length)];
    }

    private static String generateInt(String[] args) {
        int min = args.length > 1 ? parseInt(args[1], 0) : 0;
        int max = args.length > 2 ? parseInt(args[2], 100) : 100;
        if (min > max) {
            int tmp = min;
            min = max;
            max = tmp;
        }
        return String.valueOf(min + RANDOM.nextInt(max - min + 1));
    }

    private static String generateLong(String[] args) {
        long min = args.length > 1 ? parseLong(args[1], 0L) : 0L;
        long max = args.length > 2 ? parseLong(args[2], Long.MAX_VALUE) : Long.MAX_VALUE;
        if (min > max) {
            long tmp = min;
            min = max;
            max = tmp;
        }
        long range = max - min + 1;
        if (range <= 0) {
            range = Long.MAX_VALUE;
        }
        return String.valueOf(min + (long) (RANDOM.nextDouble() * range));
    }

    private static String generateFloat(String[] args) {
        double min = args.length > 1 ? parseDouble(args[1], 0) : 0;
        double max = args.length > 2 ? parseDouble(args[2], 100) : 100;
        int scale = args.length > 3 ? parseInt(args[3], 2) : 2;
        if (min > max) {
            double tmp = min;
            min = max;
            max = tmp;
        }
        double value = min + (max - min) * RANDOM.nextDouble();
        return String.format(Locale.US, "%" + (scale >= 0 ? "." + scale : "") + "f", value);
    }

    private static String generateDate(String[] args) {
        String format = args.length > 1 ? args[1].trim() : "yyyy-MM-dd HH:mm:ss";
        // 默认生成最近 10 年内的随机时间
        int days = args.length > 2 ? parseInt(args[2], 3650) : 3650;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -RANDOM.nextInt(days));
        calendar.set(Calendar.HOUR_OF_DAY, RANDOM.nextInt(24));
        calendar.set(Calendar.MINUTE, RANDOM.nextInt(60));
        calendar.set(Calendar.SECOND, RANDOM.nextInt(60));
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(calendar.getTime());
    }

    private static String generateText(String[] args) {
        int length = args.length > 1 ? parseInt(args[1], 10) : 10;
        String charset = args.length > 2 ? args[2].trim() : "abcdefghijklmnopqrstuvwxyz0123456789";
        return generateRandomString(length, charset);
    }

    private static String generateChoice(String[] args) {
        if (args.length < 2) {
            return "";
        }
        // 把除 type 外的所有参数重新拼起来，支持 choice, "a,b,c" 或 choice, a, b, c
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
            if (i > 1) {
                sb.append(",");
            }
            sb.append(args[i].trim());
        }
        String joined = sb.toString().trim();
        // 去掉首尾引号
        if (joined.startsWith("\"") && joined.endsWith("\"")) {
            joined = joined.substring(1, joined.length() - 1);
        }
        String[] choices = joined.split(",");
        if (choices.length == 0) {
            return "";
        }
        String choice = choices[RANDOM.nextInt(choices.length)].trim();
        // 去掉单个选项的引号
        if (choice.startsWith("\"") && choice.endsWith("\"")) {
            choice = choice.substring(1, choice.length() - 1);
        }
        return choice;
    }

    private static String generateIdCard() {
        String[] areaCodes = {"110101", "310101", "440106", "440305", "330106", "510107", "420106", "610104"};
        String areaCode = areaCodes[RANDOM.nextInt(areaCodes.length)];

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -(18 + RANDOM.nextInt(50)));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String birthday = sdf.format(calendar.getTime());

        int sequence = 100 + RANDOM.nextInt(900);
        String base = areaCode + birthday + sequence;
        return base + calculateCheckCode(base);
    }

    private static String generateCompany(String[] args) {
        String locale = args.length > 1 ? args[1].trim().toLowerCase() : "zh";
        if ("en".equals(locale)) {
            return EN_COMPANY_PREFIXES[RANDOM.nextInt(EN_COMPANY_PREFIXES.length)]
                    + EN_COMPANY_SUFFIXES[RANDOM.nextInt(EN_COMPANY_SUFFIXES.length)];
        }
        return COMPANY_PREFIXES[RANDOM.nextInt(COMPANY_PREFIXES.length)]
                + COMPANY_SUFFIXES[RANDOM.nextInt(COMPANY_SUFFIXES.length)] + "有限公司";
    }

    private static String generateAddress(String[] args) {
        String locale = args.length > 1 ? args[1].trim().toLowerCase() : "zh";
        if ("en".equals(locale)) {
            int number = 1 + RANDOM.nextInt(9999);
            return number + " " + EN_STREETS[RANDOM.nextInt(EN_STREETS.length)] + ", "
                    + EN_CITIES[RANDOM.nextInt(EN_CITIES.length)];
        }
        String city = CITIES[RANDOM.nextInt(CITIES.length)];
        String street = STREETS[RANDOM.nextInt(STREETS.length)];
        int number = 1 + RANDOM.nextInt(999);
        return city + street + number + "号";
    }

    private static String generateCharacter(String[] args) {
        String caseType = args.length > 1 ? args[1].trim().toLowerCase() : "lower";
        int length = args.length > 2 ? parseInt(args[2], 1) : 1;
        String charset;
        switch (caseType) {
            case "upper":
                charset = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                break;
            case "number":
                charset = "0123456789";
                break;
            case "mixed":
                charset = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                break;
            case "lower":
            default:
                charset = "abcdefghijklmnopqrstuvwxyz";
                break;
        }
        return generateRandomString(length, charset);
    }

    private static String generateDateTime(String[] args) {
        String format = args.length > 1 ? args[1].trim() : "yyyy-MM-dd HH:mm:ss";
        int days = args.length > 2 ? parseInt(args[2], 3650) : 3650;
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -RANDOM.nextInt(days));
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(calendar.getTime());
    }

    private static String generateTime(String[] args) {
        String format = args.length > 1 ? args[1].trim() : "HH:mm:ss";
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, RANDOM.nextInt(24));
        calendar.set(Calendar.MINUTE, RANDOM.nextInt(60));
        calendar.set(Calendar.SECOND, RANDOM.nextInt(60));
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        return sdf.format(calendar.getTime());
    }

    private static String generateBankCard() {
        String[] prefixes = {"622202", "622203", "622208", "621700", "621288", "621226", "623058", "621081"};
        String prefix = prefixes[RANDOM.nextInt(prefixes.length)];
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 12; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static String generateTimestamp(String[] args) {
        String unit = args.length > 1 ? args[1].trim().toLowerCase() : "ms";
        long currentTime = System.currentTimeMillis();
        switch (unit) {
            case "s":
                return String.valueOf(currentTime / 1000);
            case "ns":
                return String.valueOf(System.nanoTime());
            default:
                return String.valueOf(currentTime);
        }
    }

    private static String generateRandomString(int length, String charset) {
        if (length <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(charset.charAt(RANDOM.nextInt(charset.length())));
        }
        return sb.toString();
    }

    private static int parseInt(String str, int defaultValue) {
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static long parseLong(String str, long defaultValue) {
        try {
            return Long.parseLong(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static double parseDouble(String str, double defaultValue) {
        try {
            return Double.parseDouble(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 计算 18 位身份证校验码
     */
    private static String calculateCheckCode(String base) {
        if (base == null || base.length() != 17) {
            return "0";
        }
        int[] weight = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] validate = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (base.charAt(i) - '0') * weight[i];
        }
        return String.valueOf(validate[sum % 11]);
    }
}
