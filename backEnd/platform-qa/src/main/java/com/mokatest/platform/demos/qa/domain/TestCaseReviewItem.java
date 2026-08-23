package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 评审明细实体（用例×评审人）
 */
@TableName(value = "test_case_review_item")
@Data
public class TestCaseReviewItem {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer reviewId;

    private Integer testCaseId;

    private Integer reviewerId;

    /**
     * 结论: PENDING/PASS/REJECT
     */
    private String conclusion;

    /**
     * 评审意见
     */
    private String comment;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date reviewTime;
}
