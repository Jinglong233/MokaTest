package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 测试计划报告（元数据，统计数据实时计算不落库）
 */
@TableName(value = "test_plan_report")
@Data
public class TestPlanReport {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String reportName;

    /** 报告类型（预留）：PLAN */
    private String reportType;

    private Integer planId;

    private Integer projectId;

    /** 触发方式：MANUAL-手动 AUTO-计划完成自动 */
    private String triggerType;

    /** 报告总结（富文本） */
    private String summary;

    /** 报告内容快照（JSON，生成时固化：统计+图表+缺陷列表+用例明细） */
    private String content;

    /** 通过率快照（生成时），列表页直接展示 */
    private Integer passRate;

    /** 执行结果快照（生成时）：NONE/ALL_PASS/NOT_PASS */
    private String execResult;

    private Integer createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    @TableLogic
    private Integer isDeleted;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deletedAt;
}
