package com.mokatest.platform.demos.operationlog.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 登录日志实体（安全审计数据，与业务操作日志分离）
 */
@TableName(value = "sys_login_log")
@Data
public class SysLoginLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作类型：LOGIN / LOGOUT
     */
    private String operation;

    /**
     * 用户ID（登录失败且用户不存在时为 null）
     */
    private Long userId;

    /**
     * 登录使用的用户名（按输入记录）
     */
    private String username;

    /**
     * 用户昵称（冗余，便于展示）
     */
    private String nickname;

    /**
     * 状态：SUCCESS / FAIL
     */
    private String status;

    /**
     * 失败原因（用户不存在/密码错误等）
     */
    private String message;

    private String ip;

    /**
     * IP 归属地（省/市·运营商，离线解析；内网IP/未知等情况见 IpRegionService）
     */
    private String ipRegion;

    private String userAgent;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date operateTime;
}
