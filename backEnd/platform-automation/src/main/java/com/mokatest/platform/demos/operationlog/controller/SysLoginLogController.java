package com.mokatest.platform.demos.operationlog.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.operationlog.service.SysLoginLogService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录日志查询接口（仅超级管理员）
 */
@RestController
@RequestMapping("/loginLog")
@RequiredArgsConstructor
public class SysLoginLogController {

    private final SysLoginLogService loginLogService;
    private final ProjectPermissionChecker permissionChecker;

    /**
     * 分页查询登录日志
     */
    @GetMapping("/list")
    public SaResult list(@RequestParam(required = false) String operation,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(required = false) String startTime,
                         @RequestParam(required = false) String endTime,
                         @RequestParam(required = false) Integer pageNum,
                         @RequestParam(required = false) Integer pageSize) {
        checkSuperAdmin();
        return loginLogService.list(operation, status, keyword, startTime, endTime, pageNum, pageSize);
    }

    /**
     * 仅超级管理员可访问登录日志
     */
    private void checkSuperAdmin() {
        if (!permissionChecker.isSuperAdmin(StpUtil.getLoginIdAsString())) {
            throw new BusinessException("仅超级管理员可查看登录日志");
        }
    }
}
