package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.service.TestCaseOperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用例操作日志接口
 * 权限：查看 qa:testcase:view
 */
@Slf4j
@RestController
@RequestMapping("/qa/testCase/operationLog")
@RequiredArgsConstructor
public class TestCaseOperationLogController {

    private final TestCaseOperationLogService testCaseOperationLogService;

    /**
     * 用例操作日志列表
     */
    @SaCheckPermission("qa:testcase:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer caseId) {
        return testCaseOperationLogService.listByCaseId(caseId);
    }
}
