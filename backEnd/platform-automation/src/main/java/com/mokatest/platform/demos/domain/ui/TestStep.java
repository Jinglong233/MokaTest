package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mokatest.platform.demos.config.DynamicJsonTypeHandler;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 测试步骤主表
 * @TableName test_step
 */
@TableName(value ="test_step")
@Data
public class TestStep implements Serializable {
    /**
     * 步骤ID
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 步骤类型
     */
    private String stepType;

    /**
     * 步骤名称
     */
    private String stepName;

    /**
     * 步骤描述
     */
    private String description;

    /**
     * 父步骤ID
     */
    private Integer parentId;

    /**
     * 执行顺序
     */
    private Integer orderIndex;

    /**
     * 所属项目ID
     */
    private String projectId;

    /**
     * 所属场景ID
     */
    private String scenarioId;

    /**
     * 是否禁用 0：否1：是
     */
    private Integer isDisable;

    /**
     * 步骤详情
     */
    @TableField(typeHandler = DynamicJsonTypeHandler.class)
    private Object stepDetail;

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
     * 更新人ID
     */
    private String updateUserId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}