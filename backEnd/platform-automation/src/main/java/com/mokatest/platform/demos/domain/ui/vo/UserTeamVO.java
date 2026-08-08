package com.mokatest.platform.demos.domain.ui.vo;

import lombok.Data;

/**
 * 用户所属团队及角色信息 VO
 */
@Data
public class UserTeamVO {

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 团队名称
     */
    private String teamName;

    /**
     * 角色ID
     */
    private Long roleId;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 成员状态：0-禁用，1-正常
     */
    private Integer status;
}
