package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 需求池实体
 * @TableName requirement
 */
@TableName(value = "requirement")
@Data
public class Requirement {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private String reqCode;

    private String title;

    private String description;

    private String priority;

    private String status;

    private Integer projectId;

    private Integer moduleId;

    private Integer parentId;

    private String reqType;

    private String source;

    private String participants;

    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date expectReleaseTime;

    private String tags;

    private String version;

    private Integer ownerId;

    private Integer createUserId;

    private Integer updateUserId;

    private Date createTime;

    private Date updateTime;

    @TableLogic
    private Integer isDeleted;

    private Date deletedAt;
}
