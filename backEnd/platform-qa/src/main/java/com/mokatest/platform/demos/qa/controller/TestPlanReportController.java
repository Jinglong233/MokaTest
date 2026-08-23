package com.mokatest.platform.demos.qa.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.util.SaResult;
import com.mokatest.platform.demos.qa.domain.TestPlanReport;
import com.mokatest.platform.demos.qa.service.TestPlanReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 测试计划报告
 */
@Tag(name = "测试计划报告")
@RestController
@RequestMapping("/qa/testPlanReport")
@RequiredArgsConstructor
public class TestPlanReportController {

    private final TestPlanReportService testPlanReportService;
    private final com.mokatest.platform.demos.qa.service.TestPlanService testPlanService;

    /**
     * 报告列表
     * 权限：qa:testplan:view
     */
    @SaCheckPermission("qa:testplan:view")
    @GetMapping("/list")
    public SaResult list(@RequestParam Integer projectId,
                         @RequestParam(required = false) String keyword,
                         @RequestParam(defaultValue = "1") Integer page,
                         @RequestParam(defaultValue = "10") Integer pageSize) {
        return testPlanReportService.listByProject(projectId, keyword, page, pageSize);
    }

    /**
     * 手动生成报告
     * 权限：qa:testplan:execute
     */
    @SaCheckPermission("qa:testplan:execute")
    @PostMapping("/generate")
    public SaResult generate(@RequestParam Integer planId) {
        return testPlanService.generatePlanReport(planId, "MANUAL", Integer.valueOf(StpUtil.getLoginIdAsString()));
    }

    /**
     * 报告详情（元数据）
     * 权限：qa:testplan:view
     */
    @SaCheckPermission("qa:testplan:view")
    @GetMapping("/{id}")
    public SaResult detail(@PathVariable Integer id) {
        TestPlanReport report = testPlanReportService.getById(id);
        if (report == null) {
            return SaResult.error("报告不存在");
        }
        return SaResult.ok().setData(report);
    }

    /**
     * 更新报告总结
     * 权限：qa:testplan:update
     */
    @SaCheckPermission("qa:testplan:update")
    @PostMapping("/summary")
    public SaResult updateSummary(@RequestBody Map<String, Object> body) {
        Integer id = body.get("id") != null ? ((Number) body.get("id")).intValue() : null;
        String summary = body.get("summary") != null ? String.valueOf(body.get("summary")) : null;
        if (id == null) {
            return SaResult.error("缺少报告ID");
        }
        return testPlanReportService.updateSummary(id, summary);
    }

    /**
     * 删除报告（逻辑删除）
     * 权限：qa:testplan:delete
     */
    @SaCheckPermission("qa:testplan:delete")
    @PostMapping("/delete/{id}")
    public SaResult delete(@PathVariable Integer id) {
        return testPlanReportService.deleteReport(id);
    }
}
