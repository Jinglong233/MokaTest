package com.mokatest.platform.demos.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.mokatest.platform.demos.exception.BusinessException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 请求参数 ARRAY 类型的安全解析工具
 *
 * OBJECT 类型已从请求参数中移除，本工具仅保留 ARRAY 安全解析能力。
 *
 * 设计目标：
 *   防止超深嵌套 JSON 导致栈溢出或 OOM
 *   防止超长字符串/超大数组导致内存爆炸
 *   统一 ARRAY 在 Query / FormData / x-www-form-urlencoded 中的序列化行为
 *
 * 核心规则：
 *   ARRAY 元素为标量时，输出 key[] 形式（可多次出现，保留顺序）
 *   ARRAY 元素含对象/数组时，整体序列化为 JSON 字符串，避免复杂索引语义
 *   单个参数值最大 {@value #MAX_LENGTH} 字节，数组最大 {@value #MAX_ARRAY_SIZE} 个元素
 *
 * @author JingLong
 * @since 2026-06-19
 */
public final class RequestParameterJsonUtils {

    private RequestParameterJsonUtils() {
    }

    /** 单个参数值最大长度（字符数） */
    public static final int MAX_LENGTH = 50 * 1024;

    /** 数组最大元素数 */
    public static final int MAX_ARRAY_SIZE = 1000;

    /**
     * ARRAY 类型参数的安全解析
     *
     * @param value 参数值，应为 JSON 数组字符串，如 ["a","b"]
     * @param name  参数名
     * @return 扁平后的键值对列表，每个元素为 name[] / value
     */
    public static List<NameValue> safeParseArray(String value, String name) {
        if (value == null || value.isEmpty()) {
            return Collections.emptyList();
        }
        if (value.length() > MAX_LENGTH) {
            throw new BusinessException("ARRAY 类型参数值超过最大长度限制（" + MAX_LENGTH + "）");
        }
        try {
            Object parsed = JSON.parse(value);
            if (!(parsed instanceof JSONArray)) {
                throw new BusinessException("ARRAY 类型参数值必须是 JSON 数组");
            }
            JSONArray array = (JSONArray) parsed;
            if (array.size() > MAX_ARRAY_SIZE) {
                throw new BusinessException("ARRAY 类型参数元素数超过最大限制（" + MAX_ARRAY_SIZE + "）");
            }

            // 如果数组元素包含对象或数组，直接序列化为 JSON 字符串作为一个整体值
            boolean containsComplex = false;
            for (Object item : array) {
                if (item instanceof JSONObject || item instanceof JSONArray) {
                    containsComplex = true;
                    break;
                }
            }
            if (containsComplex) {
                return Collections.singletonList(new NameValue(name, JSON.toJSONString(array)));
            }

            List<NameValue> result = new ArrayList<>(array.size());
            for (Object item : array) {
                result.add(new NameValue(name, item == null ? "" : item.toString()));
            }
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("ARRAY 类型参数值 JSON 解析失败: " + e.getMessage());
        }
    }

    /**
     * 键值对内部类
     */
    public static class NameValue {
        private final String name;
        private final String value;

        public NameValue(String name, String value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }
    }
}
