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
 * @TableName report
 */
@TableName(value ="report")
@Data
public class Report implements Serializable {
    /**
     * 报告id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 所属项目id
     */
    private Integer projectId;

    /**
     * 场景数量
     */
    private Integer sceneNumber;

    /**
     * 步骤总数
     */
    private Integer stepNumber;

    /**
     * 断言总数
     */
    private Integer assertNumber;

    /**
     * 执行时长
     */
    private Double executionDuration;

    /**
     * 执行者id
     */
    private String executionUserId;

    /**
     * 执行者姓名
     */
    private String executionUserName;

    /**
     * 任务类型
     */
    private String taskType;

    /**
     * 场景执行错误数量
     */
    private Integer sceneErrorNumber;

    /**
     * 场景执行成功数量
     */
    private Integer sceneSuccessNumber;

    /**
     * 断言成功数量
     */
    private Integer assertSuccessNumber;

    /**
     * 断言失败数量
     */
    private Integer assertErrorNumber;

    /**
     * 断言跳过数量
     */
    private Integer assertSkipNumber;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 关联计划id
     */
    private Integer planId;

    /**
     * 计划名称
     */
    private String planName;

    /**
     * 报告名称
     */
    private String reportName;

    /**
     * 视频执行地址
     */
    private Object videoPath;

    /**
     * 场景列表
     */
    private Object scenes;

    /**
     * 步骤成功数量
     */
    private Integer stepSuccessNumber;

    /**
     * 步骤失败数量
     */
    private Integer stepErrorNumber;

    /**
     * 步骤跳过数量
     */
    private Integer stepSkipNumber;

    /**
     * 报告分类：UI/API/MIXED
     */
    private String reportCategory;

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