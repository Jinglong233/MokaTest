package com.mokatest.platform.demos.domain.ui.vo;

import lombok.Data;

import java.util.Date;

/**
 * 团队成员VO
 */
@Data
public class TeamMemberVO {

    /**
     * team_member 表主键
     */
    private Long id;

    /**
     * 团队ID
     */
    private Long teamId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 手机号（已脱敏）
     */
    private String phone;

    /**
     * 邮箱（已脱敏）
     */
    private String email;

    /**
     * 头像URL
     */
    private String avatar;

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

    /**
     * 加入时间
     */
    private Date joinTime;

    /**
     * 是否为超级管理员
     */
    private Boolean superAdmin;

    /**
     * 是否为团队管理员（即 team.owner_id 指向的用户）
     */
    private Boolean teamOwner;
}
