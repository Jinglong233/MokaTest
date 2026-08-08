package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 测试计划与用例关联实体
 */
@TableName(value = "test_plan_case")
@Data
public class TestPlanCase {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer planId;

    private Integer testCaseId;

    private Integer sort;

    private String executeResult;

    private String executeRemark;

    private Integer executeUserId;

    private Integer bugId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date executeTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
