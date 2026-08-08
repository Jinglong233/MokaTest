package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.service.AutoOverviewService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自动化测试 - 项目概览接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看自动化概览：auto:overview:view
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/auto/overview")
public class AutoOverviewController {

    @Resource
    private AutoOverviewService autoOverviewService;

    /**
     * 获取项目自动化测试概览
     * 权限：auto:overview:view
     */
    @SaCheckPermission("auto:overview:view")
    @GetMapping("/{projectId}")
    public SaResult getAutoOverview(@PathVariable Integer projectId) {
        return autoOverviewService.getAutoOverview(projectId);
    }
}
