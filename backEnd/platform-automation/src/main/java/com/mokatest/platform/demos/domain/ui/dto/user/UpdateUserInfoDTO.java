package com.mokatest.platform.demos.domain.ui.dto.user;

import lombok.Data;

/**
 * @Author JingLong
 * @Description
 * @Date 2026-03-21 20:10
 **/
@Data
public class UpdateUserInfoDTO {
    /**
     * 主键ID
     */
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
     * 状态：1-正常，2-禁用
     */
    private Integer status;

}
