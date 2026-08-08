package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.service.QaOverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 质量管理 - 项目概览接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看项目概览：qa:overview:view
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/overview")
@RequiredArgsConstructor
public class QaOverviewController {

    private final QaOverviewService qaOverviewService;

    /**
     * 获取项目质量管理概览
     * 权限：qa:overview:view
     */
    @SaCheckPermission("qa:overview:view")
    @GetMapping("/{projectId}")
    public SaResult getProjectOverview(@PathVariable Integer projectId) {
        return qaOverviewService.getProjectOverview(projectId);
    }
}
