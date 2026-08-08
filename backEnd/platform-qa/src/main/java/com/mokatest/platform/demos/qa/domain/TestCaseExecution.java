package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 用例执行历史记录实体
 */
@TableName(value = "test_case_execution")
@Data
public class TestCaseExecution {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer testCaseId;

    private String testCaseName;

    private Integer planId;

    private String result;

    private String remark;

    private Integer executeUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date executeTime;

    private Integer bugId;

    private Integer autoReportId;
}
