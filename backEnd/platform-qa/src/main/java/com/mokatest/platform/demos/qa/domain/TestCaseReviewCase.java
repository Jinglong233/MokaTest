package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 评审单圈选用例实体
 */
@TableName(value = "test_case_review_case")
@Data
public class TestCaseReviewCase {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer reviewId;

    private Integer testCaseId;

    /**
     * 最终结论: PENDING/PASS/REJECT
     */
    private String finalResult;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
