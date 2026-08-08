package com.mokatest.platform.demos.domain.ui.vo;

import lombok.Data;

import java.util.Date;

/**
 * 用户在团队下某个项目的角色分配 VO
 */
@Data
public class UserProjectRoleVO {

    /**
     * 项目ID
     */
    private Long projectId;

    /**
     * 项目名称
     */
    private String projectName;

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
     * 过期时间，NULL 表示永久有效
     */
    private Date expireTime;
}
