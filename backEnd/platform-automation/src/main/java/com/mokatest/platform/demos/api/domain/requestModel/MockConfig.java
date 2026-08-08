package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

/**
 * 参数级 Mock 生成配置
 *
 * 用于替代手写 {@code {{__MOCK()__}}} 表达式，以结构化方式描述单个参数的 Mock 生成规则。
 *
 * 当 {@link RequestParameter#getValue()} 为 {@code {{__MOCK__}}} 且本配置不为空时，
 * 执行器按 {@link #type} 及对应参数生成数据并替换占位符。</p>
 *
 * @author JingLong
 * @since 2026-06-19
 */
@Data
public class MockConfig {

    /**
     * Mock 规则类型
     *
     * 与前端 Mock 弹窗中的规则类型对应：
     *   name / phone / email / uuid / idCard / company / address
     *   int / float / date / text / choice
     *   fixed
     *   template
     */
    private String type;

    /**
     * 字符类型规则
     *
     * 用于 {@code character} 类型，控制生成字符的字符集：
     *   {@code lower}：小写字母
     *   {@code upper}：大写字母
     *   {@code number}：数字
     *   {@code mixed}：字母数字混合
     */
    private String caseType;

    /**
     * 语言/地区：zh / en
     *
     * 用于 name / company / address
     */
    private String locale;

    /**
     * 最小值
     *
     * 用于 int / float
     */
    private Number min;

    /**
     * 最大值
     *
     * 用于 int / float
     */
    private Number max;

    /**
     * 小数位数
     *
     * 用于 float
     */
    private Integer scale;

    /**
     * 字符串长度
     *
     * 用于 text
     */
    private Integer length;

    /**
     * 日期格式
     *
     * 用于 date
     */
    private String format;

    /**
     * 枚举选项，英文逗号分隔
     *
     * 用于 choice
     */
    private String choices;

    /**
     * 固定值
     *
     * 用于 fixed
     */
    private String fixedValue;

    /**
     * 数据模板 ID
     *
     * 用于 template
     */
    private Integer templateId;
}
