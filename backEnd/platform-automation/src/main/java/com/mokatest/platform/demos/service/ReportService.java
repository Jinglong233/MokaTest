package com.mokatest.platform.demos.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.domain.ui.Report;
import com.mokatest.platform.demos.domain.ui.dto.queryDto.ReportQueryDTO;

import java.util.List;

/**
* @author: JingLong
* @description 针对表【report】的数据库操作Service
* @createDate 2025-09-24 19:59:50
*/
public interface ReportService extends IService<Report> {

    List<Report> allReport(Integer projectId);

    Report getReportDetail(Integer reportId);

    Page<Report> reportPageList(ReportQueryDTO queryDTO);

    /**
     * 删除报告
     * 
     * 仅允许删除已完成的报告（status = 1），执行逻辑删除并记录删除时间。
     *
     * @param reportId 报告ID
     * @return 是否成功
     */
    Boolean deleteReport(Integer reportId);
}
