package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 角色表
 */
@TableName(value = "role")
@Data
public class Role {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色名称
     */
    private String name;

    /**
     * 角色编码
     */
    private String code;

    /**
     * 范围类型：SYSTEM-内置角色，TEMPLATE-自定义模板
     */
    private String scopeType;

    /**
     * TEMPLATE 范围：NULL=全局模板，project.id=项目模板
     */
    private Long scopeId;

    /**
     * 所属团队，NULL 表示系统预设角色（旧字段，保留兼容）
     */
    private Long teamId;

    /**
     * 角色说明
     */
    private String description;

    /**
     * 是否系统预设角色：0-否，1-是
     */
    private Integer isSystem;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
