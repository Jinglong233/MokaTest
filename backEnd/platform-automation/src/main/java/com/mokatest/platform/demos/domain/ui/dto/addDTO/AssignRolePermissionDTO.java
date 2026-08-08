package com.mokatest.platform.demos.domain.ui.dto.addDTO;

import lombok.Data;

import java.util.List;

/**
 * 角色分配权限DTO
 */
@Data
public class AssignRolePermissionDTO {

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 权限ID列表
     */
    private List<Long> permissionIds;
}
