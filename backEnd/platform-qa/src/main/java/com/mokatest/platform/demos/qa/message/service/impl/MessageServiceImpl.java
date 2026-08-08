package com.mokatest.platform.demos.qa.message.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mokatest.platform.demos.qa.message.domain.Message;
import com.mokatest.platform.demos.qa.message.dto.MessageContext;
import com.mokatest.platform.demos.qa.message.mapper.MessageMapper;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import com.mokatest.platform.demos.qa.message.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 消息 Service 实现
 *
 * 发送时机约定：若当前线程处于事务中，则注册 afterCommit 回调，
 * 等主事务提交成功后再异步发送，避免主事务回滚产生幽灵通知；
 * 不在事务中则直接异步发送。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {

    private final MessageAsyncSender messageAsyncSender;
    private final NotifyDispatchService notifyDispatchService;

    @Override
    public void send(MessageContext context) {
        if (context == null) {
            return;
        }
        Message message = notifyDispatchService.buildMessage(context);
        if (message == null) {
            return;
        }
        runAfterCommit(() -> messageAsyncSender.saveMessages(java.util.Collections.singletonList(message)));
    }

    @Override
    public void sendBatch(List<MessageContext> contexts) {
        if (contexts == null || contexts.isEmpty()) {
            return;
        }
        List<Message> messages = notifyDispatchService.buildMessages(contexts);
        if (messages.isEmpty()) {
            return;
        }
        runAfterCommit(() -> messageAsyncSender.saveMessages(messages));
    }

    /**
     * 当前存在事务时延迟到事务提交后执行，否则立即执行
     */
    private void runAfterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    @Override
    public SaResult listMyMessages(Integer page, Integer pageSize, Integer isRead) {
        Integer userId = StpUtil.getLoginIdAsInt();
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("receiver_id", userId)
                .eq("is_deleted", 0);
        if (isRead != null) {
            wrapper.eq("is_read", isRead);
        }
        wrapper.orderByDesc("create_time");
        IPage<Message> pageParam = new Page<>(page != null ? page : 1, pageSize != null ? pageSize : 20);
        IPage<Message> result = baseMapper.selectPage(pageParam, wrapper);

        List<MessageVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("records", voList);
        data.put("total", result.getTotal());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return SaResult.ok().setData(data);
    }

    @Override
    public SaResult getUnreadCount() {
        Integer userId = StpUtil.getLoginIdAsInt();
        int count = baseMapper.countUnread(userId);
        return SaResult.ok().setData(count);
    }

    @Override
    public SaResult markRead(Integer messageId) {
        Integer userId = StpUtil.getLoginIdAsInt();
        baseMapper.markRead(messageId, userId);
        return SaResult.ok();
    }

    @Override
    public SaResult markAllRead() {
        Integer userId = StpUtil.getLoginIdAsInt();
        baseMapper.markAllRead(userId);
        return SaResult.ok();
    }

    @Override
    public SaResult deleteMessage(Integer messageId) {
        Integer userId = StpUtil.getLoginIdAsInt();
        Message message = getById(messageId);
        if (message == null || !userId.equals(message.getReceiverId())) {
            return SaResult.error("消息不存在或无权删除");
        }
        message.setIsDeleted(1);
        message.setDeletedAt(new Date());
        updateById(message);
        return SaResult.ok();
    }

    private MessageVO convertToVO(Message message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setEventType(message.getEventType() != null ? message.getEventType().name() : null);
        vo.setBizType(message.getBizType());
        vo.setBizId(message.getBizId());
        vo.setTeamId(message.getTeamId());
        vo.setProjectId(message.getProjectId());
        vo.setIsRead(message.getIsRead());
        vo.setCreateTime(message.getCreateTime());
        vo.setExtraData(message.getExtraData());
        return vo;
    }
}
