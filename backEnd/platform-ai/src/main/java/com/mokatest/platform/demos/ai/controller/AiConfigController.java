package com.mokatest.platform.demos.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
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
 * AI 配置管理接口（项目维度）
 *
 * 鉴权：project:aiconfig:update，项目 owner/团队管理员自动持有，普通成员需角色分配；
 * 数据按 ProjectContextHolder 当前项目过滤，跨项目操作在服务层归属校验拦截。
 */
@SaCheckPermission("project:aiconfig:update")
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
