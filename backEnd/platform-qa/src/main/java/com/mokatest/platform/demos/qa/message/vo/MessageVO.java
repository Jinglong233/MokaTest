package com.mokatest.platform.demos.qa.message.vo;

import lombok.Data;

import java.util.Date;

/**
 * 消息 VO
 */
@Data
public class MessageVO {

    private Integer id;

    private String title;

    private String content;

    private String eventType;

    private String bizType;

    private Integer bizId;

    private Integer teamId;

    private Integer projectId;

    private Integer isRead;

    private Date createTime;

    private String extraData;
}
