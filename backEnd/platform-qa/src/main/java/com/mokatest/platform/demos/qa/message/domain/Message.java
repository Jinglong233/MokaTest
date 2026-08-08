package com.mokatest.platform.demos.qa.message.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import lombok.Data;

import java.util.Date;

/**
 * 站内信消息实体
 */
@TableName("message")
@Data
public class Message {

    @TableId(type = IdType.AUTO)
    private Integer id;

    /**
     * 接收人ID
     */
    private Integer receiverId;

    /**
     * 发送人ID（操作人）
     */
    private Integer senderId;

    /**
     * 事件类型
     */
    private MessageEventType eventType;

    /**
     * 使用的模板编码
     */
    private String templateCode;

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 业务类型：bug / requirement
     */
    private String bizType;

    /**
     * 业务对象ID
     */
    private Integer bizId;

    /**
     * 所属团队ID
     */
    private Integer teamId;

    /**
     * 所属项目ID
     */
    private Integer projectId;

    /**
     * 扩展数据（JSON）
     */
    private String extraData;

    /**
     * 是否已读：0-未读 1-已读
     */
    private Integer isRead;

    /**
     * 阅读时间
     */
    private Date readTime;

    @TableLogic
    private Integer isDeleted;

    /**
     * 删除时间
     */
    private Date deletedAt;

    /**
     * 创建时间
     */
    private Date createTime;
}
