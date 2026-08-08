package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.BugComment;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import com.mokatest.platform.demos.qa.service.BugCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Bug评论接口
 *
 *权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看评论：qa:bug:comment:view
 *   创建评论：qa:bug:comment:create
 *   删除评论：qa:bug:comment:delete
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/bug/comment")
@RequiredArgsConstructor
public class BugCommentController {

    private final BugCommentService bugCommentService;

    /**
     * BUG评论列表
     * 权限：qa:bug:comment:view
     */
    @SaCheckPermission("qa:bug:comment:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer bugId) {
        return bugCommentService.listByBug(bugId);
    }

    /**
     * 保存BUG评论
     * 权限：qa:bug:comment:create
     */
    @SaCheckPermission("qa:bug:comment:create")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "bugComment", targetId = "#comment.id", targetName = "#comment.content")
    @PostMapping("/save")
    public SaResult save(@RequestBody BugComment comment) {
        return bugCommentService.saveComment(comment, Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 删除BUG评论
     * 权限：qa:bug:comment:delete
     */
    @SaCheckPermission("qa:bug:comment:delete")
    @OperationLog(module = "qa", type = OperateType.DELETE, targetType = "bugComment", targetId = "#id")
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return bugCommentService.deleteComment(id);
    }
}
