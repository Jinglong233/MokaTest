package com.mokatest.platform.demos.domain.ui.vo;


import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * @Author JingLong
 * @Description 用户VO
 * @Date 2025/7/25 14:41
 **/
@Data
public class UserVO {
    private Long id;

    /**
     * 用户名，唯一
     */
    private String username;
    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 状态：0-禁用，1-正常
     */
    private Integer status;

    /**
     * 全局角色：super_admin-超级管理员，user-普通用户
     */
    private String role;

    /**
     * 所属团队及角色列表（超管查看全部用户时填充）
     */
    private List<UserTeamVO> teams;

    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;

}
