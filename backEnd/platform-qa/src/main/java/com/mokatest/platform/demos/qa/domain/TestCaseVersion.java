package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 用例版本快照实体（REVIEWED 基线后的每次修改生成快照）
 */
@TableName(value = "test_case_version")
@Data
public class TestCaseVersion {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer testCaseId;

    /**
     * 版本号，从1递增
     */
    private Integer versionNo;

    /**
     * 用例完整快照（JSON）
     */
    private String snapshot;

    private String changeNote;

    private Integer createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
