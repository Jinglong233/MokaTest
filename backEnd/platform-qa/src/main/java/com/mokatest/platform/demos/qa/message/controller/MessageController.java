package com.mokatest.platform.demos.qa.message.controller;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.Bug;
import com.mokatest.platform.demos.qa.domain.Requirement;
import com.mokatest.platform.demos.qa.domain.TestCase;
import com.mokatest.platform.demos.qa.message.service.MessageService;
import com.mokatest.platform.demos.qa.service.BugCommentService;
import com.mokatest.platform.demos.qa.service.BugService;
import com.mokatest.platform.demos.qa.service.RequirementService;
import com.mokatest.platform.demos.qa.service.TestCaseCommentService;
import com.mokatest.platform.demos.qa.service.TestCaseService;
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
    private final BugService bugService;
    private final TestCaseService testCaseService;
    private final BugCommentService bugCommentService;
    private final TestCaseCommentService testCaseCommentService;
    private final RequirementService requirementService;

    /**
     * 我的消息列表
     */
    @GetMapping("/list")
    public SaResult list(@RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "20") Integer pageSize,
                         @RequestParam(required = false) Integer isRead,
                         @RequestParam(required = false) String bizType,
                         @RequestParam(required = false) Boolean isMention) {
        return messageService.listMyMessages(page, pageSize, isRead, bizType, isMention);
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

    /**
     * 校验消息来源资源是否存在
     */
    @GetMapping("/validate-source")
    public SaResult validateSource(
            @RequestParam String bizType,
            @RequestParam Integer bizId,
            @RequestParam(required = false) Integer commentId) {
        try {
            // 1. 校验业务实体存在
            if ("bug".equals(bizType)) {
                Bug bug = bugService.getById(bizId);
                if (bug == null || bug.getIsDeleted() == 1) {
                    return SaResult.error("该BUG已被删除或不存在");
                }
            } else if ("testCase".equals(bizType)) {
                TestCase tc = testCaseService.getById(bizId);
                if (tc == null || tc.getIsDeleted() == 1) {
                    return SaResult.error("该用例已被删除或不存在");
                }
            } else if ("requirement".equals(bizType)) {
                Requirement req = requirementService.getById(bizId);
                if (req == null || req.getIsDeleted() == 1) {
                    return SaResult.error("该需求已被删除或不存在");
                }
            } else {
                return SaResult.error("不支持的消息类型");
            }

            // 2. 校验评论存在（如果传了 commentId）
            if (commentId != null && commentId > 0) {
                boolean commentExists;
                if ("bug".equals(bizType)) {
                    commentExists = bugCommentService.getById(commentId) != null;
                } else {
                    commentExists = testCaseCommentService.getById(commentId) != null;
                }
                if (!commentExists) {
                    return SaResult.error("该评论已被删除或不存在");
                }
            }

            return SaResult.ok("校验通过");
        } catch (Exception e) {
            return SaResult.error("校验失败：" + e.getMessage());
        }
    }
}
