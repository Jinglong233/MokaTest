package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.operationlog.annotation.OperationLog;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import com.mokatest.platform.demos.qa.domain.TestCaseComment;
import com.mokatest.platform.demos.qa.service.TestCaseCommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用例评论接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看评论：qa:testcase:view
 *   创建/删除评论：qa:testcase:update（与用例编辑共用，删除仅限本人或管理员由前端控制入口）
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/testCase/comment")
@RequiredArgsConstructor
public class TestCaseCommentController {

    private final TestCaseCommentService testCaseCommentService;

    /**
     * 用例评论列表
     * 权限：qa:testcase:view
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer testCaseId) {
        return testCaseCommentService.listByCase(testCaseId);
    }

    /**
     * 保存用例评论
     * 权限：qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:update")
    @OperationLog(module = "qa", type = OperateType.CREATE, targetType = "testCaseComment", targetId = "#comment.id", targetName = "#comment.content")
    @PostMapping("/save")
    public SaResult save(@RequestBody TestCaseComment comment) {
        return testCaseCommentService.saveComment(comment, Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 编辑用例评论（仅本人可改）
     * 权限：qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:update")
    @OperationLog(module = "qa", type = OperateType.UPDATE, targetType = "testCaseComment", targetId = "#comment.id")
    @PostMapping("/update")
    public SaResult update(@RequestBody TestCaseComment comment) {
        return testCaseCommentService.updateComment(comment.getId(), comment.getContent(),
                Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 删除用例评论
     * 权限：qa:testcase:update
     */
    @SaCheckPermission("qa:testcase:update")
    @OperationLog(module = "qa", type = OperateType.DELETE, targetType = "testCaseComment", targetId = "#id")
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return testCaseCommentService.deleteComment(id);
    }
}
