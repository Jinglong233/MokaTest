package com.mokatest.platform.demos.qa.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * Bug评论实体
 */
@TableName(value = "bug_comment")
@Data
public class BugComment {

    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer bugId;

    private String content;

    private String mentionUserIds;

    private Integer createUserId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
