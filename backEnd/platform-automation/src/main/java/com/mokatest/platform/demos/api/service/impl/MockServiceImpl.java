package com.mokatest.platform.demos.api.service.impl;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.requestModel.MockFieldRule;
import com.mokatest.platform.demos.api.domain.requestModel.ResponseSchema;
import com.mokatest.platform.demos.api.http.validation.SchemaValidator;
import com.mokatest.platform.demos.api.service.MockService;
import com.mokatest.platform.demos.util.MockRuleGenerator;
import com.mokatest.platform.demos.util.FunctionParser;
import org.springframework.stereotype.Service;

/**
 * Mock 辅助 Service 实现
 *
 * @author JingLong
 * @since 2026-06-17
 */
@Service
public class MockServiceImpl implements MockService {

    @Override
    public SaResult previewMockRules(MockFieldRule rules) {
        String json = MockRuleGenerator.generate(rules);
        return SaResult.ok().setData(json);
    }

    @Override
    public SaResult generate(String expression) {
        if (expression == null) {
            return SaResult.ok().setData("");
        }
        String result = FunctionParser.parse(expression);
        return SaResult.ok().setData(result);
    }

    @Override
    public SaResult previewResponseSchema(ResponseSchema responseSchema) {
        if (responseSchema == null || responseSchema.getMode() == null
                || responseSchema.getMode() == ResponseSchema.Mode.NONE) {
            return SaResult.error("未配置响应定义");
        }
        MockFieldRule root = SchemaValidator.resolveSchema(responseSchema);
        if (root == null) {
            return SaResult.error("响应定义未配置有效的结构（模板不存在或内联 schema 为空）");
        }
        return SaResult.ok().setData(MockRuleGenerator.generate(root));
    }
}
