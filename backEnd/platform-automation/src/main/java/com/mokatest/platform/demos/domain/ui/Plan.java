package com.mokatest.platform.demos.domain.ui;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 
 * @TableName plan
 */
@TableName(value ="plan")
@Data
public class Plan implements Serializable {
    /**
     * id
     */
    @TableId
    private Integer id;

    /**
     * 计划运行设置
     */
    private String planRunningSetting;

    /**
     * 所属项目id
     */
    private Integer projectId;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * cron表达式
     */
    private String cronExpression;

    /**
     * 计划描述
     */
    private String description;

    /**
     * 场景执行类型（顺序、并发）
     */
    private Object executionType;

    /**
     * 场景状态提取
     */
    @TableField(updateStrategy = FieldStrategy.IGNORED)
    private Integer sceneStatusExtract;

    /**
     * 参数
     */
    private Object params;

    /**
     * 任务类型
     */
    private Object taskType;

    /**
     * 状态
     */
    private Object status;

    /**
     * 创建人id
     */
    private Integer createUserId;

    /**
     * 更新人id
     */
    private Integer updateUserId;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 更新时间
     */
    private Date updatedAt;

    /**
     * 是否被激活 1：是 0：否
     */
    private Integer isActive;

    /**
     * 执行后是否发送 Webhook 通知 1：开启 0：关闭
     * 
     * 与 plan_webhook 表解耦：计划只管开关，通知配置在独立模块管理。
     * 执行完成后，若此字段为 1，则查询该项目下所有启用的 Webhook 配置逐一发送。
     */
    private Integer webhookEnabled;

    /**
     * 关联的 Webhook 配置ID，逗号分隔（如 "1,2,3"）
     * 
     * 计划开启通知后，只向此处指定的 Webhook 配置发送消息。
     * 若为 null 或空字符串，则默认发送该项目下所有启用的 Webhook 配置（向下兼容）。
     */
    private String webhookIds;

    /**
     * 计划分类：UI/API/MIXED
     */
    private String planCategory;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 删除时间
     */
    private Date deletedAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}