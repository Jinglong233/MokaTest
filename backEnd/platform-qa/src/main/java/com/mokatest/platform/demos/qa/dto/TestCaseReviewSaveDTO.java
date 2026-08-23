package com.mokatest.platform.demos.qa.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 创建用例评审单请求
 */
@Data
public class TestCaseReviewSaveDTO {

    private Integer projectId;

    private Integer moduleId;

    private String title;

    private String description;

    /**
     * 通过规则: ALL全票 / MAJORITY多数
     */
    private String passRule;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deadline;

    private List<Integer> caseIds;

    private List<Integer> reviewerIds;
}
