package com.mokatest.platform.demos.qa.service;

import cn.dev33.satoken.util.SaResult;
import com.baomidou.mybatisplus.extension.service.IService;
import com.mokatest.platform.demos.qa.domain.TestPlanReport;

public interface TestPlanReportService extends IService<TestPlanReport> {

    /** 项目报告列表（分页 + 名称/计划名模糊搜索），附实时通过率/执行结果 */
    SaResult listByProject(Integer projectId, String keyword, Integer page, Integer pageSize);

    /** 插入报告记录（内容快照由调用方组装） */
    SaResult createReport(TestPlanReport report);

    /** 更新报告总结（富文本） */
    SaResult updateSummary(Integer id, String summary);

    /** 逻辑删除 */
    SaResult deleteReport(Integer id);
}
