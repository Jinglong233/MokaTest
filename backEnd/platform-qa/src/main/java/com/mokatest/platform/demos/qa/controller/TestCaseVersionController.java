package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.service.TestCaseVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用例版本快照接口（不做权限码校验）
 */
@Slf4j
@RestController
@RequestMapping("/qa/testCase/version")
@RequiredArgsConstructor
public class TestCaseVersionController {

    private final TestCaseVersionService testCaseVersionService;

    /**
     * 用例版本列表
     */
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer caseId) {
        return testCaseVersionService.listByCaseId(caseId);
    }

    /**
     * 版本快照详情
     */
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Integer id) {
        return testCaseVersionService.getDetail(id);
    }

    /**
     * 回滚到指定版本（写回用例并将状态改为 DRAFT）
     */
    @PostMapping("/rollback")
    public SaResult rollback(@RequestParam Integer versionId) {
        return testCaseVersionService.rollback(versionId);
    }
}
