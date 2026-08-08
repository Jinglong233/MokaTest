package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 团队成员表
 * @TableName team_member
 */
@TableName(value ="team_member")
@Data
public class TeamMember {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
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
     * 角色ID
     */
    private Long roleId;

    /**
     * 团队内角色：admin-管理员，member-普通成员（旧字段，兼容保留）
     */
    private String role;

    /**
     * 成员状态：0-禁用（如被移除），1-正常
     */
    private Integer status;

    /**
     * 加入时间
     */
    private Date joinTime;
}