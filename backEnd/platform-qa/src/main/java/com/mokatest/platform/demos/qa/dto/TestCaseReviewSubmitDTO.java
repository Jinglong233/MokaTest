package com.mokatest.platform.demos.qa.dto;

import lombok.Data;

/**
 * 提交评审结论请求
 */
@Data
public class TestCaseReviewSubmitDTO {

    private Integer reviewId;

    private Integer testCaseId;

    /**
     * 结论: PASS/REJECT
     */
    private String conclusion;

    /**
     * 评审意见（REJECT 时必填）
     */
    private String comment;
}
