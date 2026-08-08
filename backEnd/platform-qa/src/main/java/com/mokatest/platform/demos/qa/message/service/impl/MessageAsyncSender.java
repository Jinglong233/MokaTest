package com.mokatest.platform.demos.qa.message.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.message.domain.Message;
import com.mokatest.platform.demos.qa.message.mapper.MessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息异步落库器。
 * 决策与渲染已在 {@link NotifyDispatchService} 同步完成，
 * 由 {@link MessageServiceImpl} 在事务提交后调用本类批量落库，
 * 避免「异步线程先于主事务提交落库」导致的幽灵通知（主事务回滚但消息已发出）。
 */
@Slf4j
@Component
public class MessageAsyncSender extends ServiceImpl<MessageMapper, Message> {

    @Async
    public void saveMessages(List<Message> messages) {
        try {
            if (messages == null || messages.isEmpty()) {
                return;
            }
            boolean success = saveBatch(messages);
            if (success) {
                log.info("消息发送成功，数量={}，事件={}", messages.size(), messages.get(0).getEventType());
            } else {
                log.warn("消息保存失败，数量={}", messages.size());
            }
        } catch (Exception e) {
            log.error("异步保存消息失败", e);
        }
    }
}
