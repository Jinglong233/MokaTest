package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

/**
 * Mock 字段规则参数
 *
 * 已废弃：参数已上移到 {@link MockFieldRule} 顶层字段。保留此类仅用于兼容旧数据反序列化。
 *
 * @author JingLong
 * @since 2026-06-17
 */
@Deprecated
@Data
public class MockFieldRuleParams {

    /**
     * 语言/地区：zh / en
     */
    private String locale;

    /**
     * 最小值（int/float）
     */
    private Integer min;

    /**
     * 最大值（int/float）
     */
    private Integer max;

    /**
     * 小数位数（float）
     */
    private Integer scale;

    /**
     * 长度（text）
     */
    private Integer length;

    /**
     * 日期格式（date）
     */
    private String format;

    /**
     * 选项列表（choice），英文逗号分隔
     */
    private String choices;
}
