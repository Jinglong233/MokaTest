package com.mokatest.platform.demos.common;

import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * SuperAdminChecker 实现：委托 automation 的 ProjectPermissionChecker
 * （platform-ai 的 AiConfigServiceImpl 通过接口依赖，避免反向依赖 automation）
 */
@Component
public class SuperAdminCheckerImpl implements SuperAdminChecker {

    @Autowired
    private ProjectPermissionChecker projectPermissionChecker;

    @Override
    public boolean isSuperAdmin(String loginId) {
        return projectPermissionChecker.isSuperAdmin(loginId);
    }
}
