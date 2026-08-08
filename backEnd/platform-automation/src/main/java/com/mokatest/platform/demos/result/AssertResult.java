package com.mokatest.platform.demos.result;

import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import lombok.Data;

@Data
public class AssertResult {
    private Boolean success;
    private String assertTip;

    private String assertType;

    private String assertRelationship;

    // 断言执行时的实际值（用于前端展示，无论通过/失败都记录）
    private String actualValue;

    /**
     * 断言规则来源：标识该断言规则来自全局配置、场景配置、环境配置还是接口自身配置
     */
    private RuleSource source;

    public AssertResult() {
    }

    public AssertResult(boolean success, String assertTip) {
        this.assertTip = assertTip;
        this.success = success;
    }

    public AssertResult(Boolean success, String assertTip, String assertType) {
        this.success = success;
        this.assertTip = assertTip;
        this.assertType = assertType;
    }

    public AssertResult(Boolean success, String assertTip, String assertType,
                        String assertRelationship) {
        this.success = success;
        this.assertTip = assertTip;
        this.assertType = assertType;
        this.assertRelationship = assertRelationship;
    }

    public AssertResult(Boolean success, String assertTip, String assertType,
                        String assertRelationship, String actualValue) {
        this.success = success;
        this.assertTip = assertTip;
        this.assertType = assertType;
        this.assertRelationship = assertRelationship;
        this.actualValue = actualValue;
    }

    public AssertResult(Boolean success, String assertTip, String assertType,
                        String assertRelationship, String actualValue, RuleSource source) {
        this.success = success;
        this.assertTip = assertTip;
        this.assertType = assertType;
        this.assertRelationship = assertRelationship;
        this.actualValue = actualValue;
        this.source = source;
    }
}