package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 用例操作日志实体
 */
@TableName(value = "test_case_operation_log")
@Data
public class TestCaseOperationLog {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer testCaseId;

    /**
     * 操作类型: CREATE/UPDATE/TRANSITION/DELETE/REVIEW_PASS/REVIEW_REJECT/VERSION/REVIEW_VOTE
     */
    private String operation;

    /**
     * 关联评审单ID（仅 REVIEW_VOTE 类型有值）
     */
    private Integer reviewId;

    private String fieldName;

    private String oldValue;

    private String newValue;

    /**
     * 操作意见（如流转/评审意见）
     */
    private String comment;

    private Integer operatorId;

    /**
     * 操作人昵称（冗余）
     */
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date operateTime;
}
