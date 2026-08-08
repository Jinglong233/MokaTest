package com.mokatest.platform.demos.qa.domain.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * BUG池列表视图对象
 */
@Data
public class BugVO {

    private Integer id;

    private String bugCode;

    private String title;

    private String description;

    private String reproduceSteps;

    private String severity;

    private String priority;

    private String status;

    private Integer projectId;

    private Integer requirementId;

    /** 关联需求标题（后端关联查询） */
    private String requirementTitle;

    private Integer testCaseId;

    /** 关联用例名称（后端关联查询） */
    private String caseName;

    private Integer reporterId;

    /** 报告人名称（后端关联查询） */
    private String reporterName;

    private Integer assigneeId;

    /** 指派人名称（后端关联查询） */
    private String assigneeName;

    private Integer moduleId;

    /** 模块名称（后端关联查询） */
    private String moduleName;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date deadline;

    private String environment;

    private String foundVersion;

    private String fixedVersion;

    private String reproduceRate;

    private String closeReason;

    private String tags;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    /** 创建人ID */
    private Integer createUserId;

    /** 创建人名称（后端关联查询） */
    private String createUserName;

    /** 发现计划名称（后端关联查询） */
    private String planName;
}
