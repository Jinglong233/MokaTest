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

    /** 父评论ID（0=顶层评论，支持二级回复） */
    private Integer parentId;

    private String content;

    private String mentionUserIds;

    private Integer createUserId;

    /** 创建人昵称（冗余存储，避免列表 join） */
    private String createUserName;

    /** 创建人头像URL（非表字段，列表查询时按 create_user_id 关联填充） */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private String createUserAvatar;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
