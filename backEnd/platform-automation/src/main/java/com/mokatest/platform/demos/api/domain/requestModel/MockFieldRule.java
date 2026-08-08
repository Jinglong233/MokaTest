package com.mokatest.platform.demos.api.domain.requestModel;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Data;

import java.util.List;

/**
 * Mock 字段规则
 *
 * 统一支持标量、对象、数组三种结构，并支持可空/必填、高级 schema 约束：
 *   标量字段：fieldType = STRING/INT/LONG/FLOAT/DOUBLE/BOOLEAN，通过 ruleType 生成值
 *   对象字段：fieldType = OBJECT，children 为对象属性列表
 *   <li>数组字段：fieldType = ARRAY，作为容器通过 children 定义元素对象结构；
 *       通过 minItems/maxItems 控制元素个数，uniqueItems 控制元素唯一性</li>
 *
 * 兼容旧数据：旧 {@link MockFieldRuleParams} 中的字段在顶层字段为空时作为回退读取。
 *
 * @author JingLong
 * @since 2026-06-17
 */
@JsonDeserialize(using = MockFieldRuleDeserializer.class)
@Data
public class MockFieldRule {

    /**
     * 字段名（根节点可为空）
     */
    private String fieldName;

    /**
     * 字段描述
     */
    private String description;

    /**
     * 字段类型：STRING / INT / LONG / FLOAT / DOUBLE / BOOLEAN / ARRAY / OBJECT / TEMPLATE
     */
    private String fieldType;

    /**
     * 是否可为 null：true 时生成阶段可按概率输出 null
     */
    private Boolean nullable;

    /**
     * 是否必须：false 时生成阶段可按概率省略该字段
     */
    private Boolean required;

    /**
     * 是否枚举：true 时 choices 字段生效
     */
    private Boolean isEnum;

    /**
     * 是否常量：true 时 fixedValue 作为唯一允许值
     */
    private Boolean isConstant;

    /**
     * Mock 规则类型：
     * name / phone / email / int / long / float / double / date / uuid / text /
     * choice / idCard / company / address / fixed / template / null
     */
    private String ruleType;

    /**
     * 规则参数（旧版字段，已废弃，新字段优先读取顶层字段）
     */
    @Deprecated
    private MockFieldRuleParams params;

    /**
     * 固定值（ruleType = fixed 或 isConstant = true 时使用）
     */
    private String fixedValue;

    /**
     * 数组长度（旧字段，已废弃，新字段优先读取 minItems/maxItems）
     */
    @Deprecated
    private Integer arrayLength;

    /**
     * 数组元素类型（旧字段，已废弃，Array 现在只作为容器）
     */
    @Deprecated
    private String arrayElementType;

    /**
     * 数组最小元素个数
     */
    private Integer minItems;

    /**
     * 数组最大元素个数
     */
    private Integer maxItems;

    /**
     * 数组元素是否唯一
     */
    private Boolean uniqueItems;

    /**
     * 引用的数据模板 ID（ruleType = template 时使用）
     */
    private Integer templateId;

    /**
     * 引用模板时隐藏的字段名列表（fieldType = TEMPLATE 时使用）：
     * 生成/校验时将模板中同名字段剔除
     */
    private List<String> excludedFields;

    /**
     * 日期/字符串格式
     */
    private String format;

    /**
     * 字符串最小长度
     */
    private Integer minLength;

    /**
     * 字符串最大长度
     */
    private Integer maxLength;

    /**
     * 数值最小值
     */
    private Number min;

    /**
     * 数值最大值
     */
    private Number max;

    /**
     * 小数位数（FLOAT / DOUBLE）
     */
    private Integer scale;

    /**
     * 正则表达式（STRING）
     */
    private String pattern;

    /**
     * 默认值/兜底值
     */
    private String defaultValue;

    /**
     * 枚举选项，英文逗号分隔
     */
    private String choices;

    /**
     * 随机字符串字符集
     */
    private String charset;

    /**
     * 随机字符大小写类型（ruleType = character 时使用）：lower / upper / number / mixed
     */
    private String caseType;

    /**
     * 随机字符串长度
     */
    private Integer length;

    /**
     * 语言/地区：zh / en
     */
    private String locale;

    /**
     * 子字段列表：
     * - fieldType = OBJECT 时，表示对象属性
     * - fieldType = ARRAY 且元素为对象时，表示数组元素对象的属性
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<MockFieldRule> children;
}
