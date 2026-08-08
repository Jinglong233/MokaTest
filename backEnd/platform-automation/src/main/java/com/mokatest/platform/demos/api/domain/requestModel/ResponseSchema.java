package com.mokatest.platform.demos.api.domain.requestModel;

import lombok.Data;

import java.util.List;

/**
 * 接口响应结构定义（对标 Apifox 数据模型绑定）
 *
 * 三种模式：
 *   NONE     — 不定义（默认，不校验）
 *   TEMPLATE — 绑定数据模板，overrides 可在引用处隐藏/覆盖字段
 *   INLINE   — 内联编辑 schema 规则树
 *
 * validateEnabled=true 时，执行后自动对响应体做结构校验（SchemaValidator），
 * 结果以 assertType=SCHEMA 的 AssertResult 混入 assertionResults。
 *
 * @author JingLong
 * @since 2026-08-06
 */
@Data
public class ResponseSchema {

    public enum Mode {
        NONE, TEMPLATE, INLINE
    }

    /**
     * 定义模式
     */
    private Mode mode = Mode.NONE;

    /**
     * 绑定的数据模板ID（mode=TEMPLATE 时有效）
     */
    private Integer templateId;

    /**
     * 内联 schema 规则树（mode=INLINE 时有效）
     */
    private MockFieldRule schema;

    /**
     * 引用处隐藏字段（mode=TEMPLATE 时有效）：字段名列表，校验/生成时从模板字段中剔除
     */
    private List<String> hiddenFields;

    /**
     * 引用处覆盖字段（mode=TEMPLATE 时有效）：与模板同名字段以本地规则为准
     */
    private List<MockFieldRule> overrideFields;

    /**
     * 执行时是否自动校验响应结构（默认 true）
     */
    private Boolean validateEnabled = true;
}
