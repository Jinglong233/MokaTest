package com.mokatest.platform.demos.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mokatest.platform.demos.domain.ui.Report;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.ReportQueryDTO;
import com.mokatest.platform.demos.domain.ui.vo.ResponseVO;
import com.mokatest.platform.demos.service.ReportService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 测试报告管理接口
 *
 * 权限说明：所有接口均基于团队维度 RBAC 进行鉴权，通过 {@link cn.dev33.satoken.annotation.SaCheckPermission} 注解声明。
 *   查看报告：report:view
 * admin 角色默认拥有上述所有权限。
 */
@RestController
@RequestMapping("/report")
public class ReportController {

    @Resource
    private ReportService reportService;

    /**
     * 分页查询报告
     * 权限：report:view
     */
    @SaCheckPermission("report:view")
    @PostMapping("reportPageList")
    public ResponseVO reportPageList(@RequestBody ReportQueryDTO queryDTO) {
        Page<Report> reportList = reportService.reportPageList(queryDTO);
        return ResponseVO.success(reportList);
    }

    /**
     * 获取所有报告
     * 权限：report:view
     */
    @SaCheckPermission("report:view")
    @GetMapping("allReport")
    public ResponseVO allReport(@RequestParam Integer projectId) {
        List<Report> reportList = reportService.allReport(projectId);
        return ResponseVO.success(reportList);
    }

    /**
     * 获取报告详情
     * 权限：report:view
     */
    @SaCheckPermission("report:view")
    @GetMapping("reportDetail")
    public ResponseVO getReportDetail(@RequestParam Integer reportId) {
        Report report = reportService.getReportDetail(reportId);
        return ResponseVO.success(report);
    }

    /**
     * 删除报告
     * 权限：report:delete
     */
    @SaCheckPermission("report:delete")
    @GetMapping("deleteReport")
    public ResponseVO deleteReport(@RequestParam Integer reportId) {
        Boolean result = reportService.deleteReport(reportId);
        return ResponseVO.success(result);
    }
}
