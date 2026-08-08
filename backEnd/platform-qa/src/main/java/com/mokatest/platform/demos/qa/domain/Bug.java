package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * BUG池实体
 * @TableName bug
 */
@TableName(value = "bug")
@Data
public class Bug {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String bugCode;

    private String title;

    private String description;

    private String reproduceSteps;

    private String severity;

    private String priority;

    private String status;

    private Integer projectId;

    private Integer moduleId;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date deadline;

    private String environment;

    private String foundVersion;

    private String fixedVersion;

    private String reproduceRate;

    private String closeReason;

    private String tags;

    private Integer requirementId;

    private Integer testCaseId;

    private Integer planCaseId;

    private Integer reporterId;

    private Integer assigneeId;

    private Integer createUserId;

    private Integer updateUserId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDeleted;

    private Date deletedAt;
}
