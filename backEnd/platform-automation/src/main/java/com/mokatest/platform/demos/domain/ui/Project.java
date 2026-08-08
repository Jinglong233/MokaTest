package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.mokatest.platform.demos.config.DynamicJsonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @TableName project
 */
@TableName(value = "project")
@Data
public class Project implements Serializable {
    /**
     * 项目id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 描述
     */
    private String description;

    /**
     * 所属团队id
     */
    private Integer teamId;

    /**
     * 更新人ID
     */
    private String updateUserId;

    /**
     * 覆盖率
     */
    private Integer coverage;

    /**
     * 状态
     */
    private Object status;

    /**
     * api测试用例数量
     */
    private Integer apiTotal;

    /**
     * UI测试场景数量
     */
    private Integer uiTotal;

    /**
     * 性能测试用例数量
     */
    private Integer performanceTotal;

    /**
     * 计划数量
     */
    private Integer planTotal;

    /**
     * UI测试报告通过率
     */
    private Integer uiPass;

    /**
     * 标签
     */
    @TableField(typeHandler = DynamicJsonTypeHandler.class)
    private Object tagClassify;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updatedAt;

    /**
     * 创建人ID
     */
    private String createUserId;

    /**
     * 创建人姓名
     */
    private String createUserName;

    /**
     * 项目负责人ID
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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deletedAt;

    /**
     * 当前登录用户在本项目的角色名称（列表展示用，不入库）
     */
    @TableField(exist = false)
    private String myRoleName;

    /**
     * 当前登录用户在本项目的角色编码（列表展示用，不入库）
     */
    @TableField(exist = false)
    private String myRoleCode;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}