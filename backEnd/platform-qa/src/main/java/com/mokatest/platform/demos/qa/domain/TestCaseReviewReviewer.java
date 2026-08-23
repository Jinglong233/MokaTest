package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 评审单评审人实体（冗余，便于"待我评审"查询）
 */
@TableName(value = "test_case_review_reviewer")
@Data
public class TestCaseReviewReviewer {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer reviewId;

    private Integer reviewerId;
}
