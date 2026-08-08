package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 用户角色授权表
 *
 * 记录用户在某个范围（团队/项目）下被授予的角色。
 *   scope_id 含义由关联的 role.scope_type 决定：TEAM 角色填 team.id，PROJECT 角色填 project.id。
 *   同用户在同一个 scope 下不能重复授予同一个角色。
 */
@TableName(value = "user_role")
@Data
public class UserRole {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID（关联 user.id）
     */
    private Long userId;

    /**
     * 角色ID（关联 role.id）
     */
    private Long roleId;

    /**
     * 授权范围ID：团队角色填 team.id，项目角色填 project.id
     */
    private Long scopeId;

    /**
     * 授权人ID
     */
    private Long grantedBy;

    /**
     * 授权时间
     */
    private Date grantedTime;

    /**
     * 过期时间，NULL 表示永久有效
     */
    private Date expireTime;

    /**
     * 状态：0-已撤销，1-生效中
     */
    private Integer status;
}
