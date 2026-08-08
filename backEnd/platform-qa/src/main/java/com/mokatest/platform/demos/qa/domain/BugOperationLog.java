package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * Bug操作日志实体
 */
@TableName(value = "bug_operation_log")
@Data
public class BugOperationLog {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer bugId;

    private String fieldName;

    private String oldValue;

    private String newValue;

    private Integer operatorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date operateTime;
}
