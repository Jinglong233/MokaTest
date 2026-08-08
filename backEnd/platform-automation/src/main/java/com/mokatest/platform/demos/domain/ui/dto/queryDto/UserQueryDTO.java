package com.mokatest.platform.demos.domain.ui.dto.queryDto;


import lombok.Data;

@Data
public class UserQueryDTO extends BasePageQueryDTO {

    /**
     * 用户名，唯一
     */
    private String username;


    /**
     * 昵称
     */
    private String nickname;

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
     * 项目ID（可选）。传入时按项目维度收窄可选人：
     * 项目级授权成员 ∪ 项目所属团队的团队管理员，剔除超管。
     * 不传时保持原有「同团队成员」口径。
     */
    private Integer projectId;


}
