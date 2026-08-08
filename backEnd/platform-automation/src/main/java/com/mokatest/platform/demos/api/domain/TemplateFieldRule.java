package com.mokatest.platform.demos.api.domain;

import lombok.Data;

/**
 * 数据模板字段规则
 *
 * @author JingLong
 * @since 2026-06-17
 */
@Data
public class TemplateFieldRule {

    /**
     * 字段名
     */
    private String fieldName;

    /**
     * 生成类型：name / phone / email / int / float / date / uuid / text / choice / idCard / company / address
     */
    private String type;

    /**
     * 语言/地区（name/company/address 等可用）：zh / en
     */
    private String locale;

    /**
     * 最小值（int/float 可用）
     */
    private Integer min;

    /**
     * 最大值（int/float 可用）
     */
    private Integer max;

    /**
     * 小数位数（float 可用）
     */
    private Integer scale;

    /**
     * 长度（text 可用）
     */
    private Integer length;

    /**
     * 日期格式（date 可用）
     */
    private String format;

    /**
     * 选项列表（choice 可用），英文逗号分隔
     */
    private String choices;

    /**
     * 字段描述
     */
    private String description;
}
