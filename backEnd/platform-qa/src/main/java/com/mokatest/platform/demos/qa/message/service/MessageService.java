package com.mokatest.platform.demos.qa.message.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.message.domain.Message;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;

import java.util.List;

/**
 * 消息 Service
 */
public interface MessageService extends IService<Message> {

    /**
     * 发送单条消息（异步）
     */
    void send(MessageContext context);

    /**
     * 批量发送消息（异步）
     */
    void sendBatch(List<MessageContext> contexts);

    /**
     * 查询我的消息列表
     */
    SaResult listMyMessages(Integer page, Integer pageSize, Integer isRead);

    /**
     * 获取未读消息数
     */
    SaResult getUnreadCount();

    /**
     * 标记单条已读
     */
    SaResult markRead(Integer messageId);

    /**
     * 全部已读
     */
    SaResult markAllRead();

    /**
     * 删除消息（逻辑删除）
     */
    SaResult deleteMessage(Integer messageId);
}
