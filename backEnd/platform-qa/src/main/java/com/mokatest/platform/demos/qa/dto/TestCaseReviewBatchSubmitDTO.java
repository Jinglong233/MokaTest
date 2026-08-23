package com.mokatest.platform.demos.qa.dto;

import lombok.Data;

import java.util.List;

/**
 * 批量提交评审结论请求
 */
@Data
public class TestCaseReviewBatchSubmitDTO {

    private Integer reviewId;

    private List<Integer> testCaseIds;

    /**
     * 结论: PASS/REJECT
     */
    private String conclusion;

    /**
     * 评审意见（REJECT 时必填）
     */
    private String comment;
}
