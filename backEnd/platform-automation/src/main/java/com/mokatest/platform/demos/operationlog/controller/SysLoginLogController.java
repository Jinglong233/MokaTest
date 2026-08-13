package com.mokatest.platform.demos.operationlog.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.operationlog.service.SysLoginLogService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

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
                         @RequestParam(required = false) String ip,
                         @RequestParam(required = false) String startTime,
                         @RequestParam(required = false) String endTime,
                         @RequestParam(required = false) Integer pageNum,
                         @RequestParam(required = false) Integer pageSize) {
        checkSuperAdmin();
        return loginLogService.list(operation, status, keyword, ip, startTime, endTime, pageNum, pageSize);
    }

    /**
     * 删除单条登录日志
     */
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Long id) {
        checkSuperAdmin();
        loginLogService.delete(id);
        return SaResult.ok();
    }

    /**
     * 批量删除登录日志
     */
    @PostMapping("/batchDelete")
    public SaResult batchDelete(@RequestBody List<Long> ids) {
        checkSuperAdmin();
        loginLogService.batchDelete(ids);
        return SaResult.ok();
    }

    /**
     * 清空登录日志
     */
    @PostMapping("/clear")
    public SaResult clear() {
        checkSuperAdmin();
        loginLogService.clear();
        return SaResult.ok();
    }

    /**
     * 按筛选条件导出 Excel
     */
    @GetMapping("/export")
    public void export(@RequestParam(required = false) String operation,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) String ip,
                       @RequestParam(required = false) String startTime,
                       @RequestParam(required = false) String endTime,
                       HttpServletResponse response) throws IOException {
        checkSuperAdmin();
        loginLogService.export(operation, status, keyword, ip, startTime, endTime, response);
    }

    /**
     * 仅超级管理员可访问登录日志
     */
    private void checkSuperAdmin() {
        if (!permissionChecker.isSuperAdmin(StpUtil.getLoginIdAsString())) {
            throw new BusinessException("仅超级管理员可操作登录日志");
        }
    }
}
