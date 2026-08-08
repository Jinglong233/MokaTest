package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.service.BugOperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Bug操作日志接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link SaCheckPermission} 注解声明。
 *   查看操作日志：qa:bug:operationlog:view
 * admin 角色默认拥有上述所有权限。
 */
@Slf4j
@RestController
@RequestMapping("/qa/bug/operationLog")
@RequiredArgsConstructor
public class BugOperationLogController {

    private final BugOperationLogService bugOperationLogService;

    /**
     * BUG操作日志列表
     * 权限：qa:bug:operationlog:view
     */
    @SaCheckPermission("qa:bug:operationlog:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer bugId) {
        return bugOperationLogService.listByBug(bugId);
    }
}
