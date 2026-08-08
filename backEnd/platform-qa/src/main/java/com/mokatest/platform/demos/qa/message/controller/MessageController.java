package com.mokatest.platform.demos.qa.message.controller;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 消息 Controller
 */
@Slf4j
@RestController
@RequestMapping("/qa/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /**
     * 我的消息列表
     */
    @GetMapping("/list")
    public SaResult list(@RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "20") Integer pageSize,
                         @RequestParam(required = false) Integer isRead) {
        return messageService.listMyMessages(page, pageSize, isRead);
    }

    /**
     * 未读消息数
     */
    @GetMapping("/unreadCount")
    public SaResult unreadCount() {
        return messageService.getUnreadCount();
    }

    /**
     * 标记单条已读
     */
    @PostMapping("/read/{id}")
    public SaResult markRead(@PathVariable Integer id) {
        return messageService.markRead(id);
    }

    /**
     * 全部已读
     */
    @PostMapping("/readAll")
    public SaResult markAllRead() {
        return messageService.markAllRead();
    }

    /**
     * 删除消息
     */
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return messageService.deleteMessage(id);
    }
}
