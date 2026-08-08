package com.mokatest.platform.demos.domain.ui.dto.addDTO;

import lombok.Data;

/**
 * 团队成员操作DTO（移除、启用/禁用）
 */
@Data
public class TeamMemberOperateDTO {

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 状态：0-禁用，1-正常（移除时不传）
     */
    private Integer status;
}
