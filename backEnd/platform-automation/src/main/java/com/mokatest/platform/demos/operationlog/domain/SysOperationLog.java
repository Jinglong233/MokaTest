package com.mokatest.platform.demos.operationlog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 系统通用操作日志实体
 */
@TableName(value = "sys_operation_log")
@Data
public class SysOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String module;

    private String operateType;

    private String targetType;

    private Long targetId;

    private String targetName;

    private Integer operatorId;

    private String operatorName;

    private String description;

    private String requestParams;

    private Integer responseCode;

    private String responseMsg;

    private String ip;

    private String userAgent;

    private Integer durationMs;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date operateTime;
}
