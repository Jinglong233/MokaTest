package com.mokatest.platform.demos.api.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.api.domain.requestModel.ResponseSchema;
import com.mokatest.platform.demos.api.service.MockService;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import jakarta.annotation.Resource;

/**
 * Mock 辅助接口
 *
 * @author JingLong
 * @since 2026-06-17
 */
@RestController
@RequestMapping("/mock")
public class MockController {

    @Resource
    private MockService mockService;

    /**
     * 根据 Mock/Template 表达式生成单个值
     */
    @SaCheckPermission("auto:api:view")
    @RequestMapping("/generate")
    public SaResult generate(@RequestBody Map<String, String> body) {
        return mockService.generate(body.get("expression"));
    }

    /**
     * 按响应定义（ResponseSchema）预览生成的 Mock Body
     */
    @SaCheckPermission("auto:api:view")
    @RequestMapping("/previewSchema")
    public SaResult previewSchema(@RequestBody ResponseSchema responseSchema) {
        return mockService.previewResponseSchema(responseSchema);
    }
}
