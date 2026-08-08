package com.mokatest.platform.demos.qa.message.dto;

import com.mokatest.platform.demos.qa.message.enums.MessageEventType;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 消息发送上下文
 */
@Data
@Builder
public class MessageContext {

    /**
     * 事件类型
     */
    private MessageEventType eventType;

    /**
     * 发送人ID（操作人）
     */
    private Integer senderId;

    /**
     * 接收人ID
     */
    private Integer receiverId;

    /**
     * 接收人角色（REPORTER/ASSIGNEE/OWNER/PARTICIPANT/CREATOR/MENTION），
     * 用于项目级通知规则按角色开关过滤；为空时不过滤
     */
    private String receiverRole;

    /**
     * 所属项目ID
     */
    private Integer projectId;

    /**
     * 所属团队ID
     */
    private Integer teamId;

    /**
     * 业务类型
     */
    private String bizType;

    /**
     * 业务对象ID
     */
    private Integer bizId;

    /**
     * 模板变量
     */
    private Map<String, Object> params;

    /**
     * 来源对象快照（用于消息详情直接展示，无需切换项目）
     */
    private Map<String, Object> snapshot;
}
