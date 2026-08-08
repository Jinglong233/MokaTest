package com.mokatest.platform.demos.operationlog.util;

import com.alibaba.fastjson.JSON;
import lombok.Data;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 字段变更对比工具
 */
public class FieldCompareUtil {

    /**
     * 默认忽略字段
     */
    private static final Set<String> DEFAULT_IGNORE_FIELDS = new HashSet<>(Arrays.asList(
            "id", "createTime", "updateTime", "createUserId", "updateUserId",
            "projectId", "deleted", "isDeleted", "deletedAt", "serialVersionUID"
    ));

    /**
     * 对比两个对象的字段差异
     *
     * @param oldObj           旧对象
     * @param newObj           新对象
     * @param ignoreFields     额外忽略的字段（逗号分隔）
     * @param skipNullNewValue 是否跳过新对象为 null 的字段（用于部分更新场景，默认 true）
     * @return 变更列表
     */
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 格式化字段值：Date 类型统一格式化为 yyyy-MM-dd HH:mm:ss；集合/对象类型用 JSON 序列化
     */
    private static String formatValue(Object value) {
        if (value == null) return null;
        if (value instanceof Date) {
            synchronized (DATE_FORMAT) {
                return DATE_FORMAT.format((Date) value);
            }
        }
        // 集合、Map、数组或复杂对象用 JSON 序列化，避免 toString() 输出对象引用
        if (value instanceof Collection || value instanceof Map || value.getClass().isArray() || !isSimpleType(value.getClass())) {
            return JSON.toJSONString(value);
        }
        return value.toString();
    }

    /**
     * 判断是否为简单类型（无需 JSON 序列化）
     */
    private static boolean isSimpleType(Class<?> clazz) {
        return clazz.isPrimitive()
                || clazz == String.class
                || Number.class.isAssignableFrom(clazz)
                || clazz == Boolean.class
                || clazz == Character.class
                || clazz.isEnum();
    }

    public static List<FieldChange> compare(Object oldObj, Object newObj, String ignoreFields, boolean skipNullNewValue) {
        List<FieldChange> changes = new ArrayList<>();
        if (oldObj == null || newObj == null) {
            return changes;
        }

        Set<String> ignoreSet = new HashSet<>(DEFAULT_IGNORE_FIELDS);
        if (ignoreFields != null && !ignoreFields.isEmpty()) {
            Arrays.stream(ignoreFields.split(","))
                    .map(String::trim)
                    .forEach(ignoreSet::add);
        }

        Class<?> clazz = oldObj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();

            if (ignoreSet.contains(fieldName)) {
                continue;
            }

            try {
                Object oldValue = field.get(oldObj);
                Object newValue = field.get(newObj);

                // 部分更新场景：新对象字段为 null 时，默认认为是"不修改"
                if (skipNullNewValue && newValue == null && oldValue != null) {
                    continue;
                }

                // 统一格式化后比较（解决 Date 类型格式不一致问题）
                String oldStr = formatValue(oldValue);
                String newStr = formatValue(newValue);

                if (!Objects.equals(oldStr, newStr)) {
                    String label = getFieldLabel(clazz.getSimpleName(), fieldName);
                    changes.add(new FieldChange(fieldName, label, oldStr, newStr));
                }
            } catch (IllegalAccessException e) {
                // 忽略无法访问的字段
            }
        }
        return changes;
    }

    /**
     * 对比两个对象的字段差异（默认跳过新对象为 null 的字段）
     *
     * @param oldObj       旧对象
     * @param newObj       新对象
     * @param ignoreFields 额外忽略的字段（逗号分隔）
     * @return 变更列表
     */
    public static List<FieldChange> compare(Object oldObj, Object newObj, String ignoreFields) {
        return compare(oldObj, newObj, ignoreFields, true);
    }

    /**
     * 将变更列表转为 JSON 字符串
     */
    public static String toJson(List<FieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(changes);
    }

    /**
     * 通用字段中文映射（不区分实体类）
     */
    private static final Map<String, String> COMMON_LABEL_MAP = new HashMap<>();
    static {
        COMMON_LABEL_MAP.put("bugCode", "BUG编号");
        COMMON_LABEL_MAP.put("reqCode", "需求编号");
        COMMON_LABEL_MAP.put("caseCode", "用例编号");
        COMMON_LABEL_MAP.put("caseName", "用例名称");
        COMMON_LABEL_MAP.put("setName", "测试集名称");
        COMMON_LABEL_MAP.put("planName", "计划名称");
        COMMON_LABEL_MAP.put("moduleName", "模块名称");
        COMMON_LABEL_MAP.put("description", "描述");
        COMMON_LABEL_MAP.put("preCondition", "前置条件");
        COMMON_LABEL_MAP.put("reproduceSteps", "复现步骤");
        COMMON_LABEL_MAP.put("severity", "严重程度");
        COMMON_LABEL_MAP.put("priority", "优先级");
        COMMON_LABEL_MAP.put("status", "状态");
        COMMON_LABEL_MAP.put("environment", "环境");
        COMMON_LABEL_MAP.put("foundVersion", "发现版本");
        COMMON_LABEL_MAP.put("fixedVersion", "修复版本");
        COMMON_LABEL_MAP.put("reproduceRate", "重现概率");
        COMMON_LABEL_MAP.put("closeReason", "关闭原因");
        COMMON_LABEL_MAP.put("tags", "标签");
        COMMON_LABEL_MAP.put("deadline", "截止日期");
        COMMON_LABEL_MAP.put("assigneeId", "指派人");
        COMMON_LABEL_MAP.put("reporterId", "报告人");
        COMMON_LABEL_MAP.put("ownerId", "负责人");
        COMMON_LABEL_MAP.put("moduleId", "所属模块");
        COMMON_LABEL_MAP.put("projectId", "所属项目");
        COMMON_LABEL_MAP.put("parentId", "父级");
        COMMON_LABEL_MAP.put("requirementId", "关联需求");
        COMMON_LABEL_MAP.put("testCaseId", "关联用例");
        COMMON_LABEL_MAP.put("participants", "参与人");
        COMMON_LABEL_MAP.put("version", "版本");
        COMMON_LABEL_MAP.put("expectReleaseTime", "期望上线时间");
        COMMON_LABEL_MAP.put("caseType", "用例类型");
        COMMON_LABEL_MAP.put("lastResult", "最近执行结果");
        COMMON_LABEL_MAP.put("lastExecuteTime", "最近执行时间");
        COMMON_LABEL_MAP.put("expectDuration", "预期执行时长");
        COMMON_LABEL_MAP.put("content", "内容");
        COMMON_LABEL_MAP.put("source", "来源");
        COMMON_LABEL_MAP.put("reqType", "需求类型");
        COMMON_LABEL_MAP.put("testSteps", "测试步骤");
        COMMON_LABEL_MAP.put("bindRemark", "绑定备注");
        COMMON_LABEL_MAP.put("autoType", "自动化类型");
        COMMON_LABEL_MAP.put("autoId", "自动化ID");
        COMMON_LABEL_MAP.put("autoName", "自动化名称");
        COMMON_LABEL_MAP.put("folderId", "所属文件夹");
        COMMON_LABEL_MAP.put("planCaseId", "关联计划用例");
        COMMON_LABEL_MAP.put("setIds", "所属测试集");
        COMMON_LABEL_MAP.put("sort", "排序");
        COMMON_LABEL_MAP.put("mockResponse", "Mock响应");
    }

    /**
     * 按实体类区分的字段中文映射（优先级高于通用映射）
     * key 格式：实体类简单名:字段名
     */
    private static final Map<String, String> ENTITY_LABEL_MAP = new HashMap<>();
    static {
        ENTITY_LABEL_MAP.put("Bug:title", "BUG标题");
        ENTITY_LABEL_MAP.put("Requirement:title", "需求标题");
        ENTITY_LABEL_MAP.put("TestCase:title", "用例标题");
        ENTITY_LABEL_MAP.put("TestPlan:planName", "计划名称");
        ENTITY_LABEL_MAP.put("QaModule:moduleName", "模块名称");
        ENTITY_LABEL_MAP.put("BugComment:content", "评论内容");
    }

    /**
     * 获取字段中文标签
     */
    private static String getFieldLabel(String entityName, String fieldName) {
        String entityKey = entityName + ":" + fieldName;
        if (ENTITY_LABEL_MAP.containsKey(entityKey)) {
            return ENTITY_LABEL_MAP.get(entityKey);
        }
        return COMMON_LABEL_MAP.getOrDefault(fieldName, fieldName);
    }

    /**
     * 构建创建内容摘要（CREATE 操作用于展示新建实体的关键字段）
     *
     * @param newObj       新实体对象
     * @param ignoreFields 额外忽略的字段（逗号分隔）
     * @return 字段列表 JSON 字符串
     */
    public static String buildCreateSummary(Object newObj, String ignoreFields) {
        if (newObj == null) {
            return null;
        }
        List<CreateField> fields = new ArrayList<>();
        Set<String> ignoreSet = new HashSet<>(DEFAULT_IGNORE_FIELDS);
        if (ignoreFields != null && !ignoreFields.isEmpty()) {
            Arrays.stream(ignoreFields.split(","))
                    .map(String::trim)
                    .forEach(ignoreSet::add);
        }

        Class<?> clazz = newObj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();
            if (ignoreSet.contains(fieldName)) {
                continue;
            }
            try {
                Object value = field.get(newObj);
                if (value != null) {
                    String label = getFieldLabel(clazz.getSimpleName(), fieldName);
                    fields.add(new CreateField(fieldName, label, formatValue(value)));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        if (fields.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(fields);
    }

    /**
     * 字段变更项
     */
    @Data
    public static class FieldChange {
        private String field;  // 原始字段名
        private String label;  // 中文标签
        private String old;
        private String ne; // 不用 new 避免关键字冲突
        private String oldDisplayValue; // 关联对象补充后的展示值
        private String neDisplayValue;  // 关联对象补充后的展示值

        public FieldChange(String field, String label, String old, String ne) {
            this.field = field;
            this.label = label;
            this.old = old;
            this.ne = ne;
        }
    }

    /**
     * 创建内容字段项（CREATE/DELETE 操作展示用）
     */
    @Data
    public static class CreateField {
        private String field;
        private String label;
        private String value;
        private String displayValue; // 关联对象补充后的展示值（如"标题 (ID:14)"）

        public CreateField(String field, String label, String value) {
            this.field = field;
            this.label = label;
            this.value = value;
        }
    }

    /**
     * 构建删除前内容摘要（DELETE 操作展示用）
     * 与 buildCreateSummary 类似，但标注为删除前数据
     */
    public static String buildDeleteSummary(Object oldObj) {
        if (oldObj == null) {
            return null;
        }
        List<CreateField> fields = new ArrayList<>();
        Set<String> ignoreSet = new HashSet<>(DEFAULT_IGNORE_FIELDS);

        Class<?> clazz = oldObj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName = field.getName();
            if (ignoreSet.contains(fieldName)) {
                continue;
            }
            try {
                Object value = field.get(oldObj);
                if (value != null) {
                    String label = getFieldLabel(clazz.getSimpleName(), fieldName);
                    fields.add(new CreateField(fieldName, label, formatValue(value)));
                }
            } catch (IllegalAccessException ignored) {
            }
        }
        if (fields.isEmpty()) {
            return null;
        }
        return JSON.toJSONString(fields);
    }
}
