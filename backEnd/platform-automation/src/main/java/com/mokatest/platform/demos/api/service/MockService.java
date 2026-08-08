package com.mokatest.platform.demos.api.service;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import com.mokatest.platform.demos.api.domain.requestModel.ResponseSchema;

/**
 * Mock 辅助 Service
 *
 * @author JingLong
 * @since 2026-06-17
 */
public interface MockService {

    /**
     * 预览字段规则生成的 Mock 响应体
     */
    SaResult previewMockRules(MockFieldRule rules);

    /**
     * 根据 Mock/Template 表达式生成单个值
     *
     * @param expression 如 {{__MOCK(phone)__}} / {{__TEMPLATE(1)__}}
     */
    SaResult generate(String expression);

    /**
     * 按响应定义（ResponseSchema）预览生成的 Mock Body
     */
    SaResult previewResponseSchema(ResponseSchema responseSchema);
}
