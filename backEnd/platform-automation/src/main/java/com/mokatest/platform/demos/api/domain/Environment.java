package com.mokatest.platform.demos.api.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.mokatest.platform.demos.api.domain.requestModel.DataBaseParameter;
import com.mokatest.platform.demos.api.domain.requestModel.RequestParameter;
import com.mokatest.platform.demos.api.domain.requestModel.ServeParameter;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 环境配置表
 * @TableName environment
 */
@TableName(value = "environment",autoResultMap = true)
@Data
public class Environment {
    /**
     * 环境id
     */
    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 环境名称
     */
    private String envName;

    /**
     * 所属团队id
     */
    private Integer teamId;

    /**
     * cookie列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequestParameter> cookies;

    /**
     * header列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequestParameter> headers;


    /**
     * 环境变量列表
     */

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<RequestParameter> envVar;

    /**
     * 服务
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<ServeParameter> serve;

    /**
     * 数据库列表
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<DataBaseParameter> dbs;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建者id
     */
    private Integer createUserId;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 更新人id
     */
    private Integer updateUserId;

    /**
     * 是否已删除：0-未删除，1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /**
     * 删除时间
     */
    private Date deletedAt;
}