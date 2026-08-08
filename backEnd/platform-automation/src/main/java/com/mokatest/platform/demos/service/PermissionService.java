package com.mokatest.platform.demos.service;

import cn.dev33.satoken.util.SaResult;

public interface PermissionService {

    SaResult getPermissionTree();

    SaResult getAllPermissions();
}
