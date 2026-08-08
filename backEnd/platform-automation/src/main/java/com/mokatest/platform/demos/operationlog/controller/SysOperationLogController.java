package com.mokatest.platform.demos.operationlog.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.exception.BusinessException;
import com.mokatest.platform.demos.operationlog.dto.OperationLogQueryDTO;
import com.mokatest.platform.demos.operationlog.enums.OperateType;
import com.mokatest.platform.demos.operationlog.service.SysOperationLogService;
import com.mokatest.platform.demos.service.ProjectPermissionChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统操作日志查询接口（仅超级管理员）
 */
@RestController
@RequestMapping("/operationLog")
@RequiredArgsConstructor
public class SysOperationLogController {

    private final SysOperationLogService logService;
    private final ProjectPermissionChecker permissionChecker;

    /**
     * 分页查询操作日志
     */
    @GetMapping("/list")
    public SaResult list(OperationLogQueryDTO query) {
        checkSuperAdmin();
        return logService.list(query);
    }

    /**
     * 操作日志详情
     */
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Long id) {
        checkSuperAdmin();
        return logService.detail(id);
    }

    /**
     * 获取模块选项
     */
    @GetMapping("/moduleOptions")
    public SaResult moduleOptions() {
        checkSuperAdmin();
        List<Map<String, String>> options = Arrays.asList(
                Map.of("label", "质量管理", "value", "qa"),
                Map.of("label", "自动化测试", "value", "automation"),
                Map.of("label", "团队", "value", "team"),
                Map.of("label", "项目", "value", "project"),
                Map.of("label", "系统", "value", "system")
        );
        return SaResult.ok().setData(options);
    }

    /**
     * 获取操作类型选项
     */
    @GetMapping("/typeOptions")
    public SaResult typeOptions() {
        checkSuperAdmin();
        // LOGIN/LOGOUT 已迁移至独立的登录日志（sys_login_log），操作日志不再提供
        List<Map<String, String>> options = Arrays.stream(OperateType.values())
                .filter(type -> type != OperateType.LOGIN && type != OperateType.LOGOUT)
                .map(type -> Map.of("label", type.name(), "value", type.name()))
                .collect(Collectors.toList());
        return SaResult.ok().setData(options);
    }

    /**
     * 获取对象类型选项
     */
    @GetMapping("/targetTypeOptions")
    public SaResult targetTypeOptions() {
        checkSuperAdmin();
        List<Map<String, String>> options = Arrays.asList(
                Map.of("label", "需求", "value", "requirement"),
                Map.of("label", "BUG", "value", "bug"),
                Map.of("label", "用例", "value", "testCase"),
                Map.of("label", "测试集", "value", "testCaseSet"),
                Map.of("label", "模块", "value", "qaModule"),
                Map.of("label", "测试计划", "value", "testPlan"),
                Map.of("label", "BUG评论", "value", "bugComment"),
                Map.of("label", "场景", "value", "scene"),
                Map.of("label", "任务", "value", "plan"),
                Map.of("label", "执行", "value", "task"),
                Map.of("label", "用户", "value", "user"),
                Map.of("label", "团队", "value", "team"),
                Map.of("label", "项目", "value", "project"),
                Map.of("label", "团队成员", "value", "teamMember"),
                Map.of("label", "项目成员", "value", "projectMember")
        );
        return SaResult.ok().setData(options);
    }

    /**
     * 仅超级管理员可访问操作日志
     */
    private void checkSuperAdmin() {
        if (!permissionChecker.isSuperAdmin(StpUtil.getLoginIdAsString())) {
            throw new BusinessException("仅超级管理员可查看操作日志");
        }
    }
}
