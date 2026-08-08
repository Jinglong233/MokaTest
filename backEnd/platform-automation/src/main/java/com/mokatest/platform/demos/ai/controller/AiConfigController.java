package com.mokatest.platform.demos.ai.controller;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.ai.domain.AiConfig;
import com.mokatest.platform.demos.ai.service.AiConfigService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 配置管理接口（系统级）
 *
 * 鉴权：服务层强制校验超级管理员（对齐操作日志模块的做法），
 * 不走 @SaCheckPermission，因为 AI 配置属于平台级能力，不属于任何团队/项目权限域。
 */
@RestController
@RequestMapping("/ai/config")
public class AiConfigController {

    @Resource
    private AiConfigService aiConfigService;

    /**
     * 获取当前配置（apiKey 打码返回）
     */
    @GetMapping
    public SaResult getConfig() {
        return aiConfigService.getMaskedConfig();
    }

    /**
     * 配置档案列表（apiKey 打码，生效行置顶）
     */
    @GetMapping("/list")
    public SaResult list() {
        return aiConfigService.listConfigs();
    }

    /**
     * 保存配置
     */
    @PostMapping("/save")
    public SaResult save(@RequestBody AiConfig config) {
        return aiConfigService.saveConfig(config);
    }

    /**
     * 激活指定配置为唯一生效
     */
    @PostMapping("/activate/{id}")
    public SaResult activate(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return aiConfigService.activate(id);
    }

    /**
     * 停用生效配置（全平台 AI 进入未启用状态）
     */
    @PostMapping("/deactivate/{id}")
    public SaResult deactivate(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return aiConfigService.deactivate(id);
    }

    /**
     * 删除配置（生效中的禁止删除）
     */
    @PostMapping("/delete/{id}")
    public SaResult delete(@org.springframework.web.bind.annotation.PathVariable Long id) {
        return aiConfigService.deleteConfig(id);
    }

    /**
     * 连通性测试（实际调一次 chat 接口）
     */
    @PostMapping("/test")
    public SaResult test(@RequestBody AiConfig config) {
        return aiConfigService.testConnection(config);
    }
}
