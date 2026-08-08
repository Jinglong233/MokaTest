package com.mokatest.platform.demos.service;

import cn.dev33.satoken.util.SaResult;

public interface RoleService {

    SaResult getRoleList(Long teamId);

    SaResult getRoleDetail(Long id);

    SaResult createRole(com.mokatest.platform.demos.domain.ui.Role role);

    SaResult updateRole(com.mokatest.platform.demos.domain.ui.Role role);

    SaResult deleteRole(Long id);

    SaResult getRolePermissions(Long roleId);

    SaResult assignPermissions(Long roleId, java.util.List<Long> permissionIds);
}
