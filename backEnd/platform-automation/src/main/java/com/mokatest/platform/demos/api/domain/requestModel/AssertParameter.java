package com.mokatest.platform.demos.api.domain.requestModel;

import com.mokatest.platform.demos.api.domain.apiEnum.RuleSource;
import com.mokatest.platform.demos.enums.AssertRelationship;
import com.mokatest.platform.demos.api.domain.apiEnum.ApiAssertType;
import lombok.Data;

/**
 * @Description: 断言参数
 * @Author: JingLong
 * @DateTime: 2026/4/3 14:27
 */
@Data
public class AssertParameter {
    // 断言体
    private ApiAssertType apiAssertType;
    // 字段
    private String field;
    // 断言关系
    private AssertRelationship assertRelationship;
    // 断言值
    private String assertValue;
    // 是否禁用
    private boolean disabled = false;

    /**
     * 断言规则来源（运行时填充，不持久化到数据库）
     * 用于标识该断言规则来自 GLOBAL/ENVIRONMENT/SCENE/API
     */
    private RuleSource source;
}
