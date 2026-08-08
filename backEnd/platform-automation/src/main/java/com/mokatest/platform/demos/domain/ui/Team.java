package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @TableName team
 */
@TableName(value ="team")
@Data
public class Team implements Serializable {
    /**
     * 团队id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 团队名称
     */
    private String teamName;

    /**
     * 团队人数
     */
    private Integer teamNumber;

    /**
     * 状态：0-禁用，1-正常
     */
    private Integer status;

    /**
     * 是否个人团队：0-否，1-是
     */
    private Integer isPersonal;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 创建人ID
     */
    private String createUserId;

    /**
     * 更新人id
     */
    private String updateUserId;

    /**
     * 团队管理员ID（团队管理员唯一来源，与 project.owner_id 模型对齐）
     */
    private Long ownerId;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 删除时间
     */
    private Date deletedAt;

    /**
     * 当前登录用户在该团队是否为管理员（不入库，仅用于团队切换下拉过滤）
     */
    @TableField(exist = false)
    private Boolean manageable;

    /**
     * 创建人显示名（不入库，列表展示用，避免前端再查用户）
     */
    @TableField(exist = false)
    private String createUserName;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}