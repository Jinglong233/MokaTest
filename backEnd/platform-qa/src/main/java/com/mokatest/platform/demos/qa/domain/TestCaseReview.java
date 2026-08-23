package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 用例评审单实体
 */
@TableName(value = "test_case_review")
@Data
public class TestCaseReview {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String reviewCode;

    private Integer projectId;

    private Integer moduleId;

    private String title;

    private String description;

    /**
     * 状态: REVIEWING/COMPLETED/CANCELLED
     */
    private String status;

    /**
     * 通过规则: ALL全票 / MAJORITY多数
     */
    private String passRule;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deadline;

    private String resultSummary;

    private Integer creatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @TableLogic
    private Integer isDeleted;

    private Date deletedAt;
}
