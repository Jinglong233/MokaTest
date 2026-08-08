package com.mokatest.platform.demos.qa.config.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.config.domain.ProjectConfig;
import com.mokatest.platform.demos.qa.config.service.ProjectConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 项目级统一配置接口（通知规则 / 字段显隐等，差量存储）。
 *
 * 权限说明：
 *   读取配置：project:view（项目成员可读）
 *   修改配置：project:config:update（项目管理员）
 */
@Slf4j
@RestController
@RequestMapping("/qa/projectConfig")
@RequiredArgsConstructor
public class ProjectConfigController {

    private final ProjectConfigService projectConfigService;

    /**
     * 查询项目全部配置差量
     */
    @SaCheckPermission("project:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId) {
        return projectConfigService.listByProject(projectId);
    }

    /**
     * 全量保存项目配置差量（按 config_type 整体替换，未提交的 key 恢复默认）
     */
    @SaCheckPermission("project:config:update")
    @PostMapping("/saveAll")
    public SaResult saveAll(@RequestBody SaveAllRequest request) {
        if (request == null || request.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        return projectConfigService.saveAll(request.getProjectId(), request.getConfigs());
    }

    /**
     * 重置项目全部配置为默认
     */
    @SaCheckPermission("project:config:update")
    @PostMapping("/reset")
    public SaResult reset(@RequestBody ResetRequest request) {
        if (request == null || request.getProjectId() == null) {
            return SaResult.error("缺少项目ID");
        }
        return projectConfigService.resetByProject(request.getProjectId());
    }

    @Data
    public static class SaveAllRequest {
        private Integer projectId;
        private List<ProjectConfig> configs;
    }

    @Data
    public static class ResetRequest {
        private Integer projectId;
    }
}
