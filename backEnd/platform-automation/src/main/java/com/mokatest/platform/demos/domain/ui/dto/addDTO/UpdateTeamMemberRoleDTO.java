package com.mokatest.platform.demos.domain.ui.dto.addDTO;

import lombok.Data;

/**
 * 修改团队成员角色DTO
 */
@Data
public class UpdateTeamMemberRoleDTO {

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 角色ID
     */
    private Long roleId;
}
